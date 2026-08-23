package com.tripcompanion.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tripcompanion.app.domain.model.EventStatus
import com.tripcompanion.app.domain.model.EventType
import java.time.LocalDate
import com.tripcompanion.app.ui.theme.AppThemeExtended

/**
 * The itinerary's structural devices.
 *
 * The vertical rail here encodes something true: an itinerary *is* an ordered sequence in
 * time, so a connected line with one node per stop is the honest diagram of it. That is why
 * this file exists rather than a list of cards — and equally why nothing else in the app uses
 * numbered markers, since nothing else in the app is a sequence.
 */

// ═══════════════════════════════════════════════════════════════════════════════
//  Day selection
// ═══════════════════════════════════════════════════════════════════════════════

/** One day in the selector strip. `label` is the weekday, `dayOfMonth` the figure. */
data class DayTab(
    val date: LocalDate,
    val label: String,
    val dayOfMonth: String,
    val dayNumber: Int,
    val eventCount: Int = 0,
    val isToday: Boolean = false
)

/**
 * The horizontal day strip above an itinerary.
 *
 * Each tab carries its weekday, its date and how many events are on it, because "Day 3" on
 * its own is not enough to navigate by — a person thinks in *Thursday the 24th*, and the
 * count is what tells them whether that day is planned yet.
 */
