package com.pakmap.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import java.net.URL

private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val SAT_TILES =
    "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
private const val TERRARIUM =
    "https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png"

private val PAKISTAN_CENTER = LatLng(30.0, 69.5)
private val PAKISTAN_GREEN = Color(0xFF01411C)

private var libertyJson: String? = null

private fun demSource(): JSONObject = JSONObject()
    .put("type", "raster-dem")
    .put("tiles", JSONArray().put(TERRARIUM))
    .put("tileSize", 256)
    .put("encoding", "terrarium")
    .put("maxzoom", 15)

private fun hillshadeLayer(source: String): JSONObject = JSONObject()
    .put("id", "pak-hillshade")
    .put("type", "hillshade")
    .put("source", source)
    .put(
        "paint", JSONObject()
            .put("hillshade-exaggeration", 0.7)
            .put("hillshade-shadow-color", "#3b3b3b")
            .put("hillshade-highlight-color", "#ffffff")
            .put("hillshade-accent-color", "#555555")
    )

// Satellite style (optionally with terrain shading for 3D look)
private fun satelliteStyle(hillshade: Boolean): String {
    val sources = JSONObject().put(
        "sat", JSONObject()
            .put("type", "raster")
            .put("tiles", JSONArray().put(SAT_TILES))
            .put("tileSize", 256)
            .put("maxzoom", 18)
            .put("attribution", "Esri World Imagery")
    )
    val layers = JSONArray().put(
        JSONObject().put("id", "sat").put("type", "raster").put("source", "sat")
    )
    if (hillshade) {
        sources.put("dem", demSource())
        layers.put(hillshadeLayer("dem"))
    }
    return JSONObject()
        .put("version", 8)
        .put("glyphs", "https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf")
        .put("sources", sources)
        .put("layers", layers)
        .toString()
}

// Normal map style + terrain shading (downloads base style once, then reuses it)
private fun normalStyleWithHillshade(): String? {
    return try {
        val base = libertyJson ?: URL(STYLE_URL).readText().also { libertyJson = it }
        val root = JSONObject(base)
        root.getJSONObject("sources").put("pak-dem", demSource())
        val layers = root.getJSONArray("layers")
        val out = JSONArray()
        var inserted = false
        for (i in 0 until layers.length()) {
            val layer = layers.getJSONObject(i)
            if (!inserted && layer.optString("type") == "symbol") {
                out.put(hillshadeLayer("pak-dem"))
                inserted = true
            }
            out.put(layer)
        }
        if (!inserted) out.put(hillshadeLayer("pak-dem"))
        root.put("layers", out)
        root.toString()
    } catch (e: Exception) {
        null
    }
}

private val LABEL_FONT = arrayOf("Noto Sans Regular")

// Adds all Pakistan data (points + river lines) on top of any map style
private fun addPlaceLayers(style: Style, geoJson: String) {
    style.addSource(GeoJsonSource("pak-places", geoJson))

    // River lines
    style.addLayer(
        LineLayer("pak-rivers", "pak-places")
            .withFilter(Expression.eq(Expression.geometryType(), Expression.literal("LineString")))
            .withProperties(
                PropertyFactory.lineColor(Places.categories.first { it.id == "rivers" }.color),
                PropertyFactory.lineWidth(2.5f),
                PropertyFactory.lineCap("round"),
                PropertyFactory.lineJoin("round")
            )
    )
    style.addLayer(
        SymbolLayer("pak-river-labels", "pak-places")
            .withFilter(Expression.eq(Expression.geometryType(), Expression.literal("LineString")))
            .withProperties(
                PropertyFactory.symbolPlacement("line"),
                PropertyFactory.textField(Expression.get("name")),
                PropertyFactory.textFont(LABEL_FONT),
                PropertyFactory.textSize(11f),
                PropertyFactory.textColor(Places.categories.first { it.id == "rivers" }.color),
                PropertyFactory.textHaloColor(android.graphics.Color.WHITE),
                PropertyFactory.textHaloWidth(1.5f)
            )
    )

    // One colored dot layer per point category
    for (cat in Places.categories) {
        if (cat.id == "rivers") continue
        style.addLayer(
            CircleLayer("pak-dots-" + cat.id, "pak-places")
                .withFilter(Expression.eq(Expression.get("category"), Expression.literal(cat.id)))
                .withProperties(
                    PropertyFactory.circleColor(cat.color),
                    PropertyFactory.circleRadius(6f),
                    PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
                    PropertyFactory.circleStrokeWidth(1.5f)
                )
        )
    }

    // Names of points (visible when zoomed in a little)
    style.addLayer(
        SymbolLayer("pak-point-labels", "pak-places")
            .withFilter(Expression.eq(Expression.geometryType(), Expression.literal("Point")))
            .withProperties(
                PropertyFactory.textField(Expression.get("name")),
                PropertyFactory.textFont(LABEL_FONT),
                PropertyFactory.textSize(11f),
                PropertyFactory.textOffset(arrayOf(0f, 0.9f)),
                PropertyFactory.textAnchor("top"),
                PropertyFactory.textColor(android.graphics.Color.parseColor("#222222")),
                PropertyFactory.textHaloColor(android.graphics.Color.WHITE),
                PropertyFactory.textHaloWidth(1.5f),
                PropertyFactory.textOptional(true)
            )
    )
}

@Composable
fun MapScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember { MapView(context) }
    val geoJson = remember { Places.geoJson(context) }

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var is3D by remember { mutableStateOf(false) }
    var satellite by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    // Style + camera change whenever 2D/3D or Normal/Satellite changes
    LaunchedEffect(map, satellite, is3D) {
        val m = map ?: return@LaunchedEffect

        if (satellite) {
            m.setStyle(Style.Builder().fromJson(satelliteStyle(is3D))) { st -> addPlaceLayers(st, geoJson) }
        } else if (!is3D) {
            m.setStyle(Style.Builder().fromUri(STYLE_URL)) { st -> addPlaceLayers(st, geoJson) }
        } else {
            val json = withContext(Dispatchers.IO) { normalStyleWithHillshade() }
            if (json != null) {
                m.setStyle(Style.Builder().fromJson(json)) { st -> addPlaceLayers(st, geoJson) }
            } else {
                m.setStyle(Style.Builder().fromUri(STYLE_URL)) { st -> addPlaceLayers(st, geoJson) }
            }
        }

        m.uiSettings.isTiltGesturesEnabled = is3D
        val cp = m.cameraPosition
        val target = CameraPosition.Builder(cp)
            .tilt(if (is3D) 60.0 else 0.0)
            .bearing(if (is3D) cp.bearing else 0.0)
            .build()
        m.animateCamera(CameraUpdateFactory.newCameraPosition(target), 800)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                mapView.apply {
                    getMapAsync { m ->
                        m.cameraPosition = CameraPosition.Builder()
                            .target(PAKISTAN_CENTER)
                            .zoom(5.0)
                            .build()
                        m.setMinZoomPreference(4.5)
                        m.setLatLngBoundsForCameraTarget(
                            LatLngBounds.from(38.5, 79.0, 22.0, 59.5)
                        )
                        m.uiSettings.isTiltGesturesEnabled = false
                        map = m
                    }
                }
            }
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("2D", !is3D) { is3D = false }
                Pill("3D", is3D) { is3D = true }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Normal", !satellite) { satellite = false }
                Pill("Satellite", satellite) { satellite = true }
            }
        }
    }
}

@Composable
private fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) PAKISTAN_GREEN else Color.White,
            contentColor = if (selected) Color.White else PAKISTAN_GREEN
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
    ) {
        Text(text)
    }
}
