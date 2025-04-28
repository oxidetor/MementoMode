package com.example.appblocker

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.appblocker.databinding.FragmentUsageStatsBinding
import com.google.android.material.switchmaterial.SwitchMaterial
import java.util.concurrent.TimeUnit
import android.app.AppOpsManager

enum class TimeRange { DAY, WEEK, MONTH, YEAR }

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable,
    val timeInForeground: Long,
    val numberOfDays: Int, // For calculating average
    val isBlocked: Boolean
)

class UsageStatsFragment : Fragment() {
    private companion object {
        private const val TAG = "UsageStatsFragment"
    }

    private var _binding: FragmentUsageStatsBinding? = null
    private val binding get() = _binding!!

    private lateinit var usageStatsAdapter: UsageStatsAdapter
    private val usageStatsList = mutableListOf<AppUsageInfo>()
    private val filteredUsageStatsList = mutableListOf<AppUsageInfo>()
    private var currentTimeRange = TimeRange.DAY
    private var showBlockedAppsOnly = false
    private var blockedApps = setOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUsageStatsBinding.inflate(inflater, container, false)
        val view = binding.root

        // Load blocked apps
        loadBlockedApps()

        // Set up RecyclerView
        setupRecyclerView()

        // Set up time filter spinner
        setupTimeFilterSpinner()

        // Set up blocked apps toggle
        setupBlockedAppsToggle()

