package com.example.appblocker

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.util.concurrent.TimeUnit

class TemporaryAccessManager(context: Context) {
    private val TAG = "TemporaryAccessManager"
    private val prefs: SharedPreferences = context.getSharedPreferences("temp_access", Context.MODE_PRIVATE)

    fun grantTemporaryAccess(packageName: String, minutes: Int) {
        val expiryTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(minutes.toLong())
        prefs.edit().putLong(packageName, expiryTime).apply()
        Log.d(TAG, "Granted $minutes minutes of access to $packageName until $expiryTime")
    }

    fun hasTemporaryAccess(packageName: String): Boolean {
        val expiryTime = prefs.getLong(packageName, 0)
        val hasAccess = expiryTime > System.currentTimeMillis()
        
        if (!hasAccess && expiryTime != 0L) {
            // Clean up expired access
            prefs.edit().remove(packageName).apply()
        }
        
        return hasAccess
    }

    fun getRemainingMinutes(packageName: String): Int {
        val expiryTime = prefs.getLong(packageName, 0)
        if (expiryTime == 0L) return 0
        
        val remainingMillis = expiryTime - System.currentTimeMillis()
        return TimeUnit.MILLISECONDS.toMinutes(remainingMillis).toInt()
    }

    fun getRemainingSeconds(packageName: String): Int {
        val expiryTime = prefs.getLong(packageName, 0)
        if (expiryTime == 0L) return 0
        
        val remainingMillis = expiryTime - System.currentTimeMillis()
        return TimeUnit.MILLISECONDS.toSeconds(remainingMillis).toInt()
    }

    fun revokeAccess(packageName: String) {
        prefs.edit().remove(packageName).apply()
        Log.d(TAG, "Revoked access for $packageName")
    }
} 