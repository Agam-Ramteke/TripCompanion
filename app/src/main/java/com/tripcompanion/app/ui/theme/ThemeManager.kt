package com.tripcompanion.app.ui.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One visual identity, three ways to pick a mode.
 *
 * [SYSTEM] follows the device, and is the default — a travel app that ignores the phone's
 * own light/dark setting feels broken at night.
 */
enum class AppThemeType {
    LIGHT,
    DARK,
    SYSTEM
}

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Owns which mode is active.
 *
 * Persisted with DataStore (§5). The choice has to survive process death, so it is read
 * back as a flow rather than held only in memory.
 */
@Singleton
class ThemeManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val currentTheme: StateFlow<AppThemeType> = context.themeDataStore.data
        // A corrupt or unreadable preferences file should not stop the app from
        // drawing; fall back to defaults and let the next write repair it.
        .catch { cause ->
            if (cause is IOException) emit(emptyPreferences()) else throw cause
        }
        .map { prefs -> prefs[KEY_THEME].toThemeType() }
        .stateIn(scope, SharingStarted.Eagerly, AppThemeType.SYSTEM)

    fun setTheme(theme: AppThemeType) {
        scope.launch {
            context.themeDataStore.edit { prefs -> prefs[KEY_THEME] = theme.name }
        }
    }

    /**
     * Also reads the names written by earlier versions, so a device that already chose a
     * mode keeps it across the redesign instead of silently resetting.
     */
    private fun String?.toThemeType(): AppThemeType = when (this) {
        "LIGHT", "NOTHING_LIGHT", "NEO_BRUTALIST" -> AppThemeType.LIGHT
        "DARK", "NOTHING_DARK", "NOTHING_OS" -> AppThemeType.DARK
        else -> AppThemeType.SYSTEM
    }

    private companion object {
        val KEY_THEME = stringPreferencesKey("selected_theme")
    }
}
