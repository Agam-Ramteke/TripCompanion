package com.tripcompanion.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tripcompanion.app.core.util.DateTimeUtils
import com.tripcompanion.app.domain.model.TrainPassenger
import com.tripcompanion.app.feature.train.TrainTicketViewModel
import com.tripcompanion.app.ui.components.AppCard
import com.tripcompanion.app.ui.components.AppIconButton
import com.tripcompanion.app.ui.components.EmptyState
import com.tripcompanion.app.ui.components.SecondaryButton
import com.tripcompanion.app.ui.components.TicketCard
import com.tripcompanion.app.ui.components.TicketPassenger
import com.tripcompanion.app.ui.components.noteworthyLabel
import com.tripcompanion.app.ui.components.tone
import com.tripcompanion.app.ui.theme.AppThemeExtended

/**
 * The travel document.
 *
 * Everything here comes from the database, so it reads in a tunnel with no signal — which is
 * where a ticket is actually needed. Nothing on this screen asks the network.
 */
@Composable
fun TrainTicketScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEditTrain: (Long, Long) -> Unit,
    onNavigateToProfile: () -> Unit,
    viewModel: TrainTicketViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val metrics = AppThemeExtended.metrics

    if (state.isLoading) {
        Box(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val train = state.train
    if (train == null) {
        Box(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = metrics.screenPadding),
            contentAlignment = Alignment.Center
        ) {
            EmptyState(
                icon = Icons.Default.ConfirmationNumber,
                title = "No ticket here",
                message = "This train was deleted, so there is nothing left to show.",
                actionLabel = "Go back",
                onAction = onNavigateBack
            )
        }
        return
    }

    // The party on the booking, or the profile name standing in for a booking that has none.
    val ticketPassengers = train.passengers.map { it.toTicketPassenger() }.ifEmpty {
        state.passengerName.takeIf { it.isNotBlank() }?.let { name ->
            listOf(
                TicketPassenger(
                    serialLabel = "1",
                    name = name,
                    statusLabel = train.bookingStatus.noteworthyLabel,
                    statusTone = train.bookingStatus.tone
                )
            )
        }.orEmpty()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            start = metrics.screenPadding,
            end = metrics.screenPadding,
            top = 12.dp,
            bottom = 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(metrics.rowGap)
    ) {
        item("header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    onClick = onNavigateBack,
                    size = 44.dp
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Ticket",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (state.tripName.isNotBlank()) {
                        Text(
                            text = state.tripName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        item("ticket") {
            val departureCode = if (train.boardsElsewhere) train.boardingCode.ifBlank { "NAGPUR" } else train.originCode.ifBlank { "—" }
            val departureName = if (train.boardsElsewhere) train.boardingName.ifBlank { "Nagpur" } else train.originName.ifBlank { train.originCode }
            val departureTime = DateTimeUtils.formatTime(train.departureTime)
            val arrivalTime = DateTimeUtils.formatTime(train.arrivalTime)

            TicketCard(
                trainNumber = train.number,
                trainName = train.name.ifBlank { "Train ${train.number}" },
                originCode = departureCode,
                originName = departureName,
                originTime = departureTime,
                destinationCode = train.destinationCode.ifBlank { "—" },
                destinationName = train.destinationName.ifBlank { train.destinationCode },
                destinationTime = arrivalTime,
                dateLabel = DateTimeUtils.formatShortDateWithYear(train.departureTime.toLocalDate()),
                pnr = train.pnr.takeIf { it.isNotBlank() },
                travelClass = train.travelClass.takeIf { it.isNotBlank() },
                platform = train.platform.takeIf { it.isNotBlank() },
                passengers = ticketPassengers,
                bookingStatusLabel = train.effectiveBookingStatus.noteworthyLabel,
                bookingStatusTone = train.effectiveBookingStatus.tone
            )
        }

        // Warning for when the passenger boards elsewhere down the line.
        if (train.boardsElsewhere) {
            item("boards-elsewhere") {
                AppCard(
                    color = AppThemeExtended.colors.warningSoft,
                    borderColor = AppThemeExtended.colors.warning
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = AppThemeExtended.colors.warning,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "BOARD AT ${train.boardingName.uppercase()}",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Your ticket is booked from " +
                                    "${train.originName.ifBlank { train.originCode }}, but your " +
                                    "boarding station is ${train.boardingName}.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Departure from ${train.boardingName}: ${DateTimeUtils.formatTime(train.departureTime)}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // A ticket with nobody on it is not an error, but it is worth one line — the booking form
        // and the profile are the two places a name comes from.
        if (ticketPassengers.isEmpty()) {
            item("no-name") {
                AppCard {
                    Text(
                        text = "No passenger on this ticket",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Add the party in the booking form, import the e-ticket PDF, or " +
                            "set your name in Settings and every ticket carries it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    SecondaryButton(
                        text = "Set your name",
                        onClick = onNavigateToProfile,
                        icon = Icons.Default.Person
                    )
                }
            }
        }

        state.boardingStop?.let { stop ->
            item("boarding") {
                AppCard {
                    Text(
                        text = "Boarding",
                        style = AppThemeExtended.text.eyebrow,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stop.stationName.ifBlank { stop.stationCode },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    stop.scheduledDeparture?.let { departure ->
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Timetabled departure ${DateTimeUtils.formatTime(departure)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (train.notes.isNotBlank()) {
            item("notes") {
                AppCard {
                    Text(
                        text = "Notes",
                        style = AppThemeExtended.text.eyebrow,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = train.notes,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        item("edit") {
            SecondaryButton(
                text = "Edit these details",
                onClick = { onNavigateToEditTrain(train.tripId, train.id) },
                icon = Icons.Default.Edit
            )
        }
    }
}

/**
 * A passenger, resolved to the strings a ticket prints.
 *
 * The berth falls back through three levels — the parsed seat, the queue position, then the
 * railway's own column verbatim — because a blank where the berth goes is the one thing this
 * screen must never show. The berth *type* goes on the detail line rather than beside the
 * berth: "Side upper" is the part that changes how a night on a train feels, and the narrow
 * right-hand column is for the number you look for on the coach.
 */
private fun TrainPassenger.toTicketPassenger(): TicketPassenger = TicketPassenger(
    serialLabel = serialNo.toString(),
    name = name.ifBlank { "Passenger $serialNo" },
    detail = listOf(age?.toString().orEmpty(), gender.label, allotment.berthType.label)
        .filter { it.isNotBlank() }
        .joinToString(" · "),
    seat = allotment.seatLabel
        .ifBlank { allotment.queueLabel }
        .ifBlank { currentStatusText }
        .ifBlank { "—" },
    statusLabel = allotment.status.noteworthyLabel,
    statusTone = allotment.status.tone
)
