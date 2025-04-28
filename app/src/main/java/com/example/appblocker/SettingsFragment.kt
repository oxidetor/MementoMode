package com.example.appblocker

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.example.appblocker.ai.AIServiceFactory
import kotlin.text.*

class SettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {
    
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // Migrate older preferences formats
        migratePreferences()
        
        // Set the preferences from XML
        setPreferencesFromResource(R.xml.preferences, rootKey)
        
        // Get shared preferences
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        
        // Set up preference change listeners and summaries
        setupGoalHoursPreference()
        setupAccessDurationPreference()
        setupHabitsPreference()
        setupAPIKeyPreference()
        setupCustomPromptPreference()
        setupCoachPersonaPreference()
        
        // Register preference change listener
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }
    
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (sharedPreferences == null || key == null) return
        
        when (key) {
            "goal_minutes" -> setupGoalHoursPreference()
            "accessDuration" -> setupAccessDurationPreference()
            "aiStrictness" -> updateAIStrictness(sharedPreferences.getString("aiStrictness", "moderate") ?: "moderate")
            "aiModel" -> updateAIModel(sharedPreferences.getString("aiModel", "gpt-3.5-turbo") ?: "gpt-3.5-turbo")
            "debugMode" -> updateDebugMode(sharedPreferences.getBoolean("debugMode", false))
            "coach_persona" -> updateCoachPersona(sharedPreferences.getString("coach_persona", "nova") ?: "nova")
        }
    }
    
    /**
     * Migrates older preference formats to the new minutes-based system
     */
    private fun migratePreferences() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val editor = prefs.edit()
        
        // First check if we need to migrate from goal_hours to goal_minutes
        try {
            // Case 1: Float-based goal_hours exists
            val goalHoursFloat = prefs.getFloat("goal_hours", -1f)
            if (goalHoursFloat > 0) {
                // Convert hours to minutes
                val goalMinutes = (goalHoursFloat * 60).toInt()
                // Round to nearest 15 minutes
                val roundedMinutes = (goalMinutes / 15) * 15
                
                // Save as minutes
                editor.putInt("goal_minutes", roundedMinutes)
                editor.remove("goal_hours") // Remove old setting
                
                Log.i("SettingsFragment", "Migrated goal_hours float $goalHoursFloat to goal_minutes $roundedMinutes")
            }
        } catch (e: ClassCastException) {
            // Case 2: Int-based goal_hours exists (half-hour increments)
            try {
                val goalHoursInt = prefs.getInt("goal_hours", -1)
                if (goalHoursInt > 0) {
                    // Convert half-hours to minutes (half-hour = 30 minutes)
                    val goalMinutes = goalHoursInt * 30
                    
                    // Save as minutes
                    editor.putInt("goal_minutes", goalMinutes)
                    editor.remove("goal_hours") // Remove old setting
                    
                    Log.i("SettingsFragment", "Migrated goal_hours int $goalHoursInt to goal_minutes $goalMinutes")
                }
            } catch (e2: ClassCastException) {
                Log.d("SettingsFragment", "No goal_hours preference found")
            }
        } catch (e: Exception) {
            Log.e("SettingsFragment", "Error during preference migration: ${e.message}")
        }
        
        // Apply all changes
        editor.apply()
    }
    
    private fun setupGoalHoursPreference() {
        findPreference<SeekBarPreference>("goal_minutes")?.let { preference ->
            // Hide the raw numeric value display (we'll replace it with a formatted version)
            preference.showSeekBarValue = false
            
            // Create a custom SeekBar.OnSeekBarChangeListener to show hours instead of minutes
            preference.setOnPreferenceClickListener {
                // Update the title with current value
                val currentMinutes = preference.value
                val hours = currentMinutes / 60f
                preference.title = getString(R.string.daily_goal_title) + String.format(" (%.2f hours)", hours)
                
                // We can't directly access the dialog elements in AndroidX preferences
                // Instead, we'll update the display when the value changes
                false
            }
            
            // Set a custom formatter for the seek bar value to show hours instead of minutes
            preference.setOnPreferenceChangeListener { _, newValue ->
                if (newValue is Int) {
                    val hours = newValue / 60f
                    preference.title = getString(R.string.daily_goal_title) + String.format(" (%.2f hours)", hours)
                }
                true
            }
            
            // Initialize with current value
            val currentMinutes = preference.value
            val hours = currentMinutes / 60f
            preference.title = getString(R.string.daily_goal_title) + String.format(" (%.2f hours)", hours)
            
            // Add a summary provider that shows the value as hours
            preference.summaryProvider = Preference.SummaryProvider<SeekBarPreference> { pref ->
                val prefHours = pref.value / 60f
                getString(R.string.daily_goal_summary) + String.format(" (%.2f hours)", prefHours)
            }
        }
    }
    
    private fun setupAccessDurationPreference() {
        findPreference<SeekBarPreference>("accessDuration")?.let { preference ->
            preference.summaryProvider = Preference.SummaryProvider<SeekBarPreference> { pref ->
                getString(R.string.duration_description) + String.format(" (%d minutes)", pref.value)
            }
        }
    }
    
    private fun setupHabitsPreference() {
        findPreference<Preference>("manage_habits")?.let { preference ->
            // Update the summary to show how many habits are selected
            val selectedHabits = sharedPreferences.getStringSet("selected_habits", emptySet()) ?: emptySet()
            
            // Log for debugging
            Log.d("SettingsFragment", "Retrieved ${selectedHabits.size} selected habits: ${selectedHabits.joinToString()}")
            
            preference.summary = if (selectedHabits.isEmpty()) {
                getString(R.string.healthy_habits_summary)
            } else {
                getString(R.string.habits_selected, selectedHabits.size)
            }
            
            // Set up click listener to open a dialog to manage habits
            preference.setOnPreferenceClickListener {
                showHabitsDialog(selectedHabits)
                true
            }
        }
    }
    
    private fun showHabitsDialog(currentHabits: Set<String>) {
        val context = requireContext()
        
        // Create a list of all available habits with their selection state
        val allHabits = getAvailableHabits()
        val habitItems = allHabits.map { habit ->
            val isSelected = currentHabits.contains(habit)
            Pair(habit, isSelected)
        }.toMutableList()
        
        // Sort habits so selected ones appear at the top
        val sortedHabitItems = habitItems.sortedByDescending { it.second }.toMutableList()
        
        // Create multi-choice dialog
        val habitNames = sortedHabitItems.map { it.first }.toTypedArray()
        val checkedItems = sortedHabitItems.map { it.second }.toBooleanArray()
        
        val dialogBuilder = androidx.appcompat.app.AlertDialog.Builder(context)
        dialogBuilder.setTitle(getString(R.string.healthy_habits))
        dialogBuilder.setMultiChoiceItems(habitNames, checkedItems) { _, which, isChecked ->
            sortedHabitItems[which] = Pair(sortedHabitItems[which].first, isChecked)
        }
        
        dialogBuilder.setPositiveButton(getString(R.string.save)) { _, _ ->
            // Save the updated habit selections to preferences
            val selectedHabits = sortedHabitItems.filter { it.second }.map { it.first }.toSet()
            sharedPreferences.edit().putStringSet("selected_habits", selectedHabits).apply()
            
            // Update the preference summary
            findPreference<Preference>("manage_habits")?.summary = 
                if (selectedHabits.isEmpty()) {
                    getString(R.string.healthy_habits_summary)
                } else {
                    getString(R.string.habits_selected, selectedHabits.size)
                }
            
            // Log for debugging purposes
            Log.d("SettingsFragment", "Saved ${selectedHabits.size} habits")
        }
        
        dialogBuilder.setNegativeButton(getString(R.string.cancel), null)
        dialogBuilder.show()
    }
    
    private fun getAvailableHabits(): List<String> {
        return listOf(
            "💪 Exercise regularly",
            "📚 Read books",
            "🧠 Learn a new skill",
            "👨‍👩‍👧‍👦 Spend time with family",
            "🎨 Work on a hobby",
            "🧘 Meditate",
            "😴 Get more sleep",
            "🌳 Spend time in nature",
            "🎭 Express creativity through art",
            "🎵 Practice an instrument",
            "🧩 Solve puzzles or games",
            "🌿 Gardening",
            "🧠 Practice mindfulness",
            "🚴 Go cycling",
            "🏊 Go swimming",
            "☕ Enjoy tea or coffee mindfully",
            "🧹 Declutter living space",
            "💤 Better sleep habits",
            "🤝 Volunteer in community",
            "🗣️ Learn a new language"
        )
    }
    
    private fun setupAPIKeyPreference() {
        findPreference<EditTextPreference>("openai_api_key")?.let { preference ->
            // Mask the API key in the summary
            preference.summaryProvider = Preference.SummaryProvider<EditTextPreference> { pref ->
                val apiKey = pref.text
                if (apiKey.isNullOrBlank()) {
                    getString(R.string.openai_api_key_summary)
                } else {
                    // Mask all but the last 4 characters
                    val maskedKey = "*".repeat(apiKey.length - 4) + apiKey.takeLast(4)
                    "API Key: $maskedKey"
                }
            }
        }
    }
    
    private fun setupCustomPromptPreference() {
        findPreference<EditTextPreference>("customPrompt")?.let { preference ->
            // Show a preview of the custom prompt
            preference.summaryProvider = Preference.SummaryProvider<EditTextPreference> { pref ->
                val prompt = pref.text
                if (prompt.isNullOrBlank()) {
                    getString(R.string.custom_prompt_description)
                } else {
                    if (prompt.length > 50) {
                        prompt.take(50) + "..."
                    } else {
                        prompt
                    }
                }
            }
        }
    }
    
    private fun setupCoachPersonaPreference() {
        findPreference<ListPreference>("coach_persona")?.let { preference ->
            // Update summary to show current coach selection
            preference.summaryProvider = Preference.SummaryProvider<ListPreference> { pref ->
                val personaId = pref.value
                getString(R.string.selected_coach, getCoachNameFromId(personaId))
            }
        }
    }
    
    private fun getCoachNameFromId(personaId: String): String {
        return when (personaId) {
            "sergeant" -> getString(R.string.coach_sergeant_name)
            "wisdom" -> getString(R.string.coach_wisdom_name)
            "spark" -> getString(R.string.coach_spark_name)
            "zen" -> getString(R.string.coach_zen_name)
            "nova" -> getString(R.string.coach_nova_name)
            else -> ""
        }
    }
    
    private fun updateAIStrictness(strictnessValue: String) {
        // Save the strictness value to SharedPreferences
        val strictnessIntValue = when (strictnessValue) {
            "lenient" -> 1
            "strict" -> 3
            else -> 2 // moderate
        }
        
        // Save to the shared preferences used by the AI service
        val appSharedPrefs = requireContext().getSharedPreferences(
            AppBlockerService.PREFS_NAME, Context.MODE_PRIVATE)
        appSharedPrefs.edit().putInt(SettingsActivity.PREF_AI_STRICTNESS, strictnessIntValue).apply()
    }
    
    private fun updateAIModel(modelId: String) {
        // Save the selected model to shared preferences for the AI service
        val appSharedPrefs = requireContext().getSharedPreferences(
            AppBlockerService.PREFS_NAME, Context.MODE_PRIVATE)
        appSharedPrefs.edit().putString(SettingsActivity.PREF_AI_MODEL, modelId).apply()
    }
    
    private fun updateDebugMode(enabled: Boolean) {
        // Update debug mode in shared preferences
        val appSharedPrefs = requireContext().getSharedPreferences(
            AppBlockerService.PREFS_NAME, Context.MODE_PRIVATE)
        appSharedPrefs.edit().putBoolean(SettingsActivity.PREF_DEBUG_MODE, enabled).apply()
    }
    
    private fun updateCoachPersona(personaId: String) {
        // No special handling needed as the preference is saved automatically
        // and will be read by HomeFragment and other relevant components
        Log.d("SettingsFragment", "Coach persona updated to: $personaId")
        
        // Show toast to confirm change
        Toast.makeText(
            requireContext(),
            getString(R.string.coach_selected, getCoachNameFromId(personaId)),
            Toast.LENGTH_SHORT
        ).show()
    }
} 