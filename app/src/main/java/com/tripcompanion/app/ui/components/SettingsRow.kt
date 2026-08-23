package com.tripcompanion.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripcompanion.app.ui.theme.AppThemeExtended

/**
 * Settings rows, grouped.
 *
 * A settings screen is a list, and it should look like one: rows of a consistent height
 * inside a single grouped container, divided by hairlines. One card per setting turns a
 * scannable list into a stack of tiles that all shout equally, so a group here is *one*
 * surface holding several rows — never one surface per row.
 */

/**
 * A titled group of rows on one surface.
 *
 * The title sits outside the container, in sentence case. Dividers are drawn between rows
 * but never above the first or below the last, which is what keeps the group reading as a
 * single object.
 */
@Composable
fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Surface(
            shape = AppThemeExtended.metrics.cardShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                AppThemeExtended.metrics.borderWidth,
                MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(content = content)
        }
    }
}

/** The hairline between two rows in a group; indented past the icon column. */
@Composable
fun SettingsRowDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = 56.dp)
            .height(AppThemeExtended.metrics.dividerWidth)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

/**
 * One setting: tinted icon, label, optional description, and on the right either its current
 * value, a switch, or a chevron.
 *
 * The trailing slot is exclusive by construction — pass `checked`/`onCheckedChange` for a
 * toggle *or* `value` for a drill-in, not both. A row that shows a value and a switch is a
 * row where it isn't clear what tapping does.
 */
@Composable
fun SettingsRow(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    value: String? = null,
    iconTint: Color = AppThemeExtended.colors.accent,
    iconBackground: Color = AppThemeExtended.colors.accentSoft,
    checked: Boolean? = null,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    showChevron: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    val isToggle = checked != null && onCheckedChange != null

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null && !isToggle) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = RoundedCornerShape(9.dp), color = iconBackground) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier
                    .padding(6.dp)
                    .size(18.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        when {
            isToggle -> {
                Spacer(Modifier.width(10.dp))
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }

            else -> {
                if (value != null) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (showChevron && onClick != null) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = AppThemeExtended.colors.textFaint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * The account row at the top of Settings: avatar, name, one line beneath.
 *
 * Taller than a [SettingsRow] and on its own surface, because it identifies whose trips
 * these are rather than configuring anything. The avatar falls back to the first letter of
 * the name — an initial is recognisable, a generic person glyph is not.
 */
@Composable
fun ProfileRow(
    name: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    avatarUri: String? = null,
    actionLabel: String? = null,
    onClick: (() -> Unit)? = null
) {
    AppCard(
        modifier = modifier,
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(AppThemeExtended.colors.accentSoft),
                contentAlignment = Alignment.Center
            ) {
                if (avatarUri.isNullOrBlank()) {
                    Text(
                        text = name.trim().take(1).uppercase().ifEmpty { "?" },
                        style = MaterialTheme.typography.headlineMedium,
                        color = AppThemeExtended.colors.accent
                    )
                } else {
                    AppImage(
                        uri = avatarUri,
                        contentDescription = name,
                        modifier = Modifier.fillMaxSize(),
                        shape = CircleShape
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (actionLabel != null && onClick != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = AppThemeExtended.colors.accent
                )
            }
        }
    }
}

/**
 * One appearance choice — Light, Dark or System — as a tappable swatch.
 *
 * Shows a preview of the mode it selects, because "Dark" as a word is a description while a
 * dark swatch is the thing itself. Selection is a ring plus a tick, not colour alone, so it
 * survives greyscale and colour-blindness.
 */
@Composable
fun ThemeOptionCard(
    label: String,
    previewBackground: Color,
    previewSurface: Color,
    previewAccent: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AppThemeExtended.colors

    Column(
        modifier = modifier
            .clip(AppThemeExtended.metrics.cardShape)
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = previewBackground,
            border = BorderStroke(
                if (selected) 2.dp else AppThemeExtended.metrics.borderWidth,
                if (selected) colors.accent else MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
        ) {
            Column(
                Modifier.padding(9.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(0.55f)
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(previewAccent)
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(previewSurface)
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(previewSurface)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = colors.accent,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) colors.accent else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
