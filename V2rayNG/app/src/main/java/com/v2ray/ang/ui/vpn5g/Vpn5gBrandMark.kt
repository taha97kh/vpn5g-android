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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Subtle brand watermark for the primary buttons (v1.1.4 §9).
 *
 * The three house names — VPNCRACKNET, VPN5G_IRBOT, AREF_5G — drawn faint,
 * slightly rotated, stacked behind the button label. Decorative only:
 * the semantics are cleared so TalkBack never reads it and it can never
 * become a focus target, and it has no click handling, so it cannot
 * interfere with touch. Keep the alpha low: it must be felt, not read.
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
                .padding(vertical = 2.dp),
        ) {
            Text(
                text = "VPNCRACKNET",
                color = Vpn5gOnDark,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier
                    .alpha(0.08f)
                    .rotate(-6f),
            )
            Text(
                text = "VPN5G_IRBOT",
                color = Vpn5gOnDark,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier
                    .alpha(0.07f)
                    .rotate(3f),
            )
            Text(
                text = "AREF_5G",
                color = Vpn5gOnDark,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier
                    .alpha(0.08f)
                    .rotate(-2f),
            )
        }
    }
}
