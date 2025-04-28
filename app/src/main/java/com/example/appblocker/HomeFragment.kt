package com.example.appblocker

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.util.Calendar
import java.util.concurrent.TimeUnit

class HomeFragment : Fragment() {
    
    // UI components
    private lateinit var coachAvatar: View
    private lateinit var coachName: TextView
    private lateinit var coachMessage: TextView
    private lateinit var habitsChipGroup: ChipGroup
    private lateinit var usageValue: TextView
    private lateinit var goalProgress: TextView
    private lateinit var goalProgressBar: LinearProgressIndicator
    private lateinit var todaySaved: TextView
    private lateinit var weekSaved: TextView
    private lateinit var totalSaved: TextView
    private lateinit var updateAppsButton: MaterialButton
    private lateinit var premiumBanner: View
    private lateinit var trialCountdown: TextView

    companion object {
        private const val PREF_TRIAL_START_TIME = "trial_start_time"
        private const val TRIAL_DURATION_DAYS = 7
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize UI components
        initViews(view)
        
        // Set up button click listeners
        setupButtonListeners()
        
        // Load and display data
        loadUserData()
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh data when fragment becomes visible
        loadUserData()
    }
    
    private fun initViews(view: View) {
        // Find all view references
        coachAvatar = view.findViewById(R.id.coachAvatar)
        coachName = view.findViewById(R.id.coachName)
        coachMessage = view.findViewById(R.id.coachMessage)
        habitsChipGroup = view.findViewById(R.id.habitsChipGroup)
        usageValue = view.findViewById(R.id.usageValue)
        goalProgress = view.findViewById(R.id.goalProgress)
        goalProgressBar = view.findViewById(R.id.goalProgressBar)
        todaySaved = view.findViewById(R.id.todaySaved)
        weekSaved = view.findViewById(R.id.weekSaved)
        totalSaved = view.findViewById(R.id.totalSaved)
        updateAppsButton = view.findViewById(R.id.updateAppsButton)
        premiumBanner = view.findViewById(R.id.premiumBanner)
        trialCountdown = view.findViewById(R.id.trialCountdown)
    }
    
    private fun setupButtonListeners() {
        updateAppsButton.setOnClickListener {
            val intent = Intent(context, SelectAppsActivity::class.java)
            startActivity(intent)
        }
        
        premiumBanner.setOnClickListener {
            val intent = Intent(context, PremiumActivity::class.java)
            startActivity(intent)
        }
    }
    
    private fun loadUserData() {
        context?.let { ctx ->
            val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
            
            // 1. Load user name if available
            val userName = prefs.getString("user_name", "")
            
            // 2. Load coach persona - use the exact key that's used in AiCoachSetupActivity
            val coachPersonaId = prefs.getString("coach_persona", "nova")
            setupCoachPersona(coachPersonaId ?: "nova", userName)
            
            // 3. Load and display habits
            loadHabits()
            
            // 4. Load usage statistics
            updateUsageStatistics()
            
            // 5. Update trial countdown
            updateTrialCountdown(prefs)
        }
    }
    
    private fun setupCoachPersona(personaId: String, userName: String?) {
        // Set coach name
        coachName.text = when (personaId) {
            "sergeant" -> getString(R.string.coach_sergeant_name)
            "wisdom" -> getString(R.string.coach_wisdom_name)
            "spark" -> getString(R.string.coach_spark_name)
            "zen" -> getString(R.string.coach_zen_name)
            "nova" -> getString(R.string.coach_nova_name)
            else -> getString(R.string.coach_nova_name)
        }
        
        // Set coach avatar
        val drawableResId = when (personaId) {
            "sergeant" -> R.drawable.ic_coach_sergeant
            "wisdom" -> R.drawable.ic_coach_wisdom
            "spark" -> R.drawable.ic_coach_spark
            "zen" -> R.drawable.ic_coach_zen
            "nova" -> R.drawable.ic_coach_nova
            else -> R.drawable.ic_coach_nova
        }
        (coachAvatar as? android.widget.ImageView)?.setImageResource(drawableResId)
        
        // Set appropriate greeting based on time of day
        val calendar = Calendar.getInstance()
        val hourOfDay = calendar.get(Calendar.HOUR_OF_DAY)
        
        val timeOfDayGreeting = when {
            hourOfDay < 12 -> getString(R.string.coach_greeting_morning_time)
            hourOfDay < 18 -> getString(R.string.coach_greeting_afternoon_time)
            else -> getString(R.string.coach_greeting_evening_time)
        }
        
        // Create personalized greeting with username if available
        val personalizedGreeting = if (!userName.isNullOrEmpty()) {
            "$timeOfDayGreeting $userName! ${getString(R.string.coach_greeting_with_name)}"
        } else {
            "$timeOfDayGreeting! ${getString(R.string.coach_greeting_generic)}"
        }
        
        coachMessage.text = personalizedGreeting
    }
    
