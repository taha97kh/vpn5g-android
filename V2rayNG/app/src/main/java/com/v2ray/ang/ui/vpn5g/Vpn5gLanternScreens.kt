package com.v2ray.ang.ui.vpn5g

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.handler.vpn5g.Vpn5gRouting
import com.v2ray.ang.handler.vpn5g.Vpn5gRoutingMode

/**
 * Lantern-style screens (v1.1.21): the hamburger drawer, the routing-mode
 * screen and the features page. The look comes from Aref's Lantern
 * reference; every item is TAHA VPN 5G's own — no Lantern name, no
 * invented feature, no invented city.
 */

/**
 * Visible top back button (v1.1.29, Aref's order): every screen
 * reachable from the hamburger menu carries one at the top, so
 * the way back is seen without scrolling (the older bottom
 * «خانه» text buttons stay). The chevron follows the layout
 * direction — back points right in RTL, left in LTR.
 */
@Composable
fun Vpn5gBackRow(onBack: () -> Unit) {
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current ==
        androidx.compose.ui.unit.LayoutDirection.Rtl
    Row(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(50.dp))
                .border(1.dp, Vpn5gTeal, RoundedCornerShape(50.dp))
                .clickable(role = Role.Button, onClick = onBack)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text(
                text = if (rtl) "›" else "‹",
                color = Vpn5gTealLight,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.vpn5g_back),
                color = Vpn5gTealLight,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ---------------------------------------------------------------- drawer

/**
 * Hamburger side menu (Lantern settings walkthrough): Pro upgrade,
 * login, VPN settings, usage stats, language, appearance, support,
 * about. Navigation callbacks are the parent's — the parent closes
 * the drawer and navigates; this composable only calls them.
 */
@Composable
fun Vpn5gDrawerContent(
    state: Vpn5gHomeUiState,
    onOpenPremium: () -> Unit,
    onOpenAuth: () -> Unit,
    onOpenMe: () -> Unit,
    onOpenRouting: () -> Unit,
    onOpenPerAppBypass: () -> Unit,
    onOpenSupport: () -> Unit,
    onLanguageChange: (String) -> Unit,
    onThemeModeChange: (String) -> Unit,
    /** v1.1.30: share the app (referral link) via the share sheet. */
    onShareApp: () -> Unit = {},
    /** v1.1.30: open the server list («اپ‌های تحریم‌شده» row). */
    onOpenServerSelect: () -> Unit = {},
) {
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showAppearanceDialog by remember { mutableStateOf(false) }
    val languageNames = stringArrayResource(R.array.language_select)
    val languageValues = stringArrayResource(R.array.language_select_value)
    val languageName = when (state.languageCode) {
        "auto" -> stringResource(R.string.vpn5g_drawer_appearance_system)
        else -> languageValues.indexOf(state.languageCode)
            .takeIf { it >= 0 }?.let { languageNames[it] } ?: state.languageCode
    }
    val appearanceName = when (state.themeMode) {
        "1" -> stringResource(R.string.vpn5g_drawer_appearance_light)
        "2" -> stringResource(R.string.vpn5g_drawer_appearance_dark)
        else -> stringResource(R.string.vpn5g_drawer_appearance_system)
    }
    val splitActive = Vpn5gRouting.isSplitTunnelActive(state.routingMode, state.directFirst)
    val routingName = stringResource(
        if (state.routingMode == Vpn5gRoutingMode.FULL) R.string.vpn5g_routing_full
        else R.string.vpn5g_routing_smart
    )

    // v1.1.30 (Aref's order): premium users get a visibly different,
    // more chic drawer — gold gradient header + premium badge; free
    // users keep the plain header.
    val isPremium = hasActiveSubscription(state)
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .background(Vpn5gBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Header: shield mark + wordmark + account line. Premium
        // (v1.1.30): gold gradient card + «پریمیوم فعال» badge.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = if (isPremium) {
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Vpn5gGold.copy(alpha = 0.35f),
                                Vpn5gGold.copy(alpha = 0.08f),
                            )
                        )
                    )
                    .border(1.dp, Vpn5gGold, RoundedCornerShape(16.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            } else {
                Modifier.fillMaxWidth()
            },
        ) {
            Vpn5gShieldLogo(width = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Vpn5gBrandText(fontSize = 20.sp, textAlign = TextAlign.Start)
                Text(
                    text = state.signedInEmail
                        ?: stringResource(R.string.vpn5g_drawer_login),
                    color = Vpn5gOnDarkMuted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isPremium) {
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(Vpn5gGold)
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.vpn5g_drawer_premium_badge),
                            color = Color.Black,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        // Pro upgrade (teal pill with crown, Lantern style).
        // v1.1.22 (fix #3): while any subscription is active the pill
        // becomes the subscription details; when it ends, the upsell
        // returns.
        if (hasActiveSubscription(state)) {
            Vpn5gSubscriptionDetailsPill(
                state = state,
                onOpenPremium = onOpenPremium,
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50.dp))
                    .background(Vpn5gTeal)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.vpn5g_pro_upgrade_button),
                    ) { onOpenPremium() }
                    .padding(vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Vpn5gCrownIcon(color = Color.White, size = 20.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.vpn5g_pro_upgrade_button),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // Account: login, or the signed-in email -> Me.
        Vpn5gGlassCard(modifier = Modifier.fillMaxWidth()) {
            if (state.signedInEmail.isNullOrBlank()) {
                Vpn5gDrawerRow(
                    title = stringResource(R.string.vpn5g_drawer_login),
                    subtitle = stringResource(R.string.vpn5g_auth_title),
                    onClick = onOpenAuth,
                ) { Vpn5gPersonIcon(color = Vpn5gTeal, size = 22.dp) }
            } else {
                Vpn5gDrawerRow(
                    title = state.signedInEmail,
                    subtitle = stringResource(R.string.vpn5g_profile_account),
                    onClick = onOpenMe,
                ) { Vpn5gPersonIcon(color = Vpn5gTeal, size = 22.dp) }
            }
        }

        // VPN settings.
        Text(
            text = stringResource(R.string.vpn5g_drawer_vpn_settings),
            color = Vpn5gOnDarkMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        )
        Vpn5gGlassCard(modifier = Modifier.fillMaxWidth()) {
            Vpn5gDrawerRow(
                title = stringResource(R.string.vpn5g_routing_title),
                subtitle = routingName,
                onClick = onOpenRouting,
            ) { Vpn5gRoutesIcon(color = Vpn5gTeal, size = 22.dp) }
            Vpn5gDrawerDivider()
            Vpn5gDrawerRow(
                title = stringResource(R.string.vpn5g_split_tunnel_title),
                subtitle = stringResource(
                    if (splitActive) R.string.vpn5g_split_on else R.string.vpn5g_split_off
                ),
                onClick = onOpenPerAppBypass,
            ) { Vpn5gAppsIcon(color = Vpn5gTeal, size = 22.dp) }
            Vpn5gDrawerDivider()
            Vpn5gDrawerRow(
                title = stringResource(R.string.vpn5g_drawer_usage),
                subtitle = stringResource(R.string.vpn5g_subscription_title),
                onClick = onOpenMe,
            ) { Vpn5gBoltIcon(color = Vpn5gTeal, size = 22.dp) }
            Vpn5gDrawerDivider()
            // v1.1.30: share-app referral (1 GB gift for both sides).
            Vpn5gDrawerRow(
                title = stringResource(R.string.vpn5g_drawer_share_app),
                subtitle = stringResource(R.string.vpn5g_drawer_share_subtitle),
                onClick = onShareApp,
            ) { Vpn5gGiftIcon(color = Vpn5gGold, size = 22.dp) }
            Vpn5gDrawerDivider()
            // v1.1.30: sanctioned-apps servers (Google Flow, Gemini…).
            Vpn5gDrawerRow(
                title = stringResource(R.string.vpn5g_drawer_sanctioned),
                subtitle = stringResource(R.string.vpn5g_drawer_sanctioned_subtitle),
                onClick = onOpenServerSelect,
            ) { Vpn5gGlobeIcon(color = Vpn5gTeal, size = 22.dp) }
            Vpn5gDrawerDivider()
            Vpn5gDrawerRow(
                title = stringResource(R.string.vpn5g_drawer_language),
                subtitle = languageName,
                onClick = { showLanguageDialog = true },
            ) { Vpn5gGlobeIcon(color = Vpn5gTeal, size = 22.dp) }
            Vpn5gDrawerDivider()
            Vpn5gDrawerRow(
                title = stringResource(R.string.vpn5g_drawer_appearance),
                subtitle = appearanceName,
                onClick = { showAppearanceDialog = true },
            ) { Vpn5gHelpIcon(color = Vpn5gTeal, size = 22.dp) }
        }

        // Support + about.
        Vpn5gGlassCard(modifier = Modifier.fillMaxWidth()) {
            Vpn5gDrawerRow(
                title = stringResource(R.string.vpn5g_drawer_support),
                subtitle = stringResource(R.string.vpn5g_link_support),
                onClick = onOpenSupport,
            ) { Vpn5gHelpIcon(color = Vpn5gTeal, size = 22.dp) }
            Vpn5gDrawerDivider()
            Vpn5gDrawerRow(
                title = stringResource(R.string.vpn5g_drawer_about),
                subtitle = stringResource(R.string.vpn5g_footer_made_by),
                onClick = onOpenMe,
            ) { Vpn5gHomeIcon(color = Vpn5gTeal, size = 22.dp) }
        }
        Vpn5gFooter()
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.vpn5g_drawer_language)) },
            text = {
                Column {
                    languageValues.forEachIndexed { i, code ->
                        val label = if (code == "auto") {
                            stringResource(R.string.vpn5g_drawer_appearance_system)
                        } else {
                            languageNames[i]
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(role = Role.Button, onClickLabel = label) {
                                    onLanguageChange(code)
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 6.dp),
                        ) {
                            RadioButton(
                                // Display-only: the row is the one
                                // activation node for this choice.
                                selected = state.languageCode == code,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Vpn5gTeal,
                                ),
                            )
                            Text(text = label, color = Vpn5gOnDark, fontSize = 15.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.vpn5g_close), color = Vpn5gTealLight)
                }
            },
        )
    }

    if (showAppearanceDialog) {
        val modes = listOf(
            "0" to R.string.vpn5g_drawer_appearance_system,
            "2" to R.string.vpn5g_drawer_appearance_dark,
            "1" to R.string.vpn5g_drawer_appearance_light,
        )
        AlertDialog(
            onDismissRequest = { showAppearanceDialog = false },
            title = { Text(stringResource(R.string.vpn5g_drawer_appearance)) },
            text = {
                Column {
                    modes.forEach { (mode, labelRes) ->
                        val label = stringResource(labelRes)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(role = Role.Button, onClickLabel = label) {
                                    onThemeModeChange(mode)
                                    showAppearanceDialog = false
                                }
                                .padding(vertical = 6.dp),
                        ) {
                            RadioButton(
                                // Display-only: the row is the one
                                // activation node for this choice.
                                selected = state.themeMode == mode,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Vpn5gTeal,
                                ),
                            )
                            Text(text = label, color = Vpn5gOnDark, fontSize = 15.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAppearanceDialog = false }) {
                    Text(stringResource(R.string.vpn5g_close), color = Vpn5gTealLight)
                }
            },
        )
    }
}

