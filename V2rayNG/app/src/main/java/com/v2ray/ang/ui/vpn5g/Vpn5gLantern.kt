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
