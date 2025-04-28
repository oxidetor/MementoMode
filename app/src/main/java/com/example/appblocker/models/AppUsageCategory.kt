package com.example.appblocker.models

data class AppUsageCategory(
    val name: String,
    val timeSpentMinutes: Long,
    val percentageOfTotal: Float,
    val iconResId: Int
) 