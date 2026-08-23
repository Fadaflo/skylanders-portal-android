// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.features.skylanders.catalog

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import org.dolphinemu.dolphinemu.databinding.ActivitySkylanderGalleryBinding

/**
 * Testansicht aus Phase 4: zeigt alle Figuren aus Dolphins Datenbank mit Bild,
 * Element und Spiel. Dient nur zur Kontrolle der Daten und Assets; die richtige
 * Bedienoberflaeche entsteht in Phase 5.
 */
class SkylanderGalleryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivitySkylanderGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val figures = SkylanderCatalog.getFigures(this)
        val withArtwork = figures.count { it.image != null }
        val games = figures.groupingBy { it.gameLabel }.eachCount().entries
            .sortedByDescending { it.value }
            .joinToString(", ") { "${it.key}: ${it.value}" }

        binding.textSummary.text =
            "${figures.size} Figuren, davon $withArtwork mit Bild\n$games"

        binding.gridFigures.layoutManager = GridLayoutManager(this, 4)
        binding.gridFigures.adapter =
            SkylanderFigureAdapter(figures.map { FigureRow(figure = it, owned = true) })
    }
}
