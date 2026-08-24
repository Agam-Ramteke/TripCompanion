package com.tripcompanion.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.tripcompanion.app.domain.model.EventType
import com.tripcompanion.app.ui.theme.AppThemeExtended

/**
 * Photography, and what stands in for it.
 *
 * The brief asks for a photo-rich app, but a trip that hasn't been given photos yet is the
 * normal first-run state — so every image in here degrades to a designed placeholder rather
 * than a grey box or a broken-image glyph. Photos are app-private `file://` URIs written by
 * [com.tripcompanion.app.core.util.ImageStorageHelper]; there are no remote image URLs in
 * this app, which is also what makes it work in airplane mode.
 */

/** The scrim under text on a photo. Dark enough for white type, light enough to see through. */
private val PhotoScrim = Brush.verticalGradient(
    0.0f to Color.Transparent,
    0.45f to Color(0x33000000),
    1.0f to Color(0xD9000000)
)

/**
 * A photograph behind a card's own content, blurred and washed toward the surface it sits on.
 *
 * The counterpart of [PhotoScrim]: that one darkens a photo so white type can sit over it, this
 * one blurs a photo and fades it toward the theme's own surface colour so the card's *ordinary*
 * type still can. A card that switched to white text whenever it happened to have a picture would
 * read as two different components; this way the photograph is atmosphere and the card is unchanged.
 *
 * [blurRadius] frosts the picture so it reads as texture rather than a competing image behind the
 * text; the fade then only has to lift contrast the rest of the way, which is why it can be gentler
 * (and the photo more present) than an unblurred wash would allow. The wash is deliberately light —
 * a background the user chose for this card should read as their photograph, not a tinted panel —
 * and only the lower portion stays opaque enough to seat the card's own dark text. Blur is a real
 * render effect on API 31+ and a silent no-op below it — the fade alone still carries legibility there.
 *
 * Draw it with [Modifier.matchParentSize] inside a `Box` so the picture never decides the card's
 * height; the content does. Nothing is drawn at all when there is no picture, because a
 * placeholder gradient behind live text is noise rather than a stand-in.
 */
@Composable
fun PhotoBackdrop(
    uri: String?,
    modifier: Modifier = Modifier,
    fadeColor: Color = MaterialTheme.colorScheme.surface,
    blurRadius: Dp = 16.dp,
    contentDescription: String? = null
) {
    if (uri.isNullOrBlank()) return
    Box(modifier) {
        AppImage(
            uri = uri,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .blur(blurRadius),
            shape = RoundedCornerShape(0.dp)
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to fadeColor.copy(alpha = 0.42f),
                        0.55f to fadeColor.copy(alpha = 0.60f),
                        1.0f to fadeColor.copy(alpha = 0.80f)
                    )
                )
        )
    }
}

/**
 * One image, with its loading and empty states designed rather than defaulted.
 *
 * `uri == null` and "the file went missing" render identically, because to someone looking
 * at the screen they are the same thing: there is no picture here.
 */
@Composable
fun AppImage(
    uri: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = AppThemeExtended.metrics.imageShape,
    placeholderIcon: ImageVector? = null,
    placeholderTint: Color = AppThemeExtended.colors.accent,
    placeholderBackground: Color = AppThemeExtended.colors.accentSoft,
    contentScale: ContentScale = ContentScale.Crop
) {
    val fallback: @Composable () -> Unit = {
        GradientPlaceholder(
            icon = placeholderIcon,
            tint = placeholderTint,
            background = placeholderBackground,
            modifier = Modifier.fillMaxSize()
        )
    }

    Box(modifier.clip(shape)) {
        if (uri.isNullOrBlank()) {
            fallback()
        } else {
            SubcomposeAsyncImage(
                model = uri,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                },
                error = { fallback() }
            )
        }
    }
}

/**
 * The stand-in for a missing photo: a two-stop wash in the subject's own colour with its
 * icon at low opacity.
 *
 * Tinted by category rather than neutral, so a list of photo-less places still reads as a
 * list of *different* places.
 */
