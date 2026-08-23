package com.tripcompanion.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * The type scale.
 *
 * One family: the platform's geometric sans (Roboto on device). Weight and size carry
 * the hierarchy; there is no second face and no monospace anywhere. Where numerals
 * need to line up in a column — departure times, delays, distances, PNRs, stat
 * figures — the style asks for tabular figures via `fontFeatureSettings = "tnum"`
 * rather than switching to a mono family, which is what made the old screens read as
 * a terminal.
 *
 * Sizes follow the brief:
 *  - screen titles 28–32, section headers 18–21, card titles 16–19
 *  - body 14–16, secondary 12–14, metadata 11–12
 *
 * Sentence case is the default. Uppercase appears only in [AppTextStyles.eyebrow] and
 * status badges, both of which are short metadata by definition.
 */

private val Sans = FontFamily.Default

/** Tabular figures, so a column of times or prices doesn't shift as digits change. */
private const val TabularFigures = "tnum"

val TravelTypography = androidx.compose.material3.Typography(
    // ── Display: hero titles, usually over a photograph ──
    displayLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.8).sp
    ),
    displayMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.6).sp
    ),
    displaySmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp
    ),

    // ── Headline: screen titles and the largest section headers ──
    headlineLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 27.sp,
        letterSpacing = (-0.3).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.2).sp
    ),

    // ── Title: section headers and card titles ──
    titleLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.1).sp
    ),
    titleSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp
    ),

    // ── Body ──
    bodyLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.1.sp
    ),
    bodySmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.1.sp
    ),

    // ── Label: buttons, chips, metadata ──
    labelLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp
    ),
    labelSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.3.sp
    )
)

/**
 * Styles the Material scale has no slot for.
 *
 * Everything here earns its place by being used in more than one screen. A one-off
 * belongs inline at its call site, not in the theme.
 */
@Immutable
data class AppTextStyles(
    /** Trip and place names sitting on a hero photograph. */
    val hero: TextStyle,
    /** Times, dates, delays, distances — anything that must align down a column. */
    val time: TextStyle,
    /** The large form of [time]: a departure time given headline weight. */
    val timeLarge: TextStyle,
    /** The figure on a stat card. Tabular, so four cards in a row stay aligned. */
    val statNumber: TextStyle,
    /** Short uppercase metadata. The only place uppercase is allowed outside badges. */
    val eyebrow: TextStyle,
    /** Status badge text: small, semibold, slight tracking. */
    val badge: TextStyle
)

private val Tabular = TextStyle(
    fontFamily = Sans,
    fontFeatureSettings = TabularFigures
)

val TravelTextStyles = AppTextStyles(
    hero = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.6).sp
    ),
    time = Tabular.copy(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.sp
    ),
    timeLarge = Tabular.copy(
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.3).sp
    ),
    statNumber = Tabular.copy(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.5).sp,
        textAlign = TextAlign.Start
    ),
    eyebrow = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.9.sp
    ),
    badge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp
    )
)

val LocalAppTextStyles = staticCompositionLocalOf { TravelTextStyles }
