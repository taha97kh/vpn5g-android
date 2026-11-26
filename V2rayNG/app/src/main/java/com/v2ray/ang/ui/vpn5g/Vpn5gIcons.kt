package com.v2ray.ang.ui.vpn5g

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Hand-drawn Canvas icons for the VPN5G IRBOT home screen and bottom
 * navigation (X-VPN style). The project has no material-icons dependency,
 * so these are drawn from paths instead of adding one. All icons are
 * stroke/fill drawings in a 1x1 box scaled to [size].
 */

@Composable
fun Vpn5gPowerIcon(color: Color, size: Dp = 96.dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val stroke = w * 0.09f
        // Arc open at the top (power glyph).
        drawArc(
            color = color,
            startAngle = 55f,
            sweepAngle = 250f,
            useCenter = false,
            topLeft = Offset(w * 0.08f, w * 0.08f),
            size = androidx.compose.ui.geometry.Size(w * 0.84f, w * 0.84f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        // Vertical line from the top centre.
        drawLine(
            color = color,
            start = Offset(w * 0.5f, w * 0.02f),
            end = Offset(w * 0.5f, w * 0.48f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun Vpn5gBoltIcon(color: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.58f, h * 0.02f)
            lineTo(w * 0.22f, h * 0.56f)
            lineTo(w * 0.46f, h * 0.56f)
            lineTo(w * 0.38f, h * 0.98f)
            lineTo(w * 0.78f, h * 0.42f)
            lineTo(w * 0.54f, h * 0.42f)
            close()
        }
        drawPath(path, color)
    }
}

@Composable
fun Vpn5gHomeIcon(color: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.06f)
            lineTo(w * 0.94f, h * 0.44f)
            lineTo(w * 0.84f, h * 0.44f)
            lineTo(w * 0.84f, h * 0.94f)
            lineTo(w * 0.16f, h * 0.94f)
            lineTo(w * 0.16f, h * 0.44f)
            lineTo(w * 0.06f, h * 0.44f)
            close()
        }
        drawPath(path, color)
    }
}

@Composable
fun Vpn5gCrownIcon(color: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.06f, h * 0.82f)
            lineTo(w * 0.06f, h * 0.30f)
            lineTo(w * 0.28f, h * 0.54f)
            lineTo(w * 0.5f, h * 0.14f)
            lineTo(w * 0.72f, h * 0.54f)
            lineTo(w * 0.94f, h * 0.30f)
            lineTo(w * 0.94f, h * 0.82f)
            close()
        }
        drawPath(path, color)
        drawLine(
            color = color,
            start = Offset(w * 0.06f, h * 0.92f),
            end = Offset(w * 0.94f, h * 0.92f),
            strokeWidth = h * 0.07f,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
fun Vpn5gGlobeIcon(color: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.09f
        drawCircle(
            color = color,
            radius = w * 0.44f,
            center = Offset(w * 0.5f, h * 0.5f),
            style = Stroke(width = stroke),
        )
        drawOval(
            color = color,
            topLeft = Offset(w * 0.32f, h * 0.06f),
            size = androidx.compose.ui.geometry.Size(w * 0.36f, h * 0.88f),
            style = Stroke(width = stroke),
        )
        drawLine(
            color = color,
            start = Offset(w * 0.06f, h * 0.5f),
            end = Offset(w * 0.94f, h * 0.5f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/** Question mark in a circle, for the Guide tab (v1.1.5 §5). */
@Composable
fun Vpn5gHelpIcon(color: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.09f
        drawCircle(
            color = color,
            radius = w * 0.44f,
            center = Offset(w * 0.5f, h * 0.5f),
            style = Stroke(width = stroke),
        )
        // Question-mark hook.
        val hook = Path().apply {
            moveTo(w * 0.38f, h * 0.36f)
            cubicTo(w * 0.38f, h * 0.22f, w * 0.62f, h * 0.22f, w * 0.62f, h * 0.34f)
            cubicTo(w * 0.62f, h * 0.44f, w * 0.52f, h * 0.46f, w * 0.50f, h * 0.54f)
        }
        drawPath(hook, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
        // Dot.
        drawCircle(
            color = color,
            radius = w * 0.055f,
            center = Offset(w * 0.5f, h * 0.68f),
        )
    }
}

@Composable
fun Vpn5gRoutesIcon(color: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = h * 0.09f
        // Top line, arrow to the right.
        drawLine(color, Offset(w * 0.10f, h * 0.30f), Offset(w * 0.82f, h * 0.30f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.82f, h * 0.30f), Offset(w * 0.64f, h * 0.18f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.82f, h * 0.30f), Offset(w * 0.64f, h * 0.42f), stroke, StrokeCap.Round)
        // Bottom line, arrow to the left.
        drawLine(color, Offset(w * 0.90f, h * 0.70f), Offset(w * 0.18f, h * 0.70f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.18f, h * 0.70f), Offset(w * 0.36f, h * 0.58f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.18f, h * 0.70f), Offset(w * 0.36f, h * 0.82f), stroke, StrokeCap.Round)
    }
}

@Composable
fun Vpn5gPersonIcon(color: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.09f
        // Head.
        drawCircle(
            color = color,
            radius = w * 0.20f,
            center = Offset(w * 0.5f, h * 0.30f),
            style = Stroke(width = stroke),
        )
        // Shoulders.
        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.14f, h * 0.52f),
            size = androidx.compose.ui.geometry.Size(w * 0.72f, h * 0.62f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

@Composable
fun Vpn5gChevronIcon(color: Color, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.12f
        drawLine(color, Offset(w * 0.32f, h * 0.16f), Offset(w * 0.68f, h * 0.5f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.68f, h * 0.5f), Offset(w * 0.32f, h * 0.84f), stroke, StrokeCap.Round)
    }
}

/** Checkmark for the selected protocol row. */
@Composable
fun Vpn5gCheckIcon(color: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.11f
        drawLine(color, Offset(w * 0.18f, h * 0.54f), Offset(w * 0.42f, h * 0.78f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.42f, h * 0.78f), Offset(w * 0.84f, h * 0.22f), stroke, StrokeCap.Round)
    }
}

/** Magnifier for the server search field. */
@Composable
fun Vpn5gSearchIcon(color: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.11f
        drawCircle(
            color = color,
            radius = w * 0.30f,
            center = Offset(w * 0.42f, h * 0.42f),
            style = Stroke(width = stroke),
        )
        drawLine(
            color,
            Offset(w * 0.64f, h * 0.64f),
            Offset(w * 0.88f, h * 0.88f),
            stroke,
            StrokeCap.Round,
        )
    }
}

/** Padlock for locked countries (v1.1.15): shackle arc + body + keyhole. */
@Composable
fun Vpn5gLockIcon(color: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.10f
        // Shackle: open-bottom arc above the body.
        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.28f, h * 0.06f),
            size = androidx.compose.ui.geometry.Size(w * 0.44f, h * 0.44f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        // Body: rounded rect.
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.16f, h * 0.44f),
            size = androidx.compose.ui.geometry.Size(w * 0.68f, h * 0.48f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f, w * 0.08f),
        )
        // Keyhole: small circle + slot, cut in the background color.
        drawCircle(
            color = Color.Black,
            radius = w * 0.07f,
            center = Offset(w * 0.5f, h * 0.62f),
        )
        drawLine(
            color = Color.Black,
            start = Offset(w * 0.5f, h * 0.66f),
            end = Offset(w * 0.5f, h * 0.80f),
            strokeWidth = stroke * 0.8f,
            cap = StrokeCap.Round,
        )
    }
}