    private fun loadHabits() {
        context?.let { ctx ->
            val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
            val selectedHabits = prefs.getStringSet("selected_habits", emptySet()) ?: emptySet()
            
            // Clear existing chips
            habitsChipGroup.removeAllViews()
            
            // Add a chip for each habit
            for (habit in selectedHabits) {
                val chip = Chip(ctx)
                chip.text = habit
                chip.isCheckable = false
                chip.isChecked = false
                habitsChipGroup.addView(chip)
            }
        }
    }
    
    private fun updateUsageStatistics() {
        context?.let { ctx ->
            val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
            
            // Get screen time goal in minutes
            val screenTimeMinutes = try {
                // First check if we have the updated goal_minutes preference
                prefs.getInt("goal_minutes", 120) // Default 2 hours = 120 minutes
            } catch (e: ClassCastException) {
                // Check if we have older float-based goal_hours
                try {
                    (prefs.getFloat("goal_hours", 2f) * 60).toInt()
                } catch (e2: ClassCastException) {
                    // If all else fails, use default
                    120 // 2 hours in minutes
                }
            }

            // Also check if we have a "daily_goal" preference from settings (may be stored differently)
            val dailyGoalStr = prefs.getString("daily_goal", null)
            val finalGoalMinutes = if (dailyGoalStr != null) {
                try {
                    (dailyGoalStr.toFloat() * 60).toInt()
                } catch (e: NumberFormatException) {
                    screenTimeMinutes
                }
            } else {
                screenTimeMinutes
            }
            
            // Convert goal minutes to hours for display
            val finalGoalHours = finalGoalMinutes / 60f
            
            // Get today's usage time (placeholder for actual implementation)
            val todayUsageHours = getTodayUsageTime() / 60f
            
            // Calculate progress percentage
            val progressPercentage = (todayUsageHours / finalGoalHours * 100).toInt().coerceIn(0, 100)
            
            // Update UI
            usageValue.text = String.format("%.1f hrs", todayUsageHours)
            goalProgress.text = getString(R.string.goal_progress_text, progressPercentage, String.format("%.2f", finalGoalHours))
            goalProgressBar.progress = progressPercentage
            
            // Time saved calculations (simplified)
            val goalSavingsPerDay = 1.5f // Placeholder - calculate from goal and actual usage
            todaySaved.text = String.format("%.1f hrs", goalSavingsPerDay)
            weekSaved.text = String.format("%.1f hrs", goalSavingsPerDay * 7)
            totalSaved.text = String.format("%.1f hrs", goalSavingsPerDay * 14) // Just a placeholder
        }
    }
    
    private fun getTodayUsageTime(): Int {
        // Return today's usage time in minutes (placeholder implementation)
        return 120 // 2 hours
    }
    
    private fun updateTrialCountdown(prefs: android.content.SharedPreferences) {
        // Get or initialize trial start time
        var trialStartTime = prefs.getLong(PREF_TRIAL_START_TIME, 0)
        
        // If trial hasn't started yet, initialize it
        if (trialStartTime == 0L) {
            trialStartTime = System.currentTimeMillis()
            prefs.edit().putLong(PREF_TRIAL_START_TIME, trialStartTime).apply()
        }
        
        // Calculate time remaining in trial
        val currentTimeMillis = System.currentTimeMillis()
        val trialEndTimeMillis = trialStartTime + TimeUnit.DAYS.toMillis(TRIAL_DURATION_DAYS.toLong())
        val remainingTimeMillis = trialEndTimeMillis - currentTimeMillis
        
        if (remainingTimeMillis <= 0) {
            // Trial has expired
            trialCountdown.text = getString(R.string.trial_expired)
        } else {
            // Calculate days and hours remaining
            val remainingDays = TimeUnit.MILLISECONDS.toDays(remainingTimeMillis)
            val remainingHours = TimeUnit.MILLISECONDS.toHours(remainingTimeMillis) % 24
            
            // Format the countdown text
            trialCountdown.text = if (remainingDays > 0) {
                getString(R.string.trial_countdown, remainingDays.toInt(), remainingHours.toInt())
            } else {
                // Just hours remaining
                getString(R.string.trial_countdown, 0, remainingHours.toInt())
            }
        }
    }
} 