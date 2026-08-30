package com.tripcompanion.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The palette.
 *
 * One identity in two modes. Light is the primary mode — bright, near-white, with
 * a muted forest teal carrying every interactive state. Dark is designed rather than
 * inverted: its surfaces are chosen for a near-black ground, its teal is lifted
 * so it still reads as an accent, and photographs keep their own colour in both.
 *
 * Colour is semantic here, never decorative. Teal means "you can act on this",
 * green means "confirmed or on time", red means "wrong or urgent", orange means
 * "soon or needs attention", purple means "somewhere you sleep", destination teal
 * means "somewhere you go". A screen that wants a colour asks for the meaning.
 *
 * Two teals, and the difference matters:
 *  - [TravelLightColors.Primary] fills things, and carries white text.
 *  - [TravelLightColors.Accent] sits *on* the background as text or an icon.
 */

// ── Light ──────────────────────────────────────────────────────────────────────

object TravelLightColors {
    /** Warm stone off-white background: natural, calm, and avoids sterile computer white. */
    val Background = Color(0xFFF8F7F4)
    val Surface = Color(0xFFFFFFFF)

    /** Quiet section grounds, filter pills, and inactive control fills. */
    val SurfaceVariant = Color(0xFFF0EFEB)

    /** A card sitting on top of another card / elevated surface. */
    val SurfaceRaised = Color(0xFFFFFFFF)

    val OnBackground = Color(0xFF1A1D20)
    val OnSurface = Color(0xFF1A1D20)
    val OnSurfaceVariant = Color(0xFF5C646E)

    /** Quieter than [OnSurfaceVariant]: timestamps, hints, disabled rows. */
    val TextFaint = Color(0xFF9199A4)

    /** Fills. Carries white text — rich eucalyptus sage. */
    val Primary = Color(0xFF2D5C4C)
    val OnPrimary = Color(0xFFFFFFFF)

    /** Pressed and deep-emphasis eucalyptus tone. */
    val PrimaryDeep = Color(0xFF1E4034)

    /** Selected-state grounds, soft badges, chart fills. */
    val PrimarySoft = Color(0xFFE8F1EC)

    /** Eucalyptus accent as text or an icon on [Background]. */
    val Accent = Color(0xFF2D5C4C)
    val OnAccent = Color(0xFFFFFFFF)
    val AccentSoft = Color(0xFFE8F1EC)

    val Secondary = Color(0xFF5C646E)
    val OnSecondary = Color(0xFFFFFFFF)

    val Outline = Color(0xFFE4E7E5)
    val OutlineVariant = Color(0xFFEEEFEB)

    // Semantic families. Solid for fills and icons, Soft for badge grounds,
    // Text for the label that sits on the soft ground.
    val Success = Color(0xFF228B58)
    val OnSuccess = Color(0xFFFFFFFF)
    val SuccessSoft = Color(0xFFE6F5ED)
    val SuccessText = Color(0xFF156C42)

    val Danger = Color(0xFFD32F2F)
    val OnDanger = Color(0xFFFFFFFF)
    val DangerSoft = Color(0xFFFFEEEE)
    val DangerText = Color(0xFFB71C1C)

    val Warning = Color(0xFFD97706)
    val OnWarning = Color(0xFF3D2A00)
    val WarningSoft = Color(0xFFFEF3C7)
    val WarningText = Color(0xFF92400E)

    val Info = Color(0xFF2D5C4C)
    val OnInfo = Color(0xFFFFFFFF)
    val InfoSoft = Color(0xFFE8F1EC)
    val InfoText = Color(0xFF2D5C4C)

    /** STAY category — warm heather purple. */
    val Accommodation = Color(0xFF6655A8)
    val OnAccommodation = Color(0xFFFFFFFF)
    val AccommodationSoft = Color(0xFFF2EFFB)
    val AccommodationText = Color(0xFF4A3B8C)

    /** CUSTOM / DESTINATION category — forest sage teal. */
    val Destination = Color(0xFF1F8575)
    val OnDestination = Color(0xFFFFFFFF)
    val DestinationSoft = Color(0xFFE4F4F0)
    val DestinationText = Color(0xFF126356)

    /** JOURNEY category — warm earthy terracotta / rust (replaces harsh red). */
    val Journey = Color(0xFFC44B37)
    val OnJourney = Color(0xFFFFFFFF)
    val JourneySoft = Color(0xFFFBECE8)
    val JourneyText = Color(0xFF9E3523)

