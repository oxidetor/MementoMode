package com.example.appblocker

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log

object AppUtils {
    private const val TAG = "AppUtils"
    
    fun getInstalledApps(context: Context): List<AppInfo> {
        val packageManager = context.packageManager
        val appList = mutableListOf<AppInfo>()
        val seenPackages = mutableSetOf<String>()
        
        try {
            // First try: Get apps with launcher intent
            val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            
            val resolveInfoList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(
                    mainIntent,
                    PackageManager.ResolveInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(mainIntent, 0)
            }
            
            Log.d(TAG, "Found ${resolveInfoList.size} apps with launcher")
            
            for (resolveInfo in resolveInfoList) {
                val appInfo = resolveInfo.activityInfo.applicationInfo
                if (isUserApp(appInfo) && appInfo.packageName != context.packageName) {
                    val appName = appInfo.loadLabel(packageManager).toString()
                    val appIcon = appInfo.loadIcon(packageManager)
                    appList.add(AppInfo(appInfo.packageName, appName, appIcon))
                    seenPackages.add(appInfo.packageName)
                    Log.d(TAG, "Added launcher app: $appName")
                }
            }

            // Second try: Get all installed apps
            val installedApps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getInstalledApplications(
                    PackageManager.ApplicationInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstalledApplications(0)
            }

            Log.d(TAG, "Found ${installedApps.size} total installed apps")

            for (appInfo in installedApps) {
                // Skip if we already added this app
                if (seenPackages.contains(appInfo.packageName)) continue
                
                if (isUserApp(appInfo) && appInfo.packageName != context.packageName) {
                    // Check if the app can be launched
                    val launchIntent = packageManager.getLaunchIntentForPackage(appInfo.packageName)
                    if (launchIntent != null) {
                        val appName = appInfo.loadLabel(packageManager).toString()
                        val appIcon = appInfo.loadIcon(packageManager)
                        appList.add(AppInfo(appInfo.packageName, appName, appIcon))
                        Log.d(TAG, "Added installed app: $appName")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting apps", e)
        }
        
        val sortedList = appList.sortedBy { it.appName }
        Log.d(TAG, "Final list contains ${sortedList.size} apps")
        return sortedList
    }
    
    private fun isUserApp(appInfo: ApplicationInfo): Boolean {
        // Consider an app to be a user app if:
        // 1. It's not a system app, OR
        // 2. It's a system app that has been updated, OR
        // 3. It's certain important system apps we want to allow blocking
        val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val isUpdatedSystemApp = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        
        // List of system package prefixes we want to allow
        val allowedSystemPackages = listOf(
            "com.google.android.youtube",
            "com.google.android.apps",
            "com.android.chrome",
            "com.android.vending",  // Play Store
            "com.google.android.gm", // Gmail
            "com.google.android.googlequicksearchbox",
            "com.android.messaging",
            "com.google.android.videos", // Google TV
            "com.google.android.apps.photos"
        )
        
        val isAllowedSystemApp = allowedSystemPackages.any { prefix ->
            appInfo.packageName.startsWith(prefix)
        }
        
        return !isSystemApp || isUpdatedSystemApp || isAllowedSystemApp
    }
    
    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
    
    fun hasOverlayPermission(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }
    
    fun openUsageAccessSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open usage access settings", e)
            openAppSettings(context)
        }
    }
    
    fun openOverlaySettings(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open overlay settings", e)
            openAppSettings(context)
        }
    }
    
    private fun openAppSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }
    
    fun getForegroundApp(context: Context): String? {
        if (!hasUsageStatsPermission(context)) {
            return null
        }
        
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        
        // Get usage stats for the last 1 second
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY, time - 1000, time
        )
        
        if (stats.isEmpty()) {
            return null
        }
        
        // Return the most recently used app
        return stats.maxByOrNull { it.lastTimeUsed }?.packageName
    }
} 