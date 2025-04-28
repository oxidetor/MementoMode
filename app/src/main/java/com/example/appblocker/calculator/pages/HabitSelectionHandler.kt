package com.example.appblocker.calculator.pages

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.appblocker.R
import com.example.appblocker.ScreentimeCalculatorActivity
import com.example.appblocker.adapters.AlternativeActivityAdapter
import com.example.appblocker.calculator.CalculatorPageHandler
import com.example.appblocker.models.AlternativeActivity

/**
 * Handles the habit selection page that allows users to select alternative activities
 * they would like to do instead of spending time on their phone.
 * Follows Single Responsibility Principle by focusing only on this specific page's functionality.
 */
class HabitSelectionHandler(private val activity: ScreentimeCalculatorActivity) : CalculatorPageHandler {
    
    private lateinit var alternativeActivityAdapter: AlternativeActivityAdapter
    private val selectedActivities = mutableSetOf<String>()
    
    override fun setupPage(pageView: View): Boolean {
        val recyclerView = pageView.findViewById<RecyclerView>(R.id.activitiesRecyclerView) ?: return false
        val addButton = pageView.findViewById<Button>(R.id.addActivityButton) ?: return false
        val activityInput = pageView.findViewById<EditText>(R.id.activityInput) ?: return false
        
        // Initialize adapter with selection callback
        alternativeActivityAdapter = AlternativeActivityAdapter { activityItem, isSelected ->
            if (isSelected) {
                selectedActivities.add(activityItem.name)
            } else {
                selectedActivities.remove(activityItem.name)
            }
        }
        
        // Set up RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.adapter = alternativeActivityAdapter
        
        // Set up button to add custom activities
        addButton.setOnClickListener {
            val activityName = activityInput.text.toString().trim()
            if (activityName.isNotEmpty()) {
                addCustomActivity(activityName)
                activityInput.text.clear()
            }
        }
        
        // Set up text watcher to enable/disable add button
        activityInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: Editable?) {
                addButton.isEnabled = !s.isNullOrBlank()
            }
        })
        
        return true
    }
    
    override fun updatePageUI(pageView: View) {
        // Load suggested activities
        val suggestedActivities = getSuggestedActivities()
        alternativeActivityAdapter.updateActivities(suggestedActivities)
        
        // Sync with main activity's selected activities
        selectedActivities.clear()
        selectedActivities.addAll(activity.getSelectedActivities())
    }
    
    override fun savePageData(pageView: View) {
        // Update the selected activities in the main activity
        activity.getSelectedActivities().clear()
        activity.getSelectedActivities().addAll(selectedActivities)
    }
    
    private fun addCustomActivity(activityName: String) {
        // Create a new list with existing activities plus the new one
        val currentActivities = mutableListOf<AlternativeActivity>()
        
        // Add all existing activities
        for (i in 0 until alternativeActivityAdapter.itemCount) {
            val existingActivity = alternativeActivityAdapter.getActivity(i)
            currentActivities.add(existingActivity)
        }
        
        // Add the new one
        val newActivity = AlternativeActivity(name = activityName)
        currentActivities.add(newActivity)
        
        // Update the adapter
        alternativeActivityAdapter.updateActivities(currentActivities)
        
        // Select the new activity by default
        selectedActivities.add(activityName)
    }
    
    private fun getSuggestedActivities(): List<AlternativeActivity> {
        return listOf(
            activity.getString(R.string.suggested_activity_exercise),
            activity.getString(R.string.suggested_activity_read),
            activity.getString(R.string.suggested_activity_learn),
            activity.getString(R.string.suggested_activity_family),
            activity.getString(R.string.suggested_activity_hobby),
            activity.getString(R.string.suggested_activity_meditate),
            activity.getString(R.string.suggested_activity_sleep),
            activity.getString(R.string.suggested_activity_nature)
        ).map { AlternativeActivity(name = it) }
    }
} 