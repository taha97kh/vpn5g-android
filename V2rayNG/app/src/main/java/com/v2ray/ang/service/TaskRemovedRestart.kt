package com.v2ray.ang.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.vpn5g.Vpn5gConnectionPersistence
import com.v2ray.ang.util.LogUtil

/**
 * Recents-removal survival (v1.1.30, Aref's report: «اپ رو از رو
 * صفحه پاک میکنی نت قطع میشه» — swiping the app off the recents
 * screen killed the internet).
 *
 * The services already run as foreground services in the `:daemon`
 * process with START_STICKY, but aggressive OEM task managers kill
 * the whole process on a recents swipe and never restart it. The
 * standard countermeasure: [Service.onTaskRemoved] schedules a
 * near-immediate AlarmManager restart of the same service, so the
 * tunnel comes back by itself.
 *
 * The restart is scheduled ONLY while a persistent session is on
 * ([Vpn5gConnectionPersistence.isPersistentConnection] — the user
 * left the switch ON). After an explicit user disconnect the flag
 * is false and nothing is scheduled: the app stays closed, exactly
 * as the 1.1.29 exit-confirmation promises. The service start
 * itself stays idempotent (the existing start lock + foreground
 * entry), so a restart racing a still-running service is harmless.
 *
 * The decision ([shouldSchedule]) is pure and JVM-tested; the alarm
 * wiring is the only Android touch point.
 */
object TaskRemovedRestart {

    /** Delay before the restart alarm fires (ms). */
    const val RESTART_DELAY_MS = 1_000L

    /** Request code for the restart PendingIntent (one per service class). */
    private const val REQUEST_CODE = 30_130

    /** Pure decision: restart only while a persistent session is on. */
    fun shouldSchedule(isPersistentConnection: Boolean): Boolean =
        isPersistentConnection

    /**
     * Schedules the restart of [serviceClass] when the session is
     * persistent. Called from each core service's onTaskRemoved.
     * Never throws — a failed schedule is logged, not fatal.
     */
    fun scheduleIfPersistent(context: Context, serviceClass: Class<out Service>) {
        val persistent = runCatching {
            Vpn5gConnectionPersistence.isPersistentConnection()
        }.getOrDefault(false)
        if (!shouldSchedule(persistent)) {
            LogUtil.i(
                AppConfig.TAG,
                "TaskRemovedRestart: no persistent session, not restarting " +
                    serviceClass.simpleName,
            )
            return
        }
        runCatching {
            val intent = Intent(context, serviceClass)
            val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(
                    context,
                    REQUEST_CODE + serviceClass.name.hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            } else {
                PendingIntent.getService(
                    context,
                    REQUEST_CODE + serviceClass.name.hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }
            val alarmManager =
                context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.set(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + RESTART_DELAY_MS,
                pendingIntent,
            )
            LogUtil.i(
                AppConfig.TAG,
                "TaskRemovedRestart: restart scheduled for ${serviceClass.simpleName}",
            )
        }.onFailure { e ->
            LogUtil.e(
                AppConfig.TAG,
                "TaskRemovedRestart: failed to schedule restart for " +
                    serviceClass.simpleName,
                e,
            )
        }
    }
}
