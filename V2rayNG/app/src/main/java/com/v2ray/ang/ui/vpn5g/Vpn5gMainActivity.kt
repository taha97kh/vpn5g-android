package com.v2ray.ang.ui.vpn5g

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.AppConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.vpn5g.PromoCodeRemote
import com.v2ray.ang.handler.vpn5g.SubscriptionStatusRemote
import com.v2ray.ang.handler.vpn5g.Vpn5gAuthService
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.BOT_URL
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.CONFIG_SCHEMES
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.KEY_PROTOCOL_FILTER
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.MAX_AUTO_FAILOVER_ATTEMPTS
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.MAX_TUNNEL_ATTEMPTS
import com.v2ray.ang.handler.vpn5g.Vpn5gCountryUi
import com.v2ray.ang.handler.vpn5g.Vpn5gGeoIp
import com.v2ray.ang.handler.vpn5g.Vpn5gPromoCodes
import com.v2ray.ang.handler.vpn5g.Vpn5gProtocol
import com.v2ray.ang.handler.vpn5g.Vpn5gRemoteConfig
import com.v2ray.ang.handler.vpn5g.Vpn5gServerUi
import com.v2ray.ang.handler.vpn5g.Vpn5gSync
import com.v2ray.ang.handler.vpn5g.Vpn5gTimer
import com.v2ray.ang.handler.vpn5g.Vpn5gTunnelCheck
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.ui.base.HelperBaseComponentActivity
import com.v2ray.ang.ui.compose.colorPing
import com.v2ray.ang.ui.compose.colorPingRed
import com.v2ray.ang.ui.main.MainActivity
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Vpn5gDestination {
    HOME, PREMIUM, BROWSER, ROUTING, ME, AUTH, PROTOCOL
}

/** Server-list tabs on the home screen (X-VPN server-list style). */
enum class Vpn5gServerTab { FREE, ALL, SPECIAL }

/** Post-connect tunnel state. The UI must never show a false "connected". */
enum class Vpn5gTunnelState { IDLE, VERIFYING, VERIFIED, FAILED, UNKNOWN }

enum class Vpn5gPromoStatus { IDLE, VALID, UNKNOWN_CODE, EXHAUSTED, ALREADY_REDEEMED, INVALID }

data class Vpn5gHomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isRunning: Boolean = false,
    val offline: Boolean = false,
    val notice: String? = null,
    val countries: List<Vpn5gCountryUi> = emptyList(),
    val selectedGuid: String? = null,
    val currentServerName: String? = null,
    val currentPingMillis: Long = 0L,
    val remainingMillis: Long? = null,
    val subscriptionStatus: SubscriptionStatusRemote? = null,
    val errorMessage: String? = null,
    val tunnelState: Vpn5gTunnelState = Vpn5gTunnelState.IDLE,
    val tunnelMessage: String? = null,
    val verifiedExitIp: String? = null,
    val verifiedExitFlag: String = "",
    val signedInEmail: String? = null,
    val promoCodes: List<PromoCodeRemote>? = null,
    val promoInput: String = "",
    val promoStatus: Vpn5gPromoStatus = Vpn5gPromoStatus.IDLE,
    val promoBotLink: String? = null,
    /** Selected protocol filter id ("auto", "vless", "vmess", "trojan", "ss"). */
    val protocolFilter: String = Vpn5gProtocol.AUTO,
    /** Brief auto-failover notice ("switched to server X"); auto-dismissed. */
    val autoFailoverNotice: String? = null,
)

/**
 * Home-screen ViewModel: owns sync, running state (via the daemon broadcast,
 * same channel as MainRepository), the 5-minute auto-refresh ticker and the
 * 1-second session-countdown ticker, plus post-connect tunnel verification
 * with bounded auto-failover to the next best server. All network/disk work
 * runs on Dispatchers.IO; Compose only ever sees immutable StateFlow state.
 */
