package com.v2ray.ang.ui.vpn5g

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.BOT_URL
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.CHANNEL_URL
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.SUPPORT_BOT_URL
import com.v2ray.ang.handler.vpn5g.Vpn5gSubscriptionStatus

/**
 * "Me" screen: account (email auth), subscription remaining, and about
 * (channel, bot, support, version). Advanced settings (protocol, routing,
 * upstream MainActivity) were removed in v1.1.4 — the engine is fully
 * automatic and manages protocol, routing, MTU and DNS itself.
 */
@Composable
fun Vpn5gProfileScreen(
    state: Vpn5gHomeUiState,
    onBack: () -> Unit,
    onOpenAuth: () -> Unit,
    onSignOut: () -> Unit,
    onOpenUrl: (String) -> Unit,
    /** Opens the per-app bypass screen (v1.1.14). */
    onOpenPerAppBypass: () -> Unit,
) {
    var entered by remember { mutableStateOf(false) }
    // Section expansion is transient presentation state only (v1.1.5
    // §4). Both sections start open, as before; the user can collapse
    // either one.
    var accountExpanded by remember { mutableStateOf(true) }
    var subscriptionExpanded by remember { mutableStateOf(true) }
    var perAppExpanded by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { entered = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Vpn5gStaggeredItem(index = 0, visible = entered) {
            Vpn5gPulsingLogo(size = 96.dp)
        }
        Vpn5gStaggeredItem(index = 1, visible = entered) {
            Text(
                text = stringResource(R.string.vpn5g_nav_me),
                color = Vpn5gOnDark,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        // Account (expandable, v1.1.5 §4).
        Vpn5gStaggeredItem(index = 2, visible = entered) {
            Vpn5gExpandableSection(
                title = stringResource(R.string.vpn5g_profile_account),
                expanded = accountExpanded,
                onToggleExpand = { accountExpanded = !accountExpanded },
            ) {
                if (state.signedInEmail.isNullOrBlank()) {
                    Text(
                        text = stringResource(R.string.vpn5g_profile_not_signed_in),
                        color = Vpn5gOnDarkMuted,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onOpenAuth,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Vpn5gAccent,
                            contentColor = Color.White,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.vpn5g_auth_title), fontSize = 16.sp)
                    }
                } else {
                    Text(
                        text = state.signedInEmail,
                        color = Vpn5gOnDark,
                        fontSize = 15.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.vpn5g_auth_signout),
                            color = Vpn5gAccentLight,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }

        // Subscription remaining (remote values only; never invented).
        // Expandable (v1.1.5 §4).
        Vpn5gStaggeredItem(index = 3, visible = entered) {
            Vpn5gExpandableSection(
                title = stringResource(R.string.vpn5g_subscription_title),
                expanded = subscriptionExpanded,
                onToggleExpand = { subscriptionExpanded = !subscriptionExpanded },
            ) {
                // v1.1.11 (refinement): a signed-in user's OWN
                // subscription row wins over the bot-published global
                // status here too — the Me screen must show the
                // remaining credit THEY have, not the global one.
                // (The global status is also what the app falls back
                // to while signed out, so it stays as the last branch.)
                val ownSubscription = state.userSubscription
                val status = state.subscriptionStatus
                if (ownSubscription != null && state.isSubscriber) {
                    // v1.1.12: premium look — gold pill + the remaining
                    // days prominent (bold, larger) for subscribers.
                    Vpn5gPremiumPill()
                    Spacer(Modifier.height(8.dp))
                    val planName = state.plans
                        ?.firstOrNull { it.id == ownSubscription.plan }?.name_fa
                        ?: ownSubscription.plan
                    planName?.let { plan ->
                        Text(
                            text = stringResource(R.string.vpn5g_subscription_plan, plan),
                            color = Vpn5gOnDark,
                            fontSize = 15.sp,
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.vpn5g_subscription_days,
                            Vpn5gSubscriptionStatus.remainingDays(
                                ownSubscription,
                                System.currentTimeMillis() / 1000,
                            ),
                        ),
                        color = Vpn5gOnDark,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                } else if (ownSubscription?.status == "active") {
                    Text(
                        text = stringResource(R.string.vpn5g_subscription_expired),
                        color = Vpn5gOnDark,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.vpn5g_subscription_none_body),
                        color = Vpn5gOnDarkMuted,
                        fontSize = 14.sp,
                    )
                } else if (status == null) {
                    Text(
                        text = stringResource(R.string.vpn5g_subscription_none),
                        color = Vpn5gOnDarkMuted,
                        fontSize = 14.sp,
                    )
                } else {
                    status.plan_name?.let { plan ->
                        Text(
                            text = stringResource(R.string.vpn5g_subscription_plan, plan),
                            color = Vpn5gOnDark,
                            fontSize = 15.sp,
                        )
                    }
                    status.remaining_days?.let { days ->
                        Text(
                            text = stringResource(R.string.vpn5g_subscription_days, days),
                            color = Vpn5gOnDark,
                            fontSize = 15.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { (days / 30f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = Vpn5gAccentLight,
                            trackColor = Vpn5gOnDarkMuted.copy(alpha = 0.3f),
                        )
                    }
                    status.remaining_volume_gb?.let { volume ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.vpn5g_subscription_volume, volume),
                            color = Vpn5gOnDark,
                            fontSize = 15.sp,
                        )
                    }
                }
            }
        }

        // Per-app bypass (v1.1.14): pick apps that must NOT use the
        // tunnel, so they open without filter (direct connection). The
        // mechanism already exists upstream (PerAppProxyActivity +
        // CoreVpnService.configurePerAppProxy, applied with
        // addDisallowedApplication at service setup and taking effect
        // on the next connect) — it was only reachable through the
        // hidden upstream MainActivity, so it is surfaced here as its
        // own section. The selection persists in the upstream settings.
        Vpn5gStaggeredItem(index = 4, visible = entered) {
            Vpn5gExpandableSection(
                title = stringResource(R.string.vpn5g_per_app_title),
                expanded = perAppExpanded,
                onToggleExpand = { perAppExpanded = !perAppExpanded },
            ) {
                Text(
                    text = stringResource(R.string.vpn5g_per_app_body),
                    color = Vpn5gOnDarkMuted,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onOpenPerAppBypass,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Vpn5gAccent,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.vpn5g_per_app_open), fontSize = 16.sp)
                }
            }
        }

        // About links (v1.1.5 §6, labels renamed 2026-11-23 by Aref's
        // order): exactly three buttons, exactly these names, in this
        // order — no extra text, address or description under them.
        // The version lines below are app info, not link descriptions.
        Vpn5gStaggeredItem(index = 5, visible = entered) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(8.dp))
                // Channel / Bot / Support as neomorphism buttons (v1.1.2).
                NeuLinkButton(
                    label = stringResource(R.string.vpn5g_link_channel),
                    onClick = { onOpenUrl(CHANNEL_URL) },
                )
                Spacer(Modifier.height(12.dp))
                NeuLinkButton(
                    label = stringResource(R.string.vpn5g_link_bot),
                    onClick = { onOpenUrl(BOT_URL) },
                )
                Spacer(Modifier.height(12.dp))
                NeuLinkButton(
                    label = stringResource(R.string.vpn5g_link_support),
                    // v1.1.14: support goes DIRECTLY to the bot's support
                    // section (start=support). Bot membership cannot be
                    // verified app-side — the bot handles it.
                    onClick = { onOpenUrl(SUPPORT_BOT_URL) },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.vpn5g_about_version,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE,
                    ),
                    color = Vpn5gOnDarkMuted,
                    fontSize = 14.sp,
                )
                Text(
                    text = stringResource(
                        R.string.vpn5g_about_build_date,
                        BuildConfig.BUILD_TIME,
                    ),
                    color = Vpn5gOnDarkMuted,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.vpn5g_about_core),
                    color = Vpn5gOnDarkMuted,
                    fontSize = 14.sp,
                )
            }
        }
        Vpn5gStaggeredItem(index = 6, visible = entered) {
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.vpn5g_nav_home),
                    color = Vpn5gAccentLight,
                    fontSize = 14.sp,
                )
            }
        }
        Vpn5gStaggeredItem(index = 7, visible = entered) {
            Vpn5gFooter()
        }
    }
}

/**
 * Neomorphism button (v1.1.2): soft-extruded look on the dark theme —
 * a rounded surface with a light-to-dark diagonal gradient, a light
 * top-left edge and a dark bottom-right edge, plus a soft drop shadow.
 */
@Composable
private fun NeuLinkButton(label: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = Color.Black.copy(alpha = 0.6f),
                spotColor = Color.Black.copy(alpha = 0.6f),
            )
            .clip(RoundedCornerShape(16.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF232323), Color(0xFF1A1A1A)),
                    start = Offset.Zero,
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                ),
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.14f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.35f),
                    ),
                    start = Offset.Zero,
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                ),
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(role = Role.Button, onClickLabel = label) { onClick() },
    ) {
        Text(
            text = label,
            color = Vpn5gOnDark,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
