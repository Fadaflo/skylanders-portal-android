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

    /**
     * Zwischenspeicher fuer Artwork, ein Achtel des Speichers, den die App
     * bekommen darf, hoechstens 32 MB und mindestens 8.
     *
     * Nicht fest verdrahtet, weil sich die Geraete stark unterscheiden: auf
     * einem Tablet mit 6 GB und wenig freiem Speicher ist ein fester Block von
     * 24 MB spuerbar, auf einem neuen Telefon faellt er nicht auf.
     */
    private val bitmaps = object : LruCache<String, Bitmap>(cacheSize()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private fun cacheSize(): Int {
        val available = Runtime.getRuntime().maxMemory()
        return (available / 8).coerceIn(8L * 1024 * 1024, 32L * 1024 * 1024).toInt()
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

    /**
     * Klartext-Vorlage fuer die ultimativen Figuren, 352 Byte.
     *
     * Es ist der Spielstand einer frisch in Giants erzeugten Figur: kein Hut,
     * kein Spitzname, keine Altlasten. Ausbau, Erfahrung und Gold werden beim
     * Erschaffen hineingeschrieben.
     *
     * Frueher standen hier zwei Vorlagen aus dem PS3-Projekt, Abzuege voll
     * ausgebauter Figuren. Die brachten den Hut ihrer Herkunftsfigur mit, und
     * wo der Hut in der Datei steht, ist bis heute nicht gemessen.
     */
    fun getTemplate(context: Context): ByteArray? = try {
        context.assets.open("$ASSET_DIR/templates/neutral.bin").use { it.readBytes() }
    } catch (e: java.io.IOException) {
        null
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
