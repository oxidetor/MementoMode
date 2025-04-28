package com.example.appblocker

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import androidx.viewpager2.widget.ViewPager2
import com.example.appblocker.adapters.CalculatorPagerAdapter
import com.example.appblocker.calculator.pages.EstimateInputHandler
import com.example.appblocker.calculator.pages.GoalSettingHandler
import com.example.appblocker.calculator.pages.HabitSelectionHandler
import com.example.appblocker.calculator.pages.LifetimeImpactHandler
import com.example.appblocker.calculator.pages.UsageBreakdownHandler
import java.text.DecimalFormat
import java.util.Calendar
import java.util.concurrent.TimeUnit

class ScreentimeCalculatorActivity : AppCompatActivity() {
    private lateinit var viewPager: ViewPager2
    private lateinit var nextButton: Button
    private lateinit var backButton: Button
    private var estimatedHours: Float = 0f
    private var selectedGoalMinutes: Int = 0
    private val selectedActivities = mutableSetOf<String>()

    private val decimalFormat = DecimalFormat("#,###.#")
    
    // Page handlers following SOLID principles
    private val pageHandlers = mapOf(
        "estimate_input" to EstimateInputHandler(this),
        "lifetime_impact" to LifetimeImpactHandler(this),
        "usage_breakdown" to UsageBreakdownHandler(this),
        "goal_setting" to GoalSettingHandler(this),
        "habit_selection" to HabitSelectionHandler(this)
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_screentime_calculator)
        
        supportActionBar?.hide()
        
        viewPager = findViewById(R.id.viewPager)
        nextButton = findViewById(R.id.nextButton)
        backButton = findViewById(R.id.backButton)
        
        setupViewPager()
        setupButtons()
        
