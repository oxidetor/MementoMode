package com.example.appblocker.ai

import com.example.appblocker.AIStrictness
import com.example.appblocker.model.AIEvaluation

/**
 * Interface for AI services that can evaluate user access requests
 */
interface AIService {
    /**
     * Evaluates a user request for temporary app access
     * 
     * @param blockApp The app that the user is requesting access to
     * @param request The user's reason for requesting access
     * @param currentUsageMinutes The user's current screen time usage today in minutes
     * @param customPrompt Optional custom instructions to guide the AI evaluation
     * @return An AIEvaluation containing the decision, feedback, and time allowed (if approved)
     */
    suspend fun evaluateRequest(
        blockApp: String, 
        request: String, 
        currentUsageMinutes: Int = 0,
        customPrompt: String = ""
    ): AIEvaluation
    
    /**
     * Generates a personalized greeting message when the user tries to access a blocked app
     * 
     * @param blockApp The app that the user is requesting access to
     * @param currentUsageMinutes The user's current screen time usage today in minutes
     * @param customPrompt Optional custom instructions to guide the AI behavior
     * @return A greeting message with instructions
     */
    suspend fun generateGreeting(
        blockApp: String, 
        currentUsageMinutes: Int = 0,
        customPrompt: String = ""
    ): String
    
    /**
     * Sets the strictness level for evaluations
     */
    fun setStrictness(level: AIStrictness)
    
    /**
     * Gets the current strictness level
     */
    fun getStrictness(): AIStrictness
} 