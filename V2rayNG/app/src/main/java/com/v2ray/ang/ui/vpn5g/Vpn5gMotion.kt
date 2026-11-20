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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R

/** VPN5G IRBOT dark palette — near-black X-VPN-style background, cyan accent. */
val Vpn5gNavyTop = Color(0xFF000000)
val Vpn5gNavyBottom = Color(0xFF0A0A0A)
val Vpn5gCyan = Color(0xFF00E5CC)
val Vpn5gBlackBar = Color(0xFF000000)
val Vpn5gAccent = Color(0xFF2563EB)
val Vpn5gAccentLight = Color(0xFF3B82F6)
val Vpn5gOnDark = Color(0xFFF1F5F9)
val Vpn5gOnDarkMuted = Color(0xFF94A3B8)

/**
 * Animated background (near-black with a slowly shifting dark-cyan tint).
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
    val mid = lerp(Vpn5gNavyBottom, Color(0xFF06231F), midProgress)
    val tintProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "gradient_tint",
    )
    val tint = lerp(Color(0xFF00897B), Vpn5gCyan, tintProgress)
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

/**
 * Small credit footer ("Made by AREF KH / 2026 - 1405"), shown at the
 * bottom of the home screen content and the Me/Profile screen. This is
 * the ONLY place a personal name appears in the app.
 */
@Composable
fun Vpn5gFooter() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.vpn5g_footer_made_by),
            color = Vpn5gOnDarkMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.vpn5g_footer_years),
            color = Vpn5gOnDarkMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Launch splash (v1.1.2): the full-screen branded image fills the screen
 * (centerCrop) with a "Loading..." indicator at the bottom. Shown for
 * exactly 3 seconds by the launcher activity, then replaced by the main
 * screen. Purely presentational; it never gates sync or connect work.
 */
@Composable
fun Vpn5gSplashScreen() {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.splash_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
        ) {
            CircularProgressIndicator(color = Vpn5gCyan, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.vpn5g_loading),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
