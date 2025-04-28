package com.example.appblocker.ai

import android.content.Context
import android.util.Log

/**
 * Factory class to create AI service instances
 */
object AIServiceFactory {
    private val TAG = "AIServiceFactory"
    
    fun getService(context: Context): AIService {
        Log.d(TAG, "Creating OpenAI service")
        return RealOpenAIService(context)
    }
} 