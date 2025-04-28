package com.example.appblocker.models

import android.graphics.drawable.Drawable

data class BlockableApp(
    val packageName: String,
    val name: String,
    val icon: Drawable,
    var isSelected: Boolean = false
) 