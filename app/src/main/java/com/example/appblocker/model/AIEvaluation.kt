package com.example.appblocker.model

/**
 * Represents the result of an AI evaluation of a user's request
 * 
 * @property approved Whether the request was approved
 * @property message A message explaining the decision
 * @property timeAllowed The number of minutes the user is allowed to use the app (0 if not approved)
 */
data class AIEvaluation(
    val approved: Boolean,
    val message: String,
    val timeAllowed: Int = 0
) 