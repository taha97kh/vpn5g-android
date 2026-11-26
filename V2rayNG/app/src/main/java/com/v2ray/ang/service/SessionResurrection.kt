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
 * v1.1.33: liveness is the core-alive stamp (written by the
 * daemon speed job every ~1 s while the core answers, in every
 * mode) instead of the AUTO-only watchdog heartbeat — a stuck
 * service with a dead core is now restarted too, not just a
 * fully killed process. The service start itself is idempotent
 * either way, and checks run every 60 s.
 *
 * Honest limit: a user Force Stop (or an OEM "clear all" that
 * force-stops the app) cancels alarms too — nothing can run
 * until the user opens the app again. That is an Android rule,
 * not something an app can override.
 *
 * The decision ([resurrectAction]) is pure and JVM-tested; the
 * alarm/receiver wiring is the only Android touch point.
 */
object SessionResurrection {

    /**
     * Interval between resurrection checks (ms). v1.1.33: 60 s
     * (was 5 min) — Aref's order is that the tunnel must never
     * stay down («فیلتر هیچ وقت از کار نیفتد»); a killed daemon is
     * now back within about a minute worst case instead of five.
     */
    const val CHECK_INTERVAL_MS = 60_000L

    /** Request code for the check PendingIntent. */
    private const val REQUEST_CODE = 30_132

    /** Core service class names that count as "daemon alive". */
    private val CORE_SERVICE_NAMES = setOf(
        CoreVpnService::class.java.name,
        CoreProxyOnlyService::class.java.name,
        CoreRootService::class.java.name,
    )

    /** What one resurrection check should do. Pure, JVM-tested. */
    enum class Action { NONE, START, RESTART }

    /**
     * Pure decision (v1.1.33): resurrect a persistent session
     * whose CORE shows no sign of life. The signal is the
     * core-alive stamp (written every ~1 s by the daemon speed
     * job in every mode while the core answers stats queries),
     * NOT the watchdog heartbeat — the heartbeat is AUTO-only,
     * so before 1.1.33 a manual-mode session whose service stayed
     * up with a dead core was invisible here (serviceRunning
     * blocked the restart forever). Now:
     * - core alive → [Action.NONE] (healthy tunnel, never touch);
     * - core dead + no service → [Action.START];
     * - core dead + service up → [Action.RESTART] (stuck service:
     *   a plain start would be refused by the start lock, so the
     *   service itself must restart).
     * A non-persistent session (explicit user disconnect) is
     * always [Action.NONE].
     */
    fun resurrectAction(
        isPersistentConnection: Boolean,
        coreAliveFresh: Boolean,
        serviceRunning: Boolean,
    ): Action = when {
        !isPersistentConnection -> Action.NONE
        coreAliveFresh -> Action.NONE
        serviceRunning -> Action.RESTART
        else -> Action.START
    }



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
            // v1.1.33: the core-alive stamp (speed job, every mode)
            // is the liveness signal — the AUTO-only watchdog
            // heartbeat left manual-mode stuck services invisible.
            val coreAliveFresh = Vpn5gDaemonHeartbeat.isCoreAliveFresh()
            val serviceRunning = isCoreServiceRunning(context)
            when (resurrectAction(persistent, coreAliveFresh, serviceRunning)) {
                Action.START -> {
                    LogUtil.w(
                        AppConfig.TAG,
                        "SessionResurrection: persistent session but daemon dead " +
                            "(mode=VPN phase=resurrection) — starting service",
                    )
                    LauncherManager.startService(context)
                }

                Action.RESTART -> {
                    LogUtil.w(
                        AppConfig.TAG,
                        "SessionResurrection: service up but core dead " +
                            "(mode=VPN phase=resurrection) — restarting service",
                    )
                    LauncherManager.restartService(context)
                }

                Action.NONE -> Unit
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
