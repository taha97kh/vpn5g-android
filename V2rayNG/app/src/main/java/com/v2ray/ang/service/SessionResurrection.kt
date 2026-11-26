package com.v2ray.ang.service

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.v2ray.ang.AppConfig
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.handler.vpn5g.Vpn5gConnectionPersistence
import com.v2ray.ang.handler.vpn5g.Vpn5gDaemonHeartbeat
import com.v2ray.ang.util.LogUtil

/**
 * Session resurrection backstop (v1.1.32, Aref's report: «وقتی اپ
 * رو از روی گوشی میبندیم فیلترشکن هم باهاش بسته نشه و تونل خودش
 * رو ادامه بده» — closing the app from the phone must not close
 * the tunnel; the tunnel continues by itself).
 *
 * [TaskRemovedRestart] covers the recents swipe when the system
 * delivers `onTaskRemoved`. Aggressive OEM task managers can kill
 * the whole `:daemon` process without ever delivering that
 * callback — then no restart is scheduled and the tunnel stays
 * dead. This backstop closes that hole: while a persistent
 * session is on, a periodic alarm (armed at every successful core
 * start, see `CoreServiceManager`) checks whether the daemon is
 * still alive. If the session is persistent but the daemon
 * heartbeat is stale AND no core service is running, the service
 * is started again through [LauncherManager] (the same route
 * `BootReceiver` uses). The chain re-arms itself only while the
 * session stays persistent, so an explicit user disconnect ends
 * it within one interval — the app then stays closed, exactly as
 * the exit confirmation promises.
 *
 * The heartbeat is only written by the daemon watchdog in AUTO
 * mode, so the running-service check is what protects a healthy
 * manual-mode session from a spurious restart; the service start
 * itself is idempotent either way.
 *
 * Honest limit: a user Force Stop (or an OEM "clear all" that
 * force-stops the app) cancels alarms too — nothing can run
 * until the user opens the app again. That is an Android rule,
 * not something an app can override.
 *
 * The decision ([shouldResurrect]) is pure and JVM-tested; the
 * alarm/receiver wiring is the only Android touch point.
 */
object SessionResurrection {

    /** Interval between resurrection checks (ms). */
    const val CHECK_INTERVAL_MS = 5 * 60_000L

    /** Request code for the check PendingIntent. */
    private const val REQUEST_CODE = 30_132

    /** Core service class names that count as "daemon alive". */
    private val CORE_SERVICE_NAMES = setOf(
        CoreVpnService::class.java.name,
        CoreProxyOnlyService::class.java.name,
        CoreRootService::class.java.name,
    )

    /**
     * Pure decision: resurrect only a persistent session whose
     * daemon shows no sign of life (stale heartbeat and no core
     * service running).
     */
    fun shouldResurrect(
        isPersistentConnection: Boolean,
        heartbeatFresh: Boolean,
        serviceRunning: Boolean,
    ): Boolean = isPersistentConnection && !heartbeatFresh && !serviceRunning

    /** Arms the next resurrection check. Never throws. */
    fun arm(context: Context) {
        runCatching {
            val alarmManager =
                context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                    ?: return
            TaskRemovedRestart.setAlarm(
                alarmManager,
                TaskRemovedRestart.currentAlarmKind(alarmManager),
                System.currentTimeMillis() + CHECK_INTERVAL_MS,
                checkPendingIntent(context),
            )
        }.onFailure { e ->
            LogUtil.e(AppConfig.TAG, "SessionResurrection: failed to arm check", e)
        }
    }

    /** Cancels the pending resurrection check. Never throws. */
    fun disarm(context: Context) {
        runCatching {
            val alarmManager =
                context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                    ?: return
            alarmManager.cancel(checkPendingIntent(context))
        }.onFailure { e ->
            LogUtil.e(AppConfig.TAG, "SessionResurrection: failed to disarm check", e)
        }
    }

    /**
     * One resurrection check (called from the alarm receiver).
     * Restarts the service when the decision says so, then re-arms
     * the chain while the session stays persistent. Never throws.
     */
    fun onCheck(context: Context) {
        val persistent = runCatching {
            Vpn5gConnectionPersistence.isPersistentConnection()
        }.getOrDefault(false)
        if (!persistent) {
            LogUtil.i(
                AppConfig.TAG,
                "SessionResurrection: no persistent session, chain ends",
            )
            disarm(context)
            return
        }
        runCatching {
            val heartbeatFresh = Vpn5gDaemonHeartbeat.isHeartbeatFresh()
            val serviceRunning = isCoreServiceRunning(context)
            if (shouldResurrect(persistent, heartbeatFresh, serviceRunning)) {
                LogUtil.w(
                    AppConfig.TAG,
                    "SessionResurrection: persistent session but daemon dead " +
                        "(mode=VPN phase=resurrection) — restarting service",
                )
                LauncherManager.startService(context)
            }
        }.onFailure { e ->
            LogUtil.e(AppConfig.TAG, "SessionResurrection: check failed", e)
        }
        arm(context)
    }

    /** Broadcast PendingIntent for the periodic check (explicit component). */
    private fun checkPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, SessionResurrectionReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * True when one of the core services is in the system's
     * running-services list (own-app services only on modern
     * Android). False on any failure — the idempotent start makes
     * a false negative harmless.
     */
    private fun isCoreServiceRunning(context: Context): Boolean =
        runCatching {
            val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    ?: return false
            @Suppress("DEPRECATION")
            activityManager.getRunningServices(Int.MAX_VALUE).any { info ->
                info.service.className in CORE_SERVICE_NAMES
            }
        }.getOrDefault(false)
}

/** Alarm entry point for [SessionResurrection.onCheck]. */
class SessionResurrectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        SessionResurrection.onCheck(context)
    }
}
