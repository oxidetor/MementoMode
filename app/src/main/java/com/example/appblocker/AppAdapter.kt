package com.example.appblocker

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(allApps: List<AppInfo>) : 
    RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    // Keep original sorted list for this session
    private val originalApps: List<AppInfo> = allApps.sortedWith(
        compareByDescending<AppInfo> { it.isSelected }
            .thenBy { it.appName.lowercase() }
    )
    
    private var filteredApps: List<AppInfo> = originalApps

    class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appIcon: ImageView = itemView.findViewById(R.id.imageViewAppIcon)
        val appName: TextView = itemView.findViewById(R.id.textViewAppName)
        val checkBox: CheckBox = itemView.findViewById(R.id.checkBoxBlockApp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = filteredApps[position]
        holder.appIcon.setImageDrawable(app.appIcon)
        holder.appName.text = app.appName
        holder.checkBox.isChecked = app.isSelected
        
        // Update the appearance based on blocked status
        if (app.isSelected) {
            holder.appName.setTypeface(holder.appName.typeface, Typeface.BOLD)
            holder.itemView.background = ContextCompat.getDrawable(
                holder.itemView.context, 
                R.drawable.subtle_selected_item_background
            )
        } else {
            holder.appName.setTypeface(holder.appName.typeface, Typeface.NORMAL)
            // Use the default selectableItemBackground
            val typedValue = android.util.TypedValue()
            holder.itemView.context.theme.resolveAttribute(
                android.R.attr.selectableItemBackground, 
                typedValue, 
                true
            )
            holder.itemView.setBackgroundResource(typedValue.resourceId)
        }

        holder.itemView.setOnClickListener {
            app.isSelected = !app.isSelected
            holder.checkBox.isChecked = app.isSelected
            notifyItemChanged(position)
        }

        holder.checkBox.setOnClickListener {
            app.isSelected = holder.checkBox.isChecked
            notifyItemChanged(position)
        }
    }

    override fun getItemCount(): Int = filteredApps.size

    fun getBlockedApps(): List<String> {
        return originalApps.filter { it.isSelected }.map { it.packageName }
    }

    fun clearAllSelections() {
        originalApps.forEach { it.isSelected = false }
        notifyDataSetChanged()
    }

    private var currentQuery: String = ""

    fun filter(query: String) {
        currentQuery = query
        filteredApps = if (query.isEmpty()) {
            originalApps
        } else {
            originalApps.filter {
                it.appName.lowercase().contains(query.lowercase()) ||
                it.packageName.lowercase().contains(query.lowercase())
            }
        }
        notifyDataSetChanged()
    }
} 