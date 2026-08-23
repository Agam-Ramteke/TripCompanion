package com.tripcompanion.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripcompanion.app.BuildConfig
import com.tripcompanion.app.data.SampleTripSeeder
import com.tripcompanion.app.data.prefs.UserPreferences
import com.tripcompanion.app.data.prefs.UserPreferencesStore
import com.tripcompanion.app.domain.model.Trip
import com.tripcompanion.app.domain.repository.LocationRepository
import com.tripcompanion.app.domain.repository.TrainRepository
import com.tripcompanion.app.domain.repository.TripRepository
import com.tripcompanion.app.domain.service.TrainStatusService
import com.tripcompanion.app.domain.service.TripExportError
import com.tripcompanion.app.domain.service.TripExportOutcome
import com.tripcompanion.app.domain.service.TripImportError
import com.tripcompanion.app.domain.service.TripImportOutcome
import com.tripcompanion.app.domain.service.TripTransferService
import com.tripcompanion.app.ui.theme.AppThemeType
import com.tripcompanion.app.ui.theme.ThemeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

data class SettingsUiState(
    val theme: AppThemeType = AppThemeType.SYSTEM,
    val preferences: UserPreferences = UserPreferences(),
    /** Every trip, so the export picker can name them rather than just count them. */
    val trips: List<Trip> = emptyList(),
    val placeCount: Int = 0,
    val trainCount: Int = 0,
    val isSeeding: Boolean = false,
    /** Set after seeding so the button can confirm rather than appearing to do nothing. */
    val seededTripId: Long? = null,
    /** True while the "which trip?" dialog is open, before the save dialog is reached. */
    val isChoosingTripToExport: Boolean = false,
    /** A trip chosen for export, waiting for the screen to open the system save dialog. */
    val pendingExport: PendingExport? = null,
    /** True while a file is being written or read, so both buttons can show they are busy. */
    val isTransferring: Boolean = false,
    val exportOutcome: TripExportOutcome? = null,
    val importOutcome: TripImportOutcome? = null,
    /** Named on screen: the user should know whether tracking is observed or projected. */
    val trainProviderName: String = "",
    val isTrainProviderLive: Boolean = false,
    val appVersion: String = BuildConfig.VERSION_NAME
) {
    /** Counted from the list itself, so About and the picker can never disagree. */
    val tripCount: Int get() = trips.size
}

/**
 * A trip the user has chosen to export, on its way to the system save dialog.
 *
 * [requestId] exists only so that exporting the same trip twice is two different values. Without
 * it the second request would compare equal to the first, and the screen — which opens the dialog
 * when this changes — would sit there doing nothing.
 */
data class PendingExport(
    val tripId: Long,
    val fileName: String,
    val requestId: Long
)


