package com.example.appblocker.calculator.pages

import android.util.Log
import android.view.View
import android.widget.TextView
import com.example.appblocker.R
import com.example.appblocker.ScreentimeCalculatorActivity
import com.example.appblocker.calculator.CalculatorPageHandler
import java.text.DecimalFormat

/**
 * Handles the lifetime impact page that shows users the projected lifetime hours 
 * and days/weeks lost to screen time.
 * Follows Single Responsibility Principle by focusing only on this specific page's functionality.
 */
class LifetimeImpactHandler(private val activity: ScreentimeCalculatorActivity) : CalculatorPageHandler {
    
    private val decimalFormat = DecimalFormat("#,###.#")
    private val yearsInLife = 60 // Assume 60 more years of usage
    private val TAG = "LifetimeImpactHandler"
    
    override fun setupPage(pageView: View): Boolean {
        Log.d(TAG, "setupPage called for lifetime impact page")
        
        // Check if necessary views exist
        val lifetimeHoursView = pageView.findViewById<TextView>(R.id.lifetimeHours)
        val daysLostView = pageView.findViewById<TextView>(R.id.daysLost)
        val weeksLostView = pageView.findViewById<TextView>(R.id.weeksLost)
        
        Log.d(TAG, "Views found: lifetimeHours=${lifetimeHoursView != null}, daysLost=${daysLostView != null}, weeksLost=${weeksLostView != null}")
        
        return lifetimeHoursView != null && daysLostView != null && weeksLostView != null
    }
    
    override fun updatePageUI(pageView: View) {
        Log.d(TAG, "updatePageUI called for lifetime impact page")
        
        // Calculate lifetime values based on estimated hours
        val estimatedHours = activity.getEstimatedHours()
        Log.d(TAG, "Estimated hours: $estimatedHours")
        
        // Ensure we have a valid value (not zero)
        if (estimatedHours <= 0f) {
            Log.w(TAG, "Estimated hours is zero or negative, using default value of 4 hours")
            activity.setEstimatedHours(4f) // Use a reasonable default
        }
        
        // Recalculate with possibly updated value
        val actualHours = activity.getEstimatedHours()
        val lifetimeHours = actualHours * 365 * yearsInLife
        val daysLost = lifetimeHours / 24
        val weeksLost = daysLost / 7
        
        Log.d(TAG, "Calculated values: lifetimeHours=$lifetimeHours, daysLost=$daysLost, weeksLost=$weeksLost")
        
        // Find and update views
        val lifetimeHoursText = pageView.findViewById<TextView>(R.id.lifetimeHours)
        val daysLostText = pageView.findViewById<TextView>(R.id.daysLost)
        val weeksLostText = pageView.findViewById<TextView>(R.id.weeksLost)
        
        if (lifetimeHoursText == null || daysLostText == null || weeksLostText == null) {
            Log.e(TAG, "One or more views are null in updatePageUI: lifetimeHours=${lifetimeHoursText != null}, daysLost=${daysLostText != null}, weeksLost=${weeksLostText != null}")
            return
        }
        
        // Animate the lifetime hours counter
        activity.animateNumberCounter(lifetimeHoursText, lifetimeHours)
        
        // Set other values directly
        daysLostText.text = decimalFormat.format(daysLost)
        weeksLostText.text = decimalFormat.format(weeksLost)
        
        Log.d(TAG, "Values set to views")
    }
    
    override fun savePageData(pageView: View) {
        // No need to save anything from this page as it's just displaying calculated data
        Log.d(TAG, "savePageData called (no action needed)")
    }
} 