package com.tripcompanion.app.ui.components

import android.graphics.Point
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tripcompanion.app.ui.theme.AppThemeExtended
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView

/**
 * A CARTO raster basemap, addressed directly.
 *
 * osmdroid's own [org.osmdroid.tileprovider.tilesource.TileSourceFactory] only ships Mapnik and
 * friends, and Mapnik has one light raster set — which is why the old code had to invert it for
 * dark mode and then claw the hue back with a saturation matrix. CARTO publishes a light *and* a
 * dark set, so both themes get tiles that were drawn that way instead of a photographic negative.
 *
 * The base URLs must end in `/`: osmdroid appends `z/x/y` and [XYTileSource]'s extension to
 * whatever it is handed. Four subdomains because that is what the CDN expects and it is how
 * osmdroid parallelises a screenful of tiles.
 */
private fun cartoSource(name: String, style: String) = XYTileSource(
    name,
    3,
    20,
    256,
    ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/rastertiles/$style/",
        "https://b.basemaps.cartocdn.com/rastertiles/$style/",
        "https://c.basemaps.cartocdn.com/rastertiles/$style/",
        "https://d.basemaps.cartocdn.com/rastertiles/$style/"
    ),
    "© OpenStreetMap contributors © CARTO"
)

/** Soft pastel land, muted roads, restrained labels — the light basemap. */
private val VoyagerTiles = cartoSource("carto-voyager", "voyager")

/** The same cartography drawn dark, so night mode is not an inverted daytime map. */
private val DarkMatterTiles = cartoSource("carto-dark", "dark_all")

/**
 * One pin drawn over the map.
 *
 * Positions are geographic; the screen coordinate is worked out from the live projection every
 * time the camera moves, so a marker stays on its building rather than on its pixel.
 */
data class MapMarker(
    val id: Long,
    val latitude: Double,
    val longitude: Double,
    val label: String,
    /** Visiting order within the day. Null draws a plain pin. */
    val number: Int? = null,
    val color: Color,
    val isCurrent: Boolean = false
)

/**
 * A real, interactive OpenStreetMap canvas (§12).
 *
 * Pan and pinch-zoom come from osmdroid's own touch handling; the buttons are for
 * one-handed use. The pin is a fixed crosshair at the centre of the viewport and
 * the map moves underneath it — so "adjust the location manually" is just dragging
 * the map, and the coordinate the app saves is always the one under the crosshair.
 * There is no separate drag-the-marker mode to get out of sync with.
 *
 * §12 forbids a static image standing in for this. Nothing here is decorative:
 * remove the tiles and the screen stops working.
 *
 * Pass `interactive = false` for a map that reports a position rather than choosing one
 * — a saved place being displayed, not edited. It is still the same real map, still
 * loading real tiles; it just does not move under the finger, because a map that pans
 * on a read-only screen promises an edit that will not be saved.
 *
 * Pass [markers] to draw the trip's stops. Markers and the crosshair are alternatives, not
 * companions: a crosshair means "you are choosing a point", and showing one over a map of
 * places you already chose is a promise the screen does not keep.
 *
 * [onViewportChanged] reports the visible rectangle as four plain numbers rather than
 * osmdroid's own `BoundingBox`, so nothing above this file has to know which map library is
 * underneath. The location picker uses it to bias place search towards what is on screen.
 */
