package com.v2ray.ang.ui.vpn5g

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Brand watermark for the primary buttons (v1.1.4 §9, refined in v1.1.5).
 *
 * The three house names — VPNCRACKNET, VPN5G_IRBOT, AREF_5G — drawn as a
 * background layer strictly BEHIND the button label: the composable is
 * always the first child of the button's content Box, it is clipped to
 * the button bounds so nothing can peek out past the label, it is never
 * rotated, and its opacity is ~0.02 — effectively invisible, felt rather
 * than read. The label layer fully covers it. Decorative only: the
 * semantics are cleared so TalkBack never reads it and it can never
 * become a focus target, and it has no click handling, so it cannot
 * interfere with touch. Keep the composable (do not delete it): the
 * marks stay as a background layer, only the visibility changes.
 */
@Composable
fun Vpn5gBrandWatermark(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .padding(vertical = 2.dp),
        ) {
            Text(
                text = "VPNCRACKNET",
                color = Vpn5gOnDark,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.alpha(0.02f),
            )
            Text(
                text = "VPN5G_IRBOT",
                color = Vpn5gOnDark,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.alpha(0.02f),
            )
            Text(
                text = "AREF_5G",
                color = Vpn5gOnDark,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.alpha(0.02f),
            )
        }
    }
}
