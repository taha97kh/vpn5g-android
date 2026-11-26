package com.v2ray.ang.ui.vpn5g

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
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
import com.v2ray.ang.extension.toSpeedString
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.BOT_URL
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.CHANNEL_URL
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.SUPPORT_BOT_URL
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.SUPPORT_URL
import com.v2ray.ang.handler.vpn5g.Vpn5gPlans
import com.v2ray.ang.handler.vpn5g.Vpn5gSubscriptionStatus
import com.v2ray.ang.handler.vpn5g.Vpn5gVolume

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
    // §4). v1.1.15: all sections start CLOSED — the user opens them.
    var accountExpanded by remember { mutableStateOf(false) }
    var subscriptionExpanded by remember { mutableStateOf(false) }
    var statsExpanded by remember { mutableStateOf(false) }
    var perAppExpanded by remember { mutableStateOf(false) }
    var aboutExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // v1.1.29 (Aref's order): visible back button at the top.
        Vpn5gBackRow(onBack)
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
                            containerColor = Vpn5gTeal,
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
                            color = Vpn5gTealLight,
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
                    // v1.1.30 (Aref's order): Persian plan name,
                    // never the raw id ("sub_12" is never shown).
                    val planName = Vpn5gPlans.displayNameForPlanId(
                        ownSubscription.plan,
                        state.plans,
                    )
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
                    // v1.1.18 (Model B): used / remaining volume for
                    // this grant, measured on-device.
                    Vpn5gVolumeBlock(state)
                } else if (ownSubscription?.status == "active") {
                    Text(
                        text = stringResource(R.string.vpn5g_subscription_expired),
                        color = Vpn5gOnDark,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    // v1.1.18 (Model B): when the volume cap ended the
                    // grant, say so — "expired" alone would hide the
                    // real reason.
                    if (Vpn5gSubscriptionStatus.isVolumeExhausted(
                            ownSubscription, state.volumeState()
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.vpn5g_volume_finished),
                            color = Vpn5gOnDark,
                            fontSize = 14.sp,
                        )
                        Vpn5gVolumeBlock(state)
                    } else {
                        Text(
                            text = stringResource(R.string.vpn5g_subscription_none_body),
                            color = Vpn5gOnDarkMuted,
                            fontSize = 14.sp,
                        )
                    }
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
                            color = Vpn5gTealLight,
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

        // Usage stats (v1.1.30, Aref's order): clean stat cards —
        // this session's measured usage, the grant's total measured
        // usage, remaining GB (or «نامحدود» when the plan has no
        // cap), remaining time, live speeds and the real ping.
        // Every number is measured on-device or read from the
        // server truth; nothing is invented.
        Vpn5gStaggeredItem(index = 4, visible = entered) {
            Vpn5gExpandableSection(
                title = stringResource(R.string.vpn5g_stats_title),
                expanded = statsExpanded,
                onToggleExpand = { statsExpanded = !statsExpanded },
            ) {
                Vpn5gUsageStatsCards(state)
            }
        }

        // Per-app selection (v1.1.14, default changed v1.1.29): the
        // mechanism already exists upstream (PerAppProxyActivity +
        // CoreVpnService.configurePerAppProxy, applied at service
        // setup and taking effect on the next connect) — it was only
        // reachable through the hidden upstream MainActivity, so it
        // is surfaced here as its own section. The selection persists
        // in the upstream settings. v1.1.29 default (Aref's order):
        // Instagram + Telegram use the VPN (allowlist seed in
        // Vpn5gRouting.seedPerAppDefaultsIfNeeded); the picker still
        // offers both directions.
        Vpn5gStaggeredItem(index = 5, visible = entered) {
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
                        containerColor = Vpn5gTeal,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.vpn5g_per_app_open), fontSize = 16.sp)
                }
            }
        }

        // About («درباره برنامه», v1.1.30, Aref's order): filled with
        // v1.1.32 (Aref's order «درباره برنامه هم بره زیر دکمه برنامه
        // های فیلترشکن»): this section sits directly BELOW the
        // per-app («برنامه‌های فیلترشکن») section above; the link
        // buttons follow it.
        // real descriptions — what the app does, speed, ping, the
        // creator, security. Persian text in resources (fa/en/az).
        Vpn5gStaggeredItem(index = 6, visible = entered) {
            Vpn5gExpandableSection(
                title = stringResource(R.string.vpn5g_about_title),
                expanded = aboutExpanded,
                onToggleExpand = { aboutExpanded = !aboutExpanded },
            ) {
                Text(
                    text = stringResource(R.string.vpn5g_about_desc),
                    color = Vpn5gOnDarkMuted,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.vpn5g_about_speed_title),
                    color = Vpn5gOnDark,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.vpn5g_about_speed_body),
                    color = Vpn5gOnDarkMuted,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.vpn5g_about_ping_title),
                    color = Vpn5gOnDark,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.vpn5g_about_ping_body),
                    color = Vpn5gOnDarkMuted,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.vpn5g_about_security_title),
                    color = Vpn5gOnDark,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.vpn5g_about_security_body),
                    color = Vpn5gOnDarkMuted,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.vpn5g_about_creator_title),
                    color = Vpn5gOnDark,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.vpn5g_about_creator_body),
                    color = Vpn5gOnDarkMuted,
                    fontSize = 14.sp,
                )
            }
        }
        // About links (v1.1.5 §6, labels renamed 2026-11-23 by Aref's
        // order): exactly three buttons, exactly these names, in this
        // order — no extra text, address or description under them.
        // The version lines below are app info, not link descriptions.
        Vpn5gStaggeredItem(index = 7, visible = entered) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(8.dp))
                // Channel / Bot / Support as neomorphism buttons (v1.1.2).
                NeuLinkButton(
                    label = stringResource(R.string.vpn5g_link_channel),
                    onClick = { onOpenUrl(CHANNEL_URL) },
                ) { Vpn5gChannelIcon(color = Vpn5gTeal, size = 22.dp) }
                Spacer(Modifier.height(12.dp))
                NeuLinkButton(
                    label = stringResource(R.string.vpn5g_link_bot),
                    onClick = { onOpenUrl(BOT_URL) },
                ) { Vpn5gBotIcon(color = Vpn5gTeal, size = 22.dp) }
                Spacer(Modifier.height(12.dp))
                NeuLinkButton(
                    // v1.1.29 (Aref's order): «پشتیبانی آنلاین» is his
                    // own account (button below); the bot entry stays
                    // as «پشتیبانی ربات» so the two are distinct.
                    label = stringResource(R.string.vpn5g_link_support_bot),
                    // v1.1.14: support goes DIRECTLY to the bot's support
                    // section (start=support). Bot membership cannot be
                    // verified app-side — the bot handles it.
                    onClick = { onOpenUrl(SUPPORT_BOT_URL) },
                ) { Vpn5gSupportIcon(color = Vpn5gTeal, size = 22.dp) }
                Spacer(Modifier.height(12.dp))
                // v1.1.28 (Aref's order): his personal Telegram
                // account next to the bot support entry — "هم" (also),
                // the bot entry above stays. v1.1.29: label is plain
                // «پشتیبانی آنلاین» (no @handle on the button).
                NeuLinkButton(
                    label = stringResource(R.string.vpn5g_link_support_telegram),
                    onClick = { onOpenUrl(SUPPORT_URL) },
                ) { Vpn5gSupportIcon(color = Vpn5gTeal, size = 22.dp) }
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
        Vpn5gStaggeredItem(index = 8, visible = entered) {
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.vpn5g_nav_home),
                    color = Vpn5gTealLight,
                    fontSize = 14.sp,
                )
            }
        }
        Vpn5gStaggeredItem(index = 9, visible = entered) {
            Vpn5gFooter()
        }
    }
}

