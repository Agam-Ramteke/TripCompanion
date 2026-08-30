package com.tripcompanion.app.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tripcompanion.app.BuildConfig
import com.tripcompanion.app.domain.model.DayRouteLeg
import com.tripcompanion.app.domain.model.EventType
import com.tripcompanion.app.domain.model.RouteLegStatus
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

private const val ROUTE_COMPLETED_SOURCE_ID = "trip-vec-completed-source"
private const val ROUTE_COMPLETED_CASING_LAYER_ID = "trip-vec-completed-casing"
private const val ROUTE_COMPLETED_LINE_LAYER_ID = "trip-vec-completed-line"

private const val ROUTE_CURRENT_SOURCE_ID = "trip-vec-current-source"
private const val ROUTE_CURRENT_CASING_LAYER_ID = "trip-vec-current-casing"
private const val ROUTE_CURRENT_LINE_LAYER_ID = "trip-vec-current-line"

private const val ROUTE_FUTURE_SOURCE_ID = "trip-vec-future-source"
private const val ROUTE_FUTURE_CASING_LAYER_ID = "trip-vec-future-casing"
private const val ROUTE_FUTURE_LINE_LAYER_ID = "trip-vec-future-line"

data class VectorMarker(
    val id: Long,
    val latitude: Double,
    val longitude: Double,
    val label: String,
    val eventType: EventType,
    val color: Color,
    val isFocused: Boolean = false,
    val isCompleted: Boolean = false,
    val orderNumber: Int? = null
)

/**
 * High-performance MapLibre Native Vector Map renderer powered by Geoapify Vector Styles.
 */
