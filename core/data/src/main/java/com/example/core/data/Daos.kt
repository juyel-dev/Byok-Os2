package com.example.core.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatSessionDao {
    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
    fun getAllSessionsFlow(): Flow<List<ChatSession>>

    @Query("SELECT * FROM chat_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: String): ChatSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSession)

    @Update
    suspend fun updateSession(session: ChatSession)

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun deleteSessionById(id: String)
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSessionFlow(sessionId: String): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesForSession(sessionId: String): List<ChatMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage)

    @Update
    suspend fun updateMessage(message: ChatMessage)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: String)

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun deleteMessageById(id: String)
}

@Dao
interface LlmProviderDao {
    @Query("SELECT * FROM llm_providers ORDER BY createdAt DESC")
    fun getAllProvidersFlow(): Flow<List<LlmProvider>>

    @Query("SELECT * FROM llm_providers ORDER BY createdAt DESC")
    suspend fun getAllProviders(): List<LlmProvider>

    @Query("SELECT * FROM llm_providers WHERE id = :id LIMIT 1")
    suspend fun getProviderById(id: String): LlmProvider?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProvider(provider: LlmProvider)

    @Update
    suspend fun updateProvider(provider: LlmProvider)

    @Query("DELETE FROM llm_providers WHERE id = :id")
    suspend fun deleteProviderById(id: String)
}

@Dao
interface McpServerDao {
    @Query("SELECT * FROM mcp_servers ORDER BY id DESC")
    fun getAllServersFlow(): Flow<List<McpServer>>

    @Query("SELECT * FROM mcp_servers ORDER BY id DESC")
    suspend fun getAllServers(): List<McpServer>

    @Query("SELECT * FROM mcp_servers WHERE id = :id LIMIT 1")
    suspend fun getServerById(id: String): McpServer?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: McpServer)

    @Update
    suspend fun updateServer(server: McpServer)

    @Query("DELETE FROM mcp_servers WHERE id = :id")
    suspend fun deleteServerById(id: String)
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY createdAt DESC")
    fun getAllMemoriesFlow(): Flow<List<Memory>>

    @Query("SELECT * FROM memories WHERE isActive = 1 ORDER BY createdAt DESC")
    suspend fun getActiveMemories(): List<Memory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: Memory)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteMemoryById(id: String)

    @Query("DELETE FROM memories")
    suspend fun clearAllMemories()
}

@Dao
interface AppSettingDao {
    @Query("SELECT value FROM app_settings WHERE `key` = :key LIMIT 1")
    suspend fun getValue(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetting(setting: AppSetting)
}
