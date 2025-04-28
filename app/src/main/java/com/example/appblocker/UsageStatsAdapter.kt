package com.example.appblocker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale
import java.util.concurrent.TimeUnit

class UsageStatsAdapter(private val usageStatsList: List<AppUsageInfo>) :
    RecyclerView.Adapter<UsageStatsAdapter.UsageStatsViewHolder>() {

    class UsageStatsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appIcon: ImageView = itemView.findViewById(R.id.imageViewAppIcon)
        val appName: TextView = itemView.findViewById(R.id.textViewAppName)
        val usageTime: TextView = itemView.findViewById(R.id.textViewUsageTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UsageStatsViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_usage_stats, parent, false)
        return UsageStatsViewHolder(view)
    }

    override fun onBindViewHolder(holder: UsageStatsViewHolder, position: Int) {
        val appUsage = usageStatsList[position]
        holder.appIcon.setImageDrawable(appUsage.icon)
        holder.appName.text = appUsage.appName
        holder.usageTime.text = formatUsageTime(appUsage.timeInForeground, appUsage.numberOfDays)
    }

    override fun getItemCount(): Int = usageStatsList.size

    private fun formatUsageTime(timeInMillis: Long, days: Int): String {
        if (days <= 0) return "N/A"
        
        val averageMillisPerDay = timeInMillis / days
        val hours = TimeUnit.MILLISECONDS.toHours(averageMillisPerDay)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(averageMillisPerDay) % 60

        return when {
            hours > 0 -> String.format(Locale.getDefault(), "%dh %dm / day", hours, minutes)
            minutes > 0 -> String.format(Locale.getDefault(), "%dm / day", minutes)
            else -> "< 1m / day"
        }
    }
} 