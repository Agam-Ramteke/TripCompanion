package com.tripcompanion.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

// ── Material 3 colour schemes ──
//
// `primary` is the fill blue and `tertiary` is the on-background accent blue; the two are
// deliberately different tones. See Color.kt for why.

private val TravelLightColorScheme = lightColorScheme(
    primary = TravelLightColors.Primary,
    onPrimary = TravelLightColors.OnPrimary,
    primaryContainer = TravelLightColors.PrimarySoft,
    onPrimaryContainer = TravelLightColors.PrimaryDeep,
    secondary = TravelLightColors.Secondary,
    onSecondary = TravelLightColors.OnSecondary,
    secondaryContainer = TravelLightColors.SurfaceVariant,
    onSecondaryContainer = TravelLightColors.OnSurface,
    tertiary = TravelLightColors.Accent,
    onTertiary = TravelLightColors.OnAccent,
    tertiaryContainer = TravelLightColors.AccentSoft,
    onTertiaryContainer = TravelLightColors.Accent,
    background = TravelLightColors.Background,
    onBackground = TravelLightColors.OnBackground,
    surface = TravelLightColors.Surface,
    onSurface = TravelLightColors.OnSurface,
    surfaceVariant = TravelLightColors.SurfaceVariant,
    onSurfaceVariant = TravelLightColors.OnSurfaceVariant,
    surfaceContainerHighest = TravelLightColors.SurfaceVariant,
    error = TravelLightColors.Error,
    onError = TravelLightColors.OnError,
    errorContainer = TravelLightColors.DangerSoft,
    onErrorContainer = TravelLightColors.DangerText,
    outline = TravelLightColors.Outline,
    outlineVariant = TravelLightColors.OutlineVariant,
    scrim = TravelLightColors.OnBackground
)

private val TravelDarkColorScheme = darkColorScheme(
    primary = TravelDarkColors.Primary,
    onPrimary = TravelDarkColors.OnPrimary,
    primaryContainer = TravelDarkColors.PrimarySoft,
    onPrimaryContainer = TravelDarkColors.Accent,
    secondary = TravelDarkColors.Secondary,
    onSecondary = TravelDarkColors.OnSecondary,
    secondaryContainer = TravelDarkColors.SurfaceVariant,
    onSecondaryContainer = TravelDarkColors.OnSurface,
    tertiary = TravelDarkColors.Accent,
    onTertiary = TravelDarkColors.OnAccent,
    tertiaryContainer = TravelDarkColors.AccentSoft,
    onTertiaryContainer = TravelDarkColors.Accent,
    background = TravelDarkColors.Background,
    onBackground = TravelDarkColors.OnBackground,
    surface = TravelDarkColors.Surface,
    onSurface = TravelDarkColors.OnSurface,
    surfaceVariant = TravelDarkColors.SurfaceVariant,
    onSurfaceVariant = TravelDarkColors.OnSurfaceVariant,
    surfaceContainerHighest = TravelDarkColors.SurfaceRaised,
    error = TravelDarkColors.Error,
    onError = TravelDarkColors.OnError,
    errorContainer = TravelDarkColors.DangerSoft,
    onErrorContainer = TravelDarkColors.DangerText,
    outline = TravelDarkColors.Outline,
    outlineVariant = TravelDarkColors.OutlineVariant,
    scrim = TravelDarkColors.Background
)

// ── Theme ViewModel ──

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val themeManager: ThemeManager
) : ViewModel() {
    val currentTheme = themeManager.currentTheme

    fun setTheme(theme: AppThemeType) {
        themeManager.setTheme(theme)
    }
}

// ── Composable theme ──

@Composable
fun AppTheme(
    themeViewModel: ThemeViewModel = hiltViewModel(),
    content: @Composable () -> Unit
) {
    val themeType by themeViewModel.currentTheme.collectAsStateWithLifecycle()

    val isDark = when (themeType) {
        AppThemeType.DARK -> true
        AppThemeType.LIGHT -> false
        AppThemeType.SYSTEM -> isSystemInDarkTheme()
    }

    // The status and navigation bar icons are drawn by the system, so they have to be told
    // which way round we are. Without this, choosing Dark while the phone is set to Light
    // leaves dark icons on a near-black bar — invisible, and only in that one combination.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    CompositionLocalProvider(
        LocalExtendedColors provides if (isDark) TravelDarkExtendedColors else TravelLightExtendedColors,
        LocalAppMetrics provides TravelMetrics,
        LocalAppTextStyles provides TravelTextStyles
    ) {
        MaterialTheme(
            colorScheme = if (isDark) TravelDarkColorScheme else TravelLightColorScheme,
            typography = TravelTypography,
            shapes = TravelShapes,
            content = content
        )
    }
}

/**
 * Convenience accessors for the tokens Material 3 does not carry.
 */
object AppThemeExtended {
    val colors: ExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalExtendedColors.current

    val metrics: AppMetrics
        @Composable
        @ReadOnlyComposable
        get() = LocalAppMetrics.current

    val text: AppTextStyles
        @Composable
        @ReadOnlyComposable
        get() = LocalAppTextStyles.current
}
