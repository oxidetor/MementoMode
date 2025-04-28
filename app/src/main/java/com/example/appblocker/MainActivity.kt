package com.example.appblocker

import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.pm.ApplicationInfo
import android.os.Process
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.Manifest
import android.util.TypedValue
import androidx.fragment.app.Fragment
import com.example.appblocker.databinding.ActivityMainBinding
import java.util.Calendar
import java.util.concurrent.TimeUnit
import android.util.Log
import androidx.appcompat.widget.SwitchCompat
import android.view.animation.AlphaAnimation
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import android.widget.TextView

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var appsList: List<AppInfo> = emptyList()
    
    // Keep track of the current fragment
    private val homeFragment = HomeFragment()
    private val usageStatsFragment = UsageStatsFragment()
    private val settingsFragment = SettingsFragment()
    // Add other fragments if needed (e.g., social)

    private var toolbarSwitch: SwitchCompat? = null
    private var toolbarStatusText: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        
        setupBottomNavigation()
        loadApps() // Load apps list first
        // Load initial fragment (Home)
        if (savedInstanceState == null) {
            loadFragment(homeFragment)
            binding.bottomNavigation.selectedItemId = R.id.navigation_home
        }
        
        // Check if onboarding needs to be shown
        if (!isOnboardingCompleted()) {
            startActivity(Intent(this, ScreentimeCalculatorActivity::class.java))
            finish()
            return
        }

        ensurePermissions()
        // Removed setupHomeScreen and setupEventListeners calls here as they move to HomeFragment or are handled differently
    }
    
    override fun onResume() {
        super.onResume()
        updateServiceStatus() // Update service status globally
        updateUsageStats()    // Update stats globally, fragments will update their UI
    }
    
    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    loadFragment(homeFragment)
                    true
                }
                R.id.navigation_usage -> {
                    loadFragment(usageStatsFragment)
                    true // Return true to show selection
                }
                R.id.navigation_social -> {
                    // Show "Coming soon" message for social features
                    Toast.makeText(this, "Social features coming soon!", Toast.LENGTH_SHORT).show()
                    false // Return false to keep current selection
                }
                R.id.navigation_settings -> {
                    loadFragment(settingsFragment)
                    true // Return true to show selection
                }
                else -> false
            }
        }
    }
    
    // Function to load fragments into the container
    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
    
    // --- Methods Called by Fragments --- 
    
    fun toggleBlockerService() {
        val isRunning = isServiceRunning(AppBlockerService::class.java)
        if (isRunning) {
            stopBlockerService()
        } else {
            startBlockerService()
        }
        updateServiceStatus() // Update status immediately
    }

    fun showAppSelectionDialog() {
        val intent = Intent(this, SelectAppsActivity::class.java)
        startActivity(intent)
    }

    fun openCoachChat() {
        val intent = Intent(this, AppAccessRequestActivity::class.java)
        intent.putExtra("app_name", "AI Coach")
        intent.putExtra("package_name", "ai_coach") // Use a distinct identifier
        startActivity(intent)
    }
    
    // --- Update UI in Fragments --- 
    
    fun updateUsageStats() {
        if (!hasUsageStatsPermission()) {
            // Optionally notify the user or guide them to grant permission
            return
        }
        
        // Get usage stats manager
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        
        // Get today's usage
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()
        
        val usageStats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )
        
        // Calculate total foreground time
        var totalTimeInForeground = 0L
        for (stat in usageStats) {
            totalTimeInForeground += stat.totalTimeInForeground
        }
        
        // Each fragment should update its own UI based on data it needs
        // No need to call updateUsageStatsUI directly from here
    }

    fun updateServiceStatus() {
        val isRunning = isServiceRunning(AppBlockerService::class.java)
        
        // Update toolbar switch state
        toolbarSwitch?.isChecked = isRunning
        
        // Update status text and appearance
        updateToolbarStatus(isRunning)
    }
    
    // --- Helper Methods (Permissions, Service, etc.) --- 
    
    private fun isOnboardingCompleted(): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val calculatorCompleted = prefs.getBoolean(ScreentimeCalculatorActivity.PREF_CALC_COMPLETED, false)
        val aiCoachSetupCompleted = prefs.getBoolean(AiCoachSetupActivity.PREF_AI_COACH_SETUP_COMPLETED, false)
        
        return calculatorCompleted && aiCoachSetupCompleted
    }
    
    private fun ensurePermissions() {
        if (!hasUsageStatsPermission()) {
            requestUsageStatsPermission()
        }
        if (!hasOverlayPermission()) {
            requestOverlayPermission()
        }
    }
    
    private fun loadApps() {
        appsList = loadApps(this)
    }

    fun loadApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val result = mutableListOf<AppInfo>()
        
        // First, add all apps that have a launcher icon (user-interactable apps)
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        val launchableApps = pm.queryIntentActivities(intent, 0).map { 
            it.activityInfo.applicationInfo.packageName 
        }.toSet()
        
        installedApps.forEach { appInfo ->
            // Skip our own app
            if (appInfo.packageName == context.packageName) {
                return@forEach
            }
            
            // Keep if it's user-installed OR it has a launcher icon (user-interactable)
            val isUserApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0
            val isLaunchable = launchableApps.contains(appInfo.packageName)
            
            if (isUserApp || isLaunchable) {
                result.add(
                    AppInfo(
                        appInfo.loadLabel(pm).toString(),
                        appInfo.packageName,
                        appInfo.loadIcon(pm),
                        isSelected = isAppBlocked(context, appInfo.packageName)
                    )
                )
            }
        }
        
        return result.sortedBy { it.appName }
    }

    private fun isAppBlocked(packageName: String): Boolean {
        return isAppBlocked(this, packageName)
    }

    private fun isAppBlocked(context: Context, packageName: String): Boolean {
        val blockedApps = PreferenceManager.getDefaultSharedPreferences(context)
            .getStringSet("blocked_apps", emptySet()) ?: emptySet()
        return blockedApps.contains(packageName)
    }

    private fun getThemeColor(colorAttr: Int): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(colorAttr, typedValue, true)
        return typedValue.data
    }

    fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        try {
            for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
                if (serviceClass.name == service.service.className) {
                    return true
                }
            }
        } catch (e: Exception) {
             // Handle potential SecurityException or other issues
            Log.e("MainActivity", "Error checking service status: ${e.message}")
        }
        return false
    }

    private fun startBlockerService() {
        val blockedApps = PreferenceManager.getDefaultSharedPreferences(this)
            .getStringSet("blocked_apps", emptySet())
        
        if (blockedApps.isNullOrEmpty()) {
            Toast.makeText(this, "Please select apps to block first", Toast.LENGTH_SHORT).show()
            // Update toolbar switch directly if blocker fails to start
            toolbarSwitch?.isChecked = false 
            return
        }
        
        startService(Intent(this, AppBlockerService::class.java))
        Toast.makeText(this, "App blocker activated", Toast.LENGTH_SHORT).show()
    }

    private fun stopBlockerService() {
        stopService(Intent(this, AppBlockerService::class.java))
        Toast.makeText(this, "App blocker deactivated", Toast.LENGTH_SHORT).show()
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun requestUsageStatsPermission() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.permission_required))
            .setMessage(getString(R.string.usage_access_permission_message))
            .setPositiveButton("Grant") { _, _ ->
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            .setCancelable(false)
            .show()
    }

    private fun hasOverlayPermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun requestOverlayPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.permission_required))
                .setMessage(getString(R.string.overlay_permission_message))
                .setPositiveButton("Grant") { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
                .setCancelable(false)
                .show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        val switchItem = menu.findItem(R.id.action_toggle_blocker)
        val actionView = switchItem?.actionView
        
        toolbarSwitch = actionView?.findViewById(R.id.toolbar_switch)
        toolbarStatusText = actionView?.findViewById(R.id.toolbar_status_text)
        
        // Set initial state and listener
        toolbarSwitch?.let {
            it.isChecked = isServiceRunning(AppBlockerService::class.java)
            it.setOnCheckedChangeListener { _, isChecked ->
                toggleBlockerService()
            }
        }
        
        // Set initial status text state
        updateToolbarStatus(isServiceRunning(AppBlockerService::class.java))
        
        return true
    }
    
    private fun updateToolbarStatus(isActive: Boolean) {
        toolbarStatusText?.let { statusText ->
            // Update text content
            val statusMessage = if (isActive) 
                getString(R.string.blocker_status_active) 
            else 
                getString(R.string.blocker_status_inactive)
            
            // Prepare animation
            val fadeOut = AlphaAnimation(1f, 0.3f)
            fadeOut.duration = 200
            fadeOut.fillAfter = false
            
            val fadeIn = AlphaAnimation(0.3f, 1f)
            fadeIn.duration = 200
            fadeIn.fillAfter = true
            fadeIn.startOffset = 200
            
            // Execute fade out
            statusText.startAnimation(fadeOut)
            
            // Set selected state (for background drawable selector)
            statusText.isSelected = isActive
            
            // Set text color based on status
            val textColor = if (isActive) {
                ContextCompat.getColor(this, R.color.zen_white)
            } else {
                ContextCompat.getColor(this, R.color.zen_white)
            }
            statusText.setTextColor(textColor)
            
            // Post the text change and fade-in after the fade-out completes
            Handler(Looper.getMainLooper()).postDelayed({
                statusText.text = statusMessage
                statusText.startAnimation(fadeIn)
            }, 200)
        }
    }

    companion object {
        // Removed SERVICE_RUNNING_KEY as service status is checked directly
    }
} 