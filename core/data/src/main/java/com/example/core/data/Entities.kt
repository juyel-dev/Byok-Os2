package com.example.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import java.util.UUID

@Entity(
    tableName = "chat_sessions",
    indices = [Index(value = ["updatedAt"], name = "idx_session_updated_at")]
)
data class ChatSession(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val providerId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isCompressed: Boolean = false,
    val totalTokensUsed: Int = 0
)

@Entity(
    tableName = "chat_messages",
    indices = [Index(value = ["sessionId"], name = "idx_message_session")]
)
data class ChatMessage(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val role: String, // "user", "assistant", "tool", "system"
    val content: String,
    val providerId: String? = null,
    val isToolCall: Boolean = false,
    val toolName: String? = null,
    val toolArgumentsJson: String? = null,
    val toolResult: String? = null,
    val tokenCount: Int? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "llm_providers")
data class LlmProvider(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val displayName: String,
    val baseUrl: String,
    val encryptedApiKey: String,
    val modelName: String,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "mcp_servers")
data class McpServer(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val endpoint: String,
    val transport: String, // "HTTP" or "SSE"
    val headersJson: String? = null,
    val authToken: String? = null,
    val timeoutSeconds: Int = 30,
    val retryCount: Int = 3,
    val isEnabled: Boolean = true,
    val cachedToolsJson: String? = null
)

@Entity(tableName = "memories")
data class Memory(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val content: String,
    val type: String = "fact", // "fact", "preference", "context"
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)

@Entity(tableName = "app_settings")
data class AppSetting(
    @PrimaryKey val key: String,
    val value: String
)