/** Gift box for the gift-code button (v1.1.15): box + lid + ribbon + bow. */
@Composable
fun Vpn5gGiftIcon(color: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.09f
        // Box body.
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.14f, h * 0.42f),
            size = androidx.compose.ui.geometry.Size(w * 0.72f, h * 0.50f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.06f, w * 0.06f),
        )
        // Lid.
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.10f, h * 0.30f),
            size = androidx.compose.ui.geometry.Size(w * 0.80f, h * 0.14f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.05f, w * 0.05f),
        )
        // Ribbon: vertical band through box + lid.
        drawRect(
            color = Color.Black,
            topLeft = Offset(w * 0.45f, h * 0.30f),
            size = androidx.compose.ui.geometry.Size(w * 0.10f, h * 0.62f),
        )
        // Bow: two loops above the lid.
        drawArc(
            color = color,
            startAngle = 90f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.22f, h * 0.04f),
            size = androidx.compose.ui.geometry.Size(w * 0.24f, h * 0.28f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawArc(
            color = color,
            startAngle = 270f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.54f, h * 0.04f),
            size = androidx.compose.ui.geometry.Size(w * 0.24f, h * 0.28f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

/** Hamburger for the side menu (v1.1.21): three horizontal lines. */
@Composable
fun Vpn5gMenuIcon(color: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.10f
        listOf(0.26f, 0.50f, 0.74f).forEach { fraction ->
            drawLine(
                color,
                Offset(w * 0.16f, h * fraction),
                Offset(w * 0.84f, h * fraction),
                stroke,
                StrokeCap.Round,
            )
        }
    }
}

/** Bell for the announcements button (v1.1.21): dome + skirt + clapper. */
@Composable
fun Vpn5gBellIcon(color: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.10f
        // Dome: open-bottom arc.
        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.22f, h * 0.10f),
            size = androidx.compose.ui.geometry.Size(w * 0.56f, h * 0.56f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        // Skirt: flared base under the dome.
        drawLine(color, Offset(w * 0.14f, h * 0.72f), Offset(w * 0.86f, h * 0.72f), stroke, StrokeCap.Round)
        // Clapper: small dot below the skirt.
        drawCircle(color = color, radius = w * 0.07f, center = Offset(w * 0.5f, h * 0.86f))
    }
}

