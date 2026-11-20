package com.v2ray.ang.ui.vpn5g

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.v2ray.ang.R

/** VPN 5G dark palette. */
val Vpn5gNavyTop = Color(0xFF0F172A)
val Vpn5gNavyBottom = Color(0xFF1E293B)
val Vpn5gAccent = Color(0xFF2563EB)
val Vpn5gAccentLight = Color(0xFF3B82F6)
val Vpn5gOnDark = Color(0xFFF1F5F9)
val Vpn5gOnDarkMuted = Color(0xFF94A3B8)

/**
 * Animated gradient background (dark navy with a slowly shifting blue tint).
 *
 * Purely presentational: two [animateFloat] progress values on an infinite
 * transition drive the middle/bottom gradient stops through [lerp].
 * Render-thread driven, no bitmap animation, no new dependencies. Never
 * gates sync or connect work.
 *
 * NOTE: this project ships a Compose BOM with no Color animation support
 * (no `animateColor`, no `Color.VectorConverter`), so colors are driven by
 * float progress + `lerp` instead. Do not reintroduce `animateValue` with a
 * `Color` converter here.
 */
@Composable
fun Vpn5gAnimatedBackground(content: @Composable BoxScope.() -> Unit) {
    val transition = rememberInfiniteTransition(label = "vpn5g_gradient")
    val midProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "gradient_mid",
    )
    val mid = lerp(Vpn5gNavyBottom, Color(0xFF172554), midProgress)
    val tintProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "gradient_tint",
    )
    val tint = lerp(Color(0xFF1D4ED8), Vpn5gAccent, tintProgress)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Vpn5gNavyTop,
                        mid,
                        tint.copy(alpha = 0.35f),
                    )
                )
            )
    ) {
        content()
    }
}

/**
 * Floating/pulsing logo: gentle infinite scale (1.0 <-> 1.06) and vertical
 * drift on a 2s cycle. Subtle by design; purely presentational.
 */
@Composable
fun Vpn5gPulsingLogo(size: Dp, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "vpn5g_logo")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "logo_scale",
    )
    val offsetY by transition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "logo_offset",
    )
    Image(
        painter = painterResource(R.mipmap.ic_launcher),
        contentDescription = stringResource(R.string.app_name),
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = offsetY
            },
    )
}

/**
 * One element of a staggered springy entry: fades + slides in with a spring
 * (dampingRatio ~0.7) while the fade is delayed ~100ms per element index, so
 * logo, fields and buttons animate in one after another.
 */
@Composable
fun Vpn5gStaggeredItem(
    index: Int,
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = 400,
                delayMillis = index * 100,
            )
        ) + slideInVertically(
            animationSpec = spring(
                dampingRatio = 0.7f,
                stiffness = Spring.StiffnessMediumLow,
            ),
            initialOffsetY = { it / 2 },
        ),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        content()
    }
}
