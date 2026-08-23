package com.tripcompanion.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The palette.
 *
 * One identity in two modes. Light is the primary mode — bright, near-white, with
 * a travel blue carrying every interactive state. Dark is designed rather than
 * inverted: its surfaces are chosen for a near-black ground, its blue is lifted
 * so it still reads as an accent, and photographs keep their own colour in both.
 *
 * Colour is semantic here, never decorative. Blue means "you can act on this",
 * green means "confirmed or on time", red means "wrong or urgent", orange means
 * "soon or needs attention", purple means "somewhere you sleep", teal means
 * "somewhere you go". A screen that wants a colour asks for the meaning.
 *
 * Two blues, and the difference matters:
 *  - [TravelLightColors.Primary] fills things, and carries white text at 4.67:1.
 *  - [TravelLightColors.Accent] sits *on* the background as text or an icon,
 *    where a fill-weight blue would be too light to read.
 */

// ── Light ──────────────────────────────────────────────────────────────────────

object TravelLightColors {
    val Background = Color(0xFFF7F8FA)
    val Surface = Color(0xFFFFFFFF)

    /** Quiet section grounds and inactive control fills. */
    val SurfaceVariant = Color(0xFFF1F3F7)

    /** A card sitting on top of another card. */
    val SurfaceRaised = Color(0xFFFFFFFF)

    val OnBackground = Color(0xFF12141A)
    val OnSurface = Color(0xFF12141A)
    val OnSurfaceVariant = Color(0xFF5F6672)

    /** Quieter than [OnSurfaceVariant]: timestamps, hints, disabled rows. */
    val TextFaint = Color(0xFF9AA1AD)

    /** Fills. Carries white text. */
    val Primary = Color(0xFF1769FF)
    val OnPrimary = Color(0xFFFFFFFF)

    /** Pressed and deep-emphasis blue. */
    val PrimaryDeep = Color(0xFF0F4FD8)

    /** Selected-state grounds, soft badges, chart fills. */
    val PrimarySoft = Color(0xFFEAF2FF)

    /** Blue as text or an icon on [Background] — 6.26:1. */
    val Accent = Color(0xFF0F4FD8)
    val OnAccent = Color(0xFFFFFFFF)
    val AccentSoft = Color(0xFFEAF2FF)

    val Secondary = Color(0xFF5F6672)
    val OnSecondary = Color(0xFFFFFFFF)

    val Outline = Color(0xFFE3E7EE)
    val OutlineVariant = Color(0xFFEFF2F7)

    // Semantic families. Solid for fills and icons, Soft for badge grounds,
    // Text for the label that sits on the soft ground.
    val Success = Color(0xFF20A464)
    val OnSuccess = Color(0xFFFFFFFF)
    val SuccessSoft = Color(0xFFE8F7EF)
    val SuccessText = Color(0xFF157A49)

    val Danger = Color(0xFFFF4B4B)
    val OnDanger = Color(0xFFFFFFFF)
    val DangerSoft = Color(0xFFFFECEC)
    val DangerText = Color(0xFFD32020)

    val Warning = Color(0xFFF59E0B)
    val OnWarning = Color(0xFF3D2A00)
    val WarningSoft = Color(0xFFFFF4D6)
    val WarningText = Color(0xFF9A6206)

    val Info = Color(0xFF1769FF)
    val OnInfo = Color(0xFFFFFFFF)
    val InfoSoft = Color(0xFFEAF2FF)
    val InfoText = Color(0xFF0F4FD8)

    val Accommodation = Color(0xFF7C4DFF)
    val OnAccommodation = Color(0xFFFFFFFF)
    val AccommodationSoft = Color(0xFFF0EBFF)
    val AccommodationText = Color(0xFF5B2FE0)

    val Destination = Color(0xFF0E9384)
    val OnDestination = Color(0xFFFFFFFF)
    val DestinationSoft = Color(0xFFE3F5F2)
    val DestinationText = Color(0xFF0A6E63)

    val Error = Danger
    val OnError = OnDanger

    // Event status. One answer per mode to "what colour is SKIPPED".
    val StatusActive = Success
    val StatusStartingSoon = Warning
    val StatusUpcoming = Info
    val StatusCompleted = Color(0xFF6B7280)
    val StatusSkipped = Color(0xFF9AA1AD)
    val StatusMissed = Danger
}

// ── Dark ───────────────────────────────────────────────────────────────────────

object TravelDarkColors {
    val Background = Color(0xFF0B0C0F)
    val Surface = Color(0xFF15171B)
    val SurfaceVariant = Color(0xFF1B1E23)
    val SurfaceRaised = Color(0xFF22262C)

    val OnBackground = Color(0xFFFFFFFF)
    val OnSurface = Color(0xFFFFFFFF)
    val OnSurfaceVariant = Color(0xFFA7ABB2)
    val TextFaint = Color(0xFF71767F)

    /** Deep enough that white labels clear 4.63:1. */
    val Primary = Color(0xFF1F6FEB)
    val OnPrimary = Color(0xFFFFFFFF)
    val PrimaryDeep = Color(0xFF1758BC)

    /** Selected grounds on a near-black ground are tints, not washes. */
    val PrimarySoft = Color(0xFF152238)

    /** Lifted blue for text and icons on [Background] — 6.11:1. */
    val Accent = Color(0xFF4C8DFF)
    val OnAccent = Color(0xFF05132E)
    val AccentSoft = Color(0xFF152238)

    val Secondary = Color(0xFFA7ABB2)
    val OnSecondary = Color(0xFF0B0C0F)

    val Outline = Color(0xFF2B2F35)
    val OutlineVariant = Color(0xFF21252A)

    // Solids are lifted so they read against near-black; Soft grounds are tints
    // of the same hue rather than the light mode's pale washes.
    val Success = Color(0xFF2DBE78)
    val OnSuccess = Color(0xFF04150C)
    val SuccessSoft = Color(0xFF10261B)
    val SuccessText = Color(0xFF4FD495)

    val Danger = Color(0xFFFF6B6B)
    val OnDanger = Color(0xFF2A0606)
    val DangerSoft = Color(0xFF2E1517)
    val DangerText = Color(0xFFFF8A8A)

    val Warning = Color(0xFFFFB020)
    val OnWarning = Color(0xFF241700)
    val WarningSoft = Color(0xFF2C2109)
    val WarningText = Color(0xFFFFC55C)

    val Info = Color(0xFF4C8DFF)
    val OnInfo = Color(0xFF05132E)
    val InfoSoft = Color(0xFF152238)
    val InfoText = Color(0xFF7FADFF)

    val Accommodation = Color(0xFF9B77FF)
    val OnAccommodation = Color(0xFF16082E)
    val AccommodationSoft = Color(0xFF221A38)
    val AccommodationText = Color(0xFFB79AFF)

    val Destination = Color(0xFF25B5A3)
    val OnDestination = Color(0xFF04201C)
    val DestinationSoft = Color(0xFF0F2A27)
    val DestinationText = Color(0xFF52CFBF)

    val Error = Danger
    val OnError = OnDanger

    val StatusActive = Success
    val StatusStartingSoon = Warning
    val StatusUpcoming = Info
    val StatusCompleted = Color(0xFF8A9099)
    val StatusSkipped = Color(0xFF71767F)
    val StatusMissed = Danger
}