@Composable
private fun Vpn5gDrawerRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClickLabel = title) { onClick() }
            .padding(vertical = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Vpn5gOnDark.copy(alpha = 0.08f)),
        ) {
            leading()
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Vpn5gOnDark,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = Vpn5gOnDarkMuted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Vpn5gChevronIcon(color = Vpn5gOnDarkMuted)
    }
}

@Composable
private fun Vpn5gDrawerDivider() {
    HorizontalDivider(
        color = Vpn5gCardBorder,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

/**
 * Premium lock notice (v1.1.30, Aref's order — most features are
 * premium-only): shown in place of an advanced feature for free
 * users — lock icon, «این امکان ویژه کاربران پریمیوم است» and a
 * gold upgrade button to Premium. Shared by the routing screen
 * (FULL mode) and the sanctioned-apps tab.
 */
@Composable
internal fun Vpn5gPremiumLockNotice(onOpenPremium: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Vpn5gGold.copy(alpha = 0.12f))
            .border(1.dp, Vpn5gGold.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Vpn5gLockIcon(color = Vpn5gGold, size = 28.dp)
        Text(
            text = stringResource(R.string.vpn5g_premium_feature_locked),
            color = Vpn5gOnDark,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50.dp))
                .background(Vpn5gGold)
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.vpn5g_premium_unlock_button),
                ) { onOpenPremium() }
                .padding(vertical = 12.dp),
        ) {
            Text(
                text = stringResource(R.string.vpn5g_premium_unlock_button),
                color = Color.Black,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ------------------------------------------------------- routing screen

/**
 * Routing-mode screen (v1.1.21, Lantern routing page): smart vs full
 * tunnel, the direct-first switch, and the explanation card. The
 * choices map onto the ONE real routing lever — the upstream per-app
 * proxy — via [Vpn5gRouting]; nothing here invents a routing rule.
 * A change applies at the next connect.
 */
@Composable
fun Vpn5gRoutingScreen(
    state: Vpn5gHomeUiState,
    onBack: () -> Unit,
    onModeChange: (Vpn5gRoutingMode) -> Unit,
    onDirectFirstChange: (Boolean) -> Unit,
    onOpenPerAppBypass: () -> Unit,
    /** v1.1.30: premium gate — FULL routing is premium-only. */
    onOpenPremium: () -> Unit = {},
) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val smart = state.routingMode == Vpn5gRoutingMode.SMART
    // v1.1.30 (Aref's order — most features premium-only): the FULL
    // tunnel mode is an advanced feature. Free users keep SMART (the
    // default) and can always switch back to it; picking FULL shows
    // the premium lock instead of switching. Per-app selection stays
    // free (the 1.1.29 Instagram+Telegram default depends on it).
    val isPremium = hasActiveSubscription(state)
    var fullLockShown by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // v1.1.29 (Aref's order): visible back button at the top.
        Vpn5gBackRow(onBack)
        Vpn5gStaggeredItem(index = 0, visible = entered) {
            Text(
                text = stringResource(R.string.vpn5g_routing_title),
                color = Vpn5gOnDark,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Vpn5gStaggeredItem(index = 1, visible = entered) {
            Vpn5gRoutingModeCard(
                title = stringResource(R.string.vpn5g_routing_smart),
                body = stringResource(R.string.vpn5g_routing_smart_desc),
                selected = smart,
                onClick = { onModeChange(Vpn5gRoutingMode.SMART) },
            )
        }
        Vpn5gStaggeredItem(index = 2, visible = entered) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Vpn5gRoutingModeCard(
                    title = stringResource(R.string.vpn5g_routing_full),
                    body = stringResource(R.string.vpn5g_routing_full_desc),
                    selected = !smart,
                    onClick = {
                        if (isPremium) {
                            onModeChange(Vpn5gRoutingMode.FULL)
                        } else {
                            fullLockShown = true
                        }
                    },
                )
                if (fullLockShown && !isPremium) {
                    Vpn5gPremiumLockNotice(onOpenPremium = onOpenPremium)
                }
            }
        }
        Vpn5gStaggeredItem(index = 3, visible = entered) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Vpn5gCard)
                    .border(1.dp, Vpn5gCardBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.vpn5g_routing_smart_options),
                    color = Vpn5gOnDark,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.vpn5g_routing_direct_first),
                            color = if (smart) Vpn5gOnDark else Vpn5gOnDarkMuted,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.vpn5g_routing_direct_first_desc),
                            color = Vpn5gOnDarkMuted,
                            fontSize = 13.sp,
                        )
                    }
                    Switch(
                        checked = state.directFirst,
                        onCheckedChange = { onDirectFirstChange(it) },
                        enabled = smart,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Vpn5gGreen,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Vpn5gOnDarkMuted,
                        ),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Vpn5gTealButton(
                    text = stringResource(R.string.vpn5g_per_app_open),
                    onClick = onOpenPerAppBypass,
                )
            }
        }
        Vpn5gStaggeredItem(index = 4, visible = entered) {
            Vpn5gGlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.vpn5g_routing_info),
                    color = Vpn5gOnDarkMuted,
                    fontSize = 14.sp,
                )
            }
        }
        Vpn5gStaggeredItem(index = 5, visible = entered) {
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.vpn5g_nav_home),
                    color = Vpn5gTealLight,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun Vpn5gRoutingModeCard(
    title: String,
    body: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Vpn5gCard)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Vpn5gTeal else Vpn5gCardBorder,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(role = Role.Button, onClickLabel = title) { onClick() }
            .padding(16.dp),
    ) {
        RadioButton(
            // Display-only: the row itself is the one activation node
            // (ui/AGENTS.md) — a second clickable here would be a
            // duplicate focus target for the same action.
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = Vpn5gTeal),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Vpn5gOnDark,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = body,
                color = Vpn5gOnDarkMuted,
                fontSize = 13.sp,
            )
        }
    }
}

