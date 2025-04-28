package com.example.appblocker

import android.graphics.drawable.Drawable

data class AppInfo(
    val appName: String,
    val packageName: String,
    val appIcon: Drawable,
    var isSelected: Boolean = false
) 