package com.v2ray.ang.ui.vpn5g

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import kotlinx.coroutines.delay

/** VPN5G IRBOT dark palette — near-black X-VPN-style background, cyan accent. */
val Vpn5gNavyTop = Color(0xFF000000)
val Vpn5gNavyBottom = Color(0xFF0A0A0A)
val Vpn5gCyan = Color(0xFF00E5CC)
val Vpn5gBlackBar = Color(0xFF000000)
val Vpn5gAccent = Color(0xFF2563EB)
val Vpn5gAccentLight = Color(0xFF3B82F6)
/**
 * Electric blue (v1.1.15, luxury dark style): the light-effect accent
 * for the motion brand, the interstitial countdown and new surfaces.
 * The base palette above is untouched.
 */
val Vpn5gElectricBlue = Color(0xFF2E7DFF)
val Vpn5gOnDark = Color(0xFFF1F5F9)
val Vpn5gOnDarkMuted = Color(0xFF94A3B8)
/**
 * Premium gold (v1.1.12): the subscriber treatment — a restrained gold
 * pill and gold-tinted surfaces on the home and profile screens. Free
 * users never see it; the base palette above is untouched.
 */
val Vpn5gGold = Color(0xFFD4AF37)
val Vpn5gGoldOnDark = Color(0xFF1A1A1A)

/**
 * Small gold "premium" pill (v1.1.12): subscribers only. Display-only —
 * no click action. Used in the home header, the Premium screen's
 * status section and the Me screen's subscription section.
 */
@Composable
fun Vpn5gPremiumPill() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Vpn5gGold)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.vpn5g_premium_badge),
            color = Vpn5gGoldOnDark,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

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
 * Controlled glassmorphism card (v1.1.15, luxury dark style): a
 * translucent white gradient over the near-black background with a
 * hairline light border. Used by the interstitial ad and the
 * announcement card — the same surface everywhere, never a solid
 * block. Purely presentational.
 */
@Composable
fun Vpn5gGlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.10f),
                        Color.White.copy(alpha = 0.03f),
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        content()
    }
}

/**
 * "TAHA VPN 5G" as an animated motion graphic (v1.1.15): a light
 * sweep crossing the brand, speed lines sliding underneath and fine
 * particles drifting up — until the home is truly ready. One infinite
 * transition drives three floats; a handful of Boxes, no bitmaps, no
 * per-frame allocations, so the animation never slows the startup it
 * decorates. The logo above it stays the transparent PNG
 * (R.mipmap.ic_launcher, RGBA with a transparent background).
 */
@Composable
fun Vpn5gMotionBrandText(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "vpn5g_brand_motion")
    // Light sweep: 0 -> 1 across ~2.2 s, then restarts.
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200),
            repeatMode = RepeatMode.Restart,
        ),
        label = "brand_sweep",
    )
    // Speed lines: 0 -> 1 across ~1.4 s.
    val lines by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400),
            repeatMode = RepeatMode.Restart,
        ),
        label = "brand_lines",
    )
    // Particles: 0 -> 1 across ~3 s.
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000),
            repeatMode = RepeatMode.Restart,
        ),
        label = "brand_particles",
    )
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.vpn5g_home_brand),
                color = Vpn5gOnDark,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            // Speed lines under the text: three thin lines sliding
            // left to right at staggered offsets, fading as they travel.
            Box(modifier = Modifier.fillMaxWidth(0.7f).height(16.dp)) {
                val linePhases = listOf(0f, 0.33f, 0.66f)
                val lineWidths = listOf(0.55f, 0.35f, 0.7f)
                linePhases.forEachIndexed { i, phase ->
                    val progress = (lines + phase) % 1f
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxWidth(lineWidths[i])
                            .height(2.dp)
                            .graphicsLayer {
                                translationX = (progress - 0.5f) * widthPx * 0.7f
                                alpha = (1f - progress) * 0.8f
                            }
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.Transparent,
                                        Vpn5gElectricBlue,
                                        Vpn5gCyan,
                                        Color.Transparent,
                                    )
                                ),
                                RoundedCornerShape(1.dp),
                            ),
                    )
                }
            }
        }
        // Light sweep band crossing the brand text.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(56.dp)
                .height(44.dp)
                .graphicsLayer {
                    translationX = (sweep - 0.5f) * (widthPx + 112f)
                    alpha = 0.55f
                }
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            Vpn5gElectricBlue.copy(alpha = 0.9f),
                            Vpn5gCyan.copy(alpha = 0.9f),
                            Color.Transparent,
                        )
                    ),
                ),
        )
        // Fine particles drifting upward over the brand.
        val particleFractions = listOf(0.12f, 0.25f, 0.38f, 0.5f, 0.62f, 0.75f, 0.88f)
        particleFractions.forEachIndexed { i, fraction ->
            val progress = (drift + i * 0.13f) % 1f
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(3.dp)
                    .graphicsLayer {
                        translationX = (fraction - 0.5f) * widthPx
                        translationY = 18f - progress * 36f
                        alpha = (1f - progress) * (0.4f + 0.6f * progress)
                    }
                    .background(Vpn5gCyan, CircleShape),
            )
        }
    }
}

