package com.tripcompanion.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material 3's shape scale. The app's own surfaces read shapes from [AppMetrics]
 * instead; these cover the Material components the app doesn't wrap — menus, dialogs,
 * date pickers, sheets — so nothing lands on a default that contradicts the theme.
 */
val TravelShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(22.dp)
)
