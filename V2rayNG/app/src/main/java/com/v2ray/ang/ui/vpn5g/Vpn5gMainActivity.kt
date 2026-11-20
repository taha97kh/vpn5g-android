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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.v2ray.ang.handler.vpn5g.SubscriptionStatusRemote
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.BOT_URL
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.CONFIG_SCHEMES
import com.v2ray.ang.handler.vpn5g.Vpn5gCountryUi
import com.v2ray.ang.handler.vpn5g.Vpn5gRemoteConfig
import com.v2ray.ang.handler.vpn5g.Vpn5gSync
import com.v2ray.ang.handler.vpn5g.Vpn5gTimer
import com.v2ray.ang.helper.MessageHelper
import com.v2ray.ang.ui.base.HelperBaseComponentActivity
import com.v2ray.ang.ui.compose.colorPing
import com.v2ray.ang.ui.compose.colorPingRed
import com.v2ray.ang.ui.main.MainActivity
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.util.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Vpn5gDestination {
    HOME, GUIDE, SUBSCRIPTION, ABOUT, AUTH
}

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
)

/**
 * Home-screen ViewModel: owns sync, running state (via the daemon broadcast,
 * same channel as MainRepository), the 5-minute auto-refresh ticker and the
 * 1-second session-countdown ticker. All network/disk work runs on
 * Dispatchers.IO; Compose only ever sees immutable StateFlow state.
 */
