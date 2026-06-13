package com.example.core.data

import com.example.core.domain.models.*
import com.example.core.domain.repository.ChatRepository
import com.example.core.domain.exceptions.BackupCorruptedException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import androidx.room.withTransaction

class ChatRepositoryImpl(
    private val db: AppDatabase,
    private val cryptoManager: CryptoManager
) : ChatRepository {

    private val chatSessionDao = db.chatSessionDao()
    private val chatMessageDao = db.chatMessageDao()
    private val llmProviderDao = db.llmProviderDao()
    private val mcpServerDao = db.mcpServerDao()
    private val memoryDao = db.memoryDao()
    private val appSettingDao = db.appSettingDao()

    // --- Mappers ---
    private fun ChatSession.toDomain(): ChatSessionModel = ChatSessionModel(id, title, providerId, createdAt, updatedAt, isCompressed, totalTokensUsed)
    private fun ChatSessionModel.toEntity(): ChatSession = ChatSession(id, title, providerId, createdAt, updatedAt, isCompressed, totalTokensUsed)

    private fun ChatMessage.toDomain(): Message = Message(id, sessionId, role, content, providerId, isToolCall, toolName, toolArgumentsJson, toolResult, tokenCount, timestamp)
    private fun Message.toEntity(): ChatMessage = ChatMessage(id, sessionId, role, content, providerId, isToolCall, toolName, toolArgumentsJson, toolResult, tokenCount, timestamp)

    private fun LlmProvider.toDomain(): LlmProviderModel = LlmProviderModel(id, displayName, baseUrl, cryptoManager.decrypt(encryptedApiKey), modelName, isActive, createdAt)
    private fun LlmProviderModel.toEntity(): LlmProvider = LlmProvider(id, displayName, baseUrl, cryptoManager.encrypt(encryptedApiKey), modelName, isActive, createdAt)

    private fun McpServer.toDomain(): McpServerModel = McpServerModel(id, name, endpoint, transport, headersJson, authToken, timeoutSeconds, retryCount, isEnabled, cachedToolsJson)
    private fun McpServerModel.toEntity(): McpServer = McpServer(id, name, endpoint, transport, headersJson, authToken, timeoutSeconds, retryCount, isEnabled, cachedToolsJson)

    private fun Memory.toDomain(): MemoryModel = MemoryModel(id, content, type, createdAt, isActive)
    private fun MemoryModel.toEntity(): Memory = Memory(id, content, type, createdAt, isActive)

    // --- Implementations ---

    // Sessions
    override val allSessions: Flow<List<ChatSessionModel>> = chatSessionDao.getAllSessionsFlow().map { list -> list.map { it.toDomain() } }
    
    override suspend fun getSessionById(id: String): ChatSessionModel? = chatSessionDao.getSessionById(id)?.toDomain()
    
    override suspend fun insertSession(session: ChatSessionModel) = chatSessionDao.insertSession(session.toEntity())
    
    override suspend fun updateSession(session: ChatSessionModel) = chatSessionDao.updateSession(session.toEntity())
    
    override suspend fun deleteSessionById(id: String) = chatSessionDao.deleteSessionById(id)

    // Messages
    override fun getMessagesForSessionFlow(sessionId: String): Flow<List<Message>> =
        chatMessageDao.getMessagesForSessionFlow(sessionId).map { list -> list.map { it.toDomain() } }

    override suspend fun getMessagesForSession(sessionId: String): List<Message> =
        chatMessageDao.getMessagesForSession(sessionId).map { it.toDomain() }

    override suspend fun insertMessage(message: Message) = chatMessageDao.insertMessage(message.toEntity())

    override suspend fun updateMessage(message: Message) = chatMessageDao.updateMessage(message.toEntity())

    override suspend fun deleteMessagesForSession(sessionId: String) = chatMessageDao.deleteMessagesForSession(sessionId)

    override suspend fun deleteMessageById(id: String) = chatMessageDao.deleteMessageById(id)

    // Providers
    override val allProvidersFlow: Flow<List<LlmProviderModel>> = llmProviderDao.getAllProvidersFlow().map { list ->
        list.map { it.toDomain() }
    }

    override suspend fun getAllProviders(): List<LlmProviderModel> = llmProviderDao.getAllProviders().map { it.toDomain() }

    override suspend fun getProviderById(id: String): LlmProviderModel? = llmProviderDao.getProviderById(id)?.toDomain()

    override suspend fun insertProvider(provider: LlmProviderModel) {
        val valResult = com.example.core.common.ProviderValidator.validate(provider.baseUrl, provider.encryptedApiKey)
        if (valResult is com.example.core.common.ProviderValidator.ValidationResult.Invalid) {
            throw IllegalArgumentException("Validation failed for provider insertion: ${valResult.reason}")
        }
        llmProviderDao.insertProvider(provider.toEntity())
    }

    override suspend fun updateProvider(provider: LlmProviderModel) {
        val valResult = com.example.core.common.ProviderValidator.validate(provider.baseUrl, provider.encryptedApiKey)
        if (valResult is com.example.core.common.ProviderValidator.ValidationResult.Invalid) {
            throw IllegalArgumentException("Validation failed for provider update: ${valResult.reason}")
        }
        llmProviderDao.updateProvider(provider.toEntity())
    }

    override suspend fun deleteProviderById(id: String) = llmProviderDao.deleteProviderById(id)

    // McpServers
    override val allServersFlow: Flow<List<McpServerModel>> = mcpServerDao.getAllServersFlow().map { list -> list.map { it.toDomain() } }

    override suspend fun getAllServers(): List<McpServerModel> = mcpServerDao.getAllServers().map { it.toDomain() }

    override suspend fun getServerById(id: String): McpServerModel? = mcpServerDao.getServerById(id)?.toDomain()

    override suspend fun insertServer(server: McpServerModel) = mcpServerDao.insertServer(server.toEntity())

    override suspend fun updateServer(server: McpServerModel) = mcpServerDao.updateServer(server.toEntity())

    override suspend fun deleteServerById(id: String) = mcpServerDao.deleteServerById(id)

    // Memories
    override val allMemories: Flow<List<MemoryModel>> = memoryDao.getAllMemoriesFlow().map { list -> list.map { it.toDomain() } }

    override suspend fun getActiveMemories(): List<MemoryModel> = memoryDao.getActiveMemories().map { it.toDomain() }

    override suspend fun insertMemory(memory: MemoryModel) = memoryDao.insertMemory(memory.toEntity())

    override suspend fun deleteMemoryById(id: String) = memoryDao.deleteMemoryById(id)

    override suspend fun clearAllMemories() = memoryDao.clearAllMemories()

    // Settings helpers
    override suspend fun getSetting(key: String, default: String): String {
        return appSettingDao.getValue(key) ?: default
    }

    override suspend fun saveSetting(key: String, value: String) {
        appSettingDao.insertSetting(AppSetting(key, value))
    }

    override suspend fun exportDatabaseBackup(): String {
        return withContext(Dispatchers.IO) {
            val rootJson = JSONObject()

            // 1. Providers List (Direct entity serialization - keeps keys encrypted as "encryptedApiKey")
            val providersList = llmProviderDao.getAllProviders()
            val providersArray = JSONArray()
            for (p in providersList) {
                providersArray.put(JSONObject().apply {
                    put("id", p.id)
                    put("displayName", p.displayName)
                    put("baseUrl", p.baseUrl)
                    put("apiKey", p.encryptedApiKey) // Ciphertext only! Secure!
                    put("modelName", p.modelName)
                    put("isActive", p.isActive)
                    put("createdAt", p.createdAt)
                })
            }
            rootJson.put("llm_providers", providersArray)

            // 2. MCP Servers
            val mcpList = mcpServerDao.getAllServers()
            val mcpArray = JSONArray()
            for (s in mcpList) {
                mcpArray.put(JSONObject().apply {
                    put("id", s.id)
                    put("name", s.name)
                    put("endpoint", s.endpoint)
                    put("transport", s.transport)
                    put("headersJson", s.headersJson)
                    put("authToken", s.authToken)
                    put("timeoutSeconds", s.timeoutSeconds)
                    put("retryCount", s.retryCount)
                    put("isEnabled", s.isEnabled)
                    put("cachedToolsJson", s.cachedToolsJson)
                })
            }
            rootJson.put("mcp_servers", mcpArray)

            // 3. Memories
            val memoriesList = memoryDao.getActiveMemories()
            val memoriesArray = JSONArray()
            for (m in memoriesList) {
                memoriesArray.put(JSONObject().apply {
                    put("id", m.id)
                    put("content", m.content)
                    put("type", m.type)
                    put("createdAt", m.createdAt)
                    put("isActive", m.isActive)
                })
            }
            rootJson.put("memories", memoriesArray)

            // 4. Chat Sessions
            val chatSessions = chatSessionDao.getAllSessionsFlow().first()
            val sessionsArray = JSONArray()
            for (cs in chatSessions) {
                sessionsArray.put(JSONObject().apply {
                    put("id", cs.id)
                    put("title", cs.title)
                    put("providerId", cs.providerId)
                    put("createdAt", cs.createdAt)
                    put("updatedAt", cs.updatedAt)
                    put("isCompressed", cs.isCompressed)
                    put("totalTokensUsed", cs.totalTokensUsed)
                })
            }
            rootJson.put("chat_sessions", sessionsArray)

            rootJson.toString(2)
        }
    }

    override suspend fun importDatabaseBackup(jsonStr: String) {
        withContext(Dispatchers.IO) {
            val rootJson = try {
                JSONObject(jsonStr)
            } catch (e: Exception) {
                throw BackupCorruptedException("Database backup payload is not a valid JSON structure.", e)
            }

            // Strictly get each required table
            val providersArray = try {
                rootJson.getJSONArray("llm_providers")
            } catch (e: Exception) {
                throw BackupCorruptedException("Required table 'llm_providers' is missing or malformed.", e)
            }

            val mcpArray = try {
                rootJson.getJSONArray("mcp_servers")
            } catch (e: Exception) {
                throw BackupCorruptedException("Required table 'mcp_servers' is missing or malformed.", e)
            }

            val memoriesArray = try {
                rootJson.getJSONArray("memories")
            } catch (e: Exception) {
                throw BackupCorruptedException("Required table 'memories' is missing or malformed.", e)
            }

            val sessionsArray = try {
                rootJson.getJSONArray("chat_sessions")
            } catch (e: Exception) {
                throw BackupCorruptedException("Required table 'chat_sessions' is missing or malformed.", e)
            }

            // Pre-parse the entire payload strictly before starting transaction
            val providersToInsert = mutableListOf<LlmProvider>()
            val mcpToInsert = mutableListOf<McpServer>()
            val memoriesToInsert = mutableListOf<Memory>()
            val sessionsToInsert = mutableListOf<ChatSession>()

            try {
                for (i in 0 until providersArray.length()) {
                    val obj = providersArray.getJSONObject(i)
                    val id = obj.getString("id")
                    val displayName = obj.getString("displayName")
                    val baseUrl = obj.getString("baseUrl")
                    val apiKey = obj.getString("apiKey")
                    val modelName = obj.getString("modelName")
                    val isActive = obj.getBoolean("isActive")
                    val createdAt = obj.getLong("createdAt")

                    val encryptedKey = if (apiKey.startsWith("v1:")) {
                        apiKey
                    } else {
                        cryptoManager.encrypt(apiKey)
                    }

                    val decryptedKey = cryptoManager.decrypt(encryptedKey)
                    val finalDecryptedKey = if (decryptedKey.isEmpty()) {
                        com.example.core.common.ProviderValidator.KEY_SENTINEL
                    } else {
                        decryptedKey
                    }

                    val finalEncryptedKey = if (decryptedKey.isEmpty()) {
                        cryptoManager.encrypt(com.example.core.common.ProviderValidator.KEY_SENTINEL)
                    } else {
                        encryptedKey
                    }

                    val valResult = com.example.core.common.ProviderValidator.validate(baseUrl, finalDecryptedKey)
                    if (valResult is com.example.core.common.ProviderValidator.ValidationResult.Invalid) {
                        throw IllegalArgumentException("Validation failed for imported provider '$displayName': ${valResult.reason}")
                    }

                    providersToInsert.add(
                        LlmProvider(
                            id = id,
                            displayName = displayName,
                            baseUrl = baseUrl,
                            encryptedApiKey = finalEncryptedKey,
                            modelName = modelName,
                            isActive = isActive,
                            createdAt = createdAt
                        )
                    )
                }

                for (i in 0 until mcpArray.length()) {
                    val obj = mcpArray.getJSONObject(i)
                    val id = obj.getString("id")
                    val name = obj.getString("name")
                    val endpoint = obj.getString("endpoint")
                    val transport = obj.getString("transport")
                    val headersJson = if (obj.isNull("headersJson")) null else obj.getString("headersJson")
                    val authToken = if (obj.isNull("authToken")) null else obj.getString("authToken")
                    val timeoutSeconds = obj.getInt("timeoutSeconds")
                    val retryCount = obj.getInt("retryCount")
                    val isEnabled = obj.getBoolean("isEnabled")
                    val cachedToolsJson = if (obj.isNull("cachedToolsJson")) null else obj.getString("cachedToolsJson")

                    mcpToInsert.add(
                        McpServer(
                            id = id,
                            name = name,
                            endpoint = endpoint,
                            transport = transport,
                            headersJson = headersJson,
                            authToken = authToken,
                            timeoutSeconds = timeoutSeconds,
                            retryCount = retryCount,
                            isEnabled = isEnabled,
                            cachedToolsJson = cachedToolsJson
                        )
                    )
                }

                for (i in 0 until memoriesArray.length()) {
                    val obj = memoriesArray.getJSONObject(i)
                    val id = obj.getString("id")
                    val content = obj.getString("content")
                    val type = obj.getString("type")
                    val createdAt = obj.getLong("createdAt")
                    val isActive = obj.getBoolean("isActive")

                    memoriesToInsert.add(
                        Memory(
                            id = id,
                            content = content,
                            type = type,
                            createdAt = createdAt,
                            isActive = isActive
                        )
                    )
                }

                for (i in 0 until sessionsArray.length()) {
                    val obj = sessionsArray.getJSONObject(i)
                    val id = obj.getString("id")
                    val title = obj.getString("title")
                    val providerId = obj.getString("providerId")
                    val createdAt = obj.getLong("createdAt")
                    val updatedAt = obj.getLong("updatedAt")
                    val isCompressed = obj.getBoolean("isCompressed")
                    val totalTokensUsed = obj.getInt("totalTokensUsed")

                    sessionsToInsert.add(
                        ChatSession(
                            id = id,
                            title = title,
                            providerId = providerId,
                            createdAt = createdAt,
                            updatedAt = updatedAt,
                            isCompressed = isCompressed,
                            totalTokensUsed = totalTokensUsed
                        )
                    )
                }
            } catch (e: Exception) {
                throw BackupCorruptedException("Backup data fields are incomplete or malformed: ${e.localizedMessage}", e)
            }

            // Run in transaction so either everything inserts or it raises exception and rolls back!
            db.withTransaction {
                for (provider in providersToInsert) {
                    llmProviderDao.insertProvider(provider)
                }
                for (mcp in mcpToInsert) {
                    mcpServerDao.insertServer(mcp)
                }
                for (memory in memoriesToInsert) {
                    memoryDao.insertMemory(memory)
                }
                for (session in sessionsToInsert) {
                    chatSessionDao.insertSession(session)
                }
            }
        }
    }
}