class Vpn5gHomeViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(Vpn5gHomeUiState())
    val uiState: StateFlow<Vpn5gHomeUiState> = _uiState.asStateFlow()

    private val _failoverEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val failoverEvent: SharedFlow<String> = _failoverEvent.asSharedFlow()

    private var verifyJob: Job? = null
    private val triedGuids = mutableSetOf<String>()
    private var verifyAttempts = 0
    private var tunnelFailedStop = false
    private var baselineIp: String? = null

    // Auto-failover on an unexpected drop (v1.1): when the service stops
    // without the user asking, without the 30-minute timer ending, and
    // without a tunnel-verification failure, the app reconnects to the
    // best server in another country (bounded attempts) and shows a notice.
    private var userStopRequested = false
    private var suppressAutoFailover = false
    private var sessionActive = false
    private var sessionStartedAtMs = 0L
    private var droppedCountry: String? = null
    private var autoFailoverAttempts = 0
    private var autoFailoverPending = false
    private var failoverTargetName: String? = null
    private var autoFailoverJob: Job? = null

    private val serviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val safeIntent = intent ?: return
            when (safeIntent.getIntExtra("key", 0)) {
                AppConfig.MSG_STATE_RUNNING,
                AppConfig.MSG_STATE_START_SUCCESS -> onRunningChanged(true)
                AppConfig.MSG_STATE_NOT_RUNNING,
                AppConfig.MSG_STATE_STOP_SUCCESS -> onRunningChanged(false)
                AppConfig.MSG_STATE_START_FAILURE -> {
                    onRunningChanged(false)
                    _uiState.update {
                        it.copy(errorMessage = safeIntent.getStringExtra("content"))
                    }
                }
            }
        }
    }

    init {
        ContextCompat.registerReceiver(
            application,
            serviceReceiver,
            IntentFilter(AppConfig.BROADCAST_ACTION_ACTIVITY),
            Utils.receiverFlags(),
        )
        MessageHelper.sendMsg2Service(application, AppConfig.MSG_REGISTER_CLIENT, "")

        // Show the last stored list immediately; the bundled snapshot fills
        // a first launch so the list is never empty on entry; the refresh
        // replaces it with live data.
        viewModelScope.launch(Dispatchers.IO) {
            val cached = Vpn5gRemoteConfig.cached()
            val free = cached.free_countries?.takeIf { it.isNotEmpty() }
                ?: Vpn5gConstants.DEFAULT_FREE_COUNTRIES
            var countries = Vpn5gSync.loadGroupedServers(free)
            var usedFallback = false
            if (countries.isEmpty()) {
                val imported = Vpn5gSync.loadBundledFallback(getApplication())
                if (imported > 0) {
                    countries = Vpn5gSync.loadGroupedServers(free)
                    usedFallback = true
                }
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    countries = countries,
                    subscriptionStatus = cached.subscription_status,
                    notice = cached.notice,
                    promoCodes = cached.codes,
                    signedInEmail = Vpn5gAuthService.currentSession()?.email,
                    protocolFilter = Vpn5gProtocol.normalize(
                        MmkvManager.decodeSettingsString(KEY_PROTOCOL_FILTER)
                    ),
                    offline = usedFallback,
                )
            }
            refreshSelection()
            enrichCountries()
        }

        // Auto-refresh on entry (and every 5 minutes while open).
        viewModelScope.launch { refresh() }

        viewModelScope.launch {
            while (isActive) {
                delay(5 * 60_000L)
                refresh()
            }
        }

        // Baseline public IP for the tunnel check (background, best effort).
        viewModelScope.launch(Dispatchers.IO) {
            baselineIp = Vpn5gTunnelCheck.publicIpDirect()
        }

        // Live 30-minute countdown, once a second while connected.
        viewModelScope.launch {
            while (isActive) {
                delay(1_000L)
                if (_uiState.value.isRunning) {
                    _uiState.update {
                        it.copy(remainingMillis = Vpn5gTimer.remainingMillis())
                    }
                }
            }
        }
    }

    override fun onCleared() {
        verifyJob?.cancel()
        runCatching {
            MessageHelper.sendMsg2Service(getApplication(), AppConfig.MSG_UNREGISTER_CLIENT, "")
        }.onFailure {
            LogUtil.e(AppConfig.TAG, "Vpn5gHomeViewModel: unregister failed", it)
        }
        runCatching {
            getApplication<Application>().unregisterReceiver(serviceReceiver)
        }.onFailure {
            LogUtil.e(AppConfig.TAG, "Vpn5gHomeViewModel: unregister receiver failed", it)
        }
        super.onCleared()
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                val result = Vpn5gSync.refresh()
                val cached = Vpn5gRemoteConfig.cached()
                val free = cached.free_countries?.takeIf { it.isNotEmpty() }
                    ?: Vpn5gConstants.DEFAULT_FREE_COUNTRIES
                val countries = Vpn5gSync.loadGroupedServers(free)
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        isLoading = false,
                        offline = result.offline,
                        notice = result.notice ?: cached.notice,
                        countries = countries,
                        subscriptionStatus = cached.subscription_status,
                        promoCodes = cached.codes,
                        signedInEmail = Vpn5gAuthService.currentSession()?.email,
                    )
                }
                refreshSelection()
                enrichCountries()
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Vpn5gHomeViewModel: refresh failed", e)
                _uiState.update { it.copy(isRefreshing = false, offline = true) }
            }
        }
    }

    /** Fills in flags/country names for servers whose remark had none. */
    private suspend fun enrichCountries() {
        val app = getApplication<Application>()
        val enriched = Vpn5gSync.enrichCountries(app, _uiState.value.countries)
        val byGuid = enriched.flatMap { it.servers }.associateBy { it.guid }
        _uiState.update { state ->
            state.copy(
                countries = state.countries.map { section ->
                    section.copy(
                        servers = section.servers.map { server ->
                            val fresh = byGuid[server.guid]
                            if (fresh != null && fresh.countryCode.isNotEmpty()) {
                                server.copy(
                                    flag = fresh.flag,
                                    countryNameFa = fresh.countryNameFa,
                                    countryCode = fresh.countryCode,
                                )
                            } else {
                                server
                            }
                        }
                    )
                }
            )
        }
    }

    fun setProtocolFilter(id: String) {
        val normalized = Vpn5gProtocol.normalize(id)
        MmkvManager.encodeSettings(KEY_PROTOCOL_FILTER, normalized)
        _uiState.update { it.copy(protocolFilter = normalized) }
    }

    /** Best free server honoring the protocol filter (lowest ping, else first). */
    fun bestFreeServer(): Vpn5gServerUi? {
        val servers = Vpn5gProtocol.filterSections(
            _uiState.value.countries, _uiState.value.protocolFilter
        ).filter { it.isFree }.flatMap { it.servers }
        return servers.filter { it.pingMillis > 0 }.minByOrNull { it.pingMillis }
            ?: servers.firstOrNull()
    }

    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /** Called by the Activity before a manual connect/server tap. */
    fun resetTunnelAttempts() {
        resetForFailover()
        triedGuids.clear()
        verifyAttempts = 0
        autoFailoverAttempts = 0
        autoFailoverPending = false
        autoFailoverJob?.cancel()
        autoFailoverJob = null
        _uiState.update {
            it.copy(
                autoFailoverNotice = null,
            )
        }
    }

    /** Fresh verification state; keeps the auto-failover attempt counter. */
    private fun resetForFailover() {
        verifyJob?.cancel()
        verifyJob = null
        tunnelFailedStop = false
        _uiState.update {
            it.copy(
                tunnelState = Vpn5gTunnelState.IDLE,
                tunnelMessage = null,
                verifiedExitIp = null,
                verifiedExitFlag = "",
            )
        }
    }

    /** Manual disconnect from the power button: never auto-fail over. */
    fun userDisconnect() {
        userStopRequested = true
        autoFailoverJob?.cancel()
        autoFailoverJob = null
        resetTunnelAttempts()
    }

    /** An intentional restart is in progress (server switch / failover). */
    fun markRestarting() {
        suppressAutoFailover = true
    }

    fun consumeAutoFailoverNotice() {
        _uiState.update { it.copy(autoFailoverNotice = null) }
    }

    fun signOut() {
        Vpn5gAuthService.signOut()
        _uiState.update { it.copy(signedInEmail = null) }
    }

    fun setPromoInput(value: String) {
        _uiState.update { it.copy(promoInput = value, promoStatus = Vpn5gPromoStatus.IDLE, promoBotLink = null) }
    }

    /** Validates the entered promo code and records a redemption locally. */
    fun redeemPromo() {
        val input = _uiState.value.promoInput
        when (val result = Vpn5gPromoCodes.validate(input, _uiState.value.promoCodes)) {
            is Vpn5gPromoCodes.Validation.Valid -> {
                val link = Vpn5gPromoCodes.redeem(result.code)
                _uiState.update {
                    it.copy(promoStatus = Vpn5gPromoStatus.VALID, promoBotLink = link)
                }
            }
            Vpn5gPromoCodes.Validation.UnknownCode ->
                _uiState.update { it.copy(promoStatus = Vpn5gPromoStatus.UNKNOWN_CODE) }
            Vpn5gPromoCodes.Validation.Exhausted ->
                _uiState.update { it.copy(promoStatus = Vpn5gPromoStatus.EXHAUSTED) }
            Vpn5gPromoCodes.Validation.AlreadyRedeemed ->
                _uiState.update { it.copy(promoStatus = Vpn5gPromoStatus.ALREADY_REDEEMED) }
            Vpn5gPromoCodes.Validation.InvalidEntry ->
                _uiState.update { it.copy(promoStatus = Vpn5gPromoStatus.INVALID) }
        }
    }

    fun clearPromo() {
        _uiState.update {
            it.copy(promoStatus = Vpn5gPromoStatus.IDLE, promoBotLink = null, promoInput = "")
        }
    }

    private fun onRunningChanged(running: Boolean) {
        val app = getApplication<Application>()
        if (running) {
            // Start the 30-minute session clock once per session.
            if (Vpn5gTimer.connectedAt() == 0L) {
                Vpn5gTimer.scheduleDisconnect(app)
            }
            sessionActive = true
            sessionStartedAtMs = System.currentTimeMillis()
            userStopRequested = false
            suppressAutoFailover = false
            if (autoFailoverPending) {
                autoFailoverPending = false
                val target = failoverTargetName
                failoverTargetName = null
                if (target != null) {
                    _uiState.update {
                        it.copy(
                            autoFailoverNotice = app.getString(
                                R.string.vpn5g_autofailover_switched, target
                            ),
                        )
                    }
                }
            }
            // Verify the tunnel carries traffic (once per session; the job
            // survives failover restarts so attempts stay bounded).
            if (verifyJob?.isActive != true) startTunnelVerification()
        } else {
            Vpn5gTimer.cancelDisconnect(app)
            verifyJob?.cancel()
            verifyJob = null
            viewModelScope.launch(Dispatchers.IO) {
                baselineIp = Vpn5gTunnelCheck.publicIpDirect()
            }
            if (!tunnelFailedStop) {
                _uiState.update {
                    it.copy(
                        tunnelState = Vpn5gTunnelState.IDLE,
                        tunnelMessage = null,
                        verifiedExitIp = null,
                        verifiedExitFlag = "",
                    )
                }
            }
            // Unexpected drop? Schedule an auto-failover check (delayed, so
            // an intentional restart that comes back up cancels it). The
            // 30-minute timer ends the session at 30:00, so a stop after
            // ~29 minutes is treated as the timer, not a drop.
            val wasSession = sessionActive
            sessionActive = false
            val sessionAgeMs = if (sessionStartedAtMs > 0L) {
                System.currentTimeMillis() - sessionStartedAtMs
            } else {
                0L
            }
            val timerProbablyEnded = sessionAgeMs >= 29 * 60_000L
            val selectedGuid = MmkvManager.getSelectServer()
            droppedCountry = _uiState.value.countries
                .flatMap { it.servers }
                .firstOrNull { it.guid == selectedGuid }?.country
            val shouldFailover = !suppressAutoFailover && !userStopRequested &&
                !tunnelFailedStop && !timerProbablyEnded &&
                (wasSession || (autoFailoverPending &&
                    autoFailoverAttempts < MAX_AUTO_FAILOVER_ATTEMPTS))
            if (shouldFailover) {
                autoFailoverJob?.cancel()
                autoFailoverJob = viewModelScope.launch {
                    delay(3_000L)
                    if (!isActive) return@launch
                    if (_uiState.value.isRunning || userStopRequested ||
                        suppressAutoFailover
                    ) {
                        return@launch
                    }
                    attemptAutoFailover()
                }
            }
            tunnelFailedStop = false
        }
        _uiState.update {
            it.copy(
                isRunning = running,
                remainingMillis = if (running) Vpn5gTimer.remainingMillis() else null,
            )
        }
        viewModelScope.launch(Dispatchers.IO) { refreshSelection() }
    }

    /**
     * Auto-failover after an unexpected drop: reconnect to the best free
     * server in a DIFFERENT country than the one that dropped (lowest
     * ping, honoring the protocol filter), bounded to
     * [MAX_AUTO_FAILOVER_ATTEMPTS] attempts per session. Shows a brief
     * notice of the switch; gives up with a message when no server is left.
     */
    private fun attemptAutoFailover() {
        val app = getApplication<Application>()
        if (autoFailoverAttempts >= MAX_AUTO_FAILOVER_ATTEMPTS) {
            autoFailoverPending = false
            _uiState.update {
                it.copy(
                    autoFailoverNotice = app.getString(
                        R.string.vpn5g_autofailover_gave_up
                    ),
                )
            }
            return
        }
        val droppedGuid = MmkvManager.getSelectServer()
        val candidates = Vpn5gProtocol.filterSections(
            _uiState.value.countries, _uiState.value.protocolFilter
        )
            .filter { it.isFree }
            .flatMap { it.servers }
            .filter { it.guid != droppedGuid && it.pingMillis > 0 }
        val next = candidates
            .filter { it.country != droppedCountry }
            .minByOrNull { it.pingMillis }
            ?: candidates.minByOrNull { it.pingMillis }
        if (next == null) {
            autoFailoverPending = false
            _uiState.update {
                it.copy(
                    autoFailoverNotice = app.getString(
                        R.string.vpn5g_autofailover_gave_up
                    ),
                )
            }
            return
        }
        autoFailoverAttempts++
        autoFailoverPending = true
        failoverTargetName = next.name
        if (droppedGuid != null) triedGuids += droppedGuid
        resetForFailover()
        suppressAutoFailover = true
        _uiState.update {
            it.copy(
                autoFailoverNotice = app.getString(
                    R.string.vpn5g_autofailover_switching,
                    next.countryNameFa.ifBlank { next.country },
                ),
            )
        }
        viewModelScope.launch { _failoverEvent.emit(next.guid) }
    }

    /**
     * Post-connect verification loop. After the daemon reports running, the
     * exit IP is fetched through the tunnel and compared with the baseline:
     * a failed fetch or an unchanged exit IP means the server does not
     * tunnel, and the next best free server is tried (bounded attempts).
     * A server that fails verification is never left showing "connected".
     */
    private fun startTunnelVerification() {
        verifyJob?.cancel()
        verifyJob = viewModelScope.launch(Dispatchers.IO) {
            // verifyAttempts is a session field, not a local: a failover
            // restart may cancel and recreate this job, and the bound must
            // still hold for the whole session.
            while (isActive && verifyAttempts < MAX_TUNNEL_ATTEMPTS) {
                delay(2_500L)
                if (!_uiState.value.isRunning) return@launch
                val guid = MmkvManager.getSelectServer() ?: return@launch
                _uiState.update {
                    it.copy(tunnelState = Vpn5gTunnelState.VERIFYING, tunnelMessage = null)
                }
                val result = Vpn5gTunnelCheck.verify(baselineIp)
                when (result.verdict) {
                    Vpn5gTunnelCheck.Verdict.VERIFIED -> {
                        val flag = result.exitIp?.let { ip ->
                            Vpn5gGeoIp.countryForIp(getApplication(), ip).flag
                        }.orEmpty()
                        _uiState.update {
                            it.copy(
                                tunnelState = Vpn5gTunnelState.VERIFIED,
                                tunnelMessage = null,
                                verifiedExitIp = result.exitIp,
                                verifiedExitFlag = flag,
                            )
                        }
                        return@launch
                    }
                    Vpn5gTunnelCheck.Verdict.UNKNOWN -> {
                        _uiState.update {
                            it.copy(tunnelState = Vpn5gTunnelState.UNKNOWN, tunnelMessage = null)
                        }
                        return@launch
                    }
                    Vpn5gTunnelCheck.Verdict.FAILED -> {
                        verifyAttempts++
                        triedGuids += guid
                        val next = nextBestServer(excluding = triedGuids)
                        if (next == null || verifyAttempts >= MAX_TUNNEL_ATTEMPTS) {
                            tunnelFailedStop = true
                            LauncherManager.stopService(getApplication())
                            _uiState.update {
                                it.copy(
                                    tunnelState = Vpn5gTunnelState.FAILED,
                                    tunnelMessage = getApplication<Application>()
                                        .getString(R.string.vpn5g_tunnel_failed_all),
                                )
                            }
                            return@launch
                        }
                        _uiState.update {
                            it.copy(
                                tunnelMessage = getApplication<Application>()
                                    .getString(R.string.vpn5g_tunnel_failed_retry),
                            )
                        }
                        // The restart below is intentional: do not let the
                        // drop detector treat it as an unexpected disconnect.
                        suppressAutoFailover = true
                        _failoverEvent.emit(next.guid)
                        delay(4_000L)
                        if (!_uiState.value.isRunning) return@launch
                    }
                }
            }
        }
    }

    private fun nextBestServer(excluding: Set<String>): Vpn5gServerUi? {
        return Vpn5gProtocol.filterSections(
            _uiState.value.countries, _uiState.value.protocolFilter
        )
            .filter { it.isFree }
            .flatMap { it.servers }
            .filter { it.guid !in excluding && it.pingMillis > 0 }
            .minByOrNull { it.pingMillis }
    }

    private fun refreshSelection() {
        val selected = MmkvManager.getSelectServer()
        val profile: ProfileItem? = selected?.let { MmkvManager.decodeServerConfig(it) }
        val ping = selected?.let {
            MmkvManager.decodeServerAffiliationInfo(it)?.testDelayMillis ?: 0L
        } ?: 0L
        _uiState.update {
            it.copy(
                selectedGuid = selected,
                currentServerName = profile?.remarks?.ifBlank { profile.server },
                currentPingMillis = ping,
            )
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(Vpn5gHomeViewModel::class.java)) {
                return Vpn5gHomeViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

/**
 * VPN5G IRBOT launcher activity. Owns the VPN-permission flow (mirrors
 * MainActivity.requestServiceStart) and deep-link/file imports; all state
 * lives in [Vpn5gHomeViewModel].
 */
class Vpn5gMainActivity : HelperBaseComponentActivity() {

    private val viewModel: Vpn5gHomeViewModel by viewModels {
        Vpn5gHomeViewModel.Factory(application)
    }

    private var pendingGuid: String? = null

    private val requestVpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                pendingGuid?.let { startV2Ray(it) }
            }
            pendingGuid = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {}
        handleDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    @Composable
    override fun ScreenContent() {
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        var destination by remember { mutableStateOf(Vpn5gDestination.HOME) }
        var lockedCountry by remember { mutableStateOf<String?>(null) }

        // Tunnel failover: the ViewModel asks for the next server; the
        // Activity performs the permission-aware start.
        LaunchedEffect(Unit) {
            viewModel.failoverEvent.collect { guid ->
                requestServiceStart(guid)
            }
        }

        Vpn5gAnimatedBackground {
            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = {
                    NavigationBar(containerColor = Vpn5gBlackBar.copy(alpha = 0.95f)) {
                        navItem(
                            destination, Vpn5gDestination.HOME,
                            stringResource(R.string.vpn5g_nav_home),
                            { color -> Vpn5gHomeIcon(color) },
                        ) { destination = Vpn5gDestination.HOME }
                        navItem(
                            destination, Vpn5gDestination.PREMIUM,
                            stringResource(R.string.vpn5g_nav_premium),
                            { color -> Vpn5gCrownIcon(color) },
                        ) { destination = Vpn5gDestination.PREMIUM }
                        navItem(
                            destination, Vpn5gDestination.BROWSER,
                            stringResource(R.string.vpn5g_nav_browser),
                            { color -> Vpn5gGlobeIcon(color) },
                        ) { destination = Vpn5gDestination.BROWSER }
                        navItem(
                            destination, Vpn5gDestination.ROUTING,
                            stringResource(R.string.vpn5g_nav_routing),
                            { color -> Vpn5gRoutesIcon(color) },
                        ) { destination = Vpn5gDestination.ROUTING }
                        navItem(
                            destination, Vpn5gDestination.ME,
                            stringResource(R.string.vpn5g_nav_me),
                            { color -> Vpn5gPersonIcon(color) },
                        ) { destination = Vpn5gDestination.ME }
                    }
                },
            ) { innerPadding ->
                AnimatedContent(
                    targetState = destination,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(300)) +
                            slideInHorizontally(animationSpec = tween(300)) { it / 3 }) togetherWith
                            (fadeOut(animationSpec = tween(200)) +
                                slideOutHorizontally(animationSpec = tween(200)) { -it / 3 })
                    },
                    label = "vpn5g_destination",
                    modifier = Modifier.padding(innerPadding),
                ) { dest ->
                    when (dest) {
                        Vpn5gDestination.HOME -> Vpn5gHomeScreen(
                            state = uiState,
                            onRefresh = { viewModel.refresh() },
                            onToggleConnect = { toggleConnect() },
                            onServerTap = { guid -> onServerTap(guid) },
                            onAutoConnect = { connectBestServer() },
                            onLockedCountryTap = { lockedCountry = it },
                            onOpenBot = { Utils.openUri(this@Vpn5gMainActivity, BOT_URL) },
                            onOpenPremium = { destination = Vpn5gDestination.PREMIUM },
                            onConsumeAutoFailoverNotice = { viewModel.consumeAutoFailoverNotice() },
                        )
                        Vpn5gDestination.PREMIUM -> Vpn5gSubscriptionScreen(
                            state = uiState,
                            onBack = { destination = Vpn5gDestination.HOME },
                            onOpenBot = { Utils.openUri(this@Vpn5gMainActivity, BOT_URL) },
                            onOpenAuth = { destination = Vpn5gDestination.AUTH },
                            onPromoInputChange = { viewModel.setPromoInput(it) },
                            onRedeemPromo = { viewModel.redeemPromo() },
                            onOpenPromoBot = {
                                uiState.promoBotLink?.let { link ->
                                    Utils.openUri(this@Vpn5gMainActivity, link)
                                }
                            },
                        )
                        Vpn5gDestination.BROWSER -> Vpn5gGuideScreen(
                            onBack = { destination = Vpn5gDestination.HOME },
                            onOpenUrl = { url -> Utils.openUri(this@Vpn5gMainActivity, url) },
                        )
                        Vpn5gDestination.ROUTING -> Vpn5gRoutingScreen(
                            onBack = { destination = Vpn5gDestination.HOME },
                        )
                        Vpn5gDestination.ME -> Vpn5gProfileScreen(
                            state = uiState,
                            onBack = { destination = Vpn5gDestination.HOME },
                            onOpenAuth = { destination = Vpn5gDestination.AUTH },
                            onOpenProtocol = { destination = Vpn5gDestination.PROTOCOL },
                            onSignOut = { viewModel.signOut() },
                            onOpenAdvanced = {
                                startActivity(Intent(this@Vpn5gMainActivity, MainActivity::class.java))
                            },
                            onOpenUrl = { url -> Utils.openUri(this@Vpn5gMainActivity, url) },
                        )
                        Vpn5gDestination.AUTH -> Vpn5gAuthScreen(
                            onBack = { destination = Vpn5gDestination.ME },
                        )
                        Vpn5gDestination.PROTOCOL -> Vpn5gProtocolScreen(
                            currentProtocol = uiState.protocolFilter,
                            onSelect = {
                                viewModel.setProtocolFilter(it)
                                destination = Vpn5gDestination.ME
                            },
                            onBack = { destination = Vpn5gDestination.ME },
                        )
                    }
                }
            }
        }

        lockedCountry?.let { country ->
            AlertDialog(
                onDismissRequest = { lockedCountry = null },
                title = { Text(stringResource(R.string.vpn5g_locked_dialog_title)) },
                text = {
                    Text(
                        stringResource(R.string.vpn5g_locked_dialog_body) +
                            " (" + country + ")"
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        lockedCountry = null
                        Utils.openUri(this, BOT_URL)
                    }) { Text(stringResource(R.string.vpn5g_open_bot)) }
                },
                dismissButton = {
                    TextButton(onClick = { lockedCountry = null }) {
                        Text(stringResource(R.string.vpn5g_close))
                    }
                },
            )
        }

        uiState.errorMessage?.let { message ->
            LaunchedEffect(message) {
                if (message.isNotBlank()) {
                    toast(message)
                    viewModel.consumeError()
                }
            }
        }
    }

    @Composable
    private fun androidx.compose.foundation.layout.RowScope.navItem(
        current: Vpn5gDestination,
        target: Vpn5gDestination,
        label: String,
        icon: @Composable (Color) -> Unit,
        onClick: () -> Unit,
    ) {
        val selected = current == target
        NavigationBarItem(
            selected = selected,
            onClick = onClick,
            icon = {
                icon(if (selected) Vpn5gCyan else Vpn5gOnDarkMuted)
            },
            label = {
                Text(
                    label,
                    color = if (selected) Vpn5gCyan else Vpn5gOnDarkMuted,
                    fontSize = 11.sp,
                )
            },
        )
    }

    private fun toggleConnect() {
        val state = viewModel.uiState.value
        if (state.isRunning) {
            viewModel.userDisconnect()
            LauncherManager.stopService(this)
            return
        }
        val guid = state.selectedGuid
            ?: viewModel.bestFreeServer()?.guid
            ?: run {
                toast(R.string.vpn5g_no_server_selected)
                return
            }
        viewModel.resetTunnelAttempts()
        requestServiceStart(guid)
    }

    private fun connectBestServer() {
        val state = viewModel.uiState.value
        if (state.isRunning) return
        val guid = viewModel.bestFreeServer()?.guid
            ?: run {
                toast(R.string.vpn5g_no_server_selected)
                return
            }
        viewModel.resetTunnelAttempts()
        requestServiceStart(guid)
    }

    private fun onServerTap(guid: String) {
        val state = viewModel.uiState.value
        if (state.isRunning) {
            if (guid == state.selectedGuid) return
            viewModel.resetTunnelAttempts()
            viewModel.markRestarting()
            MmkvManager.setSelectServer(guid)
            LauncherManager.restartService(this)
            return
        }
        viewModel.resetTunnelAttempts()
        requestServiceStart(guid)
    }

    /** Mirrors MainActivity.requestServiceStart exactly. */
    private fun requestServiceStart(guid: String) {
        if (!SettingsManager.isVpnMode()) {
            startV2Ray(guid)
            return
        }
        val intent = VpnService.prepare(this)
        if (intent == null) {
            startV2Ray(guid)
        } else {
            pendingGuid = guid
            requestVpnPermission.launch(intent)
        }
    }

    private fun startV2Ray(guid: String) {
        if (guid.isBlank()) {
            toast(R.string.vpn5g_no_server_selected)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
            checkAndRequestPermission(PermissionType.ACCESS_LOCAL_NETWORK) {}
        }
        LauncherManager.startService(this, guid)
    }

    /** Deep links (vmess:// etc.) and downloaded .txt/.json config files. */
    private fun handleDeepLink(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        val scheme = uri.scheme.orEmpty()
        if (scheme in CONFIG_SCHEMES) {
            lifecycleScope.launch(Dispatchers.IO) {
                val ok = Vpn5gSync.importUri(uri.toString())
                withContext(Dispatchers.Main) {
                    toast(
                        if (ok) R.string.vpn5g_imported_config
                        else R.string.vpn5g_import_failed
                    )
                }
            }
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val text = contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (text.isBlank()) {
                    withContext(Dispatchers.Main) {
                        toast(R.string.vpn5g_import_failed)
                    }
                    return@launch
                }
                val (count, _) = Vpn5gSync.importText(text)
                withContext(Dispatchers.Main) {
                    if (count > 0) {
                        toast(getString(R.string.vpn5g_imported_file, count))
                        viewModel.refresh()
                    } else {
                        toast(R.string.vpn5g_import_failed)
                    }
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Vpn5gMainActivity: deep-link import failed", e)
                withContext(Dispatchers.Main) {
                    toast(R.string.vpn5g_import_failed)
                }
            }
        }
    }
}

