package com.pakmap.app

import android.content.Context
import android.graphics.Color
import org.json.JSONObject

data class Category(val id: String, val label: String, val color: Int)

data class PlaceItem(
    val category: String,
    val name: String,
    val info: String,
    val lat: Double,
    val lon: Double,
    val isLine: Boolean
)

object Places {
    val categories = listOf(
        Category("dams", "Dams", Color.parseColor("#1565C0")),
        Category("rivers", "Rivers", Color.parseColor("#0288D1")),
        Category("lakes", "Lakes", Color.parseColor("#00ACC1")),
        Category("passes", "Passes", Color.parseColor("#6D4C41")),
        Category("forts", "Forts", Color.parseColor("#D84315")),
        Category("airports", "Airports", Color.parseColor("#6A1B9A")),
        Category("ports", "Ports", Color.parseColor("#283593")),
        Category("mountains", "Mountains", Color.parseColor("#2E7D32"))
    )

    fun category(id: String): Category =
        categories.firstOrNull { it.id == id } ?: categories.first()

    fun label(id: String): String = category(id).label

    private var cached: String? = null

    // Reads app/src/main/assets/places.geojson (all categories in one file)
    fun geoJson(context: Context): String =
        cached ?: context.assets.open("places.geojson")
            .bufferedReader()
            .use { it.readText() }
            .also { cached = it }

    // Turns the GeoJSON into a list (used by search and the info popup)
    fun load(context: Context): List<PlaceItem> {
        val out = ArrayList<PlaceItem>()
        val features = JSONObject(geoJson(context)).getJSONArray("features")
        for (i in 0 until features.length()) {
            val f = features.getJSONObject(i)
            val props = f.getJSONObject("properties")
            val geom = f.getJSONObject("geometry")
            val coords = geom.getJSONArray("coordinates")
            val isLine = geom.getString("type") == "LineString"
            val point = if (isLine) coords.getJSONArray(coords.length() / 2) else coords
            out.add(
                PlaceItem(
                    category = props.getString("category"),
                    name = props.getString("name"),
                    info = props.optString("info", ""),
                    lat = point.getDouble(1),
                    lon = point.getDouble(0),
                    isLine = isLine
                )
            )
        }
        return out
    }
}
