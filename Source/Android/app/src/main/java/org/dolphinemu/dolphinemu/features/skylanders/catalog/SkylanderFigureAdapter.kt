// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.features.skylanders.catalog

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.dolphinemu.dolphinemu.databinding.ListItemSkylanderFigureBinding

/**
 * Raster aller Figuren mit Artwork, Elementsymbol und Spiel.
 *
 * Figuren, von denen keine .sky-Datei in der Sammlung liegt, werden blasser
 * gezeichnet — dieselbe Idee wie die rote Schrift im Desktop-Fenster.
 */
class SkylanderFigureAdapter(
    private var figures: List<CatalogFigure>,
    private val isOwned: (CatalogFigure) -> Boolean = { false },
    private val onClick: ((CatalogFigure) -> Unit)? = null
) : RecyclerView.Adapter<SkylanderFigureAdapter.ViewHolder>() {

    class ViewHolder(val binding: ListItemSkylanderFigureBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ListItemSkylanderFigureBinding.inflate(inflater, parent, false)
        if (onClick != null) {
            binding.root.isFocusable = true
            binding.root.isClickable = true
            binding.root.setBackgroundResource(
                android.R.drawable.list_selector_background
            )
        }
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val figure = figures[position]
        val context = holder.itemView.context

        holder.binding.textFigureName.text = figure.name
        holder.binding.textFigureGame.text = figure.gameLabel
        holder.binding.imageFigure.setImageBitmap(SkylanderCatalog.getArtwork(context, figure))
        holder.binding.imageElement.setImageBitmap(
            SkylanderCatalog.getElementIcon(context, figure.element)
        )

        holder.itemView.alpha = if (isOwned(figure)) 1.0f else 0.45f
        onClick?.let { listener ->
            holder.itemView.setOnClickListener { listener(figure) }
        }
    }

    override fun getItemCount(): Int = figures.size

    fun submit(newFigures: List<CatalogFigure>) {
        figures = newFigures
        notifyDataSetChanged()
    }
}
