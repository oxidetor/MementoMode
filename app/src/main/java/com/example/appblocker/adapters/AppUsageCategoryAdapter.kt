package com.example.appblocker.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.appblocker.R
import com.example.appblocker.models.AppUsageCategory

class AppUsageCategoryAdapter : RecyclerView.Adapter<AppUsageCategoryAdapter.ViewHolder>() {
    private var categories = listOf<AppUsageCategory>()

    fun updateCategories(newCategories: List<AppUsageCategory>) {
        categories = newCategories
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_usage_category, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category = categories[position]
        holder.bind(category)
    }

    override fun getItemCount(): Int = categories.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.categoryIcon)
        private val nameView: TextView = itemView.findViewById(R.id.categoryName)
        private val timeView: TextView = itemView.findViewById(R.id.categoryTime)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.categoryProgress)

        fun bind(category: AppUsageCategory) {
            iconView.setImageResource(category.iconResId)
            nameView.text = category.name
            timeView.text = formatTime(category.timeSpentMinutes)
            progressBar.progress = (category.percentageOfTotal * 100).toInt()
        }

        private fun formatTime(minutes: Long): String {
            val hours = minutes / 60
            val mins = minutes % 60
            return if (hours > 0) {
                "$hours h $mins min"
            } else {
                "$mins min"
            }
        }
    }
} 