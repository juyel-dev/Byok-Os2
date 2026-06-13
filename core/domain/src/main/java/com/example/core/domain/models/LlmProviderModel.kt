package com.example.core.domain.models

data class LlmProviderModel(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val encryptedApiKey: String,
    val modelName: String,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
