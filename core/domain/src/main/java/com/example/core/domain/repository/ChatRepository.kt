package com.example.core.domain.repository

import com.example.core.domain.models.*
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    // Sessions
    val allSessions: Flow<List<ChatSessionModel>>
    suspend fun getSessionById(id: String): ChatSessionModel?
    suspend fun insertSession(session: ChatSessionModel)
    suspend fun updateSession(session: ChatSessionModel)
    suspend fun deleteSessionById(id: String)

    // Messages
    fun getMessagesForSessionFlow(sessionId: String): Flow<List<Message>>
    suspend fun getMessagesForSession(sessionId: String): List<Message>
    suspend fun insertMessage(message: Message)
    suspend fun updateMessage(message: Message)
    suspend fun deleteMessagesForSession(sessionId: String)
    suspend fun deleteMessageById(id: String)

    // Providers
    val allProvidersFlow: Flow<List<LlmProviderModel>>
    suspend fun getAllProviders(): List<LlmProviderModel>
    suspend fun getProviderById(id: String): LlmProviderModel?
    suspend fun insertProvider(provider: LlmProviderModel)
    suspend fun updateProvider(provider: LlmProviderModel)
    suspend fun deleteProviderById(id: String)

    // McpServers
    val allServersFlow: Flow<List<McpServerModel>>
    suspend fun getAllServers(): List<McpServerModel>
    suspend fun getServerById(id: String): McpServerModel?
    suspend fun insertServer(server: McpServerModel)
    suspend fun updateServer(server: McpServerModel)
    suspend fun deleteServerById(id: String)

    // Memories
    val allMemories: Flow<List<MemoryModel>>
    suspend fun getActiveMemories(): List<MemoryModel>
    suspend fun insertMemory(memory: MemoryModel)
    suspend fun deleteMemoryById(id: String)
    suspend fun clearAllMemories()

    // Settings helpers
    suspend fun getSetting(key: String, default: String): String
    suspend fun saveSetting(key: String, value: String)

    // Backup & Restore
    suspend fun exportDatabaseBackup(): String
    suspend fun importDatabaseBackup(jsonStr: String)
}
