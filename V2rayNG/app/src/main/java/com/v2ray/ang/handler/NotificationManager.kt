package com.v2ray.ang.handler

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.CoreServiceManager
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.extension.delay
import com.v2ray.ang.extension.toSpeedString
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants
import com.v2ray.ang.handler.vpn5g.Vpn5gDaemonHeartbeat
import com.v2ray.ang.handler.vpn5g.Vpn5gVolumeStore
import com.v2ray.ang.helper.NotificationHelper
import com.v2ray.ang.ui.vpn5g.Vpn5gMainActivity
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object NotificationManager {
    private const val NOTIFICATION_ID = 1
    private const val NOTIFICATION_PENDING_INTENT_CONTENT = 0
    private const val NOTIFICATION_PENDING_INTENT_STOP_V2RAY = 1
    private const val NOTIFICATION_PENDING_INTENT_RESTART_V2RAY = 2
    private const val NOTIFICATION_ICON_THRESHOLD = 3000
    /** Speed poll interval (v1.1.14): ~1 s so the floating speeds feel live. */
    private const val QUERY_INTERVAL_MS = 1000L

    private var lastQueryTime = 0L

    /**
     * Screen-off mute (v1.1.33): while true, the speed job keeps
     * QUERYING and accumulating real tunnel bytes (volume metering
     * + core-alive stamp) but does not refresh the notification
     * text. Before 1.1.33 SCREEN_OFF cancelled the job outright,
     * so consumption during screen-off was only counted if the
     * core survived until the next query — a failover or stop in
     * between silently lost those bytes.
     */
    @Volatile
    private var screenOff = false

    private var mBuilder: NotificationCompat.Builder? = null
    private var speedNotificationJob: Job? = null
    private var mNotificationManager: NotificationManager? = null

    /**
     * Starts the speed notification.
     *
     * v1.1.14: the VPN 5G flow forces it on — live tunnel speeds are
     * part of the product now, not a hidden setting ([force] is true
     * when the service was started from the VPN 5G flow). The legacy
     * upstream setting ([AppConfig.PREF_SPEED_ENABLED]) still gates
     * the non-forced path.
     * @param currentConfig The current profile configuration.
     */
    fun startSpeedNotification(force: Boolean = false) {
        // v1.1.33: SCREEN_ON un-mutes the notification text; the
        // job itself never stopped (see [screenOff]).
        screenOff = false
        if (!force && MmkvManager.decodeSettingsBool(AppConfig.PREF_SPEED_ENABLED) != true) return
        if (speedNotificationJob != null || CoreServiceManager.isRunning() == false) return

        var lastZeroSpeed = false

        speedNotificationJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                lastZeroSpeed = updateSpeedNotificationOnce(lastZeroSpeed)
                delay(QUERY_INTERVAL_MS)
            }
        }
    }

    /**
     * Shows the notification.
     * @param currentConfig The current profile configuration.
     */
    fun showNotification(currentConfig: ProfileItem?) {
        val service = getService() ?: return

        // Reset last query time to avoid querying stats too soon after showing the notification
        lastQueryTime = System.currentTimeMillis()

        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        // v1.1.14: the notification opens the VPN 5G home screen, NEVER
        // the upstream config list (MainActivity) — the raw config list
        // must stay completely hidden. singleTop + clear-top: no second
        // task stack, the existing home comes forward.
        val startMainIntent = Intent(service, Vpn5gMainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val contentPendingIntent = PendingIntent.getActivity(service, NOTIFICATION_PENDING_INTENT_CONTENT, startMainIntent, flags)

        val stopV2RayIntent = Intent(AppConfig.BROADCAST_ACTION_SERVICE)
        stopV2RayIntent.`package` = AppConfig.ANG_PACKAGE
        stopV2RayIntent.putExtra("key", AppConfig.MSG_STATE_STOP)
        val stopV2RayPendingIntent = PendingIntent.getBroadcast(service, NOTIFICATION_PENDING_INTENT_STOP_V2RAY, stopV2RayIntent, flags)

        val restartV2RayIntent = Intent(AppConfig.BROADCAST_ACTION_SERVICE)
        restartV2RayIntent.`package` = AppConfig.ANG_PACKAGE
        restartV2RayIntent.putExtra("key", AppConfig.MSG_STATE_RESTART)
        val restartV2RayPendingIntent = PendingIntent.getBroadcast(service, NOTIFICATION_PENDING_INTENT_RESTART_V2RAY, restartV2RayIntent, flags)

        val channelId =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                createNotificationChannel()
            } else {
                // If earlier version channel ID is not used
                // https://developer.android.com/reference/android/support/v4/app/NotificationCompat.Builder.html#NotificationCompat.Builder(android.content.Context)
                ""
            }

        mBuilder = NotificationCompat.Builder(service, channelId)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(service.getString(R.string.app_name))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPendingIntent)
            .addAction(
                R.drawable.ic_delete_24dp,
                service.getString(R.string.notification_action_stop_v2ray),
                stopV2RayPendingIntent
            )
            .addAction(
                R.drawable.ic_restore_24dp,
                service.getString(R.string.title_service_restart),
                restartV2RayPendingIntent
            )

        //mBuilder?.setDefaults(NotificationCompat.FLAG_ONLY_ALERT_ONCE)

        service.startForeground(NOTIFICATION_ID, mBuilder?.build())
    }

    /**
     * Fulfills or refreshes the foreground-service contract before a start command can
     * return early. A duplicate startForegroundService call still requires the service
     * to enter foreground state promptly, even when the core is already running.
     */
    fun ensureForeground() {
        val service = getService() ?: return
        val notification = mBuilder?.build()
        if (notification == null) showNotification(null) else service.startForeground(NOTIFICATION_ID, notification)
    }

    /**
     * Cancels the notification.
     */
    fun cancelNotification() {
        val service = getService() ?: return
        service.stopForeground(Service.STOP_FOREGROUND_REMOVE)

        mBuilder = null
        speedNotificationJob?.cancel()
        speedNotificationJob = null
        mNotificationManager = null
        screenOff = false
    }

    /**
     * Screen-off (v1.1.33): mute the notification text only. The
     * stats job KEEPS RUNNING — it is the single querier of the
     * native counters and the only writer of the real volume
     * counters, so cancelling it here (the pre-1.1.33 behaviour)
     * left screen-off consumption uncounted whenever the core
     * restarted before the next query. The job still ends with
     * the service via [cancelNotification].
     */
    fun stopSpeedNotification() {
        screenOff = true
        updateNotification("", 0, 0)
    }

    /**
     * Final stats flush (v1.1.33): one last query + accumulation
     * before the core stops, so the <1 s tail since the previous
     * round is counted instead of lost. Called by
     * CoreServiceManager on every core-stop path (stop, failover
     * restart, reload) while the core is still running; the
     * native counters reset on query, so a racing job round can
     * never double-count. Never throws.
     */
    fun flushStatsOnce() {
        if (CoreServiceManager.isRunning() == false) return
        runCatching {
            // Bypass the too-short-interval guard: this is the last
            // chance to read the counters before they are gone.
            lastQueryTime = 0L
            updateSpeedNotificationOnce(lastZeroSpeed = true)
        }.onFailure { e ->
            LogUtil.e(AppConfig.TAG, "NotificationManager: final stats flush failed", e)
        }
    }

    /**
     * Creates a notification channel for Android O and above.
     * @return The channel ID.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(): String {
        val channelId = AppConfig.RAY_NG_CHANNEL_ID
        val service = getService() ?: return channelId
        NotificationHelper.ensureNotificationChannel(
            context = service,
            channelId = channelId,
            channelNameRes = R.string.notification_channel_service,
            // Foreground-service notifications must remain visible; LOW is silent but valid.
            importance = NotificationManager.IMPORTANCE_LOW,
        ) {
            lightColor = Color.DKGRAY
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        return channelId
    }

    /**
     * Updates the notification with the given content text and traffic data.
     * @param contentText The content text.
     * @param proxyTraffic The proxy traffic.
     * @param directTraffic The direct traffic.
     */
    private fun updateNotification(contentText: String?, proxyTraffic: Long, directTraffic: Long) {
        if (mBuilder != null) {
            if (proxyTraffic < NOTIFICATION_ICON_THRESHOLD && directTraffic < NOTIFICATION_ICON_THRESHOLD) {
                mBuilder?.setSmallIcon(R.drawable.ic_stat_name)
            } else if (proxyTraffic > directTraffic) {
                mBuilder?.setSmallIcon(R.drawable.ic_stat_proxy)
            } else {
                mBuilder?.setSmallIcon(R.drawable.ic_stat_direct)
            }
            mBuilder?.setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            mBuilder?.setContentText(contentText)
            getNotificationManager()?.notify(NOTIFICATION_ID, mBuilder?.build())
        }
    }

    /**
     * Gets the notification manager.
     * @return The notification manager.
     */
    private fun getNotificationManager(): NotificationManager? {
        if (mNotificationManager == null) {
            val service = getService() ?: return null
            mNotificationManager = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        }
        return mNotificationManager
    }

    /**
     * Updates the speed notification once.
     * Queries traffic stats, separates proxy and direct, and updates the notification.
     * @param lastZeroSpeed The previous zero speed state.
     * @return The current zero speed state.
     */
    private fun updateSpeedNotificationOnce(lastZeroSpeed: Boolean): Boolean {
        val queryTime = System.currentTimeMillis()
        val sinceLastQueryIn = (queryTime - lastQueryTime)

        // If the query interval is too short, skip this round to avoid excessive CPU usage
        if (sinceLastQueryIn < QUERY_INTERVAL_MS) {
            LogUtil.w(AppConfig.TAG, "Query interval too short: ${sinceLastQueryIn}ms, skipping")
            lastQueryTime = queryTime
            return lastZeroSpeed
        }
        val sinceLastQueryInSeconds = sinceLastQueryIn / 1000.0

        var proxyUplink = 0L
        var proxyDownlink = 0L
        var directUplink = 0L
        var directDownlink = 0L

        val stats = CoreServiceManager.queryAllOutboundTrafficStats()
        // v1.1.34 (zombie fix): the core ANSWERED only when the
        // stats query returned entries. queryAllOutboundTrafficStats
        // returns an empty list when the core is stopped — and a
        // wedged core whose running flag is stale answers with an
        // empty payload too. Before 1.1.34 the core-alive stamp
        // below was written unconditionally, so a dead core kept
        // a fresh stamp forever and the resurrection backstop
        // never fired: key icon up, no traffic (Aref's recents-
        // swipe report). The stamp now proves the core answered,
        // and CoreServiceManager counts silent rounds to restart
        // a wedged core in place.
        val coreAnswered = stats.isNotEmpty()
        stats.forEach { stat ->
            when {
                stat.tag == AppConfig.TAG_DIRECT -> {
                    when (stat.direction) {
                        AppConfig.UPLINK -> directUplink += stat.value
                        AppConfig.DOWNLINK -> directDownlink += stat.value
                    }
                }

                // Accumulate stats for all proxy outbounds (including custom subscription tags)
                stat.tag != AppConfig.TAG_BLOCKED -> {
                    when (stat.direction) {
                        AppConfig.UPLINK -> proxyUplink += stat.value
                        AppConfig.DOWNLINK -> proxyDownlink += stat.value
                    }
                }
            }
        }

        val proxyTotal = proxyUplink + proxyDownlink
        val directTotal = directUplink + directDownlink
        val zeroSpeed = proxyTotal + directTotal == 0L
        // v1.1.15 (speeds root-cause fix): publish the tunnel-only
        // rates for the home screen. The UI process cannot query the
        // core itself (different process — the :daemon service owns
        // the core), so this job, in the daemon, is the SINGLE querier
        // and MMKV (multi-process) carries the rates across. The
        // native counters reset on every query, so value/elapsed IS
        // the interval rate. Published every round — including zero
        // rounds — so the home screen can tell "no data" (stale) from
        // "no traffic".
        val downBps = (proxyDownlink / sinceLastQueryInSeconds).toLong().coerceAtLeast(0L)
        val upBps = (proxyUplink / sinceLastQueryInSeconds).toLong().coerceAtLeast(0L)
        MmkvManager.encodeSettings(Vpn5gConstants.KEY_SPEED_DOWN_BPS, downBps)
        MmkvManager.encodeSettings(Vpn5gConstants.KEY_SPEED_UP_BPS, upBps)
        MmkvManager.encodeSettings(Vpn5gConstants.KEY_SPEED_UPDATED_AT_MS, queryTime)
        // v1.1.18 (volume, Model B): the native counters reset on
        // every query, so proxyDownlink/proxyUplink ARE the real
        // tunnel bytes of this interval — accumulating them here, in
        // the single querier, is a true byte count, not a rate
        // sample. The counter is keyed to the current grant (set by
        // the UI process) and survives restarts. Direct (bypassed)
        // traffic never counts: it does not go through the tunnel.
        // v1.1.33: the <1 s tail before a stop is captured by
        // [flushStatsOnce] on every core-stop path, and the
        // grant-independent session counter accumulates alongside
        // so the free tier's displayed consumption is real too.
        Vpn5gVolumeStore.addIntervalBytes(proxyDownlink + proxyUplink)
        Vpn5gVolumeStore.addSessionIntervalBytes(proxyDownlink + proxyUplink)
        // v1.1.33: core-alive stamp for the resurrection backstop —
        // written every round while the core answers, every mode.
        // v1.1.34: ONLY when the core actually answered this round
        // (non-empty stats) — a silent core must let the stamp go
        // stale so the backstop restarts the stuck service.
        if (coreAnswered) {
            Vpn5gDaemonHeartbeat.writeCoreAlive(queryTime)
        }
        CoreServiceManager.noteStatsRound(coreAnswered)
        if (!screenOff && (!zeroSpeed || !lastZeroSpeed)) {
            // v1.1.14: TUNNEL-ONLY speeds with Persian labels — the
            // bypassed (direct) traffic is never shown, and nothing is
            // shown when the tunnel counter is unavailable (zeroSpeed
            // with a previous zero stays silent above).
            val service = getService()
            val text = if (service != null) {
                service.getString(R.string.vpn5g_speed_download) + " " +
                    downBps.toSpeedString() +
                    "  •  " +
                    service.getString(R.string.vpn5g_speed_upload) + " " +
                    upBps.toSpeedString()
            } else {
                "proxy •  ${upBps.toSpeedString()}↑  " +
                    "${downBps.toSpeedString()}↓"
            }
            updateNotification(text, proxyTotal, directTotal)
        }
        lastQueryTime = queryTime
        return zeroSpeed
    }

    /**
     * Gets the service instance.
     * @return The service instance.
     */
    private fun getService(): Service? {
        return CoreServiceManager.serviceControl?.get()?.getService()
    }
}
