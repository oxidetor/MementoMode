package com.example.appblocker.ai

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import com.example.appblocker.AIStrictness
import com.example.appblocker.AppBlockerService
import com.example.appblocker.SettingsActivity
import com.example.appblocker.config.ApiConfig
import com.example.appblocker.model.AIEvaluation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.DecimalFormat

/**
 * Real implementation of the OpenAI service that makes API calls to OpenAI's servers
 * using their Chat Completions API
 */
class RealOpenAIService(context: Context) : AIService {
    private val TAG = "RealOpenAIService"
    private val context: Context = context
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        AppBlockerService.PREFS_NAME, Context.MODE_PRIVATE
    )
    private val appPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    
    // Store conversation history
    private val conversationHistory = mutableListOf<Message>()
    
    private val apiKey: String
        get() {
            val userKey = sharedPreferences.getString(SettingsActivity.PREF_API_KEY, "") ?: ""
            return if (userKey.isBlank() && ApiConfig.hasDevKey()) {
                Log.d(TAG, "Using developer API key instead of user key")
                ApiConfig.DEVELOPER_API_KEY
            } else {
                userKey
            }
        }
    
    private var strictness = AIStrictness.MODERATE
    
    init {
        // Load saved strictness level from preferences
        val savedStrictness = sharedPreferences.getInt(SettingsActivity.PREF_AI_STRICTNESS, 2)
        strictness = when (savedStrictness) {
            1 -> AIStrictness.LENIENT
            3 -> AIStrictness.STRICT
            else -> AIStrictness.MODERATE
        }
    }
    
    override fun setStrictness(level: AIStrictness) {
        strictness = level
        // Save strictness level to preferences
        val strictnessValue = when (level) {
            AIStrictness.LENIENT -> 1
            AIStrictness.STRICT -> 3
            AIStrictness.MODERATE -> 2
        }
        sharedPreferences.edit().putInt(SettingsActivity.PREF_AI_STRICTNESS, strictnessValue).apply()
    }
    
    override fun getStrictness(): AIStrictness {
        return strictness
    }

    /**
     * Retrieves the selected coach persona information from preferences
     */
    private fun getCoachPersona(): CoachPersona {
        val personaId = appPreferences.getString("coach_persona", "nova") ?: "nova"
        val userName = appPreferences.getString("user_name", "") ?: ""
        
        return when (personaId) {
            "sergeant" -> CoachPersona(
                id = "sergeant",
                name = "Sergeant Focus",
                style = "direct, firm, and no-nonsense",
                tone = "authoritative and straight-to-the-point",
                userName = userName
            )
            "wisdom" -> CoachPersona(
                id = "wisdom",
                name = "Professor Wisdom",
                style = "thoughtful, reflective, and philosophical",
                tone = "wise and thought-provoking",
                userName = userName
            )
            "spark" -> CoachPersona(
                id = "spark",
                name = "Spark",
                style = "energetic, motivational, and enthusiastic",
                tone = "upbeat and encouraging",
                userName = userName
            )
            "zen" -> CoachPersona(
                id = "zen",
                name = "Zen",
                style = "calm, mindful, and balanced",
                tone = "peaceful and centering",
                userName = userName
            )
            else -> CoachPersona(
                id = "nova",
                name = "Nova",
                style = "modern, friendly, and supportive",
                tone = "conversational and relatable",
                userName = userName
            )
        }
    }
    
    /**
     * Retrieves the user's screen time goal from preferences
     */
    private fun getUserScreenTimeGoal(): String {
        val goalMinutes = appPreferences.getInt("goal_minutes", 180)
        val goalHours = goalMinutes / 60.0
        val decimalFormat = DecimalFormat("0.00")
        return decimalFormat.format(goalHours)
    }
    
    /**
     * Retrieves the user's screen time goal in minutes
     */
    private fun getUserScreenTimeGoalMinutes(): Int {
        return appPreferences.getInt("goal_minutes", 180)
    }
    
    /**
     * Format current usage as a percentage of daily goal
     */
    private fun formatUsagePercentage(currentUsageMinutes: Int): String {
        val goalMinutes = getUserScreenTimeGoalMinutes()
        val percentage = (currentUsageMinutes.toDouble() / goalMinutes.toDouble()) * 100
        return String.format("%.1f%%", percentage)
    }
    
    /**
     * Format current usage time in hours
     */
    private fun formatCurrentUsageHours(currentUsageMinutes: Int): String {
        val usageHours = currentUsageMinutes / 60.0
        val decimalFormat = DecimalFormat("0.00")
        return decimalFormat.format(usageHours)
    }
    
    /**
     * Calculate remaining screen time in minutes
     */
    private fun calculateRemainingScreenTime(currentUsageMinutes: Int): Int {
        val goalMinutes = getUserScreenTimeGoalMinutes()
        val remaining = goalMinutes - currentUsageMinutes
        return if (remaining < 0) 0 else remaining
    }
    
    /**
     * Format remaining screen time in hours
     */
    private fun formatRemainingScreenTimeHours(currentUsageMinutes: Int): String {
        val remainingMinutes = calculateRemainingScreenTime(currentUsageMinutes)
        val remainingHours = remainingMinutes / 60.0
        val decimalFormat = DecimalFormat("0.00")
        return decimalFormat.format(remainingHours)
    }
    
    /**
     * Retrieves the user's selected habits from preferences
     */
    private fun getUserHabits(): List<String> {
        val habitsSet = appPreferences.getStringSet("selected_habits", emptySet()) ?: emptySet()
        return habitsSet.toList()
    }

    /**
     * Format total app usage as a percentage of daily goal
     */
    private fun formatTotalUsagePercentage(currentUsageMinutes: Int): String {
        val goalMinutes = getUserScreenTimeGoalMinutes()
        // Get total usage from shared preferences
        val totalUsageMinutes = sharedPreferences.getInt("total_usage_minutes", currentUsageMinutes)
        val percentage = (totalUsageMinutes.toDouble() / goalMinutes.toDouble()) * 100
        return String.format("%.1f%%", percentage)
    }
    
    /**
     * Format total app usage time in hours
     */
    private fun formatTotalUsageHours(currentUsageMinutes: Int): String {
        // Get total usage from shared preferences
        val totalUsageMinutes = sharedPreferences.getInt("total_usage_minutes", currentUsageMinutes)
        val usageHours = totalUsageMinutes / 60.0
        val decimalFormat = DecimalFormat("0.00")
        return decimalFormat.format(usageHours)
    }

    /**
     * Evaluates a user request using OpenAI's Chat Completions API
     */
    override suspend fun evaluateRequest(
        blockApp: String,
        request: String,
        currentUsageMinutes: Int,
        customPrompt: String
    ): AIEvaluation {
        // If API key is not set, return default rejection
        if (apiKey.isBlank()) {
            Log.e(TAG, "API key not set")
            return AIEvaluation(false, "API key not set. Please configure a valid API key in Settings.", 0)
        }
        
        return try {
            // Create system prompt
            val systemPrompt = createPrompt(blockApp, currentUsageMinutes, customPrompt)
            
            // If this is a new conversation, add the system message
            if (conversationHistory.isEmpty()) {
                conversationHistory.add(Message("system", systemPrompt))
            }
            
            // Add the user message to conversation history
            conversationHistory.add(Message("user", request))
            
            // Make the API call
            val response = callChatCompletionsAPI()
            Log.d(TAG, "Chat completion response: $response")
            
            // Parse the response
            parseResponse(response)
        } catch (e: Exception) {
            Log.e(TAG, "Error in chat completion", e)
            
            // Provide more specific error messages based on the exception
            val errorMessage = when {
                e.message?.contains("timeout") == true ->
                    "The request timed out. As a fallback, access has been granted."
                e.message?.contains("network") == true || e.message?.contains("connect") == true ->
                    "Network error. Please check your internet connection."
                e.message?.contains("API key") == true ->
                    "Invalid API key. Please check your API key in Settings."
                else -> "Error connecting to AI service: ${e.message}"
            }
            
            // For timeout errors, grant access as a fallback with default 15 minutes
            return if (e.message?.contains("timeout") == true) {
                AIEvaluation(true, errorMessage, 15)
            } else {
                AIEvaluation(false, errorMessage, 0)
            }
        }
    }
    
    /**
     * Generates a personalized greeting message when the user tries to access a blocked app
     */
    override suspend fun generateGreeting(
        blockApp: String, 
        currentUsageMinutes: Int,
        customPrompt: String
    ): String {
        // If API key is not set, return default greeting
        if (apiKey.isBlank()) {
            Log.e(TAG, "API key not set for greeting generation")
            return "How can I help you with using this app today?"
        }
        
        return try {
            // Save the original conversation history
            val savedConversationHistory = conversationHistory.toList()
            
            // Create a temporary conversation history just for the greeting
            conversationHistory.clear()
            
            // Create a greeting-specific prompt
            val greetingPrompt = createGreetingPrompt(blockApp, currentUsageMinutes, customPrompt)
            conversationHistory.add(Message("system", greetingPrompt))
            
            // Make the API call with the temporary conversation history
            val response = callChatCompletionsAPI()
            Log.d(TAG, "Greeting generation response: $response")
            
            // Restore the original conversation history
            conversationHistory.clear()
            conversationHistory.addAll(savedConversationHistory)
            
            // Return the generated greeting
            response
        } catch (e: Exception) {
            Log.e(TAG, "Error generating greeting", e)
            // Return default greeting on error
            "How can I help you with using this app today?"
        }
    }
    
    /**
     * Creates a prompt specifically for generating a greeting
     */
    private fun createGreetingPrompt(blockApp: String, currentUsageMinutes: Int, customPrompt: String): String {
        // Get coach persona
        val coachPersona = getCoachPersona()
        
        // Get user's screen time goal and habits
        val screenTimeGoal = getUserScreenTimeGoal()
        val habits = getUserHabits()
        
        // Format current blocked app usage data
        val currentUsageHours = formatCurrentUsageHours(currentUsageMinutes)
        val usagePercentage = formatUsagePercentage(currentUsageMinutes)
        val remainingHours = formatRemainingScreenTimeHours(currentUsageMinutes)
        
        // Format total usage data
        val totalUsageHours = formatTotalUsageHours(currentUsageMinutes)
        val totalUsagePercentage = formatTotalUsagePercentage(currentUsageMinutes)
        
        // Format habits as a bulleted list if any exist
        val habitsSection = if (habits.isNotEmpty()) {
            """
            The user has selected these habits/activities they want to focus on instead of excessive phone usage:
            ${habits.joinToString("\n") { "- $it" }}
            """
        } else {
            "The user hasn't specified any particular habits they want to develop yet."
        }
        
        // Format custom instructions with more prominence
        val userCustomInstructions = if (customPrompt.isNotBlank()) {
            """
            IMPORTANT - USER'S CUSTOM INSTRUCTIONS:
            $customPrompt
            
            These custom instructions should take precedence over the general guidelines below, 
            while still maintaining the spirit of helping the user manage their app usage.
            """
        } else {
            ""
        }
        
        // Get the strictness level as context
        val strictnessContext = when (strictness) {
            AIStrictness.LENIENT -> "You are operating at a LENIENT strictness level."
            AIStrictness.MODERATE -> "You are operating at a MODERATE strictness level."
            AIStrictness.STRICT -> "You are operating at a STRICT strictness level."
        }
        
        // Create personalized greeting based on coach persona
        val personalizedGreeting = if (coachPersona.userName.isNotEmpty()) {
            "The user's name is ${coachPersona.userName}. Use their name occasionally to make interactions more personal."
        } else {
            "The user hasn't provided their name yet."
        }
        
        // Create usage information
        val usageInfo = """
            CURRENT SCREEN TIME USAGE:
            - Daily goal: $screenTimeGoal hours
            - Usage of blocked apps: $currentUsageHours hours ($usagePercentage of daily goal)
            - Total screen time: $totalUsageHours hours ($totalUsagePercentage of daily goal)
            - Remaining screen time: $remainingHours hours
            
            Reference this usage information when appropriate in your greeting. If they're close to or over their limit,
            gently remind them of this fact.
        """.trimIndent()
        
        return """
            You are ${coachPersona.name}, an AI coach that helps users manage their app usage. Your personality is ${coachPersona.style} and your tone is ${coachPersona.tone}.
            
            $personalizedGreeting
            
            The user is trying to access the app "$blockApp" which they've chosen to limit.
            
            $usageInfo
            
            $habitsSection
            
            $userCustomInstructions
            
            Generate a warm, personalized greeting asking how you can assist them with using this app.
            Be concise (1-2 sentences) but engaging. Your greeting should:
            1. Acknowledge they want to use $blockApp
            2. If relevant, briefly mention their current usage compared to their goal
            3. Ask how you can assist them today
            
            $strictnessContext
            
            COACHING STYLE GUIDANCE:
            - As ${coachPersona.name}, be ${coachPersona.style} in your approach
            - Use a ${coachPersona.tone} tone that reflects your coaching style
            - Stay in character throughout the interaction
            
            Make your greeting conversational, friendly, and tailored specifically to $blockApp.
            
            Reply ONLY with the greeting text. Do not use any markdown, JSON, or formatting.
        """.trimIndent()
    }
    
    /**
     * Calls the OpenAI Chat Completions API
     */
    private suspend fun callChatCompletionsAPI(): String = withContext(Dispatchers.IO) {
        val url = URL("https://api.openai.com/v1/chat/completions")
        val connection = url.openConnection() as HttpURLConnection
        
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.doOutput = true
            connection.connectTimeout = 10000 // 10 seconds timeout
            connection.readTimeout = 10000 // 10 seconds timeout
            
            // Get the selected model
            val modelService = OpenAIModelService(context)
            val model = modelService.getSelectedModel()
            
            // Create the request body
            val requestBody = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    for (message in conversationHistory) {
                        put(JSONObject().apply {
                            put("role", message.role)
                            put("content", message.content)
                        })
                    }
                })
                put("temperature", 0.7)
            }
            
            // Log the request details
            Log.d(TAG, "Sending request to OpenAI Chat Completions API")
            Log.d(TAG, "Using model: $model")
            Log.d(TAG, "Conversation history:")
            for (message in conversationHistory) {
                Log.d(TAG, "- ${message.role}: ${message.content}")
            }
            Log.d(TAG, "Full request body: ${requestBody.toString(2)}")
            
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(requestBody.toString())
                writer.flush()
            }
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                
                // Log the response
                Log.d(TAG, "Received response from OpenAI: $response")
                
                // Parse the response to get the message content
                val jsonResponse = JSONObject(response)
                val choices = jsonResponse.getJSONArray("choices")
                if (choices.length() > 0) {
                    val choice = choices.getJSONObject(0)
                    val message = choice.getJSONObject("message")
                    val content = message.getString("content")
                    
                    // Add the assistant's response to the conversation history
                    conversationHistory.add(Message("assistant", content))
                    
                    // Log the extracted content
                    Log.d(TAG, "Extracted content from response: $content")
                    
                    return@withContext content
                } else {
                    throw Exception("No response choices returned")
                }
            } else {
                val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                Log.e(TAG, "Chat completion error: $errorResponse")
                throw Exception("Failed to get chat completion: $responseCode")
            }
        } finally {
            connection.disconnect()
        }
    }
    
    /**
     * Parses the message response into an AIEvaluation object
     */
    private fun parseResponse(message: String): AIEvaluation {
        try {
            Log.d(TAG, "Parsing message response: $message")
            
            // Check if the message contains a JSON object
            val jsonStart = message.indexOf("{")
            val jsonEnd = message.lastIndexOf("}") + 1
            
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                // This appears to be a JSON response (final decision)
                val jsonStr = message.substring(jsonStart, jsonEnd)
                Log.d(TAG, "Extracted JSON: $jsonStr")
                
                try {
                    val evaluation = JSONObject(jsonStr)
                    
                    // Check if the required fields exist
                    if (!evaluation.has("approved") || !evaluation.has("message")) {
                        Log.e(TAG, "JSON response missing required fields: $jsonStr")
                        // Instead of returning an error, treat this as a follow-up question
                        return AIEvaluation(
                            approved = false,
                            message = message.trim(),
                            timeAllowed = 0
                        )
                    }
                    
                    val approved = evaluation.getBoolean("approved")
                    val responseMessage = evaluation.getString("message")
                    
                    // Extract time allowed if present, default to 15 minutes if approved but no time specified
                    val timeAllowed = if (approved) {
                        if (evaluation.has("timeAllowed")) {
                            try {
                                evaluation.getInt("timeAllowed")
                            } catch (e: Exception) {
                                // If there's any issue parsing the time, default to 15 minutes
                                15
                            }
                        } else {
                            // Default time if not specified
                            15
                        }
                    } else {
                        // If not approved, time allowed is always 0
                        0
                    }
                    
                    Log.d(TAG, "Evaluation result: approved=$approved, message=$responseMessage, timeAllowed=$timeAllowed")
                    return AIEvaluation(approved, responseMessage, timeAllowed)
                } catch (e: Exception) {
                    // If we can't parse the JSON, treat it as a follow-up question
                    Log.e(TAG, "Error parsing JSON: $jsonStr", e)
                    return AIEvaluation(
                        approved = false,
                        message = message.trim(),
                        timeAllowed = 0
                    )
                }
            } else {
                // This appears to be a plain text response without JSON
                // Try to detect if this is actually an approval in natural language
                val cleanMessage = message.replace("`", "").trim()
                
                // Check for approval language patterns in the message
                val isApproval = detectNaturalLanguageApproval(cleanMessage)
                
                if (isApproval) {
                    // This seems like an approval message - extract time if possible
                    val extractedTime = extractTimeAllowed(cleanMessage)
                    
                    Log.d(TAG, "Detected natural language approval with time: $extractedTime minutes")
                    
                    // Add a note that this was auto-detected
                    val enhancedMessage = "$cleanMessage\n\n(Note: I've approved your request based on your explanation.)"
                    
                    return AIEvaluation(
                        approved = true,
                        message = enhancedMessage,
                        timeAllowed = extractedTime
                    )
                }
                
                // Create a special AIEvaluation that indicates this is a follow-up question
                // We use a special format: approved=false (to keep the overlay open) and the question as the message
                return AIEvaluation(
                    approved = false,
                    message = cleanMessage,
                    timeAllowed = 0
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing message response", e)
            // Instead of returning an error message, treat the original message as a follow-up question
            return AIEvaluation(
                approved = false,
                message = message.trim(),
                timeAllowed = 0
            )
        }
    }
    
    /**
     * Detect if a message is approving access without using JSON format
     */
    private fun detectNaturalLanguageApproval(message: String): Boolean {
        val lowerMessage = message.lowercase()
        
        // Common approval phrases
        val approvalPhrases = listOf(
            "i'll grant you", "i will grant you",
            "you can use", "you may use", 
            "i'm granting you", "i am granting you",
            "i'll give you", "i will give you",
            "access granted", "request approved",
            "i'll allow", "i will allow",
            "i'm allowing", "i am allowing",
            "i've approved", "i have approved",
            "you're approved", "you are approved",
            "for the next", "for the following"
        )
        
        // Check for approval phrases
        for (phrase in approvalPhrases) {
            if (lowerMessage.contains(phrase)) {
                return true
            }
        }
        
        // Check for time allocation patterns which indicate approval
        val timePatterns = listOf(
            "\\d+ minute", "\\d+ min",
            "\\d+ hour", "\\d+ hr",
            "a minute", "a few minutes", 
            "an hour", "a few hours"
        )
        
        for (pattern in timePatterns) {
            if (lowerMessage.contains(Regex(pattern))) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * Extract the number of minutes allowed from a message
     */
    private fun extractTimeAllowed(message: String): Int {
        val lowerMessage = message.lowercase()
        
        try {
            // Try to extract minutes
            val minutePatterns = listOf(
                "(\\d+)\\s*minute", "(\\d+)\\s*min",
                "for\\s*(\\d+)"
            )
            
            for (pattern in minutePatterns) {
                val regex = Regex(pattern)
                val matchResult = regex.find(lowerMessage)
                if (matchResult != null) {
                    val minutesStr = matchResult.groupValues[1]
                    val minutes = minutesStr.toIntOrNull() ?: continue
                    if (minutes > 0 && minutes <= 120) { // Sanity check: between 1 and 120 minutes
                        return minutes
                    }
                }
            }
            
            // Try to extract hours and convert to minutes
            val hourPatterns = listOf(
                "(\\d+)\\s*hour", "(\\d+)\\s*hr"
            )
            
            for (pattern in hourPatterns) {
                val regex = Regex(pattern)
                val matchResult = regex.find(lowerMessage)
                if (matchResult != null) {
                    val hoursStr = matchResult.groupValues[1]
                    val hours = hoursStr.toIntOrNull() ?: continue
                    val minutes = hours * 60
                    if (minutes > 0 && minutes <= 240) { // Sanity check: between 1 minute and 4 hours
                        return minutes
                    }
                }
            }
            
            // Check for common phrases and assign default times
            if (lowerMessage.contains("a minute") || lowerMessage.contains("a moment")) {
                return 5 // "a minute" or "a moment" → 5 minutes
            }
            
            if (lowerMessage.contains("a few minutes") || lowerMessage.contains("briefly")) {
                return 10 // "a few minutes" or "briefly" → 10 minutes
            }
            
            if (lowerMessage.contains("an hour") || lowerMessage.contains("1 hour")) {
                return 60 // "an hour" or "1 hour" → 60 minutes
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting time allowed", e)
        }
        
        // Default fallback time if we couldn't extract a specific duration
        return 15
    }
    
    /**
     * Creates the system prompt for the AI based on the app and strictness level
     */
    private fun createPrompt(blockApp: String, currentUsageMinutes: Int, customPrompt: String): String {
        // Get coach persona
        val coachPersona = getCoachPersona()
        
        // Get user's screen time goal and habits
        val screenTimeGoal = getUserScreenTimeGoal()
        val habits = getUserHabits()
        
        // Format current blocked app usage data
        val currentUsageHours = formatCurrentUsageHours(currentUsageMinutes)
        val usagePercentage = formatUsagePercentage(currentUsageMinutes)
        val remainingMinutes = calculateRemainingScreenTime(currentUsageMinutes)
        val remainingHours = formatRemainingScreenTimeHours(currentUsageMinutes)
        
        // Format total usage data
        val totalUsageHours = formatTotalUsageHours(currentUsageMinutes)
        val totalUsagePercentage = formatTotalUsagePercentage(currentUsageMinutes)
        
        // Format habits as a bulleted list if any exist
        val habitsSection = if (habits.isNotEmpty()) {
            """
            IMPORTANT - USER'S HEALTHY HABITS:
            The user has selected these habits/activities they want to focus on instead of excessive phone usage:
            ${habits.joinToString("\n") { "- $it" }}
            
            Reference these habits when suggesting alternatives to app usage or when discussing how the user 
            could better spend their time. Encourage the user to engage in these specific activities instead 
            of spending excessive time on $blockApp.
            """
        } else {
            "The user hasn't specified any particular habits they want to develop yet."
        }
        
        // Format custom instructions with high priority
        val userCustomInstructions = if (customPrompt.isNotBlank()) {
            """
            IMPORTANT - USER'S CUSTOM INSTRUCTIONS:
            $customPrompt
            
            These custom instructions should be treated as HIGH PRIORITY directives that take precedence 
            over general guidelines below. They represent the user's specific preferences for how 
            access requests should be evaluated. Always prioritize these instructions while still 
            maintaining the core purpose of helping manage app usage.
            """
        } else {
            ""
        }
        
        val strictnessPrompt = when (strictness) {
            AIStrictness.LENIENT -> """
                [STRICTNESS LEVEL: LENIENT]
                
                You should be lenient but still require at least a basic substantive reason.
                Don't approve vague requests like "Can I please use it?" or "I need it" without any context.
                The user should provide at least a minimal explanation of why they need access.
                As long as the explanation is coherent, you don't need to ask follow-up questions - just evaluate their initial request.
                The bar for approval is still relatively low, but they must provide some actual reason.
            """.trimIndent()
            
            AIStrictness.MODERATE -> """
                [STRICTNESS LEVEL: MODERATE]
                
                You should set a moderate bar for granting access.
                If the user's reason is vague or general, ask ONE to THREE follow-up question to get more specific details.
                After they respond, make your final decision based on their complete explanation.
                Look for specific, time-bound reasons rather than general desires to use the app.
                If they've already provided specific details, you can make a decision without follow-up questions.
            """.trimIndent()
            
            AIStrictness.STRICT -> """
                [STRICTNESS LEVEL: STRICT]
                
                You should be very strict and push back on the user's request, even if they provide what appears to be a solid reason.
                Ask at least THREE follow-up questions to help the user determine their specific intention.
                Establish clear guard rails around the usage (time limits, specific tasks only).
                Only approve access after this thorough questioning process.
                If this is the first interaction, ALWAYS ask clarifying questions instead of making an immediate decision.
                Look for very specific, time-bound, and necessary reasons for app usage.
            """.trimIndent()
        }
        
        // Determine if this is a follow-up message
        val isFollowUp = conversationHistory.size > 2 // System + at least one user-assistant exchange
        
        // Add context about conversation state
        val conversationContext = if (isFollowUp) {
            """
            This is a follow-up message in an ongoing conversation.
            If you've already asked enough clarifying questions based on the strictness level,
            you should now make a final decision.
            """.trimIndent()
        } else {
            "This is the first message in the conversation."
        }
        
        // Create personalized context based on coach persona and user name
        val personalizedContext = if (coachPersona.userName.isNotEmpty()) {
            "The user's name is ${coachPersona.userName}. Use their name occasionally to make interactions more personal."
        } else {
            "The user hasn't provided their name yet."
        }
        
        // Create usage status section
        val usageStatus = """
            CURRENT SCREEN TIME USAGE:
            - Daily goal: $screenTimeGoal hours
            - Usage of blocked apps: $currentUsageHours hours ($usagePercentage of daily goal)
            - Total screen time: $totalUsageHours hours ($totalUsagePercentage of daily goal)
            - Remaining screen time: $remainingHours hours ($remainingMinutes minutes)
            
            Consider this usage information when evaluating their request. If they're close to or over their limit,
            be more strict about granting access or allocate less time. If they have plenty of remaining screen time,
            you can be more generous with the time allocation if their reason is valid.
        """.trimIndent()
        
        // Response format examples
        val formatExamples = """
            RESPONSE FORMAT EXAMPLES:
            
            1. When APPROVING access, ALWAYS use this exact JSON format:
               {"approved": true, "message": "Your message here explaining the decision", "timeAllowed": 15}
               
            2. When DENYING access, ALWAYS use this exact JSON format:
               {"approved": false, "message": "Your message here explaining why access was denied", "timeAllowed": 0}
               
            3. For follow-up questions, use plain text WITHOUT any JSON:
               "Can you tell me more about why you need to use this app right now?"
            
            DO NOT use expressions like "I'll grant you access" or "You can use the app for X minutes" in plain text.
            ALWAYS put approval decisions in the exact JSON format shown above.
            
            INCORRECT (DO NOT DO THIS):
            "Ok John, I'll grant you access to Instagram for 10 minutes."
            
            CORRECT (DO THIS):
            {"approved": true, "message": "Ok John, I'll grant you access to Instagram for 10 minutes.", "timeAllowed": 10}
        """.trimIndent()
        
        return """
            You are ${coachPersona.name}, an AI coach that helps users manage their app usage time. Your personality is ${coachPersona.style} and your tone is ${coachPersona.tone}.
            
            $personalizedContext
            
            The user is trying to access the app "$blockApp" which they have chosen to limit.
            Your task is to evaluate their request and decide if they should be granted temporary access.
            
            IMPORTANT - USER'S SCREEN TIME GOAL:
            The user has set a daily screen time goal of $screenTimeGoal hours.
            
            $usageStatus
            
            $habitsSection
            
            $userCustomInstructions
            
            $strictnessPrompt
            
            $conversationContext
            
            COACHING STYLE GUIDANCE:
            - As ${coachPersona.name}, be ${coachPersona.style} in your approach to evaluating this request
            - Use a ${coachPersona.tone} tone that reflects your coaching style
            - Stay in character throughout the interaction
            
            TIME ALLOCATION INSTRUCTIONS:
            - If you approve the request, you MUST specify how many minutes the user should be allowed to use the app
            - Consider the nature of their request, their current usage, and their daily goal when determining this time
            - For quick tasks (checking a message, looking something up), 5-10 minutes may be appropriate
            - For activities that require more engagement (watching a video, reading an article), 15-30 minutes may be appropriate
            - For important tasks (work, education), longer periods may be justified if well explained
            - If they're close to their daily limit, be more conservative with time allocation
            - Never allocate more time than their remaining screen time unless absolutely necessary
            
            CRITICAL: RESPONSE FORMAT INSTRUCTIONS
            $formatExamples
            
            IMPORTANT RULES:
            1. NEVER respond in plain text when making a final decision - always use the JSON format
            2. ONLY use plain text for follow-up questions
            3. The app requires the exact JSON format to function correctly
            4. If you approve, you MUST include the timeAllowed value in minutes as a number
            5. If you deny, ALWAYS set timeAllowed to 0
            6. Make sure all JSON field names and values are properly formatted
            
            Your response format is critical for the app to function properly.
        """.trimIndent()
    }
    
    /**
     * Clear the conversation history when the blocking overlay is closed
     */
    fun clearConversationHistory() {
        conversationHistory.clear()
        Log.d(TAG, "Cleared conversation history")
    }
    
    /**
     * Data class to represent a message in the conversation
     */
    private data class Message(
        val role: String,  // "system", "user", or "assistant"
        val content: String
    )
    
    /**
     * Data class to represent coach persona information
     */
    private data class CoachPersona(
        val id: String,
        val name: String,
        val style: String,
        val tone: String,
        val userName: String
    )
} 