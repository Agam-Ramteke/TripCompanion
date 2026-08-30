package com.tripcompanion.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tripcompanion.app.domain.model.EventStatus
import com.tripcompanion.app.domain.model.EventType
import com.tripcompanion.app.domain.model.TripStatus
import com.tripcompanion.app.ui.theme.AppThemeExtended

/**
 * The list rows of the app.
 *
 * Each of these takes plain values rather than a domain model. That keeps the components
 * usable from a preview and from two different screens with different queries behind them,
 * and it means adding a column to an entity never edits this file. The one exception is
 * [EventType] and [EventStatus], which are passed as enums precisely so the colour comes
 * from [EventType.color] and cannot be typed in by hand.
 */

// ═══════════════════════════════════════════════════════════════════════════════
//  Trips
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * A trip in a list: cover photograph, name, dates, status, and a one-line count.
 *
 * The progress bar only appears while a trip is actually running. Showing "0% complete" on
 * a trip that starts in three weeks would be technically true and completely useless.
 */
@Composable
fun TripCard(
    name: String,
    dateRange: String,
    status: TripStatus,
    modifier: Modifier = Modifier,
    coverUri: String? = null,
    summary: String? = null,
    progressFraction: Float? = null,
    countdown: String? = null,
    imageHeight: Dp = 150.dp,
    onClick: (() -> Unit)? = null
) {
    AppMediaCard(modifier = modifier, onClick = onClick) {
        Box {
            AppImage(
                uri = coverUri,
                contentDescription = name,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(imageHeight),
                shape = RoundedCornerShape(0.dp),
                placeholderIcon = Icons.Default.Place,
                placeholderTint = AppThemeExtended.colors.destination,
                placeholderBackground = AppThemeExtended.colors.destinationSoft
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            0.5f to Color.Transparent,
                            1f to Color(0x99000000)
                        )
                    )
            )
            TripStatusBadge(
                status = status,
                filled = true,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            )
            if (countdown != null) {
                Surface(
                    shape = AppThemeExtended.metrics.badgeShape,
                    color = Color(0xB3000000),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                ) {
                    Text(
                        text = countdown,
                        style = AppThemeExtended.text.badge,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Column(Modifier.padding(AppThemeExtended.metrics.cardPadding)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = dateRange,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (summary != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppThemeExtended.colors.textFaint
                )
            }
            if (progressFraction != null) {
                Spacer(Modifier.height(12.dp))
                AppProgressBar(
                    fraction = progressFraction,
                    color = AppThemeExtended.colors.statusActive
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Activities
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * One itinerary entry: when, what, where, and what state it's in.
 *
 * The time sits in its own left column in tabular numerals so a day's worth of these forms
 * a readable schedule down the edge of the screen rather than a ragged stack of sentences.
 */
@Composable
fun ActivityCard(
    title: String,
    time: String,
    type: EventType,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    status: EventStatus? = null,
    imageUri: String? = null,
    trailing: String? = null,
    onClick: (() -> Unit)? = null
) {
    AppCard(modifier = modifier, onClick = onClick, contentPadding = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(58.dp)
                    .clip(AppThemeExtended.metrics.imageShape)
            ) {
                if (imageUri.isNullOrBlank()) {
                    CategoryPlaceholder(type = type, modifier = Modifier.fillMaxSize(), iconSize = 24.dp)
                } else {
                    AppImage(
                        uri = imageUri,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        placeholderIcon = type.icon,
                        placeholderTint = type.color,
                        placeholderBackground = type.softColor
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = time,
                        style = AppThemeExtended.text.time,
                        color = type.color
                    )
                    if (status != null) {
                        Spacer(Modifier.width(8.dp))
                        StatusBadge(status = status)
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.labelMedium,
                    color = AppThemeExtended.colors.textFaint
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Trains
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * A booked train: number and name, the two ends of the journey with their times, and the
 * seat you're actually in.
 *
 * Laid out as origin — arrow — destination because that is the shape of the information;
 * a label/value list of the same facts takes twice the height and reads half as fast.
 *
 * [color], [borderColor] and [borderWidth] are exposed because two other screens draw this same
 * card: the itinerary rings the stop the traveller is on right now, and Home nests it inside its
 * next-up card over a photograph, where a second opaque surface would simply hide the picture.
 */
@Composable
fun TrainCard(
    trainNumber: String,
    trainName: String,
    originName: String,
    originTime: String,
    destinationName: String,
    destinationTime: String,
    modifier: Modifier = Modifier,
    dateLabel: String? = null,
    duration: String? = null,
    seatSummary: String? = null,
    statusLabel: String? = null,
    statusTone: BadgeTone = BadgeTone.INFO,
    color: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    borderWidth: Dp = AppThemeExtended.metrics.borderWidth,
    elevation: Dp = AppThemeExtended.metrics.cardElevation,
    onClick: (() -> Unit)? = null
) {
    val colors = AppThemeExtended.colors

    AppCard(
        modifier = modifier,
        onClick = onClick,
        color = color,
        borderColor = borderColor,
        borderWidth = borderWidth,
        elevation = elevation
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(10.dp), color = colors.journeySoft) {
                    Icon(
                        EventType.JOURNEY.icon,
                        contentDescription = null,
                        tint = colors.journey,
                        modifier = Modifier
                            .padding(7.dp)
                            .size(18.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = trainName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = trainNumber,
                        style = AppThemeExtended.text.time,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (statusLabel != null) {
                Spacer(Modifier.width(8.dp))
                StatusBadge(label = statusLabel, tone = statusTone)
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = originTime,
                    style = AppThemeExtended.text.timeLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = originName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 10.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "to",
                    tint = colors.accent,
                    modifier = Modifier.size(18.dp)
                )
                if (duration != null) {
                    Text(
                        text = duration,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textFaint
                    )
                }
            }
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = destinationTime,
                    style = AppThemeExtended.text.timeLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = destinationName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (dateLabel != null || seatSummary != null) {
            Spacer(Modifier.height(12.dp))
            AppDivider()
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateLabel.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (seatSummary != null) {
                    Text(
                        text = seatSummary,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * One row of the ticket stub, already resolved to the strings that go on screen.
 *
 * A plain record rather than the domain passenger, for the reason this file gives at the top:
 * deciding that a missing berth reads as "No berth allotted", or that a confirmed booking needs
 * no badge, is the screen's judgement about its own copy — not the card's.
 *
 * [serialLabel] is the chart number the railway prints, so the list on screen is in the order
 * the TTE reads it out.
 */
data class TicketPassenger(
    val serialLabel: String,
    val name: String,
    /** Age and gender, or whatever part of them is known. Blank is fine. */
    val detail: String = "",
    /** `"S4 · 8"`, `"Waitlist 24 · GNWL"`, or the sentence that stands in for either. */
    val seat: String = "",
    val statusLabel: String? = null,
    val statusTone: BadgeTone = BadgeTone.POSITIVE
)

/**
 * The travel document: reservation details in a shape that reads as a ticket.
 *
 * The perforation is the whole idea — it splits the card into "which train" above and "who
 * is travelling, in which berth" below, which is exactly how a real reservation is checked.
 *
 * [passengers] is a list because a PNR covers a party and the railway allots berth by berth:
 * two people on this one ticket routinely sit apart, sometimes in different coaches. A single
 * coach-and-seat pair could only ever be right for one of them.
 */
@Composable
fun TicketCard(
    trainNumber: String,
    trainName: String,
    originCode: String,
    originName: String,
    originTime: String,
    destinationCode: String,
    destinationName: String,
    destinationTime: String,
    dateLabel: String,
    modifier: Modifier = Modifier,
    pnr: String? = null,
    travelClass: String? = null,
    platform: String? = null,
    passengers: List<TicketPassenger> = emptyList(),
    bookingStatusLabel: String? = null,
    bookingStatusTone: BadgeTone = BadgeTone.POSITIVE
) {
    val colors = AppThemeExtended.colors
    val metrics = AppThemeExtended.metrics
    val clipboardManager = LocalClipboardManager.current

    AppCard(
        modifier = modifier,
        shape = metrics.cardShapeLarge,
        contentPadding = PaddingValues(0.dp)
    ) {
        // Header: the train, with a balanced compact header and prominent journey section
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.accent)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Eyebrow("TRAIN", color = Color.White.copy(alpha = 0.8f))
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = if (trainName.contains(trainNumber)) trainName else "$trainName ($trainNumber)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (bookingStatusLabel != null) {
                    Spacer(Modifier.width(8.dp))
                    StatusBadge(
                        label = bookingStatusLabel,
                        tone = bookingStatusTone,
                        filled = true
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Primary Journey Section: BOARDING vs ARRIVAL with prominent times and centered arrow
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Eyebrow("BOARDING", color = Color.White.copy(alpha = 0.8f))
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = originCode,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Text(
                        text = originName,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = originTime,
                        style = AppThemeExtended.text.timeLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                        color = Color.White
                    )
                }

                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "to",
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .size(22.dp)
                )

                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Eyebrow("ARRIVAL", color = Color.White.copy(alpha = 0.8f))
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = destinationCode,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Text(
                        text = destinationName,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = destinationTime,
                        style = AppThemeExtended.text.timeLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                        color = Color.White
                    )
                }
            }
        }

        TicketPerforation(
            notchColor = MaterialTheme.colorScheme.background,
            lineColor = MaterialTheme.colorScheme.outline
        )

        // Stub: who is travelling and where they sit.
        Column(Modifier.padding(AppThemeExtended.metrics.cardPadding)) {
            if (pnr != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Eyebrow("PNR")
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = pnr,
                            style = AppThemeExtended.text.timeLarge.copy(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = { clipboardManager.setText(AnnotatedString(pnr)) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Outlined.ContentCopy,
                            contentDescription = "Copy PNR",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            // 3 Independent Columns: DATE | CLASS | PLATFORM with defined layout area and separation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1.3f)) {
                    Eyebrow("DATE")
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = dateLabel,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(0.85f)) {
                    Eyebrow("CLASS")
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = travelClass ?: "—",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(0.85f)) {
                    Eyebrow("PLATFORM")
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = platform ?: "—",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (passengers.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                AppDivider()
                Spacer(Modifier.height(12.dp))
                Eyebrow(if (passengers.size == 1) "PASSENGER" else "PASSENGERS")
                passengers.forEach { passenger ->
                    Spacer(Modifier.height(10.dp))
                    TicketPassengerRow(passenger)
                }
            }
        }
    }
}

/**
 * A passenger and their berth, laid out the way the chart is: number, person, place.
 */
@Composable
private fun TicketPassengerRow(passenger: TicketPassenger) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = passenger.serialLabel,
            style = AppThemeExtended.text.time,
            color = AppThemeExtended.colors.textFaint,
            modifier = Modifier.width(22.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = passenger.name,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (passenger.detail.isNotBlank()) {
                Spacer(Modifier.height(1.dp))
                Text(
                    text = passenger.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            if (passenger.seat.isNotBlank()) {
                Text(
                    text = passenger.seat,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (passenger.statusLabel != null) {
                Spacer(Modifier.height(2.dp))
                StatusBadge(label = passenger.statusLabel, tone = passenger.statusTone)
            }
        }
    }
}

/**
 * The dashed tear line, with a notch bitten out of each edge.
 *
 * The notches are drawn in the *background* colour so they read as holes in the card rather
 * than dots on it — which only works because they are painted at the card's edge.
 */
@Composable
private fun TicketPerforation(
    notchColor: Color,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier
            .fillMaxWidth()
            .height(20.dp)
    ) {
        val notchRadius = size.height / 2
        val midY = size.height / 2

        drawLine(
            color = lineColor,
            start = Offset(notchRadius + 8.dp.toPx(), midY),
            end = Offset(size.width - notchRadius - 8.dp.toPx(), midY),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(6.dp.toPx(), 5.dp.toPx()),
                0f
            )
        )
        drawCircle(color = notchColor, radius = notchRadius, center = Offset(0f, midY))
        drawCircle(color = notchColor, radius = notchRadius, center = Offset(size.width, midY))
    }
}

@Composable
private fun TicketField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    wide: Boolean = false
) {
    Column(modifier) {
        Eyebrow(label)
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = if (wide) {
                AppThemeExtended.text.timeLarge
            } else {
                MaterialTheme.typography.titleMedium
            },
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Stays
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Accommodation: the photograph, the name, and the two times that matter — check-in and
 * check-out — side by side.
 *
 * [borderColor] and [borderWidth] are exposed because the itinerary draws the same card and
 * needs to ring the stop the traveller is inside right now.
 */
@Composable
fun HotelCard(
    name: String,
    modifier: Modifier = Modifier,
    imageUri: String? = null,
    address: String? = null,
    checkInLabel: String? = null,
    checkOutLabel: String? = null,
    nightsLabel: String? = null,
    statusLabel: String? = null,
    statusTone: BadgeTone = BadgeTone.POSITIVE,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    borderWidth: Dp = AppThemeExtended.metrics.borderWidth,
    onClick: (() -> Unit)? = null
) {
    val colors = AppThemeExtended.colors

    AppCard(
        modifier = modifier,
        onClick = onClick,
        borderColor = borderColor,
        borderWidth = borderWidth,
        contentPadding = PaddingValues(0.dp)
    ) {
        Box {
            if (!imageUri.isNullOrBlank()) {
                PhotoBackdrop(
                    uri = imageUri,
                    modifier = Modifier.matchParentSize(),
                    overlayAlpha = 0.60f
                )
            }
            Column(Modifier.padding(AppThemeExtended.metrics.cardPadding)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.staySoft
                        ) {
                            Icon(
                                Icons.Default.Hotel,
                                contentDescription = null,
                                tint = colors.stay,
                                modifier = Modifier
                                    .padding(6.dp)
                                    .size(16.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "STAY",
                            style = AppThemeExtended.text.eyebrow,
                            color = colors.stay
                        )
                    }
                    if (statusLabel != null) {
                        Spacer(Modifier.width(8.dp))
                        StatusBadge(
                            label = statusLabel,
                            tone = statusTone
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (address != null) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Place,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = address,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (checkInLabel != null || checkOutLabel != null) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = AppThemeExtended.metrics.controlShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (imageUri.isNullOrBlank()) 1f else 0.85f)
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Eyebrow("Check-in")
                                Text(
                                    text = checkInLabel ?: "—",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (nightsLabel != null) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    Text(
                                        text = nightsLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.textFaint
                                    )
                                }
                            }
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                Eyebrow("Check-out")
                                Text(
                                    text = checkOutLabel ?: "—",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Places
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * A place worth going to: photograph, name, category, and the two numbers people actually
 * decide on — how well it's rated and how far away it is.
 *
 * The save toggle is on the image, not in a menu. Saving a place is the most common thing
 * anyone does on a screen like this, and it should cost one tap.
 */
@Composable
fun PlaceCard(
    name: String,
    modifier: Modifier = Modifier,
    imageUri: String? = null,
    category: String? = null,
    rating: Double? = null,
    distanceLabel: String? = null,
    openingHours: String? = null,
    isSaved: Boolean = false,
    onToggleSaved: (() -> Unit)? = null,
    statusLabel: String? = null,
    statusTone: BadgeTone = BadgeTone.POSITIVE,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    borderWidth: Dp = AppThemeExtended.metrics.borderWidth,
    onClick: (() -> Unit)? = null
) {
    val colors = AppThemeExtended.colors

    AppCard(
        modifier = modifier,
        onClick = onClick,
        borderColor = borderColor,
        borderWidth = borderWidth,
        contentPadding = PaddingValues(12.dp)
    ) {
        Row {
            Box(
                Modifier
                    .size(86.dp)
                    .clip(AppThemeExtended.metrics.imageShape)
            ) {
                AppImage(
                    uri = imageUri,
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize(),
                    placeholderIcon = Icons.Default.Place,
                    placeholderTint = colors.destination,
                    placeholderBackground = colors.destinationSoft
                )
                if (onToggleSaved != null) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xB3000000),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onToggleSaved)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (isSaved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (isSaved) "Remove from saved" else "Save place",
                                tint = if (isSaved) colors.danger else Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (statusLabel != null) {
                        Spacer(Modifier.width(6.dp))
                        StatusBadge(label = statusLabel, tone = statusTone)
                    }
                }
                if (category != null) {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.destination
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (rating != null) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = colors.warning,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = String.format("%.1f", rating),
                            style = AppThemeExtended.text.time,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (distanceLabel != null) {
                        if (rating != null) {
                            Text(
                                text = "  ·  ",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textFaint
                            )
                        }
                        Text(
                            text = distanceLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (openingHours != null) {
                    Text(
                        text = openingHours,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Quick actions
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * A shortcut tile: icon in a tinted square, one short label.
 *
 * Sized to sit four-across on a 411dp screen, which is why the label is capped at two lines
 * and the icon square is fixed rather than proportional.
 */
@Composable
fun QuickActionCard(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = AppThemeExtended.colors.accent,
    accentSoft: Color = AppThemeExtended.colors.accentSoft
) {
    AppCard(
        modifier = modifier,
        onClick = onClick,
        fillWidth = false,
        contentPadding = PaddingValues(vertical = 14.dp, horizontal = 8.dp)
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(shape = RoundedCornerShape(12.dp), color = accentSoft) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(20.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
