package com.example.appblocker.calculator.pages

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import com.example.appblocker.R
import com.example.appblocker.ScreentimeCalculatorActivity
import com.example.appblocker.calculator.CalculatorPageHandler
import java.util.concurrent.TimeUnit

/**
 * Handles the first page of the calculator where users input their estimated daily screen time.
 * Follows Single Responsibility Principle by focusing only on this specific page's functionality.
 */
class EstimateInputHandler(private val activity: ScreentimeCalculatorActivity) : CalculatorPageHandler {
    
    private var averageUsage: Float = 0f
    
    override fun setupPage(pageView: View): Boolean {
        val hoursInput = pageView.findViewById<EditText>(R.id.hoursInput) ?: return false
        
        // Load average usage time from device stats
        averageUsage = loadAverageUsageTime()
        
        // Set initial value
        hoursInput.setText(String.format("%.1f", averageUsage))
        
        // Set up text change listener
        hoursInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: Editable?) {
                try {
                    val hours = s.toString().toFloatOrNull() ?: 0f
                    activity.setEstimatedHours(hours)
                } catch (e: NumberFormatException) {
                    activity.setEstimatedHours(0f)
                }
            }
        })
        
        return true
    }
    
    override fun updatePageUI(pageView: View) {
        // Nothing additional to update
    }
    
    override fun savePageData(pageView: View) {
        val hoursInput = pageView.findViewById<EditText>(R.id.hoursInput) ?: return
        try {
            val hours = hoursInput.text.toString().toFloatOrNull() ?: averageUsage
            activity.setEstimatedHours(hours)
        } catch (e: Exception) {
            activity.setEstimatedHours(averageUsage)
        }
    }
    
    private fun loadAverageUsageTime(): Float {
        if (!activity.hasUsageStatsPermission()) {
            activity.requestUsageStatsPermission()
            return 4f // Default value
        }
        
        val usageStats = activity.getWeeklyUsageStats()
        
        if (usageStats.isEmpty()) {
            return 4f // Default value
        }
        
        val totalTimeInForeground = usageStats.sumOf { stat -> stat.totalTimeInForeground }
        val averageDaily = TimeUnit.MILLISECONDS.toMinutes(totalTimeInForeground / 7)
        return averageDaily / 60f
    }
} 