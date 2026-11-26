package com.v2ray.ang.core

import android.app.Activity
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import androidx.core.content.ContextCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.contracts.IDialerService
import com.v2ray.ang.contracts.ServiceControl
import com.v2ray.ang.dto.ConnectionTestResult
import com.v2ray.ang.dto.OutboundTrafficStat
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.BrowserDialerMode
import com.v2ray.ang.extension.delay
import com.v2ray.ang.extension.isNotNullEmpty
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.NotificationManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.SpeedtestManager
import com.v2ray.ang.handler.vpn5g.Vpn5gAutoConnect
import com.v2ray.ang.handler.vpn5g.Vpn5gConnectionPersistence
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants
import com.v2ray.ang.handler.vpn5g.Vpn5gDaemonHeartbeat
import com.v2ray.ang.handler.vpn5g.Vpn5gDaemonPick
import com.v2ray.ang.handler.vpn5g.Vpn5gFailedServers
import com.v2ray.ang.handler.vpn5g.Vpn5gGeoIp
import com.v2ray.ang.handler.vpn5g.Vpn5gHealthScore
import com.v2ray.ang.handler.vpn5g.Vpn5gRealPing
import com.v2ray.ang.handler.vpn5g.Vpn5gServerValidator
import com.v2ray.ang.handler.vpn5g.Vpn5gStandbyLiveness
import com.v2ray.ang.handler.vpn5g.Vpn5gSync
import com.v2ray.ang.handler.vpn5g.Vpn5gTunnelCheck
import com.v2ray.ang.handler.vpn5g.Vpn5gWatchdogScore
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.service.DialerNativeService
import com.v2ray.ang.service.DialerWebviewService
import com.v2ray.ang.service.NetworkMonitor
import com.v2ray.ang.service.SessionResurrection
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
// NOTE: no kotlinx.coroutines.delay import here — the file already
// imports com.v2ray.ang.extension.delay (Int/Long wrappers over
// kotlinx delay); importing both makes every delay(Long) call
// ambiguous (broke the 1.1.29 CI compile).
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.ProcessFinder
import java.lang.ref.SoftReference
import java.net.InetSocketAddress

object CoreServiceManager {

    private val coreController: CoreController = CoreNativeManager.newCoreController(CoreCallback())
    private val mMsgReceive = ReceiveMessageHandler()
    private var currentConfig: ProfileItem? = null
    private var processFinder: XrayProcessFinder? = null
    private var browserDialer: IDialerService? = null
    private var networkMonitor: NetworkMonitor? = null
    private val connectionTestScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var isReloading = false

    /**
     * Restart wait (v1.1.13): after stopping the core for a restart,
     * wait at most this long for it to actually stop, polling every
     * [RESTART_STOP_POLL_MS], instead of a fixed 500 ms. Starting the
     * new core while the old one is still stopping is refused with
     * "Core already running" and wedges the daemon flag until a
     * force-stop clears it.
     */
    private const val RESTART_STOP_WAIT_MS = 3_000L
    private const val RESTART_STOP_POLL_MS = 100L

    /** Tun descriptor the core was started with, null in the proxy only and root run modes. */
    private var currentVpnInterface: ParcelFileDescriptor? = null

    /**
     * Daemon watchdog (v1.1.29): drop detection + failover decision
     * live HERE, in the daemon process, so recovery works with the
     * screen off and the UI process dead — the UI-process watchdog
     * alone stranded sessions on «در حال تغییر سرور...» whenever
     * the UI was frozen or killed (Aref's 2026-11-25 device
     * report). The job is a child of [daemonWatchdogScope], started
     * after every successful core start and cancelled first thing
     * in [stopCoreLoop] (teardown prevents new work). It only acts
     * for VPN 5G persistent sessions in AUTO mode (see
     * [daemonWatchdogGateOpen]); every other usage of this manager
     * is untouched. The UI watchdog stays as backup and stands
     * down while the daemon heartbeat is fresh.
     */
    private val daemonWatchdogScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var daemonWatchdogJob: Job? = null

    /**
     * Daemon standby liveness (v1.1.33): keeps several good-ping
     * configs probed-alive and pre-ranked in MMKV while a session
     * runs, so a drop replaces the dead server from the queue
     * instead of a stale stored delay. Same gate as the watchdog
     * (persistent + AUTO); cancelled with it in [stopCoreLoop].
     */
    private var daemonStandbyJob: Job? = null

    @Volatile
    private var daemonWatchdogScore = 0.0

    @Volatile
    private var daemonBaselineIp: String? = null

    /** Guids already tried by the in-flight daemon failover. */
    private val daemonTriedGuids = mutableSetOf<String>()

    var serviceControl: SoftReference<ServiceControl>? = null
        set(value) {
            field = value
            val service = value?.get()?.getService()
            CoreNativeManager.initCoreEnv(service)
            if (service != null && processFinder == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                processFinder = XrayProcessFinder(service)
                coreController.registerProcessFinder(processFinder)
            }
        }

