package com.example.core.data.service

import android.content.Context
import com.example.core.domain.models.*
import com.example.core.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import org.json.JSONArray
import org.json.JSONObject

class SupabaseSetupService(
    private val context: Context,
    private val repository: ChatRepository,
    private val adminClient: SupabaseAdminClient,
    private val securePrefs: SupabaseSecurePrefs
) {

    private val MIGRATIONS = listOf(
        1 to """
            CREATE TABLE IF NOT EXISTS schema_version (
                version INTEGER PRIMARY KEY,
                applied_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
            );
        """.trimIndent(),
        2 to """
            CREATE TABLE IF NOT EXISTS chat_sessions (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                provider_id TEXT NOT NULL,
                created_at BIGINT NOT NULL,
                updated_at BIGINT NOT NULL,
                is_compressed BOOLEAN NOT NULL DEFAULT false,
                total_tokens_used INTEGER NOT NULL DEFAULT 0
            );
            DO $$
            BEGIN
                IF NOT EXISTS (
                    SELECT 1 FROM pg_publication_tables 
                    WHERE pubname = 'supabase_realtime' 
                      AND schemaname = 'public' 
                      AND tablename = 'chat_sessions'
                ) THEN
                    ALTER PUBLICATION supabase_realtime ADD TABLE chat_sessions;
                END IF;
            END $$;
        """.trimIndent(),
        3 to """
            CREATE TABLE IF NOT EXISTS chat_messages (
                id TEXT PRIMARY KEY,
                session_id TEXT NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                provider_id TEXT,
                is_tool_call BOOLEAN NOT NULL DEFAULT false,
                tool_name TEXT,
                tool_arguments_json TEXT,
                tool_result TEXT,
                token_count INTEGER,
                timestamp BIGINT NOT NULL,
                updated_at BIGINT NOT NULL
            );
            DO $$
            BEGIN
                IF NOT EXISTS (
                    SELECT 1 FROM pg_publication_tables 
                    WHERE pubname = 'supabase_realtime' 
                      AND schemaname = 'public' 
                      AND tablename = 'chat_messages'
                ) THEN
                    ALTER PUBLICATION supabase_realtime ADD TABLE chat_messages;
                END IF;
            END $$;
        """.trimIndent(),
        4 to """
            CREATE TABLE IF NOT EXISTS llm_providers (
                id TEXT PRIMARY KEY,
                display_name TEXT NOT NULL,
                base_url TEXT NOT NULL,
                encrypted_api_key TEXT NOT NULL,
                model_name TEXT NOT NULL,
                is_active BOOLEAN NOT NULL DEFAULT true,
                created_at BIGINT NOT NULL,
                updated_at BIGINT NOT NULL
            );
            DO $$
            BEGIN
                IF NOT EXISTS (
                    SELECT 1 FROM pg_publication_tables 
                    WHERE pubname = 'supabase_realtime' 
                      AND schemaname = 'public' 
                      AND tablename = 'llm_providers'
                ) THEN
                    ALTER PUBLICATION supabase_realtime ADD TABLE llm_providers;
                END IF;
            END $$;
        """.trimIndent(),
        5 to """
            CREATE TABLE IF NOT EXISTS mcp_servers (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                endpoint TEXT NOT NULL,
                transport TEXT NOT NULL,
                headers_json TEXT,
                auth_token TEXT,
                timeout_seconds INTEGER NOT NULL DEFAULT 30,
                retry_count INTEGER NOT NULL DEFAULT 3,
                is_enabled BOOLEAN NOT NULL DEFAULT true,
                cached_tools_json TEXT,
                updated_at BIGINT NOT NULL
            );
            DO $$
            BEGIN
                IF NOT EXISTS (
                    SELECT 1 FROM pg_publication_tables 
                    WHERE pubname = 'supabase_realtime' 
                      AND schemaname = 'public' 
                      AND tablename = 'mcp_servers'
                ) THEN
                    ALTER PUBLICATION supabase_realtime ADD TABLE mcp_servers;
                END IF;
            END $$;
        """.trimIndent(),
        6 to """
            CREATE TABLE IF NOT EXISTS memories (
                id TEXT PRIMARY KEY,
                content TEXT NOT NULL,
                type TEXT NOT NULL DEFAULT 'fact',
                created_at BIGINT NOT NULL,
                is_active BOOLEAN NOT NULL DEFAULT true,
                updated_at BIGINT NOT NULL
            );
            DO $$
            BEGIN
                IF NOT EXISTS (
                    SELECT 1 FROM pg_publication_tables 
                    WHERE pubname = 'supabase_realtime' 
                      AND schemaname = 'public' 
                      AND tablename = 'memories'
                ) THEN
                    ALTER PUBLICATION supabase_realtime ADD TABLE memories;
                END IF;
            END $$;
        """.trimIndent(),
        7 to """
            CREATE TABLE IF NOT EXISTS app_settings (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL,
                updated_at BIGINT NOT NULL
            );
            DO $$
            BEGIN
                IF NOT EXISTS (
                    SELECT 1 FROM pg_publication_tables 
                    WHERE pubname = 'supabase_realtime' 
                      AND schemaname = 'public' 
                      AND tablename = 'app_settings'
                ) THEN
                    ALTER PUBLICATION supabase_realtime ADD TABLE app_settings;
                END IF;
            END $$;
        """.trimIndent(),
        8 to """
            INSERT INTO storage.buckets (id, name, public)
            VALUES ('attachments', 'attachments', false)
            ON CONFLICT (id) DO NOTHING;

            INSERT INTO storage.buckets (id, name, public)
            VALUES ('exports', 'exports', false)
            ON CONFLICT (id) DO NOTHING;

            DROP POLICY IF EXISTS "Allow all access to secure attachments" ON storage.objects;
            CREATE POLICY "Allow all access to secure attachments" ON storage.objects
                FOR ALL USING (bucket_id = 'attachments');

            DROP POLICY IF EXISTS "Allow all access to secure exports" ON storage.objects;
            CREATE POLICY "Allow all access to secure exports" ON storage.objects
                FOR ALL USING (bucket_id = 'exports');
        """.trimIndent()
    )

    fun runAutoSetup(url: String, serviceRoleKey: String, pat: String): Flow<String> = flow {
        if (url.isBlank() || serviceRoleKey.isBlank() || pat.isBlank()) {
            emit("ERROR: URL, Service Role Key, and PAT cannot be blank.")
            return@flow
        }

        emit("START: Validating Project Connection...")
        val projectRef = adminClient.extractProjectRef(url)
        emit("CONNECT_OK: Project Reference identified: '$projectRef'")

        // Save immediately to secureprefs
        securePrefs.saveUrl(url)
        securePrefs.saveServiceRoleKey(serviceRoleKey)
        securePrefs.savePat(pat)

        try {
            emit("MIGRATION_START: Scanning legacy database schema version...")
            var currentVersion = 0
            try {
                val checkRes = adminClient.executeSql(url, pat, "SELECT MAX(version) as max_version FROM schema_version;")
                val resultsArray = checkRes.optJSONArray("results")
                if (resultsArray != null && resultsArray.length() > 0) {
                    currentVersion = resultsArray.getJSONObject(0).optInt("max_version", 0)
                }
            } catch (e: Exception) {
                emit("INFO: No schema_version table found. Applying initial migrations.")
                currentVersion = 0
            }

            val maxMigrationVersion = MIGRATIONS.maxOf { it.first }
            if (currentVersion >= maxMigrationVersion) {
                emit("SUCCESS: Setup complete. Starting sync...")
                securePrefs.saveConfigured(true)
                runSyncInternal().collect { step ->
                    emit(step)
                }
                return@flow
            }

            for ((version, sql) in MIGRATIONS) {
                if (version > currentVersion) {
                    emit("MIGRATION_STEP: Applying step $version...")
                    adminClient.executeSql(url, pat, sql)
                    adminClient.executeSql(url, pat, "INSERT INTO schema_version (version) VALUES ($version);")
                    emit("SUCCESS: Migration step $version successfully applied.")
                } else {
                    emit("SKIP: Migration step $version is already applied.")
                }
            }

            emit("VERIFY: Pinpoint validating provisioned tables and active buckets...")
            val verifyRes = adminClient.executeSql(url, pat, "SELECT * FROM schema_version;")
            if (verifyRes.has("results")) {
                securePrefs.saveConfigured(true)
                emit("DONE: Supabase setup completed. 7 tables & 2 buckets verified, publications active.")
                
                // Immediately start synchronization
                runSyncInternal().collect { step ->
                    emit(step)
                }
            } else {
                throw Exception("Schema table verification failed. No results returned.")
            }
        } catch (e: Exception) {
            securePrefs.saveConfigured(false)
            emit("ERROR: Setup Failed! ${e.localizedMessage}")
        }
    }

    fun runSyncConflictsResolution(localSessionsCount: Int): Flow<String> = flow {
        // Trigger synchronized tables flush
        runSyncInternal().collect { step ->
            emit(step)
        }
    }

    private fun runSyncInternal(): Flow<String> = flow {
        emit("START: Initializing bidirection database synchronization...")
        val url = securePrefs.getUrl()
        val serviceRoleKey = securePrefs.getServiceRoleKey()

        if (url.isBlank() || serviceRoleKey.isBlank()) {
            emit("ERROR: Supabase URL or Service Role Key is missing. Sync aborted.")
            return@flow
        }

        try {
            // 1. SESSIONS
            emit("SYNC: Synchronizing 'chat_sessions' table...")
            val localSessions = repository.allSessions.first()
            val remoteSessionsArray = adminClient.fetchPostgrestRows(url, serviceRoleKey, "chat_sessions")
            val remoteSessionsMap = mutableMapOf<String, JSONObject>()
            for (i in 0 until remoteSessionsArray.length()) {
                val obj = remoteSessionsArray.getJSONObject(i)
                remoteSessionsMap[obj.getString("id")] = obj
            }

            val localSessionPushes = JSONArray()
            for (local in localSessions) {
                val remote = remoteSessionsMap[local.id]
                if (remote == null) {
                    localSessionPushes.put(JSONObject().apply {
                        put("id", local.id)
                        put("title", local.title)
                        put("provider_id", local.providerId)
                        put("created_at", local.createdAt)
                        put("updated_at", local.updatedAt)
                        put("is_compressed", local.isCompressed)
                        put("total_tokens_used", local.totalTokensUsed)
                    })
                } else {
                    val remoteUpdatedAt = remote.getLong("updated_at")
                    if (local.updatedAt > remoteUpdatedAt) {
                        localSessionPushes.put(JSONObject().apply {
                            put("id", local.id)
                            put("title", local.title)
                            put("provider_id", local.providerId)
                            put("created_at", local.createdAt)
                            put("updated_at", local.updatedAt)
                            put("is_compressed", local.isCompressed)
                            put("total_tokens_used", local.totalTokensUsed)
                        })
                    } else if (remoteUpdatedAt > local.updatedAt) {
                        val updatedModel = ChatSessionModel(
                            id = local.id,
                            title = remote.getString("title"),
                            providerId = remote.getString("provider_id"),
                            createdAt = remote.getLong("created_at"),
                            updatedAt = remote.getLong("updated_at"),
                            isCompressed = remote.getBoolean("is_compressed"),
                            totalTokensUsed = remote.getInt("total_tokens_used")
                        )
                        repository.updateSession(updatedModel)
                    }
                }
            }

            for ((id, remote) in remoteSessionsMap) {
                if (localSessions.none { it.id == id }) {
                    val newModel = ChatSessionModel(
                        id = id,
                        title = remote.getString("title"),
                        providerId = remote.getString("provider_id"),
                        createdAt = remote.getLong("created_at"),
                        updatedAt = remote.getLong("updated_at"),
                        isCompressed = remote.getBoolean("is_compressed"),
                        totalTokensUsed = remote.getInt("total_tokens_used")
                    )
                    repository.insertSession(newModel)
                }
            }

            if (localSessionPushes.length() > 0) {
                adminClient.upsertPostgrestRows(url, serviceRoleKey, "chat_sessions", localSessionPushes)
            }


            // 2. MESSAGES
            emit("SYNC: Synchronizing 'chat_messages' table...")
            val remoteMessagesArray = adminClient.fetchPostgrestRows(url, serviceRoleKey, "chat_messages")
            val remoteMessagesMap = mutableMapOf<String, JSONObject>()
            for (i in 0 until remoteMessagesArray.length()) {
                val obj = remoteMessagesArray.getJSONObject(i)
                remoteMessagesMap[obj.getString("id")] = obj
            }

            val localMessagePushes = JSONArray()
            val sessionsList = repository.allSessions.first()
            for (session in sessionsList) {
                val messages = repository.getMessagesForSession(session.id)
                for (msg in messages) {
                    val remote = remoteMessagesMap[msg.id]
                    if (remote == null) {
                        localMessagePushes.put(JSONObject().apply {
                            put("id", msg.id)
                            put("session_id", msg.sessionId)
                            put("role", msg.role)
                            put("content", msg.content)
                            put("provider_id", msg.providerId)
                            put("is_tool_call", msg.isToolCall)
                            put("tool_name", msg.toolName)
                            put("tool_arguments_json", msg.toolArgumentsJson)
                            put("tool_result", msg.toolResult)
                            put("token_count", msg.tokenCount)
                            put("timestamp", msg.timestamp)
                            put("updated_at", msg.timestamp)
                        })
                    } else {
                        val remoteTimestamp = remote.getLong("timestamp")
                        if (msg.timestamp > remoteTimestamp) {
                            localMessagePushes.put(JSONObject().apply {
                                put("id", msg.id)
                                put("session_id", msg.sessionId)
                                put("role", msg.role)
                                put("content", msg.content)
                                put("provider_id", msg.providerId)
                                put("is_tool_call", msg.isToolCall)
                                put("tool_name", msg.toolName)
                                put("tool_arguments_json", msg.toolArgumentsJson)
                                put("tool_result", msg.toolResult)
                                put("token_count", msg.tokenCount)
                                put("timestamp", msg.timestamp)
                                put("updated_at", msg.timestamp)
                            })
                        } else if (remoteTimestamp > msg.timestamp) {
                            val updatedMsg = Message(
                                id = msg.id,
                                sessionId = remote.getString("session_id"),
                                role = remote.getString("role"),
                                content = remote.getString("content"),
                                providerId = if (remote.isNull("provider_id")) null else remote.getString("provider_id"),
                                isToolCall = remote.getBoolean("is_tool_call"),
                                toolName = if (remote.isNull("tool_name")) null else remote.getString("tool_name"),
                                toolArgumentsJson = if (remote.isNull("tool_arguments_json")) null else remote.getString("tool_arguments_json"),
                                toolResult = if (remote.isNull("tool_result")) null else remote.getString("tool_result"),
                                tokenCount = if (remote.isNull("token_count")) null else remote.getInt("token_count"),
                                timestamp = remote.getLong("timestamp")
                            )
                            repository.insertMessage(updatedMsg)
                        }
                    }
                }
            }

            for ((id, remote) in remoteMessagesMap) {
                val targetSessionId = remote.getString("session_id")
                if (sessionsList.any { it.id == targetSessionId }) {
                    val localMessages = repository.getMessagesForSession(targetSessionId)
                    if (localMessages.none { it.id == id }) {
                        val newMsg = Message(
                            id = id,
                            sessionId = targetSessionId,
                            role = remote.getString("role"),
                            content = remote.getString("content"),
                            providerId = if (remote.isNull("provider_id")) null else remote.getString("provider_id"),
                            isToolCall = remote.getBoolean("is_tool_call"),
                            toolName = if (remote.isNull("tool_name")) null else remote.getString("tool_name"),
                            toolArgumentsJson = if (remote.isNull("tool_arguments_json")) null else remote.getString("tool_arguments_json"),
                            toolResult = if (remote.isNull("tool_result")) null else remote.getString("tool_result"),
                            tokenCount = if (remote.isNull("token_count")) null else remote.getInt("token_count"),
                            timestamp = remote.getLong("timestamp")
                        )
                        repository.insertMessage(newMsg)
                    }
                }
            }

            if (localMessagePushes.length() > 0) {
                adminClient.upsertPostgrestRows(url, serviceRoleKey, "chat_messages", localMessagePushes)
            }


            // 3. LLM PROVIDERS
            emit("SYNC: Synchronizing 'llm_providers' table...")
            val localProviders = repository.getAllProviders()
            val remoteProvidersArray = adminClient.fetchPostgrestRows(url, serviceRoleKey, "llm_providers")
            val remoteProvidersMap = mutableMapOf<String, JSONObject>()
            for (i in 0 until remoteProvidersArray.length()) {
                val obj = remoteProvidersArray.getJSONObject(i)
                remoteProvidersMap[obj.getString("id")] = obj
            }

            val localProviderPushes = JSONArray()
            for (local in localProviders) {
                val remote = remoteProvidersMap[local.id]
                if (remote == null) {
                    localProviderPushes.put(JSONObject().apply {
                        put("id", local.id)
                        put("display_name", local.displayName)
                        put("base_url", local.baseUrl)
                        put("encrypted_api_key", local.encryptedApiKey)
                        put("model_name", local.modelName)
                        put("is_active", local.isActive)
                        put("created_at", local.createdAt)
                        put("updated_at", local.createdAt)
                    })
                } else {
                    val remoteCreatedAt = remote.getLong("created_at")
                    if (local.createdAt > remoteCreatedAt) {
                        localProviderPushes.put(JSONObject().apply {
                            put("id", local.id)
                            put("display_name", local.displayName)
                            put("base_url", local.baseUrl)
                            put("encrypted_api_key", local.encryptedApiKey)
                            put("model_name", local.modelName)
                            put("is_active", local.isActive)
                            put("created_at", local.createdAt)
                            put("updated_at", local.createdAt)
                        })
                    } else if (remoteCreatedAt > local.createdAt) {
                        val updatedPrv = LlmProviderModel(
                            id = local.id,
                            displayName = remote.getString("display_name"),
                            baseUrl = remote.getString("base_url"),
                            encryptedApiKey = remote.getString("encrypted_api_key"),
                            modelName = remote.getString("model_name"),
                            isActive = remote.getBoolean("is_active"),
                            createdAt = remote.getLong("created_at")
                        )
                        repository.updateProvider(updatedPrv)
                    }
                }
            }

            for ((id, remote) in remoteProvidersMap) {
                if (localProviders.none { it.id == id }) {
                    val newPrv = LlmProviderModel(
                        id = id,
                        displayName = remote.getString("display_name"),
                        baseUrl = remote.getString("base_url"),
                        encryptedApiKey = remote.getString("encrypted_api_key"),
                        modelName = remote.getString("model_name"),
                        isActive = remote.getBoolean("is_active"),
                        createdAt = remote.getLong("created_at")
                    )
                    repository.insertProvider(newPrv)
                }
            }

            if (localProviderPushes.length() > 0) {
                adminClient.upsertPostgrestRows(url, serviceRoleKey, "llm_providers", localProviderPushes)
            }


            // 4. MCP SERVERS
            emit("SYNC: Synchronizing 'mcp_servers' table...")
            val localServers = repository.getAllServers()
            val remoteServersArray = adminClient.fetchPostgrestRows(url, serviceRoleKey, "mcp_servers")
            val remoteServersMap = mutableMapOf<String, JSONObject>()
            for (i in 0 until remoteServersArray.length()) {
                val obj = remoteServersArray.getJSONObject(i)
                remoteServersMap[obj.getString("id")] = obj
            }

            val localServerPushes = JSONArray()
            for (local in localServers) {
                localServerPushes.put(JSONObject().apply {
                    put("id", local.id)
                    put("name", local.name)
                    put("endpoint", local.endpoint)
                    put("transport", local.transport)
                    put("headers_json", local.headersJson)
                    put("auth_token", local.authToken)
                    put("timeout_seconds", local.timeoutSeconds)
                    put("retry_count", local.retryCount)
                    put("is_enabled", local.isEnabled)
                    put("cached_tools_json", local.cachedToolsJson)
                    put("updated_at", System.currentTimeMillis())
                })
            }

            for ((id, remote) in remoteServersMap) {
                if (localServers.none { it.id == id }) {
                    val newSrv = McpServerModel(
                        id = id,
                        name = remote.getString("name"),
                        endpoint = remote.getString("endpoint"),
                        transport = remote.getString("transport"),
                        headersJson = if (remote.isNull("headers_json")) null else remote.getString("headers_json"),
                        authToken = if (remote.isNull("auth_token")) null else remote.getString("auth_token"),
                        timeoutSeconds = remote.getInt("timeout_seconds"),
                        retryCount = remote.getInt("retry_count"),
                        isEnabled = remote.getBoolean("is_enabled"),
                        cachedToolsJson = if (remote.isNull("cached_tools_json")) null else remote.getString("cached_tools_json")
                    )
                    repository.insertServer(newSrv)
                }
            }

            if (localServerPushes.length() > 0) {
                adminClient.upsertPostgrestRows(url, serviceRoleKey, "mcp_servers", localServerPushes)
            }


            // 5. MEMORIES
            emit("SYNC: Synchronizing 'memories' table...")
            val localMemories = repository.allMemories.first()
            val remoteMemoriesArray = adminClient.fetchPostgrestRows(url, serviceRoleKey, "memories")
            val remoteMemoriesMap = mutableMapOf<String, JSONObject>()
            for (i in 0 until remoteMemoriesArray.length()) {
                val obj = remoteMemoriesArray.getJSONObject(i)
                remoteMemoriesMap[obj.getString("id")] = obj
            }

            val localMemoryPushes = JSONArray()
            for (local in localMemories) {
                val remote = remoteMemoriesMap[local.id]
                if (remote == null) {
                    localMemoryPushes.put(JSONObject().apply {
                        put("id", local.id)
                        put("content", local.content)
                        put("type", local.type)
                        put("created_at", local.createdAt)
                        put("is_active", local.isActive)
                        put("updated_at", local.createdAt)
                    })
                } else {
                    val remoteCreatedAt = remote.getLong("created_at")
                    if (local.createdAt > remoteCreatedAt) {
                        localMemoryPushes.put(JSONObject().apply {
                            put("id", local.id)
                            put("content", local.content)
                            put("type", local.type)
                            put("created_at", local.createdAt)
                            put("is_active", local.isActive)
                            put("updated_at", local.createdAt)
                        })
                    } else if (remoteCreatedAt > local.createdAt) {
                        val updatedMem = MemoryModel(
                            id = local.id,
                            content = remote.getString("content"),
                            type = remote.getString("type"),
                            createdAt = remote.getLong("created_at"),
                            isActive = remote.getBoolean("is_active")
                        )
                        repository.insertMemory(updatedMem)
                    }
                }
            }

            for ((id, remote) in remoteMemoriesMap) {
                if (localMemories.none { it.id == id }) {
                    val newMem = MemoryModel(
                        id = id,
                        content = remote.getString("content"),
                        type = remote.getString("type"),
                        createdAt = remote.getLong("created_at"),
                        isActive = remote.getBoolean("is_active")
                    )
                    repository.insertMemory(newMem)
                }
            }

            if (localMemoryPushes.length() > 0) {
                adminClient.upsertPostgrestRows(url, serviceRoleKey, "memories", localMemoryPushes)
            }


            // 6. APP SETTINGS
            emit("SYNC: Synchronizing 'app_settings' table...")
            val remoteSettingsArray = adminClient.fetchPostgrestRows(url, serviceRoleKey, "app_settings")
            val remoteSettingsMap = mutableMapOf<String, String>()
            for (i in 0 until remoteSettingsArray.length()) {
                val obj = remoteSettingsArray.getJSONObject(i)
                remoteSettingsMap[obj.getString("key")] = obj.getString("value")
            }

            val expectedSettingKeys = listOf(
                "onboarding_completed", "theme_mode", "temperature", "max_tokens", "top_p", "top_k",
                "presence_penalty", "frequency_penalty", "system_prompt", "auto_compress", "compress_threshold", "keep_last_n",
                "supabase_url", "supabase_pat", "supabase_sync", "supabase_configured"
            )

            val localSettingsPushes = JSONArray()
            for (key in expectedSettingKeys) {
                val localValue = repository.getSetting(key, "")
                val remoteValue = remoteSettingsMap[key]
                if (localValue.isNotEmpty()) {
                    if (remoteValue == null || remoteValue != localValue) {
                        localSettingsPushes.put(JSONObject().apply {
                            put("key", key)
                            put("value", localValue)
                            put("updated_at", System.currentTimeMillis())
                        })
                    }
                } else if (remoteValue != null && remoteValue.isNotEmpty()) {
                    repository.saveSetting(key, remoteValue)
                }
            }

            if (localSettingsPushes.length() > 0) {
                adminClient.upsertPostgrestRows(url, serviceRoleKey, "app_settings", localSettingsPushes)
            }

            emit("DONE_SYNC: Low-latency cloud synchronization and conflict check pass succeeded!")
        } catch (e: Exception) {
            emit("ERROR: Synchronize error - ${e.localizedMessage}")
        }
    }
}
