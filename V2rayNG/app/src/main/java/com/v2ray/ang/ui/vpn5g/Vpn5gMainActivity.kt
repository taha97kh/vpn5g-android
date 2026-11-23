package com.v2ray.ang.ui.vpn5g

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.v2ray.ang.AppConfig
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.enums.PermissionType
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.vpn5g.AnnouncementRemote
import com.v2ray.ang.handler.vpn5g.AppConfigRemote
import com.v2ray.ang.handler.vpn5g.GithubReleaseAsset
import com.v2ray.ang.handler.vpn5g.PaymentGatewayRemote
import com.v2ray.ang.handler.vpn5g.PromoCodeRemote
import com.v2ray.ang.handler.vpn5g.SubscriptionPlanRemote
import com.v2ray.ang.handler.vpn5g.SubscriptionStatusRemote
import com.v2ray.ang.handler.vpn5g.Vpn5gAnnouncement
import com.v2ray.ang.handler.vpn5g.Vpn5gAppUpdate
import com.v2ray.ang.handler.vpn5g.Vpn5gAuthService
import com.v2ray.ang.handler.vpn5g.Vpn5gAutoConnect
import com.v2ray.ang.handler.vpn5g.Vpn5gConnectEvent
import com.v2ray.ang.handler.vpn5g.Vpn5gConnectMode
import com.v2ray.ang.handler.vpn5g.Vpn5gConnectState
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.BOT_URL
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.CONFIG_SCHEMES
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.DROP_FAILOVER_DEBOUNCE_MS
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.EMPTY_POOL_BACKOFF_MS
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.KEY_UPDATE_LAST_CHECK_MS
import com.v2ray.ang.handler.vpn5g.Vpn5gCountryUi
import com.v2ray.ang.handler.vpn5g.Vpn5gFailedServers
import com.v2ray.ang.handler.vpn5g.Vpn5gGeoIp
import com.v2ray.ang.handler.vpn5g.Vpn5gManualServer
import com.v2ray.ang.handler.vpn5g.Vpn5gNetworkProfile
import com.v2ray.ang.handler.vpn5g.Vpn5gPromoCodes
import com.v2ray.ang.handler.vpn5g.Vpn5gRecoveryLink
import com.v2ray.ang.handler.vpn5g.Vpn5gRemoteConfig
import com.v2ray.ang.handler.vpn5g.Vpn5gServerUi
import com.v2ray.ang.handler.vpn5g.Vpn5gServerValidator
import com.v2ray.ang.handler.vpn5g.Vpn5gSounds
import com.v2ray.ang.handler.vpn5g.Vpn5gSync
import com.v2ray.ang.handler.vpn5g.Vpn5gTimer
import com.v2ray.ang.handler.vpn5g.Vpn5gTunnelCheck
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.ui.base.HelperBaseComponentActivity
import com.v2ray.ang.ui.compose.colorPing
import com.v2ray.ang.ui.compose.colorPingRed
import com.v2ray.ang.util.HttpUtil
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
import java.io.File

enum class Vpn5gDestination {
    HOME, PREMIUM, GUIDE, ME, AUTH
}

/** Server-list tabs on the home screen (X-VPN server-list style). */
enum class Vpn5gServerTab { FREE, ALL, SPECIAL }

/** Post-connect tunnel state. The UI must never show a false "connected". */
enum class Vpn5gTunnelState { IDLE, VERIFYING, VERIFIED, FAILED, UNKNOWN, SWITCHING }

enum class Vpn5gPromoStatus { IDLE, VALID, UNKNOWN_CODE, EXHAUSTED, ALREADY_REDEEMED, INVALID }

/** A manually picked server that turned out unusable (v1.1.5 §3). */
data class Vpn5gManualFailure(
    val serverName: String,
    val reasonText: String,
)

