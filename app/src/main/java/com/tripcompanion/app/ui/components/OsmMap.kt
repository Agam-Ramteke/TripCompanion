package com.tripcompanion.app.ui.components

import android.graphics.Paint
import android.graphics.Point
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tripcompanion.app.BuildConfig
import com.tripcompanion.app.ui.theme.AppThemeExtended
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import kotlin.math.abs
import kotlin.math.hypot

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
 * Esri World Imagery — photographic satellite tiles, free and key-less like the CARTO basemaps.
 *
 * Esri serves its pyramid as `.../tile/{z}/{y}/{x}` — row before column — whereas osmdroid's
 * [XYTileSource] would hand back `{z}/{x}/{y}`. Left to the default that swap fetches the wrong
 * tile (or a 4xx), so the URL is assembled by hand. Attribution is a usage condition, surfaced by
 * [TripMap] whenever this source is the one showing.
 */
private val SatelliteTiles: OnlineTileSourceBase = object : OnlineTileSourceBase(
    "esri-world-imagery",
    3,
    19,
    256,
    "",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"),
    "© Esri, Maxar, Earthstar Geographics"
) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        baseUrl +
            MapTileIndex.getZoom(pMapTileIndex) + "/" +
            MapTileIndex.getY(pMapTileIndex) + "/" +
            MapTileIndex.getX(pMapTileIndex)
}

/**
 * A MapTiler prebuilt raster style, keyed.
 *
 * The CARTO sets above are the keyless default; this is the *keyed* upgrade — a low-contrast
 * cartography chosen so the map recedes and the itinerary is the subject, exactly what a travel HUD
 * wants and what a general-purpose street map fights. It is the one tile source that needs a secret,
 * and the secret is the only new thing: like RailRadar and OpenRouteService the key rides in from
 * [BuildConfig] (← git-ignored `local.properties`), never the source. Absent a key, [TripMap] never
 * builds this and falls back to CARTO, so the map is whole either way.
 *
 * MapTiler serves `.../maps/{style}/256/{z}/{x}/{y}.png?key=…` — a query string osmdroid's
 * [XYTileSource] can't append, so the URL is assembled by hand like the Esri source. The style id
 * is the whole style ([MAPTILER_LIGHT_STYLE] / [MAPTILER_DARK_STYLE]); swap those constants to
 * retune the basemap without touching a call site. Attribution is a usage condition surfaced by
 * [TripMap] whenever this source is showing.
 */
private fun maptilerSource(styleId: String, key: String): OnlineTileSourceBase =
    object : OnlineTileSourceBase(
        "maptiler-$styleId",
        3,
        20,
        256,
        ".png",
        arrayOf("https://api.maptiler.com/maps/$styleId/256/"),
        "© MapTiler © OpenStreetMap contributors"
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val z = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "https://api.maptiler.com/maps/$styleId/256/$z/$x/$y.png?key=$key"
        }
    }

/**
 * MapTiler style ids for the two themes. Both are from MapTiler's low-contrast "dataviz" family —
 * built to sit *behind* content rather than be read as the content — which is the recede the brief
 * asks for. Swap for a warmer prebuilt (`landscape`, `pastel`, `bright-v2`) here if the palette
 * should lean warmer; nothing else changes.
 */
private const val MAPTILER_LIGHT_STYLE = "dataviz"
private const val MAPTILER_DARK_STYLE = "dataviz-dark"

/** Which basemap [TripMap] draws: the themed raster (MapTiler when keyed, else CARTO), or Esri satellite. */
enum class MapBasemap { Standard, Satellite }

/**
 * A stop's progress, which decides how prominently its pin is drawn.
 *
 * Derived from the trip-state engine, never from a place name (§3): [Completed] is behind the
 * traveller (done, skipped, or missed), [Current] is where they are or are headed next, [Upcoming]
 * is everything still ahead. The three map onto three weights so a glance at the map reads the same
 * story the itinerary sheet tells — muted behind, emphasised ahead.
 */