/**
 * Settings, where every row does something.
 *
 * The spec originally scoped this screen to the theme alone (§24); the design brief asks for
 * profile, preferences and about sections as well. Both are honoured by the same rule: a row
 * ships only when something reads it. That is why there is no "Backup & Sync" here — cloud
 * sync is Phase 2 (§27), and a switch that syncs nothing is worse than no switch.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themeManager: ThemeManager,
    private val userPreferences: UserPreferencesStore,
    private val sampleTripSeeder: SampleTripSeeder,
    private val tripTransferService: TripTransferService,
    tripRepository: TripRepository,
    locationRepository: LocationRepository,
    trainRepository: TrainRepository,
    trainStatusService: TrainStatusService
) : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsUiState(
            trainProviderName = trainStatusService.providerName,
            isTrainProviderLive = trainStatusService.isLive
        )
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private var exportRequests = 0L

    init {
        viewModelScope.launch {
            combine(
                themeManager.currentTheme,
                userPreferences.preferences,
                tripRepository.getAllTrips(),
                locationRepository.getAllLocations(),
                trainRepository.getAllTrains()
            ) { theme, prefs, trips, places, trains ->
                _state.value.copy(
                    theme = theme,
                    preferences = prefs,
                    trips = trips,
                    placeCount = places.size,
                    trainCount = trains.size
                )
            }.collect { next -> _state.update { next } }
        }
    }

    fun setTheme(theme: AppThemeType) = themeManager.setTheme(theme)

    fun setTravellerName(name: String) = userPreferences.setTravellerName(name)

    fun setHomeCity(city: String) = userPreferences.setHomeCity(city)

    fun setShowCompletedActivities(show: Boolean) =
        userPreferences.setShowCompletedActivities(show)

    fun setKeepScreenOnDuringJourney(keep: Boolean) =
        userPreferences.setKeepScreenOnDuringJourney(keep)

    fun setAutoRefreshLiveStatus(refresh: Boolean) =
        userPreferences.setAutoRefreshLiveStatus(refresh)

    /**
     * Writes the sample trip.
     *
     * Deliberately not idempotent — pressing it twice gives two trips, which is what "insert
     * a trip" means. Guarding on the trip's name would be exactly the special-casing §4 rules
     * out, and a duplicate is deletable while a mystery guard is not.
     */
    fun loadSampleTrip() {
        if (_state.value.isSeeding) return
        _state.update { it.copy(isSeeding = true, seededTripId = null) }
        viewModelScope.launch {
            try {
                val id = sampleTripSeeder.seed()
                _state.update { it.copy(seededTripId = id) }
            } finally {
                _state.update { it.copy(isSeeding = false) }
            }
        }
    }

    fun dismissSeedConfirmation() {
        _state.update { it.copy(seededTripId = null) }
    }

    // ---- Moving a trip to another phone ------------------------------------

    /**
     * Opens the "which trip?" step.
     *
     * A trip has to be named before a file can be, so exporting is two taps rather than one. The
     * alternative — a file holding every trip — makes sharing one trip with a friend mean sharing
     * all of them.
     */
    fun beginExport() {
        if (_state.value.trips.isEmpty() || _state.value.isTransferring) return
        _state.update { it.copy(isChoosingTripToExport = true) }
    }

    /** Abandons an export, whether from the trip list or from a cancelled save dialog. */
    fun cancelExport() {
        _state.update { it.copy(isChoosingTripToExport = false, pendingExport = null) }
    }

    /** Picks the trip and works out what to call its file; the screen opens the save dialog. */
    fun chooseTripToExport(tripId: Long) {
        viewModelScope.launch {
            val fileName = tripTransferService.suggestedFileName(tripId)
            exportRequests++
            _state.update {
                it.copy(
                    isChoosingTripToExport = false,
                    pendingExport = PendingExport(tripId, fileName, exportRequests)
                )
            }
        }
    }

    /**
     * Writes the chosen trip into the document the user just named.
     *
     * [open] is the platform's half — see
     * [com.tripcompanion.app.data.transfer.TripArchiveFiles.openForWrite] — and is closed here
     * rather than by the service, because whoever opens a stream owns flushing it.
     */
    fun exportPickedTrip(open: () -> OutputStream?) {
        val pending = _state.value.pendingExport ?: return
        if (_state.value.isTransferring) return
        _state.update { it.copy(isTransferring = true, exportOutcome = null) }
        viewModelScope.launch {
            val outcome = try {
                open()?.use { tripTransferService.export(pending.tripId, it) }
                    ?: TripExportOutcome.Failed(TripExportError.WRITE_FAILED)
            } catch (_: Exception) {
                TripExportOutcome.Failed(TripExportError.WRITE_FAILED)
            }
            _state.update {
                it.copy(isTransferring = false, pendingExport = null, exportOutcome = outcome)
            }
        }
    }

    /** Reads a trip file and adds what is in it as a new trip. */
    fun importTrip(open: () -> InputStream?) {
        if (_state.value.isTransferring) return
        _state.update { it.copy(isTransferring = true, importOutcome = null) }
        viewModelScope.launch {
            val outcome = try {
                open()?.use { tripTransferService.import(it) }
                    ?: TripImportOutcome.Failed(TripImportError.UNREADABLE)
            } catch (_: Exception) {
                TripImportOutcome.Failed(TripImportError.UNREADABLE)
            }
            _state.update { it.copy(isTransferring = false, importOutcome = outcome) }
        }
    }

    fun dismissTransferOutcome() {
        _state.update { it.copy(exportOutcome = null, importOutcome = null) }
    }
}
