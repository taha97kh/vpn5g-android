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

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .background(Vpn5gBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Header: shield mark + wordmark + account line.
        Row(verticalAlignment = Alignment.CenterVertically) {
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
            }
        }

        // Pro upgrade (teal pill with crown, Lantern style).
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
) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val smart = state.routingMode == Vpn5gRoutingMode.SMART

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
            Vpn5gRoutingModeCard(
                title = stringResource(R.string.vpn5g_routing_full),
                body = stringResource(R.string.vpn5g_routing_full_desc),
                selected = !smart,
                onClick = { onModeChange(Vpn5gRoutingMode.FULL) },
            )
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
            Vpn5gProUpgradeCard(onOpenPremium = onOpenPremium)
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