enum class MarkerState { Completed, Upcoming, Current }

/**
 * One pin drawn over the map.
 *
 * Positions are geographic; the screen coordinate is worked out from the live projection every
 * time the camera moves, so a marker stays on its building rather than on its pixel.
 *
 * A numbered status chip, not a type teardrop: [number] is the stop's visiting order (the same
 * number the sheet card carries, so map and list are one system) and [state] sets the weight. The
 * event's [color] tints an upcoming pin; completed and current derive their own tones from the
 * status palette in [MapPinMarker].
 */
data class MapMarker(
    val id: Long,
    val latitude: Double,
    val longitude: Double,
    val label: String,
    /** Visiting order within the day, shown in the pin. Null draws a dot instead of a number. */
    val number: Int? = null,
    val color: Color,
    val state: MarkerState = MarkerState.Upcoming
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
 *
 * [routePoints], when given, are joined in itinerary order by a [Polyline] and a darker casing
 * beneath it. Whatever the caller hands over is drawn verbatim — road-following geometry from the
 * routing service, or straight legs when there is none — as a native osmdroid overlay that
 * re-projects on pan and zoom for free. [recenterTrigger] frames the whole day; bumping
 * [animateToTrigger] pans to ([animateToLatitude], [animateToLongitude]) — a tapped stop or
 * center-on-me. A non-null device position draws a distinct current-location dot with an accuracy
 * ring. [zoomAlignment] chooses the corner the zoom column sits in, and [bottomInset] lifts the zoom
 * and the tile attribution clear of a bottom sheet floating over the map. The screen stacks any
 * further chrome (layers, recenter, navigate) over the map itself, reusing [MapControlButton] for
 * the shared floating-circle style.
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
    onMarkerClick: (MapMarker) -> Unit = {},
    routePoints: List<GeoPoint> = emptyList(),
    routeColor: Color = Color.Unspecified,
    basemap: MapBasemap = MapBasemap.Standard,
    zoomAlignment: Alignment = Alignment.TopEnd,
    bottomInset: Dp = 0.dp,
    animateToTrigger: Int = 0,
    animateToLatitude: Double? = null,
    animateToLongitude: Double? = null,
    deviceLatitude: Double? = null,
    deviceLongitude: Double? = null,
    deviceAccuracyMeters: Float? = null
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapRef by remember { mutableStateOf<MapView?>(null) }

    val accent = AppThemeExtended.colors.accent
    // Keyed pastel basemap when a MapTiler key is present; the keyless CARTO sets otherwise. Which
    // cut — light or dark — is decided by the luminance of the surface the map sits on, not the
    // theme's name (§4's no-special-cases habit), so a theme added later gets the right tiles
    // untouched. Satellite ignores the theme: aerial photography has no light and dark edition.
    // Remembered so a recomposition doesn't rebuild the source object and drop the tile cache.
    val mapTilerKey = BuildConfig.MAPTILER_API_KEY
    val isDarkSurface = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val tiles = remember(basemap, isDarkSurface, mapTilerKey) {
        when (basemap) {
            MapBasemap.Satellite -> SatelliteTiles
            MapBasemap.Standard -> when {
                mapTilerKey.isNotBlank() ->
                    maptilerSource(
                        if (isDarkSurface) MAPTILER_DARK_STYLE else MAPTILER_LIGHT_STYLE,
                        mapTilerKey
                    )
                isDarkSurface -> DarkMatterTiles
                else -> VoyagerTiles
            }
        }
    }
    val resolvedRouteColor = if (routeColor.isSpecified) routeColor else accent
    // The route paint is plain Android — a native osmdroid overlay wants an ARGB int and a pixel
    // width, not Compose types. Read here, in composition, where the density and colour resolve. The
    // casing is a wider, darker line drawn beneath, so the route reads as a raised ribbon over busy
    // tiles rather than a hairline lost in them.
    val routeArgb = resolvedRouteColor.toArgb()
    val casingArgb = lerp(resolvedRouteColor, Color.Black, 0.35f).toArgb()
    val routeWidthPx = with(LocalDensity.current) { ROUTE_WIDTH.toPx() }
    val casingWidthPx = with(LocalDensity.current) { ROUTE_CASING_WIDTH.toPx() }
    var routeRef by remember { mutableStateOf<Polyline?>(null) }
    var casingRef by remember { mutableStateOf<Polyline?>(null) }

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

    // Recenter = frame the whole day. More than one visible pin fits them all in view with padding;
    // a single pin (or none) falls back to a close focus on the centre. The fit itself is posted
    // when the view has no size yet — zoomToBoundingBox needs a laid-out MapView, and the first
    // recenter can beat the first layout.
    LaunchedEffect(recenterTrigger) {
        if (recenterTrigger > 0) {
            val map = mapRef ?: return@LaunchedEffect
            map.fitToPoints(
                points = markers.map { GeoPoint(it.latitude, it.longitude) },
                focusZoom = focusZoom,
                fallback = GeoPoint(latitude, longitude)
            )
        }
    }

    // Animate to one stop — a tapped pin, a tapped sheet row, or center-on-me. A monotonic trigger
    // rather than a value change, so asking for the same stop twice still recentres it.
    LaunchedEffect(animateToTrigger) {
        if (animateToTrigger > 0 && animateToLatitude != null && animateToLongitude != null) {
            mapRef?.controller?.animateTo(
                GeoPoint(animateToLatitude, animateToLongitude),
                focusZoom,
                CAMERA_ANIM_MS
            )
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

                    // The route is a native overlay, not a Compose layer: osmdroid re-projects it on
                    // every pan and zoom for free, and it stays under the pins because the pins are
                    // drawn in Compose above this view. Whatever line it is handed — road-following
                    // geometry from the routing service, or straight legs when there is none — is
                    // drawn the same way. A casing goes on first so it sits beneath the accent line.
                    val casing = Polyline(this).apply {
                        outlinePaint.color = casingArgb
                        outlinePaint.strokeWidth = casingWidthPx
                        outlinePaint.isAntiAlias = true
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                        outlinePaint.strokeJoin = Paint.Join.ROUND
                        isVisible = false
                    }
                    overlays.add(casing)
                    casingRef = casing

                    val route = Polyline(this).apply {
                        outlinePaint.color = routeArgb
                        outlinePaint.strokeWidth = routeWidthPx
                        outlinePaint.isAntiAlias = true
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                        outlinePaint.strokeJoin = Paint.Join.ROUND
                        isVisible = false
                    }
                    overlays.add(route)
                    routeRef = route

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
                // Refresh the route and its casing in place. setPoints on the existing overlays
                // avoids tearing them down and rebuilding every recomposition; both hide below two
                // points, where a "line" would just be a dot.
                casingRef?.let { casing ->
                    casing.setPoints(routePoints)
                    casing.outlinePaint.color = casingArgb
                    casing.isVisible = routePoints.size >= 2
                }
                routeRef?.let { route ->
                    route.setPoints(routePoints)
                    route.outlinePaint.color = routeArgb
                    route.isVisible = routePoints.size >= 2
                }
                map.invalidate()
            },
            onRelease = { map ->
                map.onDetach()
                if (mapRef === map) mapRef = null
            },
            modifier = Modifier.fillMaxSize()
        )

        if (deviceLatitude != null && deviceLongitude != null) {
            CurrentLocationLayer(
                map = mapRef,
                latitude = deviceLatitude,
                longitude = deviceLongitude,
                accuracyMeters = deviceAccuracyMeters,
                cameraTick = cameraTick,
                modifier = Modifier.fillMaxSize()
            )
        }

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

        if (interactive) {
            ZoomControl(
                onZoomIn = { mapRef?.controller?.zoomIn() },
                onZoomOut = { mapRef?.controller?.zoomOut() },
                modifier = Modifier
                    .align(zoomAlignment)
                    .padding(controlsPadding)
                    .padding(bottom = bottomInset)
            )
        }

        // Attribution is a condition of using these tiles, not decoration — and it must name
        // whoever actually drew what is on screen, so it follows the basemap. Lifted by
        // [bottomInset] so a sheet floating over the map cannot bury the credit.
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = bottomInset)
        ) {
            Text(
                text = when {
                    basemap == MapBasemap.Satellite -> "© Esri, Maxar, Earthstar Geographics"
                    BuildConfig.MAPTILER_API_KEY.isNotBlank() -> "© MapTiler · © OpenStreetMap"
                    else -> "© OpenStreetMap · © CARTO"
                },
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

    // Draw completed pins first and the current pin last, so the emphasised current marker (and its
    // halo) is never clipped by a neighbour laid down after it.
    val ordered = remember(placed) {
        placed.sortedBy { (marker, _) ->
            when (marker.state) {
                MarkerState.Completed -> 0
                MarkerState.Upcoming -> 1
                MarkerState.Current -> 2
            }
        }
    }
    val halfSlot = with(density) { MARKER_SLOT.toPx() / 2f }

    Box(modifier) {
        ordered.forEach { (marker, offset) ->
            // Every marker fills the same halo-sized slot with its badge centred, so the stop's
            // geographic point is the slot's centre: shift up and left by half the slot.
            MapPinMarker(
                marker = marker,
                onClick = { onMarkerClick(marker) },
                modifier = Modifier.offset {
                    IntOffset(
                        x = offset.x - halfSlot.toInt(),
                        y = offset.y - halfSlot.toInt()
                    )
                }
            )
        }
    }
}

/**
 * One pin: a numbered status chip.
 *
 * The teardrop-with-a-glyph gave way to a numbered circle in three weights (the numbered-plus-status
 * decision), so the map and the itinerary sheet tell one story — a glance reads *which stop* and
 * *where it stands*, and the number is the same on both. Weight, not hue, carries status:
 *
 *  - **Completed** — the smallest chip, a muted [com.tripcompanion.app.ui.theme.ExtendedColors]
 *    `statusCompleted` grey with a check instead of a number: done, and visibly behind the traveller.
 *  - **Upcoming** — a medium chip in the event's own category colour, present but quiet.
 *  - **Current** — the largest chip in the active accent, lifted by a shadow and ringed by a
 *    restrained halo: the one stop the eye should land on first.
 *
 * Every state fills the same [MARKER_SLOT] box with its badge centred, so [MarkerLayer] anchors
 * them all identically regardless of size.
 */
@Composable
private fun MapPinMarker(
    marker: MapMarker,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = AppThemeExtended.colors
    val isCurrent = marker.state == MarkerState.Current
    val isCompleted = marker.state == MarkerState.Completed

    val badgeColor = when (marker.state) {
        MarkerState.Current -> palette.accent
        MarkerState.Upcoming -> marker.color
        MarkerState.Completed -> palette.statusCompleted
    }
    val badgeSize = when (marker.state) {
        MarkerState.Current -> CURRENT_BADGE
        MarkerState.Upcoming -> UPCOMING_BADGE
        MarkerState.Completed -> COMPLETED_BADGE
    }

    Box(modifier.size(MARKER_SLOT), contentAlignment = Alignment.Center) {
        // The halo is the current pin's alone — a soft accent ring that says "here" without the
        // heavy pin-drop the brief rules out.
        if (isCurrent) {
            Box(
                Modifier
                    .size(CURRENT_HALO)
                    .clip(CircleShape)
                    .background(palette.accent.copy(alpha = 0.18f))
            )
        }
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = badgeColor,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface),
            shadowElevation = when {
                isCurrent -> 6.dp
                isCompleted -> 1.dp
                else -> 3.dp
            },
            modifier = Modifier.size(badgeSize)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (isCompleted) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(badgeSize * 0.52f)
                    )
                } else {
                    Text(
                        text = marker.number?.toString() ?: "•",
                        style = AppThemeExtended.text.badge,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * The device's own position, drawn unlike any itinerary pin.
 *
 * A small filled dot marks the exact fix; a faint ring around it shows the reported accuracy at the
 * current zoom, so a coarse fix looks coarse rather than falsely precise. Coloured with the theme's
 * `info` blue — kept distinct from the accent that fills the route and the current-stop pin, so
 * "where I am" never reads as "where I'm going". Re-projected on every [cameraTick], exactly like
 * [MarkerLayer].
 *
 * The ring radius is worked out by projecting a point [accuracyMeters] due east of the fix and
 * measuring the pixel gap, so it scales with zoom without a metres-per-pixel constant.
 */
@Composable
private fun CurrentLocationLayer(
    map: MapView?,
    latitude: Double,
    longitude: Double,
    accuracyMeters: Float?,
    cameraTick: Int,
    modifier: Modifier = Modifier
) {
    if (map == null) return
    val projection = map.projection
    val density = LocalDensity.current
    val dotColor = AppThemeExtended.colors.info

    val placed = remember(cameraTick, latitude, longitude, accuracyMeters, projection) {
        val center = Point()
        projection.toPixels(GeoPoint(latitude, longitude), center)
        val radiusPx = accuracyMeters?.takeIf { it > 0f }?.let { acc ->
            val edge = Point()
            projection.toPixels(
                GeoPoint(latitude, longitude).destinationPoint(acc.toDouble(), 90.0),
                edge
            )
            hypot((edge.x - center.x).toDouble(), (edge.y - center.y).toDouble()).toFloat()
        }
        Triple(center.x, center.y, radiusPx)
    }
    val (centerX, centerY, radiusPx) = placed

    Box(modifier) {
        // Accuracy ring, only when it is meaningfully larger than the dot — a three-metre ring under
        // a 16dp dot is noise, not information.
        if (radiusPx != null) {
            val ringDiameter = with(density) { (radiusPx * 2f).toDp() }
            if (ringDiameter > GPS_DOT) {
                Box(
                    Modifier
                        .offset { IntOffset(centerX - radiusPx.toInt(), centerY - radiusPx.toInt()) }
                        .size(ringDiameter)
                        .clip(CircleShape)
                        .background(dotColor.copy(alpha = 0.14f))
                        .border(1.dp, dotColor.copy(alpha = 0.35f), CircleShape)
                )
            }
        }
        val dotPx = with(density) { GPS_DOT.toPx() }
        Surface(
            shape = CircleShape,
            color = dotColor,
            border = BorderStroke(2.dp, Color.White),
            shadowElevation = 2.dp,
            modifier = Modifier
                .offset { IntOffset(centerX - (dotPx / 2f).toInt(), centerY - (dotPx / 2f).toInt()) }
                .size(GPS_DOT)
        ) {}
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
 * One floating map control — reused by the map screen for its layers, recenter and navigate
 * buttons, so the whole set lifts off the tiles by the same amount and reads as one family.
 *
 * Round, not the app's rounded rectangle. Everywhere else a card is a container for content and
 * the shared [AppCard] is right; here the button floats over a photograph-like surface with
 * nothing to align to, and a circle is the shape that reads as a control rather than as a very
 * small card. [tint] lets a caller colour the glyph — the navigate arrow goes green — while the
 * circle itself stays the surface colour so the family holds together.
 */
@Composable
fun MapControlButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    size: Dp = AppThemeExtended.metrics.controlHeight
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = AppThemeExtended.metrics.cardElevation,
        modifier = modifier.size(size)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * The zoom control: one rounded pill with + above − and a hairline between, per the mockup.
 *
 * A single pill rather than two circles, because zoom-in and zoom-out are one control with two
 * ends and the mockup draws them joined. Each half is a full-width square tap target, so the
 * touch area is the whole end of the pill, not just the glyph.
 */
@Composable
private fun ZoomControl(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val metrics = AppThemeExtended.metrics
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = metrics.cardElevation,
        modifier = modifier.width(metrics.controlHeight)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ZoomButton(Icons.Default.Add, "Zoom in", onZoomIn)
            Box(
                Modifier
                    .padding(horizontal = 10.dp)
                    .fillMaxWidth()
                    .height(metrics.dividerWidth)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            ZoomButton(Icons.Default.Remove, "Zoom out", onZoomOut)
        }
    }
}

@Composable
private fun ZoomButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(AppThemeExtended.metrics.controlHeight)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Frame a set of points, or fall back to a close focus.
 *
 * More than one point: fit the bounding box with a margin so the whole day is on screen. A box whose
 * span is a few metres — every stop in one building — would zoom to the max and read as a bug, so a
 * near-degenerate box is treated as a single point. Posted when the map has not been laid out yet,
 * because [MapView.zoomToBoundingBox] needs real view dimensions and the first recenter can arrive
 * before the first layout.
 */
private fun MapView.fitToPoints(points: List<GeoPoint>, focusZoom: Double, fallback: GeoPoint) {
    val run = Runnable {
        when {
            points.size > 1 -> {
                val box = BoundingBox.fromGeoPointsSafe(points)
                val latSpan = box.latNorth - box.latSouth
                val lonSpan = abs(box.lonEast - box.lonWest)
                if (latSpan < MIN_FIT_SPAN_DEG && lonSpan < MIN_FIT_SPAN_DEG) {
                    controller.setZoom(focusZoom)
                    controller.animateTo(points.first())
                } else {
                    zoomToBoundingBox(box, true, FIT_PADDING_PX)
                }
            }
            points.size == 1 -> {
                controller.setZoom(focusZoom)
                controller.animateTo(points.first())
            }
            else -> {
                controller.setZoom(focusZoom)
                controller.animateTo(fallback)
            }
        }
    }
    if (width == 0 || height == 0) post(run) else run.run()
}

/**
 * Marker sizing. Every pin is drawn inside a [MARKER_SLOT] box so all three states anchor
 * identically; the badge is one of three diameters by status, and only [MarkerState.Current] fills
 * the slot with a [CURRENT_HALO] ring. The slot equals the halo so the largest marker is never
 * clipped by its own anchoring box.
 */
private val COMPLETED_BADGE = 26.dp
private val UPCOMING_BADGE = 32.dp
private val CURRENT_BADGE = 40.dp
private val CURRENT_HALO = 60.dp
private val MARKER_SLOT = CURRENT_HALO

/** The device-location dot's diameter — distinct from the numbered pins, so GPS never reads as a stop. */
private val GPS_DOT = 16.dp

/**
 * Stroke of the itinerary route and its casing. The casing is wider so a darker line shows as a lip
 * on both sides of the accent route, lifting it off busy tiles; both are wide enough to read, not a
 * highway.
 */
private val ROUTE_WIDTH = 6.dp
private val ROUTE_CASING_WIDTH = 10.dp

private const val INITIAL_ZOOM = 5.0
private const val FOCUS_ZOOM = 16.0
private const val MIN_ZOOM = 3.0
private const val MAX_ZOOM = 19.0

/** Camera timing and framing for the fit-the-day / animate-to-stop moves. */
private const val CAMERA_ANIM_MS = 800L
private const val FIT_PADDING_PX = 140

/** Below this span (~400 m) a "day" is effectively one point: a fit would zoom to the max and jar. */
private const val MIN_FIT_SPAN_DEG = 0.004
