package com.example.appblocker.config

/**
 * Configuration class for API keys and related settings
 * 
 * =============================================
 * HOW TO USE THE OPENAI API:
 * =============================================
 * 1. Get an API key from https://platform.openai.com/api-keys
 * 2. Add it below as DEVELOPER_API_KEY or let users enter their own key
 * 3. The app will use the Chat Completions API to evaluate access requests
 * =============================================
 */
object ApiConfig {
    // Set your OpenAI API key here for development
    // When this is blank, the app will prompt users for their own key
    // Loads the API key from openai_api_key.txt at runtime, or returns blank if not found
    val DEVELOPER_API_KEY: String by lazy { loadApiKeyFromFile() }

    private fun loadApiKeyFromFile(): String {
        return try {
            val file = java.io.File("/data/data/com.example.appblocker/config/openai_api_key.txt")
            if (file.exists()) file.readText().trim() else ""
        } catch (e: Exception) {
            ""
        }
    }

    // Check if developer key is available
    fun hasDevKey(): Boolean = DEVELOPER_API_KEY.isNotBlank()
} 