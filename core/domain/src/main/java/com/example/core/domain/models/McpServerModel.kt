package com.example.core.domain.models

data class McpServerModel(
    val id: String,
    val name: String,
    val endpoint: String,
    val transport: String,
    val headersJson: String? = null,
    val authToken: String? = null,
    val timeoutSeconds: Int = 30,
    val retryCount: Int = 3,
    val isEnabled: Boolean = true,
    val cachedToolsJson: String? = null
)
