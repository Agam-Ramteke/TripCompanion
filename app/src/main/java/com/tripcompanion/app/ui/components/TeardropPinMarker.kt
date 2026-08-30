package com.tripcompanion.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.Fort
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tripcompanion.app.domain.model.EventType
import kotlin.math.roundToInt

private val PIN_WIDTH = 34.dp
private val PIN_HEIGHT = 44.dp
private val CIRCLE_DIAMETER = 34.dp
private val ICON_SIZE = 18.dp

/**
 * Editorial Travel Itinerary Teardrop Pin Marker.
 * Matches the illustrated mockup:
 * - Circular head with a bottom teardrop tip pointing directly at the location.
 * - 2.5dp crisp white border & soft drop shadow.
 * - Category glyph in pure white.
 * - Clean floating place label text to the right (no bounding box) with high-contrast text halo.
 */
@Composable
fun TeardropPinMarker(
    title: String,
    eventType: EventType,
    pinColor: Color,
    screenX: Float,
    screenY: Float,
    modifier: Modifier = Modifier,
    isFocused: Boolean = false,
    isCompleted: Boolean = false,
    orderNumber: Int? = null,
    onClick: () -> Unit = {}
) {
    val isDark = MaterialTheme.colorScheme.background.let {
        (it.red * 0.299 + it.green * 0.587 + it.blue * 0.114) < 0.5
    }

    val displayColor = when {
        isCompleted -> Color(0xFF64748B)
        else -> pinColor
    }

    val categoryIcon = resolveCategoryIcon(eventType, title)

    // Pulsing halo for the focused/selected stop
    val infiniteTransition = rememberInfiniteTransition(label = "halo")
    val haloPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloScale"
    )

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .offset {
                IntOffset(
                    x = (screenX - 17.dp.toPx()).roundToInt(),
                    y = (screenY - 44.dp.toPx()).roundToInt()
                )
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Teardrop Pin Canvas + Centered Glyph
            Box(
                modifier = Modifier.size(PIN_WIDTH, PIN_HEIGHT),
                contentAlignment = Alignment.TopCenter
            ) {
                // Focus Halo
                if (isFocused) {
                    Canvas(
                        modifier = Modifier
                            .size(54.dp)
                            .offset(x = 0.dp, y = (-5).dp)
                    ) {
                        drawCircle(
                            color = displayColor.copy(alpha = 0.28f),
                            radius = (size.minDimension / 2f) * haloPulse
                        )
                        drawCircle(
                            color = displayColor.copy(alpha = 0.45f),
                            radius = (size.minDimension / 2f) * 0.85f
                        )
                    }
                }

                // Teardrop Shape with White Border and Drop Shadow
                Canvas(modifier = Modifier.size(PIN_WIDTH, PIN_HEIGHT)) {
                    val w = size.width
                    val h = size.height
                    val r = w / 2f
                    val tipY = h

                    // Teardrop Path
                    val path = Path().apply {
                        // Top circle arc from angle 150 deg to 30 deg
                        reset()
                        moveTo(w / 2f, 0f)
                        // Right curve down to tip
                        cubicTo(
                            w, 0f,
                            w, r * 1.35f,
                            w * 0.62f, r * 1.75f
                        )
                        lineTo(w / 2f, tipY)
                        // Left curve back up from tip
                        lineTo(w * 0.38f, r * 1.75f)
                        cubicTo(
                            0f, r * 1.35f,
                            0f, 0f,
                            w / 2f, 0f
                        )
                        close()
                    }

                    // Shadow
                    drawIntoCanvas { canvas ->
                        val shadowPaint = Paint().apply {
                            color = Color.Black.copy(alpha = 0.28f)
                            asFrameworkPaint().maskFilter =
                                android.graphics.BlurMaskFilter(6f, android.graphics.BlurMaskFilter.Blur.NORMAL)
                        }
                        canvas.drawPath(path, shadowPaint)
                    }

                    // Fill with vibrant category gradient
                    val gradient = Brush.verticalGradient(
                        colors = listOf(
                            displayColor,
                            displayColor.copy(alpha = 0.88f)
                        ),
                        startY = 0f,
                        endY = h
                    )
                    drawPath(path, brush = gradient)

                    // 2.2dp Crisp Pure White Border
                    drawIntoCanvas { canvas ->
                        val strokePaint = Paint().apply {
                            color = Color.White
                            strokeWidth = 4.5f
                            style = PaintingStyle.Stroke
                        }
                        canvas.drawPath(path, strokePaint)
                    }
                }

                // Category Icon Centered in Head
                Box(
                    modifier = Modifier
                        .size(CIRCLE_DIAMETER)
                        .offset(y = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = categoryIcon,
                        contentDescription = title,
                        tint = Color.White,
                        modifier = Modifier.size(ICON_SIZE)
                    )
                }
            }

            Spacer(Modifier.width(6.dp))

            // Floating Clean Place Label Pill Badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isDark) Color(0xFF1E293B).copy(alpha = 0.94f) else Color.White.copy(alpha = 0.95f),
                shadowElevation = if (isFocused) 4.dp else 2.dp,
                border = BorderStroke(
                    width = if (isFocused) 1.5.dp else 0.5.dp,
                    color = if (isFocused) displayColor else if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.10f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (orderNumber != null) {
                        Text(
                            text = "$orderNumber. ",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp
                            ),
                            color = displayColor
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold,
                            fontSize = 12.sp,
                            letterSpacing = 0.1.sp
                        ),
                        color = if (isDark) Color(0xFFF1F5F9) else Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Resolves the category icon for the stop matching the mockup.
 */
private fun resolveCategoryIcon(eventType: EventType, title: String): ImageVector {
    val lower = title.lowercase()
    return when {
        lower.contains("palace") || lower.contains("fort") || lower.contains("castle") || lower.contains("sajjangarh") ->
            Icons.Default.Fort
        lower.contains("haveli") || lower.contains("mandir") || lower.contains("temple") || lower.contains("monument") ->
            Icons.Default.AccountBalance
        lower.contains("lake") || lower.contains("photo") || lower.contains("camera") || lower.contains("view") || lower.contains("point") ->
            Icons.Default.CameraAlt
        lower.contains("hotel") || lower.contains("resort") || lower.contains("stay") || eventType == EventType.STAY ->
            Icons.Default.Hotel
        lower.contains("food") || lower.contains("restaurant") || lower.contains("cafe") || lower.contains("lunch") || lower.contains("dinner") || eventType == EventType.FOOD ->
            Icons.Default.Restaurant
        lower.contains("station") || lower.contains("train") || lower.contains("rail") || eventType == EventType.JOURNEY ->
            Icons.Default.DirectionsTransit
        eventType == EventType.VISIT ->
            Icons.Default.Place
        else ->
            Icons.Default.Place
    }
}