class Vpn5gHomeViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(Vpn5gHomeUiState())
    val uiState: StateFlow<Vpn5gHomeUiState> = _uiState.asStateFlow()

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

        // Show the last stored list immediately; the refresh replaces it.
        viewModelScope.launch(Dispatchers.IO) {
            val cached = Vpn5gRemoteConfig.cached()
            val free = cached.free_countries?.takeIf { it.isNotEmpty() }
                ?: Vpn5gConstants.DEFAULT_FREE_COUNTRIES
            val countries = Vpn5gSync.loadGroupedServers(free)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    countries = countries,
                    subscriptionStatus = cached.subscription_status,
                    notice = cached.notice,
                )
            }
            refreshSelection()
        }

        viewModelScope.launch { refresh() }

        // Auto-refresh every 5 minutes while the screen is open.
        viewModelScope.launch {
            while (isActive) {
                delay(5 * 60_000L)
                refresh()
            }
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
                    )
                }
                refreshSelection()
            } catch (e: Exception) {
                LogUtil.e(AppConfig.TAG, "Vpn5gHomeViewModel: refresh failed", e)
                _uiState.update { it.copy(isRefreshing = false, offline = true) }
            }
        }
    }

    fun consumeError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun onRunningChanged(running: Boolean) {
        val app = getApplication<Application>()
        if (running) {
            // Start the 30-minute session clock once per session.
            if (Vpn5gTimer.connectedAt() == 0L) {
                Vpn5gTimer.scheduleDisconnect(app)
            }
        } else {
            Vpn5gTimer.cancelDisconnect(app)
        }
        _uiState.update {
            it.copy(
                isRunning = running,
                remainingMillis = if (running) Vpn5gTimer.remainingMillis() else null,
            )
        }
        viewModelScope.launch(Dispatchers.IO) { refreshSelection() }
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
 * VPN 5G launcher activity. Owns the VPN-permission flow (mirrors
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

        Vpn5gAnimatedBackground {
            Scaffold(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                bottomBar = {
                    NavigationBar(containerColor = Vpn5gNavyTop.copy(alpha = 0.9f)) {
                        navItem(
                            destination, Vpn5gDestination.HOME,
                            stringResource(R.string.vpn5g_nav_home),
                        ) { destination = Vpn5gDestination.HOME }
                        navItem(
                            destination, Vpn5gDestination.GUIDE,
                            stringResource(R.string.vpn5g_nav_guide),
                        ) { destination = Vpn5gDestination.GUIDE }
                        navItem(
                            destination, Vpn5gDestination.SUBSCRIPTION,
                            stringResource(R.string.vpn5g_nav_subscription),
                        ) { destination = Vpn5gDestination.SUBSCRIPTION }
                        navItem(
                            destination, Vpn5gDestination.ABOUT,
                            stringResource(R.string.vpn5g_nav_about),
                        ) { destination = Vpn5gDestination.ABOUT }
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
                            onLockedCountryTap = { lockedCountry = it },
                            onOpenBot = { Utils.openUri(this@Vpn5gMainActivity, BOT_URL) },
                            onOpenAdvanced = {
                                startActivity(Intent(this@Vpn5gMainActivity, MainActivity::class.java))
                            },
                        )
                        Vpn5gDestination.GUIDE -> Vpn5gGuideScreen(
                            onBack = { destination = Vpn5gDestination.HOME },
                        )
                        Vpn5gDestination.SUBSCRIPTION -> Vpn5gSubscriptionScreen(
                            state = uiState,
                            onBack = { destination = Vpn5gDestination.HOME },
                            onOpenBot = { Utils.openUri(this@Vpn5gMainActivity, BOT_URL) },
                            onOpenAuth = { destination = Vpn5gDestination.AUTH },
                        )
                        Vpn5gDestination.ABOUT -> Vpn5gAboutScreen(
                            onBack = { destination = Vpn5gDestination.HOME },
                            onOpenUrl = { url -> Utils.openUri(this@Vpn5gMainActivity, url) },
                        )
                        Vpn5gDestination.AUTH -> Vpn5gAuthScreen(
                            onBack = { destination = Vpn5gDestination.SUBSCRIPTION },
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
            androidx.compose.runtime.LaunchedEffect(message) {
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
        onClick: () -> Unit,
    ) {
        NavigationBarItem(
            selected = current == target,
            onClick = onClick,
            icon = {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            if (current == target) Vpn5gAccentLight else Vpn5gOnDarkMuted,
                            CircleShape,
                        )
                )
            },
            label = { Text(label, color = Vpn5gOnDark) },
        )
    }

    private fun toggleConnect() {
        val state = viewModel.uiState.value
        if (state.isRunning) {
            LauncherManager.stopService(this)
            return
        }
        val guid = state.selectedGuid
            ?: state.countries.firstOrNull { it.isFree }
                ?.servers?.firstOrNull()?.guid
            ?: run {
                toast(R.string.vpn5g_no_server_selected)
                return
            }
        requestServiceStart(guid)
    }

    private fun onServerTap(guid: String) {
        val state = viewModel.uiState.value
        if (state.isRunning) {
            if (guid == state.selectedGuid) return
            MmkvManager.setSelectServer(guid)
            LauncherManager.restartService(this)
            return
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Vpn5gHomeScreen(
    state: Vpn5gHomeUiState,
    onRefresh: () -> Unit,
    onToggleConnect: () -> Unit,
    onServerTap: (String) -> Unit,
    onLockedCountryTap: (String) -> Unit,
    onOpenBot: () -> Unit,
    onOpenAdvanced: () -> Unit,
) {
    var entered by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { entered = true }

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
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                // Header: pulsing logo + title + status.
                Vpn5gStaggeredItem(index = 0, visible = entered) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Vpn5gPulsingLogo(size = 64.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                color = Vpn5gOnDark,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = stringResource(R.string.vpn5g_app_tagline),
                                color = Vpn5gOnDarkMuted,
                                fontSize = 13.sp,
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
                                color = androidx.compose.ui.graphics.Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            item {
                // Big one-tap connect / disconnect button.
                Vpn5gStaggeredItem(index = 1, visible = entered) {
                    Button(
                        onClick = onToggleConnect,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.isRunning) colorPingRed else Vpn5gAccent,
                            contentColor = androidx.compose.ui.graphics.Color.White,
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                    ) {
                        Text(
                            text = stringResource(
                                if (state.isRunning) R.string.vpn5g_disconnect
                                else R.string.vpn5g_connect
                            ),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            if (state.isRunning) {
                item {
                    // Current server + ping + 30-minute countdown.
                    Vpn5gStaggeredItem(index = 2, visible = entered) {
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

            state.notice?.let { notice ->
                item {
                    Vpn5gStaggeredItem(index = 3, visible = entered) {
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

            if (state.countries.isEmpty() && !state.isLoading) {
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

            // Free sections, then the house-ad card, then locked sections.
            val freeSections = state.countries.filter { it.isFree }
            val lockedSections = state.countries.filter { !it.isFree }
            freeSections.forEach { country ->
                item(key = "country_${country.label}") {
                    Vpn5gCountrySection(
                        country = country,
                        onServerTap = onServerTap,
                        onLockedTap = null,
                    )
                }
            }
            if (freeSections.isNotEmpty() || lockedSections.isNotEmpty()) {
                item(key = "bot_promo") {
                    Vpn5gBotPromoCard(onOpenBot = onOpenBot)
                }
            }
            lockedSections.forEach { country ->
                item(key = "country_${country.label}") {
                    Vpn5gCountrySection(
                        country = country,
                        onServerTap = {},
                        onLockedTap = { onLockedCountryTap(country.label) },
                    )
                }
            }

            item {
                TextButton(onClick = onOpenAdvanced, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.vpn5g_advanced_settings),
                        color = Vpn5gAccentLight,
                        fontSize = 14.sp,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
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
                    text = server.name,
                    color = Vpn5gOnDark,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f),
                )
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
            color = androidx.compose.ui.graphics.Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