// ------------------------------------------------------- features screen

/**
 * Features page (v1.1.21, Lantern features page): OUR real features
 * only — smart routing, advanced protocols, private servers, auto
 * connect, the 30-minute free session, gift codes. No personal-proxy
 * invite (we do not have one) and no invented capability.
 */
@Composable
fun Vpn5gFeaturesScreen(
    onBack: () -> Unit,
    /** v1.1.22 (fix #3): the bottom card swaps to the subscription
     * details while a subscription is active. */
    state: Vpn5gHomeUiState = Vpn5gHomeUiState(),
    /** v1.1.21: the Pro card at the bottom opens Premium. */
    onOpenPremium: () -> Unit,
) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    // The icon slot is a composable lambda, so the list needs its
    // element type spelled out — Triple's inference would otherwise
    // store the lambdas as plain () -> Unit and the feature rows
    // could not call them.
    val features: List<Triple<Int, Int, @Composable () -> Unit>> = listOf(
        Triple(
            R.string.vpn5g_feature_smart_title,
            R.string.vpn5g_feature_smart_body,
        ) { Vpn5gRoutesIcon(color = Color.White, size = 24.dp) },
        Triple(
            R.string.vpn5g_feature_protocols_title,
            R.string.vpn5g_feature_protocols_body,
        ) { Vpn5gGlobeIcon(color = Color.White, size = 24.dp) },
        Triple(
            R.string.vpn5g_feature_servers_title,
            R.string.vpn5g_feature_servers_body,
        ) { Vpn5gBoltIcon(color = Color.White, size = 24.dp) },
        Triple(
            R.string.vpn5g_feature_auto_title,
            R.string.vpn5g_feature_auto_body,
        ) { Vpn5gPowerIcon(color = Color.White, size = 24.dp) },
        Triple(
            R.string.vpn5g_feature_free_title,
            R.string.vpn5g_feature_free_body,
        ) { Vpn5gCheckIcon(color = Color.White, size = 24.dp) },
        Triple(
            R.string.vpn5g_feature_gift_title,
            R.string.vpn5g_feature_gift_body,
        ) { Vpn5gGiftIcon(color = Color.White, size = 24.dp) },
    )

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // v1.1.29 (Aref's order): visible back button at the top.
        Vpn5gBackRow(onBack)
        Vpn5gStaggeredItem(index = 0, visible = entered) {
            Text(
                text = stringResource(R.string.vpn5g_features_title),
                color = Vpn5gOnDark,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Vpn5gStaggeredItem(index = 1, visible = entered) {
            Text(
                text = stringResource(R.string.vpn5g_features_headline),
                color = Vpn5gOnDark,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        features.forEachIndexed { i, (titleRes, bodyRes, icon) ->
            Vpn5gStaggeredItem(index = minOf(i + 2, 5), visible = entered) {
                Vpn5gFeatureRow(
                    title = stringResource(titleRes),
                    body = stringResource(bodyRes),
                    icon = icon,
                )
            }
        }
        Vpn5gStaggeredItem(index = 5, visible = entered) {
            // v1.1.22 (fix #3): same upsell → details swap as home.
            if (hasActiveSubscription(state)) {
                Vpn5gSubscriptionDetailsCard(
                    state = state,
                    onOpenPremium = onOpenPremium,
                )
            } else {
                Vpn5gProUpgradeCard(onOpenPremium = onOpenPremium)
            }
        }
        Vpn5gStaggeredItem(index = 5, visible = entered) {
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.vpn5g_nav_home),
                    color = Vpn5gTealLight,
                    fontSize = 14.sp,
                )
            }
        }
    }
}
