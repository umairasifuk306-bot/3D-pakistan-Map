package com.pakmap.app

import android.content.Context
import android.graphics.Color

data class Category(val id: String, val label: String, val color: Int)

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

    private var cached: String? = null

    // Reads app/src/main/assets/places.geojson (all categories in one file)
    fun geoJson(context: Context): String =
        cached ?: context.assets.open("places.geojson")
            .bufferedReader()
            .use { it.readText() }
            .also { cached = it }
}
