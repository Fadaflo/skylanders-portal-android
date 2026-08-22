// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.features.skylanders.catalog

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import org.json.JSONArray

/**
 * Eine Figur aus Dolphins list_skylanders, angereichert um den Dateinamen des
 * Artworks. Erzeugt von tools/extract_skylanders.py und tools/build_assets.py.
 */
data class CatalogFigure(
    val id: Int,
    val variant: Int,
    val name: String,
    val game: String,
    val element: String,
    val type: String,
    val image: String?
) {
    val gameLabel: String
        get() = GAME_LABELS[game] ?: game

    companion object {
        val GAME_LABELS = mapOf(
            "SpyrosAdv" to "Spyro's Adventure",
            "Giants" to "Giants",
            "SwapForce" to "Swap Force",
            "TrapTeam" to "Trap Team",
            "Superchargers" to "SuperChargers"
        )
    }
}

/**
 * Laedt den Figurenkatalog aus assets/skylanders/ und haelt die Artworks in
 * einem Zwischenspeicher. Der Katalog wird einmal gelesen und dann gehalten.
 */
object SkylanderCatalog {
    private const val ASSET_DIR = "skylanders"
    private const val CATALOG = "$ASSET_DIR/skylanders.json"

    private var figures: List<CatalogFigure>? = null

    private val bitmaps = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    @Synchronized
    fun getFigures(context: Context): List<CatalogFigure> {
        figures?.let { return it }

        val text = context.assets.open(CATALOG).bufferedReader().use { it.readText() }
        val array = JSONArray(text)
        val list = ArrayList<CatalogFigure>(array.length())
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            list.add(
                CatalogFigure(
                    id = o.getInt("id"),
                    variant = o.getInt("variant"),
                    name = o.getString("name"),
                    game = o.getString("game"),
                    element = o.getString("element"),
                    type = o.getString("type"),
                    image = if (o.isNull("image")) null else o.getString("image")
                )
            )
        }

        figures = list
        return list
    }

    /** Artwork einer Figur, oder null wenn es keines gibt. */
    fun getArtwork(context: Context, figure: CatalogFigure): Bitmap? {
        val name = figure.image ?: return null
        return loadAsset(context, "$ASSET_DIR/images/$name")
    }

    /** Symbol eines Elements, oder null bei "Other". */
    fun getElementIcon(context: Context, element: String): Bitmap? =
        loadAsset(context, "$ASSET_DIR/elements/$element.png")

    private fun loadAsset(context: Context, path: String): Bitmap? {
        bitmaps.get(path)?.let { return it }
        return try {
            context.assets.open(path).use { BitmapFactory.decodeStream(it) }
                ?.also { bitmaps.put(path, it) }
        } catch (e: java.io.IOException) {
            null
        }
    }
}