data class Vpn5gHomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isRunning: Boolean = false,
    /** One-button connect state (v1.1.4 §1): the home text comes from here. */
    val connectState: Vpn5gConnectState = Vpn5gConnectState.IDLE,
    /** Auto pick vs the user's manual pick (v1.1.5 §3). */
    val connectMode: Vpn5gConnectMode = Vpn5gConnectMode.AUTO,
    /** GUID of the manually picked free server; null in auto mode. */
    val manualGuid: String? = null,
    /** The manual pick failed: precise reason + return-to-auto offer. */
    val manualServerFailed: Vpn5gManualFailure? = null,
    /** Precise failure reason for the FAILED state; null otherwise. */
    val lastError: String? = null,
    val offline: Boolean = false,
    val notice: String? = null,
    /** Admin-published app announcement (v1.1.5); null = none to show. */
    val announcement: AnnouncementRemote? = null,
    val countries: List<Vpn5gCountryUi> = emptyList(),
    val selectedGuid: String? = null,
    val currentServerName: String? = null,
    val currentServerFlag: String = "",
    val currentPingMillis: Long = 0L,
    /** True when [currentPingMillis] is a real through-the-proxy delay
     * (v1.1.7); false = TCP-only, shown as "—" instead of a number. */
    val currentHasRealPing: Boolean = false,
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
    /** A newer GitHub release is available (v1.1.3): card shown on home. */
    val updateAvailable: Boolean = false,
    /** Display version of the newer release (e.g. "1.1.4"), null when none. */
    val updateVersion: String? = null,
    /** True while the update APK is downloading. */
    val updateDownloading: Boolean = false,
    /** Download progress 0..1 while downloading. */
    val updateDownloadProgress: Float = 0f,
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

    /** Fired with the server GUID a connect chain picked; the Activity starts it. */
    private val _connectEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val connectEvent: SharedFlow<String> = _connectEvent.asSharedFlow()

    /** One connect chain at a time; a new tap cancels the previous one. */
    private var connectChainJob: Job? = null

    /** Periodic tunnel re-verification while connected (v1.1.4 §7). */
    private var reverifyJob: Job? = null
    private var ticksSinceReverify = 0

    /** Fired with the downloaded APK file; the Activity opens the installer. */
    private val _updateInstallEvent = MutableSharedFlow<File>(extraBufferCapacity = 1)
    val updateInstallEvent: SharedFlow<File> = _updateInstallEvent.asSharedFlow()

    /** Release asset picked by the update check; the download source. */
    private var pendingUpdateAsset: GithubReleaseAsset? = null
    private var updateDownloadJob: Job? = null

    private var verifyJob: Job? = null
    private val triedGuids = mutableSetOf<String>()
    private var verifyAttempts = 0
    private var tunnelFailedStop = false
    private var baselineIp: String? = null
    /**
     * True while the verification loop's own failover restart is in flight
     * (v1.1.5 §2 root fix): the daemon stop that belongs to that restart
     * must not cancel the waiting verification job or trigger the drop
     * detector — the job owns the restart and waits for the service to
     * come back with the next server. Written by the verify job, read on
     * the broadcast (main) thread.
     */
    @Volatile
    private var failoverRestartInProgress = false

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

    /** Dismiss key of the app announcement the user closed (v1.1.5). */
    private var dismissedAnnouncementKey: String? = null

    /**
     * The announcement to show from a fetched remote config, or null
     * when there is none, it is disabled, its text is blank, or the
     * user already dismissed this version of it.
     */
    private fun visibleAnnouncement(cached: AppConfigRemote): AnnouncementRemote? {
        val announcement = cached.announcement
        return if (Vpn5gAnnouncement.shouldShow(announcement, dismissedAnnouncementKey)) {
            announcement
        } else {
            null
        }
    }

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

        // Dismissed announcement (v1.1.5): loaded once; a newly published
        // announcement version (new id) is shown again after a dismiss.
        dismissedAnnouncementKey = MmkvManager.decodeSettingsString(
            Vpn5gConstants.KEY_ANNOUNCEMENT_DISMISSED
        )

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
                    announcement = visibleAnnouncement(cached),
                    promoCodes = cached.codes,
                    plans = cached.plans,
                    paymentGateway = cached.payment_gateway,
                    trialEnabled = cached.trial_enabled ?: false,
                    trialHours = cached.trial_hours ?: 24,
                    signedInEmail = Vpn5gAuthService.currentSession()?.email,
                    offline = usedFallback,
                )
            }
            refreshSelection()
            enrichCountries()
        }

        // Restore the manual server pick (v1.1.5 §3): the choice survives
        // restarts; the pick is re-validated on every connect, so a stale
        // stored GUID can never connect to a dead server.
        val savedManualGuid = MmkvManager.decodeSettingsString(
            Vpn5gConstants.KEY_MANUAL_SERVER_GUID
        )?.takeIf { it.isNotBlank() }
        if (savedManualGuid != null) {
            _uiState.update {
                it.copy(
                    connectMode = Vpn5gConnectMode.MANUAL,
                    manualGuid = savedManualGuid,
                )
            }
        }

        // Auto-refresh on entry (and every 5 minutes while open).
        viewModelScope.launch { refresh() }

        // App update check (v1.1.3): once per day at most, silent on failure.
        checkForUpdate()

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

        // Live 30-minute countdown, once a second while connected, plus
        // a 60-second tunnel re-verification while connected (v1.1.4 §7):
        // a tunnel that silently stops carrying traffic must not keep
        // showing "connected".
        viewModelScope.launch {
            while (isActive) {
                delay(1_000L)
                val state = _uiState.value
                if (state.isRunning) {
                    _uiState.update {
                        it.copy(remainingMillis = Vpn5gTimer.remainingMillis())
                    }
                    if (state.connectState == Vpn5gConnectState.CONNECTED) {
                        ticksSinceReverify++
                        if (ticksSinceReverify >= 60) {
                            ticksSinceReverify = 0
                            reverifyTunnel()
                        }
                    } else {
                        ticksSinceReverify = 0
                    }
                }
            }
        }
    }

    override fun onCleared() {
        verifyJob?.cancel()
        updateDownloadJob?.cancel()
        connectChainJob?.cancel()
        reverifyJob?.cancel()
        autoFailoverJob?.cancel()
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
                // v1.1.7: the real-ping probe is skipped while the VPN is
                // running (no throwaway core next to the live one); the
                // real-ping evidence is keyed by server identity, so proven
                // servers keep their real ping across the refresh.
                val result = Vpn5gSync.refresh(
                    getApplication<Application>(),
                    skipRealProbe = _uiState.value.isRunning,
                )
                val cached = Vpn5gRemoteConfig.cached()
                val countries = Vpn5gSync.loadGroupedServers()
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        isLoading = false,
                        offline = result.offline,
                        notice = result.notice ?: cached.notice,
                        announcement = visibleAnnouncement(cached),
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

    /**
     * Best free server (lowest measured ping). A config with no ping
     * result is never returned — it must never be tried first. Servers
     * that failed tunnel verification within the last 6 hours are
     * excluded. Null when no free server has a ping yet.
     */
    fun bestFreeServer(): Vpn5gServerUi? {
        val failed = Vpn5gFailedServers.prune(
            Vpn5gFailedServers.load(), System.currentTimeMillis()
        )
        return Vpn5gAutoConnect.pickBestServer(
            _uiState.value.countries, triedGuids + failed.keys
        )
    }

    /**
     * The display name of a server GUID ("نامشخص" when the GUID is gone
     * from the list), for precise manual-failure messages.
     */
    private fun manualServerName(guid: String? = _uiState.value.manualGuid): String {
        val app = getApplication<Application>()
        if (guid.isNullOrBlank()) return app.getString(R.string.vpn5g_country_unknown)
        val server = _uiState.value.countries.flatMap { it.servers }
            .firstOrNull { it.guid == guid } ?: return app.getString(R.string.vpn5g_country_unknown)
        return displayCountryName(server, app.getString(R.string.vpn5g_country_unknown))
    }

    /**
     * Pick a free server by hand (v1.1.5 §3): the choice is validated
     * exactly like the auto pick (free, pinged, not recently failed) and
     * structurally (the stored profile must still parse), then persisted
     * for the next connect and made the engine's selected server. A bad
     * pick is refused with the precise reason and leaves the previous
     * mode untouched. Returns false when the pick was refused.
     */
    fun selectManualServer(guid: String): Boolean {
        val app = getApplication<Application>()
        val failed = Vpn5gFailedServers.prune(
            Vpn5gFailedServers.load(), System.currentTimeMillis()
        )
        val resolution = Vpn5gManualServer.resolve(
            _uiState.value.countries, guid, failed, System.currentTimeMillis()
        )
        val profile = resolution.server?.let { MmkvManager.decodeServerConfig(guid) }
        val structurallyValid = profile != null &&
            Vpn5gServerValidator.validate(profile).isValid
        if (resolution.server == null || !structurallyValid) {
            val reasonRes = when {
                resolution.server != null -> R.string.vpn5g_manual_invalid
                resolution.problem == Vpn5gManualServer.Problem.NOT_FREE ->
                    R.string.vpn5g_manual_not_free
                resolution.problem == Vpn5gManualServer.Problem.NO_PING ->
                    R.string.vpn5g_manual_no_ping
                resolution.problem == Vpn5gManualServer.Problem.FAILED_RECENTLY ->
                    R.string.vpn5g_manual_failed_recently
                else -> R.string.vpn5g_manual_not_found
            }
            val reason = app.getString(reasonRes)
            _uiState.update {
                it.copy(manualServerFailed = Vpn5gManualFailure(manualServerName(guid), reason))
            }
            return false
        }
        MmkvManager.encodeSettings(Vpn5gConstants.KEY_MANUAL_SERVER_GUID, guid)
        MmkvManager.setSelectServer(guid)
        setManualServerState(guid)
        return true
    }

    /**
     * Record a manual pick without validating it: the caller already
     * chose the server explicitly (a tap while connected) and the
     * tunnel verification after the switch is the judge.
     */
    fun setManualServer(guid: String) {
        MmkvManager.encodeSettings(Vpn5gConstants.KEY_MANUAL_SERVER_GUID, guid)
        MmkvManager.setSelectServer(guid)
        setManualServerState(guid)
    }

    private fun setManualServerState(guid: String) {
        _uiState.update {
            it.copy(
                connectMode = Vpn5gConnectMode.MANUAL,
                manualGuid = guid,
                manualServerFailed = null,
            )
        }
    }

    /** Return to automatic server selection (v1.1.5 §3). */
    fun selectAutoMode() {
        MmkvManager.encodeSettings(Vpn5gConstants.KEY_MANUAL_SERVER_GUID, "")
        _uiState.update {
            it.copy(
                connectMode = Vpn5gConnectMode.AUTO,
                manualGuid = null,
                manualServerFailed = null,
            )
        }
    }

    fun clearManualFailure() {
        _uiState.update { it.copy(manualServerFailed = null) }
    }

    /**
     * One-button connect (v1.1.4 §1, v1.1.5 §2–§3): real internet check →
     * resolve the server (the user's manual pick in MANUAL mode, validated
     * exactly like the auto pick; the fastest free server in AUTO mode) →
     * apply the transport MTU profile → hand the GUID to the Activity,
     * which starts the service. A bad manual pick fails fast with the
     * precise reason and stays Disconnected (FAILED) instead of silently
     * switching to another server — the user returns to auto explicitly.
     * Every failure lands in RETRYING (transient) or FAILED (gave up) with
     * a short Persian message, never a technical error. Only one chain
     * runs at a time; a new tap cancels the previous one.
     */
    fun beginConnect() {
        connectChainJob?.cancel()
        connectChainJob = viewModelScope.launch {
            userStopRequested = false
            _uiState.update {
                it.copy(
                    connectState = Vpn5gAutoConnect.transition(
                        it.connectState, Vpn5gConnectEvent.START
                    ),
                    errorMessage = null,
                    lastError = null,
                    manualServerFailed = null,
                )
            }
            val app = getApplication<Application>()
            // Strip any stored forced QUIC block before the tunnel is built.
            Vpn5gNetworkProfile.removeForcedQuicBlock()
            if (!Vpn5gAutoConnect.hasInternet(app)) {
                _uiState.update {
                    it.copy(
                        connectState = Vpn5gAutoConnect.transition(
                            it.connectState, Vpn5gConnectEvent.INTERNET_FAILED
                        ),
                        errorMessage = app.getString(R.string.vpn5g_no_internet),
                    )
                }
                return@launch
            }
            _uiState.update {
                it.copy(
                    connectState = Vpn5gAutoConnect.transition(
                        it.connectState, Vpn5gConnectEvent.INTERNET_OK
                    ),
                )
            }
            resetTunnelAttempts()
            // On a first launch the live refresh may still be syncing, so
            // wait briefly for a pickable server instead of failing the
            // tap; the 15-second bound keeps a truly empty list from
            // hanging the button. A manual pick that is gone, locked,
            // unpinged or recently failed fails fast — waiting cannot fix
            // any of those — while a pick that is merely not synced yet
            // waits for the list like the auto pick does.
            val deadline = System.currentTimeMillis() + 15_000L
            var guid: String? = null
            var manualProblem: Vpn5gManualServer.Problem? = null
            while (System.currentTimeMillis() < deadline) {
                val failed = Vpn5gFailedServers.prune(
                    Vpn5gFailedServers.load(), System.currentTimeMillis()
                )
                if (_uiState.value.connectMode == Vpn5gConnectMode.MANUAL) {
                    val resolution = Vpn5gManualServer.resolve(
                        _uiState.value.countries,
                        _uiState.value.manualGuid,
                        failed,
                        System.currentTimeMillis(),
                    )
                    manualProblem = resolution.problem
                    guid = resolution.server?.guid
                    if (guid != null) break
                    // Only a missing server can appear while syncing.
                    if (manualProblem != Vpn5gManualServer.Problem.NOT_FOUND) break
                } else {
                    guid = Vpn5gAutoConnect.pickBestServer(
                        _uiState.value.countries, triedGuids + failed.keys
                    )?.guid
                    if (guid != null) {
                        // v1.1.7 connect-time gate: an auto pick is
                        // re-validated before the core ever sees it, so an
                        // unencrypted config (e.g. from a list synced
                        // before the encrypted-only filter) can never
                        // trigger the red dialog — it is skipped and the
                        // next server is tried instead.
                        val profile = MmkvManager.decodeServerConfig(guid)
                        if (profile == null ||
                            !Vpn5gServerValidator.validate(profile).isValid
                        ) {
                            triedGuids += guid
                            Vpn5gFailedServers.save(
                                Vpn5gFailedServers.mark(
                                    failed, guid, System.currentTimeMillis()
                                )
                            )
                            LogUtil.i(
                                AppConfig.TAG,
                                "Vpn5gHomeViewModel: auto pick failed " +
                                    "validation, skipping",
                            )
                            guid = null
                        } else {
                            break
                        }
                    }
                }
                delay(500L)
            }
            if (guid == null) {
                if (_uiState.value.connectMode == Vpn5gConnectMode.MANUAL) {
                    val reasonRes = when (manualProblem) {
                        Vpn5gManualServer.Problem.NOT_FREE -> R.string.vpn5g_manual_not_free
                        Vpn5gManualServer.Problem.NO_PING -> R.string.vpn5g_manual_no_ping
                        Vpn5gManualServer.Problem.FAILED_RECENTLY -> R.string.vpn5g_manual_failed_recently
                        else -> R.string.vpn5g_manual_not_found
                    }
                    val reason = app.getString(reasonRes)
                    val serverName = manualServerName()
                    _uiState.update {
                        it.copy(
                            connectState = Vpn5gAutoConnect.transition(
                                it.connectState, Vpn5gConnectEvent.GIVE_UP
                            ),
                            lastError = reason,
                            manualServerFailed = Vpn5gManualFailure(serverName, reason),
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            connectState = Vpn5gAutoConnect.transition(
                                it.connectState, Vpn5gConnectEvent.SERVER_MISSING
                            ),
                            errorMessage = app.getString(R.string.vpn5g_no_server_selected),
                        )
                    }
                }
                return@launch
            }
            // A manual pick is re-validated structurally before it is
            // tried, exactly like the auto pipeline validates every config
            // at sync time — a corrupt stored profile never reaches the
            // tunnel, and the user gets the precise reason instead. The
            // auto pick is re-validated the same way inside the pick loop
            // above (a stale list synced before the 1.1.7 encrypted-only
            // filter must not reach the core either).
            if (_uiState.value.connectMode == Vpn5gConnectMode.MANUAL) {
                val profile = MmkvManager.decodeServerConfig(guid)
                if (profile == null ||
                    !Vpn5gServerValidator.validate(profile).isValid
                ) {
                    val reason = app.getString(R.string.vpn5g_manual_invalid)
                    _uiState.update {
                        it.copy(
                            connectState = Vpn5gAutoConnect.transition(
                                it.connectState, Vpn5gConnectEvent.GIVE_UP
                            ),
                            lastError = reason,
                            manualServerFailed = Vpn5gManualFailure(manualServerName(), reason),
                        )
                    }
                    return@launch
                }
            }
            _uiState.update {
                it.copy(
                    connectState = Vpn5gAutoConnect.transition(
                        it.connectState, Vpn5gConnectEvent.SERVER_FOUND
                    ),
                )
            }
            Vpn5gNetworkProfile.applyMtu(app)
            // Fresh baseline IP while still disconnected: the init-time
            // baseline can be stale after a network change.
            withContext(Dispatchers.IO) {
                baselineIp = Vpn5gTunnelCheck.publicIpDirect()
            }
            _connectEvent.emit(guid)
            // Safety net (service guide: a broadcast send is not proof the
            // daemon is running): if the service has not reported running
            // within 60 s, fail the chain. A late VPN-permission grant
            // still recovers — the start moves RETRYING back to
            // CREATING_TUNNEL and verification runs.
            val startDeadline = System.currentTimeMillis() + 60_000L
            while (isActive &&
                _uiState.value.connectState == Vpn5gConnectState.CONNECTING &&
                System.currentTimeMillis() < startDeadline
            ) {
                delay(500L)
            }
            if (_uiState.value.connectState == Vpn5gConnectState.CONNECTING) {
                _uiState.update {
                    it.copy(
                        connectState = Vpn5gAutoConnect.transition(
                            it.connectState, Vpn5gConnectEvent.SERVICE_FAILED
                        ),
                        errorMessage = app.getString(R.string.vpn5g_start_failed),
                    )
                }
            }
        }
    }

    /** Re-read the daemon state when the Activity returns to the foreground. */
    fun refreshDaemonState() {
        MessageHelper.sendMsg2Service(getApplication(), AppConfig.MSG_REGISTER_CLIENT, "")
    }

    /**
     * The server to actually connect to when the user taps [guid]: the
     * tapped server itself when it has a real through-the-proxy ping;
     * otherwise the next best free server in a different country, switched
     * to immediately (with a brief notice). A config with a TCP-only ping
     * or no ping result is never tried first. Returns [guid] unchanged
     * when no better server exists.
     */
    fun resolveConnectGuid(guid: String): String {
        val app = getApplication<Application>()
        val server = _uiState.value.countries.flatMap { it.servers }
            .firstOrNull { it.guid == guid } ?: return guid
        if (server.hasRealPing && server.pingMillis > 0) return guid
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

    /**
     * Checks the latest GitHub release (v1.1.3). Runs at most once per day:
     * the timestamp is stored only when the fetch succeeds, so a failed
     * check (e.g. no network) retries on the next launch instead of staying
     * silent for a day. Never shows anything when offline or when the
     * installed version is already the latest.
     */
    fun checkForUpdate() {
        viewModelScope.launch(Dispatchers.IO) {
            val lastCheck = MmkvManager.decodeSettingsLong(KEY_UPDATE_LAST_CHECK_MS, 0L)
            if (System.currentTimeMillis() - lastCheck < Vpn5gAppUpdate.UPDATE_CHECK_INTERVAL_MS) {
                return@launch
            }
            val release = Vpn5gAppUpdate.fetchLatest() ?: return@launch
            MmkvManager.encodeSettings(KEY_UPDATE_LAST_CHECK_MS, System.currentTimeMillis())
            val latestTag = release.tagName ?: return@launch
            if (!Vpn5gAppUpdate.isNewerVersion(latestTag, BuildConfig.VERSION_NAME)) {
                return@launch
            }
            val asset = Vpn5gAppUpdate.pickPlaystoreApkAsset(release) ?: return@launch
            pendingUpdateAsset = asset
            _uiState.update {
                it.copy(
                    updateAvailable = true,
                    updateVersion = Vpn5gAppUpdate.displayVersion(latestTag),
                )
            }
        }
    }

    /** Hides the update card until the next daily check. */
    fun dismissUpdate() {
        _uiState.update { it.copy(updateAvailable = false) }
    }

    /**
     * Downloads the pending update APK into the app cache with visible
     * progress, then asks the Activity to fire the system installer.
     * A failed download shows a toast and leaves the card for a retry.
     */
    fun startUpdateDownload() {
        val asset = pendingUpdateAsset ?: return
        val url = asset.browserDownloadUrl ?: return
        if (_uiState.value.updateDownloading) return
        updateDownloadJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(updateDownloading = true, updateDownloadProgress = 0f) }
            val app = getApplication<Application>()
            val target = File(
                File(app.cacheDir, "update").apply { mkdirs() },
                asset.name?.takeIf { it.endsWith(".apk", ignoreCase = true) } ?: "VPN5G_update.apk",
            )
            val ok = try {
                HttpUtil.downloadToFile(
                    UrlContentRequest(url = url, timeout = 60_000),
                    target,
                ) { downloaded, total ->
                    if (total > 0) {
                        _uiState.update {
                            it.copy(
                                updateDownloadProgress = (downloaded.toFloat() / total)
                                    .coerceIn(0f, 1f),
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Vpn5gHomeViewModel: update download failed", e)
                false
            }
            if (ok && target.exists() && target.length() > 0) {
                _uiState.update { it.copy(updateDownloading = false, updateDownloadProgress = 1f) }
                _updateInstallEvent.emit(target)
            } else {
                runCatching { target.delete() }
                _uiState.update {
                    it.copy(
                        updateDownloading = false,
                        updateDownloadProgress = 0f,
                        errorMessage = app.getString(R.string.vpn5g_update_download_failed),
                    )
                }
            }
        }
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
        reverifyJob?.cancel()
        reverifyJob = null
        ticksSinceReverify = 0
        _uiState.update {
            it.copy(
                autoFailoverNotice = null,
                lastError = null,
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
        connectChainJob?.cancel()
        connectChainJob = null
        autoFailoverJob?.cancel()
        autoFailoverJob = null
        resetTunnelAttempts()
        _uiState.update {
            it.copy(
                connectState = Vpn5gAutoConnect.transition(
                    it.connectState, Vpn5gConnectEvent.USER_DISCONNECT
                ),
                lastError = null,
                manualServerFailed = null,
            )
        }
    }

    /** A server switch while connected: the tunnel is rebuilt and re-verified. */
    fun markSwitching() {
        _uiState.update {
            it.copy(
                connectState = Vpn5gAutoConnect.transition(
                    it.connectState, Vpn5gConnectEvent.RESTARTING
                ),
            )
        }
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

    /**
     * Dismiss the app announcement (v1.1.5): the dismissal survives
     * restarts (persisted by dismiss key), but a newly published
     * announcement version (new id) is shown again.
     */
    fun dismissAnnouncement() {
        val current = _uiState.value.announcement ?: return
        val key = Vpn5gAnnouncement.dismissKey(current)
        dismissedAnnouncementKey = key
        viewModelScope.launch(Dispatchers.IO) {
            MmkvManager.encodeSettings(Vpn5gConstants.KEY_ANNOUNCEMENT_DISMISSED, key)
        }
        _uiState.update { it.copy(announcement = null) }
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
            // The service started: a connect chain in flight moves to
            // CREATING_TUNNEL. A plain state re-read while already
            // CONNECTED (e.g. Activity resume) leaves the state alone.
            // A failover/manual-switch restart also lands here, from
            // SWITCHING_SERVER (v1.1.5 §2).
            val chainState = _uiState.value.connectState
            if (chainState == Vpn5gConnectState.CONNECTING ||
                chainState == Vpn5gConnectState.RETRYING ||
                chainState == Vpn5gConnectState.SWITCHING_SERVER
            ) {
                _uiState.update {
                    it.copy(
                        connectState = Vpn5gAutoConnect.transition(
                            it.connectState, Vpn5gConnectEvent.SERVICE_STARTED
                        ),
                    )
                }
            }
            // Verify the tunnel carries traffic (once per session; the job
            // survives failover restarts so attempts stay bounded). Only
            // while a tunnel is actually being created — never on a plain
            // state re-read while already connected.
            if (_uiState.value.connectState == Vpn5gConnectState.CREATING_TUNNEL &&
                verifyJob?.isActive != true
            ) {
                startTunnelVerification()
            }
        } else {
            Vpn5gTimer.cancelDisconnect(app)
            // A planned failover restart's own stop must not kill the
            // verification job that is waiting for the restart (v1.1.5
            // §2 root fix) — the job owns the stop/start cycle and the
            // next verify attempt. Every other stop cancels verification.
            if (!failoverRestartInProgress) {
                verifyJob?.cancel()
                verifyJob = null
            }
            viewModelScope.launch(Dispatchers.IO) {
                baselineIp = Vpn5gTunnelCheck.publicIpDirect()
            }
            if (!tunnelFailedStop && !failoverRestartInProgress) {
                _uiState.update {
                    it.copy(
                        tunnelState = Vpn5gTunnelState.IDLE,
                        tunnelMessage = null,
                        verifiedExitIp = null,
                        verifiedExitFlag = "",
                    )
                }
            }
            // Unexpected drop? Schedule an auto-failover check after a
            // short debounce (v1.1.5 refinement: was a fixed 3,000 ms).
            // The debounce only covers a transient daemon hiccup that
            // recovers on its own; intentional restarts are already
            // excluded by suppressAutoFailover, so a real drop switches
            // servers fast. The 30-minute timer ends the session at
            // 30:00, so a stop after ~29 minutes is treated as the
            // timer, not a drop.
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
                (wasSession || autoFailoverPending)
            if (shouldFailover) {
                autoFailoverJob?.cancel()
                autoFailoverJob = viewModelScope.launch {
                    delay(DROP_FAILOVER_DEBOUNCE_MS)
                    if (!isActive) return@launch
                    if (_uiState.value.isRunning || userStopRequested ||
                        suppressAutoFailover
                    ) {
                        return@launch
                    }
                    attemptAutoFailover()
                }
            }
            // An intentional restart (server switch / verification
            // failover) keeps its CREATING_TUNNEL state through the
            // stop/start cycle; every other stop ends the chain.
            if (!suppressAutoFailover) {
                _uiState.update {
                    it.copy(
                        connectState = if (shouldFailover) {
                            Vpn5gConnectState.RETRYING
                        } else {
                            Vpn5gConnectState.IDLE
                        },
                    )
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
     * real ping). v1.1.7: there is no attempt cap any more — the failover
     * keeps going until a tunnel verifies or the user disconnects; when
     * no untried real-pinged server remains it refreshes the list and
     * tries again after a backoff, staying in RETRYING (never FAILED).
     * Servers that failed tunnel verification within the last 6 hours are
     * excluded. Shows a brief notice of the switch. In MANUAL mode the
     * user's pick is never silently replaced (v1.1.5 §3): a drop ends the
     * session with the precise reason and a return-to-auto offer instead.
     */
    private fun attemptAutoFailover() {
        val app = getApplication<Application>()
        if (_uiState.value.connectMode == Vpn5gConnectMode.MANUAL) {
            autoFailoverPending = false
            val reason = app.getString(R.string.vpn5g_manual_dropped)
            _uiState.update {
                it.copy(
                    connectState = Vpn5gAutoConnect.transition(
                        it.connectState, Vpn5gConnectEvent.GIVE_UP
                    ),
                    lastError = reason,
                    manualServerFailed = Vpn5gManualFailure(manualServerName(), reason),
                )
            }
            return
        }
        val droppedGuid = MmkvManager.getSelectServer()
        val failed = Vpn5gFailedServers.prune(
            Vpn5gFailedServers.load(), System.currentTimeMillis()
        )
        val candidates = _uiState.value.countries
            .flatMap { it.servers }
            .filter {
                it.isFree && it.hasRealPing && it.guid != droppedGuid &&
                    it.guid !in failed && it.pingMillis > 0
            }
        val next = candidates
            .filter { it.country != droppedCountry }
            .minByOrNull { it.pingMillis }
            ?: candidates.minByOrNull { it.pingMillis }
        if (next == null) {
            // v1.1.7: no untried real-pinged server right now — the chain
            // does NOT give up. Stay in RETRYING, refresh the list after
            // a backoff, and try again; the user can still disconnect.
            // (The minimal refresh here duplicates the server-list part
            // of refresh(): the failover retry only needs fresh servers,
            // not the remote config / auth state that refresh() also loads.)
            autoFailoverPending = true
            _uiState.update {
                it.copy(
                    connectState = Vpn5gConnectState.RETRYING,
                    autoFailoverNotice = app.getString(R.string.vpn5g_refreshing_servers),
                )
            }
            autoFailoverJob?.cancel()
            autoFailoverJob = viewModelScope.launch(Dispatchers.IO) {
                delay(EMPTY_POOL_BACKOFF_MS)
                if (!isActive || userStopRequested || suppressAutoFailover) return@launch
                runCatching {
                    Vpn5gSync.refresh(getApplication<Application>(), skipRealProbe = false)
                }
                if (!isActive || userStopRequested || suppressAutoFailover) return@launch
                val countries = Vpn5gSync.loadGroupedServers()
                _uiState.update { it.copy(countries = countries) }
                if (!isActive || userStopRequested || suppressAutoFailover) return@launch
                attemptAutoFailover()
            }
            return
        }
        autoFailoverAttempts++
        // v1.1.7: the counter is a session diagnostic now (the chain has
        // no attempt cap); logging it keeps the field read, not write-only.
        LogUtil.i(AppConfig.TAG, "Vpn5gHomeViewModel: drop failover #$autoFailoverAttempts")
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
                connectState = Vpn5gAutoConnect.transition(
                    it.connectState, Vpn5gConnectEvent.RESTARTING
                ),
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
     * Post-connect verification loop (v1.1.4 §1, v1.1.5 §2 root fix).
     * After the daemon reports running, the exit IP is fetched through
     * the tunnel and compared with the baseline: a failed fetch or an
     * unchanged exit IP means the server does not tunnel.
     *
     * Root fix for the 1.1.4 failover: starting a new server while the old
     * core is still running does NOT switch servers — CoreServiceManager
     * refuses a second core (`startCoreLoop` returns false while running)
     * and CoreVpnService then stops the service entirely. So a failover
     * never starts the next server directly; it asks the Activity for a
     * clean restart-or-start (the daemon's MSG_STATE_RESTART path stops
     * the old core first, waits, then starts the next server — exactly
     * one active core, no stale TUN or sockets). This job waits for that
     * stop/start cycle instead of dying on it, and the 6-hour failed
     * store keeps the dead server out of the picker.
     *
     * In MANUAL mode a failed manual server is never silently replaced:
     * the chain stops with the precise reason and offers a return to
     * auto. A server that fails verification is never left showing
     * "connected".
     */
    private fun startTunnelVerification() {
        verifyJob?.cancel()
        verifyJob = viewModelScope.launch(Dispatchers.IO) {
            try {
            // v1.1.7: the chain has NO attempt cap — it keeps trying the
            // next pinged free server until one VERIFIES as a real tunnel
            // or the user disconnects (Aref: "تا زمانی که وصل نشده
            // کانفینگ اتومات تمام نشود"). verifyAttempts is a session
            // diagnostic counter only; the chain is bounded sanely by the
            // candidate list (one pass, then a re-refresh) and by
            // per-attempt timeouts, the 6h failed store and the 30-minute
            // session timer — never by a count.
            while (isActive) {
                delay(1_500L)
                if (!_uiState.value.isRunning) return@launch
                val guid = MmkvManager.getSelectServer() ?: return@launch
                _uiState.update {
                    it.copy(
                        connectState = Vpn5gAutoConnect.transition(
                            it.connectState, Vpn5gConnectEvent.TUNNEL_READY
                        ),
                        tunnelState = Vpn5gTunnelState.VERIFYING,
                        tunnelMessage = null,
                    )
                }
                val result = Vpn5gTunnelCheck.verify(baselineIp)
                when (result.verdict) {
                    Vpn5gTunnelCheck.Verdict.VERIFIED -> {
                        val flag = result.exitIp?.let { ip ->
                            Vpn5gGeoIp.countryForIp(getApplication(), ip).flag
                        }.orEmpty()
                        Vpn5gSounds.playConnected()
                        ticksSinceReverify = 0
                        _uiState.update {
                            it.copy(
                                connectState = Vpn5gAutoConnect.transition(
                                    it.connectState, Vpn5gConnectEvent.VERIFIED
                                ),
                                tunnelState = Vpn5gTunnelState.VERIFIED,
                                tunnelMessage = null,
                                verifiedExitIp = result.exitIp,
                                verifiedExitFlag = flag,
                            )
                        }
                        return@launch
                    }
                    // UNKNOWN is a failure, not a success: the check could
                    // not prove traffic flows through the tunnel, so the
                    // next server is tried. "Connected" is never shown on
                    // an unproven tunnel (v1.1.4 §1).
                    Vpn5gTunnelCheck.Verdict.FAILED,
                    Vpn5gTunnelCheck.Verdict.UNKNOWN -> {
                        verifyAttempts++
                        // v1.1.7: the counter is a session diagnostic now
                        // (the chain has no attempt cap); logging it keeps
                        // the field read, not write-only.
                        LogUtil.i(
                            AppConfig.TAG,
                            "Vpn5gHomeViewModel: tunnel verify failed " +
                                "(attempt #$verifyAttempts)",
                        )
                        triedGuids += guid
                        // Keep a dead server out of the auto picker for 6h.
                        val failed = Vpn5gFailedServers.prune(
                            Vpn5gFailedServers.load(), System.currentTimeMillis()
                        )
                        Vpn5gFailedServers.save(
                            Vpn5gFailedServers.mark(
                                failed, guid, System.currentTimeMillis()
                            )
                        )
                        val failedReason = when (result.verdict) {
                            Vpn5gTunnelCheck.Verdict.UNKNOWN ->
                                getApplication<Application>()
                                    .getString(R.string.vpn5g_tunnel_unknown)
                            else ->
                                getApplication<Application>()
                                    .getString(R.string.vpn5g_tunnel_failed)
                        }
                        // Manual pick failed: stop here with the precise
                        // reason and a return-to-auto offer — never switch
                        // silently to another server (v1.1.5 §3).
                        if (_uiState.value.connectMode == Vpn5gConnectMode.MANUAL &&
                            guid == _uiState.value.manualGuid
                        ) {
                            tunnelFailedStop = true
                            LauncherManager.stopService(getApplication())
                            Vpn5gSounds.playError()
                            _uiState.update {
                                it.copy(
                                    connectState = Vpn5gAutoConnect.transition(
                                        it.connectState, Vpn5gConnectEvent.GIVE_UP
                                    ),
                                    tunnelState = Vpn5gTunnelState.FAILED,
                                    tunnelMessage = failedReason,
                                    lastError = failedReason,
                                    manualServerFailed = Vpn5gManualFailure(
                                        manualServerName(guid), failedReason
                                    ),
                                )
                            }
                            return@launch
                        }
                        val failedCountry = _uiState.value.countries
                            .flatMap { it.servers }
                            .firstOrNull { it.guid == guid }?.country
                        var next = nextBestServer(
                            excluding = triedGuids,
                            differentCountryThan = failedCountry,
                        )
                        if (next == null) {
                            // v1.1.7: the pinged free list is exhausted —
                            // the chain does NOT give up. Refresh the list
                            // (new servers, fresh pings, fresh real-ping
                            // probe) and keep going; the 6h failed store
                            // keeps the dead servers out, so a refresh can
                            // only add servers that were not tried. If the
                            // refresh yields no untried candidate either,
                            // wait with backoff in RETRYING and refresh
                            // again — never a FAILED state while the user
                            // has not disconnected.
                            _uiState.update {
                                it.copy(
                                    connectState = Vpn5gConnectState.RETRYING,
                                    tunnelState = Vpn5gTunnelState.SWITCHING,
                                    tunnelMessage = getApplication<Application>()
                                        .getString(R.string.vpn5g_refreshing_servers),
                                )
                            }
                            while (isActive && next == null) {
                                runCatching {
                                    Vpn5gSync.refresh(
                                        getApplication<Application>(), skipRealProbe = false
                                    )
                                }
                                _uiState.update {
                                    it.copy(countries = Vpn5gSync.loadGroupedServers())
                                }
                                next = nextBestServer(
                                    excluding = triedGuids,
                                    differentCountryThan = failedCountry,
                                )
                                if (next == null) {
                                    if (!_uiState.value.isRunning) return@launch
                                    delay(EMPTY_POOL_BACKOFF_MS)
                                }
                            }
                        }
                        val target = next ?: return@launch
                        _uiState.update {
                            it.copy(
                                connectState = Vpn5gAutoConnect.transition(
                                    it.connectState, Vpn5gConnectEvent.SWITCHING
                                ),
                                tunnelState = Vpn5gTunnelState.SWITCHING,
                                tunnelMessage = getApplication<Application>()
                                    .getString(R.string.vpn5g_tunnel_failed_retry),
                            )
                        }
                        // The restart below is intentional: do not let the
                        // drop detector treat it as an unexpected disconnect,
                        // and do not let the daemon stop cancel this job —
                        // the job waits for the restart to come back with
                        // the next server.
                        suppressAutoFailover = true
                        failoverRestartInProgress = true
                        _failoverEvent.emit(target.guid)
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
                        failoverRestartInProgress = false
                        if (!_uiState.value.isRunning) {
                            val reason = getApplication<Application>()
                                .getString(R.string.vpn5g_error_restart_failed)
                            _uiState.update {
                                it.copy(
                                    connectState = Vpn5gAutoConnect.transition(
                                        it.connectState, Vpn5gConnectEvent.GIVE_UP
                                    ),
                                    tunnelState = Vpn5gTunnelState.FAILED,
                                    tunnelMessage = reason,
                                    lastError = reason,
                                )
                            }
                            return@launch
                        }
                    }
                }
            }
            } finally {
                failoverRestartInProgress = false
            }
        }
    }

    /**
     * Next best free server with a REAL through-the-proxy ping (v1.1.7),
     * preferring a DIFFERENT country than [differentCountryThan] (a dead
     * server's country is avoided first); falls back to the same country
     * when nothing else is left. Never returns a config with a TCP-only
     * ping (a middlebox can fake it) or no ping result, and never a server
     * that failed tunnel verification within the last 6 hours.
     */
    private fun nextBestServer(
        excluding: Set<String>,
        differentCountryThan: String? = null,
    ): Vpn5gServerUi? {
        val failed = Vpn5gFailedServers.prune(
            Vpn5gFailedServers.load(), System.currentTimeMillis()
        )
        val candidates = _uiState.value.countries
            .flatMap { it.servers }
            .filter {
                it.isFree && it.hasRealPing && it.guid !in excluding &&
                    it.guid !in failed && it.pingMillis > 0
            }
        if (differentCountryThan.isNullOrBlank()) {
            return candidates.minByOrNull { it.pingMillis }
        }
        return candidates.filter { it.country != differentCountryThan }
            .minByOrNull { it.pingMillis }
            ?: candidates.minByOrNull { it.pingMillis }
    }

    /**
     * Periodic tunnel re-verification while connected (v1.1.4 §7). A failed
     * re-check drops the connection so the drop detector can fail over to
     * another server; a dead tunnel is never left showing "connected".
     */
    private fun reverifyTunnel() {
        reverifyJob?.cancel()
        reverifyJob = viewModelScope.launch(Dispatchers.IO) {
            if (!_uiState.value.isRunning ||
                _uiState.value.connectState != Vpn5gConnectState.CONNECTED
            ) {
                return@launch
            }
            val result = Vpn5gTunnelCheck.verify(baselineIp)
            if (result.verdict == Vpn5gTunnelCheck.Verdict.VERIFIED) return@launch
            if (!_uiState.value.isRunning ||
                _uiState.value.connectState != Vpn5gConnectState.CONNECTED
            ) {
                return@launch
            }
            LogUtil.i(
                AppConfig.TAG,
                "Vpn5gHomeViewModel: periodic re-verify failed; dropping",
            )
            _uiState.update {
                it.copy(
                    connectState = Vpn5gAutoConnect.transition(
                        it.connectState, Vpn5gConnectEvent.VERIFY_FAILED
                    ),
                )
            }
            LauncherManager.stopService(getApplication())
        }
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
                currentHasRealPing = server?.hasRealPing ?: false,
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

    /** APK waiting for the "install unknown apps" grant (Android 8+). */
    private var pendingInstallFile: File? = null

    /**
     * Parsed password-recovery deep link (v1.1.8), waiting to be
     * consumed by the auth screen. Set by [handleDeepLink] when the
     * user returns from the emailed recovery link
     * (`vpn5g://auth/callback`, type=recovery); cleared once the
     * auth screen has opened the new-password form for it.
     */
    private val recoveryLinkFlow = MutableStateFlow<Vpn5gRecoveryLink.Parsed?>(null)

    private val requestVpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                pendingGuid?.let { startV2Ray(it) }
            }
            pendingGuid = null
        }

    private val requestInstallPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (packageManager.canRequestPackageInstalls()) {
                pendingInstallFile?.let { installApk(it) }
            }
            // Still ungranted: the user declined in settings; the update
            // card stays and the user can tap the button again.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {}
        handleDeepLink(intent)
    }

    override fun onResume() {
        super.onResume()
        // Re-read the daemon state when returning to the foreground
        // (v1.1.4 §7): a state change while the app was backgrounded
        // must not leave a stale home screen.
        viewModel.refreshDaemonState()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    /**
     * Fires the system package installer for a downloaded APK. Stock
     * Android never allows a fully silent install: the user confirms once
     * in the system UI. When the "install unknown apps" grant is missing
     * (Android 8+), the user is sent to this app's settings page first
     * and the install is retried when they return.
     */
    private fun installApk(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()
        ) {
            pendingInstallFile = file
            requestInstallPermission.launch(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName"),
                ),
            )
            return
        }
        pendingInstallFile = null
        val uri = FileProvider.getUriForFile(this, "$packageName.cache", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(intent)
    }

    @Composable
    override fun ScreenContent() {
        val uiState by viewModel.uiState.collectAsStateWithLifecycle()
        var destination by remember { mutableStateOf(Vpn5gDestination.HOME) }
        var lockedCountry by remember { mutableStateOf<String?>(null) }

        // Password-recovery deep link (v1.1.8): when the user returns
        // from the emailed link, open the auth screen; the screen
        // consumes the token exactly once and opens the new-password
        // form for it.
        val recoveryLink by recoveryLinkFlow.collectAsStateWithLifecycle()
        LaunchedEffect(recoveryLink) {
            if (recoveryLink != null) destination = Vpn5gDestination.AUTH
        }

        // Tunnel failover: the ViewModel asks for the next server; the
        // Activity restarts the daemon cleanly onto it (v1.1.5 §2 root
        // fix). A plain start while the old core is still running does
        // NOT switch servers — it stops the service — so the failover
        // goes through the daemon's restart path (stop, wait, start with
        // the new select server), falling back to the permission-aware
        // start when the daemon is already stopped.
        LaunchedEffect(Unit) {
            viewModel.failoverEvent.collect { guid ->
                requestServiceRestartOrStart(guid)
            }
        }

        // One-button connect: the ViewModel picked a server after the
        // internet check; the Activity performs the permission-aware start.
        LaunchedEffect(Unit) {
            viewModel.connectEvent.collect { guid ->
                requestServiceStart(guid)
            }
        }

        // App update: the ViewModel asks for the installer once the APK
        // has downloaded; the Activity owns the install intent + grant flow.
        LaunchedEffect(Unit) {
            viewModel.updateInstallEvent.collect { file ->
                installApk(file)
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
                            onSelectManualServer = { guid -> viewModel.selectManualServer(guid) },
                            onSelectAutoMode = { viewModel.selectAutoMode() },
                            onClearManualFailure = { viewModel.clearManualFailure() },
                            onRetryConnect = { viewModel.beginConnect() },
                            onLockedCountryTap = { lockedCountry = it },
                            onOpenBot = { Utils.openUri(this@Vpn5gMainActivity, BOT_URL) },
                            onOpenPremium = { destination = Vpn5gDestination.PREMIUM },
                            onConsumeAutoFailoverNotice = { viewModel.consumeAutoFailoverNotice() },
                            onUpdateDownload = { viewModel.startUpdateDownload() },
                            onDismissUpdate = { viewModel.dismissUpdate() },
                            onDismissAnnouncement = { viewModel.dismissAnnouncement() },
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
                        Vpn5gDestination.GUIDE -> Vpn5gGuideScreen(
                            onBack = { destination = Vpn5gDestination.HOME },
                            onOpenUrl = { url -> Utils.openUri(this@Vpn5gMainActivity, url) },
                        )
                        Vpn5gDestination.ME -> Vpn5gProfileScreen(
                            state = uiState,
                            onBack = { destination = Vpn5gDestination.HOME },
                            onOpenAuth = { destination = Vpn5gDestination.AUTH },
                            onSignOut = { viewModel.signOut() },
                            onOpenUrl = { url -> Utils.openUri(this@Vpn5gMainActivity, url) },
                        )
                        Vpn5gDestination.AUTH -> Vpn5gAuthScreen(
                            onBack = { destination = Vpn5gDestination.ME },
                            recoveryToken = recoveryLink,
                            onRecoveryTokenConsumed = { recoveryLinkFlow.value = null },
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
            Vpn5gDestination.GUIDE to R.string.vpn5g_nav_guide,
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
                                Vpn5gDestination.GUIDE -> Vpn5gHelpIcon(iconColor)
                                Vpn5gDestination.ME -> Vpn5gPersonIcon(iconColor)
                                Vpn5gDestination.AUTH -> {
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
        // One-button connect (v1.1.5 §2–§3): the ViewModel runs the
        // internet check and resolves the server from the current mode
        // (manual pick or auto), then hands the GUID back on connectEvent.
        viewModel.beginConnect()
    }

    private fun connectBestServer() {
        val state = viewModel.uiState.value
        if (state.isRunning) return
        // The "Auto" row always means automatic selection (v1.1.5 §3):
        // picking auto again clears any manual pick.
        viewModel.selectAutoMode()
        viewModel.beginConnect()
    }

    private fun onServerTap(guid: String) {
        val state = viewModel.uiState.value
        if (state.isRunning) {
            if (guid == state.selectedGuid) return
            viewModel.resetTunnelAttempts()
            // Resolve after the reset so a no-ping switch notice survives.
            val resolved = viewModel.resolveConnectGuid(guid)
            // Tapping a server while connected also makes it the manual
            // pick (v1.1.5 §3): the tunnel is rebuilt and re-verified on
            // the new server, and the pick survives the next connect.
            // The post-switch verification judges the server; the pick
            // itself is recorded without a pre-validation side effect.
            viewModel.setManualServer(resolved)
            viewModel.markSwitching()
            viewModel.markRestarting()
            MmkvManager.setSelectServer(resolved)
            LauncherManager.restartService(this)
            return
        }
        // A tap while disconnected picks the server by hand, then the
        // one-button chain validates and connects to it (v1.1.5 §3).
        viewModel.selectManualServer(guid)
        viewModel.beginConnect()
    }

    /**
     * Clean server switch for failover (v1.1.5 §2 root fix): when the
     * daemon is running, restart it onto the new server — the daemon
     * stops the old core first, waits 500 ms, then starts the new one,
     * so exactly one core and one TUN ever exist. When the daemon is
     * already stopped (unexpected drop), fall back to the
     * permission-aware start. The select server is set first, because
     * the daemon's restart path starts whatever is selected.
     */
    private fun requestServiceRestartOrStart(guid: String) {
        MmkvManager.setSelectServer(guid)
        LauncherManager.restartServiceOrStart(this) { requestServiceStart(guid) }
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
        // Password recovery (v1.1.8): the emailed link returns as
        // vpn5g://auth/callback with type=recovery and the session
        // tokens in the fragment. A vpn5g:// link that is not a
        // valid recovery link is rejected here (never passed to the
        // config importer, which would only toast "import failed").
        if (scheme.equals("vpn5g", ignoreCase = true)) {
            val link = Vpn5gRecoveryLink.parse(uri.toString())
            if (link != null) {
                recoveryLinkFlow.value = link
            } else {
                toast(R.string.vpn5g_auth_recovery_link_invalid)
            }
            return
        }
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
/**
 * Prominent in-app update banner (v1.1.3): shown when a newer GitHub
 * release exists. One tap downloads the APK with visible progress and
 * opens the system installer; the user confirms once in the system UI.
 * "Later" hides the card until the next daily check.
 */
@Composable
private fun Vpn5gUpdateCard(
    version: String,
    downloading: Boolean,
    progress: Float,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Vpn5gCyan.copy(alpha = 0.18f))
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.vpn5g_update_title),
            color = Vpn5gOnDark,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.vpn5g_update_body, version),
            color = Vpn5gOnDark,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(12.dp))
        if (downloading) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = Vpn5gCyan,
                trackColor = Vpn5gOnDark.copy(alpha = 0.15f),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.vpn5g_update_downloading,
                    (progress * 100).toInt(),
                ),
                color = Vpn5gOnDarkMuted,
                fontSize = 13.sp,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onUpdate,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Vpn5gCyan,
                        contentColor = Color.Black,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(R.string.vpn5g_update_button),
                        fontWeight = FontWeight.Bold,
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.vpn5g_update_later),
                        color = Vpn5gOnDarkMuted,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Vpn5gHomeScreen(
    state: Vpn5gHomeUiState,
    onRefresh: () -> Unit,
    onToggleConnect: () -> Unit,
    onServerTap: (String) -> Unit,
    onAutoConnect: () -> Unit,
    onSelectManualServer: (String) -> Boolean,
    onSelectAutoMode: () -> Unit,
    onClearManualFailure: () -> Unit,
    onRetryConnect: () -> Unit,
    onLockedCountryTap: (String) -> Unit,
    onOpenBot: () -> Unit,
    onOpenPremium: () -> Unit,
    onConsumeAutoFailoverNotice: () -> Unit,
    onUpdateDownload: () -> Unit,
    onDismissUpdate: () -> Unit,
    onDismissAnnouncement: () -> Unit,
) {
    var entered by remember { mutableStateOf(false) }
    var serverTab by rememberSaveable { mutableStateOf(Vpn5gServerTab.FREE) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    // Free-servers picker (v1.1.5 §3): the row expands to a country
    // list, then to that country's servers. Transient presentation
    // state only; the pick itself lives in the ViewModel.
    var freePickerExpanded by rememberSaveable { mutableStateOf(false) }
    var pickerCountry by rememberSaveable { mutableStateOf<String?>(null) }
    // Auto-row info panel (v1.1.5 §4). Transient presentation state only.
    var autoInfoExpanded by rememberSaveable { mutableStateOf(false) }
    // Premium tab (v1.1.2): country groups start collapsed; tapping a
    // country reveals its servers. Transient presentation state only.
    var specialExpanded by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(Unit) { entered = true }

    val displayCountries = state.countries
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
    val freeCount = freeSections.sumOf { it.servers.size }
    // Free servers always have a real through-the-proxy ping (v1.1.7
    // top-20 selection), so the fastest free server is the best one;
    // never fall back to a TCP-only, no-ping or locked server.
    val bestServer = freeSections.flatMap { it.servers }
        .filter { it.hasRealPing && it.pingMillis > 0 }
        .minByOrNull { it.pingMillis }

    // The home text comes from the connect state machine only (v1.1.4
    // §1, v1.1.5 §2): "connected" appears only after real traffic passed
    // through the tunnel — never from the service running or a ping.
    // The seven spec states are shown verbatim; FAILED shows the precise
    // reason in the error card below the button.
    val statusTextRes = when (state.connectState) {
        Vpn5gConnectState.CHECKING_INTERNET -> R.string.vpn5g_connect_checking_internet
        Vpn5gConnectState.FINDING_SERVER -> R.string.vpn5g_connect_finding_server
        Vpn5gConnectState.CONNECTING -> R.string.vpn5g_connect_connecting
        Vpn5gConnectState.CREATING_TUNNEL -> R.string.vpn5g_connect_creating_tunnel
        Vpn5gConnectState.VERIFYING_TUNNEL -> R.string.vpn5g_connect_verifying_tunnel
        Vpn5gConnectState.SWITCHING_SERVER -> R.string.vpn5g_connect_switching_server
        Vpn5gConnectState.CONNECTED -> R.string.vpn5g_connected
        Vpn5gConnectState.RETRYING -> R.string.vpn5g_connect_retrying
        Vpn5gConnectState.FAILED -> R.string.vpn5g_connect_failed
        Vpn5gConnectState.IDLE -> R.string.vpn5g_status_not_connected
    }
    val statusColor = when (state.connectState) {
        Vpn5gConnectState.CONNECTED -> colorPing
        Vpn5gConnectState.FAILED -> colorPingRed
        Vpn5gConnectState.IDLE -> Vpn5gOnDarkMuted
        // RETRYING is a connecting state, not a failure: the chain is
        // switching servers, so it reads cyan like the other
        // connecting states (v1.1.5 refinement).
        else -> Vpn5gCyan
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
                                    if (state.connectState == Vpn5gConnectState.CONNECTED) colorPing else Vpn5gOnDarkMuted.copy(alpha = 0.4f)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = stringResource(
                                    if (state.connectState == Vpn5gConnectState.CONNECTED) R.string.vpn5g_connected
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
                        connectState = state.connectState,
                        remainingFraction = state.remainingMillis
                            ?.let { (it / (30L * 60_000L).toFloat()).coerceIn(0f, 1f) },
                        onClick = onToggleConnect,
                    )
                }
            }

            val newVersion = state.updateVersion
            if (state.updateAvailable && newVersion != null) {
                item {
                    // In-app update banner (v1.1.3): shown when a newer
                    // GitHub release exists; one tap downloads + installs.
                    Vpn5gUpdateCard(
                        version = newVersion,
                        downloading = state.updateDownloading,
                        progress = state.updateDownloadProgress,
                        onUpdate = onUpdateDownload,
                        onDismiss = onDismissUpdate,
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
                                    // v1.1.7: a TCP-only ping is never shown
                                    // as a number — only a real
                                    // through-the-proxy delay is.
                                    text = if (state.currentHasRealPing) {
                                        stringResource(
                                            R.string.vpn5g_ping_ms,
                                            state.currentPingMillis,
                                        )
                                    } else {
                                        "—"
                                    },
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

            // Connection failed (v1.1.5 §2): the precise reason, still
            // Disconnected, with a retry. The card only appears in the
            // FAILED state, when lastError is set — and only when there
            // is no manual-failure card: a failed manual pick sets both
            // lastError and manualServerFailed, and the manual card
            // below (reason + return-to-auto) is the only card shown,
            // so the same reason is never shown twice, stacked.
            if (state.connectState == Vpn5gConnectState.FAILED &&
                state.manualServerFailed == null
            ) {
                item(key = "failed_card") {
                    Vpn5gErrorCard(
                        title = stringResource(R.string.vpn5g_connect_failed),
                        body = state.lastError
                            ?: stringResource(R.string.vpn5g_start_failed),
                        primaryLabel = stringResource(R.string.vpn5g_retry),
                        onPrimaryClick = onRetryConnect,
                        secondaryLabel = null,
                        onSecondaryClick = null,
                    )
                }
            }

            // Manual pick failed (v1.1.5 §3): the precise reason for the
            // picked server, with a return-to-auto action. The pick is
            // never silently replaced.
            state.manualServerFailed?.let { failure ->
                item(key = "manual_failed_card") {
                    Vpn5gErrorCard(
                        title = stringResource(
                            R.string.vpn5g_manual_failed_title, failure.serverName
                        ),
                        body = failure.reasonText,
                        primaryLabel = stringResource(R.string.vpn5g_manual_back_to_auto),
                        onPrimaryClick = {
                            onSelectAutoMode()
                            onClearManualFailure()
                        },
                        secondaryLabel = stringResource(R.string.vpn5g_close),
                        onSecondaryClick = onClearManualFailure,
                    )
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

            // Admin-published app announcement (v1.1.5): a dismissible
            // card under the notice; nothing is shown when there is no
            // live announcement.
            state.announcement?.let { announcement ->
                item {
                    Vpn5gAnnouncementCard(
                        announcement = announcement,
                        onDismiss = onDismissAnnouncement,
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
                        text = stringResource(R.string.vpn5g_empty_list),
                        color = Vpn5gOnDarkMuted,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // "Free Servers" picker (v1.1.5 §3): a real dropdown — tap
            // the row for the country list (flag + server count + best
            // ping), tap a country for its servers with health + real
            // ping, tap a server to pick it by hand. The pick is
            // persisted and validated exactly like the auto pick; a bad
            // pick is refused with the precise reason, never silently
            // replaced. The list below (tabs + sections) is untouched.
            if (freeSections.isNotEmpty() && serverTab != Vpn5gServerTab.SPECIAL) {
                item(key = "free_servers_row") {
                    val manualServer = state.manualGuid?.let { guid ->
                        freeSections.flatMap { it.servers }
                            .firstOrNull { it.guid == guid }
                    }
                    val manualSubtitle =
                        if (state.connectMode == Vpn5gConnectMode.MANUAL && manualServer != null) {
                            // v1.1.7: the ping suffix is shown only for a
                            // real through-the-proxy delay, never for a
                            // TCP-only one.
                            stringResource(R.string.vpn5g_manual_label) + ": " +
                                manualServer.flag + " " + displayCountryName(
                                manualServer,
                                stringResource(R.string.vpn5g_country_unknown),
                            ) + if (manualServer.hasRealPing) {
                                " — " + stringResource(
                                    R.string.vpn5g_ping_ms, manualServer.pingMillis
                                )
                            } else {
                                ""
                            }
                        } else {
                            null
                        }
                    Vpn5gExpandableSection(
                        title = stringResource(R.string.vpn5g_free_servers),
                        subtitle = manualSubtitle,
                        badge = stringResource(R.string.vpn5g_servers_count, freeCount),
                        leading = { Vpn5gBoltIcon(color = Vpn5gCyan, size = 26.dp) },
                        expanded = freePickerExpanded,
                        onToggleExpand = {
                            freePickerExpanded = !freePickerExpanded
                            if (!freePickerExpanded) pickerCountry = null
                        },
                    ) {
                        if (pickerCountry == null) {
                            freeSections.forEach { section ->
                                val bestPing = section.servers
                                    .filter { it.hasRealPing && it.pingMillis > 0 }
                                    .minOfOrNull { it.pingMillis }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable(
                                            role = Role.Button,
                                            onClickLabel = section.label,
                                        ) { pickerCountry = section.label }
                                        .padding(vertical = 10.dp),
                                ) {
                                    Text(
                                        text = section.label,
                                        color = Vpn5gOnDark,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.vpn5g_servers_count,
                                            section.servers.size,
                                        ),
                                        color = Vpn5gOnDarkMuted,
                                        fontSize = 13.sp,
                                    )
                                    if (bestPing != null) {
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(
                                                R.string.vpn5g_ping_ms, bestPing
                                            ),
                                            color = colorPing,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Vpn5gChevronIcon(color = Vpn5gOnDarkMuted)
                                }
                            }
                        } else {
                            val pickedSection = freeSections
                                .firstOrNull { it.label == pickerCountry }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable(
                                        role = Role.Button,
                                        onClickLabel = stringResource(
                                            R.string.vpn5g_picker_back
                                        ),
                                    ) { pickerCountry = null }
                                    .padding(vertical = 10.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.vpn5g_picker_back),
                                    color = Vpn5gAccentLight,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            pickedSection?.servers?.forEach { server ->
                                val selected = server.guid == state.manualGuid
                                val hasPing = server.hasRealPing && server.pingMillis > 0
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (selected) Vpn5gAccent.copy(alpha = 0.35f)
                                            else Color.Transparent
                                        )
                                        .clickable(
                                            enabled = hasPing,
                                            role = Role.Button,
                                            onClickLabel = displayCountryName(
                                                server,
                                                stringResource(
                                                    R.string.vpn5g_country_unknown
                                                ),
                                            ),
                                        ) {
                                            if (onSelectManualServer(server.guid)) {
                                                freePickerExpanded = false
                                                pickerCountry = null
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 10.dp),
                                ) {
                                    Text(
                                        text = server.flag,
                                        fontSize = 22.sp,
                                        modifier = Modifier.padding(end = 12.dp),
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = displayCountryName(
                                                server,
                                                stringResource(
                                                    R.string.vpn5g_country_unknown
                                                ),
                                            ),
                                            color = Vpn5gOnDark,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            text = if (hasPing) {
                                                stringResource(
                                                    R.string.vpn5g_picker_healthy
                                                )
                                            } else {
                                                stringResource(
                                                    R.string.vpn5g_picker_no_ping
                                                )
                                            },
                                            color = if (hasPing) {
                                                colorPing
                                            } else {
                                                Vpn5gOnDarkMuted
                                            },
                                            fontSize = 13.sp,
                                        )
                                    }
                                    Text(
                                        text = if (hasPing) {
                                            stringResource(
                                                R.string.vpn5g_ping_ms,
                                                server.pingMillis,
                                            )
                                        } else {
                                            "—"
                                        },
                                        color = colorPing,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    if (selected) {
                                        Spacer(Modifier.width(8.dp))
                                        Vpn5gCheckIcon(color = Vpn5gCyan, size = 20.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // "Auto" row (v1.1.5 §3–§4): the best free server, one tap to
            // connect — it works independently of any manual pick (tapping
            // it clears the manual pick first). The chevron opens an info
            // panel explaining auto mode and, while a manual pick is
            // active, offers the return to auto.
            if (bestServer != null && serverTab != Vpn5gServerTab.SPECIAL) {
                item(key = "auto_row") {
                    Vpn5gExpandableSection(
                        title = stringResource(R.string.vpn5g_auto_server),
                        subtitle = bestServer.flag + " " + displayCountryName(
                            bestServer,
                            stringResource(R.string.vpn5g_country_unknown),
                        ),
                        badge = if (bestServer.hasRealPing && bestServer.pingMillis > 0L) {
                            stringResource(R.string.vpn5g_ping_ms, bestServer.pingMillis)
                        } else {
                            "—"
                        },
                        leading = { Vpn5gRoutesIcon(color = Vpn5gCyan, size = 26.dp) },
                        expanded = autoInfoExpanded,
                        onToggleExpand = { autoInfoExpanded = !autoInfoExpanded },
                        onHeaderClick = onAutoConnect,
                    ) {
                        Text(
                            text = stringResource(R.string.vpn5g_auto_info_body),
                            color = Vpn5gOnDarkMuted,
                            fontSize = 14.sp,
                        )
                        if (state.connectMode == Vpn5gConnectMode.MANUAL) {
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    onSelectAutoMode()
                                    autoInfoExpanded = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Vpn5gAccent,
                                    contentColor = Color.White,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.vpn5g_manual_back_to_auto
                                    ),
                                    fontSize = 15.sp,
                                )
                            }
                        }
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
                            text = if (bestServer.hasRealPing && bestServer.pingMillis > 0L) {
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

            // Free tab (v1.1.2): no server rows at all — only the
            // free-service picker and the auto row above. The list is
            // always shown on the other tabs (v1.1.5 §3: the free-servers
            // row is now the picker, not a list toggle).
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
 * Admin-published app announcement card (v1.1.5): text plus an optional
 * image, shown on the home screen under the notice. Dismissible: the
 * dismiss is owned by the ViewModel (persisted by announcement id or
 * text) and this composable only calls [onDismiss]. The image is loaded
 * with Coil; a failed image hides its slot so the card never shows a
 * blank box — the text always remains.
 */
@Composable
private fun Vpn5gAnnouncementCard(
    announcement: AnnouncementRemote,
    onDismiss: () -> Unit,
) {
    var showImage by remember { mutableStateOf(!announcement.image_url.isNullOrBlank()) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Vpn5gAccent.copy(alpha = 0.25f))
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = announcement.text.orEmpty(),
                color = Vpn5gOnDark,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.vpn5g_close),
                    color = Vpn5gOnDarkMuted,
                    fontSize = 13.sp,
                )
            }
        }
        if (showImage) {
            Spacer(Modifier.height(8.dp))
            AsyncImage(
                model = announcement.image_url.orEmpty(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { showImage = false },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
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

/**
 * Failure card (v1.1.5 §2–§3): the precise reason in plain words, never
 * a technical error, with a primary action (retry / return to auto) and
 * an optional secondary action (dismiss). Shown only in the FAILED state
 * or when the manual pick failed — the app stays Disconnected behind it.
 */
@Composable
private fun Vpn5gErrorCard(
    title: String,
    body: String,
    primaryLabel: String,
    onPrimaryClick: () -> Unit,
    secondaryLabel: String?,
    onSecondaryClick: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colorPingRed.copy(alpha = 0.15f))
            .padding(14.dp),
    ) {
        Text(
            text = title,
            color = Vpn5gOnDark,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = body,
            color = Vpn5gOnDark,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(
                onClick = onPrimaryClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Vpn5gAccent,
                    contentColor = Color.White,
                ),
                modifier = Modifier.weight(1f),
            ) {
                Text(text = primaryLabel, fontSize = 15.sp)
            }
            if (secondaryLabel != null && onSecondaryClick != null) {
                TextButton(
                    onClick = onSecondaryClick,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = secondaryLabel,
                        color = Vpn5gAccentLight,
                        fontSize = 15.sp,
                    )
                }
            }
        }
    }
}

/** Big circular power button with a cyan ring (X-VPN style). */
@Composable
private fun Vpn5gPowerButton(
    connectState: Vpn5gConnectState,
    remainingFraction: Float?,
    onClick: () -> Unit,
) {
    // Derived from the connect state machine only (v1.1.4 §1, v1.1.5
    // §2): the ring spins while a tunnel is being created or verified
    // or while the chain is switching servers or retrying, shows the
    // session arc only once the tunnel is verified, and turns red only
    // while failed.
    val isVerifying = connectState == Vpn5gConnectState.CHECKING_INTERNET ||
        connectState == Vpn5gConnectState.FINDING_SERVER ||
        connectState == Vpn5gConnectState.CONNECTING ||
        connectState == Vpn5gConnectState.CREATING_TUNNEL ||
        connectState == Vpn5gConnectState.VERIFYING_TUNNEL ||
        connectState == Vpn5gConnectState.SWITCHING_SERVER ||
        // RETRYING is a connecting state (v1.1.5 refinement): the ring
        // spins cyan while the chain switches servers, never red.
        connectState == Vpn5gConnectState.RETRYING
    val isFailed = connectState == Vpn5gConnectState.FAILED
    val connected = connectState == Vpn5gConnectState.CONNECTED
    val ringColor = when {
        isFailed -> colorPingRed
        connected || isVerifying -> Vpn5gCyan
        else -> Vpn5gOnDarkMuted.copy(alpha = 0.5f)
    }
    val glyphColor = when {
        isFailed -> colorPingRed
        connected -> Color.White
        else -> Vpn5gOnDarkMuted
    }
    // "Direct Connect": the 4ref.ai brand sits on the circle itself,
    // under the power glyph.
    val brand = stringResource(R.string.vpn5g_connect_brand)

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
    val progressSweep = if (connected) {
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
                    // v1.1.7: a TCP-only ping is never shown as a number.
                    text = if (server.hasRealPing && server.pingMillis > 0L) {
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
