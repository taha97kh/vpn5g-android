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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
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
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.vpn5g.PaymentGatewayRemote
import com.v2ray.ang.handler.vpn5g.PromoCodeRemote
import com.v2ray.ang.handler.vpn5g.SubscriptionPlanRemote
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
import com.v2ray.ang.handler.vpn5g.Vpn5gSounds
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
    val currentServerFlag: String = "",
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
    /** Admin-managed subscription plans (null = none published). */
    val plans: List<SubscriptionPlanRemote>? = null,
    /** Direct bank payment gateway settings (null = not configured). */
    val paymentGateway: PaymentGatewayRemote? = null,
    /** Whether the free trial is offered (bot-managed flag). */
    val trialEnabled: Boolean = false,
    /** Free-trial length in hours (24 per the product rule). */
    val trialHours: Int = 24,
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
                    // The raw failure text comes from the core (English);
                    // log it, but show the user a short Persian message.
                    LogUtil.e(
                        AppConfig.TAG,
                        "Vpn5gHomeViewModel: service start failed: " +
                            safeIntent.getStringExtra("content"),
                    )
                    Vpn5gSounds.playError()
                    _uiState.update {
                        it.copy(
                            errorMessage = getApplication<Application>()
                                .getString(R.string.vpn5g_start_failed),
                        )
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
            // A failure to read the stored list must never leave the home
            // screen stuck on "loading": fall back to an empty list.
            var countries = runCatching { Vpn5gSync.loadGroupedServers() }.getOrElse {
                LogUtil.e(AppConfig.TAG, "Vpn5gHomeViewModel: load failed", it)
                emptyList()
            }
            var usedFallback = false
            if (countries.isEmpty()) {
                val imported = Vpn5gSync.loadBundledFallback(getApplication())
                if (imported > 0) {
                    countries = runCatching { Vpn5gSync.loadGroupedServers() }.getOrElse { emptyList() }
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
                    plans = cached.plans,
                    paymentGateway = cached.payment_gateway,
                    trialEnabled = cached.trial_enabled ?: false,
                    trialHours = cached.trial_hours ?: 24,
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
                val countries = Vpn5gSync.loadGroupedServers()
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        isLoading = false,
                        offline = result.offline,
                        notice = result.notice ?: cached.notice,
                        countries = countries,
                        subscriptionStatus = cached.subscription_status,
                        promoCodes = cached.codes,
                        plans = cached.plans,
                        paymentGateway = cached.payment_gateway,
                        trialEnabled = cached.trial_enabled ?: false,
                        trialHours = cached.trial_hours ?: 24,
                        signedInEmail = Vpn5gAuthService.currentSession()?.email,
                    )
                }
                refreshSelection()
                enrichCountries()
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Vpn5gHomeViewModel: refresh failed", e)
                _uiState.update { it.copy(isRefreshing = false, isLoading = false, offline = true) }
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

    /**
     * Best free server honoring the protocol filter (lowest measured ping).
     * A config with no ping result is never returned — it must never be
     * tried first. Null when no free server has a ping yet.
     */
    fun bestFreeServer(): Vpn5gServerUi? {
        val servers = Vpn5gProtocol.filterSections(
            _uiState.value.countries, _uiState.value.protocolFilter
        ).flatMap { it.servers }.filter { it.isFree && it.pingMillis > 0 }
        return servers.minByOrNull { it.pingMillis }
    }

    /**
     * The server to actually connect to when the user taps [guid]: the
     * tapped server itself when it has a measured ping; otherwise the next
     * best free server in a different country, switched to immediately
     * (with a brief notice). A config with no ping result is never tried
     * first. Returns [guid] unchanged when no better server exists.
     */
    fun resolveConnectGuid(guid: String): String {
        val app = getApplication<Application>()
        val server = _uiState.value.countries.flatMap { it.servers }
            .firstOrNull { it.guid == guid } ?: return guid
        if (server.pingMillis > 0) return guid
        val next = nextBestServer(
            excluding = setOf(guid),
            differentCountryThan = server.country,
        ) ?: return guid
        _uiState.update {
            it.copy(
                tunnelMessage = app.getString(R.string.vpn5g_no_ping_switch),
            )
        }
        return next.guid
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
            .flatMap { it.servers }
            .filter { it.isFree && it.guid != droppedGuid && it.pingMillis > 0 }
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
        failoverTargetName = displayCountryName(
            next,
            app.getString(R.string.vpn5g_country_unknown),
        )
        if (droppedGuid != null) triedGuids += droppedGuid
        resetForFailover()
        suppressAutoFailover = true
        _uiState.update {
            it.copy(
                autoFailoverNotice = app.getString(
                    R.string.vpn5g_autofailover_switching,
                    displayCountryName(
                        next,
                        app.getString(R.string.vpn5g_country_unknown),
                    ),
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
                delay(1_500L)
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
                        Vpn5gSounds.playConnected()
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
                        Vpn5gSounds.playConnected()
                        _uiState.update {
                            it.copy(tunnelState = Vpn5gTunnelState.UNKNOWN, tunnelMessage = null)
                        }
                        return@launch
                    }
                    Vpn5gTunnelCheck.Verdict.FAILED -> {
                        verifyAttempts++
                        triedGuids += guid
                        val failedCountry = _uiState.value.countries
                            .flatMap { it.servers }
                            .firstOrNull { it.guid == guid }?.country
                        val next = nextBestServer(
                            excluding = triedGuids,
                            differentCountryThan = failedCountry,
                        )
                        if (next == null || verifyAttempts >= MAX_TUNNEL_ATTEMPTS) {
                            tunnelFailedStop = true
                            LauncherManager.stopService(getApplication())
                            Vpn5gSounds.playError()
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
                        // Switch immediately: wait only until the restart
                        // actually happens (service stops, then runs again
                        // with the new server), in short polls — not a
                        // fixed multi-second sleep. The next attempt must
                        // start fast.
                        val stopDeadline = System.currentTimeMillis() + 5_000L
                        while (isActive && _uiState.value.isRunning &&
                            System.currentTimeMillis() < stopDeadline
                        ) {
                            delay(200L)
                        }
                        val startDeadline = System.currentTimeMillis() + 10_000L
                        while (isActive && !_uiState.value.isRunning &&
                            System.currentTimeMillis() < startDeadline
                        ) {
                            delay(200L)
                        }
                        if (!_uiState.value.isRunning) return@launch
                    }
                }
            }
        }
    }

    /**
     * Next best free server with a measured ping, preferring a DIFFERENT
     * country than [differentCountryThan] (a dead server's country is
     * avoided first); falls back to the same country when nothing else is
     * left. Never returns a config with no ping result.
     */
    private fun nextBestServer(
        excluding: Set<String>,
        differentCountryThan: String? = null,
    ): Vpn5gServerUi? {
        val candidates = Vpn5gProtocol.filterSections(
            _uiState.value.countries, _uiState.value.protocolFilter
        )
            .flatMap { it.servers }
            .filter { it.isFree && it.guid !in excluding && it.pingMillis > 0 }
        if (differentCountryThan.isNullOrBlank()) {
            return candidates.minByOrNull { it.pingMillis }
        }
        return candidates.filter { it.country != differentCountryThan }
            .minByOrNull { it.pingMillis }
            ?: candidates.minByOrNull { it.pingMillis }
    }

    private fun refreshSelection() {
        val selected = MmkvManager.getSelectServer()
        val server = selected?.let { guid ->
            _uiState.value.countries.flatMap { it.servers }.firstOrNull { it.guid == guid }
        }
        val ping = selected?.let {
            MmkvManager.decodeServerAffiliationInfo(it)?.testDelayMillis ?: 0L
        } ?: 0L
        _uiState.update {
            it.copy(
                selectedGuid = selected,
                // Never the config remark: flag + country name only.
                currentServerName = server?.let {
                    displayCountryName(
                        it,
                        getApplication<Application>().getString(R.string.vpn5g_country_unknown),
                    )
                },
                currentServerFlag = server?.flag.orEmpty(),
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

        // Launch splash (v1.1.2): full-screen branded image for exactly
        // 3 seconds, then the main screen.
        var showSplash by remember { mutableStateOf(true) }
        LaunchedEffect(Unit) {
            delay(3_000L)
            showSplash = false
        }
        if (showSplash) {
            Vpn5gSplashScreen()
            return
        }

        Vpn5gAnimatedBackground {
            Scaffold(
                containerColor = Color.Transparent,
                bottomBar = {
                    Vpn5gFloatingNavBar(
                        destination = destination,
                        onDestinationChange = { destination = it },
                    )
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
                            onOpenUrl = { url -> Utils.openUri(this@Vpn5gMainActivity, url) },
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

    /**
     * Floating pill bottom bar (v1.1.2): a detached rounded bar with a
     * sliding pill behind the selected item. The indicator offset animates
     * with a spring, so switching tabs feels instant and fluid. The
     * AnimatedContent in [Vpn5gMainActivity.ScreenContent] already fades +
     * slides between screens; the bar itself only ever changes selection.
     */
    @Composable
    private fun Vpn5gFloatingNavBar(
        destination: Vpn5gDestination,
        onDestinationChange: (Vpn5gDestination) -> Unit,
    ) {
        val destinations = listOf(
            Vpn5gDestination.HOME to R.string.vpn5g_nav_home,
            Vpn5gDestination.PREMIUM to R.string.vpn5g_nav_premium,
            Vpn5gDestination.BROWSER to R.string.vpn5g_nav_browser,
            Vpn5gDestination.ROUTING to R.string.vpn5g_nav_routing,
            Vpn5gDestination.ME to R.string.vpn5g_nav_me,
        )
        val selectedIndex = destinations
            .indexOfFirst { it.first == destination }.coerceAtLeast(0)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            BoxWithConstraints(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(28.dp))
                    .clip(RoundedCornerShape(28.dp))
                    .background(Vpn5gBlackBar.copy(alpha = 0.95f)),
            ) {
                val itemWidth = maxWidth / destinations.size
                val indicatorOffset by animateDpAsState(
                    targetValue = itemWidth * selectedIndex + 6.dp,
                    animationSpec = spring(
                        dampingRatio = 0.8f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    label = "vpn5g_nav_indicator",
                )
                // Sliding pill behind the selected item.
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(itemWidth - 12.dp)
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Vpn5gCyan.copy(alpha = 0.18f)),
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    destinations.forEach { (target, labelRes) ->
                        val selected = target == destination
                        val iconColor = if (selected) Vpn5gCyan else Vpn5gOnDarkMuted
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    role = Role.Button,
                                    onClickLabel = stringResource(labelRes),
                                ) { onDestinationChange(target) }
                                .padding(vertical = 8.dp),
                        ) {
                            when (target) {
                                Vpn5gDestination.HOME -> Vpn5gHomeIcon(iconColor)
                                Vpn5gDestination.PREMIUM -> Vpn5gCrownIcon(iconColor)
                                Vpn5gDestination.BROWSER -> Vpn5gGlobeIcon(iconColor)
                                Vpn5gDestination.ROUTING -> Vpn5gRoutesIcon(iconColor)
                                Vpn5gDestination.ME -> Vpn5gPersonIcon(iconColor)
                                Vpn5gDestination.AUTH,
                                Vpn5gDestination.PROTOCOL -> {
                                    // Never a bottom-bar destination.
                                }
                            }
                            Text(
                                text = stringResource(labelRes),
                                color = iconColor,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun toggleConnect() {
        val state = viewModel.uiState.value
        if (state.isRunning) {
            viewModel.userDisconnect()
            LauncherManager.stopService(this)
            return
        }
        connectWhenReady { rawGuid ->
            viewModel.resetTunnelAttempts()
            // Never try a config with no ping first: resolveConnectGuid
            // switches to the next best server immediately when needed.
            // It runs after the reset so its switch notice is not cleared.
            requestServiceStart(viewModel.resolveConnectGuid(rawGuid))
        }
    }

    private fun connectBestServer() {
        val state = viewModel.uiState.value
        if (state.isRunning) return
        connectWhenReady { guid ->
            viewModel.resetTunnelAttempts()
            requestServiceStart(guid)
        }
    }

    /**
     * One-click connect (v1.1.2): on a first launch the live refresh may
     * still be syncing/ping-testing, so no pinged free server exists yet.
     * Wait briefly for one instead of failing the tap; the 15-second
     * bound keeps a truly empty list from hanging the button.
     */
    private fun connectWhenReady(onReady: (String) -> Unit) {
        val guid = viewModel.uiState.value.selectedGuid
            ?: viewModel.bestFreeServer()?.guid
        if (guid != null) {
            onReady(guid)
            return
        }
        lifecycleScope.launch {
            val deadline = System.currentTimeMillis() + 15_000L
            var found: String? = null
            while (System.currentTimeMillis() < deadline) {
                delay(500L)
                found = viewModel.uiState.value.selectedGuid
                    ?: viewModel.bestFreeServer()?.guid
                if (found != null) break
            }
            if (found != null) {
                onReady(found)
            } else {
                toast(R.string.vpn5g_no_server_selected)
            }
        }
    }

    private fun onServerTap(guid: String) {
        val state = viewModel.uiState.value
        if (state.isRunning) {
            if (guid == state.selectedGuid) return
            viewModel.resetTunnelAttempts()
            // Resolve after the reset so a no-ping switch notice survives.
            val resolved = viewModel.resolveConnectGuid(guid)
            viewModel.markRestarting()
            MmkvManager.setSelectServer(resolved)
            LauncherManager.restartService(this)
            return
        }
        viewModel.resetTunnelAttempts()
        requestServiceStart(viewModel.resolveConnectGuid(guid))
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
    // Premium tab (v1.1.2): country groups start collapsed; tapping a
    // country reveals its servers. Transient presentation state only.
    var specialExpanded by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(Unit) { entered = true }

    val displayCountries = Vpn5gProtocol.filterSections(state.countries, state.protocolFilter)
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
    // Split each country into a free copy (top-20 by ping only) and a
    // locked copy (everything else), so a section never mixes tiers and
    // free counts are per-server, never per-section.
    val splitSections = searchedCountries.flatMap { section ->
        val free = section.servers.filter { it.isFree }
        val locked = section.servers.filter { !it.isFree }
        listOfNotNull(
            if (free.isNotEmpty()) section.copy(servers = free, isFree = true) else null,
            if (locked.isNotEmpty()) section.copy(servers = locked, isFree = false) else null,
        )
    }
    val freeSections = splitSections.filter { it.isFree }
    val lockedSections = splitSections.filter { !it.isFree }
    val listExpanded = serversExpanded || searchQuery.isNotBlank()
    val freeCount = freeSections.sumOf { it.servers.size }
    // Free servers always have a measured ping (top-20 selection), so
    // the fastest free server is the best one; never fall back to a
    // no-ping or locked server.
    val bestServer = freeSections.flatMap { it.servers }
        .filter { it.pingMillis > 0 }
        .minByOrNull { it.pingMillis }

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
                    // Inside item{} so LaunchedEffect runs in a @Composable context.
                    LaunchedEffect(notice) {
                        delay(8_000L)
                        onConsumeAutoFailoverNotice()
                    }
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
                            text = bestServer.flag + " " + displayCountryName(
                                bestServer,
                                stringResource(R.string.vpn5g_country_unknown),
                            ),
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
            // Hidden on the Free tab (v1.1.2): it shows only the free
            // service card and the auto row, no server rows.
            if (bestServer != null && searchQuery.isBlank() &&
                serverTab != Vpn5gServerTab.SPECIAL &&
                serverTab != Vpn5gServerTab.FREE
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
                                onClickLabel = displayCountryName(
                                    bestServer,
                                    stringResource(R.string.vpn5g_country_unknown),
                                ),
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
                                text = displayCountryName(
                                    bestServer,
                                    stringResource(R.string.vpn5g_country_unknown),
                                ),
                                color = Vpn5gOnDark,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = stringResource(R.string.vpn5g_server_subtitle),
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

            // Search by country or server name. Hidden on the Free tab
            // (v1.1.2): no server list is shown there.
            if (serverTab != Vpn5gServerTab.FREE) {
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
            }

            if (listExpanded) {
                // Free tab (v1.1.2): no server rows at all — only the
                // free-service card and the auto row above.
                val ordered = when (serverTab) {
                    Vpn5gServerTab.FREE -> emptyList()
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
                    item(key = "country_${if (locked) "locked" else "free"}_${country.label}") {
                        // Premium tab (v1.1.2): fast servers grouped by
                        // country; tapping a country reveals its servers.
                        val expandable = serverTab == Vpn5gServerTab.SPECIAL
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
                            expandable = expandable,
                            expanded = !expandable ||
                                country.label in specialExpanded,
                            onToggleExpand = if (expandable) {
                                {
                                    specialExpanded =
                                        if (country.label in specialExpanded) {
                                            specialExpanded - country.label
                                        } else {
                                            specialExpanded + country.label
                                        }
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
            }

            item {
                Vpn5gFooter()
            }

            item {
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/**
 * The only name a server row may show: the Persian country name, the
 * country key when no Persian name is known, or [unknown] when neither
 * exists. The config remark is never displayed anywhere.
 */
private fun displayCountryName(server: Vpn5gServerUi, unknown: String): String {
    return server.countryNameFa.ifBlank { server.country }.ifBlank { unknown }
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
    // "Direct Connect": the 4ref.ai brand sits on the circle itself,
    // under the power glyph.
    val brand = stringResource(R.string.vpn5g_connect_brand)
    val connected = isRunning && !isVerifying && !isFailed

    // Spinning ring while connecting: a 90° arc rotating ~900ms/rev.
    val spinTransition = rememberInfiniteTransition(label = "vpn5g_connect_spin")
    val spinAngle by spinTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
        ),
        label = "vpn5g_connect_spin_angle",
    )

    // Flame sequence, once per connection: the brand briefly catches
    // fire (orange/red glow + slight scale-up) then fades away, leaving
    // the normal connected state. Resets on disconnect.
    var flameDone by remember { mutableStateOf(false) }
    val flameAlpha = remember { Animatable(0f) }
    val brandAlpha = remember { Animatable(1f) }
    val brandScale = remember { Animatable(1f) }
    LaunchedEffect(connected) {
        if (connected) {
            if (!flameDone) {
                val flameJob = launch { flameAlpha.animateTo(1f, tween(450)) }
                val scaleJob = launch { brandScale.animateTo(1.15f, tween(450)) }
                flameJob.join()
                scaleJob.join()
                brandAlpha.animateTo(0f, tween(450))
                flameAlpha.animateTo(0f, tween(300))
                flameDone = true
            }
        } else {
            flameDone = false
            flameAlpha.snapTo(0f)
            brandAlpha.snapTo(1f)
            brandScale.snapTo(1f)
        }
    }

    // While connected the ring shows the 30-minute session as a
    // progress arc; it never spins once connected.
    val progressSweep = if (isRunning && !isVerifying) {
        360f * (remainingFraction ?: 1f)
    } else {
        0f
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
            // Connecting: spinning arc. Connected: session progress arc.
            if (isVerifying) {
                drawArc(
                    color = ringColor,
                    startAngle = spinAngle - 90f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            } else if (progressSweep > 0f) {
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = progressSweep,
                    useCenter = false,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        // Power glyph, exactly centered in the circle at all times. The
        // brand text sits below it and only while it is visible: it
        // catches fire on connect and fades away, leaving the glyph
        // exactly centered; it comes back on disconnect.
        Vpn5gPowerIcon(color = glyphColor, size = 84.dp)
        if (brandAlpha.value > 0f) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 58.dp)
                    .graphicsLayer {
                        alpha = brandAlpha.value
                        scaleX = brandScale.value
                        scaleY = brandScale.value
                    },
            ) {
                // Flame glow: radial orange/red gradient behind the text.
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFFF6A00).copy(alpha = flameAlpha.value),
                                    Color(0xFFFF2200).copy(
                                        alpha = flameAlpha.value * 0.6f,
                                    ),
                                    Color.Transparent,
                                ),
                            ),
                            shape = CircleShape,
                        ),
                )
                Text(
                    text = brand,
                    color = lerp(
                        Vpn5gOnDarkMuted,
                        Color(0xFFFF7A00),
                        flameAlpha.value,
                    ),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun Vpn5gCountrySection(
    country: Vpn5gCountryUi,
    onServerTap: (String) -> Unit,
    onLockedTap: (() -> Unit)?,
    /** Premium tab (v1.1.2): the country header toggles the group. */
    expandable: Boolean = false,
    /** Collapsed groups hide their servers until tapped. */
    expanded: Boolean = true,
    onToggleExpand: (() -> Unit)? = null,
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .then(
                    when {
                        onToggleExpand != null -> Modifier.clickable(
                            role = Role.Button,
                            onClickLabel = country.label,
                        ) { onToggleExpand() }
                        onLockedTap != null -> Modifier.clickable(
                            role = Role.Button,
                            onClickLabel = stringResource(R.string.vpn5g_needs_subscription),
                        ) { onLockedTap() }
                        else -> Modifier
                    }
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
            if (expandable) {
                Spacer(Modifier.width(8.dp))
                Vpn5gChevronIcon(
                    color = Vpn5gOnDarkMuted,
                    modifier = Modifier.graphicsLayer {
                        rotationZ = if (expanded) 90f else 0f
                    },
                )
            }
        }
        if (!expanded) return@Column
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
                        enabled = country.isFree || (expandable && onLockedTap != null),
                        role = Role.Button,
                        onClickLabel = displayCountryName(
                            server,
                            stringResource(R.string.vpn5g_country_unknown),
                        ),
                    ) {
                        if (country.isFree) {
                            onServerTap(server.guid)
                        } else {
                            // Locked row in an expandable group opens the
                            // subscription dialog (the header now toggles).
                            onLockedTap?.invoke()
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    text = server.flag,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    // Never the config remark: country name only.
                    Text(
                        text = displayCountryName(
                            server,
                            stringResource(R.string.vpn5g_country_unknown),
                        ),
                        color = Vpn5gOnDark,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.vpn5g_server_subtitle),
                        color = Vpn5gOnDarkMuted,
                        fontSize = 12.sp,
                    )
                }
                // VIP badge on every locked row (v1.1.2).
                if (!country.isFree) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.vpn5g_vip),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Vpn5gAccent)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
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
