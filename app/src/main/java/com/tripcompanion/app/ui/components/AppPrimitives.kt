package com.tripcompanion.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tripcompanion.app.domain.model.EventStatus
import com.tripcompanion.app.domain.model.TripStatus
import com.tripcompanion.app.ui.theme.AppThemeExtended

// ═══════════════════════════════════════════════════════════════════════════════
//  Containers
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * The app's one container: a white (or raised, in dark) rounded surface with a hairline
 * border and a soft shadow.
 *
 * Elevation is dropped to zero in dark mode. A drop shadow against `#0B0C0F` is invisible,
 * so depth there comes from the surface being a step lighter than the background plus the
 * border — which is why this decision lives here once rather than in every screen.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    borderWidth: Dp = AppThemeExtended.metrics.borderWidth,
    shape: Shape = AppThemeExtended.metrics.cardShape,
    elevation: Dp = AppThemeExtended.metrics.cardElevation,
    fillWidth: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(AppThemeExtended.metrics.cardPadding),
    content: @Composable ColumnScope.() -> Unit
) {
    val drawnElevation = if (AppThemeExtended.colors.isDark) 0.dp else elevation

    Surface(
        shape = shape,
        color = color,
        border = if (borderWidth > 0.dp) BorderStroke(borderWidth, borderColor) else null,
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .then(
                if (drawnElevation > 0.dp) {
                    Modifier.shadow(drawnElevation, shape, clip = false)
                } else {
                    Modifier
                }
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

/**
 * A card with no padding, for the ones that start with a full-bleed photograph.
 *
 * Clips its content so an image reaches the rounded corner instead of squaring it off.
 */
@Composable
fun AppMediaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    borderWidth: Dp = AppThemeExtended.metrics.borderWidth,
    shape: Shape = AppThemeExtended.metrics.cardShape,
    content: @Composable ColumnScope.() -> Unit
) {
    AppCard(
        modifier = modifier,
        onClick = onClick,
        borderColor = borderColor,
        borderWidth = borderWidth,
        shape = shape,
        contentPadding = PaddingValues(0.dp),
        content = {
            Column(Modifier.clip(shape), content = content)
        }
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Section furniture
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Names a section, in sentence case, with an optional trailing action.
 *
 * This is the app's main structural device. It is deliberately not uppercase: a screen
 * where every header shouts has no hierarchy left to spend.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (actionLabel != null && onActionClick != null) {
            Spacer(Modifier.width(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(AppThemeExtended.metrics.chipShape)
                    .clickable(onClick = onActionClick)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = AppThemeExtended.colors.accent
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = AppThemeExtended.colors.accent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Small uppercase metadata — "NEXT UP", "DAY 2 OF 5", a field label above a value.
 *
 * The only place outside status badges where uppercase is allowed, and it is capped at
 * a few words by design.
 */
@Composable
fun Eyebrow(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Text(
        text = text.uppercase(),
        style = AppThemeExtended.text.eyebrow,
        color = color,
        modifier = modifier
    )
}

/** A hairline rule. */
@Composable
fun AppDivider(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.outlineVariant
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(AppThemeExtended.metrics.dividerWidth)
            .background(color)
    )
}

/**
 * Label-and-value row, used wherever a screen lists facts — an address, a duration,
 * a coach number.
 *
 * The label is sentence case. Uppercasing every fact label was the old identity's tell.
 */
@Composable
fun MetaRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    valueStyle: TextStyle? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = valueStyle ?: MaterialTheme.typography.bodyMedium,
            color = valueColor,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Buttons
// ═══════════════════════════════════════════════════════════════════════════════

/** Matches the prototype's `.btn:disabled`, which drops the whole control to 40%. */
private const val DISABLED_ALPHA = 0.4f

/**
 * The screen's main action — save, create, confirm, book.
 *
 * Blue fill, white label, sentence case. A disabled button keeps its shape, its footprint
 * and its position and only loses presence, so a keystroke that flips validity cannot
 * shift the layout under the thumb already reaching for it.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    icon: ImageVector? = null,
    fill: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    val metrics = AppThemeExtended.metrics

    Surface(
        shape = metrics.buttonShape,
        color = fill,
        modifier = modifier
            .fillMaxWidth()
            .height(metrics.controlHeight)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .then(if (enabled && !busy) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (busy) {
                CircularProgressIndicator(
                    color = contentColor,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColor
                    )
                }
            }
        }
    }
}

/**
 * The alternative to the main action — outlined, never a second fill.
 *
 * Two filled buttons side by side make neither of them the answer.
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    borderColor: Color = MaterialTheme.colorScheme.outline
) {
    val metrics = AppThemeExtended.metrics

    Surface(
        shape = metrics.buttonShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(metrics.borderWidth, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .height(metrics.controlHeight)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor
                )
            }
        }
    }
}

/**
 * A round icon button. Two grounds: `translucent` for buttons that sit on a photograph,
 * where a solid chip would fight the image, and the default surface elsewhere.
 */
@Composable
fun AppIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    background: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color? = MaterialTheme.colorScheme.outline,
    size: Dp = 40.dp
) {
    Surface(
        shape = CircleShape,
        color = background,
        border = borderColor?.let { BorderStroke(AppThemeExtended.metrics.borderWidth, it) },
        modifier = modifier
            .size(size)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(size * 0.5f)
            )
        }
    }
}

/** A quiet inline action: "See all", "Change", "Clear". */
@Composable
fun TextActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = AppThemeExtended.colors.accent,
    icon: ImageVector? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(AppThemeExtended.metrics.chipShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Selection controls
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Mutually exclusive views of one list — Upcoming / Ongoing / Completed.
 *
 * A segmented control rather than chips, because these options partition the list: exactly
 * one is always active and picking one deselects the others.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = AppThemeExtended.metrics.chipShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(4.dp)) {
            options.forEach { option ->
                val isSelected = option.equals(selected, ignoreCase = true)
                val background by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        Color.Transparent
                    },
                    animationSpec = tween(220),
                    label = "segment-bg"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(AppThemeExtended.metrics.chipShape)
                        .background(background)
                        .clickable { onSelect(option) }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * An independent filter — a category, a tag. Several can be on at once, which is the
 * difference from [SegmentedControl].
 */
@Composable
fun AppFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Color = AppThemeExtended.colors.accent
) {
    Surface(
        shape = AppThemeExtended.metrics.chipShape,
        color = if (selected) AppThemeExtended.colors.accentSoft else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            AppThemeExtended.metrics.borderWidth,
            if (selected) accent else MaterialTheme.colorScheme.outline
        ),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** A horizontally scrolling row of [AppFilterChip]s that doesn't clip on a narrow screen. */
@Composable
fun FilterChipRow(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 0.dp),
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * A tappable field whose value comes from somewhere other than the keyboard — a date
 * picker, a time picker, the map.
 *
 * Reads as a labelled readout rather than a text input, which is honest: tapping it opens
 * a picker, and a control that looks typable but isn't is a small betrayal every time.
 *
 * `maxLines = 2` is deliberate. The previous single-line version truncated a perfectly
 * ordinary date to "22 August 20…" on a 411dp screen; a wrapped date is better than a
 * date you cannot read.
 */
@Composable
fun PickerField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    valueStyle: TextStyle? = null
) {
    AppCard(
        modifier = modifier,
        onClick = onClick,
        contentPadding = PaddingValues(
            horizontal = AppThemeExtended.metrics.cardPadding,
            vertical = 12.dp
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = value,
                    style = valueStyle ?: MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (icon != null) {
                Spacer(Modifier.width(10.dp))
                Icon(
                    icon,
                    contentDescription = null,
                    tint = AppThemeExtended.colors.accent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/** Rounded search field with a leading magnifier and a clear affordance once typed in. */
@Composable
fun AppSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    enabled: Boolean = true
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
        shape = AppThemeExtended.metrics.chipShape,
        textStyle = MaterialTheme.typography.bodyLarge,
        placeholder = {
            Text(
                placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = AppThemeExtended.colors.textFaint
            )
        },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = if (query.isNotEmpty()) {
            {
                Icon(
                    Icons.Default.Clear,
                    contentDescription = "Clear search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onQueryChange("") }
                )
            }
        } else {
            null
        },
        keyboardOptions = keyboardOptions,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Badges and progress
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * The one status vocabulary (§28).
 *
 * A tone is a meaning, not a colour: a screen asks for [BadgeTone.POSITIVE] and gets
 * whichever green reads correctly in the active mode. Every badge in the app — event
 * status, trip status, on-time, delayed, confirmed — resolves through here, so the same
 * state can never appear in two colours on two screens.
 */
enum class BadgeTone { POSITIVE, WARNING, NEGATIVE, INFO, NEUTRAL }

@Composable
fun StatusBadge(
    label: String,
    tone: BadgeTone,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    uppercase: Boolean = true,
    icon: ImageVector? = null
) {
    val colors = AppThemeExtended.colors
    val (solid, soft, onSoft) = when (tone) {
        BadgeTone.POSITIVE -> Triple(colors.success, colors.successSoft, colors.successText)
        BadgeTone.WARNING -> Triple(colors.warning, colors.warningSoft, colors.warningText)
        BadgeTone.NEGATIVE -> Triple(colors.danger, colors.dangerSoft, colors.dangerText)
        BadgeTone.INFO -> Triple(colors.info, colors.infoSoft, colors.infoText)
        BadgeTone.NEUTRAL -> Triple(
            colors.statusCompleted,
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Surface(
        shape = AppThemeExtended.metrics.badgeShape,
        color = if (filled) solid else soft,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (filled) contrastOn(solid) else onSoft,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = if (uppercase) label.uppercase() else label,
                style = AppThemeExtended.text.badge,
                color = if (filled) contrastOn(solid) else onSoft
            )
        }
    }
}

/** Status of one event, worded and toned from a single place. */
@Composable
fun StatusBadge(
    status: EventStatus,
    modifier: Modifier = Modifier,
    filled: Boolean = false
) {
    StatusBadge(
        label = statusLabel(status),
        tone = statusTone(status),
        modifier = modifier,
        filled = filled
    )
}

/**
 * The same badge for a whole trip rather than one event.
 *
 * A trip's status and an event's status are different enumerations answering different
 * questions ("is this trip cancelled" versus "is this stop running right now"), but they
 * land side by side and must not look like two kinds of badge — so they share one drawing
 * and differ only in their words.
 */
@Composable
fun TripStatusBadge(
    status: TripStatus,
    modifier: Modifier = Modifier,
    filled: Boolean = false
) {
    StatusBadge(
        label = tripStatusLabel(status),
        tone = tripStatusTone(status),
        modifier = modifier,
        filled = filled
    )
}

/** The one mapping from event status to a tone. */
fun statusTone(status: EventStatus): BadgeTone = when (status) {
    EventStatus.ACTIVE -> BadgeTone.POSITIVE
    EventStatus.STARTING_SOON -> BadgeTone.WARNING
    EventStatus.UPCOMING -> BadgeTone.INFO
    EventStatus.COMPLETED -> BadgeTone.NEUTRAL
    EventStatus.SKIPPED -> BadgeTone.NEUTRAL
    EventStatus.MISSED -> BadgeTone.NEGATIVE
}

/** The one mapping from event status to a colour, for markers and rails. */
@Composable
fun statusColor(status: EventStatus): Color {
    val colors = AppThemeExtended.colors
    return when (status) {
        EventStatus.ACTIVE -> colors.statusActive
        EventStatus.STARTING_SOON -> colors.statusStartingSoon
        EventStatus.UPCOMING -> colors.statusUpcoming
        EventStatus.COMPLETED -> colors.statusCompleted
        EventStatus.SKIPPED -> colors.statusSkipped
        EventStatus.MISSED -> colors.statusMissed
    }
}

/**
 * Human wording for a status. The enum name is a constant, not copy: "STARTING SOON"
 * with an underscore stripped is the kind of thing that leaks the schema onto the screen.
 */
fun statusLabel(status: EventStatus): String = when (status) {
    EventStatus.ACTIVE -> "Now"
    EventStatus.STARTING_SOON -> "Starting soon"
    EventStatus.UPCOMING -> "Upcoming"
    EventStatus.COMPLETED -> "Done"
    EventStatus.SKIPPED -> "Skipped"
    EventStatus.MISSED -> "Missed"
}

/**
 * A trip's tone. Planning takes the informational blue rather than a warning: planning is
 * the normal condition of a trip in this app, and reads as the subject of the screen.
 */
fun tripStatusTone(status: TripStatus): BadgeTone = when (status) {
    TripStatus.PLANNING -> BadgeTone.INFO
    TripStatus.ACTIVE -> BadgeTone.POSITIVE
    TripStatus.COMPLETED -> BadgeTone.NEUTRAL
    TripStatus.CANCELLED -> BadgeTone.NEGATIVE
}

@Composable
fun tripStatusColor(status: TripStatus): Color {
    val colors = AppThemeExtended.colors
    return when (status) {
        TripStatus.PLANNING -> colors.info
        TripStatus.ACTIVE -> colors.statusActive
        TripStatus.COMPLETED -> colors.statusCompleted
        TripStatus.CANCELLED -> colors.danger
    }
}

/** Human wording for a trip's status. */
fun tripStatusLabel(status: TripStatus): String = when (status) {
    TripStatus.PLANNING -> "Planning"
    TripStatus.ACTIVE -> "Travelling"
    TripStatus.COMPLETED -> "Done"
    TripStatus.CANCELLED -> "Cancelled"
}

/**
 * Readable text on an arbitrary fill. Uses relative luminance rather than a lookup,
 * because the same call has to work for a light green and a dark red.
 */
private fun contrastOn(background: Color): Color =
    if (background.luminanceApprox() > 0.5f) Color.Black else Color.White

private fun Color.luminanceApprox(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

/**
 * "In 3 days", "Starts in 2h 10m" — the pill that answers "when" without arithmetic.
 *
 * Takes already-formatted text. Turning a duration into words is
 * [com.tripcompanion.app.core.util.DateTimeUtils]' job, and it is unit-tested there.
 */
@Composable
fun CountdownBadge(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: BadgeTone = BadgeTone.INFO
) {
    StatusBadge(
        label = text,
        tone = tone,
        modifier = modifier,
        uppercase = false,
        icon = icon
    )
}

/**
 * A determinate progress bar — trip completion, a train's distance covered.
 *
 * Animates so a two-minute refresh reads as movement rather than a jump.
 */
@Composable
fun AppProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    height: Dp = 6.dp
) {
    val target = fraction.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(320),
        label = "progress"
    )

    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(trackColor)
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .fillMaxSize()
                .clip(RoundedCornerShape(height / 2))
                .background(color)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Stats
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * One figure with its label and a tinted icon — the four-across summary on Home.
 *
 * The figure is set in tabular numerals so four cards in a row keep their baselines and
 * digit columns aligned as the numbers change.
 */
@Composable
fun StatCard(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    accent: Color = AppThemeExtended.colors.accent,
    accentSoft: Color = AppThemeExtended.colors.accentSoft,
    onClick: (() -> Unit)? = null
) {
    AppCard(
        modifier = modifier,
        onClick = onClick,
        fillWidth = false,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp)
    ) {
        Surface(shape = RoundedCornerShape(10.dp), color = accentSoft) {
            Icon(
                icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier
                    .padding(6.dp)
                    .size(18.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = value,
            style = AppThemeExtended.text.statNumber,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Empty states
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * What a list looks like before it has anything in it (§27).
 *
 * An empty screen is an invitation, so this is a title, one line of direction and — where
 * there is something useful to do — the action itself. Never a bare "No data".
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = AppThemeExtended.colors.accentSoft,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = AppThemeExtended.colors.accent,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            PrimaryButton(
                text = actionLabel,
                onClick = onAction,
                modifier = Modifier.width(220.dp)
            )
        }
        if (secondaryActionLabel != null && onSecondaryAction != null) {
            Spacer(Modifier.height(10.dp))
            TextActionButton(text = secondaryActionLabel, onClick = onSecondaryAction)
        }
    }
}
