package com.v2ray.ang.ui.vpn5g

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.v2ray.ang.extension.toSpeedString
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.SettingsManager
import com.v2ray.ang.handler.vpn5g.AdsRemote
import com.v2ray.ang.handler.vpn5g.AnnouncementRemote
import com.v2ray.ang.handler.vpn5g.AppConfigRemote
import com.v2ray.ang.handler.vpn5g.GithubReleaseAsset
import com.v2ray.ang.handler.vpn5g.PaymentGatewayRemote
import com.v2ray.ang.handler.vpn5g.PromoCodeRemote
import com.v2ray.ang.handler.vpn5g.SubscriptionPlanRemote
import com.v2ray.ang.handler.vpn5g.SubscriptionStatusRemote
import com.v2ray.ang.handler.vpn5g.Vpn5gAds
import com.v2ray.ang.handler.vpn5g.Vpn5gAnnouncement
import com.v2ray.ang.handler.vpn5g.Vpn5gAppUpdate
import com.v2ray.ang.handler.vpn5g.Vpn5gAuthService
import com.v2ray.ang.handler.vpn5g.Vpn5gAutoConnect
import com.v2ray.ang.handler.vpn5g.Vpn5gBatteryOptimization
import com.v2ray.ang.handler.vpn5g.Vpn5gConnectEvent
import com.v2ray.ang.handler.vpn5g.Vpn5gConnectMode
import com.v2ray.ang.handler.vpn5g.Vpn5gConnectState
import com.v2ray.ang.handler.vpn5g.Vpn5gConnectionPersistence
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.BOT_URL
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.SUPPORT_BOT_URL
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.CONFIG_SCHEMES
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.DROP_FAILOVER_DEBOUNCE_MS
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.EMPTY_POOL_BACKOFF_MS
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.KEY_UPDATE_LAST_CHECK_MS
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.MAX_DROP_FAILOVER_ROUNDS
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.WATCHDOG_MAX_CONSECUTIVE_FAILURES
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.WATCHDOG_PROBE_INTERVAL_TICKS
import com.v2ray.ang.handler.vpn5g.Vpn5gCountryUi
import com.v2ray.ang.handler.vpn5g.Vpn5gCrashHandler
import com.v2ray.ang.handler.vpn5g.Vpn5gFailedServers
import com.v2ray.ang.handler.vpn5g.Vpn5gGeoIp
import com.v2ray.ang.handler.vpn5g.Vpn5gManualServer
import com.v2ray.ang.handler.vpn5g.Vpn5gNetworkProfile
import com.v2ray.ang.handler.vpn5g.Vpn5gOperator
import com.v2ray.ang.handler.vpn5g.Vpn5gPromoCodes
import com.v2ray.ang.handler.vpn5g.Vpn5gRecoveryLink
import com.v2ray.ang.handler.vpn5g.Vpn5gRemoteConfig
import com.v2ray.ang.handler.vpn5g.Vpn5gRouting
import com.v2ray.ang.handler.vpn5g.Vpn5gRoutingMode
import com.v2ray.ang.handler.vpn5g.Vpn5gServerUi
import com.v2ray.ang.handler.vpn5g.Vpn5gServerValidator
import com.v2ray.ang.handler.vpn5g.Vpn5gSounds
import com.v2ray.ang.handler.vpn5g.Vpn5gSpeedMonitor
import com.v2ray.ang.handler.vpn5g.Vpn5gStartupProgress
import com.v2ray.ang.handler.vpn5g.Vpn5gSubscriptionStatus
import com.v2ray.ang.handler.vpn5g.Vpn5gSync
import com.v2ray.ang.handler.vpn5g.Vpn5gTamperCheck
import com.v2ray.ang.handler.vpn5g.Vpn5gTimer
import com.v2ray.ang.handler.vpn5g.Vpn5gTunnelCheck
import com.v2ray.ang.handler.vpn5g.Vpn5gVolumeStore
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.ui.base.HelperBaseComponentActivity
import com.v2ray.ang.ui.compose.ThemeManager
import com.v2ray.ang.ui.compose.colorPing
import com.v2ray.ang.ui.compose.colorPingRed
import com.v2ray.ang.ui.perappproxy.PerAppProxyActivity
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

enum class Vpn5gDestination {
    HOME, PREMIUM, GUIDE, ME, AUTH,
    /** v1.1.21: the server list moved out of home into its own screen. */
    SERVER_SELECT,
    /** v1.1.21: Lantern routing-mode screen. */
    ROUTING,
    /** v1.1.21: Lantern features page. */
    FEATURES,
}

/** Server-list tabs on the home screen (X-VPN server-list style). */
enum class Vpn5gServerTab { FREE, ALL, SPECIAL }

/** Post-connect tunnel state. The UI must never show a false "connected". */
enum class Vpn5gTunnelState { IDLE, VERIFYING, VERIFIED, FAILED, UNKNOWN, SWITCHING }

enum class Vpn5gPromoStatus { IDLE, VALID, UNKNOWN_CODE, EXHAUSTED, ALREADY_REDEEMED, INVALID, REDEEMED, EXPIRED, NOT_YOURS, NOT_SIGNED_IN, WRONG_SECTION, INVALID_EMAIL, NO_REGISTRATION, SERVER_UNAVAILABLE, NOT_APPLIED }

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
    /**
     * Startup notice (v1.1.21, Aref 2026-11-25): shown on the FIRST
     * entry after install only (v1.1.22 correction — never on later
     * launches). [startupNoticeShownBefore] is the persisted part:
     * true once the notice was displayed on an earlier launch
     * (MMKV, loaded at init); it is never changed during the
     * session, so marking the notice shown on first display does
     * not hide it mid-session. [startupNoticeDismissed] is the
     * in-memory part: dismiss hides it for the rest of this session
     * only.
     */
    val startupNoticeDismissed: Boolean = false,
    val startupNoticeShownBefore: Boolean = false,
    /**
     * Current page of the startup notice (v1.1.21): 0 = free servers,
     * 1 = gift codes + Premium countries, 2 = Terms & Conditions +
     * login. In-memory only, like [startupNoticeDismissed].
     */
    val startupNoticePage: Int = 0,
    /**
     * Terms & Conditions accepted (v1.1.21): true once the user ticked
     * the checkbox on the notice's last page. PERSISTED in MMKV — the
     * user is never asked to re-tick on later launches.
     */
    val termsAccepted: Boolean = false,
    val countries: List<Vpn5gCountryUi> = emptyList(),
    val selectedGuid: String? = null,
    val currentServerName: String? = null,
    val currentServerFlag: String = "",
    /** Protocol of the selected server (v1.1.21): shown in the
     * home status card's current-server row. */
    val currentServerProtocol: String = "",
    val currentPingMillis: Long = 0L,
    /** True when [currentPingMillis] is a real through-the-proxy delay
     * (v1.1.7); false = TCP-only, shown as "—" instead of a number. */
    val currentHasRealPing: Boolean = false,
    val remainingMillis: Long? = null,
    /**
     * The signed-in user's own subscription (v1.1.11): null when
     * signed out or never fetched. [isSubscriber] is the gate for
     * the unlimited session, the full server pool and the fast
     * failover; the Premium screen shows the plan + remaining days.
     */
    val userSubscription: Vpn5gSubscriptionStatus.Vpn5gUserSubscription? = null,
    val isSubscriber: Boolean = false,
    /** True while the GIFT tier is active (v1.1.14): top-100 pool, no 30-minute cutoff. */
    val isGift: Boolean = false,
    /** Whole remaining hours of the gift tier (0 when not a gift). */
    val giftRemainingHours: Long = 0L,
    /**
     * On-device measured tunnel bytes for the current grant (v1.1.18,
     * Model B): cumulative across sessions, reset only when a new
     * grant starts. The subscription panel shows it against the
     * grant's volume cap; 0 when there is no grant to meter.
     */
    val volumeConsumedBytes: Long = 0L,
    val subscriptionStatus: SubscriptionStatusRemote? = null,
    val errorMessage: String? = null,
    val tunnelState: Vpn5gTunnelState = Vpn5gTunnelState.IDLE,
    val tunnelMessage: String? = null,
    val verifiedExitIp: String? = null,
    val verifiedExitFlag: String = "",
    val signedInEmail: String? = null,
    /** Live tunnel-only speeds, bytes/s (v1.1.14); 0 = no data yet / disconnected. */
    val downloadSpeedBps: Long = 0L,
    val uploadSpeedBps: Long = 0L,
    /** Current network operator label, e.g. "وای‌فای" / "ایرانسل" (v1.1.14). */
    val operatorLabel: String? = null,
    /** Startup sync (v1.1.14): real 0→100% server-update progress. */
    val startupProgress: Float = 0f,
    /** True once the startup sync finished with at least one server. */
    val startupDone: Boolean = false,
    /** True when the startup sync finished with no server (retry state). */
    val startupError: Boolean = false,
    val promoCodes: List<PromoCodeRemote>? = null,
    /**
     * v1.1.16: gift codes and purchased subscription codes have
     * SEPARATE sections with their OWN entry boxes — never a shared
     * input. Each section tracks its own input + redeem status.
     */
    val giftCodeInput: String = "",
    val giftCodeStatus: Vpn5gPromoStatus = Vpn5gPromoStatus.IDLE,
    val subCodeInput: String = "",
    val subCodeStatus: Vpn5gPromoStatus = Vpn5gPromoStatus.IDLE,
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
    /** Remote ad config (v1.1.15): null = no ad config published, no ad. */
    val ads: AdsRemote? = null,
    /** Interstitial ad currently on screen, or null (v1.1.15). */
    val interstitialAd: Vpn5gAds.InterstitialAdUi? = null,
    /** Expanded locked-country group keys in Premium (v1.1.15); persisted. */
    val expandedCountryKeys: Set<String> = emptySet(),
    /** True when the startup signature check failed (v1.1.15, H3): the
     * build is treated as tampered — premium features are refused and
     * the free tier is shown, never a crash. */
    val tamperDetected: Boolean = false,
    /** A crash report from an earlier run is waiting (v1.1.16): the
     * home screen offers to share it with support. */
    val crashReportAvailable: Boolean = false,
    /**
     * Routing mode (v1.1.21, Lantern routing screen): the mode and the
     * direct-first switch, loaded from MMKV at startup. The tunnel
     * reads the mapped upstream prefs at the next connect.
     */
    val routingMode: Vpn5gRoutingMode = Vpn5gRoutingMode.SMART,
    val directFirst: Boolean = true,
    /** Stored language code ("auto" = system), for the drawer (v1.1.21). */
    /** v1.1.22: default locale is Persian (fa), not "auto". */
    val languageCode: String = "fa",
    /** Stored theme mode ("0" = system, "1" = light, "2" = dark). */
    val themeMode: String = "0",
)

/**
 * Whether the paged startup notice shows (v1.1.22, pure): only on the
 * first entry after install — never when it was already shown on an
 * earlier launch ([shownBefore]) and never after this session's
 * dismiss. JVM-tested in [Vpn5gStartupNoticeTest].
 */
internal fun shouldShowStartupNotice(shownBefore: Boolean, dismissed: Boolean): Boolean {
    return !shownBefore && !dismissed
}

/**
 * Home-screen ViewModel: owns sync, running state (via the daemon broadcast,
 * same channel as MainRepository), the 5-minute auto-refresh ticker and the
 * 1-second session-countdown ticker, plus post-connect tunnel verification
 * with bounded auto-failover to the next best server. All network/disk work
 * runs on Dispatchers.IO; Compose only ever sees immutable StateFlow state.
 */
