// SPDX-License-Identifier: GPL-3.0-or-later
package app.projecthaven.ui.screens.maps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.projecthaven.domain.geo.Coordinate
import app.projecthaven.domain.maps.PlaceRecord
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

private const val USER_SOURCE = "haven-user"
private const val POI_SOURCE = "haven-poi"

/**
 * MapLibre map rendering only local data (PMTiles from installed or bundled packs). The
 * style is generated in [app.projecthaven.data.maps.MapStyle]; no online tiles are used.
 */
@Composable
fun OfflineMap(
    styleJson: String,
    /** [west, south, east, north] to show first. */
    initialBounds: List<Double>?,
    user: Coordinate?,
    pois: List<PlaceRecord>,
    focus: Coordinate?,
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        MapView(context).apply { onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.uiSettings.isCompassEnabled = true
            m.uiSettings.isAttributionEnabled = true
            m.uiSettings.isLogoEnabled = false
            initialBounds?.takeIf { it.size == 4 }?.let { (w, s, e, n) ->
                m.moveCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.from(n, e, s, w), 48))
            }
            map = m
        }
    }

    LaunchedEffect(map, styleJson) {
        val m = map ?: return@LaunchedEffect
        style = null
        m.setStyle(Style.Builder().fromJson(styleJson)) { loaded ->
            val accent = if (darkTheme) "#0A84FF" else "#0066D6"
            val red = if (darkTheme) "#FF453A" else "#D70015"
            loaded.addSource(GeoJsonSource(POI_SOURCE))
            loaded.addLayer(
                CircleLayer("haven-poi-layer", POI_SOURCE).withProperties(
                    PropertyFactory.circleColor(red),
                    PropertyFactory.circleRadius(5f),
                    PropertyFactory.circleStrokeColor("#FFFFFF"),
                    PropertyFactory.circleStrokeWidth(1.5f),
                ),
            )
            loaded.addSource(GeoJsonSource(USER_SOURCE))
            loaded.addLayer(
                CircleLayer("haven-user-layer", USER_SOURCE).withProperties(
                    PropertyFactory.circleColor(accent),
                    PropertyFactory.circleRadius(8f),
                    PropertyFactory.circleStrokeColor("#FFFFFF"),
                    PropertyFactory.circleStrokeWidth(2.5f),
                ),
            )
            style = loaded
        }
    }

    LaunchedEffect(style, user) {
        val s = style ?: return@LaunchedEffect
        val source = s.getSourceAs<GeoJsonSource>(USER_SOURCE) ?: return@LaunchedEffect
        if (user == null) source.setGeoJson(FeatureCollection.fromFeatures(emptyList<Feature>()))
        else source.setGeoJson(Point.fromLngLat(user.longitude, user.latitude))
    }

    LaunchedEffect(style, pois) {
        val s = style ?: return@LaunchedEffect
        val source = s.getSourceAs<GeoJsonSource>(POI_SOURCE) ?: return@LaunchedEffect
        source.setGeoJson(FeatureCollection.fromFeatures(pois.map { Feature.fromGeometry(Point.fromLngLat(it.lon, it.lat)) }))
    }

    LaunchedEffect(map, focus) {
        val m = map ?: return@LaunchedEffect
        val f = focus ?: return@LaunchedEffect
        m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(f.latitude, f.longitude), 15.0))
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}