/**
 * Usage-stats cards (v1.1.30, Aref's order): a clean 2-column grid
 * of stat cards — this session's measured usage, the grant's total
 * measured usage, remaining GB (or «نامحدود» when the plan has no
 * cap), remaining time (days for subscribers, hours for the gift
 * tier, minutes for the free 30-minute session), live download /
 * upload speeds and the real ping. Every value is measured
 * on-device or read from the server truth; nothing is invented —
 * an unknown value shows «—».
 */
@Composable
private fun Vpn5gUsageStatsCards(state: Vpn5gHomeUiState) {
    val subscription = state.userSubscription
    val capBytes = subscription?.volumeCapBytes
    val sessionValue = if (!state.isRunning && state.sessionConsumedBytes <= 0L) {
        stringResource(R.string.vpn5g_stats_no_session)
    } else {
        stringResource(
            R.string.vpn5g_stats_gb_value,
            Vpn5gVolume.formatGb(state.sessionConsumedBytes),
        )
    }
    val totalValue = stringResource(
        R.string.vpn5g_stats_gb_value,
        Vpn5gVolume.formatGb(state.volumeConsumedBytes),
    )
    val remainingGbValue = if (capBytes == null) {
        stringResource(R.string.vpn5g_stats_unlimited)
    } else {
        stringResource(
            R.string.vpn5g_stats_gb_value,
            Vpn5gVolume.formatGb(
                Vpn5gVolume.remainingBytes(state.volumeConsumedBytes, capBytes) ?: 0L
            ),
        )
    }
    val remainingTimeValue = when {
        state.isSubscriber && subscription != null -> stringResource(
            R.string.vpn5g_stats_days,
            Vpn5gSubscriptionStatus.remainingDays(
                subscription,
                System.currentTimeMillis() / 1000,
            ),
        )
        state.isGift -> stringResource(
            R.string.vpn5g_stats_hours,
            state.giftRemainingHours,
        )
        state.isRunning && state.remainingMillis != null -> stringResource(
            R.string.vpn5g_stats_minutes,
            (state.remainingMillis / 60_000L).coerceAtLeast(0L),
        )
        else -> "—"
    }
    val downloadValue = if (state.isRunning && state.downloadSpeedBps > 0L) {
        state.downloadSpeedBps.toSpeedString()
    } else {
        "—"
    }
    val uploadValue = if (state.isRunning && state.uploadSpeedBps > 0L) {
        state.uploadSpeedBps.toSpeedString()
    } else {
        "—"
    }
    val pingValue = if (state.currentHasRealPing && state.currentPingMillis > 0L) {
        "${state.currentPingMillis} ms"
    } else {
        "—"
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Vpn5gStatCard(
                label = stringResource(R.string.vpn5g_stats_session),
                value = sessionValue,
                modifier = Modifier.weight(1f),
            )
            Vpn5gStatCard(
                label = stringResource(R.string.vpn5g_stats_total),
                value = totalValue,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Vpn5gStatCard(
                label = stringResource(R.string.vpn5g_stats_remaining_gb),
                value = remainingGbValue,
                modifier = Modifier.weight(1f),
            )
            Vpn5gStatCard(
                label = stringResource(R.string.vpn5g_stats_remaining_time),
                value = remainingTimeValue,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Vpn5gStatCard(
                label = stringResource(R.string.vpn5g_speed_download),
                value = downloadValue,
                modifier = Modifier.weight(1f),
            )
            Vpn5gStatCard(
                label = stringResource(R.string.vpn5g_speed_upload),
                value = uploadValue,
                modifier = Modifier.weight(1f),
            )
        }
        Vpn5gStatCard(
            label = stringResource(R.string.vpn5g_stats_ping),
            value = pingValue,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** One stat card: small muted label over a bold value (v1.1.30). */
@Composable
private fun Vpn5gStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Vpn5gCard)
            .border(1.dp, Vpn5gOnDarkMuted.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            color = Vpn5gOnDarkMuted,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            color = Vpn5gOnDark,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Neomorphism button (v1.1.2): soft-extruded look on the dark theme —
 * a rounded surface with a light-to-dark diagonal gradient, a light
 * top-left edge and a dark bottom-right edge, plus a soft drop shadow.
 * v1.1.32 (Aref's order): each link button carries its own dedicated
 * icon (channel/bot/support) next to the label.
 */
@Composable
private fun NeuLinkButton(
    label: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit = {},
) {
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                color = Vpn5gOnDark,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
