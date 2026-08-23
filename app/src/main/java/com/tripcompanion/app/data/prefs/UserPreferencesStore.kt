package com.tripcompanion.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
 * The settings a traveller sets about themselves and how the trip screens behave.
 *
 * Every field here is read somewhere. That is the whole rule for this class: the brief asks
 * for a Settings screen with profile and preference sections, and a preference that no screen
 * consults is a switch that lies. If a row is added to Settings, a field is added here and a
 * screen starts reading it, in the same change.
 */
data class UserPreferences(
    /** Shown in Home's greeting and on the ticket as the passenger. Blank means "not set". */
    val travellerName: String = "",
    /** Where journeys are measured from when a trip has no origin of its own. */
    val homeCity: String = "",
    /** Itinerary filter: off hides done and skipped entries instead of greying them. */
    val showCompletedActivities: Boolean = true,
    /** Live tracking holds the screen awake, because a phone that sleeps mid-journey is useless. */
    val keepScreenOnDuringJourney: Boolean = false,
    /** Whether the live status screen re-checks on its own, or only when pulled. */
    val autoRefreshLiveStatus: Boolean = true
)

private val Context.userPreferencesDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "user_preferences")

/**
 * Reads and writes [UserPreferences].
 *
 * A second DataStore file rather than sharing [com.tripcompanion.app.ui.theme.ThemeManager]'s:
 * two `preferencesDataStore` delegates over one name throw at first access, and the theme is
 * needed before Compose starts while these are needed only once a screen asks.
 */
@Singleton
class UserPreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val preferences: StateFlow<UserPreferences> = context.userPreferencesDataStore.data
        // An unreadable preferences file must not stop the app drawing; defaults stand in
        // and the next write repairs it. Same contract as the theme store.
        .catch { cause ->
            if (cause is IOException) emit(emptyPreferences()) else throw cause
        }
        .map { prefs ->
            UserPreferences(
                travellerName = prefs[KEY_NAME].orEmpty(),
                homeCity = prefs[KEY_HOME_CITY].orEmpty(),
                showCompletedActivities = prefs[KEY_SHOW_COMPLETED] ?: true,
                keepScreenOnDuringJourney = prefs[KEY_KEEP_AWAKE] ?: false,
                autoRefreshLiveStatus = prefs[KEY_AUTO_REFRESH] ?: true
            )
        }
        .stateIn(scope, SharingStarted.Eagerly, UserPreferences())

    fun setTravellerName(name: String) = putString(KEY_NAME, name.trim())

    fun setHomeCity(city: String) = putString(KEY_HOME_CITY, city.trim())

    fun setShowCompletedActivities(show: Boolean) = putBoolean(KEY_SHOW_COMPLETED, show)

    fun setKeepScreenOnDuringJourney(keep: Boolean) = putBoolean(KEY_KEEP_AWAKE, keep)

    fun setAutoRefreshLiveStatus(refresh: Boolean) = putBoolean(KEY_AUTO_REFRESH, refresh)

    private fun putString(key: Preferences.Key<String>, value: String) {
        scope.launch { context.userPreferencesDataStore.edit { it[key] = value } }
    }

    private fun putBoolean(key: Preferences.Key<Boolean>, value: Boolean) {
        scope.launch { context.userPreferencesDataStore.edit { it[key] = value } }
    }

    private companion object {
        val KEY_NAME = stringPreferencesKey("traveller_name")
        val KEY_HOME_CITY = stringPreferencesKey("home_city")
        val KEY_SHOW_COMPLETED = booleanPreferencesKey("show_completed_activities")
        val KEY_KEEP_AWAKE = booleanPreferencesKey("keep_screen_on_during_journey")
        val KEY_AUTO_REFRESH = booleanPreferencesKey("auto_refresh_live_status")
    }
}
