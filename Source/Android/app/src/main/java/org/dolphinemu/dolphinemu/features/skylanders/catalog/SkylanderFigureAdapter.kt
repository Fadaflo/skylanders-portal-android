// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.features.skylanders.catalog

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.dolphinemu.dolphinemu.R
import org.dolphinemu.dolphinemu.databinding.ListItemSkylanderFigureBinding

/**
 * Eine Karte im Raster.
 *
 * In der Sammlung steht eine Karte fuer eine .sky-Datei — zwei Kopien
 * derselben Figur sind zwei Karten, weil es zwei Spielstaende sind. Beim
 * Beschwoeren steht eine Karte fuer einen Eintrag aus Dolphins Datenbank.
 */
data class FigureRow(
    val figure: CatalogFigure,
    val file: CollectedFigure? = null,
    val owned: Boolean = file != null,
    val state: String? = null,
    val marked: Boolean = false
)

/** Das Figurenraster: Artwork, Elementsymbol und eine Zeile Zustand. */
class SkylanderFigureAdapter(
    private var rows: List<FigureRow>,
    private val onClick: ((Int) -> Unit)? = null
) : RecyclerView.Adapter<SkylanderFigureAdapter.ViewHolder>() {

    /**
     * Die markierte Karte. Eigene Auswahl statt Androids Fokus, weil das
     * Steuerkreuz des DualShock 4 als Achse ankommt und Androids Navigation
     * davon nichts mitbekommt. -1 heisst: nichts markiert.
     */
    var selected: Int = -1
        set(value) {
            val old = field
            field = value
            if (old >= 0) notifyItemChanged(old)
            if (value >= 0) notifyItemChanged(value)
        }

    /** Zeichnet den Auswahlrahmen nur, wenn das Raster am Zug ist. */
    var selectionVisible: Boolean = true
        set(value) {
            field = value
            if (selected >= 0) notifyItemChanged(selected)
        }

    class ViewHolder(val binding: ListItemSkylanderFigureBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ListItemSkylanderFigureBinding.inflate(inflater, parent, false)
        if (onClick != null) {
            binding.root.isClickable = true
        }
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val row = rows[position]
        val context = holder.itemView.context

        holder.binding.textFigureName.text = row.figure.name
        holder.binding.textFigureGame.text = row.state ?: row.figure.gameLabel
        holder.binding.imageFigure.setImageBitmap(
            SkylanderCatalog.getArtwork(context, row.figure)
        )
        holder.binding.imageElement.setImageBitmap(
            SkylanderCatalog.getElementIcon(context, row.figure.element)
        )

        val chosen = position == selected && selectionVisible
        holder.itemView.setBackgroundResource(
            when {
                row.marked -> R.drawable.sky_card_marked
                chosen -> R.drawable.sky_card_selected
                else -> 0
            }
        )
        holder.itemView.alpha = if (chosen || row.marked || row.owned) 1.0f else 0.45f

        onClick?.let { listener -> holder.itemView.setOnClickListener { listener(position) } }
    }

    override fun getItemCount(): Int = rows.size

    fun submit(newRows: List<FigureRow>) {
        rows = newRows
        notifyDataSetChanged()
    }

    fun rowAt(position: Int): FigureRow? = rows.getOrNull(position)
}