    /**
     * Checks if the V2Ray service is running.
     * @return True if the service is running, false otherwise.
     */
    fun isRunning() = coreController.isRunning

    /**
     * Gets the name of the currently running server.
     * @return The name of the running server.
     */
    fun getRunningServerName() = currentConfig?.remarks.orEmpty()

    /**
     * Refer to the official documentation for [registerReceiver](https://developer.android.com/reference/androidx/core/content/ContextCompat#registerReceiver(android.content.Context,android.content.BroadcastReceiver,android.content.IntentFilter,int):
     * `registerReceiver(Context, BroadcastReceiver, IntentFilter, int)`.
     * Starts the V2Ray core service.
     */
    fun startCoreLoop(vpnInterface: ParcelFileDescriptor?): Boolean {
        if (isRunning()) {
            LogUtil.w(AppConfig.TAG, "StartCore-Manager: Core already running")
            return false
        }

        val service = getService()
        if (service == null) {
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: Service is null")
            return false
        }

        try {
            doStartCoreLoop(service, vpnInterface)
            return true
        } catch (e: Exception) {
            val message = e.message?.takeUnless { it.isBlank() } ?: e.javaClass.simpleName
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: $message", e)
            MessageHelper.sendMsg2UI(service, AppConfig.MSG_STATE_START_FAILURE, message)
            NotificationManager.cancelNotification()
            return false
        }
    }

    @Throws(Exception::class)
    private fun doStartCoreLoop(service: Service, vpnInterface: ParcelFileDescriptor?) {
        val mFilter = IntentFilter(AppConfig.BROADCAST_ACTION_SERVICE)
        mFilter.addAction(Intent.ACTION_SCREEN_ON)
        mFilter.addAction(Intent.ACTION_SCREEN_OFF)
        mFilter.addAction(Intent.ACTION_USER_PRESENT)
        ContextCompat.registerReceiver(service, mMsgReceive, mFilter, Utils.receiverFlags())

        currentVpnInterface = vpnInterface
        launchCore(service, vpnInterface)
        startNetworkMonitor(service)
    }

    @Throws(Exception::class)
    private fun launchCore(service: Service, vpnInterface: ParcelFileDescriptor?, isReload: Boolean = false) {
        val guid = MmkvManager.getSelectServer() ?: error("No server selected")
        val config = MmkvManager.decodeServerConfig(guid) ?: error("Failed to decode server config")

        LogUtil.i(AppConfig.TAG, "StartCore-Manager: Starting core loop for ${config.remarks}")
        val result = CoreConfigManager.getV2rayConfig(service, guid)
        LogUtil.d(AppConfig.TAG, result.content)
        if (!result.status) {
            error(result.errorMessage.ifBlank { "Failed to get V2Ray config" })
        }

        currentConfig = config
        var tunFd = vpnInterface?.fd ?: 0
        val dialerMode = BrowserDialerMode.from(config.browserDialerMode)
        val dialerAddr = if (dialerMode != null) {
            "127.0.0.1:${Utils.findRandomFreePort()}"
        } else {
            ""
        }
        if (SettingsManager.isUsingHevTun()) {
            tunFd = 0
        }

        NotificationManager.showNotification(currentConfig)
        if (dialerAddr.isNotNullEmpty()) {
            CoreNativeManager.reconcileBrowserDialer(dialerAddr)
        }
        coreController.startLoop(result.content, tunFd)

        if (!isRunning()) {
            error("Core failed to start")
        }

        if (browserDialer != null) {
            browserDialer!!.stop()
            browserDialer = null
        }
        when (dialerMode) {
            BrowserDialerMode.OKHTTP -> {
                browserDialer = DialerNativeService()
                browserDialer!!.start(service, dialerAddr)
            }

            BrowserDialerMode.WEBVIEW -> {
                browserDialer = DialerWebviewService()
                browserDialer!!.start(service, dialerAddr)
            }

            else -> {}
        }

        if (!isReload) {
            MessageHelper.sendMsg2UI(service, AppConfig.MSG_STATE_START_SUCCESS, "")
        }
        // v1.1.14: the VPN 5G flow always shows live tunnel speeds.
        NotificationManager.startSpeedNotification(force = true)
        LogUtil.i(AppConfig.TAG, "StartCore-Manager: Core started successfully")
        // v1.1.29: the daemon watchdog owns drop detection for VPN 5G
        // persistent sessions from here on (no-op when already running,
        // e.g. across a reload).
        startDaemonWatchdog()
        // v1.1.33: keep the hot standby queue fresh from the daemon
        // too — the UI standby job dies with the UI process, and a
        // drop after that must still switch onto a probed-alive
        // server (Aref: «همیشه چندین کانفینگ پینگ خوب در انتظار باشد»).
        startDaemonStandby()
        // v1.1.32: arm the session-resurrection backstop — if the
        // whole daemon process is killed (recents swipe on an
        // aggressive OEM that never delivers onTaskRemoved), a
        // periodic alarm restarts the service while the session is
        // persistent. The chain self-terminates once the session
        // ends, so arming here is safe for every consumer.
        SessionResurrection.arm(service)
    }