private fun formatRemaining(millis: Long?): String {
    if (millis == null) return "--:--"
    val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

/**
 * X-VPN-style home screen: black background, connection status above a big
 * circular power button with a cyan ring, "Free Servers" and "Auto" rows,
 * then the grouped server list with tabs (Free / All / Special), search,
 * a Recommended fastest-server row, and a premium banner. The ring shows
 * the 30-minute session as a progress arc while connected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Vpn5gHomeScreen(
    state: Vpn5gHomeUiState,
    onRefresh: () -> Unit,
    onToggleConnect: () -> Unit,
    onServerTap: (String) -> Unit,
    onAutoConnect: () -> Unit,
    onLockedCountryTap: (String) -> Unit,
    onOpenBot: () -> Unit,
    onOpenPremium: () -> Unit,
    onConsumeAutoFailoverNotice: () -> Unit,
) {
    var entered by remember { mutableStateOf(false) }
    var serversExpanded by rememberSaveable { mutableStateOf(true) }
    var serverTab by rememberSaveable { mutableStateOf(Vpn5gServerTab.FREE) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { entered = true }

    val displayCountries = Vpn5gProtocol.filterSections(state.countries, state.protocolFilter)
    val freeSectionsAll = displayCountries.filter { it.isFree }
    val searchedCountries = if (searchQuery.isBlank()) {
        displayCountries
    } else {
        displayCountries.mapNotNull { section ->
            val labelMatch = section.label.contains(searchQuery, ignoreCase = true)
            val servers = if (labelMatch) {
                section.servers
            } else {
                section.servers.filter {
                    it.name.contains(searchQuery, ignoreCase = true) ||
                        it.countryNameFa.contains(searchQuery, ignoreCase = true)
                }
            }
            if (servers.isEmpty()) null else section.copy(servers = servers)
        }
    }
    val freeSections = searchedCountries.filter { it.isFree }
    val lockedSections = searchedCountries.filter { !it.isFree }
    val listExpanded = serversExpanded || searchQuery.isNotBlank()
    val freeCount = freeSections.sumOf { it.servers.size }
    val bestServer = freeSectionsAll.flatMap { it.servers }
        .filter { it.pingMillis > 0 }
        .minByOrNull { it.pingMillis }
        ?: freeSectionsAll.flatMap { it.servers }.firstOrNull()

    val statusTextRes = when {
        state.isRunning && state.tunnelState == Vpn5gTunnelState.VERIFYING ->
            R.string.vpn5g_status_verifying
        state.isRunning && state.tunnelState == Vpn5gTunnelState.FAILED ->
            R.string.vpn5g_status_tunnel_failed
        state.isRunning -> R.string.vpn5g_connected
        else -> R.string.vpn5g_status_not_connected
    }
    val statusColor = when {
        state.isRunning && state.tunnelState == Vpn5gTunnelState.FAILED -> colorPingRed
        state.isRunning && state.tunnelState == Vpn5gTunnelState.VERIFYING -> Vpn5gCyan
        state.isRunning -> colorPing
        else -> Vpn5gOnDarkMuted
    }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                // Header: shield logo + brand + status pill.
                Vpn5gStaggeredItem(index = 0, visible = entered) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Vpn5gPulsingLogo(size = 48.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                color = Vpn5gOnDark,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = stringResource(R.string.vpn5g_app_tagline),
                                color = Vpn5gOnDarkMuted,
                                fontSize = 12.sp,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (state.isRunning) colorPing else Vpn5gOnDarkMuted.copy(alpha = 0.4f)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = stringResource(
                                    if (state.isRunning) R.string.vpn5g_connected
                                    else R.string.vpn5g_disconnected
                                ),
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            item {
                // Status text above the power button (X-VPN style).
                Vpn5gStaggeredItem(index = 1, visible = entered) {
                    Text(
                        text = stringResource(statusTextRes),
                        color = statusColor,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    )
                }
            }

            item {
                // Big circular power button with a cyan ring.
                Vpn5gStaggeredItem(index = 2, visible = entered) {
                    Vpn5gPowerButton(
                        isRunning = state.isRunning,
                        isVerifying = state.isRunning &&
                            state.tunnelState == Vpn5gTunnelState.VERIFYING,
                        isFailed = state.isRunning &&
                            state.tunnelState == Vpn5gTunnelState.FAILED,
                        remainingFraction = state.remainingMillis
                            ?.let { (it / (30L * 60_000L).toFloat()).coerceIn(0f, 1f) },
                        onClick = onToggleConnect,
                    )
                }
            }

            if (state.isRunning) {
                item {
                    // Current server + exit IP + ping + 30-minute countdown.
                    Vpn5gStaggeredItem(index = 3, visible = entered) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Vpn5gOnDark.copy(alpha = 0.08f))
                                .padding(16.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.vpn5g_current_server),
                                color = Vpn5gOnDarkMuted,
                                fontSize = 13.sp,
                            )
                            Text(
                                text = state.currentServerName
                                    ?: stringResource(R.string.value_unknown),
                                color = Vpn5gOnDark,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            if (state.tunnelState == Vpn5gTunnelState.VERIFIED &&
                                state.verifiedExitIp != null
                            ) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = state.verifiedExitFlag + " " + stringResource(
                                        R.string.vpn5g_exit_ip,
                                        state.verifiedExitIp,
                                    ),
                                    color = colorPing,
                                    fontSize = 14.sp,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Row {
                                Text(
                                    text = stringResource(
                                        R.string.vpn5g_ping_ms,
                                        state.currentPingMillis,
                                    ),
                                    color = if (state.currentPingMillis < 0L) colorPingRed else colorPing,
                                    fontSize = 14.sp,
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = stringResource(
                                        R.string.vpn5g_remaining_time,
                                        formatRemaining(state.remainingMillis),
                                    ),
                                    color = Vpn5gOnDark,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }

            state.tunnelMessage?.let { message ->
                item {
                    Text(
                        text = message,
                        color = Vpn5gOnDark,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Vpn5gAccent.copy(alpha = 0.25f))
                            .padding(12.dp),
                    )
                }
            }

            // Auto-failover notice: shown briefly after an unexpected drop
            // switches the connection to another country's server.
            state.autoFailoverNotice?.let { notice ->
                item {
                    Text(
                        text = notice,
                        color = Vpn5gOnDark,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Vpn5gCyan.copy(alpha = 0.2f))
                            .padding(12.dp),
                    )
                }
                LaunchedEffect(notice) {
                    delay(8_000L)
                    onConsumeAutoFailoverNotice()
                }
            }

            state.notice?.let { notice ->
                item {
                    Text(
                        text = notice,
                        color = Vpn5gOnDark,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Vpn5gAccent.copy(alpha = 0.25f))
                            .padding(12.dp),
                    )
                }
            }

            if (state.offline) {
                item {
                    Text(
                        text = stringResource(R.string.vpn5g_offline_cached),
                        color = Vpn5gOnDarkMuted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // First sync in progress: show a loading line, never the empty text.
            if (state.isLoading || (state.isRefreshing && state.countries.isEmpty())) {
                item {
                    Text(
                        text = stringResource(R.string.vpn5g_loading_servers),
                        color = Vpn5gOnDarkMuted,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (displayCountries.isEmpty() && !state.isLoading && !state.isRefreshing) {
                item {
                    Text(
                        text = stringResource(
                            if (state.countries.isEmpty()) R.string.vpn5g_empty_list
                            else R.string.vpn5g_protocol_no_servers
                        ),
                        color = Vpn5gOnDarkMuted,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // "Free Servers" row (X-VPN style): expands/collapses the list.
            if (freeSections.isNotEmpty() && serverTab != Vpn5gServerTab.SPECIAL) {
                item(key = "free_servers_row") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Vpn5gOnDark.copy(alpha = 0.06f))
                            .clickable(
                                role = Role.Button,
                                onClickLabel = stringResource(R.string.vpn5g_free_servers),
                            ) { serversExpanded = !serversExpanded }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Vpn5gBoltIcon(color = Vpn5gCyan, size = 26.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.vpn5g_free_servers),
                            color = Vpn5gOnDark,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = stringResource(R.string.vpn5g_servers_count, freeCount),
                            color = Vpn5gOnDarkMuted,
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Vpn5gChevronIcon(
                            color = Vpn5gOnDarkMuted,
                        )
                    }
                }
            }

            // "Auto" row: the best free server, one tap to connect.
            if (bestServer != null && serverTab != Vpn5gServerTab.SPECIAL) {
                item(key = "auto_row") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Vpn5gOnDark.copy(alpha = 0.06f))
                            .clickable(
                                role = Role.Button,
                                onClickLabel = stringResource(R.string.vpn5g_auto_connect),
                            ) { onAutoConnect() }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Vpn5gRoutesIcon(color = Vpn5gCyan, size = 26.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.vpn5g_auto_server),
                            color = Vpn5gOnDark,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = bestServer.flag + " " + bestServer.name,
                            color = Vpn5gOnDarkMuted,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = if (bestServer.pingMillis > 0L) {
                                stringResource(R.string.vpn5g_ping_ms, bestServer.pingMillis)
                            } else {
                                "—"
                            },
                            color = colorPing,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            // Server-list tabs: Free / All locations / Special (X-VPN style).
            item(key = "server_tabs") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Vpn5gServerTab.entries.forEach { tab ->
                        val selected = serverTab == tab
                        val tabLabelRes = when (tab) {
                            Vpn5gServerTab.FREE -> R.string.vpn5g_tab_free
                            Vpn5gServerTab.ALL -> R.string.vpn5g_tab_all
                            Vpn5gServerTab.SPECIAL -> R.string.vpn5g_tab_special
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) Vpn5gOnDark.copy(alpha = 0.12f)
                                    else Color.Transparent
                                )
                                .clickable(
                                    role = Role.Button,
                                    onClickLabel = stringResource(tabLabelRes),
                                ) { serverTab = tab }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(
                                text = stringResource(tabLabelRes),
                                color = if (selected) Vpn5gCyan else Vpn5gOnDarkMuted,
                                fontSize = 15.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }

            // Premium promo banner (no prices here — plans live on Premium).
            if (searchQuery.isBlank()) {
                item(key = "premium_banner") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Vpn5gCyan.copy(alpha = 0.15f))
                            .clickable(
                                role = Role.Button,
                                onClickLabel = stringResource(R.string.vpn5g_premium_banner_title),
                            ) { onOpenPremium() }
                            .padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.vpn5g_premium_banner_title),
                            color = Vpn5gCyan,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.vpn5g_premium_banner_body),
                            color = Vpn5gOnDark,
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.vpn5g_premium_banner_button),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            // Recommended: the fastest free server, one tap to connect.
            if (bestServer != null && searchQuery.isBlank() &&
                serverTab != Vpn5gServerTab.SPECIAL
            ) {
                item(key = "recommended_header") {
                    Text(
                        text = stringResource(R.string.vpn5g_recommended),
                        color = Vpn5gOnDarkMuted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start,
                    )
                }
                item(key = "recommended_fastest") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Vpn5gOnDark.copy(alpha = 0.06f))
                            .clickable(
                                role = Role.Button,
                                onClickLabel = stringResource(R.string.vpn5g_fastest_server),
                            ) { onServerTap(bestServer.guid) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Text(
                            text = bestServer.flag,
                            fontSize = 22.sp,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.vpn5g_fastest_server),
                                color = Vpn5gOnDark,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = bestServer.name,
                                color = Vpn5gOnDarkMuted,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = if (bestServer.pingMillis > 0L) {
                                stringResource(R.string.vpn5g_ping_ms, bestServer.pingMillis)
                            } else {
                                "—"
                            },
                            color = colorPing,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            // Search by country or server name.
            item(key = "server_search") {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.vpn5g_search_hint)) },
                    leadingIcon = { Vpn5gSearchIcon(color = Vpn5gOnDarkMuted, size = 20.dp) },
                    singleLine = true,
                    colors = vpn5gSearchFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (listExpanded) {
                val ordered = when (serverTab) {
                    Vpn5gServerTab.FREE -> freeSections.map { it to false }
                    Vpn5gServerTab.ALL -> freeSections.map { it to false } +
                        lockedSections.map { it to true }
                    Vpn5gServerTab.SPECIAL -> lockedSections.map { it to true }
                }
                var promoShown = false
                ordered.forEach { (country, locked) ->
                    if (serverTab == Vpn5gServerTab.ALL && locked && !promoShown) {
                        item(key = "bot_promo") {
                            Vpn5gBotPromoCard(onOpenBot = onOpenBot)
                        }
                        promoShown = true
                    }
                    item(key = "country_${country.label}") {
                        Vpn5gCountrySection(
                            country = country,
                            onServerTap = if (locked) {
                                {}
                            } else {
                                onServerTap
                            },
                            onLockedTap = if (locked) {
                                { onLockedCountryTap(country.label) }
                            } else {
                                null
                            },
                        )
                    }
                }
                if (serverTab == Vpn5gServerTab.FREE && ordered.isNotEmpty()) {
                    item(key = "bot_promo") {
                        Vpn5gBotPromoCard(onOpenBot = onOpenBot)
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** Big circular power button with a cyan ring (X-VPN style). */
@Composable
private fun Vpn5gPowerButton(
    isRunning: Boolean,
    isVerifying: Boolean,
    isFailed: Boolean,
    remainingFraction: Float?,
    onClick: () -> Unit,
) {
    val ringColor = when {
        isFailed -> colorPingRed
        isRunning -> Vpn5gCyan
        else -> Vpn5gOnDarkMuted.copy(alpha = 0.5f)
    }
    val glyphColor = when {
        isFailed -> colorPingRed
        isRunning -> Color.White
        else -> Vpn5gOnDarkMuted
    }
    // While connected the ring shows the 30-minute session as a progress arc.
    val sweep = when {
        isVerifying -> 270f
        isRunning -> 360f * (remainingFraction ?: 1f)
        else -> 0f
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(220.dp)
            .clip(CircleShape)
            .background(Color(0xFF1A1A1A))
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.vpn5g_connect)) {
                onClick()
            }
            .padding(10.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = size.width * 0.035f
            // Track ring.
            drawArc(
                color = Color(0xFF2A2A2A),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            // Progress ring.
            if (sweep > 0f) {
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Vpn5gPowerIcon(color = glyphColor, size = 84.dp)
    }
}

@Composable
private fun Vpn5gCountrySection(
    country: Vpn5gCountryUi,
    onServerTap: (String) -> Unit,
    onLockedTap: (() -> Unit)?,
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .then(
                    if (onLockedTap != null) Modifier.clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.vpn5g_needs_subscription),
                    ) { onLockedTap() } else Modifier
                ),
        ) {
            Text(
                text = country.label,
                color = Vpn5gOnDark,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.vpn5g_servers_count, country.servers.size),
                color = Vpn5gOnDarkMuted,
                fontSize = 13.sp,
            )
            if (!country.isFree) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.vpn5g_needs_subscription),
                    color = Vpn5gAccentLight,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        country.servers.forEach { server ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (server.isSelected) Vpn5gAccent.copy(alpha = 0.35f)
                        else Vpn5gOnDark.copy(alpha = 0.06f)
                    )
                    .clickable(
                        enabled = country.isFree,
                        role = Role.Button,
                        onClickLabel = server.name,
                    ) { onServerTap(server.guid) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    text = server.flag,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = buildString {
                            if (server.countryNameFa.isNotBlank()) {
                                append(server.countryNameFa)
                                append(" — ")
                            }
                            append(server.name)
                        },
                        color = Vpn5gOnDark,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "VPN5G_IRBOT",
                        color = Vpn5gOnDarkMuted,
                        fontSize = 12.sp,
                    )
                }
                Text(
                    text = if (server.pingMillis > 0L) {
                        stringResource(R.string.vpn5g_ping_ms, server.pingMillis)
                    } else {
                        "—"
                    },
                    color = if (server.pingMillis < 0L) colorPingRed else colorPing,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun vpn5gSearchFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Vpn5gOnDark,
    unfocusedTextColor = Vpn5gOnDark,
    focusedPlaceholderColor = Vpn5gOnDarkMuted,
    unfocusedPlaceholderColor = Vpn5gOnDarkMuted,
    focusedBorderColor = Vpn5gCyan,
    unfocusedBorderColor = Vpn5gOnDarkMuted,
    cursorColor = Vpn5gCyan,
)

@Composable
private fun Vpn5gBotPromoCard(onOpenBot: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Vpn5gAccent.copy(alpha = 0.3f))
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.vpn5g_open_bot)) {
                onOpenBot()
            }
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.vpn5g_bot_promo_title),
            color = Vpn5gOnDark,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.vpn5g_bot_promo_body),
            color = Vpn5gOnDarkMuted,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.vpn5g_open_bot),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
