package com.tripcompanion.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colour roles Material 3's [androidx.compose.material3.ColorScheme] has no slot for.
 *
 * Three groups, and the grouping is the point:
 *
 *  - **Emphasis** — [accent] is blue used *as text or an icon on the background*, which
 *    needs more contrast than the blue that fills a button. [surfaceRaised] is a card on
 *    a card. [textFaint] is a third text tier below `onSurfaceVariant`.
 *  - **Semantic** — success / warning / danger / info. Each comes as a triple: the solid
 *    for fills and icons, a `Soft` ground for badges, and a `Text` tone that is legible
 *    *on* that soft ground. Never pair a solid with a soft; that's what the triple is for.
 *  - **Category** — one colour per [com.tripcompanion.app.domain.model.EventType], read
 *    through `EventType.color` so timeline markers, plan-strip nodes and map pins can
 *    never disagree.
 *
 * Status colours are resolved per mode rather than computed, because a grey that reads as
 * "completed, not an error" on white is not the same grey on near-black.
 */
@Immutable
data class ExtendedColors(
    /**
     * Which mode is drawing. Components read this for the handful of decisions that are
     * structural rather than a colour swap — chiefly elevation, since a drop shadow on a
     * near-black ground is invisible and cards must separate by surface step and border
     * instead.
     */
    val isDark: Boolean = false,

    // Emphasis
    val accent: Color = TravelLightColors.Accent,
    val onAccent: Color = TravelLightColors.OnAccent,
    val accentSoft: Color = TravelLightColors.AccentSoft,
    val surfaceRaised: Color = TravelLightColors.SurfaceRaised,
    val textFaint: Color = TravelLightColors.TextFaint,

    // Semantic
    val success: Color = TravelLightColors.Success,
    val onSuccess: Color = TravelLightColors.OnSuccess,
    val successSoft: Color = TravelLightColors.SuccessSoft,
    val successText: Color = TravelLightColors.SuccessText,

    val warning: Color = TravelLightColors.Warning,
    val onWarning: Color = TravelLightColors.OnWarning,
    val warningSoft: Color = TravelLightColors.WarningSoft,
    val warningText: Color = TravelLightColors.WarningText,

    val danger: Color = TravelLightColors.Danger,
    val onDanger: Color = TravelLightColors.OnDanger,
    val dangerSoft: Color = TravelLightColors.DangerSoft,
    val dangerText: Color = TravelLightColors.DangerText,

    val info: Color = TravelLightColors.Info,
    val onInfo: Color = TravelLightColors.OnInfo,
    val infoSoft: Color = TravelLightColors.InfoSoft,
    val infoText: Color = TravelLightColors.InfoText,

    /**
     * Teal, for places and destinations. Not one of the four semantic roles — a place is
     * neither good news nor bad news — but it needs the same triple so a place chip and a
     * place marker match.
     */
    val destination: Color = TravelLightColors.Destination,
    val destinationSoft: Color = TravelLightColors.DestinationSoft,
    val destinationText: Color = TravelLightColors.DestinationText,

    // Category, one per EventType
    val journey: Color = TravelLightColors.Danger,
    val journeySoft: Color = TravelLightColors.DangerSoft,
    val stay: Color = TravelLightColors.Accommodation,
    val staySoft: Color = TravelLightColors.AccommodationSoft,
    val visit: Color = TravelLightColors.Primary,
    val visitSoft: Color = TravelLightColors.PrimarySoft,
    val food: Color = TravelLightColors.Warning,
    val foodSoft: Color = TravelLightColors.WarningSoft,
    val custom: Color = TravelLightColors.Destination,
    val customSoft: Color = TravelLightColors.DestinationSoft,

    // Event status
    val statusActive: Color = TravelLightColors.StatusActive,
    val statusStartingSoon: Color = TravelLightColors.StatusStartingSoon,
    val statusUpcoming: Color = TravelLightColors.StatusUpcoming,
    val statusCompleted: Color = TravelLightColors.StatusCompleted,
    val statusSkipped: Color = TravelLightColors.StatusSkipped,
    val statusMissed: Color = TravelLightColors.StatusMissed
)

val TravelLightExtendedColors = ExtendedColors()

val TravelDarkExtendedColors = ExtendedColors(
    isDark = true,
    accent = TravelDarkColors.Accent,
    onAccent = TravelDarkColors.OnAccent,
    accentSoft = TravelDarkColors.AccentSoft,
    surfaceRaised = TravelDarkColors.SurfaceRaised,
    textFaint = TravelDarkColors.TextFaint,

    success = TravelDarkColors.Success,
    onSuccess = TravelDarkColors.OnSuccess,
    successSoft = TravelDarkColors.SuccessSoft,
    successText = TravelDarkColors.SuccessText,

    warning = TravelDarkColors.Warning,
    onWarning = TravelDarkColors.OnWarning,
    warningSoft = TravelDarkColors.WarningSoft,
    warningText = TravelDarkColors.WarningText,

    danger = TravelDarkColors.Danger,
    onDanger = TravelDarkColors.OnDanger,
    dangerSoft = TravelDarkColors.DangerSoft,
    dangerText = TravelDarkColors.DangerText,

    info = TravelDarkColors.Info,
    onInfo = TravelDarkColors.OnInfo,
    infoSoft = TravelDarkColors.InfoSoft,
    infoText = TravelDarkColors.InfoText,

    destination = TravelDarkColors.Destination,
    destinationSoft = TravelDarkColors.DestinationSoft,
    destinationText = TravelDarkColors.DestinationText,

    journey = TravelDarkColors.Danger,
    journeySoft = TravelDarkColors.DangerSoft,
    stay = TravelDarkColors.Accommodation,
    staySoft = TravelDarkColors.AccommodationSoft,
    visit = TravelDarkColors.Accent,
    visitSoft = TravelDarkColors.PrimarySoft,
    food = TravelDarkColors.Warning,
    foodSoft = TravelDarkColors.WarningSoft,
    custom = TravelDarkColors.Destination,
    customSoft = TravelDarkColors.DestinationSoft,

    statusActive = TravelDarkColors.StatusActive,
    statusStartingSoon = TravelDarkColors.StatusStartingSoon,
    statusUpcoming = TravelDarkColors.StatusUpcoming,
    statusCompleted = TravelDarkColors.StatusCompleted,
    statusSkipped = TravelDarkColors.StatusSkipped,
    statusMissed = TravelDarkColors.StatusMissed
)

val LocalExtendedColors = staticCompositionLocalOf { TravelLightExtendedColors }
