package com.example.appblocker.calculator.pages

import android.view.View
import android.widget.TextView
import com.example.appblocker.R
import com.example.appblocker.ScreentimeCalculatorActivity
import com.example.appblocker.calculator.CalculatorPageHandler
import com.google.android.material.slider.Slider

/**
 * Handles the goal setting page that allows users to set a target for daily screen time usage.
 * Follows Single Responsibility Principle by focusing only on this specific page's functionality.
 */
class GoalSettingHandler(private val activity: ScreentimeCalculatorActivity) : CalculatorPageHandler {
    
    override fun setupPage(pageView: View): Boolean {
        val goalSlider = pageView.findViewById<Slider>(R.id.goalSlider) ?: return false
        
        // Update slider to use 0.25 step size (15-minute increments)
        goalSlider.stepSize = 0.25f
        
        // Set up slider change listener
        goalSlider.addOnChangeListener { _, value, _ ->
            // Convert hours to minutes internally
            activity.setSelectedGoalHours(value)
            updateSavingsTexts(pageView)
        }
        
        return true
    }
    
    override fun updatePageUI(pageView: View) {
        val goalSlider = pageView.findViewById<Slider>(R.id.goalSlider) ?: return
        
        // Set initial slider value to half of estimated usage (reasonable goal)
        val estimatedHours = activity.getEstimatedHours()
        val initialGoal = estimatedHours / 2
        
        // Constrain to slider min/max
        val sliderValue = initialGoal.coerceIn(goalSlider.valueFrom, goalSlider.valueTo)
        
        // Round to nearest 0.25 to ensure 15-minute increments
        val roundedValue = (sliderValue * 4).toInt() / 4f
        
        goalSlider.value = roundedValue
        activity.setSelectedGoalHours(roundedValue)
        
        // Update text values
        updateSavingsTexts(pageView)
    }
    
    override fun savePageData(pageView: View) {
        val goalSlider = pageView.findViewById<Slider>(R.id.goalSlider) ?: return
        activity.setSelectedGoalHours(goalSlider.value)
    }
    
    private fun updateSavingsTexts(pageView: View) {
        val goalSelectedText = pageView.findViewById<TextView>(R.id.goalSelected) ?: return
        val dailySavingsText = pageView.findViewById<TextView>(R.id.dailySavings) ?: return
        val weeklySavingsText = pageView.findViewById<TextView>(R.id.weeklySavings) ?: return
        
        val estimatedHours = activity.getEstimatedHours()
        val selectedGoalHours = activity.getSelectedGoalHours()
        
        // Update the "goal selected" text
        goalSelectedText.text = pageView.context.getString(
            R.string.goal_selected, 
            String.format("%.2f", selectedGoalHours)
        )
        
        // Calculate and display savings
        val dailySavings = (estimatedHours - selectedGoalHours).coerceAtLeast(0f)
        val weeklySavings = dailySavings * 7
        
        dailySavingsText.text = String.format("%.1f", dailySavings)
        weeklySavingsText.text = String.format("%.1f", weeklySavings)
    }
} 