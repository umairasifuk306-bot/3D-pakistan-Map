package com.pakmap.app

import android.graphics.RectF
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.rememberUpdatedState
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
import org.maplibre.android.style.layers.Property
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

// Show/hide categories on the map
private fun applyFilter(style: Style, enabled: Set<String>) {
    for (cat in Places.categories) {
        val vis = PropertyFactory.visibility(
            if (cat.id in enabled) Property.VISIBLE else Property.NONE
        )
        if (cat.id == "rivers") {
            style.getLayer("pak-rivers")?.setProperties(vis)
            style.getLayer("pak-river-labels")?.setProperties(vis)
        } else {
            style.getLayer("pak-dots-" + cat.id)?.setProperties(vis)
        }
    }
    val pointCats = Places.categories.filter { it.id != "rivers" && it.id in enabled }
    val filter = if (pointCats.isEmpty()) {
        Expression.literal(false)
    } else {
        Expression.all(
            Expression.eq(Expression.geometryType(), Expression.literal("Point")),
            Expression.any(
                *pointCats.map {
                    Expression.eq(Expression.get("category"), Expression.literal(it.id))
                }.toTypedArray()
            )
        )
    }
    (style.getLayer("pak-point-labels") as? SymbolLayer)?.setFilter(filter)
}

@Composable
fun MapScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusManager = LocalFocusManager.current
    val mapView = remember { MapView(context) }
    val geoJson = remember { Places.geoJson(context) }
    val items = remember { Places.load(context) }

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var is3D by remember { mutableStateOf(false) }
    var satellite by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<PlaceItem?>(null) }
    var enabled by remember { mutableStateOf(Places.categories.map { it.id }.toSet()) }
    val enabledNow by rememberUpdatedState(enabled)

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
        val onLoaded = { st: Style ->
            addPlaceLayers(st, geoJson)
            applyFilter(st, enabledNow)
        }

        if (satellite) {
            m.setStyle(Style.Builder().fromJson(satelliteStyle(is3D))) { st -> onLoaded(st) }
        } else if (!is3D) {
            m.setStyle(Style.Builder().fromUri(STYLE_URL)) { st -> onLoaded(st) }
        } else {
            val json = withContext(Dispatchers.IO) { normalStyleWithHillshade() }
            if (json != null) {
                m.setStyle(Style.Builder().fromJson(json)) { st -> onLoaded(st) }
            } else {
                m.setStyle(Style.Builder().fromUri(STYLE_URL)) { st -> onLoaded(st) }
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

    // Category filter changes
    LaunchedEffect(map, enabled) {
        val st = map?.style
        if (st != null && st.isFullyLoaded) applyFilter(st, enabled)
    }

    val goTo: (PlaceItem) -> Unit = { item ->
        selected = item
        query = ""
        focusManager.clearFocus()
        if (item.category !in enabled) enabled = enabled + item.category
        map?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(item.lat, item.lon),
                if (item.isLine) 7.0 else 9.0
            ),
            1200
        )
    }

    val results = remember(query) {
        if (query.isBlank()) {
            emptyList()
        } else {
            items.filter {
                it.name.contains(query, ignoreCase = true) ||
                    Places.label(it.category).contains(query, ignoreCase = true)
            }.take(8)
        }
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
                        val d = context.resources.displayMetrics.density
                        m.uiSettings.setCompassMargins(0, (150 * d).toInt(), (12 * d).toInt(), 0)

                        // Tap on a dot or river -> show details
                        m.addOnMapClickListener { latLng ->
                            val pt = m.projection.toScreenLocation(latLng)
                            val box = RectF(pt.x - 32f, pt.y - 32f, pt.x + 32f, pt.y + 32f)
                            val layerIds = Places.categories.map {
                                if (it.id == "rivers") "pak-rivers" else "pak-dots-" + it.id
                            }.toTypedArray()
                            val name = m.queryRenderedFeatures(box, *layerIds)
                                .firstOrNull()?.getStringProperty("name")
                            val hit = if (name != null) items.firstOrNull { it.name == name } else null
                            selected = hit
                            hit != null
                        }
                        map = m
                    }
                }
            }
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Search bar
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search dam, fort, lake, K2...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                } else null,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )

            // Search results
            if (results.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Column {
                        for (r in results) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { goTo(r) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(Places.category(r.category).color))
                                )
                                Spacer(Modifier.size(10.dp))
                                Text(r.name, color = Color.Black)
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    Places.label(r.category),
                                    color = Color.Gray,
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            // Category filter chips
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val allOn = enabled.size == Places.categories.size
                Chip("All", PAKISTAN_GREEN, allOn) {
                    enabled = Places.categories.map { it.id }.toSet()
                }
                for (cat in Places.categories) {
                    Chip(cat.label, Color(cat.color), cat.id in enabled) {
                        enabled = if (cat.id in enabled) enabled - cat.id else enabled + cat.id
                    }
                }
            }

            // Map mode buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("2D", !is3D) { is3D = false }
                Pill("3D", is3D) { is3D = true }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Normal", !satellite) { satellite = false }
                Pill("Satellite", satellite) { satellite = true }
            }
        }

        // Detail popup
        selected?.let { item ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                item.name,
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                            )
                            Text(
                                Places.label(item.category),
                                color = Color(Places.category(item.category).color),
                                style = androidx.compose.material3.MaterialTheme.typography.labelLarge
                            )
                        }
                        IconButton(onClick = { selected = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                        }
                    }
                    Spacer(Modifier.size(6.dp))
                    Text(item.info, color = Color.DarkGray)
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, color: Color, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) color else Color.White)
            .border(1.dp, color, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(text, color = if (selected) Color.White else color)
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
