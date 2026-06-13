package com.example.core.domain.models

data class Message(
    val id: String,
    val sessionId: String,
    val role: String,
    val content: String,
    val providerId: String? = null,
    val isToolCall: Boolean = false,
    val toolName: String? = null,
    val toolArgumentsJson: String? = null,
    val toolResult: String? = null,
    val tokenCount: Int? = null,
    val timestamp: Long = System.currentTimeMillis()
)