    /**
     * Stops the V2Ray core service.
     * Unregisters broadcast receivers, stops notifications, and shuts down plugins.
     * @return True if the core was stopped successfully, false otherwise.
     */
    fun stopCoreLoop(): Boolean {
        // v1.1.29: teardown prevents new work first — the daemon
        // watchdog (and any in-flight daemon failover) is cancelled
        // before anything else, so a user stop, the 30-minute timer
        // or a revoke can never be fought by a failover restart.
        // The heartbeat is zeroed so the UI backup knows at once
        // that the daemon no longer owns detection.
        daemonWatchdogJob?.cancel()
        daemonWatchdogJob = null
        daemonStandbyJob?.cancel()
        daemonStandbyJob = null
        daemonWatchdogScore = 0.0
        daemonTriedGuids.clear()
        Vpn5gDaemonHeartbeat.writeHeartbeat(0L)
        // v1.1.33: capture the last interval's real tunnel bytes
        // before the core stops — the native counters die with it.
        flushTrafficStats()
        connectionTestScope.coroutineContext.cancelChildren()
        val service = getService() ?: return false

        networkMonitor?.unregister()
        networkMonitor = null
        currentVpnInterface = null

        if (isRunning()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    coreController.stopLoop()
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to stop V2Ray loop", e)
                }
            }
        }

        // Close existing browser dialer
        CoreNativeManager.reconcileBrowserDialer("")
        if (browserDialer != null) {
            browserDialer!!.stop()
            browserDialer = null
        }

        MessageHelper.sendMsg2UI(service, AppConfig.MSG_STATE_STOP_SUCCESS, "")
        NotificationManager.cancelNotification()

        try {
            service.unregisterReceiver(mMsgReceive)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to unregister receiver", e)
        }

        return true
    }

    /**
     * Subscribes to upstream network changes for whichever run mode is active.
     * All three services share this manager, so the tunnel recovers from a handover in proxy only
     * and root mode as well, not just behind the VPN interface.
     */
    private fun startNetworkMonitor(service: Service) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        if (networkMonitor != null) return

        val connectivity = service.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        networkMonitor = NetworkMonitor(
            connectivity = connectivity,
            onUnderlyingNetworksChanged = { networks -> serviceControl?.get()?.setUnderlyingNetworks(networks) },
            onHandover = { reloadCore() },
        ).also { it.register() }
    }

    /**
     * Restarts the core in place after the upstream network changed: the service, the notification
     * and the VPN interface all stay up, so nothing of this is visible.
     *
     * The config is rebuilt on purpose, outbound server domains are resolved while building it and
     * an address resolved on a network that is gone can be unusable on the new one.
     *
     * @return True if the core is running again.
     */
    private fun reloadCore(): Boolean {
        if (isReloading) return false
        val service = getService() ?: return false
        if (!isRunning()) return false

        return try {
            val tunFd = currentVpnInterface

            isReloading = true
            connectionTestScope.coroutineContext.cancelChildren()
            LogUtil.i(AppConfig.TAG, "StartCore-Manager: Core reload start...")

            flushTrafficStats()
            coreController.stopLoop()
            launchCore(service, tunFd, isReload = true)

            LogUtil.i(AppConfig.TAG, "StartCore-Manager: Core reload finished")
            true
        } catch (e: Exception) {
            val message = e.message?.takeUnless { it.isBlank() } ?: e.javaClass.simpleName
            LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to reload core: $message", e)
            MessageHelper.sendMsg2UI(service, AppConfig.MSG_STATE_START_FAILURE, message)
            false
        } finally {
            isReloading = false
        }
    }

    /**
     * Final traffic-stats flush (v1.1.33): one last native-counter
     * read before the core stops, so the bytes since the previous
     * ~1 s round land in the real volume counters instead of
     * dying with the core. Delegates to the single querier
     * ([NotificationManager.flushStatsOnce]); a no-op when the
     * core is not running. Never throws.
     */
    private fun flushTrafficStats() {
        if (!isRunning()) return
        runCatching { NotificationManager.flushStatsOnce() }
            .onFailure { e ->
                LogUtil.e(AppConfig.TAG, "StartCore-Manager: traffic flush failed", e)
            }
    }

    /**
     * Queries and resets all outbound traffic counters in one core call.
     * Go side format: tag,direction,value;tag,direction,value;
     */
    fun queryAllOutboundTrafficStats(): List<OutboundTrafficStat> {
        // The stats manager is gone once the core stops, querying it then reaches into freed state.
        if (!isRunning()) return emptyList()

        val payload = coreController.queryAllOutboundTrafficStats()

        val result = ArrayList<OutboundTrafficStat>()

        payload.split(';').forEach { entry ->
            if (entry.isBlank()) return@forEach

            val parts = entry.split(',', limit = 3)
            if (parts.size != 3) return@forEach

            val value = parts[2].toLongOrNull() ?: return@forEach

            result.add(
                OutboundTrafficStat(
                    tag = parts[0],
                    direction = parts[1],
                    value = value,
                )
            )
        }
//        LogUtil.d(AppConfig.TAG, "Queried outbound traffic stats: $result")
        return result
    }

    /**
     * Measures the connection delay for the current V2Ray configuration.
     * Tests with primary URL first, then falls back to alternative URL if needed.
     * Also fetches remote IP information if the delay test was successful.
     */
    private fun measureV2rayDelay(requestId: String) {
        val service = getService() ?: return
        if (!isRunning() || isReloading) {
            MessageHelper.sendMsg2UI(service, AppConfig.MSG_MEASURE_DELAY_CANCEL, "", requestId)
            return
        }

        connectionTestScope.coroutineContext.cancelChildren()
        connectionTestScope.launch {
            var time = -1L
            var errorStr = ""

            try {
                time = coreController.measureDelay(SettingsManager.getDelayTestUrl())
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to measure delay", e)
                errorStr = e.message?.substringAfter("\":").orEmpty()
            }
            if (time == -1L) {
                ensureActive()
                try {
                    time = coreController.measureDelay(SettingsManager.getDelayTestUrl(true))
                } catch (e: Exception) {
                    LogUtil.e(AppConfig.TAG, "StartCore-Manager: Failed to measure delay", e)
                    errorStr = e.message?.substringAfter("\":").orEmpty()
                }
            }

            ensureActive()
            val initialResult = ConnectionTestResult(
                delayMillis = time,
                errorMessage = errorStr,
                country = null,
                ipAddress = null,
            )
            withContext(Dispatchers.Main.immediate) {
                if (isRunning()) {
                    MessageHelper.sendMsg2UI(service, AppConfig.MSG_MEASURE_DELAY_RESULT, initialResult, requestId)
                } else {
                    MessageHelper.sendMsg2UI(service, AppConfig.MSG_MEASURE_DELAY_CANCEL, "", requestId)
                    return@withContext
                }
            }

            if (time >= 0) {
                val endpoint = SpeedtestManager.getRemoteIPInfo()
                if (endpoint != null && (endpoint.country != null || endpoint.ipAddress != null)) {
                    ensureActive()
                    val fullResult = ConnectionTestResult(
                        delayMillis = time,
                        errorMessage = errorStr,
                        country = endpoint.country,
                        ipAddress = endpoint.ipAddress,
                    )
                    withContext(Dispatchers.Main.immediate) {
                        if (isRunning()) {
                            MessageHelper.sendMsg2UI(service, AppConfig.MSG_MEASURE_DELAY_RESULT, fullResult, requestId)
                        }
                    }
                }
            }
        }.invokeOnCompletion { cause ->
            if (cause is CancellationException) {
                MessageHelper.sendMsg2UI(service, AppConfig.MSG_MEASURE_DELAY_CANCEL, "", requestId)
            }
        }
    }

    // -----------------------------------------------------------------
    // Daemon watchdog (v1.1.29)
    // -----------------------------------------------------------------

    /**
     * Whether the daemon watchdog may act right now: only for a
     * VPN 5G persistent session (the flag the VPN 5G UI sets on
     * connect and clears on manual disconnect / timer end) in AUTO
     * mode (a manual pick is never silently replaced — the UI owns
     * manual failure). Every other consumer of this manager
     * (upstream UI, proxy-only, root) never sets the flag, so the
     * watchdog stays inert for them.
     */
    private fun daemonWatchdogGateOpen(): Boolean =
        Vpn5gConnectionPersistence.isPersistentConnection() &&
            MmkvManager.decodeSettingsString(Vpn5gConstants.KEY_MANUAL_SERVER_GUID)
                .isNullOrBlank()

    /** Starts the daemon watchdog loop if it is not already running. */
    private fun startDaemonWatchdog() {
        if (daemonWatchdogJob?.isActive == true) return
        daemonWatchdogScore = 0.0
        daemonWatchdogJob = daemonWatchdogScope.launch {
            while (isActive) {
                delay(Vpn5gConstants.WATCHDOG_PROBE_INTERVAL_TICKS * 1_000L)
                if (!isActive) break
                if (!isRunning() || isReloading) continue
                if (!daemonWatchdogGateOpen()) continue
                Vpn5gDaemonHeartbeat.writeHeartbeat()
                if (daemonBaselineIp == null) {
                    daemonBaselineIp = Vpn5gTunnelCheck.publicIpDirect()
                }
                val first = Vpn5gTunnelCheck.probeOnce(daemonBaselineIp)
                daemonWatchdogScore = Vpn5gWatchdogScore.nextScore(
                    daemonWatchdogScore, first.verdict
                )
                if (Vpn5gWatchdogScore.needsConfirm(daemonWatchdogScore)) {
                    delay(Vpn5gConstants.WATCHDOG_CONFIRM_DELAY_MS)
                    if (!isActive || !isRunning() || isReloading) continue
                    val confirm = Vpn5gTunnelCheck.probeOnce(daemonBaselineIp)
                    daemonWatchdogScore = Vpn5gWatchdogScore.nextScore(
                        daemonWatchdogScore, confirm.verdict
                    )
                }
                Vpn5gDaemonHeartbeat.writeHeartbeat()
                if (Vpn5gWatchdogScore.shouldDrop(daemonWatchdogScore)) {
                    daemonWatchdogScore = 0.0
                    daemonFailover()
                }
            }
        }
    }

    /**
     * Daemon failover: pick the next server from MMKV, restart the
     * core onto it in place (same stop/start discipline as
     * [reloadCore] — the service, TUN and notification stay up),
     * verify through the tunnel, and repeat until a tunnel
     * verifies or the watchdog job is cancelled by a stop. There
     * is no give-up while the job lives: the switch is ON, so the
     * session must recover or keep trying at capped backoff.
     */
    private suspend fun daemonFailover() {
        val service = getService() ?: return
        val droppedGuid = MmkvManager.getSelectServer()
        if (droppedGuid != null) {
            daemonTriedGuids += droppedGuid
            markDaemonFailed(droppedGuid)
        }
        Vpn5gDaemonHeartbeat.writeFailoverStart()
        LogUtil.i(
            AppConfig.TAG,
            "StartCore-Manager: daemon watchdog drop (mode=VPN phase=failover guid=$droppedGuid)",
        )
        var round = 0
        while (daemonWatchdogJob?.isActive == true && getService() != null) {
            if (!daemonWatchdogGateOpen()) return
            val next = daemonPickNext()
            if (next == null) {
                // No candidate at all: refresh the stored list (fast,
                // real-probe skipped — the live core must not get a
                // throwaway sibling) and retry at growing backoff.
                delay(Vpn5gAutoConnect.failoverRetryBackoffMs(round))
                round++
                if (daemonWatchdogJob?.isActive != true) return
                runCatching {
                    Vpn5gSync.refresh(service, skipRealProbe = true)
                }.onFailure {
                    LogUtil.e(AppConfig.TAG, "StartCore-Manager: daemon failover refresh failed", it)
                }
                continue
            }
            daemonTriedGuids += next
            Vpn5gDaemonHeartbeat.writePendingFailoverGuid(next)
            Vpn5gDaemonHeartbeat.writeFailoverStart()
            MmkvManager.setSelectServer(next)
            val restarted = daemonRestartOnto(next)
            if (!restarted) {
                markDaemonFailed(next)
                continue
            }
            // Let the UI refresh its selection/state onto the new server.
            MessageHelper.sendMsg2UI(service, AppConfig.MSG_STATE_START_SUCCESS, "")
            if (daemonVerifyTunnel()) {
                Vpn5gDaemonHeartbeat.clearPendingFailoverGuid()
                daemonTriedGuids.clear()
                daemonWatchdogScore = 0.0
                runCatching {
                    MmkvManager.decodeServerConfig(next)?.let { profile ->
                        Vpn5gHealthScore.recordOutcome(
                            Vpn5gServerValidator.dedupeKey(profile), success = true
                        )
                    }
                }
                LogUtil.i(
                    AppConfig.TAG,
                    "StartCore-Manager: daemon failover verified (mode=VPN phase=failover guid=$next)",
                )
                return
            }
            markDaemonFailed(next)
            runCatching {
                MmkvManager.decodeServerConfig(next)?.let { profile ->
                    Vpn5gHealthScore.recordOutcome(
                        Vpn5gServerValidator.dedupeKey(profile), success = false
                    )
                }
            }
        }
    }

    /**
     * Restarts the core in place onto the already-selected server
     * (daemon failover): stop, wait for the actual stop (bounded,
     * same discipline as the MSG_STATE_RESTART path), start again
     * with the existing TUN. True when the core is running again.
     */
    private suspend fun daemonRestartOnto(guid: String): Boolean {
        val service = getService() ?: return false
        return try {
            isReloading = true
            connectionTestScope.coroutineContext.cancelChildren()
            flushTrafficStats()
            coreController.stopLoop()
            val stopDeadline = System.currentTimeMillis() + RESTART_STOP_WAIT_MS
            while (shouldKeepWaitingForCoreStop(
                    isRunning(), System.currentTimeMillis(), stopDeadline
                )
            ) {
                delay(RESTART_STOP_POLL_MS)
            }
            if (daemonWatchdogJob?.isActive != true) return false
            launchCore(service, currentVpnInterface, isReload = true)
            isRunning()
        } catch (e: Exception) {
            LogUtil.e(
                AppConfig.TAG,
                "StartCore-Manager: daemon failover restart failed (mode=VPN phase=failover guid=$guid)",
                e,
            )
            false
        } finally {
            isReloading = false
        }
    }

    /**
     * Post-restart daemon verification: probe through the tunnel
     * until VERIFIED or the fast verify budget runs out. UNKNOWN
     * (no local proxy port yet — the new core may still be
     * binding) keeps probing inside the budget instead of failing
     * the candidate at once.
     */
    private suspend fun daemonVerifyTunnel(): Boolean {
        val deadline = System.currentTimeMillis() + Vpn5gAutoConnect.FAST_VERIFY_TIMEOUT_MS
        while (daemonWatchdogJob?.isActive == true &&
            System.currentTimeMillis() < deadline
        ) {
            if (!isRunning()) return false
            val result = Vpn5gTunnelCheck.probeOnce(daemonBaselineIp)
            if (result.verdict == Vpn5gTunnelCheck.Verdict.VERIFIED) return true
            delay(500L)
        }
        return false
    }

    /**
     * Builds the daemon candidate list from MMKV (stored VPN 5G
     * profiles + stored delays + real-ping identities + health
     * scores). Shared by the failover pick and the standby
     * liveness job (v1.1.33).
     */
    private fun daemonCandidates(): List<Vpn5gDaemonPick.Candidate> {
        val subId = runCatching { Vpn5gSync.ensureSubscriptionId() }.getOrNull()
            ?: return emptyList()
        val realKeys = Vpn5gRealPing.loadRealPingKeys()
        val healthScores = Vpn5gHealthScore.currentScores()
        return MmkvManager.decodeServerList(subId).mapNotNull { guid ->
            val profile = MmkvManager.decodeServerConfig(guid) ?: return@mapNotNull null
            val key = Vpn5gServerValidator.dedupeKey(profile)
            val delay = MmkvManager.decodeServerAffiliationInfo(guid)?.testDelayMillis ?: 0L
            Vpn5gDaemonPick.Candidate(
                guid = guid,
                delayMs = delay,
                hasRealPing = key in realKeys,
                adjustedDelayMs = Vpn5gHealthScore.adjustedPing(
                    delay, healthScores[key] ?: 0.0
                ),
                // v1.1.30: country from the remark (synchronous,
                // no I/O) so the daemon pick exhausts one country
                // before the next, like the UI pick.
                countryCode = Vpn5gGeoIp.countryFromRemark(profile.remarks)?.code ?: "",
            )
        }
    }

    /**
     * Picks the next daemon failover target from [daemonCandidates]
     * (v1.1.33: the persisted standby queue is tier 0 — a
     * just-probed-alive server beats a stale stored delay). Null
     * only when every stored profile is excluded.
     */
    private fun daemonPickNext(): String? {
        val candidates = daemonCandidates()
        if (candidates.isEmpty()) return null
        val failed = Vpn5gFailedServers.prune(
            Vpn5gFailedServers.load(), System.currentTimeMillis()
        )
        return Vpn5gDaemonPick.pickNext(
            candidates = candidates,
            excluding = daemonTriedGuids,
            failedGuids = failed.keys,
            standbyQueue = Vpn5gStandbyLiveness.loadQueue(),
        )?.guid
    }

    /**
     * Daemon standby liveness (v1.1.33): while a persistent AUTO
     * session runs, probe the top standby candidates directly
     * (TCP+TLS, no second core — this package is excluded from
     * the tunnel, so the socket measures the server from the
     * device network) and keep the pre-ranked replacement queue
     * persisted in MMKV. The UI standby job does the same while
     * the UI process lives; this daemon copy keeps the queue
     * fresh with the screen off / UI dead, so the daemon
     * failover switches onto a server probed alive seconds ago
     * instead of one whose stored delay is minutes old. Alive
     * results also refresh the stored test delays. Child of
     * [daemonWatchdogScope]; cancelled in [stopCoreLoop].
     */
    private fun startDaemonStandby() {
        if (daemonStandbyJob?.isActive == true) return
        daemonStandbyJob = daemonWatchdogScope.launch {
            while (isActive) {
                delay(Vpn5gConstants.STANDBY_LIVENESS_INTERVAL_MS)
                if (!isActive) break
                if (!isRunning() || isReloading) continue
                if (!daemonWatchdogGateOpen()) continue
                runCatching { daemonStandbyRound() }
                    .onFailure { e ->
                        LogUtil.e(
                            AppConfig.TAG,
                            "StartCore-Manager: daemon standby round failed",
                            e,
                        )
                    }
            }
        }
    }

    /** One daemon standby probe round (see [startDaemonStandby]). */
    private suspend fun daemonStandbyRound() {
        val currentGuid = MmkvManager.getSelectServer()
        val candidates = daemonCandidates()
            .filter { it.guid != currentGuid && it.guid !in daemonTriedGuids }
            .sortedWith(compareBy({ !it.hasRealPing }, { it.adjustedDelayMs }))
            .take(Vpn5gConstants.STANDBY_PROBE_K)
        if (candidates.isEmpty()) return
        val results = coroutineScope {
            candidates.map { candidate ->
                async {
                    val profile = MmkvManager.decodeServerConfig(candidate.guid)
                    if (profile == null) {
                        Vpn5gStandbyLiveness.Result(candidate.guid, -1L, false)
                    } else {
                        Vpn5gStandbyLiveness.probe(candidate.guid, profile)
                    }
                }
            }.awaitAll()
        }
        Vpn5gStandbyLiveness.persistQueue(Vpn5gStandbyLiveness.rankQueue(results))
        // Refresh stored delays for probed-alive servers so every
        // pick (daemon tiers, UI list) orders by fresh numbers.
        results.filter { it.alive }.forEach { result ->
            runCatching {
                MmkvManager.encodeServerTestDelayMillis(result.guid, result.tcpDelayMs)
            }
        }
    }

    /** Records a daemon-side tunnel failure in the 6-hour failed store. */
    private fun markDaemonFailed(guid: String) {
        runCatching {
            val failed = Vpn5gFailedServers.prune(
                Vpn5gFailedServers.load(), System.currentTimeMillis()
            )
            Vpn5gFailedServers.save(
                Vpn5gFailedServers.mark(failed, guid, System.currentTimeMillis())
            )
        }
    }

    /**
     * Gets the current service instance.
     * @return The current service instance, or null if not available.
     */
    private fun getService(): Service? {
        return serviceControl?.get()?.getService()
    }

    /**
     * Core callback handler implementation for handling V2Ray core events.
     * Handles startup, shutdown, socket protection, and status emission.
     */
    private class CoreCallback : CoreCallbackHandler {
        /**
         * Called when V2Ray core starts up.
         * @return 0 for success, any other value for failure.
         */
        override fun startup(): Long {
            LogUtil.i(AppConfig.TAG, "StartCore-Manager: CoreCallback startup")
            return 0
        }

        /**
         * Called when V2Ray core shuts down.
         * @return 0 for success, any other value for failure.
         */
        override fun shutdown(): Long {
            LogUtil.i(AppConfig.TAG, "StartCore-Manager: CoreCallback shutdown")
            return 0
        }

        /**
         * Called when V2Ray core emits status information.
         * @param l Status code.
         * @param s Status message.
         * @return Always returns 0.
         */
        override fun onEmitStatus(l: Long, s: String?): Long {
            LogUtil.i(AppConfig.TAG, "StartCore-Manager: CoreCallback onEmitStatus $s")
            return 0
        }
    }

    /**
     * Process finder implementation for Xray core.
     * Uses ConnectivityManager to find the owning UID of a connection based on network parameters.
     */
    private class XrayProcessFinder(context: Context) : ProcessFinder {
        private val cm: ConnectivityManager? = context.getSystemService(ConnectivityManager::class.java)

        override fun findProcessByConnection(network: String, srcIP: String, srcPort: Long, destIP: String, destPort: Long): Long {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return -1L
            if (cm == null) return -1L
            val proto = when (network) {
                "tcp" -> OsConstants.IPPROTO_TCP
                "udp" -> OsConstants.IPPROTO_UDP
                else -> return -1L
            }

            if (destIP.isBlank() || destPort == 0L) {
                LogUtil.d(AppConfig.TAG, "ProcessFinder: Find $network connection from $srcIP:$srcPort to :$destPort, (no dest)")
                return -1L
            }

            return try {
                val uid = cm.getConnectionOwnerUid(
                    proto,
                    InetSocketAddress(srcIP, srcPort.toInt()),
                    InetSocketAddress(destIP, destPort.toInt())
                ).toLong()
                LogUtil.d(AppConfig.TAG, "ProcessFinder: Find $network connection from $srcIP:$srcPort to $destIP:$destPort, uid=$uid")
                //LogUtil.d(AppConfig.TAG, "ProcessFinder: Find $network connection from $srcIP:$srcPort to $destIP:$destPort, uid=$uid,${PackageUidResolver.uidToPackageName(uid.toString())}")

                uid
            } catch (_: Exception) {
                -1L
            }
        }
    }

    /**
     * Broadcast receiver for handling messages sent to the service.
     * Handles registration, service control, and screen events.
     */
    private class ReceiveMessageHandler : BroadcastReceiver() {
        /**
         * Handles received broadcast messages.
         * Processes service control messages and screen state changes.
         * @param ctx The context in which the receiver is running.
         * @param intent The intent being received.
         */
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val serviceControl = serviceControl?.get() ?: return
            when (intent?.getIntExtra("key", 0)) {
                AppConfig.MSG_REGISTER_CLIENT -> {
                    if (isRunning()) {
                        MessageHelper.sendMsg2UI(serviceControl.getService(), AppConfig.MSG_STATE_RUNNING, "")
                    } else {
                        MessageHelper.sendMsg2UI(serviceControl.getService(), AppConfig.MSG_STATE_NOT_RUNNING, "")
                    }
                }

                AppConfig.MSG_UNREGISTER_CLIENT -> {
                    // nothing to do
                }

                AppConfig.MSG_STATE_START -> {
                    // nothing to do
                }

                AppConfig.MSG_STATE_STOP -> {
                    LogUtil.i(AppConfig.TAG, "StartCore-Manager: Stop service")
                    serviceControl.stopService()
                }

                AppConfig.MSG_STATE_RESTART -> {
                    LogUtil.i(AppConfig.TAG, "StartCore-Manager: Restart service")
                    // The UI and daemon run in separate processes, so acknowledge the active
                    // daemon before stopping it instead of relying on possibly stale UI state.
                    if (isOrderedBroadcast) resultCode = Activity.RESULT_OK

                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.Default).launch {
                        try {
                            // v1.1.33 (seamless failover): when this
                            // restart IS a failover onto the persisted
                            // pending target and the TUN is up, restart
                            // the core IN PLACE (same TUN, same service
                            // — the daemon failover discipline) instead
                            // of tearing the whole service down. The
                            // user-visible gap shrinks to the core
                            // stop/start; the tunnel interface never
                            // disappears. Any other restart (settings,
                            // notification button, manual switch) keeps
                            // the full stop/start below, which also
                            // re-applies VPN setup.
                            val pendingGuid = Vpn5gDaemonHeartbeat.pendingFailoverGuid()
                            val selected = MmkvManager.getSelectServer()
                            val inPlaceFailover = pendingGuid.isNotBlank() &&
                                pendingGuid == selected &&
                                currentVpnInterface != null &&
                                isRunning() &&
                                daemonWatchdogJob?.isActive == true
                            if (inPlaceFailover && daemonRestartOnto(pendingGuid)) {
                                Vpn5gDaemonHeartbeat.clearPendingFailoverGuid()
                                MessageHelper.sendMsg2UI(
                                    serviceControl.getService(),
                                    AppConfig.MSG_STATE_START_SUCCESS,
                                    "",
                                )
                                return@launch
                            }
                            serviceControl.stopService()
                            // v1.1.13: wait for the core to ACTUALLY stop
                            // instead of a fixed 500 ms — starting the new
                            // core while the old one is still stopping is
                            // refused with "Core already running" and
                            // wedges the daemon flag until a force-stop
                            // clears it. The wait is bounded: if the core
                            // still has not stopped at the deadline, the
                            // start proceeds exactly as before.
                            val stopDeadline =
                                System.currentTimeMillis() + RESTART_STOP_WAIT_MS
                            while (shouldKeepWaitingForCoreStop(
                                    isRunning(),
                                    System.currentTimeMillis(),
                                    stopDeadline,
                                )
                            ) {
                                delay(RESTART_STOP_POLL_MS)
                            }
                            LauncherManager.startService(serviceControl.getService())
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }

                AppConfig.MSG_MEASURE_DELAY -> {
                    if (isOrderedBroadcast) resultCode = Activity.RESULT_OK
                    measureV2rayDelay(intent.getStringExtra("content").orEmpty())
                }
            }

            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    LogUtil.i(AppConfig.TAG, "StartCore-Manager: Screen off")
                    NotificationManager.stopSpeedNotification()
                }

                Intent.ACTION_SCREEN_ON -> {
                    LogUtil.i(AppConfig.TAG, "StartCore-Manager: Screen on")
                    NotificationManager.startSpeedNotification(force = true)
                }
            }
        }
    }
}

/**
 * Pure restart-wait decision (v1.1.13): keep polling while the core is
 * still running AND the stop deadline has not passed. A stopped core
 * ends the wait at once; an expired deadline ends it even if the core
 * is still stopping (the start then proceeds as before). Pure, so JVM
 * tests drive it without the daemon. Kept top-level — not a member of
 * [CoreServiceManager] — so tests can call it without initializing
 * the core controller.
 */
internal fun shouldKeepWaitingForCoreStop(
    isRunning: Boolean,
    nowMs: Long,
    deadlineMs: Long,
): Boolean = isRunning && nowMs < deadlineMs
