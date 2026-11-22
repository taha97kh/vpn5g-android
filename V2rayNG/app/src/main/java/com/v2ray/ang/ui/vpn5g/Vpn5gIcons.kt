package com.v2ray.ang.ui.vpn5g

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
