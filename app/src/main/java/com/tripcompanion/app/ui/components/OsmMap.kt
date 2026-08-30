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
import com.tripcompanion.app.domain.model.DayRouteLeg
import com.tripcompanion.app.domain.model.EventType
import com.tripcompanion.app.domain.model.RouteLegStatus
import com.tripcompanion.app.ui.theme.AppThemeExtended
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import kotlin.math.abs
import kotlin.math.hypot

/** Humanitarian OSM warm pastel tile source — free and key-less fallback. */
private val OsmHotTiles = XYTileSource(
    "osm-hot",
    1,
    19,
    256,
    ".png",
    arrayOf(
        "https://a.tile.openstreetmap.fr/hot/",
        "https://b.tile.openstreetmap.fr/hot/"
    ),
    "© OpenStreetMap contributors, Humanitarian OpenStreetMap Team"
)

/**
 * Esri World Imagery — photographic satellite tiles, free and key-less.
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
 * LocationIQ raster street tile source.
 */
private fun locationIqStreetSource(key: String): OnlineTileSourceBase =
    object : OnlineTileSourceBase(
        "locationiq-streets",
        1,
        19,
        256,
        ".png",
        arrayOf("https://tiles.locationiq.com/v3/streets/r/"),
        "© LocationIQ · © OpenStreetMap contributors"
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val z = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "https://tiles.locationiq.com/v3/streets/r/$z/$x/$y.png?key=$key"
        }
    }

/**
 * Geoapify raster tile source (osm-bright-smooth / positron / dark-matter).
 */
private fun geoapifySource(styleId: String, key: String): OnlineTileSourceBase =
    object : OnlineTileSourceBase(
        "geoapify-$styleId",
        1,
        20,
        256,
        ".png",
        arrayOf("https://maps.geoapify.com/v1/tile/$styleId/"),
        "© OpenStreetMap contributors © Geoapify"
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val z = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "https://maps.geoapify.com/v1/tile/$styleId/$z/$x/$y.png?apiKey=$key"
        }
    }

/** Which basemap [TripMap] draws: the themed raster (Geoapify when keyed, else Mapnik), or Esri satellite. */
enum class MapBasemap { Standard, Satellite }

/**
 * A stop's progress, which decides how prominently its pin is drawn.
 */
enum class MarkerState { Completed, Upcoming, Current }

/**
 * One pin drawn over the map.
 */
