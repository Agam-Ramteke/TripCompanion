package com.tripcompanion.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.tripcompanion.app.ui.theme.AppThemeExtended

/**
 * Modifier extension that draws the cartographic travel-inspired background behind any container.
 *
 * Visual elements:
 * - Abstract topographic contour lines curving along perimeter negative spaces
 * - Subtle dashed travel journey arcs with tiny waypoint nodes
 * - Cartographic grid ticks and coordinate crosshairs along outer margins
 * - Minimalist geometric compass reticle in the upper margin
 *
 * Contrast is strictly restrained:
 * - Dark mode: True AMOLED Black (#000000) base with subtle near-black / charcoal lines (#080B0A to #151A18)
 * - Light mode: Clean neutral (#F7F8FA) base with faint muted gray-green lines (#E8ECEA to #DDE5E1)
 * - Muted green accent is used sparingly for waypoint dots and navigation details.
 */
fun Modifier.cartographicBackground(
    isDark: Boolean,
    accentColor: Color
): Modifier = this.drawWithCache {
    val w = size.width
    val h = size.height

    // Base background colors
    val baseColor = if (isDark) Color(0xFF000000) else Color(0xFFF8F7F4)

    // Palette for cartographic line geometry (kept extremely subtle and low-contrast)
    val contourMajorColor = if (isDark) Color(0xFF141A17).copy(alpha = 0.35f) else Color(0xFFE2E0D8).copy(alpha = 0.45f)
    val contourMinorColor = if (isDark) Color(0xFF0D1210).copy(alpha = 0.25f) else Color(0xFFEBE9E2).copy(alpha = 0.35f)
    val routeLineColor = if (isDark) Color(0xFF18201C).copy(alpha = 0.40f) else Color(0xFFDCDAD1).copy(alpha = 0.50f)
    val gridTickColor = if (isDark) Color(0xFF121714).copy(alpha = 0.30f) else Color(0xFFDFDDD4).copy(alpha = 0.40f)
    val waypointRingColor = if (isDark) Color(0xFF1A241F).copy(alpha = 0.40f) else Color(0xFFD6D3CA).copy(alpha = 0.50f)
    val waypointAccentMuted = if (isDark) accentColor.copy(alpha = 0.20f) else accentColor.copy(alpha = 0.22f)
    val waypointAccentSolid = if (isDark) accentColor.copy(alpha = 0.40f) else accentColor.copy(alpha = 0.40f)

    // Stroke specifications
    val contourMajorStroke = Stroke(
        width = 1.1.dp.toPx(),
        cap = StrokeCap.Round,
        join = StrokeJoin.Round
    )
    val contourMinorStroke = Stroke(
        width = 0.9.dp.toPx(),
        cap = StrokeCap.Round,
        join = StrokeJoin.Round
    )
    val routeStroke = Stroke(
        width = 1.1.dp.toPx(),
        cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(
            intervals = floatArrayOf(8.dp.toPx(), 8.dp.toPx()),
            phase = 0f
        )
    )
    val routeSecondaryStroke = Stroke(
        width = 0.9.dp.toPx(),
        cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(
            intervals = floatArrayOf(4.dp.toPx(), 7.dp.toPx()),
            phase = 0f
        )
    )
    val thinLineStroke = Stroke(width = 0.85.dp.toPx(), cap = StrokeCap.Round)
    val waypointRingStroke = Stroke(width = 1.0.dp.toPx())

    // ── Precompute Paths (cached per size / theme) ──

    // Top-Right Topographic Contours (4 concentric flowing waves)
    val trContour1 = Path().apply {
        moveTo(w * 0.38f, 0f)
        cubicTo(w * 0.56f, h * 0.035f, w * 0.74f, h * 0.075f, w, h * 0.120f)
    }
    val trContour2 = Path().apply {
        moveTo(w * 0.50f, 0f)
        cubicTo(w * 0.66f, h * 0.055f, w * 0.82f, h * 0.115f, w, h * 0.180f)
    }
    val trContour3 = Path().apply {
        moveTo(w * 0.64f, 0f)
        cubicTo(w * 0.76f, h * 0.080f, w * 0.89f, h * 0.160f, w, h * 0.250f)
    }
    val trContour4 = Path().apply {
        moveTo(w * 0.76f, 0f)
        cubicTo(w * 0.85f, h * 0.105f, w * 0.94f, h * 0.195f, w, h * 0.315f)
    }
    val trContour5 = Path().apply {
        moveTo(w * 0.87f, 0f)
        cubicTo(w * 0.92f, h * 0.065f, w * 0.97f, h * 0.120f, w, h * 0.165f)
    }

    // Bottom-Left Topographic Contours (3 concentric waves along lower corner)
    val blContour1 = Path().apply {
        moveTo(0f, h * 0.70f)
        cubicTo(w * 0.16f, h * 0.76f, w * 0.26f, h * 0.87f, w * 0.33f, h)
    }
    val blContour2 = Path().apply {
        moveTo(0f, h * 0.78f)
        cubicTo(w * 0.12f, h * 0.83f, w * 0.20f, h * 0.92f, w * 0.23f, h)
    }
    val blContour3 = Path().apply {
        moveTo(0f, h * 0.86f)
        cubicTo(w * 0.07f, h * 0.89f, w * 0.13f, h * 0.96f, w * 0.14f, h)
    }

    // Mid-Right Margin Topographic Ridges
    val mrContour1 = Path().apply {
        moveTo(w, h * 0.44f)
        cubicTo(w * 0.88f, h * 0.50f, w * 0.89f, h * 0.59f, w, h * 0.65f)
    }
    val mrContour2 = Path().apply {
        moveTo(w, h * 0.49f)
        cubicTo(w * 0.92f, h * 0.53f, w * 0.93f, h * 0.58f, w, h * 0.61f)
    }

    // Journey Route Arcs
    val journeyRoute1 = Path().apply {
        moveTo(w * 0.04f, h * 0.115f)
        cubicTo(w * 0.26f, h * 0.155f, w * 0.62f, h * 0.195f, w * 0.96f, h * 0.345f)
    }
    val journeyRoute2 = Path().apply {
        moveTo(w * 0.05f, h * 0.835f)
        cubicTo(w * 0.38f, h * 0.745f, w * 0.66f, h * 0.775f, w * 0.95f, h * 0.865f)
    }

    // Compass Geometry
    val compassCenter = Offset(w * 0.86f, h * 0.085f)
    val compassOuterR = 15.dp.toPx()
    val compassInnerR = 5.5.dp.toPx()
    val compassDiamondPath = Path().apply {
        moveTo(compassCenter.x, compassCenter.y - compassOuterR - 3.dp.toPx())
        lineTo(compassCenter.x + 2.5.dp.toPx(), compassCenter.y - compassOuterR + 4.dp.toPx())
        lineTo(compassCenter.x, compassCenter.y - compassOuterR + 2.dp.toPx())
        lineTo(compassCenter.x - 2.5.dp.toPx(), compassCenter.y - compassOuterR + 4.dp.toPx())
        close()
    }

    // Crosshairs positions
    val crosshairs = listOf(
        Offset(w * 0.08f, h * 0.065f),
        Offset(w * 0.92f, h * 0.065f),
        Offset(w * 0.92f, h * 0.680f),
        Offset(w * 0.08f, h * 0.935f)
    )
    val crosshairArm = 3.5.dp.toPx()

    // Margin grid ticks
    val leftTicks = listOf(h * 0.28f, h * 0.52f, h * 0.76f)
    val rightTicks = listOf(h * 0.22f, h * 0.42f, h * 0.82f)
    val topTicks = listOf(w * 0.25f, w * 0.72f)
    val bottomTicks = listOf(w * 0.35f, w * 0.68f)
    val tickLen = 3.5.dp.toPx()

    // Waypoints
    val wp1 = Offset(w * 0.10f, h * 0.128f)
    val wp2 = Offset(w * 0.44f, h * 0.178f)
    val wp3 = Offset(w * 0.80f, h * 0.275f)
    val wp4 = Offset(w * 0.26f, h * 0.785f)
    val wp5 = Offset(w * 0.72f, h * 0.790f)

    onDrawBehind {
        // 1. Solid base ground
        drawRect(color = baseColor)

        // 2. Top-right topographic contours
        drawPath(trContour1, contourMajorColor, style = contourMajorStroke)
        drawPath(trContour2, contourMinorColor, style = contourMinorStroke)
        drawPath(trContour3, contourMajorColor, style = contourMajorStroke)
        drawPath(trContour4, contourMinorColor, style = contourMinorStroke)
        drawPath(trContour5, contourMinorColor, style = contourMinorStroke)

        // 3. Bottom-left topographic contours
        drawPath(blContour1, contourMajorColor, style = contourMajorStroke)
        drawPath(blContour2, contourMinorColor, style = contourMinorStroke)
        drawPath(blContour3, contourMinorColor, style = contourMinorStroke)

        // 4. Mid-right topographic ridges
        drawPath(mrContour1, contourMinorColor, style = contourMinorStroke)
        drawPath(mrContour2, contourMinorColor, style = contourMinorStroke)

        // 5. Journey route dashed arcs
        drawPath(journeyRoute1, routeLineColor, style = routeStroke)
        drawPath(journeyRoute2, routeLineColor, style = routeSecondaryStroke)

        // 6. Waypoints on route
        // WP 1 (Accent node)
        drawCircle(waypointAccentMuted, radius = 4.5.dp.toPx(), center = wp1, style = waypointRingStroke)
        drawCircle(waypointAccentSolid, radius = 1.8.dp.toPx(), center = wp1)

        // WP 2 (Subtle ring)
        drawCircle(waypointRingColor, radius = 2.8.dp.toPx(), center = wp2, style = waypointRingStroke)

        // WP 3 (Accent node)
        drawCircle(waypointRingColor, radius = 4.0.dp.toPx(), center = wp3, style = waypointRingStroke)
        drawCircle(waypointAccentSolid, radius = 1.6.dp.toPx(), center = wp3)

        // WP 4 (Subtle ring)
        drawCircle(waypointRingColor, radius = 2.6.dp.toPx(), center = wp4, style = waypointRingStroke)

        // WP 5 (Accent node)
        drawCircle(waypointAccentMuted, radius = 4.0.dp.toPx(), center = wp5, style = waypointRingStroke)
        drawCircle(waypointAccentSolid, radius = 1.6.dp.toPx(), center = wp5)

        // 7. Minimalist Navigation Reticle
        drawCircle(gridTickColor, radius = compassOuterR, center = compassCenter, style = thinLineStroke)
        drawCircle(gridTickColor, radius = compassInnerR, center = compassCenter, style = thinLineStroke)
        // 4 cardinal notches
        drawLine(gridTickColor, Offset(compassCenter.x - compassOuterR - 2.5.dp.toPx(), compassCenter.y), Offset(compassCenter.x - compassOuterR + 1.dp.toPx(), compassCenter.y), strokeWidth = 0.85.dp.toPx())
        drawLine(gridTickColor, Offset(compassCenter.x + compassOuterR - 1.dp.toPx(), compassCenter.y), Offset(compassCenter.x + compassOuterR + 2.5.dp.toPx(), compassCenter.y), strokeWidth = 0.85.dp.toPx())
        drawLine(gridTickColor, Offset(compassCenter.x, compassCenter.y + compassOuterR - 1.dp.toPx()), Offset(compassCenter.x, compassCenter.y + compassOuterR + 2.5.dp.toPx()), strokeWidth = 0.85.dp.toPx())
        // North indicator diamond
        drawPath(compassDiamondPath, waypointAccentSolid)

        // 8. Margin Crosshairs
        for (pt in crosshairs) {
            drawLine(gridTickColor, Offset(pt.x - crosshairArm, pt.y), Offset(pt.x + crosshairArm, pt.y), strokeWidth = 0.85.dp.toPx())
            drawLine(gridTickColor, Offset(pt.x, pt.y - crosshairArm), Offset(pt.x, pt.y + crosshairArm), strokeWidth = 0.85.dp.toPx())
        }

        // 9. Margin Grid Ticks
        for (y in leftTicks) {
            drawLine(gridTickColor, Offset(0f, y), Offset(tickLen, y), strokeWidth = 0.85.dp.toPx())
        }
        for (y in rightTicks) {
            drawLine(gridTickColor, Offset(w - tickLen, y), Offset(w, y), strokeWidth = 0.85.dp.toPx())
        }
        for (x in topTicks) {
            drawLine(gridTickColor, Offset(x, 0f), Offset(x, tickLen), strokeWidth = 0.85.dp.toPx())
        }
        for (x in bottomTicks) {
            drawLine(gridTickColor, Offset(x, h - tickLen), Offset(x, h), strokeWidth = 0.85.dp.toPx())
        }
    }
}

/**
 * High-performance full-screen container with the cartographic background.
 */
@Composable
fun CartographicBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = AppThemeExtended.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .cartographicBackground(isDark = colors.isDark, accentColor = colors.accent)
    ) {
        content()
    }
}
