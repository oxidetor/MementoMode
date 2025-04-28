package com.example.appblocker.models

data class AlternativeActivity(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    var isSelected: Boolean = false
) 