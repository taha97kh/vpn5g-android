package com.v2ray.ang.ui.vpn5g

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.handler.vpn5g.Vpn5gSubscriptionStatus

/**
 * Lantern-style shared components (v1.1.21 restyle): charcoal cards,
 * teal primary pill buttons, gold Pro accents. The look comes from
 * Aref's Lantern reference; the content is always TAHA VPN 5G's own —
 * no Lantern name, no invented feature, no invented city.
 */

/** Full-width teal pill button (the Lantern primary action). */
@Composable
fun Vpn5gTealButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50.dp))
            .background(Vpn5gTeal)
            .clickable(role = Role.Button, onClickLabel = text) { onClick() }
            .padding(vertical = 13.dp),
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Gold-bordered Pro upgrade card (v1.1.21): the Lantern "upgrade" card
 * on top of the home and server-select screens. Opens OUR Premium
 * screen — plans, codes and the subscription itself never leave it.
 */
@Composable
fun Vpn5gProUpgradeCard(onOpenPremium: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Vpn5gCard)
            .border(1.dp, Vpn5gGold.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.vpn5g_pro_card_body),
            color = Vpn5gOnDark,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
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
}

/**
 * Whether any subscription is active (v1.1.22, fix #3): the single
 * gate for the upsell → details swap — the home card, the drawer
 * pill and the features page all use it, so the three can never
 * disagree. A row alone is not enough: an expired row still sits in
 * the state while isSubscriber/isGift are false.
 */
internal fun hasActiveSubscription(state: Vpn5gHomeUiState): Boolean {
    return state.isSubscriber || state.isGift
}

/**
 * Plan display name for the active subscription (v1.1.22): the
 * published plan's Persian name, falling back to the plan id, or null
 * when there is no subscription row at all. Shared by the details
 * card and the drawer pill — the same name the status section shows.
 */
internal fun subscriptionPlanDisplayName(state: Vpn5gHomeUiState): String? {
    val planId = state.userSubscription?.plan ?: return null
    return state.plans?.firstOrNull { it.id == planId }?.name_fa ?: planId
}

/**
 * Subscription details card (v1.1.22, fix #3): while ANY subscription
 * is active (gift or sub) the «ارتقا به نسخه پرو» upsell is replaced
 * by this card — «مشخصات اشتراک» with plan / expiry / volume —
 * tapping it opens Premium, where the full status lives. When the
 * subscription ends the caller shows the upsell again (gated on
 * isSubscriber/isGift, never on the row alone). Same gold border as
 * the upsell card it replaces.
 */
@Composable
fun Vpn5gSubscriptionDetailsCard(
    state: Vpn5gHomeUiState,
    onOpenPremium: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Vpn5gCard)
            .border(1.dp, Vpn5gGold.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.vpn5g_subscription_details_title),
            ) { onOpenPremium() }
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Vpn5gCrownIcon(color = Vpn5gGold, size = 20.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.vpn5g_subscription_details_title),
                color = Vpn5gOnDark,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(8.dp))
        subscriptionPlanDisplayName(state)?.let { plan ->
            Text(
                text = stringResource(R.string.vpn5g_subscription_plan, plan),
                color = Vpn5gOnDark,
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(4.dp))
        }
        state.userSubscription?.expiresAtEpochSeconds?.let { expiresAt ->
            Text(
                text = stringResource(
                    R.string.vpn5g_promo_redeemed_expiry,
                    Vpn5gSubscriptionStatus.formatExpiryDate(expiresAt),
                ),
                color = Vpn5gOnDark,
                fontSize = 15.sp,
            )
        }
        // Volume total + used with progress (v1.1.18 Model B) — the
        // same block the Premium status section shows.
        Vpn5gVolumeBlock(state)
    }
}

/**
 * Compact subscription details pill (v1.1.22, fix #3): the drawer's
 * teal «ارتقا به نسخه پرو» pill becomes this gold-bordered pill while
 * a subscription is active — title + plan and expiry on one line —
 * tapping it opens Premium.
 */
@Composable
fun Vpn5gSubscriptionDetailsPill(
    state: Vpn5gHomeUiState,
    onOpenPremium: () -> Unit,
) {
    val subtitle = buildString {
        subscriptionPlanDisplayName(state)?.let { append(it) }
        state.userSubscription?.expiresAtEpochSeconds?.let { expiresAt ->
            if (isNotEmpty()) append(" · ")
            append(
                Vpn5gSubscriptionStatus.formatExpiryDate(expiresAt),
            )
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50.dp))
            .background(Vpn5gCard)
            .border(1.dp, Vpn5gGold.copy(alpha = 0.8f), RoundedCornerShape(50.dp))
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.vpn5g_subscription_details_title),
            ) { onOpenPremium() }
            .padding(vertical = 12.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Vpn5gCrownIcon(color = Vpn5gGold, size = 20.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.vpn5g_subscription_details_title),
                color = Vpn5gOnDark,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        if (subtitle.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = Vpn5gOnDarkMuted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * One row of a Lantern status card: a leading icon, a bold title, a
 * muted subtitle and a chevron. Exactly one activation (the whole row),
 * so one semantic node.
 */
@Composable
fun Vpn5gStatusRow(
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
            .padding(vertical = 10.dp),
    ) {
        leading()
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

/**
 * Lantern server row: flag + country (never a city — no city data
 * exists in any source) + protocol and ping, a status dot, and a
 * chevron. Locked rows show the gold lock instead of the dot.
 */
@Composable
fun Vpn5gLanternServerRow(
    flag: String,
    name: String,
    protocol: String,
    pingText: String,
    locked: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Vpn5gOnDark.copy(alpha = 0.06f))
            .clickable(role = Role.Button, onClickLabel = name) { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(
            text = flag,
            fontSize = 22.sp,
            modifier = Modifier.padding(end = 12.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = Vpn5gOnDark,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = protocol + " · " + pingText,
                color = Vpn5gOnDarkMuted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (locked) {
            Vpn5gLockIcon(color = Vpn5gGold, size = 18.dp)
        } else {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Vpn5gGreen),
            )
        }
        Spacer(Modifier.width(10.dp))
        Vpn5gChevronIcon(color = Vpn5gOnDarkMuted)
    }
}

/**
 * One Lantern features row: a teal rounded-square icon, a bold title
 * and a muted body. The icon lambda draws in white on the teal tile.
 */
@Composable
fun Vpn5gFeatureRow(
    title: String,
    body: String,
    icon: @Composable () -> Unit,
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Vpn5gTeal),
        ) {
            icon()
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Vpn5gOnDark,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = body,
                color = Vpn5gOnDarkMuted,
                fontSize = 14.sp,
            )
        }
    }
}