@Composable
fun TripVectorMap(
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier,
    recenterTrigger: Int = 0,
    interactive: Boolean = true,
    initialZoom: Double = 12.5,
    focusZoom: Double = 15.5,
    controlsPadding: PaddingValues = PaddingValues(end = 12.dp),
    markers: List<VectorMarker> = emptyList(),
    onMarkerClick: (VectorMarker) -> Unit = {},
    legs: List<DayRouteLeg> = emptyList(),
    zoomAlignment: Alignment = Alignment.CenterEnd,
    bottomInset: Dp = 0.dp,
    animateToTrigger: Int = 0,
    animateToLatitude: Double? = null,
    animateToLongitude: Double? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Initialize MapLibre instance
    remember {
        MapLibre.getInstance(context)
    }

    val geoapifyKey = BuildConfig.GEOAPIFY_API_KEY
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val targetStyleUri = remember(isDark, geoapifyKey) {
        GeoapifyVectorStyles.getStyleUrl(isDark = isDark, apiKey = geoapifyKey, useFallback = false)
    }

    var maplibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var cameraTick by remember { mutableIntStateOf(0) }
    var currentLoadedStyleUri by remember { mutableStateOf<String?>(null) }

    // Lifecycle handling
    DisposableEffect(lifecycleOwner, mapViewRef) {
        val mapView = mapViewRef
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView?.onStart()
                Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                Lifecycle.Event.ON_STOP -> mapView?.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView?.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView?.onDestroy()
        }
    }

    // Camera animation for stop focus
    LaunchedEffect(animateToTrigger) {
        if (animateToTrigger > 0 && animateToLatitude != null && animateToLongitude != null) {
            maplibreMap?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(animateToLatitude, animateToLongitude),
                    focusZoom
                ),
                800
            )
        }
    }

    // Camera framing for full day route
    LaunchedEffect(recenterTrigger) {
        if (recenterTrigger > 0) {
            val map = maplibreMap ?: return@LaunchedEffect
            val allCoordinates = when {
                legs.isNotEmpty() -> legs.flatMap { it.points.map { pt -> LatLng(pt.first, pt.second) } }
                markers.isNotEmpty() -> markers.map { LatLng(it.latitude, it.longitude) }
                else -> listOf(LatLng(latitude, longitude))
            }

            if (allCoordinates.size > 1) {
                val builder = LatLngBounds.Builder()
                allCoordinates.forEach { builder.include(it) }
                val bounds = builder.build()
                val view = mapViewRef
                if (view != null && (view.width == 0 || view.height == 0)) {
                    view.post {
                        try {
                            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 48), 800)
                        } catch (_: Exception) {
                            map.animateCamera(CameraUpdateFactory.newLatLngZoom(allCoordinates.first(), focusZoom), 800)
                        }
                    }
                } else {
                    try {
                        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 48), 800)
                    } catch (_: Exception) {
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(allCoordinates.first(), focusZoom), 800)
                    }
                }
            } else if (allCoordinates.size == 1) {
                map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(allCoordinates.first(), focusZoom),
                    800
                )
            }
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    onCreate(null)
                    getMapAsync { map ->
                        maplibreMap = map
                        map.uiSettings.apply {
                            isAttributionEnabled = false
                            isLogoEnabled = false
                            isCompassEnabled = false
                            isRotateGesturesEnabled = interactive
                            isTiltGesturesEnabled = interactive
                            isZoomGesturesEnabled = interactive
                            isScrollGesturesEnabled = interactive
                        }

                        map.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(latitude, longitude))
                            .zoom(initialZoom)
                            .build()

                        map.setStyle(Style.Builder().fromUri(targetStyleUri)) { style ->
                            currentLoadedStyleUri = targetStyleUri
                            updateVectorRouteLayers(style, legs, isDark)
                            cameraTick++
                        }

                        map.addOnCameraMoveStartedListener { cameraTick++ }
                        map.addOnCameraMoveListener { cameraTick++ }
                        map.addOnCameraIdleListener { cameraTick++ }
                        map.addOnCameraMoveCancelListener { cameraTick++ }
                    }
                    mapViewRef = this
                }
            },
            update = { _ ->
                maplibreMap?.let { map ->
                    map.getStyle { style ->
                        if (currentLoadedStyleUri != targetStyleUri) {
                            map.setStyle(Style.Builder().fromUri(targetStyleUri)) { updatedStyle ->
                                currentLoadedStyleUri = targetStyleUri
                                updateVectorRouteLayers(updatedStyle, legs, isDark)
                                cameraTick++
                            }
                        } else {
                            updateVectorRouteLayers(style, legs, isDark)
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Reactive Compose Marker Overlay
        maplibreMap?.let { map ->
            val placedMarkers = remember(cameraTick, markers, map) {
                markers.mapNotNull { marker ->
                    try {
                        val pt = map.projection.toScreenLocation(LatLng(marker.latitude, marker.longitude))
                        if (!pt.x.isNaN() && !pt.y.isNaN()) marker to pt else null
                    } catch (_: Exception) {
                        null
                    }
                }
            }
            placedMarkers.forEach { (marker, point) ->
                TeardropPinMarker(
                    title = marker.label,
                    eventType = marker.eventType,
                    pinColor = marker.color,
                    isFocused = marker.isFocused,
                    isCompleted = marker.isCompleted,
                    orderNumber = marker.orderNumber,
                    screenX = point.x,
                    screenY = point.y,
                    onClick = { onMarkerClick(marker) }
                )
            }
        }

        // Attribution
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = bottomInset + 8.dp)
        ) {
            Text(
                text = if (geoapifyKey.isNotBlank()) "© Geoapify · © OpenStreetMap" else "© OpenStreetMap contributors",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

private fun updateVectorRouteLayers(style: Style, legs: List<DayRouteLeg>, isDark: Boolean) {
    val completedLegs = legs.filter { it.status == RouteLegStatus.COMPLETED }
    val currentLegs = legs.filter { it.status == RouteLegStatus.CURRENT }
    val futureLegs = legs.filter { it.status == RouteLegStatus.FUTURE }

    val completedLine = if (isDark) AndroidColor.parseColor("#52736C") else AndroidColor.parseColor("#7E948E")
    val completedCasing = if (isDark) AndroidColor.parseColor("#2A3F3B") else AndroidColor.parseColor("#50615C")
    updateRouteSource(style, ROUTE_COMPLETED_SOURCE_ID, ROUTE_COMPLETED_CASING_LAYER_ID, ROUTE_COMPLETED_LINE_LAYER_ID, completedLegs, completedLine, completedCasing, 4.5f, 7.5f)

    val futureLine = if (isDark) AndroidColor.parseColor("#6D8F87") else AndroidColor.parseColor("#9BB6AF")
    val futureCasing = if (isDark) AndroidColor.parseColor("#384D47") else AndroidColor.parseColor("#6F857F")
    updateRouteSource(style, ROUTE_FUTURE_SOURCE_ID, ROUTE_FUTURE_CASING_LAYER_ID, ROUTE_FUTURE_LINE_LAYER_ID, futureLegs, futureLine, futureCasing, 4.0f, 6.5f)

    val currentLine = if (isDark) AndroidColor.parseColor("#26C6DA") else AndroidColor.parseColor("#2F5D50")
    val currentCasing = if (isDark) AndroidColor.parseColor("#061D22") else AndroidColor.parseColor("#132B25")
    updateRouteSource(style, ROUTE_CURRENT_SOURCE_ID, ROUTE_CURRENT_CASING_LAYER_ID, ROUTE_CURRENT_LINE_LAYER_ID, currentLegs, currentLine, currentCasing, 6.5f, 10.5f)
}

private fun updateRouteSource(
    style: Style,
    sourceId: String,
    casingLayerId: String,
    lineLayerId: String,
    legs: List<DayRouteLeg>,
    lineColor: Int,
    casingColor: Int,
    lineWidth: Float,
    casingWidth: Float
) {
    val features = legs.mapNotNull { leg ->
        if (leg.points.size >= 2) {
            val coords = leg.points.map { Point.fromLngLat(it.second, it.first) }
            Feature.fromGeometry(LineString.fromLngLats(coords))
        } else null
    }
    val featureCollection = FeatureCollection.fromFeatures(features)
    var source = style.getSourceAs<GeoJsonSource>(sourceId)

    if (source == null) {
        source = GeoJsonSource(sourceId, featureCollection)
        style.addSource(source)

        val casingLayer = LineLayer(casingLayerId, sourceId).apply {
            setProperties(
                PropertyFactory.lineColor(casingColor),
                PropertyFactory.lineWidth(casingWidth),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        }
        style.addLayer(casingLayer)

        val lineLayer = LineLayer(lineLayerId, sourceId).apply {
            setProperties(
                PropertyFactory.lineColor(lineColor),
                PropertyFactory.lineWidth(lineWidth),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        }
        style.addLayer(lineLayer)
    } else {
        source.setGeoJson(featureCollection)
        style.getLayerAs<LineLayer>(casingLayerId)?.setProperties(PropertyFactory.lineColor(casingColor))
        style.getLayerAs<LineLayer>(lineLayerId)?.setProperties(PropertyFactory.lineColor(lineColor))
    }
}