    /** VISIT category — ocean slate / travel azure. */
    val Visit = Color(0xFF25689E)
    val OnVisit = Color(0xFFFFFFFF)
    val VisitSoft = Color(0xFFEAF2FA)
    val VisitText = Color(0xFF194E78)

    val Food = Color(0xFFD97706)
    val OnFood = Color(0xFFFFFFFF)
    val FoodSoft = Color(0xFFFEF3C7)
    val FoodText = Color(0xFF92400E)

    val Error = Danger
    val OnError = OnDanger

    // Event status. One answer per mode to "what colour is SKIPPED".
    val StatusActive = Success
    val StatusStartingSoon = Warning
    val StatusUpcoming = Info
    val StatusCompleted = Color(0xFF6B7280)
    val StatusSkipped = Color(0xFF9199A4)
    val StatusMissed = Danger
}

// ── Dark ───────────────────────────────────────────────────────────────────────

object TravelDarkColors {
    /** True AMOLED Black ground. */
    val Background = Color(0xFF000000)
    /** Elevated dark slate surface for tactile physical card depth over black. */
    val Surface = Color(0xFF121519)
    val SurfaceVariant = Color(0xFF1A1F26)
    val SurfaceRaised = Color(0xFF222933)

    val OnBackground = Color(0xFFF4F6F8)
    val OnSurface = Color(0xFFF4F6F8)
    val OnSurfaceVariant = Color(0xFFA0A6B2)
    val TextFaint = Color(0xFF6B727E)

    /** Lifted eucalyptus sage for dark theme readability. */
    val Primary = Color(0xFF5E9C83)
    val OnPrimary = Color(0xFF0B1F17)
    val PrimaryDeep = Color(0xFF477A66)

    /** Selected grounds on a near-black ground are subtle deep tints. */
    val PrimarySoft = Color(0xFF122A20)

    /** Lifted sage for text and icons on [Background]. */
    val Accent = Color(0xFF5E9C83)
    val OnAccent = Color(0xFF122A20)
    val AccentSoft = Color(0xFF122A20)

    val Secondary = Color(0xFFA0A6B2)
    val OnSecondary = Color(0xFF0B0C0F)

    val Outline = Color(0xFF232B34)
    val OutlineVariant = Color(0xFF1B2129)

    // Solids are lifted so they read against dark slate; Soft grounds are deep tints.
    val Success = Color(0xFF34D399)
    val OnSuccess = Color(0xFF04150C)
    val SuccessSoft = Color(0xFF0F2C20)
    val SuccessText = Color(0xFF6EE7B7)

    val Danger = Color(0xFFFF6B6B)
    val OnDanger = Color(0xFF2A0606)
    val DangerSoft = Color(0xFF2E1517)
    val DangerText = Color(0xFFFF8A8A)

    val Warning = Color(0xFFFBBF24)
    val OnWarning = Color(0xFF241700)
    val WarningSoft = Color(0xFF2C2109)
    val WarningText = Color(0xFFFDE68A)

    val Info = Color(0xFF5E9C83)
    val OnInfo = Color(0xFF122A20)
    val InfoSoft = Color(0xFF122A20)
    val InfoText = Color(0xFF5E9C83)

    val Accommodation = Color(0xFF9B86EE)
    val OnAccommodation = Color(0xFF16082E)
    val AccommodationSoft = Color(0xFF211B34)
    val AccommodationText = Color(0xFFC4B5FD)

    val Destination = Color(0xFF34B8A4)
    val OnDestination = Color(0xFF04201C)
    val DestinationSoft = Color(0xFF0D2B26)
    val DestinationText = Color(0xFF6EE7D6)

    /** JOURNEY category — lifted warm terracotta / rust. */
    val Journey = Color(0xFFE56B55)
    val OnJourney = Color(0xFF2D1612)
    val JourneySoft = Color(0xFF2D1612)
    val JourneyText = Color(0xFFFF927D)

    /** VISIT category — lifted ocean slate. */
    val Visit = Color(0xFF59A0DC)
    val OnVisit = Color(0xFF0D1D2E)
    val VisitSoft = Color(0xFF142234)
    val VisitText = Color(0xFF93C5FD)

    val Food = Color(0xFFF59E0B)
    val OnFood = Color(0xFF2C1F0A)
    val FoodSoft = Color(0xFF2C1F0A)
    val FoodText = Color(0xFFFDE68A)

    val Error = Danger
    val OnError = OnDanger

    val StatusActive = Success
    val StatusStartingSoon = Warning
    val StatusUpcoming = Info
    val StatusCompleted = Color(0xFF8A9099)
    val StatusSkipped = Color(0xFF6B727E)
    val StatusMissed = Danger
}