@Composable
fun DaySelector(
    days: List<DayTab>,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 0.dp)
) {
    val colors = AppThemeExtended.colors
    val metrics = AppThemeExtended.metrics

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(days, key = { it.date.toString() }) { day ->
            val isSelected = day.date == selected
            val background by animateColorAsState(
                targetValue = if (isSelected) colors.accent else MaterialTheme.colorScheme.surface,
                animationSpec = tween(200),
                label = "day-bg"
            )
            val contentColor = if (isSelected) {
                Color.White
            } else {
                MaterialTheme.colorScheme.onSurface
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = background,
                border = BorderStroke(
                    metrics.borderWidth,
                    if (isSelected) colors.accent else MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .width(66.dp)
                    .clickable { onSelect(day.date) }
            ) {
                Column(
                    Modifier.padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = day.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) {
                            Color.White.copy(alpha = 0.85f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        text = day.dayOfMonth,
                        style = AppThemeExtended.text.timeLarge,
                        color = contentColor
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = if (day.eventCount == 1) "1 stop" else "${day.eventCount} stops",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) {
                            Color.White.copy(alpha = 0.85f)
                        } else {
                            colors.textFaint
                        },
                        maxLines = 1
                    )
                    if (day.isToday) {
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) Color.White else colors.statusActive)
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Day plan strip
// ═══════════════════════════════════════════════════════════════════════════════

/** One beat in the compact day strip on Home. */
data class PlanStepItem(
    val icon: ImageVector,
    val label: String,
    val time: String,
    val color: Color,
    val softColor: Color,
    val isCurrent: Boolean = false
)

/**
 * Today at a glance: the day's stops as a horizontal chain.
 *
 * This is a summary, not a list — it never scrolls vertically and it is never the place you
 * edit from. The current stop is the one drawn filled, so a glance answers "where am I in
 * the day" without reading a single time.
 */
@Composable
fun DayPlanStrip(
    steps: List<PlanStepItem>,
    modifier: Modifier = Modifier,
    onStepClick: ((Int) -> Unit)? = null
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.Top
    ) {
        itemsIndexed(steps) { index, step ->
            Row(verticalAlignment = Alignment.Top) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(78.dp)
                        .then(
                            if (onStepClick != null) {
                                Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onStepClick(index) }
                            } else {
                                Modifier
                            }
                        )
                        .padding(vertical = 4.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (step.isCurrent) step.color else step.softColor,
                        border = if (step.isCurrent) {
                            null
                        } else {
                            BorderStroke(AppThemeExtended.metrics.borderWidth, step.color.copy(alpha = 0.35f))
                        },
                        modifier = Modifier.size(AppThemeExtended.metrics.timelineNodeSize)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                step.icon,
                                contentDescription = null,
                                tint = if (step.isCurrent) Color.White else step.color,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = step.time,
                        style = AppThemeExtended.text.time,
                        color = if (step.isCurrent) {
                            step.color
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1
                    )
                    Text(
                        text = step.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
                if (index < steps.lastIndex) {
                    // The connector sits at the node's vertical centre, so the chain reads
                    // as one line rather than a row of separate tiles.
                    Box(
                        Modifier
                            .padding(top = AppThemeExtended.metrics.timelineNodeSize / 2 + 4.dp)
                            .width(14.dp)
                            .height(AppThemeExtended.metrics.timelineRailWidth)
                            .background(MaterialTheme.colorScheme.outline)
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Vertical timeline
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * One stop on the itinerary's vertical rail.
 *
 * Draws its own rail segments rather than relying on a parent to draw a continuous line,
 * which is what lets this be used inside a `LazyColumn`: the list can recycle any row and the
 * rail still joins up, because each row owns the piece of line above and below its own node.
 *
 * `isFirst` suppresses the segment above and `isLast` the segment below, so the line starts
 * and stops at the day's real boundaries instead of running off into the padding.
 */
@Composable
fun TimelineItem(
    time: String,
    type: EventType,
    modifier: Modifier = Modifier,
    status: EventStatus? = null,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    nodeSize: Dp = AppThemeExtended.metrics.timelineNodeSize,
    content: @Composable ColumnScope.() -> Unit
) {
    val metrics = AppThemeExtended.metrics
    val railColor = MaterialTheme.colorScheme.outline
    val nodeColor = type.color
    // "Done" drains the node's colour so a completed day recedes and the live stop stands
    // out — the rail's job is to show progress, not to shout every stop equally.
    val isSpent = status == EventStatus.COMPLETED || status == EventStatus.SKIPPED

    // `IntrinsicSize.Min` is what makes the rail work. The rail's lower segment fills the
    // leftover vertical space with `weight`, and weight needs a bounded height — without
    // this the row would wrap its content, the segment would measure zero, and the line
    // would break between every stop.
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(nodeSize)
        ) {
            Box(
                Modifier
                    .width(metrics.timelineRailWidth)
                    .height(if (isFirst) 0.dp else 8.dp)
                    .background(railColor)
            )
            Surface(
                shape = CircleShape,
                color = if (isSpent) MaterialTheme.colorScheme.surfaceVariant else type.softColor,
                border = BorderStroke(
                    metrics.borderWidthStrong,
                    if (isSpent) railColor else nodeColor
                ),
                modifier = Modifier.size(nodeSize)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        type.icon,
                        contentDescription = null,
                        tint = if (isSpent) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            nodeColor
                        },
                        modifier = Modifier.size(nodeSize * 0.46f)
                    )
                }
            }
            if (!isLast) {
                Box(
                    Modifier
                        .weight(1f, fill = true)
                        .width(metrics.timelineRailWidth)
                        .background(railColor)
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(
            Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else metrics.rowGap)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = time,
                    style = AppThemeExtended.text.time,
                    color = if (isSpent) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        nodeColor
                    }
                )
                if (status != null) {
                    Spacer(Modifier.width(8.dp))
                    StatusBadge(status = status)
                }
            }
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Route timeline
// ═══════════════════════════════════════════════════════════════════════════════

/** One station on a train's route. */
data class RouteStop(
    val name: String,
    val code: String,
    val scheduledLabel: String,
    val actualLabel: String? = null,
    val distanceLabel: String? = null,
    val delayLabel: String? = null,
    val isDeparted: Boolean = false,
    val isCurrent: Boolean = false,
    val dayLabel: String? = null
)

/**
 * A train's route as a vertical rail, with the section already travelled drawn solid and
 * the rest drawn quiet.
 *
 * The current station gets a ring rather than a bigger dot: a size change makes the rail
 * bend, and the point of a rail is that it doesn't.
 */
@Composable
fun RouteTimeline(
    stops: List<RouteStop>,
    modifier: Modifier = Modifier,
    onStopClick: ((RouteStop) -> Unit)? = null
) {
    val colors = AppThemeExtended.colors
    val metrics = AppThemeExtended.metrics

    Column(modifier.fillMaxWidth()) {
        stops.forEachIndexed { index, stop ->
            val isFirst = index == 0
            val isLast = index == stops.lastIndex
            // A segment is "travelled" if the stop below it has been departed.
            val railAbove = if (stop.isDeparted || stop.isCurrent) colors.journey else MaterialTheme.colorScheme.outline
            val railBelow = if (stop.isDeparted) colors.journey else MaterialTheme.colorScheme.outline

            Row(
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .then(
                        if (onStopClick != null) {
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onStopClick(stop) }
                        } else {
                            Modifier
                        }
                    )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(24.dp)
                ) {
                    Box(
                        Modifier
                            .width(metrics.timelineRailWidth)
                            .height(if (isFirst) 0.dp else 10.dp)
                            .background(railAbove)
                    )
                    when {
                        stop.isCurrent -> Box(
                            Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(colors.journey.copy(alpha = 0.28f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(colors.journey)
                            )
                        }

                        stop.isDeparted -> Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(colors.journey)
                        )

                        else -> Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(2.dp)
                        ) {
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.outline)
                            )
                        }
                    }
                    if (!isLast) {
                        Box(
                            Modifier
                                .weight(1f, fill = true)
                                .width(metrics.timelineRailWidth)
                                .background(railBelow)
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = if (isLast) 0.dp else 16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stop.name,
                            style = if (stop.isCurrent) {
                                MaterialTheme.typography.titleMedium
                            } else {
                                MaterialTheme.typography.bodyLarge
                            },
                            color = if (stop.isDeparted && !stop.isCurrent) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stop.code,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textFaint
                            )
                            if (stop.distanceLabel != null) {
                                Text(
                                    text = "  ·  ${stop.distanceLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textFaint
                                )
                            }
                            if (stop.dayLabel != null) {
                                Text(
                                    text = "  ·  ${stop.dayLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textFaint
                                )
                            }
                        }
                        if (stop.delayLabel != null) {
                            Spacer(Modifier.height(4.dp))
                            StatusBadge(
                                label = stop.delayLabel,
                                tone = BadgeTone.WARNING,
                                uppercase = false
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = stop.actualLabel ?: stop.scheduledLabel,
                            style = AppThemeExtended.text.time,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        // Only shown when it differs, so an on-time train is not cluttered
                        // with the same time printed twice.
                        if (stop.actualLabel != null && stop.actualLabel != stop.scheduledLabel) {
                            Text(
                                text = stop.scheduledLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textFaint
                            )
                        }
                    }
                }
            }
        }
    }
}