@Composable
fun GradientPlaceholder(
    icon: ImageVector?,
    modifier: Modifier = Modifier,
    tint: Color = AppThemeExtended.colors.accent,
    background: Color = AppThemeExtended.colors.accentSoft,
    iconSize: Dp = 34.dp
) {
    Box(
        modifier.background(
            Brush.linearGradient(
                listOf(background, tint.copy(alpha = if (AppThemeExtended.colors.isDark) 0.30f else 0.22f))
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = tint.copy(alpha = 0.75f),
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

/** The category-coloured placeholder for an event, keyed off its type. */
@Composable
fun CategoryPlaceholder(
    type: EventType,
    modifier: Modifier = Modifier,
    iconSize: Dp = 34.dp
) {
    GradientPlaceholder(
        icon = type.icon,
        modifier = modifier,
        tint = type.color,
        background = type.softColor,
        iconSize = iconSize
    )
}

/**
 * The photograph a screen opens on, with a gradient scrim and content laid over its lower
 * portion.
 *
 * The scrim is not decoration — it is what lets white type sit on an unknown photograph and
 * stay legible whether the user picked a bright beach or a dark palace interior. Overlay
 * content is aligned bottom-start; anything that needs the top-right (a back button, a
 * favourite toggle) positions itself inside [overlay] with its own alignment.
 */
@Composable
fun HeroImage(
    uri: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    height: Dp = AppThemeExtended.metrics.heroHeight,
    shape: Shape = RoundedCornerShape(0.dp),
    placeholderIcon: ImageVector? = null,
    placeholderTint: Color = AppThemeExtended.colors.accent,
    placeholderBackground: Color = AppThemeExtended.colors.accentSoft,
    scrim: Boolean = true,
    overlay: @Composable BoxScope.() -> Unit = {}
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
    ) {
        AppImage(
            uri = uri,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(0.dp),
            placeholderIcon = placeholderIcon,
            placeholderTint = placeholderTint,
            placeholderBackground = placeholderBackground
        )
        if (scrim) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(PhotoScrim)
            )
        }
        overlay()
    }
}

/**
 * A photograph in a rounded tile with its caption underneath — the Photo Plan grid and the
 * "photo ideas" strip.
 *
 * The caption is optional and the tile is fixed-size, because these appear in scrolling rows
 * where ragged widths read as a broken layout.
 */
@Composable
fun PhotoCard(
    uri: String?,
    modifier: Modifier = Modifier,
    caption: String? = null,
    width: Dp = 140.dp,
    imageHeight: Dp = 110.dp,
    placeholderIcon: ImageVector? = null,
    placeholderTint: Color = AppThemeExtended.colors.accent,
    placeholderBackground: Color = AppThemeExtended.colors.accentSoft,
    badge: String? = null,
    onClick: (() -> Unit)? = null
) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .width(width)
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(AppThemeExtended.metrics.imageShape)
                        .clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
    ) {
        Box {
            AppImage(
                uri = uri,
                contentDescription = caption,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(imageHeight),
                placeholderIcon = placeholderIcon,
                placeholderTint = placeholderTint,
                placeholderBackground = placeholderBackground
            )
            if (badge != null) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xB3000000),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                ) {
                    Text(
                        text = badge,
                        style = AppThemeExtended.text.badge,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }
        }
        if (caption != null) {
            Spacer(Modifier.height(7.dp))
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * A swipeable row of photographs with a position readout.
 *
 * The counter is a plain "2 / 5" rather than a dot row: dots stop being countable past four
 * or five, and a place's gallery can hold many more than that.
 */
@Composable
fun ImageCarousel(
    uris: List<String>,
    modifier: Modifier = Modifier,
    height: Dp = 240.dp,
    contentDescription: String? = null,
    placeholderIcon: ImageVector? = null,
    onImageClick: ((Int) -> Unit)? = null
) {
    if (uris.isEmpty()) {
        AppImage(
            uri = null,
            contentDescription = contentDescription,
            modifier = modifier
                .fillMaxWidth()
                .height(height),
            placeholderIcon = placeholderIcon
        )
        return
    }

    val listState = rememberLazyListState()
    val current = listState.firstVisibleItemIndex + 1

    Box(
        modifier
            .fillMaxWidth()
            .height(height)
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
            itemsIndexed(uris) { index, uri ->
                AppImage(
                    uri = uri,
                    contentDescription = contentDescription,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(height)
                        .then(
                            if (onImageClick != null) {
                                Modifier.clickable { onImageClick(index) }
                            } else {
                                Modifier
                            }
                        )
                )
            }
        }
        if (uris.size > 1) {
            Surface(
                shape = CircleShape,
                color = Color(0xB3000000),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
            ) {
                Text(
                    text = "$current / ${uris.size}",
                    style = AppThemeExtended.text.badge,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                )
            }
        }
    }
}