        // Calculate default value for daily screen time
        loadAverageUsageTime()
    }
    
    private fun setupViewPager() {
        viewPager.adapter = CalculatorPagerAdapter()
        viewPager.isUserInputEnabled = false
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                Log.d("ScreentimeCalculator", "onPageSelected called for position: $position")
                updateButtonVisibility(position)
                setupCurrentPage()
            }
        })
        
        // Manually trigger setup for the first page
        Log.d("ScreentimeCalculator", "Manually triggering setup for first page")
        // Use post to ensure the view is fully laid out
        viewPager.post {
            setupCurrentPage()
        }
    }
    
    private fun setupCurrentPage() {
        val currentView = getCurrentPageView() ?: return
        val pageTag = currentView.tag as? String ?: return
        val handler = pageHandlers[pageTag] ?: return
        
        Log.d("ScreentimeCalculator", "Setting up page with tag: $pageTag, handler: ${handler.javaClass.simpleName}")
        
        if (handler.setupPage(currentView)) {
            Log.d("ScreentimeCalculator", "setupPage successful, calling updatePageUI for $pageTag")
            handler.updatePageUI(currentView)
        } else {
            Log.e("ScreentimeCalculator", "Failed to set up page: $pageTag")
        }
    }
    
    private fun getCurrentPageView(): View? {
        val currentItem = viewPager.currentItem
        Log.d("ScreentimeCalculator", "Getting view for position: $currentItem")
        
        try {
            // Try the standard method first
            val recyclerView = viewPager.getChildAt(0) as? androidx.recyclerview.widget.RecyclerView
            if (recyclerView == null) {
                Log.e("ScreentimeCalculator", "Failed to get RecyclerView from ViewPager")
                return null
            }
            
            val viewHolder = recyclerView.findViewHolderForAdapterPosition(currentItem)
            if (viewHolder == null) {
                Log.e("ScreentimeCalculator", "ViewHolder is null for position: $currentItem")
                
                // Fallback method: try to find view that's already created
                for (i in 0 until recyclerView.childCount) {
                    val child = recyclerView.getChildAt(i)
                    val childPosition = recyclerView.getChildAdapterPosition(child)
                    if (childPosition == currentItem) {
                        Log.d("ScreentimeCalculator", "Found view using fallback method for position: $currentItem")
                        return child
                    }
                }
                
                return null
            }
            
            val itemView = viewHolder.itemView
            Log.d("ScreentimeCalculator", "Found view for position: $currentItem, tag: ${itemView.tag}")
            return itemView
            
        } catch (e: Exception) {
            Log.e("ScreentimeCalculator", "Error getting current page view: ${e.message}")
            e.printStackTrace()
            return null
        }
    }
    
    private fun updateButtonVisibility(position: Int) {
        backButton.visibility = if (position > 0) View.VISIBLE else View.INVISIBLE
        nextButton.text = if (position == 3) getString(R.string.commit_to_change) else getString(R.string.next)
    }
    
    private fun setupButtons() {
        nextButton.setOnClickListener {
            Log.d("ScreentimeCalculator", "Next button clicked, current page: ${viewPager.currentItem}")
            val currentView = getCurrentPageView()
            if (currentView != null) {
                val pageTag = currentView.tag as? String
                pageTag?.let { tag -> pageHandlers[tag]?.savePageData(currentView) }
            }
            
            if (viewPager.currentItem == 3) {
                // Save calculator data (excluding habits since they're moved to AI coach setup)
                val prefs = PreferenceManager.getDefaultSharedPreferences(this)
                prefs.edit()
                    .putBoolean(PREF_CALC_COMPLETED, true)
                    .putFloat("estimated_hours", estimatedHours)
                    .putInt("goal_minutes", selectedGoalMinutes)
                    .apply()

                // Start AI Coach Setup
                val intent = Intent(this, AiCoachSetupActivity::class.java)
                startActivity(intent)
                finish()
            } else {
                // Move to next page
                val nextPosition = viewPager.currentItem + 1
                Log.d("ScreentimeCalculator", "Moving to page position: $nextPosition")
                viewPager.currentItem = nextPosition
                
                // ViewPager2 onPageSelected callback might not always trigger
                // Manually ensure the next page gets set up
                viewPager.post {
                    if (getCurrentPageView() != null) {
                        Log.d("ScreentimeCalculator", "Manually triggering setupCurrentPage after navigation")
                        setupCurrentPage()
                    }
                }
            }
        }

        backButton.setOnClickListener {
            if (viewPager.currentItem > 0) {
                Log.d("ScreentimeCalculator", "Back button clicked, moving to page: ${viewPager.currentItem - 1}")
                viewPager.currentItem -= 1
                
                // Manually ensure the previous page gets set up
                viewPager.post {
                    if (getCurrentPageView() != null) {
                        Log.d("ScreentimeCalculator", "Manually triggering setupCurrentPage after back navigation")
                        setupCurrentPage()
                    }
                }
            }
        }

        updateButtonVisibility(0)
    }
    
    private fun loadAverageUsageTime(): Float {
        if (!hasUsageStatsPermission()) {
            requestUsageStatsPermission()
            return 4f // Default value
        }

        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -7)

        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            calendar.timeInMillis,
            System.currentTimeMillis()
        )

        if (usageStats.isEmpty()) {
            return 4f // Default value
        }

        val totalTimeInForeground = usageStats.sumOf { it.totalTimeInForeground }
        val averageDaily = TimeUnit.MILLISECONDS.toMinutes(totalTimeInForeground / 7)
        return averageDaily / 60f
    }
    
    fun getEstimatedHours(): Float = estimatedHours
    
    fun setEstimatedHours(hours: Float) {
        this.estimatedHours = hours
    }
    
    fun getSelectedGoalHours(): Float = selectedGoalMinutes / 60f
    
    fun setSelectedGoalHours(hours: Float) {
        // Convert hours to minutes and round to nearest 15 minutes
        val minutes = (hours * 60).toInt()
        this.selectedGoalMinutes = (minutes / 15) * 15
    }
    
    fun getSelectedGoalMinutes(): Int = selectedGoalMinutes
    
    fun setSelectedGoalMinutes(minutes: Int) {
        // Ensure we're using 15-minute increments
        this.selectedGoalMinutes = (minutes / 15) * 15
    }
    
    fun getSelectedActivities(): MutableSet<String> = selectedActivities
    
    fun animateTextHighlight(textView: TextView) {
        val colorFrom = textView.currentTextColor
        val colorTo = ContextCompat.getColor(this, R.color.highlight_color)
        
        val colorAnim = ValueAnimator.ofArgb(colorFrom, colorTo, colorFrom)
        colorAnim.duration = 1500
        colorAnim.addUpdateListener { animation ->
            textView.setTextColor(animation.animatedValue as Int)
        }
        colorAnim.start()
    }
    
    fun animateNumberCounter(textView: TextView, targetValue: Float) {
        try {
            val animator = ValueAnimator.ofFloat(0f, targetValue)
            animator.duration = 2000
            animator.interpolator = OvershootInterpolator(1.2f)
            
            animator.addUpdateListener { animation ->
                val value = animation.animatedValue as Float
                textView.text = decimalFormat.format(value)
            }
            
            animator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    animateTextHighlight(textView)
                }
            })
            
            animator.start()
        } catch (e: Exception) {
            Log.e("ScreentimeCalc", "Error animating number counter", e)
            // If animation fails, just set the value directly
            textView.text = decimalFormat.format(targetValue)
        }
    }
    
    fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }
    
    fun requestUsageStatsPermission() {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }
    
    fun getWeeklyUsageStats(): List<android.app.usage.UsageStats> {
        if (!hasUsageStatsPermission()) {
            requestUsageStatsPermission()
            return emptyList()
        }
        
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        
        return usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            calendar.timeInMillis,
            System.currentTimeMillis()
        )
    }
    
    companion object {
        const val PREF_CALC_COMPLETED = "screentime_calculator_completed"
        const val PREF_ALWAYS_SHOW_CALCULATOR = "always_show_calculator"
    }
} 