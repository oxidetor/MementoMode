package com.example.appblocker.calculator.pages

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.appblocker.R
import com.example.appblocker.ScreentimeCalculatorActivity
import com.example.appblocker.adapters.AppUsageCategoryAdapter
import com.example.appblocker.calculator.CalculatorPageHandler
import com.example.appblocker.models.AppUsageCategory
import java.util.concurrent.TimeUnit

/**
 * Handles the usage breakdown page that shows users their actual app usage by category.
 * Follows Single Responsibility Principle by focusing only on this specific page's functionality.
 */
class UsageBreakdownHandler(private val activity: ScreentimeCalculatorActivity) : CalculatorPageHandler {
    
    private lateinit var appUsageCategoryAdapter: AppUsageCategoryAdapter
    
    override fun setupPage(pageView: View): Boolean {
        val recyclerView = pageView.findViewById<RecyclerView>(R.id.categoryRecyclerView) ?: return false
        
        // Initialize adapter
        appUsageCategoryAdapter = AppUsageCategoryAdapter()
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.adapter = appUsageCategoryAdapter
        
        return true
    }
    
    override fun updatePageUI(pageView: View) {
        // Get app usage categories from usage stats
        val categories = getAppUsageCategories()
        appUsageCategoryAdapter.updateCategories(categories)
        
        // Update text views with estimated vs actual usage
        val estimatedUsageText = pageView.findViewById<TextView>(R.id.estimatedUsage) ?: return
        val actualUsageText = pageView.findViewById<TextView>(R.id.actualUsage) ?: return
        
        // Set values
        val estimatedHours = activity.getEstimatedHours()
        estimatedUsageText.text = String.format("%.1f hours", estimatedHours)
        
        val actualHours = categories.sumOf { it.timeSpentMinutes }.toFloat() / 60
        actualUsageText.text = String.format("%.1f hours", actualHours)
    }
    
    override fun savePageData(pageView: View) {
        // No data to save on this page
    }
    
    private fun getAppUsageCategories(): List<AppUsageCategory> {
        if (!activity.hasUsageStatsPermission()) {
            activity.requestUsageStatsPermission()
            return emptyList()
        }
        
        val usageStats = activity.getWeeklyUsageStats()
        
        val categoryMap = mutableMapOf<String, Long>()
        usageStats.forEach { stats ->
            val category = getCategoryForPackage(stats.packageName)
            categoryMap[category] = (categoryMap[category] ?: 0L) + TimeUnit.MILLISECONDS.toMinutes(stats.totalTimeInForeground / 7)
        }
        
        val totalMinutes = categoryMap.values.sum().toFloat()
        return categoryMap.map { (category, minutes) ->
            AppUsageCategory(
                name = category,
                timeSpentMinutes = minutes,
                percentageOfTotal = if (totalMinutes > 0) minutes / totalMinutes else 0f,
                iconResId = getCategoryIcon(category)
            )
        }.sortedByDescending { it.timeSpentMinutes }
    }
    
    private fun getCategoryForPackage(packageName: String): String {
        return when {
            packageName.contains("facebook") || packageName.contains("instagram") || 
            packageName.contains("twitter") || packageName.contains("snapchat") -> "Social Media"
            packageName.contains("youtube") || packageName.contains("netflix") || 
            packageName.contains("prime") -> "Entertainment"
            packageName.contains("chrome") || packageName.contains("firefox") || 
            packageName.contains("safari") -> "Web Browsing"
            packageName.contains("gmail") || packageName.contains("outlook") || 
            packageName.contains("mail") -> "Email"
            packageName.contains("whatsapp") || packageName.contains("messenger") || 
            packageName.contains("telegram") -> "Messaging"
            else -> "Other"
        }
    }
    
    private fun getCategoryIcon(category: String): Int {
        return when (category) {
            "Social Media" -> R.drawable.ic_social
            "Entertainment" -> R.drawable.ic_entertainment
            "Web Browsing" -> R.drawable.ic_web
            "Email" -> R.drawable.ic_email
            "Messaging" -> R.drawable.ic_message
            else -> R.drawable.ic_other
        }
    }
} 