/**
 * Small credit footer ("Made by AREF KH / 2026 - 1405"), shown at the
 * bottom of the home screen content and the Me/Profile screen. This is
 * the ONLY place a personal name appears in the app. The version line
 * (v1.1.5) reads BuildConfig.VERSION_NAME, never a hardcoded string,
 * so it follows every future version bump automatically.
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
        Text(
            text = stringResource(R.string.vpn5g_footer_version, BuildConfig.VERSION_NAME),
            color = Vpn5gOnDarkMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Startup screen (v1.1.14; fast entry + rotating texts v1.1.15):
 * replaces the old fixed 3-second splash.
 *
 * Shows the animated VPN 5G logo (the same pulsing logo as the connect
 * circle language — it scales and floats, then the fire/fade treatment
 * lives on the connect button itself) with the "TAHA VPN 5G" motion
 * graphic and "4ref.ai" underneath, and a REAL 0→100% progress bar
 * driven by the server-list sync stages (fetching sources, pinging) —
 * never a fake timer. v1.1.15: the home opens FAST on the cached list
 * (the heavy refresh runs in the background after entry), so the bar
 * reaches 100% as soon as the cached list is ready; with no cached
 * list the bar follows the real sync and the home opens only when it
 * reaches 100% AND at least one server is available. When the sync
 * finishes with no server, [isError] shows a retry state instead of
 * hanging at 100% forever. While loading, the status line rotates
 * through varied texts so the app never looks stuck.
 */
@Composable
fun Vpn5gStartupScreen(
    progress: Float,
    isError: Boolean,
    onRetry: () -> Unit,
) {
    Vpn5gAnimatedBackground {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 48.dp),
        ) {
            Spacer(Modifier.weight(1f))
            Vpn5gPulsingLogo(size = 120.dp)
            Spacer(Modifier.height(16.dp))
            // v1.1.15: "TAHA VPN 5G" as an animated motion graphic
            // (light sweep, speed lines, fine particles) until the
            // home is truly ready — never a static label.
            Vpn5gMotionBrandText()
            Spacer(Modifier.height(4.dp))
            Text(
                text = "4ref.ai",
                color = Vpn5gCyan,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(48.dp))
            if (isError) {
                Text(
                    text = stringResource(R.string.vpn5g_startup_no_server),
                    color = Vpn5gOnDark,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Vpn5gCyan,
                        contentColor = Color.Black,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.vpn5g_retry),
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else {
                // v1.1.15: VARIED ROTATING texts (Aref's exact list),
                // softly fading every ~1.4 s, so the user never thinks
                // the app will not open. Transient presentation state
                // only; the progress bar below stays the real sync.
                val startupTexts = listOf(
                    stringResource(R.string.vpn5g_startup_text_01),
                    stringResource(R.string.vpn5g_startup_text_02),
                    stringResource(R.string.vpn5g_startup_text_03),
                    stringResource(R.string.vpn5g_startup_text_04),
                    stringResource(R.string.vpn5g_startup_text_05),
                    stringResource(R.string.vpn5g_startup_text_06),
                    stringResource(R.string.vpn5g_startup_text_07),
                    stringResource(R.string.vpn5g_startup_text_08),
                    stringResource(R.string.vpn5g_startup_text_09),
                    stringResource(R.string.vpn5g_startup_text_10),
                    stringResource(R.string.vpn5g_startup_text_11),
                    stringResource(R.string.vpn5g_startup_text_12),
                    stringResource(R.string.vpn5g_startup_text_13),
                )
                var textIndex by remember { mutableStateOf(0) }
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(1400L)
                        textIndex = (textIndex + 1) % startupTexts.size
                    }
                }
                AnimatedContent(
                    targetState = startupTexts[textIndex],
                    transitionSpec = {
                        fadeIn(animationSpec = tween(400)) togetherWith
                            fadeOut(animationSpec = tween(400))
                    },
                    label = "vpn5g_startup_text",
                ) { text ->
                    Text(
                        text = text,
                        color = Vpn5gOnDark,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = Vpn5gCyan,
                    trackColor = Vpn5gOnDark.copy(alpha = 0.15f),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.vpn5g_startup_percent,
                        (progress.coerceIn(0f, 1f) * 100).toInt(),
                    ),
                    color = Vpn5gOnDarkMuted,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.vpn5g_footer_made_by),
                color = Vpn5gOnDarkMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
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
