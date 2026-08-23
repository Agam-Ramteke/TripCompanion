package com.tripcompanion.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Place
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.tripcompanion.app.domain.model.EventType
import com.tripcompanion.app.ui.theme.AppThemeExtended
import com.tripcompanion.app.ui.theme.ExtendedColors

/**
 * How each event type looks and reads — declared once.
 *
 * Every screen that shows an event needs an icon, a word and a colour for its type, and
 * writing that `when` at each call site is how a type gets added to the enum and missed
 * in four places. Adding a case to [EventType] now breaks exactly this file, which is
 * the intent.
 *
 * [color] is what makes a timeline marker, a plan-strip node and a map pin for the same
 * event agree. Nothing else should decide an event's colour.
 */
val EventType.icon: ImageVector
    get() = when (this) {
        EventType.JOURNEY -> Icons.Default.DirectionsTransit
        EventType.VISIT -> Icons.Default.Place
        EventType.STAY -> Icons.Default.Hotel
        EventType.FOOD -> Icons.Default.Restaurant
        EventType.CUSTOM -> Icons.Default.PushPin
    }

/** Title-cased for reading, not the raw enum name. */
val EventType.label: String
    get() = when (this) {
        EventType.JOURNEY -> "Journey"
        EventType.VISIT -> "Visit"
        EventType.STAY -> "Stay"
        EventType.FOOD -> "Food"
        EventType.CUSTOM -> "Custom"
    }

/**
 * The solid category colour, resolved against a palette instead of the composition.
 *
 * The composable [color] property below cannot be read from an ordinary lambda, and building a
 * list of map markers or chart slices is exactly that. Both read this one `when`, so adding an
 * [EventType] still breaks a single place.
 */
fun EventType.colorIn(colors: ExtendedColors): Color = when (this) {
    EventType.JOURNEY -> colors.journey
    EventType.VISIT -> colors.visit
    EventType.STAY -> colors.stay
    EventType.FOOD -> colors.food
    EventType.CUSTOM -> colors.custom
}

/** The tinted ground, resolved against a palette. See [colorIn]. */
fun EventType.softColorIn(colors: ExtendedColors): Color = when (this) {
    EventType.JOURNEY -> colors.journeySoft
    EventType.VISIT -> colors.visitSoft
    EventType.STAY -> colors.staySoft
    EventType.FOOD -> colors.foodSoft
    EventType.CUSTOM -> colors.customSoft
}

/** The solid category colour: markers, pins, icon tint on a soft ground. */
val EventType.color: Color
    @Composable
    @ReadOnlyComposable
    get() = colorIn(AppThemeExtended.colors)

/** The tinted ground the solid sits on: icon chips, badge backgrounds, placeholder fills. */
val EventType.softColor: Color
    @Composable
    @ReadOnlyComposable
    get() = softColorIn(AppThemeExtended.colors)