        // Load initial usage stats
        loadUsageStats(currentTimeRange)

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Avoid memory leaks
    }

    private fun loadBlockedApps() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        blockedApps = prefs.getStringSet("blocked_apps", emptySet()) ?: emptySet()
        Log.d(TAG, "Loaded blocked apps: $blockedApps")
    }

    private fun setupRecyclerView() {
        binding.recyclerViewUsageStats.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewUsageStats.addItemDecoration(
            DividerItemDecoration(context, DividerItemDecoration.VERTICAL)
        )
        usageStatsAdapter = UsageStatsAdapter(filteredUsageStatsList)
        binding.recyclerViewUsageStats.adapter = usageStatsAdapter
    }

    private fun setupTimeFilterSpinner() {
        val timeRanges = listOf(
            getString(R.string.time_range_day),
            getString(R.string.time_range_week),
            getString(R.string.time_range_month),
            getString(R.string.time_range_year)
        )

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, timeRanges)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerTimeFilter.adapter = adapter

        binding.spinnerTimeFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentTimeRange = when(position) {
                    0 -> TimeRange.DAY
                    1 -> TimeRange.WEEK
                    2 -> TimeRange.MONTH
                    3 -> TimeRange.YEAR
                    else -> TimeRange.DAY
                }
                loadUsageStats(currentTimeRange)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) { /* Do nothing */ }
        }
    }

    private fun setupBlockedAppsToggle() {
        binding.switchBlockedAppsOnly.isChecked = showBlockedAppsOnly

        binding.switchBlockedAppsOnly.setOnCheckedChangeListener { _, isChecked ->
            showBlockedAppsOnly = isChecked
            filterAndDisplayUsageStats()
        }
    }

    private fun loadUsageStats(timeRange: TimeRange) {
        // Check for permission before proceeding
        if (!hasUsageStatsPermission()) {
            binding.textViewNoStats.text = getString(R.string.usage_permission_required)
            binding.cardViewNoStats.visibility = View.VISIBLE
            binding.recyclerViewUsageStats.visibility = View.GONE
            return
        }

        val usageStatsManager = context?.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        if (usageStatsManager == null) {
            Log.e(TAG, "UsageStatsManager not available")
            // Optionally show an error message to the user
            binding.textViewNoStats.text = "Error: Could not get usage stats service."
            binding.cardViewNoStats.visibility = View.VISIBLE
            binding.recyclerViewUsageStats.visibility = View.GONE
            return
        }
        
        val currentTime = System.currentTimeMillis()
        val startTime = when (timeRange) {
            TimeRange.DAY -> currentTime - TimeUnit.DAYS.toMillis(1)
            TimeRange.WEEK -> currentTime - TimeUnit.DAYS.toMillis(7)
            TimeRange.MONTH -> currentTime - TimeUnit.DAYS.toMillis(30)
            TimeRange.YEAR -> currentTime - TimeUnit.DAYS.toMillis(365)
        }

        val stats = usageStatsManager.queryUsageStats(
            when (timeRange) {
                TimeRange.DAY -> UsageStatsManager.INTERVAL_DAILY
                TimeRange.WEEK -> UsageStatsManager.INTERVAL_WEEKLY
                TimeRange.MONTH -> UsageStatsManager.INTERVAL_MONTHLY
                TimeRange.YEAR -> UsageStatsManager.INTERVAL_YEARLY
            },
            startTime, currentTime
        )

        val numberOfDays = when (timeRange) {
            TimeRange.DAY -> 1
            TimeRange.WEEK -> 7
            TimeRange.MONTH -> 30
            TimeRange.YEAR -> 365
        }

        processUsageStats(stats, numberOfDays)
    }

    private fun processUsageStats(stats: List<UsageStats>?, numberOfDays: Int) {
        usageStatsList.clear()
        if (stats == null) {
             Log.w(TAG, "Received null stats list")
             filterAndDisplayUsageStats() // Display empty state
             return
        }

        val packageManager = context?.packageManager
        if (packageManager == null) {
            Log.e(TAG, "PackageManager not available")
            filterAndDisplayUsageStats() // Display empty state or error
            return
        }

        val appUsageMap = mutableMapOf<String, Long>()
        for (stat in stats) {
            val totalTimeInForeground = stat.totalTimeInForeground
            if (totalTimeInForeground > 0) {
                appUsageMap[stat.packageName] = (appUsageMap[stat.packageName] ?: 0L) + totalTimeInForeground
            }
        }

        for ((packageName, timeInForeground) in appUsageMap) {
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                // Skip system apps unless they have significant usage (heuristic)
                if (appInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0 || timeInForeground > TimeUnit.MINUTES.toMillis(1)) { 
                    usageStatsList.add(
                        AppUsageInfo(
                            packageName = packageName,
                            appName = packageManager.getApplicationLabel(appInfo).toString(),
                            icon = packageManager.getApplicationIcon(appInfo),
                            timeInForeground = timeInForeground,
                            numberOfDays = numberOfDays,
                            isBlocked = blockedApps.contains(packageName)
                        )
                    )
                }
            } catch (e: PackageManager.NameNotFoundException) {
                Log.w(TAG, "Package not found: $packageName")
            }
        }

        filterAndDisplayUsageStats()
    }

    private fun filterAndDisplayUsageStats() {
        filteredUsageStatsList.clear()
        
        if (showBlockedAppsOnly) {
            filteredUsageStatsList.addAll(usageStatsList.filter { it.isBlocked })
        } else {
            filteredUsageStatsList.addAll(usageStatsList)
        }

        // Sort by average daily usage time
        filteredUsageStatsList.sortByDescending { 
            if (it.numberOfDays > 0) it.timeInForeground / it.numberOfDays else it.timeInForeground 
        }

        // Update UI based on filtered list
        if (filteredUsageStatsList.isEmpty()) {
            val message = when {
                showBlockedAppsOnly && blockedApps.isEmpty() -> getString(R.string.no_blocked_apps)
                showBlockedAppsOnly -> getString(R.string.no_blocked_app_usage)
                else -> getString(R.string.no_usage_stats)
            }
            binding.textViewNoStats.text = message
            binding.cardViewNoStats.visibility = View.VISIBLE
            binding.recyclerViewUsageStats.visibility = View.GONE
        } else {
            binding.cardViewNoStats.visibility = View.GONE
            binding.recyclerViewUsageStats.visibility = View.VISIBLE
            usageStatsAdapter.notifyDataSetChanged()
        }
    }
    
    // Helper function to check usage stats permission (similar to MainActivity)
    private fun hasUsageStatsPermission(): Boolean {
        val appOps = context?.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                requireContext().packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                requireContext().packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
    
    // Note: Requesting permission should ideally be handled by the Activity (MainActivity)
    // to avoid issues with fragment lifecycle and activity results.
    // If permission is denied here, we just show the message.
} 