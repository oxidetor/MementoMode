package com.example.appblocker.ai

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.appblocker.AppBlockerService
import com.example.appblocker.SettingsActivity
import com.example.appblocker.config.ApiConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Service to get available OpenAI models
 */
class OpenAIModelService(private val context: Context) {
    private val TAG = "OpenAIModelService"
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        AppBlockerService.PREFS_NAME, Context.MODE_PRIVATE
    )
    
    private val availableModels = listOf(
        ModelInfo("gpt-3.5-turbo", "GPT-3.5 Turbo (Default - Fast)"),
        ModelInfo("gpt-3.5-turbo-16k", "GPT-3.5 Turbo 16K (Fast)"),
        ModelInfo("gpt-3.5-turbo-instruct", "GPT-3.5 Turbo Instruct (Fastest)"),
        ModelInfo("gpt-4o-mini", "GPT-4o Mini (Balanced)"),
        ModelInfo("gpt-4o", "GPT-4o (Capable but Slower)"),
        ModelInfo("gpt-4-turbo", "GPT-4 Turbo (Slowest)")
    )
    
    companion object {
        const val PREF_SELECTED_MODEL = "selectedModel"
        const val DEFAULT_MODEL = "gpt-3.5-turbo"
    }
    
    /**
     * Get the list of recommended models for the app
     */
    fun getRecommendedModels(): List<ModelInfo> {
        return availableModels
    }
    
    /**
     * Get the currently selected model ID
     */
    fun getSelectedModel(): String {
        return sharedPreferences.getString(PREF_SELECTED_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
    }
    
    /**
     * Save the selected model ID
     */
    fun saveSelectedModel(modelId: String) {
        sharedPreferences.edit().putString(PREF_SELECTED_MODEL, modelId).apply()
    }
    
    /**
     * Get the display name for a model ID
     */
    fun getModelDisplayName(modelId: String): String {
        return availableModels.find { it.id == modelId }?.displayName ?: modelId
    }
    
    /**
     * Fetch available models from OpenAI API
     * Note: This is a more advanced feature that requires network calls
     * and is not implemented in the basic version
     */
    suspend fun fetchAvailableModels(): Result<List<ModelInfo>> = withContext(Dispatchers.IO) {
        try {
            // Get the API key (user or developer)
            val userKey = sharedPreferences.getString(SettingsActivity.PREF_API_KEY, "") ?: ""
            val apiKey = if (userKey.isBlank() && ApiConfig.hasDevKey()) {
                ApiConfig.DEVELOPER_API_KEY
            } else {
                userKey
            }
            
            if (apiKey.isBlank()) {
                return@withContext Result.failure(IOException("API key not set"))
            }
            
            // Use a safer connection approach with proper timeouts
            val url = URL("https://api.openai.com/v1/models")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000 // 15 seconds
                readTimeout = 15000 // 15 seconds
                
                // Set up the connection
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $apiKey")
            }
            
            try {
                // Get the response
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    Log.d(TAG, "Models Response: $response")
                    
                    try {
                        // Parse the response
                        val jsonResponse = JSONObject(response)
                        if (!jsonResponse.has("data")) {
                            return@withContext Result.failure(IOException("Invalid API response format - missing data array"))
                        }
                        
                        val data = jsonResponse.getJSONArray("data")
                        
                        // Filter for chat models
                        val models = mutableListOf<ModelInfo>()
                        for (i in 0 until data.length()) {
                            try {
                                val modelObj = data.getJSONObject(i)
                                val id = modelObj.getString("id")
                                
                                // Only include chat models
                                if (id.startsWith("gpt-") && !id.contains("vision") && 
                                    (id.contains("turbo") || id.startsWith("gpt-4"))) {
                                    models.add(ModelInfo(id, id))
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Error parsing a model entry, skipping", e)
                                // Skip this item and continue processing others
                            }
                        }
                        
                        if (models.isEmpty()) {
                            // If no models were found, fallback to our default list
                            Log.w(TAG, "No compatible models found in API response, using fallback list")
                            return@withContext Result.success(availableModels)
                        }
                        
                        return@withContext Result.success(models)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing API response", e)
                        return@withContext Result.failure(IOException("Error parsing API response: ${e.message}"))
                    }
                } else {
                    val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                    Log.e(TAG, "API Error: $errorResponse")
                    return@withContext Result.failure(IOException("API Error: $responseCode - $errorResponse"))
                }
            } finally {
                // Ensure connection is always disconnected
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching models", e)
            return@withContext Result.failure(e)
        }
    }
    
    /**
     * Data class to represent an OpenAI model
     */
    data class ModelInfo(
        val id: String,
        val displayName: String
    )
} 