data class MapMarker(
    val id: Long,
    val latitude: Double,
    val longitude: Double,
    val label: String,
    val number: Int? = null,
    val color: Color,
    val eventType: EventType = EventType.VISIT,
    val state: MarkerState = MarkerState.Upcoming
)

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
    controlsPadding: PaddingValues = PaddingValues(end = 12.dp),
    crosshairBottomPadding: Dp = 0.dp,
    showCrosshair: Boolean = crosshairBottomPadding > 0.dp,
    markers: List<MapMarker> = emptyList(),
    onMarkerClick: (MapMarker) -> Unit = {},
    routePoints: List<GeoPoint> = emptyList(),
    legs: List<DayRouteLeg> = emptyList(),
    routeColor: Color = Color.Unspecified,
    basemap: MapBasemap = MapBasemap.Standard,
    zoomAlignment: Alignment = Alignment.CenterEnd,
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
    val density = LocalDensity.current

    val locationIqKey = BuildConfig.LOCATIONIQ_API_KEY
    val geoapifyKey = BuildConfig.GEOAPIFY_API_KEY
    val isDarkSurface = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val tiles = remember(basemap, isDarkSurface, locationIqKey, geoapifyKey) {
        when (basemap) {
            MapBasemap.Satellite -> SatelliteTiles
            MapBasemap.Standard -> {
                when {
                    locationIqKey.isNotBlank() -> locationIqStreetSource(locationIqKey)
                    geoapifyKey.isNotBlank() -> {
                        val style = if (isDarkSurface) "dark-matter" else "osm-bright-smooth"
                        geoapifySource(style, geoapifyKey)
                    }
                    else -> if (isDarkSurface) TileSourceFactory.MAPNIK else OsmHotTiles
                }
            }
        }
    }

    val accent = if (isDarkSurface) Color(0xFF26C6DA) else Color(0xFF2F5D50)
    val resolvedRouteColor = if (routeColor.isSpecified) routeColor else accent
    val routeArgb = resolvedRouteColor.toArgb()
    val casingArgb = if (isDarkSurface) Color(0xFF082226).toArgb() else Color(0xFF132B25).toArgb()
    val routeWidthPx = with(density) { ROUTE_WIDTH.toPx() }
    val casingWidthPx = with(density) { ROUTE_CASING_WIDTH.toPx() }
    var routeRef by remember { mutableStateOf<Polyline?>(null) }
    var casingRef by remember { mutableStateOf<Polyline?>(null) }

    var cameraTick by remember { mutableIntStateOf(0) }
    val initialCenter = remember { GeoPoint(latitude, longitude) }

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
            val map = mapRef ?: return@LaunchedEffect
            val allPoints = when {
                legs.isNotEmpty() -> legs.flatMap { leg -> leg.points.map { GeoPoint(it.first, it.second) } }
                routePoints.size > 1 -> routePoints
                markers.isNotEmpty() -> markers.map { GeoPoint(it.latitude, it.longitude) }
                else -> listOf(GeoPoint(latitude, longitude))
            }
            map.fitToPoints(
                points = allPoints,
                focusZoom = focusZoom,
                fallback = GeoPoint(latitude, longitude)
            )
        }
    }

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
                    overlayManager.tilesOverlay.setColorFilter(null)
                    setMultiTouchControls(interactive)
                    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                    setTilesScaledToDpi(true)
                    setMinZoomLevel(MIN_ZOOM)
                    setMaxZoomLevel(MAX_ZOOM)
                    controller.setZoom(initialZoom)
                    controller.setCenter(initialCenter)

                    if (!interactive) {
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

                        private fun report() {
                            mapCenter?.let { onCenterChanged(it.latitude, it.longitude) }
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

                    val casing = Polyline(this).apply {
                        outlinePaint.color = casingArgb
                        outlinePaint.strokeWidth = casingWidthPx
                        outlinePaint.isAntiAlias = true
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                        outlinePaint.strokeJoin = Paint.Join.ROUND
                        infoWindow = null
                        setOnClickListener { _, _, _ -> true }
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
                        infoWindow = null
                        setOnClickListener { _, _, _ -> true }
                        isVisible = false
                    }
                    overlays.add(route)
                    routeRef = route

                    mapRef = this
                }
            },
            update = { map ->
                if (map.tileProvider.tileSource.name() != tiles.name()) {
                    map.setTileSource(tiles)
                }

                // Render multi-leg overlays if legs are provided
                if (legs.isNotEmpty()) {
                    // Hide single fallback polyline
                    casingRef?.isVisible = false
                    routeRef?.isVisible = false

                    // Remove existing dynamic leg polylines
                    map.overlays.removeAll { it is Polyline && it != routeRef && it != casingRef }

                    legs.forEach { leg ->
                        val (coreColor, casingColor, coreWidth, casingWidth) = when (leg.status) {
                            RouteLegStatus.COMPLETED -> {
                                val core = if (isDarkSurface) Color(0xFF52736C).toArgb() else Color(0xFF7E948E).toArgb()
                                val casing = if (isDarkSurface) Color(0xFF2A3F3B).toArgb() else Color(0xFF50615C).toArgb()
                                val w = with(density) { 4.5.dp.toPx() }
                                val cw = with(density) { 7.5.dp.toPx() }
                                listOf(core, casing, w, cw)
                            }
                            RouteLegStatus.CURRENT -> {
                                val core = if (routeColor.isSpecified) routeColor.toArgb() else (if (isDarkSurface) Color(0xFF26C6DA).toArgb() else Color(0xFF2F5D50).toArgb())
                                val casing = if (isDarkSurface) Color(0xFF061D22).toArgb() else Color(0xFF132B25).toArgb()
                                val w = with(density) { 6.5.dp.toPx() }
                                val cw = with(density) { 10.5.dp.toPx() }
                                listOf(core, casing, w, cw)
                            }
                            RouteLegStatus.FUTURE -> {
                                val core = if (isDarkSurface) Color(0xFF6D8F87).toArgb() else Color(0xFF9BB6AF).toArgb()
                                val casing = if (isDarkSurface) Color(0xFF384D47).toArgb() else Color(0xFF6F857F).toArgb()
                                val w = with(density) { 4.0.dp.toPx() }
                                val cw = with(density) { 6.5.dp.toPx() }
                                listOf(core, casing, w, cw)
                            }
                        }

                        val pts = leg.points.map { GeoPoint(it.first, it.second) }
                        if (pts.size >= 2) {
                            val legCasing = Polyline(map).apply {
                                outlinePaint.color = casingColor as Int
                                outlinePaint.strokeWidth = casingWidth as Float
                                outlinePaint.isAntiAlias = true
                                outlinePaint.strokeCap = Paint.Cap.ROUND
                                outlinePaint.strokeJoin = Paint.Join.ROUND
                                infoWindow = null
                                setOnClickListener { _, _, _ -> true }
                                setPoints(pts)
                            }
                            val legLine = Polyline(map).apply {
                                outlinePaint.color = coreColor as Int
                                outlinePaint.strokeWidth = coreWidth as Float
                                outlinePaint.isAntiAlias = true
                                outlinePaint.strokeCap = Paint.Cap.ROUND
                                outlinePaint.strokeJoin = Paint.Join.ROUND
                                infoWindow = null
                                setOnClickListener { _, _, _ -> true }
                                setPoints(pts)
                            }
                            map.overlays.add(legCasing)
                            map.overlays.add(legLine)
                        }
                    }
                } else {
                    // Fallback to single polyline
                    map.overlays.removeAll { it is Polyline && it != routeRef && it != casingRef }
                    casingRef?.let { casing ->
                        casing.setPoints(routePoints)
                        casing.outlinePaint.color = casingArgb
                        casing.infoWindow = null
                        casing.setOnClickListener { _, _, _ -> true }
                        casing.isVisible = routePoints.size >= 2
                    }
                    routeRef?.let { route ->
                        route.setPoints(routePoints)
                        route.outlinePaint.color = routeArgb
                        route.infoWindow = null
                        route.setOnClickListener { _, _, _ -> true }
                        route.isVisible = routePoints.size >= 2
                    }
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

        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = bottomInset + 8.dp)
        ) {
            Text(
                text = when (basemap) {
                    MapBasemap.Satellite -> "© Esri, Maxar, Earthstar Geographics"
                    MapBasemap.Standard -> when {
                        locationIqKey.isNotBlank() -> "© LocationIQ · © OpenStreetMap contributors"
                        geoapifyKey.isNotBlank() -> "© Geoapify · © OpenStreetMap"
                        else -> "© OpenStreetMap contributors"
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

/**
 * The pins, drawn in Compose using custom illustrated [TeardropPinMarker] components.
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
    val projection = map.projection ?: return

    val placed = remember(cameraTick, markers, projection) {
        val point = Point()
        markers.mapNotNull { marker ->
            try {
                projection.toPixels(GeoPoint(marker.latitude, marker.longitude), point)
                marker to (point.x.toFloat() to point.y.toFloat())
            } catch (_: Exception) {
                null
            }
        }
    }

    Box(modifier) {
        placed.forEach { (marker, coords) ->
            TeardropPinMarker(
                title = marker.label,
                eventType = marker.eventType,
                pinColor = marker.color,
                isFocused = marker.state == MarkerState.Current,
                isCompleted = marker.state == MarkerState.Completed,
                orderNumber = marker.number,
                screenX = coords.first,
                screenY = coords.second,
                onClick = { onMarkerClick(marker) }
            )
        }
    }
}

/**
 * The device's own position, drawn with accuracy radius ring and blue pulse dot.
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
    val projection = map.projection ?: return
    val density = LocalDensity.current
    val dotColor = AppThemeExtended.colors.info

    val placed = remember(cameraTick, latitude, longitude, accuracyMeters, projection) {
        try {
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
        } catch (_: Exception) {
            Triple(0, 0, null)
        }
    }
    val (centerX, centerY, radiusPx) = placed
    if (centerX == 0 && centerY == 0) return

    Box(modifier) {
        if (radiusPx != null) {
            val ringDiameter = with(density) { (radiusPx * 2f).toDp() }
            if (ringDiameter > GPS_DOT) {
                Box(
                    Modifier
                        .offset { IntOffset((centerX - radiusPx).toInt(), (centerY - radiusPx).toInt()) }
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
                .offset { IntOffset((centerX - (dotPx / 2f)).toInt(), (centerY - (dotPx / 2f)).toInt()) }
                .size(GPS_DOT)
        ) {}
    }
}

/**
 * Center target crosshair for location picking.
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
 * Floating map control button.
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
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
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
 * Zoom control pill (+ / -).
 */
@Composable
internal fun ZoomControl(
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
internal fun ZoomButton(icon: ImageVector, label: String, onClick: () -> Unit) {
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

private val GPS_DOT = 16.dp
private val ROUTE_WIDTH = 6.dp
private val ROUTE_CASING_WIDTH = 10.dp
private const val INITIAL_ZOOM = 12.5
private const val FOCUS_ZOOM = 15.5
private const val MIN_ZOOM = 3.0
private const val MAX_ZOOM = 20.0
private const val CAMERA_ANIM_MS = 800L
private const val FIT_PADDING_PX = 48
private const val MIN_FIT_SPAN_DEG = 0.004
