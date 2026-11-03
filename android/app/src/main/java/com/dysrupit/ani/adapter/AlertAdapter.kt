package com.dysrupit.ani.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.dysrupit.ani.R
import com.dysrupit.ani.model.AlertItem

class AlertAdapter(private val items: List<AlertItem>) : RecyclerView.Adapter<AlertAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val cardContainer: LinearLayout = v.findViewById(R.id.cardAlertContainer)
        val iconContainer: FrameLayout = v.findViewById(R.id.flIconContainer)
        val icon: TextView = v.findViewById(R.id.tvAlertIcon)
        val badge: TextView = v.findViewById(R.id.tvAlertBadge)
        val title: TextView = v.findViewById(R.id.tvAlertTitle)
        val desc: TextView = v.findViewById(R.id.tvAlertDesc)
        val timestamp: TextView = v.findViewById(R.id.tvAlertTimestamp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_alert, parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        holder.desc.text = item.description
        holder.timestamp.text = item.timestamp
        
        if (!item.signal.isNullOrBlank()) {
            holder.badge.text = item.signal
            holder.badge.visibility = View.VISIBLE
        } else {
            holder.badge.visibility = View.GONE
        }

        // Set card styling & icons based on alert type
        val typeLower = item.type.lowercase()
        if (typeLower.contains("typhoon") || !item.signal.isNullOrBlank()) {
            holder.cardContainer.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FBE8E4"))
            holder.iconContainer.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F5D6D0"))
            holder.icon.text = "🌀"
        } else if (typeLower.contains("outbreak") || item.title.contains("Armyworm", true)) {
            holder.cardContainer.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFFFFF"))
            holder.iconContainer.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F5F1E9"))
            holder.icon.text = "🐛"
        } else {
            holder.cardContainer.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFFFFF"))
            holder.iconContainer.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F5F1E9"))
            holder.icon.text = "🌱"
        }
    }

    override fun getItemCount() = items.size
}
