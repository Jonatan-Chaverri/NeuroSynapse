package com.example.swipecards

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ImageAdapter(
    private val images: List<Int>,
    private var prices: List<String> // Keep prices as an immutable list
) : RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {

    class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.imageView)
        val priceTag: TextView = itemView.findViewById(R.id.priceTag)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_image, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.imageView.setImageResource(images[position % images.size])
        holder.priceTag.text = prices[position % prices.size]
    }

    override fun getItemCount(): Int {
        return Int.MAX_VALUE
    }

    // Function to update prices and notify the adapter
    fun updatePrices(newPrices: List<String>) {
        prices = newPrices // Replace the old prices with new ones
        notifyDataSetChanged() // Notify the adapter to refresh the views
    }
}