/** Megaphone for the channel button (v1.1.32, Aref's order: each of
 * the channel/bot/support buttons gets its own dedicated icon):
 * cone + mouthpiece + handle + sound arcs. */
@Composable
fun Vpn5gChannelIcon(color: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.09f
        // Cone: trapezoid opening to the right.
        val cone = Path().apply {
            moveTo(w * 0.10f, h * 0.38f)
            lineTo(w * 0.52f, h * 0.20f)
            lineTo(w * 0.52f, h * 0.80f)
            lineTo(w * 0.10f, h * 0.62f)
            close()
        }
        drawPath(cone, color)
        // Handle under the mouthpiece.
        drawLine(
            color,
            Offset(w * 0.16f, h * 0.62f),
            Offset(w * 0.22f, h * 0.88f),
            stroke,
            StrokeCap.Round,
        )
        // Sound arcs to the right of the cone mouth.
        drawArc(
            color = color,
            startAngle = -55f,
            sweepAngle = 110f,
            useCenter = false,
            topLeft = Offset(w * 0.48f, h * 0.30f),
            size = androidx.compose.ui.geometry.Size(w * 0.30f, h * 0.40f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawArc(
            color = color,
            startAngle = -55f,
            sweepAngle = 110f,
            useCenter = false,
            topLeft = Offset(w * 0.54f, h * 0.18f),
            size = androidx.compose.ui.geometry.Size(w * 0.42f, h * 0.64f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

/** Robot head for the bot button (v1.1.32): rounded head + antenna +
 * eyes + side ears. */
@Composable
fun Vpn5gBotIcon(color: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.09f
        // Antenna: stem + tip dot.
        drawLine(color, Offset(w * 0.5f, h * 0.26f), Offset(w * 0.5f, h * 0.10f), stroke, StrokeCap.Round)
        drawCircle(color = color, radius = w * 0.06f, center = Offset(w * 0.5f, h * 0.08f))
        // Head: rounded rect outline.
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.14f, h * 0.26f),
            size = androidx.compose.ui.geometry.Size(w * 0.72f, h * 0.56f),
            cornerRadius = CornerRadius(w * 0.14f, w * 0.14f),
            style = Stroke(width = stroke),
        )
        // Ears: short bars on both sides.
        drawLine(color, Offset(w * 0.06f, h * 0.48f), Offset(w * 0.14f, h * 0.48f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.86f, h * 0.48f), Offset(w * 0.94f, h * 0.48f), stroke, StrokeCap.Round)
        // Eyes: two dots.
        drawCircle(color = color, radius = w * 0.055f, center = Offset(w * 0.36f, h * 0.50f))
        drawCircle(color = color, radius = w * 0.055f, center = Offset(w * 0.64f, h * 0.50f))
        // Mouth: short line.
        drawLine(color, Offset(w * 0.40f, h * 0.68f), Offset(w * 0.60f, h * 0.68f), stroke, StrokeCap.Round)
    }
}

/** Headset for the support buttons (v1.1.32): headband arc + two ear
 * cups + microphone boom. */
@Composable
fun Vpn5gSupportIcon(color: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.09f
        // Headband: open-bottom arc.
        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.16f, h * 0.10f),
            size = androidx.compose.ui.geometry.Size(w * 0.68f, h * 0.68f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        // Ear cups: rounded rects at both ends of the band.
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.08f, h * 0.44f),
            size = androidx.compose.ui.geometry.Size(w * 0.16f, h * 0.30f),
            cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.76f, h * 0.44f),
            size = androidx.compose.ui.geometry.Size(w * 0.16f, h * 0.30f),
            cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
        )
        // Microphone boom from the right cup toward the mouth.
        drawLine(color, Offset(w * 0.84f, h * 0.74f), Offset(w * 0.84f, h * 0.86f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.84f, h * 0.86f), Offset(w * 0.62f, h * 0.86f), stroke, StrokeCap.Round)
        drawCircle(color = color, radius = w * 0.055f, center = Offset(w * 0.58f, h * 0.86f))
    }
}

/** Apps grid for the split-tunnel row (v1.1.21): 2×2 rounded squares. */
@Composable
fun Vpn5gAppsIcon(color: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val gap = w * 0.08f
        val cell = (w - gap) / 2f
        val radius = cell * 0.28f
        listOf(
            Offset(0f, 0f),
            Offset(cell + gap, 0f),
            Offset(0f, cell + gap),
            Offset(cell + gap, cell + gap),
        ).forEach { topLeft ->
            drawRoundRect(
                color = color,
                topLeft = topLeft,
                size = androidx.compose.ui.geometry.Size(cell, cell),
                cornerRadius = CornerRadius(radius, radius),
            )
        }
    }
}
