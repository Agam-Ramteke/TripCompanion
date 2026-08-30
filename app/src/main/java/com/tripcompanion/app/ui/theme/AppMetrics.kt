package com.tripcompanion.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing, borders, elevation and shape — the measurements that make the whole app feel
 * like one product rather than a set of screens.
 *
 * Cards are separated by a soft shadow and a hairline border, not by a hard offset block.
 * Radii come in three steps: [chipShape] for pills, [cardShape] for the common card, and
 * [cardShapeLarge] for hero surfaces and bottom sheets. Anything that needs a fourth
 * radius is probably the wrong shape.
 */
@Immutable
data class AppMetrics(
    // ── Borders and elevation ──
    /** Hairline card and control outline. */
    val borderWidth: Dp,
    /** Selected-state outline, thick enough to read without a fill. */
    val borderWidthStrong: Dp,
    val dividerWidth: Dp,
    /** Resting card shadow. Soft and low — depth, not drama. */
    val cardElevation: Dp,
    /** A card lifted above its neighbours: the Next Up card, a bottom sheet. */
    val cardElevationRaised: Dp,

    // ── Spacing ──
    val screenPadding: Dp,
    val cardPadding: Dp,
    /** Between two sections of a screen. */
    val sectionGap: Dp,
    /** Between two rows inside a section. */
    val rowGap: Dp,

    // ── Sizing ──
    val controlHeight: Dp,
    val navHeight: Dp,
    /** Height of a full-bleed hero image. */
    val heroHeight: Dp,
    /** Width of the rail a vertical timeline is drawn on. */
    val timelineRailWidth: Dp,
    /** Diameter of the marker on a timeline rail. */
    val timelineNodeSize: Dp,

    // ── Shapes ──
    val cardShape: Shape,
    val cardShapeLarge: Shape,
    val chipShape: Shape,
    val buttonShape: Shape,
    val controlShape: Shape,
    /** Photographs and thumbnails. */
    val imageShape: Shape,
    val badgeShape: Shape
)

val TravelMetrics = AppMetrics(
    borderWidth = 1.dp,
    borderWidthStrong = 2.dp,
    dividerWidth = 1.dp,
    cardElevation = 2.dp,
    cardElevationRaised = 4.dp,

    screenPadding = 16.dp,
    cardPadding = 14.dp,
    sectionGap = 22.dp,
    rowGap = 10.dp,

    controlHeight = 48.dp,
    navHeight = 96.dp,
    heroHeight = 210.dp,
    timelineRailWidth = 2.dp,
    timelineNodeSize = 30.dp,

    cardShape = RoundedCornerShape(18.dp),
    cardShapeLarge = RoundedCornerShape(22.dp),
    chipShape = RoundedCornerShape(100.dp),
    buttonShape = RoundedCornerShape(14.dp),
    controlShape = RoundedCornerShape(14.dp),
    imageShape = RoundedCornerShape(14.dp),
    badgeShape = RoundedCornerShape(8.dp)
)

val LocalAppMetrics = staticCompositionLocalOf { TravelMetrics }
