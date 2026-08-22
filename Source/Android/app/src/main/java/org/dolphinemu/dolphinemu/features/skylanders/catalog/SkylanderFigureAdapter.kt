// SPDX-License-Identifier: GPL-2.0-or-later

package org.dolphinemu.dolphinemu.features.skylanders.catalog

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.dolphinemu.dolphinemu.databinding.ListItemSkylanderFigureBinding

/** Raster aller Figuren mit Artwork, Elementsymbol und Spiel. */
class SkylanderFigureAdapter(private var figures: List<CatalogFigure>) :
    RecyclerView.Adapter<SkylanderFigureAdapter.ViewHolder>() {

    class ViewHolder(val binding: ListItemSkylanderFigureBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return ViewHolder(ListItemSkylanderFigureBinding.inflate(inflater, parent, false))
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
    }

    override fun getItemCount(): Int = figures.size

    fun submit(newFigures: List<CatalogFigure>) {
        figures = newFigures
        notifyDataSetChanged()
    }
}