class Vpn5gHomeViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        /**
         * Hard bound for the first screen (v1.1.16, Aref): the initial
         * entry / login page load + update must complete in UNDER 20
         * seconds, even on a fresh install with an empty cache. The
         * empty-cache path runs the quick refresh inside this bound
         * and opens the home with whatever verified — never blocks on
         * the full multi-minute sync.
         */
        const val STARTUP_DEADLINE_MS = 20_000L
    }

    private val _uiState = MutableStateFlow(Vpn5gHomeUiState())
    val uiState: StateFlow<Vpn5gHomeUiState> = _uiState.asStateFlow()

    private val _failoverEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val failoverEvent: SharedFlow<String> = _failoverEvent.asSharedFlow()

    /** Fired with the server GUID a connect chain picked; the Activity starts it. */
    private val _connectEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val connectEvent: SharedFlow<String> = _connectEvent.asSharedFlow()

    /** One connect chain at a time; a new tap cancels the previous one. */
    private var connectChainJob: Job? = null

    /**
     * Disconnect timeout (v1.1.18): if the service never reports
     * stopped after a user disconnect, the home lands in IDLE after
     * [Vpn5gAutoConnect.DISCONNECT_TIMEOUT_MS] instead of sitting in
     * DISCONNECTING forever. Cancelled when the stop lands or the
     * service comes back.
     */
    private var disconnectTimeoutJob: Job? = null

    /** Periodic tunnel re-verification while connected (v1.1.4 §7). */
    private var reverifyJob: Job? = null
    private var ticksSinceReverify = 0

    /** In-session tunnel watchdog (v1.1.11): light exit-IP probe. */
    private var watchdogJob: Job? = null
    private var watchdogTicks = 0
    private var watchdogFailures = 0

    /**
     * Drop-failover rounds with an empty pool (v1.1.11): counts the
     * no-candidate refresh rounds in [attemptAutoFailover]; after
     * [MAX_DROP_FAILOVER_ROUNDS] the chain gives up with a clear
     * error instead of looping forever on a dead network.
     */
    private var dropFailoverRounds = 0

    /** Persistent-connection reconnect (v1.1.12): bounded re-runs of the connect chain after an unexpected stop. */
    private var persistentReconnectJob: Job? = null
    private var persistentReconnectAttempts = 0

    /** Fired with the downloaded APK file; the Activity opens the installer. */
    private val _updateInstallEvent = MutableSharedFlow<File>(extraBufferCapacity = 1)
    val updateInstallEvent: SharedFlow<File> = _updateInstallEvent.asSharedFlow()

    /**
     * Fired once, on the first connect, when the battery-optimization
     * exemption is not yet granted (v1.1.12); the Activity owns the
     * system intent.
     */
    private val _batteryOptEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val batteryOptEvent: SharedFlow<Unit> = _batteryOptEvent.asSharedFlow()

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
    /**
     * v1.1.17: the one startup auto-connect has fired (or was
     * disarmed by a manual connect/disconnect). See
     * [maybeStartupAutoConnect].
     */
    private var startupAutoConnectDone = false
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
     * True when the startup signature check failed (v1.1.15, H3). The
     * field is the authority; [Vpn5gHomeUiState.tamperDetected] mirrors
     * it for the UI. A tampered build gets the free tier everywhere.
     */
    private var tamperDetected = false

    /**
     * User connects STARTED this app session (v1.1.15): connect #1 has
     * no forced ad; connects #2 and later (each after a disconnect)
     * show the interstitial first. Counts user-initiated attempts only
     * ([beginConnect] with userInitiated) — auto-reconnects and
     * failover restarts never count and never show an ad.
     */
    private var completedSessions = 0

    /** Local ad-impression ledger (v1.1.15): ad id -> counters, persisted. */
    private var adImpressions: Vpn5gAds.AdImpressionStore = Vpn5gAds.AdImpressionStore()

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

    /**
     * Runtime tamper check (v1.1.15, H3): verifies the app's own
     * signing certificate against the NEW fixed key. Runs once, before
     * the startup sync publishes any premium state. A mismatch refuses
     * privileged features and falls back to the free tier — the user is
     * warned, the app never crashes. Root/emulator detection is a SOFT
     * signal only: logged, never blocking. Never throws.
     */
    private fun performTamperCheck() {
        val app = getApplication<Application>()
        val signatureOk = runCatching { Vpn5gTamperCheck.checkSignature(app) }
            .getOrDefault(true)
        val emulator = Vpn5gTamperCheck.isEmulator()
        val rooted = runCatching { Vpn5gTamperCheck.isRooted() }.getOrDefault(false)
        if (emulator || rooted) {
            LogUtil.e(
                AppConfig.TAG,
                "Vpn5gHomeViewModel: emulator=$emulator rooted=$rooted (soft signal only)",
            )
        }
        if (!signatureOk) {
            tamperDetected = true
            LogUtil.e(
                AppConfig.TAG,
                "Vpn5gHomeViewModel: signature mismatch — tampered build, free tier only",
            )
            _uiState.update {
                it.copy(
                    tamperDetected = true,
                    errorMessage = app.getString(R.string.vpn5g_tamper_warning),
                )
            }
        }
    }

    /** Loads the persisted ad-impression ledger (v1.1.15). */
    private fun loadAdImpressions() {
        val json = MmkvManager.decodeSettingsString(Vpn5gConstants.KEY_AD_IMPRESSIONS_JSON)
        if (json.isNullOrBlank()) return
        adImpressions = runCatching {
            JsonUtil.fromJsonSafe(json, Vpn5gAds.AdImpressionStore::class.java)
        }.getOrNull() ?: Vpn5gAds.AdImpressionStore()
    }

    /** Persists the ad-impression ledger (v1.1.15). */
    private fun saveAdImpressions() {
        runCatching {
            MmkvManager.encodeSettings(
                Vpn5gConstants.KEY_AD_IMPRESSIONS_JSON,
                JsonUtil.toJson(adImpressions),
            )
        }.onFailure {
            LogUtil.e(AppConfig.TAG, "Vpn5gHomeViewModel: save ad impressions failed", it)
        }
    }

    /** Loads the persisted expanded-country keys (v1.1.15). */
    private fun loadExpandedCountryKeys() {
        val keys = MmkvManager.decodeSettingsStringSet(Vpn5gConstants.KEY_EXPANDED_COUNTRY_KEYS)
        if (!keys.isNullOrEmpty()) {
            _uiState.update { it.copy(expandedCountryKeys = keys.toSet()) }
        }
    }

    /**
     * Toggles a locked-country group in Premium (v1.1.15). The state
     * lives in the ViewModel (durable, persisted) — the composable
     * only reads it. Groups start closed; the open/closed state is
     * preserved across uses.
     */
    fun toggleCountryExpanded(key: String) {
        val current = _uiState.value.expandedCountryKeys.toMutableSet()
        if (!current.add(key)) current.remove(key)
        val updated = current.toSet()
        _uiState.update { it.copy(expandedCountryKeys = updated) }
        viewModelScope.launch(Dispatchers.IO) {
            MmkvManager.encodeSettings(
                Vpn5gConstants.KEY_EXPANDED_COUNTRY_KEYS,
                updated.toMutableSet(),
            )
        }
    }

    /**
     * Routing mode (v1.1.21, Lantern routing screen): the choice is
     * persisted and mapped to the upstream per-app-proxy prefs (see
     * [Vpn5gRouting]) so the next connect uses it. The mapping is
     * applied synchronously — it is a small MMKV write — so a mode
     * change made just before a connect is never lost.
     */
    fun setRoutingMode(mode: Vpn5gRoutingMode) {
        Vpn5gRouting.apply(mode, _uiState.value.directFirst)
        _uiState.update { it.copy(routingMode = mode) }
    }

    /**
     * Direct-first switch (v1.1.21): under Smart routing, non-VPN
     * traffic tries a direct connection first. Same persistence and
     * mapping as [setRoutingMode].
     */
    fun setDirectFirst(on: Boolean) {
        Vpn5gRouting.apply(_uiState.value.routingMode, on)
        _uiState.update { it.copy(directFirst = on) }
    }

    /**
     * Drawer language (v1.1.21): applies the new locale immediately
     * (the activity recreates) and stores the choice in state so the
     * drawer reopens on the stored value.
     */
    fun setLanguage(code: String) {
        AppLocaleManager.setApplicationLanguage(code)
        _uiState.update { it.copy(languageCode = code) }
    }

    /**
     * Drawer appearance (v1.1.21): "0" = system, "1" = light,
     * "2" = dark. Applies immediately and stores the choice.
     */
    fun setThemeMode(mode: String) {
        ThemeManager.setThemeMode(mode)
        _uiState.update { it.copy(themeMode = mode) }
    }

    /**
     * Startup notice (v1.1.22): persist the shown-flag the FIRST time
     * the notice is displayed, so it never shows again on later
     * launches — including after updates, and even if the user kills
     * the app mid-read. Idempotent; deliberately does NOT touch the
     * in-memory state, so the current session keeps showing the
     * notice until it is dismissed or completed.
     */
    fun markStartupNoticeShown() {
        runCatching {
            MmkvManager.encodeSettings(Vpn5gConstants.KEY_STARTUP_NOTICE_SHOWN, true)
        }
    }

    /**
     * Startup notice (v1.1.21, once-only since v1.1.22): dismiss for
     * the current session. Also persists the shown-flag, so a dismiss
     * without a prior display-mark still never shows the notice again.
     */
    fun dismissStartupNotice() {
        markStartupNoticeShown()
        _uiState.update { it.copy(startupNoticeDismissed = true) }
    }

    /**
     * Startup notice (v1.1.21): advance one page — «بعدی» on pages
     * 0 and 1. Clamped to the last page (2); the last page offers
     * the Guide and the dismiss button instead.
     */
    fun advanceStartupNotice() {
        _uiState.update {
            it.copy(startupNoticePage = (it.startupNoticePage + 1).coerceAtMost(2))
        }
    }

    /**
     * Terms & Conditions (v1.1.21): the user ticked the checkbox on
     * the notice's last page — persist the acceptance (MMKV) and
     * publish it, so the login button enables at once and the user is
     * never asked to re-tick on later launches. One-way: there is no
     * un-accept path in the app.
     */
    fun acceptTerms() {
        MmkvManager.encodeSettings(Vpn5gConstants.KEY_TERMS_ACCEPTED, true)
        markStartupNoticeShown()
        _uiState.update { it.copy(termsAccepted = true) }
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

        // v1.1.15 (anti-tamper H3 + speeds root-cause fix): BEFORE any
        // premium state is published, verify our own signing
        // certificate on a background thread; then turn the core stats
        // setting on once — the running core only gets a `stats` block
        // when PREF_SPEED_ENABLED is true, and that setting defaults to
        // false with nothing in the VPN5G flow ever setting it, so the
        // core ran with NO stats and every speed read 0 (the 1.1.14
        // bug). The legacy settings UI is unreachable in this app, so
        // nothing can turn it back off. Only then does the startup sync
        // run (its cached publish already forces the free tier when the
        // build is tampered).
        viewModelScope.launch(Dispatchers.IO) {
            performTamperCheck()
            if (!MmkvManager.decodeSettingsBool(AppConfig.PREF_SPEED_ENABLED, false)) {
                MmkvManager.encodeSettings(AppConfig.PREF_SPEED_ENABLED, true)
            }
            loadAdImpressions()
            loadExpandedCountryKeys()
            // Startup sync (v1.1.14): show the last stored list
            // immediately, then run the live refresh with REAL 0→100%
            // progress; the home screen opens only when the sync
            // finishes with at least one server (otherwise a retry
            // state — never a hang at 100% and never home with an empty
            // pool). The bundled snapshot fills a first launch so the
            // list is never empty on entry. v1.1.15: with a cached list
            // the home opens fast and the refresh runs in the
            // background instead of blocking entry.
            runStartupSync()
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

        // Restore the routing screen choices (v1.1.21): the mode and the
        // direct-first switch, plus the drawer language and appearance,
        // so the screens open on the stored values. The routing mapping
        // is re-applied here too, so the stored mode is always the one
        // the next connect sees even if the app was killed mid-change.
        // Language is the MMKV PREF_LANGUAGE value ("auto" = system);
        // theme is the ThemeManager flow's current value.
        val savedMode = Vpn5gRouting.loadMode()
        val savedDirectFirst = Vpn5gRouting.loadDirectFirst()
        Vpn5gRouting.apply(savedMode, savedDirectFirst)
        _uiState.update {
            it.copy(
                routingMode = savedMode,
                directFirst = savedDirectFirst,
                languageCode = MmkvManager.decodeSettingsString(
                    AppConfig.PREF_LANGUAGE
                ) ?: "fa",
                themeMode = ThemeManager.themeMode.value,
                // v1.1.21: T&C acceptance is persisted — a user who
                // ticked it once is never asked again.
                termsAccepted = MmkvManager.decodeSettingsBool(
                    Vpn5gConstants.KEY_TERMS_ACCEPTED, false
                ),
                // v1.1.22: the notice shows only on the first entry
                // after install — a user who already saw it on an
                // earlier launch never sees it again.
                startupNoticeShownBefore = MmkvManager.decodeSettingsBool(
                    Vpn5gConstants.KEY_STARTUP_NOTICE_SHOWN, false
                ),
            )
        }

        // Re-check the operator when the network changes (v1.1.14): the
        // list is re-sorted for the new operator; a live session is kept
        // unless the operator change breaks it (the watchdog handles
        // that). Registration is best-effort and unregistered on clear.
        registerNetworkCallback()

        // Live tunnel speeds (v1.1.14): the monitor only polls while a
        // session runs; the state below is zero the rest of the time.
        viewModelScope.launch {
            Vpn5gSpeedMonitor.speeds.collect { speed ->
                _uiState.update {
                    it.copy(
                        downloadSpeedBps = speed.downBps,
                        uploadSpeedBps = speed.upBps,
                    )
                }
            }
        }

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
        // showing "connected". v1.1.11: subscribers have no countdown
        // (remainingMillis stays null) and a light watchdog probe every
        // 20 s drops a dead tunnel fast so the failover can switch.
        viewModelScope.launch {
            while (isActive) {
                delay(1_000L)
                val state = _uiState.value
                if (state.isRunning) {
                    _uiState.update {
                        it.copy(
                            // v1.1.11/1.1.14: subscribers AND gift-tier
                            // users have no countdown (no 30-minute
                            // timer while the gift lasts).
                            remainingMillis = if (state.isSubscriber || state.isGift) {
                                null
                            } else {
                                Vpn5gTimer.remainingMillis()
                            }
                        )
                    }
                    // v1.1.18 (volume, Model B): the daemon accumulates
                    // real tunnel bytes into MMKV; re-read the counter
                    // every second so the subscription panel stays
                    // live, and catch a cap reached mid-session.
                    val consumed = Vpn5gVolumeStore.consumedBytes()
                    if (consumed != state.volumeConsumedBytes) {
                        _uiState.update { it.copy(volumeConsumedBytes = consumed) }
                    }
                    if ((state.isSubscriber || state.isGift) &&
                        Vpn5gSubscriptionStatus.isVolumeExhausted(
                            state.userSubscription,
                            Vpn5gVolumeStore.consumedState(),
                        )
                    ) {
                        onVolumeExhausted()
                    }
                    if (state.connectState == Vpn5gConnectState.CONNECTED) {
                        ticksSinceReverify++
                        if (ticksSinceReverify >= 60) {
                            ticksSinceReverify = 0
                            reverifyTunnel()
                        }
                        watchdogTicks++
                        if (watchdogTicks >= WATCHDOG_PROBE_INTERVAL_TICKS) {
                            watchdogTicks = 0
                            runWatchdogProbe()
                        }
                    } else {
                        ticksSinceReverify = 0
                        watchdogTicks = 0
                        watchdogFailures = 0
                    }
                }
            }
        }
    }

    override fun onCleared() {
        verifyJob?.cancel()
        updateDownloadJob?.cancel()
        connectChainJob?.cancel()
        disconnectTimeoutJob?.cancel()
        reverifyJob?.cancel()
        watchdogJob?.cancel()
        autoFailoverJob?.cancel()
        persistentReconnectJob?.cancel()
        Vpn5gSpeedMonitor.stop()
        runCatching {
            val manager = getApplication<Application>()
                .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            networkCallback?.let { manager?.unregisterNetworkCallback(it) }
        }.onFailure {
            LogUtil.e(AppConfig.TAG, "Vpn5gHomeViewModel: unregister network callback failed", it)
        }
        networkCallback = null
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

    /** Operator-change listener (v1.1.14); null until registered. */
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    /** Registers the default-network callback (API 24+); best effort. */
    private fun registerNetworkCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        try {
            val manager = getApplication<Application>()
                .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    onNetworkChanged()
                }

                override fun onLost(network: Network) {
                    onNetworkChanged()
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    capabilities: NetworkCapabilities,
                ) {
                    onNetworkChanged()
                }
            }
            manager.registerDefaultNetworkCallback(callback)
            networkCallback = callback
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Vpn5gHomeViewModel: network callback failed", e)
        }
    }

    /**
     * Re-detects the operator after a network change (v1.1.14): when the
     * operator key changed, the list is rebuilt so the servers that
     * verified on the new operator sort first; a live session is kept
     * (only the list order changes — the watchdog drops a broken tunnel
     * on its own). Never throws.
     */
    private fun onNetworkChanged() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val operator = Vpn5gOperator.detect(getApplication())
                val changed = operator.key != Vpn5gOperator.currentKey()
                Vpn5gOperator.storeCurrentKey(operator.key)
                if (changed) {
                    val countries = runCatching { Vpn5gSync.loadGroupedServers(forceFreeTier = tamperDetected) }
                        .getOrElse { emptyList() }
                    _uiState.update {
                        it.copy(operatorLabel = operator.labelFa, countries = countries)
                    }
                    refreshSelection()
                } else {
                    _uiState.update { it.copy(operatorLabel = operator.labelFa) }
                }
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Vpn5gHomeViewModel: operator re-detect failed", e)
            }
        }
    }

    /**
     * Re-detects the operator on demand (v1.1.14): called after the
     * carrier-name permission is granted, and after a denied request
     * (detect degrades to "mobile" on its own). Never throws.
     */
    fun refreshOperator() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val operator = Vpn5gOperator.detect(getApplication())
                Vpn5gOperator.storeCurrentKey(operator.key)
                val countries = runCatching { Vpn5gSync.loadGroupedServers(forceFreeTier = tamperDetected) }
                    .getOrElse { _uiState.value.countries }
                _uiState.update {
                    it.copy(operatorLabel = operator.labelFa, countries = countries)
                }
                refreshSelection()
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Vpn5gHomeViewModel: refresh operator failed", e)
            }
        }
    }

    /**
     * Startup sync (v1.1.14): cached list first (with the bundled
     * fallback on a first launch), then the live refresh with REAL
     * progress. Retried from the startup screen's retry button via
     * [retryStartupSync].
     */
    private fun runStartupSync() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    startupProgress = Vpn5gStartupProgress.fraction(
                        Vpn5gStartupProgress.Stage.LOADING_CACHE, 0f
                    ),
                    startupError = false,
                )
            }
            val cached = Vpn5gRemoteConfig.cached()
            // A failure to read the stored list must never leave the
            // home screen stuck on "loading": fall back to an empty list.
            var countries = runCatching { Vpn5gSync.loadGroupedServers(forceFreeTier = tamperDetected) }.getOrElse {
                LogUtil.e(AppConfig.TAG, "Vpn5gHomeViewModel: load failed", it)
                emptyList()
            }
            var usedFallback = false
            if (countries.isEmpty()) {
                val imported = Vpn5gSync.loadBundledFallback(getApplication())
                if (imported > 0) {
                    countries = runCatching { Vpn5gSync.loadGroupedServers(forceFreeTier = tamperDetected) }.getOrElse { emptyList() }
                    usedFallback = true
                }
            }
            val operator = Vpn5gOperator.detect(getApplication())
            Vpn5gOperator.storeCurrentKey(operator.key)
            val nowSeconds = System.currentTimeMillis() / 1000
            val effective = Vpn5gSubscriptionStatus.loadEffective(nowSeconds)
            // v1.1.18 (Model B): the cached tier follows the volume
            // state too, and the counter belongs to the cached
            // effective grant from this launch on.
            Vpn5gVolumeStore.ensureGrant(effective)
            val volume = Vpn5gVolumeStore.consumedState()
            // v1.1.15 (anti-tamper, H3): a tampered build never publishes
            // a cached premium tier — the local cache is only a display
            // hint, never the authority.
            val subscriber = if (tamperDetected) {
                false
            } else {
                Vpn5gSubscriptionStatus.isSubscriber(effective, nowSeconds, volume)
            }
            val gift = if (tamperDetected) {
                false
            } else {
                Vpn5gSubscriptionStatus.isGiftTier(effective, nowSeconds, volume)
            }
            val giftHours = if (tamperDetected) {
                0L
            } else {
                Vpn5gSubscriptionStatus.giftRemainingHours(effective, nowSeconds, volume)
            }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    countries = countries,
                    subscriptionStatus = cached.subscription_status,
                    notice = cached.notice,
                    announcement = visibleAnnouncement(cached),
                    ads = cached.ads,
                    promoCodes = cached.codes,
                    plans = cached.plans,
                    paymentGateway = cached.payment_gateway,
                    trialEnabled = cached.trial_enabled ?: false,
                    trialHours = cached.trial_hours ?: 24,
                    signedInEmail = Vpn5gAuthService.currentSession()?.email,
                    userSubscription = effective,
                    isSubscriber = subscriber,
                    isGift = gift,
                    giftRemainingHours = giftHours,
                    volumeConsumedBytes = Vpn5gVolumeStore.consumedBytes(),
                    tamperDetected = tamperDetected,
                    operatorLabel = operator.labelFa,
                    offline = usedFallback,
                    // v1.1.16: a crash report from an earlier run waits
                    // on the home screen until shared or dismissed.
                    crashReportAvailable = Vpn5gCrashHandler.hasCrashReport(
                        getApplication()
                    ),
                    startupProgress = Vpn5gStartupProgress.fraction(
                        Vpn5gStartupProgress.Stage.LOADING_CACHE, 1f
                    ),
                )
            }
            refreshSelection()
            enrichCountries()
            // v1.1.15 (fast startup, D): with a cached list the home is
            // ready NOW — enter immediately and run the heavy refresh in
            // the background; never block entry on a full re-ping. 100%
            // only when the home is truly ready (the cached list is
            // shown).
            if (countries.isNotEmpty()) {
                _uiState.update {
                    it.copy(startupProgress = 1f, startupDone = true)
                }
                // v1.1.17: a cached list means a pickable server may
                // already exist — connect to the best one now.
                maybeStartupAutoConnect()
                refreshInternal(onProgress = null, finishStartup = false)
                return@launch
            }
            // v1.1.16 (first screen < 20 s): with NO cached list the
            // old code blocked entry on the FULL refresh (8 sequential
            // sources + full TCP ping + 45 s real probe — minutes on a
            // fresh install). Now the quick refresh runs instead,
            // bounded HARD at 20 s: two small sources in parallel + a
            // 12-server real probe (the bundled fallback already
            // published above when it imported anything). Whatever it
            // verifies opens the home; the full refresh still runs
            // afterwards in the background.
            val quickResult = withTimeoutOrNull(STARTUP_DEADLINE_MS) {
                Vpn5gSync.quickRefresh(
                    getApplication(),
                    skipRealProbe = false,
                    onProgress = { stage, within ->
                        _uiState.update {
                            it.copy(
                                startupProgress = Vpn5gStartupProgress.fraction(stage, within)
                            )
                        }
                    },
                )
            }
            val quickCountries = runCatching {
                Vpn5gSync.loadGroupedServers(forceFreeTier = tamperDetected)
            }.getOrElse { emptyList() }
            _uiState.update {
                it.copy(
                    countries = quickCountries,
                    startupProgress = 1f,
                    startupDone = quickCountries.isNotEmpty(),
                    startupError = quickCountries.isEmpty(),
                    offline = quickResult?.offline ?: true,
                )
            }
            refreshSelection()
            enrichCountries()
            // v1.1.17: whatever the quick refresh verified may
            // already be pickable — connect to the best server now.
            maybeStartupAutoConnect()
            // The full refresh still runs in the background so the rest
            // of the pool (all 8 sources) arrives without another tap.
            refreshInternal(onProgress = null, finishStartup = false)
        }
    }

    /** Retries the startup sync from the startup screen's retry button. */
    fun retryStartupSync() {
        _uiState.update { it.copy(startupError = false, startupProgress = 0f) }
        runStartupSync()
    }

    /**
     * Re-fetches ONLY app_config.json (v1.1.14, bot→app sync): called
     * when the Premium screen opens, so a bot-side change to plans,
     * prices, announcements or codes reaches the app WITHOUT an app
     * update and without waiting for the 5-minute server refresh. The
     * full refresh (Vpn5gSync.refresh) already re-fetches the config on
     * app start, every 5 minutes, and on manual refresh — this is the
     * extra fetch that makes the Premium screen always current.
     */
    fun refreshConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            val fresh = runCatching { Vpn5gRemoteConfig.fetch() }.getOrNull() ?: return@launch
            _uiState.update {
                it.copy(
                    announcement = visibleAnnouncement(fresh),
                    ads = fresh.ads,
                    promoCodes = fresh.codes,
                    plans = fresh.plans,
                    paymentGateway = fresh.payment_gateway,
                    trialEnabled = fresh.trial_enabled ?: it.trialEnabled,
                    trialHours = fresh.trial_hours ?: it.trialHours,
                    notice = fresh.notice ?: it.notice,
                    subscriptionStatus = fresh.subscription_status ?: it.subscriptionStatus,
                )
            }
        }
    }

    fun refresh() {
        refreshInternal(onProgress = null, finishStartup = false)
    }

    private fun refreshInternal(
        onProgress: ((Vpn5gStartupProgress.Stage, Float) -> Unit)?,
        finishStartup: Boolean,
    ) {
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
                    onProgress = onProgress,
                )
                val cached = Vpn5gRemoteConfig.cached()
                // v1.1.11: refresh the per-user subscription BEFORE
                // building the list — the subscriber flag decides
                // whether the pool is the top 20 or all servers. A
                // failed fetch keeps the cached subscription.
                // v1.1.14: the GIFT tier (top 50, no timer) is decided
                // here too, from the same effective subscription.
                val subscription = Vpn5gSubscriptionStatus.refresh(
                    Vpn5gAuthService.currentSession()
                )
                val nowSeconds = System.currentTimeMillis() / 1000
                val subscriber = Vpn5gSubscriptionStatus.isSubscriber(subscription, nowSeconds)
                val gift = Vpn5gSubscriptionStatus.isGiftTier(subscription, nowSeconds)
                val countries = Vpn5gSync.loadGroupedServers(forceFreeTier = tamperDetected)
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        isLoading = false,
                        offline = result.offline,
                        notice = result.notice ?: cached.notice,
                        announcement = visibleAnnouncement(cached),
                        ads = cached.ads,
                        countries = countries,
                        userSubscription = subscription,
                        // v1.1.15 (anti-tamper, H3): a tampered build never
                        // publishes a premium tier from the cache.
                        isSubscriber = if (tamperDetected) false else subscriber,
                        isGift = if (tamperDetected) false else gift,
                        giftRemainingHours = if (tamperDetected) {
                            0L
                        } else {
                            Vpn5gSubscriptionStatus.giftRemainingHours(
                                subscription, nowSeconds
                            )
                        },
                        tamperDetected = tamperDetected,
                        subscriptionStatus = cached.subscription_status,
                        promoCodes = cached.codes,
                        plans = cached.plans,
                        paymentGateway = cached.payment_gateway,
                        trialEnabled = cached.trial_enabled ?: false,
                        trialHours = cached.trial_hours ?: 24,
                        signedInEmail = Vpn5gAuthService.currentSession()?.email,
                        startupProgress = if (finishStartup) 1f else it.startupProgress,
                        // v1.1.16: the quick first-screen path may have
                        // left startupDone=false (empty quick result) —
                        // a LATER refresh that finally populates the
                        // list must open the home too, not leave the
                        // user stuck on the retry screen. After startup
                        // already succeeded this is a no-op (true stays
                        // true).
                        startupDone = if (finishStartup) {
                            countries.isNotEmpty()
                        } else {
                            it.startupDone || countries.isNotEmpty()
                        },
                        startupError = if (finishStartup) {
                            countries.isEmpty()
                        } else {
                            it.startupError && countries.isEmpty()
                        },
                    )
                }
                refreshSelection()
                enrichCountries()
                // v1.1.17: a refresh that finally populates the list
                // (the background full refresh after an empty quick
                // path) must connect to the best server now — the
                // 1.1.16 app waited for a tap that never came.
                maybeStartupAutoConnect()
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Vpn5gHomeViewModel: refresh failed", e)
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        isLoading = false,
                        offline = true,
                        startupError = if (finishStartup) true else it.startupError,
                    )
                }
            }
        }
    }

    /**
     * Re-checks the signed-in user's subscription in the background
     * (v1.1.11), e.g. on connect: the chain never waits for it. When
     * the subscriber flag flips, the server list is rebuilt so the
     * pool (top 20 vs all) matches the new status immediately. Never
     * throws.
     */
    /**
     * Volume cap reached mid-session (v1.1.18, Model B): the
     * subscription ends — the tier flips to FREE, the pool/locks
     * reload as free, and the 30-minute free timer starts from now
     * (a free user gets 30 minutes per session; the session itself
     * is NOT cut mid-stream). The subscription panel shows the
     * volume-finished reason via
     * [Vpn5gSubscriptionStatus.isVolumeExhausted]. Idempotent: the
     * ticker only calls it while the state still says
     * subscriber/gift, and the re-check below drops a stale call.
     * Never throws.
     */
    private fun onVolumeExhausted() {
        viewModelScope.launch(Dispatchers.IO) {
            val effective = _uiState.value.userSubscription
            val volume = Vpn5gVolumeStore.consumedState()
            if (!Vpn5gSubscriptionStatus.isVolumeExhausted(effective, volume)) {
                return@launch
            }
            // The free timer starts at the exhaustion moment, so the
            // countdown the home shows afterwards is real.
            Vpn5gTimer.scheduleDisconnect(getApplication())
            val countries = runCatching {
                Vpn5gSync.loadGroupedServers(forceFreeTier = tamperDetected)
            }.getOrElse { _uiState.value.countries }
            _uiState.update {
                it.copy(
                    isSubscriber = false,
                    isGift = false,
                    giftRemainingHours = 0L,
                    volumeConsumedBytes = Vpn5gVolumeStore.consumedBytes(),
                    countries = countries,
                    remainingMillis = Vpn5gTimer.remainingMillis(),
                )
            }
            refreshSelection()
        }
    }

    private fun refreshSubscriptionInBackground() {
        viewModelScope.launch(Dispatchers.IO) {
            val wasSubscriber = _uiState.value.isSubscriber
            val wasGift = _uiState.value.isGift
            val subscription = Vpn5gSubscriptionStatus.refresh(
                Vpn5gAuthService.currentSession()
            )
            val nowSeconds = System.currentTimeMillis() / 1000
            // v1.1.18 (Model B): the tier follows the volume state —
            // a capped grant that is used up is FREE here, and the
            // counter belongs to the effective grant from now on.
            Vpn5gVolumeStore.ensureGrant(subscription)
            val volume = Vpn5gVolumeStore.consumedState()
            val subscriber = Vpn5gSubscriptionStatus.isSubscriber(
                subscription, nowSeconds, volume
            )
            val gift = Vpn5gSubscriptionStatus.isGiftTier(
                subscription, nowSeconds, volume
            )
            _uiState.update {
                it.copy(
                    userSubscription = subscription,
                    // v1.1.15 (anti-tamper, H3): a tampered build never
                    // publishes a premium tier from the cache.
                    isSubscriber = if (tamperDetected) false else subscriber,
                    isGift = if (tamperDetected) false else gift,
                    giftRemainingHours = if (tamperDetected) {
                        0L
                    } else {
                        Vpn5gSubscriptionStatus.giftRemainingHours(
                            subscription, nowSeconds, volume
                        )
                    },
                    volumeConsumedBytes = Vpn5gVolumeStore.consumedBytes(),
                    tamperDetected = tamperDetected,
                )
            }
            if (subscriber != wasSubscriber || gift != wasGift) {
                val countries = runCatching { Vpn5gSync.loadGroupedServers(forceFreeTier = tamperDetected) }
                    .getOrElse { emptyList() }
                _uiState.update { it.copy(countries = countries) }
                refreshSelection()
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
    fun beginConnect(userInitiated: Boolean = false) {
        // v1.1.17: any connect (auto or manual) disarms the one
        // startup auto-connect — it must never fire twice.
        startupAutoConnectDone = true
        // v1.1.15 (forced-ad rule): count user-initiated connects only.
        if (userInitiated) completedSessions += 1
        connectChainJob?.cancel()
        connectChainJob = viewModelScope.launch {
            // v1.1.16 (crash fix): this coroutine runs on the Main
            // dispatcher with NO parent handler — any throw here used
            // to kill the app with the "this app has a bug" dialog
            // (the 1.1.15 connect crash, 2/2 taps). The whole chain
            // is guarded now: a failure lands in FAILED with a
            // Persian message, never a crash.
            try {
            userStopRequested = false
            // v1.1.15 (anti-tamper, H1): a premium tier is never trusted
            // from the local cache alone — when the cached tier is not
            // FREE, re-check the server state before connecting, bounded
            // to 3 s. A failed or timed-out check keeps the cached tier
            // (offline cannot verify); the local cache is only a display
            // hint, never the authority. A free tier keeps the old
            // background refresh — the chain never waits for it.
            if (_uiState.value.isSubscriber || _uiState.value.isGift) {
                val fresh = withTimeoutOrNull(3_000L) {
                    withContext(Dispatchers.IO) {
                        Vpn5gSubscriptionStatus.refresh(
                            Vpn5gAuthService.currentSession()
                        )
                    }
                }
                if (fresh != null) {
                    val nowSeconds = System.currentTimeMillis() / 1000
                    // v1.1.18 (Model B): volume-aware, like every other
                    // tier publish — a used-up grant never connects as
                    // a subscriber.
                    Vpn5gVolumeStore.ensureGrant(fresh)
                    val volume = Vpn5gVolumeStore.consumedState()
                    _uiState.update {
                        it.copy(
                            userSubscription = fresh,
                            isSubscriber = Vpn5gSubscriptionStatus.isSubscriber(
                                fresh, nowSeconds, volume
                            ),
                            isGift = Vpn5gSubscriptionStatus.isGiftTier(fresh, nowSeconds, volume),
                            giftRemainingHours = Vpn5gSubscriptionStatus
                                .giftRemainingHours(fresh, nowSeconds, volume),
                            volumeConsumedBytes = Vpn5gVolumeStore.consumedBytes(),
                        )
                    }
                }
            } else {
                refreshSubscriptionInBackground()
            }
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
                // v1.1.23: the chain ends here — land in FAILED, not
                // RETRYING. RETRYING renders as the blue CONNECTING
                // switch forever (nothing retries after this return),
                // which read as a stuck switch on device.
                _uiState.update {
                    it.copy(
                        connectState = Vpn5gAutoConnect.transition(
                            Vpn5gAutoConnect.transition(
                                it.connectState, Vpn5gConnectEvent.INTERNET_FAILED
                            ),
                            Vpn5gConnectEvent.GIVE_UP,
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
                    // v1.1.23: same stuck-switch fix as the internet
                    // path — SERVER_MISSING lands in RETRYING (blue
                    // CONNECTING visual) but nothing retries after
                    // this return, so give up to FAILED. With the
                    // tier-pool + provisional picker this only fires
                    // when the synced list truly has no pinged server.
                    _uiState.update {
                        it.copy(
                            connectState = Vpn5gAutoConnect.transition(
                                Vpn5gAutoConnect.transition(
                                    it.connectState, Vpn5gConnectEvent.SERVER_MISSING
                                ),
                                Vpn5gConnectEvent.GIVE_UP,
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
            } catch (e: Exception) {
                // Cancellation is not a failure — a new tap cancels
                // the previous chain on purpose.
                if (e is CancellationException) throw e
                LogUtil.e(AppConfig.TAG, "Vpn5gHomeViewModel: connect chain failed", e)
                _uiState.update {
                    it.copy(
                        connectState = Vpn5gAutoConnect.transition(
                            it.connectState, Vpn5gConnectEvent.GIVE_UP
                        ),
                        errorMessage = getApplication<Application>()
                            .getString(R.string.vpn5g_connect_failed),
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
        watchdogJob?.cancel()
        watchdogJob = null
        watchdogTicks = 0
        watchdogFailures = 0
        dropFailoverRounds = 0
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
        // v1.1.17: a manual disconnect must never be followed by an
        // auto re-connect (the 5-minute ticker refresh calls
        // maybeStartupAutoConnect too).
        startupAutoConnectDone = true
        connectChainJob?.cancel()
        connectChainJob = null
        autoFailoverJob?.cancel()
        autoFailoverJob = null
        // v1.1.12: a manual disconnect ends the persistent session —
        // no reconnect may follow it.
        Vpn5gConnectionPersistence.setPersistentConnection(false)
        persistentReconnectJob?.cancel()
        persistentReconnectJob = null
        persistentReconnectAttempts = 0
        resetTunnelAttempts()
        // v1.1.23: when the daemon never started (cancel while
        // testing/connecting), there is nothing to stop — land in
        // IDLE at once instead of sitting in DISCONNECTING until
        // the 5 s timeout. A running daemon still goes through
        // DISCONNECTING + timeout below.
        val wasRunning = _uiState.value.isRunning
        _uiState.update {
            it.copy(
                connectState = if (wasRunning) {
                    Vpn5gAutoConnect.transition(
                        it.connectState, Vpn5gConnectEvent.USER_DISCONNECT
                    )
                } else {
                    Vpn5gConnectState.IDLE
                },
                lastError = null,
                errorMessage = null,
                manualServerFailed = null,
            )
        }
        // v1.1.18: DISCONNECTING must never stick — if the service
        // never reports stopped (lost stop broadcast, process died
        // mid-stop), land in IDLE after the timeout. The job only
        // fires while still DISCONNECTING and not running (a restart
        // racing the disconnect moves the state first, and the stop
        // landing cancels this job — see onRunningChanged).
        disconnectTimeoutJob?.cancel()
        if (_uiState.value.connectState == Vpn5gConnectState.DISCONNECTING) {
            disconnectTimeoutJob = viewModelScope.launch {
                delay(Vpn5gAutoConnect.DISCONNECT_TIMEOUT_MS)
                if (_uiState.value.connectState == Vpn5gConnectState.DISCONNECTING &&
                    !_uiState.value.isRunning
                ) {
                    _uiState.update { it.copy(connectState = Vpn5gConnectState.IDLE) }
                }
            }
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

    /**
     * Re-reads the auth state after a sign-in / sign-out (v1.1.16, bug
     * 2): the home ViewModel used to read `signedInEmail` only during
     * startup sync, so a successful login did nothing until the app
     * was fully restarted. Now the Activity calls this whenever the
     * user leaves the auth screen — the session, the subscription and
     * the country list (tier) are re-published at once and home shows
     * the signed-in state immediately, no restart. Never throws; a
     * failed subscription fetch keeps the cached tier.
     */
    fun refreshAuthState() {
        viewModelScope.launch(Dispatchers.IO) {
            val session = runCatching {
                Vpn5gAuthService.currentSession()
            }.getOrNull()
            // v1.1.19: a fresh sign-in is the moment to make sure the
            // registrations row exists (see
            // [ensureRegistrationRowIfMissing]) — otherwise a later
            // redeem burns the code server-side with no grant.
            if (session != null) ensureRegistrationRowIfMissing(session)
            val subscription = runCatching {
                Vpn5gSubscriptionStatus.refresh(session)
            }.getOrNull()
            val nowSeconds = System.currentTimeMillis() / 1000
            val effective = subscription
                ?: Vpn5gSubscriptionStatus.loadEffective(nowSeconds)
            // v1.1.18 (Model B): volume-aware tier + the counter
            // belongs to the effective grant from now on.
            Vpn5gVolumeStore.ensureGrant(effective)
            val volume = Vpn5gVolumeStore.consumedState()
            val countries = runCatching {
                Vpn5gSync.loadGroupedServers(forceFreeTier = tamperDetected)
            }.getOrElse { _uiState.value.countries }
            _uiState.update {
                it.copy(
                    signedInEmail = session?.email,
                    userSubscription = effective,
                    isSubscriber = if (tamperDetected) {
                        false
                    } else {
                        Vpn5gSubscriptionStatus.isSubscriber(effective, nowSeconds, volume)
                    },
                    isGift = if (tamperDetected) {
                        false
                    } else {
                        Vpn5gSubscriptionStatus.isGiftTier(effective, nowSeconds, volume)
                    },
                    giftRemainingHours = if (tamperDetected) {
                        0L
                    } else {
                        Vpn5gSubscriptionStatus.giftRemainingHours(
                            effective, nowSeconds, volume
                        )
                    },
                    volumeConsumedBytes = Vpn5gVolumeStore.consumedBytes(),
                    countries = countries,
                )
            }
            refreshSelection()
        }
    }

    /** Dismisses the waiting crash report (v1.1.16): deletes the file. */
    fun dismissCrashReport() {
        viewModelScope.launch(Dispatchers.IO) {
            Vpn5gCrashHandler.deleteCrashReport(getApplication())
        }
        _uiState.update { it.copy(crashReportAvailable = false) }
    }

    fun signOut() {
        Vpn5gAuthService.signOut()
        // v1.1.18: the volume counter belongs to the signed-out
        // account's grant — the next account inherits nothing.
        Vpn5gVolumeStore.clear()
        _uiState.update {
            it.copy(
                signedInEmail = null,
                userSubscription = null,
                isSubscriber = false,
                isGift = false,
                giftRemainingHours = 0L,
                volumeConsumedBytes = 0L,
            )
        }
    }

    /** Gift-code section input (v1.1.16: separate from subscription codes). */
    fun setGiftCodeInput(value: String) {
        _uiState.update { it.copy(giftCodeInput = value, giftCodeStatus = Vpn5gPromoStatus.IDLE, promoBotLink = null) }
    }

    /** Subscription-code section input (v1.1.16: separate from gift codes). */
    fun setSubCodeInput(value: String) {
        _uiState.update { it.copy(subCodeInput = value, subCodeStatus = Vpn5gPromoStatus.IDLE, promoBotLink = null) }
    }

    /** Redeems the gift-code section's entry (v1.1.16). */
    fun redeemGiftCode() {
        redeemCode(
            kind = Vpn5gPromoCodes.CodeKind.GIFT,
            input = _uiState.value.giftCodeInput,
            setStatus = { status ->
                _uiState.update { it.copy(giftCodeStatus = status) }
            },
        )
    }

    /** Redeems the subscription-code section's entry (v1.1.16). */
    fun redeemSubCode() {
        redeemCode(
            kind = Vpn5gPromoCodes.CodeKind.SUBSCRIPTION,
            input = _uiState.value.subCodeInput,
            setStatus = { status ->
                _uiState.update { it.copy(subCodeStatus = status) }
            },
        )
    }

    /**
     * v1.1.17: fires the one startup auto-connect when a pickable
     * free server exists (see
     * [Vpn5gAutoConnect.shouldStartupAutoConnect]). Called after
     * every list rebuild that can make a server pickable — the
     * cached startup list, the quick refresh, and each background
     * refresh — so the app connects to the best server as soon as
     * one exists, like 1.1.14 felt, instead of waiting for a tap
     * after the list lands. The connect is userInitiated = false,
     * so the forced-ad rule never shows an interstitial for it.
     */
    private fun maybeStartupAutoConnect() {
        if (!Vpn5gAutoConnect.shouldStartupAutoConnect(
                startupAutoConnectDone = startupAutoConnectDone,
                isRunning = _uiState.value.isRunning,
                userStopRequested = userStopRequested,
                connectState = _uiState.value.connectState,
                hasPickableServer = Vpn5gAutoConnect.pickBestServer(
                    _uiState.value.countries
                ) != null,
            )
        ) return
        startupAutoConnectDone = true
        beginConnect(userInitiated = false)
    }

    /**
     * Publishes a redeemed grant to the home state (v1.1.17): the
     * plan + remaining time in the status section, the country list
     * rebuilt so the unlocked servers show, and the selection
     * refreshed. Shared by the server path, the config-list
     * fallback and the recovery path so a grant can never be saved
     * without being shown.
     */
    private fun publishRedeemed(
        subscription: Vpn5gSubscriptionStatus.Vpn5gUserSubscription,
        link: String?,
        nowSeconds: Long,
    ) {
        // v1.1.18 (Model B): the new grant owns the counter from now
        // on (a new grant resets it to 0), and the tier follows the
        // volume state.
        Vpn5gVolumeStore.ensureGrant(subscription)
        val volume = Vpn5gVolumeStore.consumedState()
        // v1.1.22: the tier for the lock rebuild comes from the MERGED
        // subscription (cached effective + this grant), and the
        // countries are rebuilt with that tier explicitly — the locks
        // open from the grant itself, never from a stale cache read.
        // (Aref 2026-11-25 ~06:42: after registering a code the
        // locked countries stayed shut.)
        val merged = Vpn5gSubscriptionStatus.effectiveSubscription(
            Vpn5gSubscriptionStatus.loadEffective(nowSeconds),
            subscription, nowSeconds, volume,
        ) ?: subscription
        val tier = Vpn5gSubscriptionStatus.tierOf(merged, nowSeconds, volume)
        val countries = runCatching {
            Vpn5gSync.loadGroupedServers(
                forceFreeTier = tamperDetected,
                tierOverride = tier,
            )
        }.getOrElse { emptyList() }
        _uiState.update {
            it.copy(
                promoBotLink = link,
                userSubscription = subscription,
                isSubscriber = tier ==
                    Vpn5gSubscriptionStatus.Vpn5gTier.SUBSCRIBER,
                isGift = tier ==
                    Vpn5gSubscriptionStatus.Vpn5gTier.GIFT,
                giftRemainingHours = Vpn5gSubscriptionStatus
                    .giftRemainingHours(subscription, nowSeconds, volume),
                volumeConsumedBytes = Vpn5gVolumeStore.consumedBytes(),
                countries = countries,
            )
        }
        refreshSelection()
        // v1.1.18: re-read the subscription from Supabase so the row
        // (bot-activated plans, expiry) and the local grant merge —
        // the server state appears at once instead of on the next
        // refresh. The just-granted subscription is the floor (see
        // refreshSubscriptionAfterRedeem).
        refreshSubscriptionAfterRedeem(subscription)
    }

    /**
     * Makes sure the signed-in user has a row in
     * vpn5g_registrations (v1.1.19): the server-side
     * `validate_and_redeem_code` only grants a code when such a row
     * exists for the email — with no row the code is burned and
     * `applied:false` comes back. Only a definitive NoRow triggers
     * the register call; a failed fetch (no user id / offline)
     * triggers nothing. Best effort only: never blocks sign-in,
     * refresh or a redeem, never throws, never changes sign-in
     * behaviour on error.
     */
    private suspend fun ensureRegistrationRowIfMissing(
        session: Vpn5gAuthService.Vpn5gAuthSession,
    ) {
        val email = session.email?.takeIf { it.isNotBlank() } ?: return
        val row = runCatching {
            Vpn5gSubscriptionStatus.fetchOwnRow(session)
        }.getOrNull()
        if (row is Vpn5gSubscriptionStatus.FetchResult.NoRow) {
            runCatching { Vpn5gAuthService.ensureRegistrationRow(email) }
        }
    }

    /**
     * Post-redeem server re-read (v1.1.18): after a grant is
     * published, the subscription is re-read from Supabase and the
     * merged result re-published (plan + remaining time + volume +
     * country list), so a server plan the local grant missed appears
     * at once. The just-granted subscription is merged back in as a
     * floor: refresh() can only drop a local gift on a DEFINITIVE
     * server "none" (get_subscription_status does not exist yet, so
     * that path is currently unreachable — the RPC answers 404), and
     * a grant validated by validate_and_redeem_code seconds ago must
     * never be demoted by its own post-redeem refresh. A Failed /
     * NoRow row fetch keeps the local grant by construction (see
     * Vpn5gSubscriptionStatus.refresh). Runs on IO; never throws.
     */
    private fun refreshSubscriptionAfterRedeem(
        justGranted: Vpn5gSubscriptionStatus.Vpn5gUserSubscription,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val session = runCatching {
                Vpn5gAuthService.currentSession()
            }.getOrNull()
            // v1.1.19: a redeem is the other moment the row must
            // exist — a missing row is why the server burned codes
            // with applied:false. Ensure it before the re-read, so
            // the NEXT redeem applies server-side.
            if (session != null) ensureRegistrationRowIfMissing(session)
            // v1.1.20: link the auth UUID before the re-read — a row
            // created after sign-in has a NULL user_id until linked,
            // so fetchOwnRow (by user_id) would find no row and the
            // re-read would miss the grant the server just applied.
            if (session != null) {
                runCatching { Vpn5gSubscriptionStatus.linkOwnUserId(session) }
            }
            val refreshed = runCatching {
                Vpn5gSubscriptionStatus.refresh(session)
            }.getOrNull()
            val nowSeconds = System.currentTimeMillis() / 1000
            val volume = Vpn5gVolumeStore.consumedState()
            val effective = Vpn5gSubscriptionStatus.effectiveSubscription(
                refreshed, justGranted, nowSeconds, volume
            ) ?: Vpn5gSubscriptionStatus.loadEffective(nowSeconds)
            Vpn5gVolumeStore.ensureGrant(effective)
            val freshVolume = Vpn5gVolumeStore.consumedState()
            val tier = Vpn5gSubscriptionStatus.tierOf(effective, nowSeconds, freshVolume)
            // v1.1.22: rebuild the locks from this merged tier
            // explicitly (see publishRedeemed) — a stale cache must
            // never re-lock what the redeem just opened.
            val countries = runCatching {
                Vpn5gSync.loadGroupedServers(
                    forceFreeTier = tamperDetected,
                    tierOverride = tier,
                )
            }.getOrElse { _uiState.value.countries }
            _uiState.update {
                it.copy(
                    userSubscription = effective,
                    isSubscriber = if (tamperDetected) {
                        false
                    } else {
                        tier == Vpn5gSubscriptionStatus.Vpn5gTier.SUBSCRIBER
                    },
                    isGift = if (tamperDetected) {
                        false
                    } else {
                        tier == Vpn5gSubscriptionStatus.Vpn5gTier.GIFT
                    },
                    giftRemainingHours = if (tamperDetected) {
                        0L
                    } else {
                        Vpn5gSubscriptionStatus.giftRemainingHours(
                            effective, nowSeconds, freshVolume
                        )
                    },
                    volumeConsumedBytes = Vpn5gVolumeStore.consumedBytes(),
                    countries = countries,
                )
            }
            refreshSelection()
        }
    }

    /** Last wall-clock run of [refreshSubscriptionAndLocks]. */
    private var lastSubscriptionRefreshMs: Long = 0L

    /**
     * Subscription + locks refresh (v1.1.22, fix #4): re-reads the
     * subscription (server row when readable — the 403 until the
     * guard runs GRANT SELECT is tolerated — merged with the local
     * grant) and rebuilds the country locks from the MERGED tier, so
     * the status section and the locks are never stale after a redeem
     * or a return to the app. Called on every entry to the foreground
     * and on opening Premium; debounced to 30 s unless forced. Runs
     * on IO; never throws.
     */
    fun refreshSubscriptionAndLocks(force: Boolean = false) {
        val nowMs = System.currentTimeMillis()
        if (!force && nowMs - lastSubscriptionRefreshMs < 30_000L) return
        lastSubscriptionRefreshMs = nowMs
        viewModelScope.launch(Dispatchers.IO) {
            val session = runCatching {
                Vpn5gAuthService.currentSession()
            }.getOrNull()
            val refreshed = runCatching {
                Vpn5gSubscriptionStatus.refresh(session)
            }.getOrNull()
            val nowSeconds = System.currentTimeMillis() / 1000
            val effective = refreshed
                ?: Vpn5gSubscriptionStatus.loadEffective(nowSeconds)
            if (effective != null) Vpn5gVolumeStore.ensureGrant(effective)
            val volume = Vpn5gVolumeStore.consumedState()
            val tier = Vpn5gSubscriptionStatus.tierOf(effective, nowSeconds, volume)
            val countries = runCatching {
                Vpn5gSync.loadGroupedServers(
                    forceFreeTier = tamperDetected,
                    tierOverride = tier,
                )
            }.getOrElse { _uiState.value.countries }
            _uiState.update {
                it.copy(
                    userSubscription = effective,
                    isSubscriber = if (tamperDetected) {
                        false
                    } else {
                        tier == Vpn5gSubscriptionStatus.Vpn5gTier.SUBSCRIBER
                    },
                    isGift = if (tamperDetected) {
                        false
                    } else {
                        tier == Vpn5gSubscriptionStatus.Vpn5gTier.GIFT
                    },
                    giftRemainingHours = if (tamperDetected) {
                        0L
                    } else {
                        Vpn5gSubscriptionStatus.giftRemainingHours(
                            effective, nowSeconds, volume
                        )
                    },
                    volumeConsumedBytes = Vpn5gVolumeStore.consumedBytes(),
                    countries = countries,
                )
            }
            refreshSelection()
        }
    }

    /**
     * Post-redeem server-row grant (v1.1.20): after the RPC said ok but
     * the answer carried no usable grant (applied:false, or the expiry
     * was stripped), the server row is the authority — ensure the row
     * exists, link the auth UUID (a row created after sign-in has a
     * NULL user_id until linked, so the by-user_id re-read would find
     * no row), re-read the subscription, and publish it when it is
     * active. Returns true when a grant was published (and REDEEMED
     * set). The published grant is persisted by the refresh itself
     * (row cache) and by publishRedeemed, so it survives a restart.
     * Never throws.
     */
    private suspend fun publishServerRowGrant(
        email: String,
        nowSeconds: Long,
        setStatus: (Vpn5gPromoStatus) -> Unit,
    ): Boolean {
        val session = runCatching {
            Vpn5gAuthService.currentSession()
        }.getOrNull() ?: return false
        ensureRegistrationRowIfMissing(session)
        runCatching { Vpn5gSubscriptionStatus.linkOwnUserId(session) }
        val refreshed = runCatching {
            Vpn5gSubscriptionStatus.refresh(session)
        }.getOrNull() ?: return false
        val volume = Vpn5gVolumeStore.consumedState()
        val active = Vpn5gSubscriptionStatus.isSubscriber(refreshed, nowSeconds, volume) ||
            Vpn5gSubscriptionStatus.isGiftTier(refreshed, nowSeconds, volume)
        if (!active) return false
        publishRedeemed(refreshed, null, nowSeconds)
        setStatus(Vpn5gPromoStatus.REDEEMED)
        return true
    }

    /**
     * v1.1.17 (redeem recovery): the code was consumed somewhere —
     * the server said already_used, or the published list shows
     * remaining_uses 0 — but this device holds no grant for it.
     * Re-derives the grant from the entry and activates it locally:
     * re-entering a consumed-but-ungranted code grants the plan
     * instead of saying invalid. Returns true when a grant was
     * activated and published. An entry whose own expiry has passed
     * is not recovered (the code is dead; the bot must re-issue).
     */
    private fun tryRecoverGrant(
        entry: PromoCodeRemote?,
        email: String,
        nowSeconds: Long,
        setStatus: (Vpn5gPromoStatus) -> Unit,
    ): Boolean {
        if (entry == null) return false
        // v1.1.19: a discount code has no grant to re-derive — it
        // goes through the bot, never through recovery.
        if (Vpn5gPromoCodes.codeKind(entry) == Vpn5gPromoCodes.CodeKind.DISCOUNT) return false
        val expiresRaw = entry.expires_at?.takeIf { it.isNotBlank() }
        if (expiresRaw != null) {
            val expiresAt = Vpn5gSubscriptionStatus.parseIsoToEpoch(expiresRaw)
            if (expiresAt == null || expiresAt <= nowSeconds) return false
        }
        val activated = Vpn5gPromoCodes.activateGift(entry, email, nowSeconds)
            ?: return false
        // v1.1.18: a redeemed grant needs no bot confirm — the grant
        // is active in-app. The link is dropped (it only ever served
        // the removed "confirm in bot" step); the server-side
        // decrement for gifts goes through recordCodeRedemption.
        val (subscription, _) = activated
        publishRedeemed(subscription, null, nowSeconds)
        setStatus(Vpn5gPromoStatus.REDEEMED)
        return true
    }

    /**
     * Redeems the entered code (v1.1.12; server-first v1.1.15; separate
     * gift/subscription sections v1.1.16; recovery v1.1.17; server-only
     * v1.1.20): the server RPC `validate_and_redeem_code` is the ONLY
     * validator — it checks existence / already-used / expiry /
     * remaining_uses / registration and marks the code used atomically,
     * so a single-use code can never be reused. The published config
     * list is NEVER a validation decision anymore (its remaining_uses
     * is stale the moment a code is redeemed anywhere): when the RPC
     * is unreachable the redeem reports SERVER_UNAVAILABLE and tries
     * nothing locally, never a false invalid. A GIFT code activates
     * immediately and the countries unlock at once (the list is
     * rebuilt); a DISCOUNT code still goes through the bot (VALID +
     * deep link). A code the server already counts as used is
     * re-granted locally instead of saying invalid (see
     * [tryRecoverGrant]). The grant comes from the RPC answer itself
     * first (see [Vpn5gPromoCodes.activateRpcGrant] — its expiry when
     * present, otherwise its tier / plan / gift_hours / gb_total), so
     * a validated-but-unapplied code still grants without the code
     * being in the published list; the server-row re-read (see
     * [publishServerRowGrant]) is only the last resort, and the
     * published entry the one after it. After a successful redeem the
     * subscription is re-read from the server row at once (see
     * [refreshSubscriptionAfterRedeem]) so the panel shows the fresh
     * plan + expiry on the same screen.
     * [setStatus] reports the outcome to the section that asked (gift
     * or subscription). Never throws.
     */
    private fun redeemCode(
        kind: Vpn5gPromoCodes.CodeKind,
        input: String,
        setStatus: (Vpn5gPromoStatus) -> Unit,
    ) {
        val email = _uiState.value.signedInEmail
        // A tampered build never redeems (H3): the server would refuse
        // the grant anyway, so fail fast with the tamper warning.
        if (tamperDetected) {
            setStatus(Vpn5gPromoStatus.INVALID)
            _uiState.update {
                it.copy(
                    errorMessage = getApplication<Application>()
                        .getString(R.string.vpn5g_tamper_warning),
                )
            }
            return
        }
        // v1.1.20: redeeming needs a signed-in email — the server
        // validates by email, so without one there is nothing to
        // validate. The sign-in button is offered by the status block.
        if (email.isNullOrBlank()) {
            setStatus(Vpn5gPromoStatus.NOT_SIGNED_IN)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val nowSeconds = System.currentTimeMillis() / 1000
            // v1.1.16 (separate sections): when the LOCAL list knows
            // this code, its kind must match the section it was typed
            // in — a gift code in the subscription box (or vice versa)
            // is refused BEFORE the server RPC, so the server never
            // marks it used. A discount code has no section of its own:
            // it flows through the normal path below (VALID + bot
            // link). When the local list does NOT know the code, the
            // server RPC below is the authority and the grant is
            // accepted as-is. This is a section routing check only —
            // never a validation decision.
            val localEntry = Vpn5gPromoCodes.findCode(input, _uiState.value.promoCodes)
            if (localEntry != null) {
                val entryKind = Vpn5gPromoCodes.codeKind(localEntry)
                if (entryKind != Vpn5gPromoCodes.CodeKind.DISCOUNT &&
                    entryKind != Vpn5gPromoCodes.CodeKind.UNKNOWN &&
                    entryKind != kind
                ) {
                    setStatus(Vpn5gPromoStatus.WRONG_SECTION)
                    return@launch
                }
            }
            // v1.1.20 (server-only): the RPC is the ONLY validator.
            val server = runCatching {
                Vpn5gAuthService.validateAndRedeemCode(input, email)
            }.getOrNull()
            when (server) {
                is Vpn5gAuthService.CodeServerResult.Validated -> {
                    // A discount code has no grant: the server
                    // validated it and the bot applies it — VALID +
                    // bot link, the only case that ever shows one
                    // (v1.1.20: the tier comes from the RPC itself,
                    // so no local list is needed to recognise it).
                    if (server.tier == "discount") {
                        val link = Vpn5gPromoCodes.redeemLink(input)
                        _uiState.update { it.copy(promoBotLink = link) }
                        setStatus(Vpn5gPromoStatus.VALID)
                        return@launch
                    }
                    // v1.1.21: the answer itself carries the grant —
                    // its expiry when present, otherwise its tier /
                    // plan / gift_hours / gb_total — so a code the
                    // server validated but did not apply (applied:
                    // false, or a stripped expiry) still grants
                    // in-app at once, WITHOUT the code being in the
                    // published list and WITHOUT waiting on the
                    // server-row re-read (the row read is 403 until
                    // the server grants SELECT — it stays as the last
                    // resort below, never the gate). A failed row
                    // re-read can never demote this grant:
                    // publishRedeemed persists it locally and merges
                    // it back as the floor on the post-redeem refresh.
                    val granted = Vpn5gPromoCodes.activateRpcGrant(
                        input, server, email, nowSeconds
                    )
                    if (granted != null) {
                        // v1.1.18: the server-validated grant activates
                        // in-app at once — no bot confirm step, so no
                        // bot link is published (the REDEEMED block no
                        // longer shows one either). The post-redeem
                        // re-read inside publishRedeemed refreshes the
                        // panel from the server row at once.
                        publishRedeemed(granted, null, nowSeconds)
                        setStatus(Vpn5gPromoStatus.REDEEMED)
                        return@launch
                    }
                    // The answer carried no usable grant: the server
                    // row is the authority now — re-read it (it may
                    // hold the grant) — then re-derive from the
                    // published entry. Only when both fail is it a
                    // specific error, never "invalid": the code was
                    // consumed.
                    if (publishServerRowGrant(email, nowSeconds, setStatus)) {
                        return@launch
                    }
                    val entry = Vpn5gPromoCodes.findCode(
                        input, _uiState.value.promoCodes
                    )
                    if (entry != null && tryRecoverGrant(
                            entry, email, nowSeconds, setStatus
                        )
                    ) {
                        // Recovered: the grant is published and
                        // REDEEMED is set by tryRecoverGrant.
                    } else if (entry != null &&
                        Vpn5gPromoCodes.codeKind(entry) ==
                        Vpn5gPromoCodes.CodeKind.DISCOUNT
                    ) {
                        val link = Vpn5gPromoCodes.redeemLink(input)
                        _uiState.update { it.copy(promoBotLink = link) }
                        setStatus(Vpn5gPromoStatus.VALID)
                    } else {
                        // ok:true but no derivable grant: a gift /
                        // subscription code with no derivable grant is
                        // NOT_APPLIED, never INVALID and never VALID
                        // — the server said ok, so the code itself is
                        // not the problem.
                        setStatus(Vpn5gPromoStatus.NOT_APPLIED)
                    }
                    return@launch
                }
                is Vpn5gAuthService.CodeServerResult.Rejected -> {
                    // v1.1.17 (recovery): the server consumed this
                    // code for this email already — re-derive the
                    // grant from the published entry and activate it
                    // locally instead of reporting it used forever.
                    // The server verified ownership by email (the
                    // RPC took p_email), so no local record is
                    // needed here.
                    if (server.status == "already_used") {
                        val entry = Vpn5gPromoCodes.findCode(
                            input, _uiState.value.promoCodes
                        )
                        if (tryRecoverGrant(
                                entry, email, nowSeconds, setStatus
                            )
                        ) return@launch
                    }
                    // v1.1.20: no_registration means the email never
                    // registered — the code was NOT consumed. Ensure
                    // the row right now (best effort) so a re-tap
                    // after registering works, and tell the user to
                    // register first instead of calling the code
                    // invalid.
                    if (server.status == "no_registration") {
                        val session = runCatching {
                            Vpn5gAuthService.currentSession()
                        }.getOrNull()
                        if (session != null) ensureRegistrationRowIfMissing(session)
                    }
                    val status = when (server.status) {
                        "unknown" -> Vpn5gPromoStatus.UNKNOWN_CODE
                        "already_used" -> Vpn5gPromoStatus.ALREADY_REDEEMED
                        "expired" -> Vpn5gPromoStatus.EXPIRED
                        "exhausted" -> Vpn5gPromoStatus.EXHAUSTED
                        "not_yours" -> Vpn5gPromoStatus.NOT_YOURS
                        // v1.1.19: the server rejected the account's
                        // email itself — a distinct red message, not
                        // a generic invalid code.
                        "invalid_email" -> Vpn5gPromoStatus.INVALID_EMAIL
                        // v1.1.20: the email never registered.
                        "no_registration" -> Vpn5gPromoStatus.NO_REGISTRATION
                        else -> Vpn5gPromoStatus.INVALID
                    }
                    setStatus(status)
                    return@launch
                }
                else -> {
                    // v1.1.20: the RPC is down (or the call threw) —
                    // a specific message, never a false invalid, and
                    // NEVER the config-list fallback: a stale
                    // remaining_uses must not decide a redeem, and a
                    // local grant without the server's atomic mark
                    // would let one code be redeemed twice.
                    setStatus(Vpn5gPromoStatus.SERVER_UNAVAILABLE)
                    return@launch
                }
            }
        }
    }

    fun clearGiftCode() {
        _uiState.update {
            it.copy(giftCodeStatus = Vpn5gPromoStatus.IDLE, promoBotLink = null, giftCodeInput = "")
        }
    }

    fun clearSubCode() {
        _uiState.update {
            it.copy(subCodeStatus = Vpn5gPromoStatus.IDLE, promoBotLink = null, subCodeInput = "")
        }
    }

    /**
     * Whether the forced interstitial applies to the NEXT user connect
     * (v1.1.15, master rule): connect #1 never shows an ad; connects
     * #2 and later do, free tier only unless the config says otherwise.
     */
    fun shouldShowInterstitialAd(): Boolean {
        val state = _uiState.value
        return Vpn5gAds.shouldShowInterstitial(
            ads = state.ads,
            completedSessions = completedSessions,
            isSubscriber = state.isSubscriber,
            isGift = state.isGift,
        )
    }

    /**
     * Puts the interstitial on screen for the next user connect;
     * returns false when no ad applies, so the connect proceeds with
     * no ad. The Activity holds the pending connect action and runs it
     * only after [completeInterstitialAd].
     */
    fun prepareInterstitialAd(): Boolean {
        if (!shouldShowInterstitialAd()) return false
        val ad = pickInterstitialAd() ?: return false
        _uiState.update { it.copy(interstitialAd = ad) }
        return true
    }

    /** Picks the ad for the next user connect, or null (v1.1.15). */
    private fun pickInterstitialAd(): Vpn5gAds.InterstitialAdUi? {
        val ads = _uiState.value.ads ?: return null
        val hour = java.util.Calendar.getInstance()
            .get(java.util.Calendar.HOUR_OF_DAY)
        return Vpn5gAds.pickInterstitial(ads, adImpressions.entries, hour)
    }

    /**
     * The ad was FULLY viewed (v1.1.15): record the impression locally
     * (so the caps are enforced) and verify the view server-side,
     * best-effort. If the ad server is off, the local countdown
     * completion still stands — the VPN starts anyway, never hangs,
     * never crashes. The Activity runs the pending connect after this.
     */
    fun completeInterstitialAd() {
        val ad = _uiState.value.interstitialAd ?: return
        val today = java.time.LocalDate.now().toString()
        adImpressions = Vpn5gAds.recordImpression(adImpressions, ad.id, today)
        saveAdImpressions()
        _uiState.update { it.copy(interstitialAd = null) }
        val email = _uiState.value.signedInEmail
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { Vpn5gAuthService.recordAdView(ad.id, email) }
        }
    }

    /**
     * The ad was closed early (v1.1.15): the view is INCOMPLETE and
     * the connection must NOT start. The Activity drops the pending
     * connect action.
     */
    fun cancelInterstitialAd() {
        _uiState.update { it.copy(interstitialAd = null) }
    }

    /**
     * The ad content was tapped (v1.1.15): record the click
     * server-side, best-effort. The Activity opens the link; the tap
     * never ends the countdown.
     */
    fun clickInterstitialAd(url: String) {
        val ad = _uiState.value.interstitialAd ?: return
        if (ad.linkUrl.isNullOrBlank() || ad.linkUrl != url) return
        val email = _uiState.value.signedInEmail
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { Vpn5gAuthService.recordAdClick(ad.id, email) }
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
            // v1.1.18: the service is back — a disconnect in flight
            // is over (either it was a restart racing the disconnect,
            // or the stop never landed). The timeout job is dead.
            disconnectTimeoutJob?.cancel()
            disconnectTimeoutJob = null
            // Start the 30-minute session clock once per session —
            // subscribers have no timer at all (v1.1.11): unlimited
            // session, no auto-disconnect, no countdown. v1.1.14: the
            // GIFT tier has no timer either while the gift lasts.
            if (Vpn5gTimer.connectedAt() == 0L && !_uiState.value.isSubscriber &&
                !_uiState.value.isGift
            ) {
                Vpn5gTimer.scheduleDisconnect(app)
            }
            // Live tunnel speeds (v1.1.14): poll while the session runs.
            Vpn5gSpeedMonitor.start()
            dropFailoverRounds = 0
            watchdogTicks = 0
            watchdogFailures = 0
            sessionActive = true
            sessionStartedAtMs = System.currentTimeMillis()
            userStopRequested = false
            // v1.1.12 ("اتصال پایدار"): the session is persistent now —
            // only a manual disconnect (or the timer above, for free
            // users) may end it. A fresh reconnect run starts from
            // attempt zero, and the battery-optimization exemption is
            // asked for once so cleaner apps cannot kill the service.
            Vpn5gConnectionPersistence.setPersistentConnection(true)
            persistentReconnectAttempts = 0
            persistentReconnectJob?.cancel()
            persistentReconnectJob = null
            maybeRequestBatteryExemption()
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
            // SWITCHING_SERVER (v1.1.5 §2). v1.1.18: a restart racing
            // a user disconnect also lands here, from DISCONNECTING —
            // the new core is a tunnel being created, and the
            // verification below runs for it.
            val chainState = _uiState.value.connectState
            if (chainState == Vpn5gConnectState.CONNECTING ||
                chainState == Vpn5gConnectState.RETRYING ||
                chainState == Vpn5gConnectState.SWITCHING_SERVER ||
                chainState == Vpn5gConnectState.DISCONNECTING
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
            // Live tunnel speeds stop with the session (v1.1.14).
            Vpn5gSpeedMonitor.stop()
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
            // Subscribers have no 30-minute timer (v1.1.11), so a long
            // session ending is never the timer for them — the drop
            // detector must still fail over. Same for the gift tier
            // (v1.1.14): no timer while the gift lasts.
            val timerProbablyEnded = !_uiState.value.isSubscriber &&
                !_uiState.value.isGift &&
                sessionAgeMs >= 29 * 60_000L
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
            // v1.1.12 ("اتصال پایدار"): the 30-minute timer ending a
            // free session is an intentional end — the persistent
            // session ends with it.
            if (timerProbablyEnded) {
                Vpn5gConnectionPersistence.setPersistentConnection(false)
            }
            // An unexpected stop the drop failover does not cover (e.g.
            // the service was killed while a restart was in flight)
            // re-runs the connect chain with backoff, bounded. A
            // manual disconnect never reaches here.
            if (!shouldFailover && Vpn5gConnectionPersistence.shouldAutoReconnect(
                    wasConnected = wasSession,
                    manualStopRequested = userStopRequested,
                    timerProbablyEnded = timerProbablyEnded,
                    tunnelFailedStop = tunnelFailedStop,
                )
            ) {
                schedulePersistentReconnect()
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
                remainingMillis = if (running && !it.isSubscriber) {
                    Vpn5gTimer.remainingMillis()
                } else {
                    null
                },
            )
        }
        viewModelScope.launch(Dispatchers.IO) { refreshSelection() }
    }

    /** Asks once, on the first connect, for the battery-optimization exemption (v1.1.12). */
    private fun maybeRequestBatteryExemption() {
        val app = getApplication<Application>()
        if (Vpn5gBatteryOptimization.wasPrompted()) return
        if (Vpn5gBatteryOptimization.isExempt(app)) return
        Vpn5gBatteryOptimization.markPrompted()
        viewModelScope.launch { _batteryOptEvent.emit(Unit) }
    }

    /**
     * Bounded reconnect after an unexpected stop (v1.1.12): re-runs
     * the full connect chain with exponential backoff, at most
     * [Vpn5gConnectionPersistence.maxAttempts] times, then gives up
     * with a clear error. Cancelled immediately by a manual
     * disconnect ([userDisconnect]) or by the service coming back.
     */
    private fun schedulePersistentReconnect() {
        persistentReconnectJob?.cancel()
        val attempt = persistentReconnectAttempts
        if (attempt >= Vpn5gConnectionPersistence.maxAttempts()) {
            persistentReconnectAttempts = 0
            val reason = getApplication<Application>().getString(
                R.string.vpn5g_persistent_reconnect_failed
            )
            _uiState.update {
                it.copy(
                    connectState = Vpn5gAutoConnect.transition(
                        it.connectState, Vpn5gConnectEvent.GIVE_UP
                    ),
                    lastError = reason,
                )
            }
            return
        }
        persistentReconnectAttempts = attempt + 1
        persistentReconnectJob = viewModelScope.launch {
            delay(Vpn5gConnectionPersistence.nextBackoffMs(attempt))
            if (!isActive) return@launch
            if (_uiState.value.isRunning || userStopRequested) return@launch
            if (!Vpn5gConnectionPersistence.isPersistentConnection()) return@launch
            beginConnect()
        }
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
        // v1.1.23: tier-pool ordering like pickBestServer —
        // real-pinged first, then TCP-pinged pool servers, so a
        // subscriber fails over onto unlocked servers too.
        val pool = _uiState.value.countries
            .flatMap { it.servers }
            .filter {
                it.isFree && it.guid != droppedGuid &&
                    it.guid !in failed && it.pingMillis > 0
            }
            .sortedWith(
                compareBy({ !it.hasRealPing }, { it.pingMillis })
            )
        val candidates = if (pool.isNotEmpty()) pool else _uiState.value.countries
            .flatMap { it.servers }
            .filter {
                it.guid != droppedGuid && it.guid !in failed && it.pingMillis > 0
            }
            .sortedWith(
                compareBy({ !it.hasRealPing }, { it.pingMillis })
            )
        val next = candidates
            .firstOrNull { it.country != droppedCountry }
            ?: candidates.firstOrNull()
        if (next == null) {
            // v1.1.11: an empty pool means the network (or the sources)
            // is dead, not just a bad server. Refresh + backoff a few
            // rounds, then give up with a clear error instead of
            // looping forever on a dead network. When candidates exist
            // the chain still never gives up (v1.1.7).
            dropFailoverRounds++
            if (dropFailoverRounds > MAX_DROP_FAILOVER_ROUNDS) {
                dropFailoverRounds = 0
                autoFailoverPending = false
                // v1.1.13: a persistent session is rescued by the
                // bounded persistent reconnect instead of ending
                // FAILED here — the persistent flag is still set, so
                // the session must not die on an empty pool.
                if (Vpn5gConnectionPersistence.failoverGiveUpAction(
                        Vpn5gConnectionPersistence.isPersistentConnection()
                    ) == Vpn5gConnectionPersistence.FailoverGiveUpAction.PERSISTENT_RECONNECT
                ) {
                    schedulePersistentReconnect()
                    return
                }
                val reason = app.getString(R.string.vpn5g_failover_no_server)
                _uiState.update {
                    it.copy(
                        connectState = Vpn5gAutoConnect.transition(
                            it.connectState, Vpn5gConnectEvent.GIVE_UP
                        ),
                        lastError = reason,
                    )
                }
                return
            }
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
                val countries = Vpn5gSync.loadGroupedServers(forceFreeTier = tamperDetected)
                _uiState.update { it.copy(countries = countries) }
                if (!isActive || userStopRequested || suppressAutoFailover) return@launch
                attemptAutoFailover()
            }
            return
        }
        autoFailoverAttempts++
        // A target was found: the empty-pool rounds reset (v1.1.11).
        dropFailoverRounds = 0
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
                        // v1.1.14: this server proved itself on the
                        // CURRENT operator — record it in the
                        // operator's own history so the auto pick
                        // prefers it next time on this operator.
                        runCatching {
                            val operatorKey = Vpn5gOperator.currentKey()
                            val profile = MmkvManager.decodeServerConfig(guid)
                            if (operatorKey != null && profile != null) {
                                Vpn5gOperator.recordVerified(
                                    operatorKey,
                                    Vpn5gServerValidator.dedupeKey(profile),
                                )
                            }
                        }
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
                                    it.copy(countries = Vpn5gSync.loadGroupedServers(forceFreeTier = tamperDetected))
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
        // v1.1.23: same tier-pool ordering as pickBestServer —
        // real-pinged pool servers first, then TCP-pinged pool
        // servers (the subscriber/gift fill pool), so failover and
        // manual switches can use unlocked servers too. Before
        // this, only real-pinged servers were candidates and a
        // subscriber's unlocked pool was invisible here.
        val pool = _uiState.value.countries
            .flatMap { it.servers }
            .filter {
                it.isFree && it.guid !in excluding &&
                    it.guid !in failed && it.pingMillis > 0
            }
            .sortedWith(
                compareBy({ !it.hasRealPing }, { it.pingMillis })
            )
        val candidates = if (pool.isNotEmpty()) pool else _uiState.value.countries
            .flatMap { it.servers }
            .filter {
                it.guid !in excluding && it.guid !in failed && it.pingMillis > 0
            }
            .sortedWith(
                compareBy({ !it.hasRealPing }, { it.pingMillis })
            )
        if (differentCountryThan.isNullOrBlank()) {
            return candidates.firstOrNull()
        }
        return candidates.firstOrNull { it.country != differentCountryThan }
            ?: candidates.firstOrNull()
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

    /**
     * One light watchdog probe (v1.1.11): a single exit-IP fetch
     * through the tunnel. Three consecutive FAILED probes drop the
     * tunnel — the drop detector then fails over to another server.
     * A single failure is a blip and is forgiven; UNKNOWN (the check
     * cannot run) is not a failure. Never runs while a verification
     * or a failover restart is in flight, and never overlaps itself.
     */
    private fun runWatchdogProbe() {
        if (watchdogJob?.isActive == true) return
        if (verifyJob?.isActive == true || reverifyJob?.isActive == true) return
        if (failoverRestartInProgress) return
        watchdogJob = viewModelScope.launch(Dispatchers.IO) {
            if (!_uiState.value.isRunning ||
                _uiState.value.connectState != Vpn5gConnectState.CONNECTED
            ) {
                return@launch
            }
            val result = Vpn5gTunnelCheck.probeOnce(baselineIp)
            if (!_uiState.value.isRunning ||
                _uiState.value.connectState != Vpn5gConnectState.CONNECTED
            ) {
                return@launch
            }
            when (result.verdict) {
                Vpn5gTunnelCheck.Verdict.VERIFIED,
                Vpn5gTunnelCheck.Verdict.UNKNOWN -> watchdogFailures = 0
                Vpn5gTunnelCheck.Verdict.FAILED -> {
                    watchdogFailures++
                    if (watchdogFailures >= WATCHDOG_MAX_CONSECUTIVE_FAILURES) {
                        watchdogFailures = 0
                        LogUtil.i(
                            AppConfig.TAG,
                            "Vpn5gHomeViewModel: watchdog probe failed " +
                                "$WATCHDOG_MAX_CONSECUTIVE_FAILURES times in a row; dropping",
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
            }
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
                currentServerProtocol = server?.protocol.orEmpty(),
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

    /**
     * Connect action waiting for the interstitial to finish (v1.1.15):
     * set by [requestUserConnect] when the forced ad shows; run only
     * after the full view ([Vpn5gHomeViewModel.completeInterstitialAd]),
     * dropped on an incomplete view.
     */
    private var pendingConnectAction: (() -> Unit)? = null

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
        // v1.1.15 (forced ad): closing the interstitial early is an
        // INCOMPLETE view — the connection must NOT start. The system
        // Back press while an ad is on screen cancels the ad (and the
        // pending connect) instead of leaving the app.
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (viewModel.uiState.value.interstitialAd != null) {
                        pendingConnectAction = null
                        viewModel.cancelInterstitialAd()
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            },
        )
    }

    override fun onResume() {
        super.onResume()
        // Re-read the daemon state when returning to the foreground
        // (v1.1.4 §7): a state change while the app was backgrounded
        // must not leave a stale home screen.
        viewModel.refreshDaemonState()
        // v1.1.22 (fix #4): the subscription status and the locks must
        // never be stale after a return to the app — refresh them
        // (debounced inside; the server row is best-effort).
        viewModel.refreshSubscriptionAndLocks()
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
        // Gift-code deep entry (v1.1.15, C): the ALL-tab gift button
        // bumps this counter and opens Premium; Premium expands its
        // gift section when the counter changes.
        var giftCodeRequest by remember { mutableStateOf(0) }

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
        // v1.1.16 (crash fix): a throw inside a collect lambda used
        // to kill the app — the LaunchedEffect coroutine has no
        // handler. Each event body is guarded now; a failed start is
        // logged and the collector keeps running.
        LaunchedEffect(Unit) {
            viewModel.failoverEvent.collect { guid ->
                runCatching { requestServiceRestartOrStart(guid) }
                    .onFailure {
                        LogUtil.e(AppConfig.TAG, "Vpn5gMainActivity: failover start failed", it)
                    }
            }
        }

        // One-button connect: the ViewModel picked a server after the
        // internet check; the Activity performs the permission-aware start.
        LaunchedEffect(Unit) {
            viewModel.connectEvent.collect { guid ->
                runCatching { requestServiceStart(guid) }
                    .onFailure {
                        LogUtil.e(AppConfig.TAG, "Vpn5gMainActivity: connect start failed", it)
                    }
            }
        }

        // App update: the ViewModel asks for the installer once the APK
        // has downloaded; the Activity owns the install intent + grant flow.
        LaunchedEffect(Unit) {
            viewModel.updateInstallEvent.collect { file ->
                runCatching { installApk(file) }
                    .onFailure {
                        LogUtil.e(AppConfig.TAG, "Vpn5gMainActivity: update install failed", it)
                    }
            }
        }

        // Battery-optimization exemption (v1.1.12): asked once, on the
        // first connect; the system dialog explains itself.
        LaunchedEffect(Unit) {
            viewModel.batteryOptEvent.collect {
                runCatching {
                    startActivity(
                        Vpn5gBatteryOptimization.exemptionIntent(this@Vpn5gMainActivity)
                    )
                }
            }
        }

        // Carrier-name permission (v1.1.14, per-operator adaptation):
        // requested once, only for this feature; denied = the operator
        // just shows as "mobile" and nothing else changes. The
        // notification permission (moved here from the hidden
        // MainActivity) is requested alongside it.
        LaunchedEffect(Unit) {
            checkAndRequestPermission(PermissionType.POST_NOTIFICATIONS) {}
            checkAndRequestPermission(PermissionType.READ_PHONE_STATE) {
                viewModel.refreshOperator()
            }
        }

        // Bot→app sync (v1.1.14): entering Premium re-fetches ONLY
        // app_config.json, so bot-side plan/price/announcement/code
        // changes show without an app update.
        // v1.1.16 (login without restart): leaving the AUTH screen
        // re-reads the session + subscription at once, so a successful
        // login applies immediately — no app restart.
        var previousDestination by remember { mutableStateOf(destination) }
        LaunchedEffect(destination) {
            if (destination == Vpn5gDestination.PREMIUM) viewModel.refreshConfig()
            if (previousDestination == Vpn5gDestination.AUTH &&
                destination != Vpn5gDestination.AUTH
            ) {
                viewModel.refreshAuthState()
            }
            previousDestination = destination
        }

        // Startup sync screen (v1.1.14): real 0→100% server-update
        // progress — it REPLACES the old fixed 3-second splash, it does
        // not add a second wait on top. The home opens when the sync
        // reaches 100% AND at least one server is available; an empty
        // sync shows a retry state instead of hanging.
        if (!uiState.startupDone) {
            Vpn5gStartupScreen(
                progress = uiState.startupProgress,
                isError = uiState.startupError,
                onRetry = { viewModel.retryStartupSync() },
            )
            return
        }

        // Forced interstitial (v1.1.15): the full-screen ad replaces the
        // home until the countdown completes; the pending connect runs
        // only after the full view, never on an early close.
        uiState.interstitialAd?.let { ad ->
            Vpn5gInterstitialAdScreen(
                ad = ad,
                onAdClick = { url ->
                    viewModel.clickInterstitialAd(url)
                    Utils.openUri(this@Vpn5gMainActivity, url)
                },
                onComplete = {
                    val action = pendingConnectAction
                    pendingConnectAction = null
                    viewModel.completeInterstitialAd()
                    action?.invoke()
                },
                onIncomplete = {
                    pendingConnectAction = null
                    viewModel.cancelInterstitialAd()
                },
            )
            return
        }

        // Hamburger drawer (v1.1.21, Lantern): the drawer holds the
        // settings that used to live only in Me — upgrade, login, VPN
        // settings (routing + per-app bypass), usage, language,
        // appearance, support, about. Closing is a suspend call, so
        // navigation fires at once and the drawer animates shut behind.
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val drawerScope = rememberCoroutineScope()
        val closeDrawerAndNavigate: (Vpn5gDestination) -> Unit = { dest ->
            drawerScope.launch { drawerState.close() }
            destination = dest
        }
        var showBellAnnouncement by remember { mutableStateOf(false) }

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                Vpn5gDrawerContent(
                    state = uiState,
                    onOpenPremium = { closeDrawerAndNavigate(Vpn5gDestination.PREMIUM) },
                    onOpenAuth = { closeDrawerAndNavigate(Vpn5gDestination.AUTH) },
                    onOpenMe = { closeDrawerAndNavigate(Vpn5gDestination.ME) },
                    onOpenRouting = { closeDrawerAndNavigate(Vpn5gDestination.ROUTING) },
                    onOpenPerAppBypass = {
                        drawerScope.launch { drawerState.close() }
                        startActivity(
                            Intent(
                                this@Vpn5gMainActivity,
                                PerAppProxyActivity::class.java,
                            )
                        )
                    },
                    onOpenSupport = {
                        drawerScope.launch { drawerState.close() }
                        Utils.openUri(this@Vpn5gMainActivity, SUPPORT_BOT_URL)
                    },
                    onLanguageChange = { viewModel.setLanguage(it) },
                    onThemeModeChange = { viewModel.setThemeMode(it) },
                )
            },
        ) {
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
                            // v1.1.21: the server list lives on its own
                            // screen now — home only links to it.
                            onOpenDrawer = { drawerScope.launch { drawerState.open() } },
                            onOpenServerSelect = { destination = Vpn5gDestination.SERVER_SELECT },
                            onOpenRouting = { destination = Vpn5gDestination.ROUTING },
                            onOpenPerAppBypass = {
                                startActivity(
                                    Intent(
                                        this@Vpn5gMainActivity,
                                        PerAppProxyActivity::class.java,
                                    )
                                )
                            },
                            onBellClick = { showBellAnnouncement = true },
                            onRetryConnect = {
                                requestUserConnect {
                                    viewModel.beginConnect(userInitiated = true)
                                }
                            },
                            onSelectAutoMode = { viewModel.selectAutoMode() },
                            onClearManualFailure = { viewModel.clearManualFailure() },
                            onOpenPremium = { destination = Vpn5gDestination.PREMIUM },
                            onOpenUrl = { url -> Utils.openUri(this@Vpn5gMainActivity, url) },
                            onConsumeAutoFailoverNotice = { viewModel.consumeAutoFailoverNotice() },
                            onUpdateDownload = { viewModel.startUpdateDownload() },
                            onDismissUpdate = { viewModel.dismissUpdate() },
                            onDismissAnnouncement = { viewModel.dismissAnnouncement() },
                            onDismissStartupNotice = { viewModel.dismissStartupNotice() },
                            onStartupNoticeShown = { viewModel.markStartupNoticeShown() },
                            onNextStartupNotice = { viewModel.advanceStartupNotice() },
                            onAcceptTerms = { viewModel.acceptTerms() },
                            onOpenLogin = { destination = Vpn5gDestination.AUTH },
                            onShareCrashReport = {
                                Vpn5gCrashHandler.shareIntent(this@Vpn5gMainActivity)?.let { intent ->
                                    startActivity(
                                        Intent.createChooser(
                                            intent,
                                            getString(R.string.vpn5g_crash_report_share),
                                        )
                                    )
                                }
                            },
                            onDismissCrashReport = { viewModel.dismissCrashReport() },
                        )
                        // v1.1.21: the server list moved out of home
                        // into its own Lantern-style screen.
                        Vpn5gDestination.SERVER_SELECT -> Vpn5gServerSelectScreen(
                            state = uiState,
                            onBack = { destination = Vpn5gDestination.HOME },
                            onRefresh = { viewModel.refresh() },
                            onServerTap = { guid -> onServerTap(guid) },
                            onAutoConnect = { connectBestServer() },
                            onSelectManualServer = { guid -> viewModel.selectManualServer(guid) },
                            onSelectAutoMode = { viewModel.selectAutoMode() },
                            onLockedCountryTap = { lockedCountry = it },
                            onOpenPremium = { destination = Vpn5gDestination.PREMIUM },
                            onOpenGiftCode = {
                                giftCodeRequest++
                                destination = Vpn5gDestination.PREMIUM
                            },
                        )
                        // v1.1.21: Lantern routing-mode screen.
                        Vpn5gDestination.ROUTING -> Vpn5gRoutingScreen(
                            state = uiState,
                            onBack = { destination = Vpn5gDestination.HOME },
                            onModeChange = { viewModel.setRoutingMode(it) },
                            onDirectFirstChange = { viewModel.setDirectFirst(it) },
                            onOpenPerAppBypass = {
                                startActivity(
                                    Intent(
                                        this@Vpn5gMainActivity,
                                        PerAppProxyActivity::class.java,
                                    )
                                )
                            },
                        )
                        // v1.1.21: Lantern features page.
                        Vpn5gDestination.FEATURES -> Vpn5gFeaturesScreen(
                            onBack = { destination = Vpn5gDestination.HOME },
                            state = uiState,
                            onOpenPremium = { destination = Vpn5gDestination.PREMIUM },
                        )
                        Vpn5gDestination.PREMIUM -> Vpn5gSubscriptionScreen(
                            state = uiState,
                            onBack = { destination = Vpn5gDestination.HOME },
                            onEntered = { viewModel.refreshSubscriptionAndLocks() },
                            onOpenBot = { Utils.openUri(this@Vpn5gMainActivity, BOT_URL) },
                            onOpenBuyBot = { planId ->
                                Utils.openUri(
                                    this@Vpn5gMainActivity,
                                    Vpn5gConstants.buyUrl(planId),
                                )
                            },
                            giftCodeRequest = giftCodeRequest,
                            onToggleCountryExpanded = { viewModel.toggleCountryExpanded(it) },
                            onOpenAuth = { destination = Vpn5gDestination.AUTH },
                            onSignOut = { viewModel.signOut() },
                            onOpenUrl = { url -> Utils.openUri(this@Vpn5gMainActivity, url) },
                            onGiftCodeInputChange = { viewModel.setGiftCodeInput(it) },
                            onRedeemGiftCode = { viewModel.redeemGiftCode() },
                            onSubCodeInputChange = { viewModel.setSubCodeInput(it) },
                            onRedeemSubCode = { viewModel.redeemSubCode() },
                            onOpenPromoBot = {
                                uiState.promoBotLink?.let { link ->
                                    Utils.openUri(this@Vpn5gMainActivity, link)
                                }
                            },
                            onCountryServerTap = { guid -> onServerTap(guid) },
                            onLockedCountryTap = { lockedCountry = it },
                        )
                        Vpn5gDestination.GUIDE -> Vpn5gGuideScreen(
                            onBack = { destination = Vpn5gDestination.HOME },
                            onOpenUrl = { url -> Utils.openUri(this@Vpn5gMainActivity, url) },
                            onOpenFeatures = { destination = Vpn5gDestination.FEATURES },
                        )
                        Vpn5gDestination.ME -> Vpn5gProfileScreen(
                            state = uiState,
                            onBack = { destination = Vpn5gDestination.HOME },
                            onOpenAuth = { destination = Vpn5gDestination.AUTH },
                            onSignOut = { viewModel.signOut() },
                            onOpenUrl = { url -> Utils.openUri(this@Vpn5gMainActivity, url) },
                            onOpenPerAppBypass = {
                                startActivity(
                                    Intent(
                                        this@Vpn5gMainActivity,
                                        PerAppProxyActivity::class.java,
                                    )
                                )
                            },
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

        // Bell (v1.1.21): the top-bar bell opens the latest
        // announcement; dismissing it is a no-op (the announcement
        // card still shows on home until dismissed there).
        if (showBellAnnouncement) {
            AlertDialog(
                onDismissRequest = { showBellAnnouncement = false },
                title = { Text(stringResource(R.string.vpn5g_notifications)) },
                text = {
                    Text(
                        uiState.announcement?.text
                            ?: stringResource(R.string.vpn5g_no_announcement)
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showBellAnnouncement = false }) {
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
                // Sliding pill behind the selected item (v1.1.21:
                // Lantern teal instead of the old cyan).
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(itemWidth - 12.dp)
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Vpn5gTeal.copy(alpha = 0.25f)),
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    destinations.forEach { (target, labelRes) ->
                        val selected = target == destination
                        val iconColor = if (selected) Vpn5gTealLight else Vpn5gOnDarkMuted
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
                                Vpn5gDestination.AUTH,
                                Vpn5gDestination.SERVER_SELECT,
                                Vpn5gDestination.ROUTING,
                                Vpn5gDestination.FEATURES -> {
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

    /**
     * User-initiated connect (v1.1.15, forced-ad rule): connect #1 has
     * no ad; connects #2 and later show the full-screen interstitial
     * FIRST, and the VPN starts only after the full view. Auto-reconnects
     * and failover restarts never go through here and never show an ad.
     * If no ad applies, the connect starts at once.
     */
    private fun requestUserConnect(connectAction: () -> Unit) {
        if (viewModel.uiState.value.isRunning) return
        if (viewModel.prepareInterstitialAd()) {
            pendingConnectAction = connectAction
            return
        }
        connectAction()
    }

    private fun toggleConnect() {
        val state = viewModel.uiState.value
        // v1.1.23: a tap while the chain is testing/connecting (daemon
        // not started yet, isRunning still false) must CANCEL the
        // chain, not restart it. Before this, only a running daemon
        // disconnected — the switch stuck in CONNECTING («در حال
        // آزمایش سرور…») and could never be turned off.
        if (Vpn5gAutoConnect.isConnectActive(state.connectState, state.isRunning)) {
            viewModel.userDisconnect()
            LauncherManager.stopService(this)
            return
        }
        // One-button connect (v1.1.5 §2–§3): the ViewModel runs the
        // internet check and resolves the server from the current mode
        // (manual pick or auto), then hands the GUID back on connectEvent.
        requestUserConnect { viewModel.beginConnect(userInitiated = true) }
    }

    private fun connectBestServer() {
        val state = viewModel.uiState.value
        if (state.isRunning) return
        // The "Auto" row always means automatic selection (v1.1.5 §3):
        // picking auto again clears any manual pick.
        requestUserConnect {
            viewModel.selectAutoMode()
            viewModel.beginConnect(userInitiated = true)
        }
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
        requestUserConnect {
            viewModel.selectManualServer(guid)
            viewModel.beginConnect(userInitiated = true)
        }
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
            .background(Vpn5gTeal.copy(alpha = 0.25f))
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
                color = Vpn5gTealLight,
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
                        containerColor = Vpn5gTeal,
                        contentColor = Color.White,
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
    onOpenDrawer: () -> Unit,
    onOpenServerSelect: () -> Unit,
    onOpenRouting: () -> Unit,
    onOpenPerAppBypass: () -> Unit,
    onBellClick: () -> Unit,
    onRetryConnect: () -> Unit,
    // Manual-failure card (v1.1.5 §3): the pick is made on the server
    // screen, but the failure surfaces on home — returning to auto and
    // clearing the failure are still home actions.
    onSelectAutoMode: () -> Unit,
    onClearManualFailure: () -> Unit,
    onOpenPremium: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onConsumeAutoFailoverNotice: () -> Unit,
    onUpdateDownload: () -> Unit,
    onDismissUpdate: () -> Unit,
    onDismissAnnouncement: () -> Unit,
    /** Startup notice (v1.1.21): dismiss for the current session only. */
    onDismissStartupNotice: () -> Unit = {},
    /** Startup notice (v1.1.22): the notice was displayed — persist
     * the shown-flag so it never shows again on later launches. */
    onStartupNoticeShown: () -> Unit = {},
    /** Startup notice (v1.1.21): «بعدی» — advance one page. */
    onNextStartupNotice: () -> Unit = {},
    /** Startup notice (v1.1.21): last page — tick the T&C checkbox. */
    onAcceptTerms: () -> Unit = {},
    /** Startup notice (v1.1.21): last page — open the login page. */
    onOpenLogin: () -> Unit = {},
    /** Crash report from an earlier run (v1.1.16): share / dismiss. */
    onShareCrashReport: () -> Unit = {},
    onDismissCrashReport: () -> Unit = {},
) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

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
        Vpn5gConnectState.DISCONNECTING -> R.string.vpn5g_disconnecting
        Vpn5gConnectState.IDLE -> R.string.vpn5g_status_not_connected
    }
    val statusColor = when (state.connectState) {
        Vpn5gConnectState.CONNECTED -> colorPing
        Vpn5gConnectState.FAILED -> colorPingRed
        Vpn5gConnectState.IDLE -> Vpn5gOnDarkMuted
        // RETRYING is a connecting state, not a failure: the chain is
        // switching servers, so it reads teal like the other
        // connecting states (v1.1.5 refinement).
        else -> Vpn5gTealLight
    }

    // Best free server (v1.1.21): the canonical auto pick, for the
    // status card's smart-location row — the same pick auto-connect
    // uses, so the row never promises a server the engine would not
    // try first. A config with no ping result is never returned.
    val bestServer = Vpn5gAutoConnect.pickBestServer(state.countries)
    // Split tunnel is live only under Smart routing with direct-first
    // on — the single tested rule in [Vpn5gRouting.isSplitTunnelActive];
    // the row shows that derived state, never a separate stored flag.
    val splitActive = Vpn5gRouting.isSplitTunnelActive(state.routingMode, state.directFirst)

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
                // Top bar (Lantern, v1.1.21): hamburger + bell on the
                // sides, the shield logo + red-5G wordmark centred.
                Vpn5gStaggeredItem(index = 0, visible = entered) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        IconButton(
                            onClick = onOpenDrawer,
                            modifier = Modifier.align(Alignment.CenterStart),
                        ) {
                            Vpn5gMenuIcon(color = Vpn5gOnDark, size = 26.dp)
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.align(Alignment.Center),
                        ) {
                            Vpn5gShieldLogo(width = 44.dp)
                            Vpn5gBrandText(fontSize = 20.sp)
                            Text(
                                text = stringResource(R.string.vpn5g_app_tagline),
                                color = Vpn5gOnDarkMuted,
                                fontSize = 12.sp,
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.align(Alignment.CenterEnd),
                        ) {
                            // Premium pill (v1.1.12): subscribers only.
                            if (state.isSubscriber) {
                                Vpn5gPremiumPill()
                                Spacer(Modifier.width(8.dp))
                            }
                            IconButton(onClick = onBellClick) {
                                Vpn5gBellIcon(color = Vpn5gOnDark, size = 26.dp)
                            }
                        }
                    }
                }
            }

            // Startup notice (v1.1.21, Aref 2026-11-25; once-only
            // since v1.1.22): paged — one point per page, shown ONLY
            // on the first entry after install, dismissible for the
            // current session from the last page. While the user reads
            // through the pages (~2 minutes) the server updates in
            // the background. No stagger: the notice must be visible
            // on entry, not fade in late. The shown-flag is persisted
            // the first time this item composes, so it never shows
            // again on later launches.
            if (shouldShowStartupNotice(
                    state.startupNoticeShownBefore,
                    state.startupNoticeDismissed,
                )
            ) {
                item(key = "startup_notice") {
                    LaunchedEffect(Unit) { onStartupNoticeShown() }
                    Vpn5gStartupNoticeCard(
                        page = state.startupNoticePage,
                        termsAccepted = state.termsAccepted,
                        onNext = onNextStartupNotice,
                        onAcceptTerms = onAcceptTerms,
                        onOpenLogin = onOpenLogin,
                        onDismiss = onDismissStartupNotice,
                    )
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

            // Current network operator (v1.1.14): Wi-Fi / carrier, from
            // the device itself — never invented. Tapping it re-detects.
            state.operatorLabel?.let { operator ->
                item(key = "operator_row") {
                    Text(
                        text = stringResource(R.string.vpn5g_operator_label, operator),
                        color = Vpn5gOnDarkMuted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item {
                // Sliding on/off connect switch (v1.1.22): Lantern
                // style, same states as the old circular button.
                Vpn5gStaggeredItem(index = 2, visible = entered) {
                    Vpn5gConnectSwitch(
                        connectState = state.connectState,
                        remainingFraction = state.remainingMillis
                            ?.let { (it / (30L * 60_000L).toFloat()).coerceIn(0f, 1f) },
                        onClick = onToggleConnect,
                    )
                }
            }

            // Tap hint under the button: connect when idle, disconnect
            // when connected. Intermediate states already say what is
            // happening in the status text above.
            if (state.connectState == Vpn5gConnectState.IDLE ||
                state.connectState == Vpn5gConnectState.CONNECTED
            ) {
                item {
                    Text(
                        text = stringResource(
                            if (state.connectState == Vpn5gConnectState.CONNECTED) {
                                R.string.vpn5g_tap_to_disconnect
                            } else {
                                R.string.vpn5g_tap_to_connect
                            }
                        ),
                        color = Vpn5gOnDarkMuted,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
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

            // VPN status card (Lantern, v1.1.21): the current server,
            // smart location, routing and split tunnel in one card.
            // The current-server row only exists while a session runs;
            // the other three rows are settings and always apply.
            item {
                Vpn5gStaggeredItem(index = 3, visible = entered) {
                    Vpn5gGlassCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(R.string.vpn5g_vpn_status_title),
                                color = Vpn5gOnDark,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (state.connectState == Vpn5gConnectState.CONNECTED) colorPing
                                        else Vpn5gOnDarkMuted.copy(alpha = 0.4f)
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
                        if (state.isRunning && state.currentServerName != null) {
                            val currentPingText =
                                if (state.currentHasRealPing && state.currentPingMillis > 0L) {
                                    stringResource(
                                        R.string.vpn5g_ping_ms,
                                        state.currentPingMillis,
                                    )
                                } else {
                                    "—"
                                }
                            val protocol = state.currentServerProtocol
                                .uppercase().ifBlank { null }
                            Vpn5gStatusRow(
                                title = state.currentServerName,
                                subtitle = listOfNotNull(protocol, currentPingText)
                                    .joinToString(" · "),
                                onClick = onOpenServerSelect,
                                leading = {
                                    Text(
                                        text = state.currentServerFlag,
                                        fontSize = 22.sp,
                                    )
                                },
                            )
                        }
                        Vpn5gStatusRow(
                            title = stringResource(R.string.vpn5g_smart_location),
                            subtitle = bestServer?.let { server ->
                                val pingText =
                                    if (server.hasRealPing && server.pingMillis > 0L) {
                                        stringResource(
                                            R.string.vpn5g_ping_ms,
                                            server.pingMillis,
                                        )
                                    } else {
                                        "—"
                                    }
                                server.flag + " " + displayCountryName(
                                    server,
                                    stringResource(R.string.vpn5g_country_unknown),
                                ) + " · " + pingText
                            } ?: stringResource(R.string.vpn5g_empty_list),
                            onClick = onOpenServerSelect,
                            leading = {
                                Vpn5gGlobeIcon(color = Vpn5gTeal, size = 24.dp)
                            },
                        )
                        Vpn5gStatusRow(
                            title = stringResource(R.string.vpn5g_routing_title),
                            subtitle = when (state.routingMode) {
                                Vpn5gRoutingMode.SMART -> stringResource(
                                    R.string.vpn5g_routing_smart
                                ) + " · " + stringResource(
                                    if (state.directFirst) R.string.vpn5g_split_on
                                    else R.string.vpn5g_split_off
                                )
                                Vpn5gRoutingMode.FULL -> stringResource(
                                    R.string.vpn5g_routing_full
                                )
                            },
                            onClick = onOpenRouting,
                            leading = {
                                Vpn5gRoutesIcon(color = Vpn5gTeal, size = 24.dp)
                            },
                        )
                        Vpn5gStatusRow(
                            title = stringResource(R.string.vpn5g_split_tunnel_title),
                            subtitle = stringResource(
                                if (splitActive) R.string.vpn5g_split_on
                                else R.string.vpn5g_split_off
                            ),
                            onClick = onOpenPerAppBypass,
                            leading = {
                                Vpn5gAppsIcon(color = Vpn5gTeal, size = 24.dp)
                            },
                        )
                        // Session footer (v1.1.11/1.1.14): subscribers
                        // have no 30-minute timer; the gift tier shows
                        // its remaining hours; free shows the countdown.
                        // The verified exit IP shows only after the
                        // tunnel proved real traffic.
                        if (state.isRunning) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = when {
                                    state.isSubscriber -> stringResource(
                                        R.string.vpn5g_session_unlimited
                                    )
                                    state.isGift -> stringResource(
                                        R.string.vpn5g_gift_remaining_hours,
                                        state.giftRemainingHours,
                                    )
                                    else -> stringResource(
                                        R.string.vpn5g_remaining_time,
                                        formatRemaining(state.remainingMillis),
                                    )
                                },
                                color = Vpn5gOnDark,
                                fontSize = 14.sp,
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
                        }
                    }
                }
            }

            // Live tunnel speeds (v1.1.14): tunnel-only (proxy) rates,
            // ~1 s poll; dashes while the counter has no data yet —
            // never a made-up number.
            if (state.isRunning) {
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Vpn5gGlassCard(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.vpn5g_speed_download),
                                color = Vpn5gOnDarkMuted,
                                fontSize = 13.sp,
                            )
                            Text(
                                text = if (state.downloadSpeedBps > 0L) {
                                    state.downloadSpeedBps.toSpeedString()
                                } else {
                                    "—"
                                },
                                color = colorPing,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Vpn5gGlassCard(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.vpn5g_speed_upload),
                                color = Vpn5gOnDarkMuted,
                                fontSize = 13.sp,
                            )
                            Text(
                                text = if (state.uploadSpeedBps > 0L) {
                                    state.uploadSpeedBps.toSpeedString()
                                } else {
                                    "—"
                                },
                                color = colorPing,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            // Pro upgrade card (Lantern, v1.1.21): replaces the old
            // premium + redeem banners — one gold-bordered card to
            // Premium, where the plans and the code boxes live.
            // v1.1.22 (fix #3): while ANY subscription is active
            // (gift or sub) the upsell becomes the subscription
            // details card; when it ends, the upsell returns.
            if (hasActiveSubscription(state)) {
                item {
                    Vpn5gSubscriptionDetailsCard(
                        state = state,
                        onOpenPremium = onOpenPremium,
                    )
                }
            } else {
                item {
                    Vpn5gProUpgradeCard(onOpenPremium = onOpenPremium)
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
                            .background(Vpn5gTeal.copy(alpha = 0.2f))
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
                            .background(Vpn5gTeal.copy(alpha = 0.2f))
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
                            .background(Vpn5gTeal.copy(alpha = 0.2f))
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
                        onOpenUrl = onOpenUrl,
                    )
                }
            }

            // Crash report from an earlier run (v1.1.16): the app died
            // with the "this app has a bug" dialog and left a short
            // report behind. The user can share it with support or
            // dismiss it; nothing is ever uploaded by the app itself.
            if (state.crashReportAvailable) {
                item {
                    Vpn5gCrashReportCard(
                        onShare = onShareCrashReport,
                        onDismiss = onDismissCrashReport,
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
/**
 * Startup notice (v1.1.21, Aref 2026-11-25): a paged Lantern card on
 * home, shown on EVERY app entry. Page 1 first only — «بعدی» advances
 * one page at a time. The copy is an engaging tour of the real
 * features (20 free servers, 30-minute session, gift codes, Premium
 * countries, auto-connect) — enough reading for about 2 minutes while
 * the server updates in the background. The last page is Terms &
 * Conditions with a checkbox: ticking it persists the acceptance
 * (MMKV — never asked again on later launches) and enables the login
 * button, which opens the login page where the 20 free servers are
 * ready. The close button dismisses the notice for the current session
 * only (the flag is in-memory, so the next launch shows it again).
 * The checkbox row is the single activation node for acceptance (the
 * inner Checkbox's semantics are cleared); the page dots are
 * decoration only, never a focus target.
 */
@Composable
private fun Vpn5gStartupNoticeCard(
    page: Int,
    termsAccepted: Boolean,
    onNext: () -> Unit,
    onAcceptTerms: () -> Unit,
    onOpenLogin: () -> Unit,
    onDismiss: () -> Unit,
) {
    val safePage = page.coerceIn(0, 2)
    val titleRes = when (safePage) {
        0 -> R.string.vpn5g_startup_notice_title1
        1 -> R.string.vpn5g_startup_notice_title2
        else -> R.string.vpn5g_startup_notice_title3
    }
    val bodyRes = when (safePage) {
        0 -> R.string.vpn5g_startup_notice_page1
        1 -> R.string.vpn5g_startup_notice_page2
        else -> R.string.vpn5g_startup_notice_terms
    }
    Vpn5gGlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Vpn5gGlobeIcon(color = Vpn5gTealLight, size = 24.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(titleRes),
                    color = Vpn5gOnDark,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(bodyRes),
                    color = Vpn5gOnDark,
                    fontSize = 14.sp,
                )
            }
        }
        if (safePage >= 2) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = termsAccepted,
                        onValueChange = { onAcceptTerms() },
                        role = Role.Checkbox,
                        enabled = !termsAccepted,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = termsAccepted,
                    onCheckedChange = null,
                    modifier = Modifier.clearAndSetSemantics {},
                    colors = CheckboxDefaults.colors(
                        checkedColor = Vpn5gTeal,
                        uncheckedColor = Vpn5gOnDarkMuted,
                        checkmarkColor = Color.White,
                        disabledCheckedColor = Vpn5gTeal,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.vpn5g_startup_notice_accept),
                    color = Vpn5gOnDark,
                    fontSize = 13.sp,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0..2) {
                    Box(
                        modifier = Modifier
                            .size(if (i == safePage) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == safePage) Vpn5gTealLight else Vpn5gOnDarkMuted
                            ),
                    )
                }
            }
            if (safePage >= 2) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onOpenLogin,
                        enabled = termsAccepted,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Vpn5gTeal,
                            contentColor = Color.White,
                            disabledContainerColor = Vpn5gCardBorder,
                            disabledContentColor = Vpn5gOnDarkMuted,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.vpn5g_startup_notice_login),
                            fontSize = 14.sp,
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = stringResource(R.string.vpn5g_close),
                            color = Vpn5gOnDarkMuted,
                            fontSize = 13.sp,
                        )
                    }
                }
            } else {
                Button(
                    onClick = onNext,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Vpn5gTeal,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.vpn5g_next),
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun Vpn5gAnnouncementCard(
    announcement: AnnouncementRemote,
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    var showImage by remember { mutableStateOf(!announcement.image_url.isNullOrBlank()) }
    // v1.1.15: glass card; the text + image are ONE focusable node that
    // opens the optional link (F), while dismiss stays a separate action.
    val link = announcement.link_url
    Vpn5gGlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (!link.isNullOrBlank()) {
                            Modifier
                                .semantics(mergeDescendants = true) {}
                                .clickable(
                                    onClickLabel = stringResource(R.string.vpn5g_open_link),
                                ) { onOpenUrl(link) }
                        } else {
                            Modifier
                        }
                    ),
            ) {
                Text(
                    text = announcement.text.orEmpty(),
                    color = Vpn5gOnDark,
                    fontSize = 14.sp,
                )
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
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.vpn5g_close),
                    color = Vpn5gOnDarkMuted,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

/**
 * Crash-report card (v1.1.16): shown on home when an earlier run left
 * a crash report behind. One action shares the short report text
 * with support (the user picks the app); the other deletes it. The
 * app never uploads anything itself.
 */
@Composable
private fun Vpn5gCrashReportCard(
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    Vpn5gGlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.vpn5g_crash_report_title),
                color = Vpn5gOnDark,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.vpn5g_crash_report_body),
                color = Vpn5gOnDarkMuted,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onShare,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Vpn5gTeal,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(R.string.vpn5g_crash_report_share),
                        fontSize = 14.sp,
                    )
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(R.string.vpn5g_close),
                        color = Vpn5gOnDarkMuted,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
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
                    containerColor = Vpn5gTeal,
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
                        color = Vpn5gTealLight,
                        fontSize = 15.sp,
                    )
                }
            }
        }
    }
}

/**
 * Visual states of the connect switch (v1.1.22, pure): the same
 * connect state machine the circular button showed, mapped to a
 * sliding switch. JVM-tested in [Vpn5gConnectSwitchTest].
 */
internal enum class Vpn5gConnectSwitchVisual {
    OFF,
    CONNECTING,
    ON,
    DISCONNECTING,
    FAILED,
}

/** Pure mapping from the tunnel state machine to the switch visual. */
internal fun connectSwitchVisual(
    connectState: Vpn5gConnectState,
): Vpn5gConnectSwitchVisual {
    return when (connectState) {
        Vpn5gConnectState.CONNECTED -> Vpn5gConnectSwitchVisual.ON
        Vpn5gConnectState.FAILED -> Vpn5gConnectSwitchVisual.FAILED
        Vpn5gConnectState.DISCONNECTING -> Vpn5gConnectSwitchVisual.DISCONNECTING
        Vpn5gConnectState.CHECKING_INTERNET,
        Vpn5gConnectState.FINDING_SERVER,
        Vpn5gConnectState.CONNECTING,
        Vpn5gConnectState.CREATING_TUNNEL,
        Vpn5gConnectState.VERIFYING_TUNNEL,
        Vpn5gConnectState.SWITCHING_SERVER,
        Vpn5gConnectState.RETRYING -> Vpn5gConnectSwitchVisual.CONNECTING
        Vpn5gConnectState.IDLE -> Vpn5gConnectSwitchVisual.OFF
    }
}

/**
 * Sliding on/off connect switch (v1.1.22, fix #1, Lantern style):
 * replaces the circular power button — same tap behaviour
 * ([onClick] = toggleConnect) and the same state machine, only the
 * control shape changes. The thumb slides from off (start) to on
 * (end); while connecting it slides back and forth on a blue track;
 * while connected the track is green with a soft glow; while failed
 * the track is red. The 30-minute session progress stays as a thin
 * bar under the switch while connected. One semantic node
 * (Role.Switch): the on/off state comes from the toggleable, the
 * name is the connect/disconnect action label.
 */
@Composable
private fun Vpn5gConnectSwitch(
    connectState: Vpn5gConnectState,
    remainingFraction: Float?,
    onClick: () -> Unit,
) {
    // Derived from the connect state machine only (v1.1.4 §1, v1.1.5
    // §2): connecting while a tunnel is being created or verified or
    // while the chain is switching servers or retrying, on only once
    // the tunnel is verified, red only while failed. DISCONNECTING
    // slides the thumb back — a tap then just re-asks for the same
    // disconnect (toggleConnect guards on isRunning; no second
    // tunnel can start while one is stopping).
    val visual = connectSwitchVisual(connectState)
    val isOn = visual == Vpn5gConnectSwitchVisual.ON
    // v1.1.23: normal switch size (was 232x68 with a 56 dp thumb —
    // Aref, 2026-11-25: «سویچ روشن خاموش بزرگ هست»).
    val trackWidth = 168.dp
    val trackHeight = 52.dp
    val thumbSize = 40.dp
    // Thumb travel: track width minus thumb minus both side insets.
    val travel = trackWidth - thumbSize - 12.dp
    val trackColor = when (visual) {
        Vpn5gConnectSwitchVisual.ON -> Vpn5gGreen
        Vpn5gConnectSwitchVisual.CONNECTING -> Vpn5gElectricBlue
        Vpn5gConnectSwitchVisual.DISCONNECTING ->
            Vpn5gElectricBlue.copy(alpha = 0.55f)
        Vpn5gConnectSwitchVisual.FAILED -> colorPingRed
        Vpn5gConnectSwitchVisual.OFF -> Color(0xFF2A2A2A)
    }
    val glyphColor = when (visual) {
        Vpn5gConnectSwitchVisual.ON -> Vpn5gGreen
        Vpn5gConnectSwitchVisual.CONNECTING -> Vpn5gElectricBlue
        Vpn5gConnectSwitchVisual.DISCONNECTING -> Vpn5gElectricBlue
        Vpn5gConnectSwitchVisual.FAILED -> colorPingRed
        Vpn5gConnectSwitchVisual.OFF -> Vpn5gOnDarkMuted
    }
    // The thumb slides back and forth while connecting. The infinite
    // transition's value is read ONLY while connecting, so it does
    // not recompose the switch in any other state. (animateFloat, not
    // animateDp: this Compose version has no animateDp on
    // InfiniteTransition.)
    val slideTransition = rememberInfiniteTransition(label = "vpn5g_switch_slide")
    val slideFraction by slideTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "vpn5g_switch_slide_fraction",
    )
    // Settled positions animate once (off ↔ on, on → off while
    // disconnecting); the connecting slide is direct.
    val settledOffset by animateDpAsState(
        targetValue = if (isOn) travel else 0.dp,
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "vpn5g_switch_thumb",
    )
    val thumbOffset = if (visual == Vpn5gConnectSwitchVisual.CONNECTING) {
        travel * slideFraction
    } else {
        settledOffset
    }
    val actionLabel = stringResource(
        if (isOn) {
            R.string.vpn5g_tap_to_disconnect
        } else {
            R.string.vpn5g_tap_to_connect
        },
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .shadow(
                    elevation = if (isOn ||
                        visual == Vpn5gConnectSwitchVisual.CONNECTING
                    ) {
                        16.dp
                    } else {
                        0.dp
                    },
                    shape = RoundedCornerShape(50.dp),
                    ambientColor = trackColor.copy(alpha = 0.5f),
                    spotColor = trackColor.copy(alpha = 0.5f),
                )
                .width(trackWidth)
                .height(trackHeight)
                .clip(RoundedCornerShape(50.dp))
                .background(trackColor)
                .toggleable(
                    value = isOn,
                    role = Role.Switch,
                    onValueChange = { onClick() },
                )
                .semantics { contentDescription = actionLabel }
                .padding(6.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(Color.White),
            ) {
                Vpn5gPowerIcon(color = glyphColor, size = 20.dp)
            }
        }
        // While connected the 30-minute session shows as a thin
        // progress bar under the switch (was the ring arc).
        if (isOn && remainingFraction != null) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { remainingFraction.coerceIn(0f, 1f) },
                modifier = Modifier.width(trackWidth),
                color = Vpn5gGreen,
                trackColor = Vpn5gOnDarkMuted.copy(alpha = 0.3f),
            )
        }
    }
}
