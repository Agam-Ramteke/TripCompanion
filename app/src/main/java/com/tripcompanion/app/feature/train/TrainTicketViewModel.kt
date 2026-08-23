package com.tripcompanion.app.feature.train

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tripcompanion.app.data.prefs.UserPreferencesStore
import com.tripcompanion.app.domain.model.Train
import com.tripcompanion.app.domain.model.TrainStop
import com.tripcompanion.app.domain.repository.TrainRepository
import com.tripcompanion.app.domain.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrainTicketUiState(
    val train: Train? = null,
    /** Origin and terminus of the stored timetable, used only to name the boarding point. */
    val boardingStop: TrainStop? = null,
    val tripName: String = "",
    /** From the traveller's own profile. Blank is fine — a ticket without a name still works. */
    val passengerName: String = "",
    val isLoading: Boolean = true
)

/**
 * The ticket, and nothing else.
 *
 * Deliberately does not touch the network. A ticket is what you hold up to a conductor in a
 * tunnel with no signal, so every field on it comes from the database and the profile.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TrainTicketViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val trainRepository: TrainRepository,
    private val tripRepository: TripRepository,
    private val userPreferences: UserPreferencesStore
) : ViewModel() {

    private val trainId: Long = savedStateHandle.get<String>("trainId")?.toLongOrNull() ?: 0L

    private val _state = MutableStateFlow(TrainTicketUiState())
    val state: StateFlow<TrainTicketUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                trainRepository.getTrainById(trainId),
                trainRepository.getStops(trainId),
                userPreferences.preferences
            ) { train, stops, prefs ->
                Triple(train, stops, prefs.travellerName)
            }.flatMapLatest { (train, stops, name) ->
                if (train == null) {
                    flowOf(TrainTicketUiState(isLoading = false))
                } else {
                    tripRepository.getTripById(train.tripId).map { trip ->
                        // The boarding point, not the station the ticket was booked from: a
                        // reservation that starts further down the line is ordinary, and the
                        // timetabled time that matters is the one where you actually get on.
                        val boardingCode = train.boardingCode.ifBlank { train.originCode }
                        TrainTicketUiState(
                            train = train,
                            boardingStop = stops.firstOrNull {
                                it.stationCode.equals(boardingCode, ignoreCase = true)
                            },
                            tripName = trip?.name.orEmpty(),
                            passengerName = name,
                            isLoading = false
                        )
                    }
                }
            }.collect { next -> _state.update { next } }
        }
    }
}
