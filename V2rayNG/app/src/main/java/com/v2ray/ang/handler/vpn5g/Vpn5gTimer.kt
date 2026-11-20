package com.v2ray.ang.handler.vpn5g

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.DISCONNECT_WORK_NAME
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.KEY_CONNECTED_AT
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.SESSION_LIMIT_MINUTES
import com.v2ray.ang.util.LogUtil
import java.util.concurrent.TimeUnit

/**
 * 30-minute auto-disconnect for VPN 5G sessions.
 *
 * On connect, [scheduleDisconnect] enqueues unique one-time work with a
 * 30-minute delay and records the connect timestamp (MMKV SETTING
 * "vpn5g_connected_at") so the home screen can show a live countdown.
 * On disconnect, [cancelDisconnect] cancels the work and clears the stamp.
 * The worker stops the VPN via [LauncherManager] and posts a notification;
 * no reconnect action is attached — reconnecting happens from the home screen.
 */
object Vpn5gTimer {

    fun scheduleDisconnect(context: Context) {
        MmkvManager.encodeSettings(KEY_CONNECTED_AT, System.currentTimeMillis())
        val request = OneTimeWorkRequestBuilder<Vpn5gDisconnectWorker>()
            .setInitialDelay(SESSION_LIMIT_MINUTES, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            DISCONNECT_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancelDisconnect(context: Context) {
        MmkvManager.encodeSettings(KEY_CONNECTED_AT, 0L)
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(DISCONNECT_WORK_NAME)
    }

    /** Connect-start timestamp in ms, or 0 when not connected by VPN 5G. */
    fun connectedAt(): Long = MmkvManager.decodeSettingsLong(KEY_CONNECTED_AT, 0L)

    /** Milliseconds remaining in the 30-minute session, or null when unknown. */
    fun remainingMillis(now: Long = System.currentTimeMillis()): Long? {
        val started = connectedAt()
        if (started <= 0L) return null
        val remaining = started + SESSION_LIMIT_MINUTES * 60_000L - now
        return remaining.coerceAtLeast(0L)
    }

    class Vpn5gDisconnectWorker(
        appContext: Context,
        params: WorkerParameters,
    ) : CoroutineWorker(appContext, params) {

        override suspend fun doWork(): Result {
            return try {
                LauncherManager.stopService(applicationContext)
                MmkvManager.encodeSettings(KEY_CONNECTED_AT, 0L)
                postTimerEndedNotification(applicationContext)
                Result.success()
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Vpn5gTimer: disconnect worker failed", e)
                Result.failure()
            }
        }

        private fun postTimerEndedNotification(context: Context) {
            val channelId = "vpn5g_timer"
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "VPN 5G",
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
                manager.createNotificationChannel(channel)
            }
            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_stat_name)
                .setContentTitle(context.getString(R.string.vpn5g_timer_ended_title))
                .setContentText(context.getString(R.string.vpn5g_timer_ended_body))
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(context.getString(R.string.vpn5g_timer_ended_body))
                )
                .setAutoCancel(true)
                .build()
            try {
                manager.notify(5101, notification)
            } catch (e: SecurityException) {
                LogUtil.e(AppConfig.TAG, "Vpn5gTimer: notification permission missing", e)
            }
        }
    }
}