@Composable
fun TripMap(
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier,
    recenterTrigger: Int = 0,
    onCenterChanged: (Double, Double) -> Unit = { _, _ -> },
    onViewportChanged: (north: Double, east: Double, south: Double, west: Double) -> Unit =
        { _, _, _, _ -> },
    interactive: Boolean = true,
    initialZoom: Double = INITIAL_ZOOM,
    focusZoom: Double = FOCUS_ZOOM,
    controlsPadding: PaddingValues = PaddingValues(12.dp),
    crosshairBottomPadding: Dp = 0.dp,
    showCrosshair: Boolean = true,
    markers: List<MapMarker> = emptyList(),
    onMarkerClick: (MapMarker) -> Unit = {}
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapRef by remember { mutableStateOf<MapView?>(null) }

    // Which basemap, decided by the luminance of the surface it has to sit on rather than by
    // the theme's name — §4's no-special-cases habit applied to the UI. A theme added later
    // gets the right tiles without this line being touched.
    val tiles = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        DarkMatterTiles
    } else {
        VoyagerTiles
    }

    // Bumped on every scroll and zoom. The marker layer reads it so its screen positions are
    // recomputed from the live projection; without it the pins would freeze where they were
    // first drawn and slide off their places as soon as the map moved.
    var cameraTick by remember { mutableIntStateOf(0) }

    // The initial camera is read once. Putting it in AndroidView's update block
    // instead would snap the map back to state on every recomposition, which is
    // indistinguishable from the map refusing to be dragged.
    val initialCenter = remember { GeoPoint(latitude, longitude) }

    // osmdroid runs tile-download threads of its own and needs the lifecycle
    // forwarded, or they keep working while the screen is in the background.
    DisposableEffect(lifecycleOwner, mapRef) {
        val map = mapRef
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> map?.onResume()
                Lifecycle.Event.ON_PAUSE -> map?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(recenterTrigger) {
        if (recenterTrigger > 0) {
            mapRef?.controller?.let { controller ->
                controller.setZoom(focusZoom)
                controller.animateTo(GeoPoint(latitude, longitude))
            }
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { context ->
                MapView(context).apply {
                    setTileSource(tiles)
                    // No colour filter. The tiles are already the right colour for this
                    // theme, and tinting them again is what used to turn parks magenta.
                    overlayManager.tilesOverlay.setColorFilter(null)
                    setMultiTouchControls(interactive)
                    // osmdroid's own zoom buttons are a dated widget; the app draws
                    // its own so they follow the active theme.
                    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                    setTilesScaledToDpi(true)
                    setMinZoomLevel(MIN_ZOOM)
                    setMaxZoomLevel(MAX_ZOOM)
                    controller.setZoom(initialZoom)
                    controller.setCenter(initialCenter)

                    if (!interactive) {
                        // Swallowing the gesture before osmdroid sees it is the only way
                        // to stop panning outright — MapView has no read-only mode, and
                        // clearing multi-touch alone still leaves single-finger drag.
                        setOnTouchListener { _, _ -> true }
                    }

                    addMapListener(object : MapListener {
                        override fun onScroll(event: ScrollEvent?): Boolean {
                            report()
                            return true
                        }

                        override fun onZoom(event: ZoomEvent?): Boolean {
                            report()
                            return true
                        }

                        /**
                         * The camera, in the two forms callers ask for.
                         *
                         * Read from the live [MapView] rather than from the event, because a
                         * [ScrollEvent] carries only a scroll offset and a [ZoomEvent] only a
                         * zoom level — neither knows where the map ended up.
                         */
                        private fun report() {
                            mapCenter.let { onCenterChanged(it.latitude, it.longitude) }
                            boundingBox?.let {
                                onViewportChanged(
                                    it.latNorth,
                                    it.lonEast,
                                    it.latSouth,
                                    it.lonWest
                                )
                            }
                            cameraTick++
                        }
                    })
                    mapRef = this
                }
            },
            update = { map ->
                // The theme can flip while this screen is open, so the source is re-checked —
                // but only swapped when it actually differs. Calling setTileSource with the
                // source already in place drops the tile cache, and the map blinks through
                // empty grey on every recomposition.
                if (map.tileProvider.tileSource.name() != tiles.name()) {
                    map.setTileSource(tiles)
                }
                map.invalidate()
            },
            onRelease = { map ->
                map.onDetach()
                if (mapRef === map) mapRef = null
            },
            modifier = Modifier.fillMaxSize()
        )

        if (markers.isNotEmpty()) {
            MarkerLayer(
                map = mapRef,
                markers = markers,
                cameraTick = cameraTick,
                onMarkerClick = onMarkerClick,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (showCrosshair) {
            MapCrosshair(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = crosshairBottomPadding)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(controlsPadding),
            verticalArrangement = Arrangement.spacedBy(AppThemeExtended.metrics.rowGap)
        ) {
            if (interactive) {
                MapControlButton(
                    icon = Icons.Default.Add,
                    label = "Zoom in",
                    onClick = { mapRef?.controller?.zoomIn() }
                )
                MapControlButton(
                    icon = Icons.Default.Remove,
                    label = "Zoom out",
                    onClick = { mapRef?.controller?.zoomOut() }
                )
            }
        }

        // Attribution is a condition of using these tiles, not decoration. Both parties:
        // OSM for the data, CARTO for the cartography.
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            Text(
                text = "© OpenStreetMap · © CARTO",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

/**
 * The pins, drawn in Compose rather than as osmdroid overlays.
 *
 * osmdroid wants a `Drawable` per marker, which means baking a bitmap for every category
 * colour and re-baking them when the theme flips. Reading the projection and laying the pins
 * out as composables keeps one source of truth for the palette and gives the numbers real
 * type instead of rasterised text.
 *
 * [cameraTick] is what makes this correct: the projection is only valid for the current
 * camera, so the positions are recomputed whenever it changes.
 */
@Composable
private fun MarkerLayer(
    map: MapView?,
    markers: List<MapMarker>,
    cameraTick: Int,
    onMarkerClick: (MapMarker) -> Unit,
    modifier: Modifier = Modifier
) {
    if (map == null) return
    val projection = map.projection
    val density = LocalDensity.current

    // Read once per camera change, not once per marker: the projection is the expensive part.
    val placed = remember(cameraTick, markers, projection) {
        val point = Point()
        markers.map { marker ->
            projection.toPixels(GeoPoint(marker.latitude, marker.longitude), point)
            marker to IntOffset(point.x, point.y)
        }
    }

    Box(modifier) {
        placed.forEach { (marker, offset) ->
            // The pin's point is its bottom centre, so the composable is shifted up and left
            // by half its own size — otherwise every marker sits below and right of its place.
            val halfWidth = with(density) { MARKER_WIDTH.toPx() / 2f }
            val fullHeight = with(density) { MARKER_HEIGHT.toPx() }
            MapPinMarker(
                marker = marker,
                onClick = { onMarkerClick(marker) },
                modifier = Modifier.offset {
                    IntOffset(
                        x = offset.x - halfWidth.toInt(),
                        y = offset.y - fullHeight.toInt()
                    )
                }
            )
        }
    }
}

/**
 * A map pin: a round head with a point at the bottom.
 *
 * One continuous path rather than a circle plus a triangle. Two overlapping sub-paths wound in
 * opposite directions cancel each other out under non-zero fill and the overlap comes out as a
 * hole, so the head is drawn as an arc that stops short of the bottom and the two lines to the
 * tip close the gap it left.
 *
 * The tip is at bottom-centre, which is what lets [MarkerLayer] anchor a marker by shifting it
 * up by its full height and left by half its width.
 */
private val TeardropShape = GenericShape { size, _ ->
    val diameter = size.width
    val radius = diameter / 2f
    // 120° round the top to 60°, leaving the bottom 60° open for the point.
    arcTo(
        rect = Rect(0f, 0f, diameter, diameter),
        startAngleDegrees = 120f,
        sweepAngleDegrees = 300f,
        forceMoveTo = true
    )
    lineTo(radius, size.height)
    close()
}

/**
 * One pin: a teardrop in the event's own colour with its visiting number in the head.
 *
 * The number is the information a map of a day's plan actually carries — which of these do I
 * reach first — and it is the thing a plain dot cannot say. It sits in the round part, not in
 * the middle of the whole shape, so the bottom padding is exactly the tail's height.
 */
@Composable
private fun MapPinMarker(
    marker: MapMarker,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = TeardropShape,
        color = marker.color,
        border = BorderStroke(
            if (marker.isCurrent) 3.dp else 2.dp,
            if (marker.isCurrent) {
                MaterialTheme.colorScheme.onBackground
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        shadowElevation = 3.dp,
        modifier = modifier.size(width = MARKER_WIDTH, height = MARKER_HEIGHT)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = MARKER_TAIL),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = marker.number?.toString() ?: "•",
                style = AppThemeExtended.text.badge,
                color = Color.White
            )
        }
    }
}

/**
 * The pin. A ring on a stem over a ground dot: the ring shows the target, the dot
 * shows the exact point, and the gap between them keeps the point itself visible.
 */
@Composable
private fun MapCrosshair(modifier: Modifier = Modifier) {
    val accent = AppThemeExtended.colors.accent
    val metrics = AppThemeExtended.metrics
    val outline = MaterialTheme.colorScheme.outline

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
            }
            Box(
                Modifier
                    .width(metrics.timelineRailWidth.coerceAtLeast(2.dp))
                    .height(14.dp)
                    .background(accent)
            )
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(outline)
            )
        }
    }
}

/**
 * One floating map control.
 *
 * Round, not the app's rounded rectangle. Everywhere else a card is a container for content and
 * the shared [AppCard] is right; here the button floats over a photograph-like surface with
 * nothing to align to, and a circle is the shape that reads as a control rather than as a very
 * small card. Elevation comes from the theme so it lifts off the tiles by the same amount cards
 * lift off the background.
 */
@Composable
private fun MapControlButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val metrics = AppThemeExtended.metrics
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = metrics.cardElevation,
        modifier = Modifier.size(metrics.controlHeight)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * The pin's footprint: the head is [MARKER_WIDTH] across, and [MARKER_TAIL] of point hangs
 * below it. Kept as three related constants because [MapPinMarker] needs the tail on its own
 * to centre the number in the head rather than in the whole silhouette.
 */
private val MARKER_WIDTH = 34.dp
private val MARKER_HEIGHT = 46.dp
private val MARKER_TAIL = MARKER_HEIGHT - MARKER_WIDTH

private const val INITIAL_ZOOM = 5.0
private const val FOCUS_ZOOM = 16.0
private const val MIN_ZOOM = 3.0
private const val MAX_ZOOM = 19.0
