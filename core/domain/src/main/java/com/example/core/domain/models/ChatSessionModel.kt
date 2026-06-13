package com.example.core.domain.models

data class ChatSessionModel(
    val id: String,
    val title: String,
    val providerId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isCompressed: Boolean = false,
    val totalTokensUsed: Int = 0
)
