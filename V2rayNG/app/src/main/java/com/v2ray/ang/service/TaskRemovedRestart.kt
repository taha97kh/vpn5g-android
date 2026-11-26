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
 *
 * v1.1.32 hardening (Aref's report: the tunnel still died when the
 * app was closed from recents). The v1.1.30 restart used a single
 * INEXACT alarm (`AlarmManager.set`): on Android 12+ an inexact
 * alarm can be deferred for a long time once the app drops into a
 * restricted standby bucket after a recents swipe, and starting a
 * foreground service from an inexact-alarm PendingIntent in the
 * background is not among the documented exemptions — the start is
 * silently dropped. Now: an EXACT alarm when the platform allows
 * it (fires on time, Doze-exempt, FGS-start exempt), otherwise
 * `setAndAllowWhileIdle`; and TWO alarms (immediate + retry) so a
 * single dropped start cannot strand the session. The service
 * start itself is idempotent, so a duplicate is harmless. The
 * [SessionResurrection] periodic check is the backstop for OEMs
 * that kill the process without ever delivering onTaskRemoved.
 */
object TaskRemovedRestart {

    /** Delay before the restart alarm fires (ms). */
    const val RESTART_DELAY_MS = 1_000L

    /** Delay before the retry restart alarm fires (ms). */
    const val RETRY_DELAY_MS = 20_000L

    /** Request code for the restart PendingIntent (one per service class). */
    private const val REQUEST_CODE = 30_130

    /** Pure decision: restart only while a persistent session is on. */
    fun shouldSchedule(isPersistentConnection: Boolean): Boolean =
        isPersistentConnection

    /** How an alarm is scheduled. Pure decision, JVM-tested. */
    enum class AlarmKind { EXACT, ALLOW_WHILE_IDLE }

    /**
     * Exact alarms need no permission before Android 12 (API 31);
     * from API 31 they need the exact-alarm grant. Below API 23
     * there is no allow-while-idle variant at all (the scheduler
     * falls back to a plain alarm there).
     */
    fun chooseAlarmKind(sdkInt: Int, canScheduleExactAlarms: Boolean): AlarmKind =
        if (sdkInt >= 23 && (sdkInt < 31 || canScheduleExactAlarms)) {
            AlarmKind.EXACT
        } else {
            AlarmKind.ALLOW_WHILE_IDLE
        }

    /** The alarm kind usable right now on this device. */
    internal fun currentAlarmKind(alarmManager: AlarmManager): AlarmKind {
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { alarmManager.canScheduleExactAlarms() }.getOrDefault(false)
        } else {
            true
        }
        return chooseAlarmKind(Build.VERSION.SDK_INT, canExact)
    }

    /** Schedules [pendingIntent] at [triggerAtMs] with the given [kind]. */
    internal fun setAlarm(
        alarmManager: AlarmManager,
        kind: AlarmKind,
        triggerAtMs: Long,
        pendingIntent: PendingIntent,
    ) {
        when {
            kind == AlarmKind.EXACT && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent,
                )

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent,
                )

            else -> alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
        }
    }

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
            val alarmManager =
                context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                    ?: return
            val kind = currentAlarmKind(alarmManager)
            val now = System.currentTimeMillis()
            // Two attempts: immediate + retry. A dropped first start
            // (background-start denial, OEM race) must not strand
            // the session; the idempotent service start makes a
            // duplicate harmless.
            setAlarm(
                alarmManager, kind, now + RESTART_DELAY_MS,
                restartPendingIntent(context, serviceClass, retry = false),
            )
            setAlarm(
                alarmManager, kind, now + RETRY_DELAY_MS,
                restartPendingIntent(context, serviceClass, retry = true),
            )
            LogUtil.i(
                AppConfig.TAG,
                "TaskRemovedRestart: restart scheduled for " +
                    "${serviceClass.simpleName} (kind=$kind)",
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

    /** Foreground-service PendingIntent that restarts [serviceClass]. */
    private fun restartPendingIntent(
        context: Context,
        serviceClass: Class<out Service>,
        retry: Boolean,
    ): PendingIntent {
        val intent = Intent(context, serviceClass)
        val requestCode =
            REQUEST_CODE + serviceClass.name.hashCode() + if (retry) 1 else 0
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        } else {
            PendingIntent.getService(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
