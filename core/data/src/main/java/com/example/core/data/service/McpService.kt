package com.example.core.data.service

import com.example.core.domain.models.McpServerModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

// Health status for servers
enum class McpHealthStatus {
    HEALTHY,
    DEGRADED,
    UNREACHABLE
}

// Exception hierarchy for typed Result error propagation
sealed class McpError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class ToolDiscoveryError(val serverName: String, val reason: String, cause: Throwable? = null) : 
        McpError(
            JSONObject().apply {
                put("error_type", "ToolDiscoveryError")
                put("server", serverName)
                put("reason", reason)
                put("message", "[$serverName] Tool discovery failed: $reason")
                cause?.message?.let { put("cause", it) }
            }.toString(),
            cause
        )

    class ToolExecutionError(val serverName: String, val toolName: String, val reason: String, cause: Throwable? = null) : 
        McpError(
            JSONObject().apply {
                put("error_type", "ToolExecutionError")
                put("server", serverName)
                put("tool", toolName)
                put("reason", reason)
                put("message", "[$serverName::$toolName] Tool execution failed: $reason")
                cause?.message?.let { put("cause", it) }
            }.toString(),
            cause
        )
}

// Thread-safe Circuit Breaker implementation
class CircuitBreaker(
    val failureThreshold: Int = 3,
    val resetTimeoutMs: Long = 30000L // 30 seconds
) {
    enum class State { CLOSED, OPEN, HALF_OPEN }

    private var state = State.CLOSED
    private var failureCount = 0
    private var lastFailureTime = 0L

    @Synchronized
    fun canExecute(): Boolean {
        val now = System.currentTimeMillis()
        if (state == State.OPEN) {
            if (now - lastFailureTime >= resetTimeoutMs) {
                state = State.HALF_OPEN
                return true
            }
            return false
        }
        return true
    }

    @Synchronized
    fun recordSuccess() {
        failureCount = 0
        state = State.CLOSED
    }

    @Synchronized
    fun recordFailure() {
        failureCount++
        lastFailureTime = System.currentTimeMillis()
        if (state == State.HALF_OPEN || failureCount >= failureThreshold) {
            state = State.OPEN
        }
    }

    @Synchronized
    fun getState(): State = state

    @Synchronized
    fun getFailureCount(): Int = failureCount
}

data class McpTool(
    val name: String,
    val description: String,
    val inputSchema: String,
    val serverId: String = "",
    val serverName: String = ""
)

data class ToolIdentifier(
    val serverId: String,
    val toolName: String
) {
    fun toNamespacedId(): String = "$serverId::$toolName"

    companion object {
        fun parse(id: String?): ToolIdentifier? {
            if (id == null) return null
            val parts = id.trim().split("::", limit = 2)
            return if (parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) {
                ToolIdentifier(parts[0], parts[1])
            } else {
                null
            }
        }
    }
}

data class RegisteredTool(
    val identifier: ToolIdentifier,
    val tool: McpTool,
    val server: McpServerModel
)

sealed class DispatchResult {
    data class Success(val registeredTool: RegisteredTool) : DispatchResult()
    data class AmbiguityError(val toolName: String, val matchingServers: List<McpServerModel>) : DispatchResult()
    data class NotFound(val toolName: String) : DispatchResult()
    data class InvalidNamespace(val rawName: String) : DispatchResult()
}

object McpRegistryLogger {
    fun d(tag: String, msg: String) = println("DEBUG [$tag] $msg")
    fun i(tag: String, msg: String) = println("INFO [$tag] $msg")
    fun w(tag: String, msg: String) = println("WARN [$tag] $msg")
    fun e(tag: String, msg: String) = println("ERROR [$tag] $msg")
}

class McpRegistry private constructor(
    private val toolMap: Map<ToolIdentifier, RegisteredTool>
) {
    val items: Collection<RegisteredTool> get() = toolMap.values

    fun getTool(identifier: ToolIdentifier): RegisteredTool? = toolMap[identifier]

    fun findToolsByName(toolName: String): List<RegisteredTool> {
        return toolMap.values.filter { it.identifier.toolName == toolName }
    }

    fun dispatch(rawName: String): DispatchResult {
        if (rawName.trim().contains("::")) {
            val parsed = ToolIdentifier.parse(rawName)
            if (parsed != null) {
                val registered = getTool(parsed)
                return if (registered != null) {
                    DispatchResult.Success(registered)
                } else {
                    McpRegistryLogger.e("McpRegistry", "Registry Dispatch Error: Namespaced tool '$rawName' not found or stale reference.")
                    DispatchResult.NotFound(parsed.toolName)
                }
            } else {
                McpRegistryLogger.e("McpRegistry", "Registry Dispatch Error: Namespaced tool '$rawName' is malformed.")
                return DispatchResult.InvalidNamespace(rawName)
            }
        }

        val matching = findToolsByName(rawName)
        return when {
            matching.isEmpty() -> {
                McpRegistryLogger.e("McpRegistry", "Registry Dispatch Error: Bare tool '$rawName' not found.")
                DispatchResult.NotFound(rawName)
            }
            matching.size == 1 -> {
                McpRegistryLogger.i("McpRegistry", "Single-server compatibility: Resolved bare tool '$rawName' to server '${matching.first().server.id}' because there is no ambiguity.")
                DispatchResult.Success(matching.first())
            }
            else -> {
                val matchingServers = matching.map { it.server }
                McpRegistryLogger.w("McpRegistry", "Registry Collision Ambiguity: Multiple servers offer tool '$rawName': ${matchingServers.map { it.id }}")
                DispatchResult.AmbiguityError(rawName, matchingServers)
            }
        }
    }

    fun dumpSnapshot(): String {
        return buildString {
            append("=== MCP Registry Snapshot Dump ===\n")
            append("Total registered tools: ${toolMap.size}\n")
            toolMap.forEach { (ident, registered) ->
                append("- Namespace: ${ident.toNamespacedId()} | Tool: ${registered.tool.name} | Server: ${registered.server.name} (ID: ${registered.server.id})\n")
            }
            append("==================================")
        }
    }

    class Builder {
        private val tempMap = mutableMapOf<ToolIdentifier, RegisteredTool>()

        fun register(server: McpServerModel, tool: McpTool): Builder {
            val identifier = ToolIdentifier(server.id, tool.name)
            // Prevent overwrites and concurrent mutation
            synchronized(this) {
                if (tempMap.containsKey(identifier)) {
                    McpRegistryLogger.w("McpRegistry", "Collision ignored during registration: Tool ${tool.name} on server ${server.id} already registered.")
                }
                val finalizedTool = tool.copy(serverId = server.id, serverName = server.name)
                tempMap[identifier] = RegisteredTool(identifier, finalizedTool, server)
                McpRegistryLogger.d("McpRegistry", "Registered tool: ${identifier.toNamespacedId()}")
            }
            return this
        }

        fun build(): McpRegistry {
            return McpRegistry(tempMap.toMap()) // Makes it immutable
        }
    }
}

data class McpLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val serverName: String,
    val toolName: String,
    val arguments: String,
    val durationMs: Long,
    val status: String, // "SUCCESS", "RETRYING", "FAILED", "SANDBOX_BLOCKED"
    val error: String? = null,
    val outputPreview: String
)

class McpService(private val client: OkHttpClient) : KoinComponent {
    private val supabaseSetupService: SupabaseSetupService by inject()

    // Thread-safe live log execution history
    private val _executionLogs = MutableStateFlow<List<McpLogEntry>>(emptyList())
    val executionLogs = _executionLogs.asStateFlow()

    // Per-server health tracking
    private val _serverHealths = MutableStateFlow<Map<String, McpHealthStatus>>(emptyMap())
    val serverHealths = _serverHealths.asStateFlow()

    // Thread-safe map of circuit breakers
    private val circuitBreakers = ConcurrentHashMap<String, CircuitBreaker>()

    // Simulated virtual filesystem (only for development/sandbox mode)
    private val virtualFiles = ConcurrentHashMap<String, String>().apply {
        put("main.dart", "void main() {\n  print('Hello BYOK OS!');\n}")
        put("notes.txt", "1. Learn Jetpack Compose.\n2. Configure MCP servers.\n3. Integrate local models.")
        put("workspace_config.json", "{\n  \"theme\": \"amoled\",\n  \"sync_frequency_minutes\": 15,\n  \"local_db_cache\": \"enabled\"\n}")
    }

    private fun addLog(log: McpLogEntry) {
        val current = _executionLogs.value.toMutableList()
        current.add(0, log) // Add on top
        if (current.size > 100) { // Bound history in-memory to prevent leaks
            current.removeAt(current.size - 1)
        }
        _executionLogs.value = current
    }

    // Helper check if this is a production environment
    private fun isProductionEnvironment(): Boolean {
        val buildEnv = System.getenv("BUILD_ENV") ?: System.getenv("ENV") ?: ""
        return buildEnv.lowercase() == "prod" || buildEnv.lowercase() == "production"
    }

    private fun isMockServer(server: McpServerModel): Boolean {
        val endpoint = server.endpoint.lowercase().trim()
        val name = server.name.lowercase().trim()
        return endpoint.startsWith("mock://") || endpoint.isEmpty() || name.contains("mock") || name.contains("fallback")
    }

    private fun getCircuitBreaker(serverId: String, retryCount: Int): CircuitBreaker {
        return circuitBreakers.computeIfAbsent(serverId) {
            CircuitBreaker(failureThreshold = if (retryCount > 0) retryCount else 3)
        }
    }

    private fun updateHealthStatus(serverId: String, cb: CircuitBreaker) {
        val currentHealths = _serverHealths.value.toMutableMap()
        val nextHealth = when (cb.getState()) {
            CircuitBreaker.State.OPEN -> McpHealthStatus.UNREACHABLE
            CircuitBreaker.State.HALF_OPEN -> McpHealthStatus.DEGRADED
            CircuitBreaker.State.CLOSED -> {
                if (cb.getFailureCount() > 0) McpHealthStatus.DEGRADED else McpHealthStatus.HEALTHY
            }
        }
        currentHealths[serverId] = nextHealth
        _serverHealths.value = currentHealths
    }

    private fun calculateExponentialBackoff(attempt: Int): Long {
        val base = 1000L
        val factor = 2
        val exponent = attempt - 1
        val delay = base * Math.pow(factor.toDouble(), exponent.toDouble()).toLong()
        return minOf(delay, 10000L) // limit max delay to 10 seconds
    }

    // Discover tools available on an MCP Server
    suspend fun listTools(server: McpServerModel): Result<List<McpTool>> = withContext(Dispatchers.IO) {
        if (!server.isEnabled) {
            return@withContext Result.success(emptyList())
        }

        val cb = getCircuitBreaker(server.id, server.retryCount)
        if (!cb.canExecute() && !isMockServer(server)) {
            val err = McpError.ToolDiscoveryError(server.name, "Circuit Breaker is OPEN. Host is UNREACHABLE.")
            updateHealthStatus(server.id, cb)
            return@withContext Result.failure(err)
        }

        // Mock mode environment gate
        if (isMockServer(server)) {
            if (isProductionEnvironment()) {
                val err = McpError.ToolDiscoveryError(server.name, "Mock connections are disabled in production.")
                return@withContext Result.failure(err)
            } else {
                val serverNameLower = server.name.lowercase()
                val mockTools = when {
                    serverNameLower.contains("file") || serverNameLower.contains("disk") -> getFilesystemTools()
                    serverNameLower.contains("github") || serverNameLower.contains("git") -> getGithubTools()
                    serverNameLower.contains("browser") || serverNameLower.contains("web") || serverNameLower.contains("automation") -> getBrowserAutomationTools()
                    serverNameLower.contains("cloud") || serverNameLower.contains("sync") -> getCloudSyncTools()
                    serverNameLower.contains("social") || serverNameLower.contains("notifier") || serverNameLower.contains("alert") -> getSocialNotifierTools()
                    serverNameLower.contains("media") || serverNameLower.contains("voice") || serverNameLower.contains("image") -> getMediaOcrTools()
                    else -> getDefaultLocalTools()
                }
                val mappedMockTools = mockTools.map {
                    it.copy(serverId = server.id, serverName = server.name)
                }
                cb.recordSuccess()
                updateHealthStatus(server.id, cb)
                return@withContext Result.success(mappedMockTools)
            }
        }

        var lastException: Exception? = null
        val limit = if (server.retryCount < 1) 1 else server.retryCount

        for (attempt in 1..limit) {
            try {
                if (attempt > 1) {
                    val backoffMs = calculateExponentialBackoff(attempt)
                    delay(backoffMs)
                }

                val tools = withTimeout(server.timeoutSeconds * 1000L) {
                    fetchToolsFromEndpoint(server)
                }
                cb.recordSuccess()
                updateHealthStatus(server.id, cb)
                return@withContext Result.success(tools)
            } catch (e: Exception) {
                lastException = e
                cb.recordFailure()
                updateHealthStatus(server.id, cb)
            }
        }

        val error = McpError.ToolDiscoveryError(
            serverName = server.name,
            reason = lastException?.message ?: "Unreachable",
            cause = lastException
        )
        Result.failure(error)
    }

    private fun fetchToolsFromEndpoint(server: McpServerModel): List<McpTool> {
        val requestBuilder = Request.Builder()
            .url("${server.endpoint}/tools")
            .get()

        if (!server.headersJson.isNullOrBlank()) {
            val headers = JSONObject(server.headersJson)
            headers.keys().forEach { key ->
                requestBuilder.addHeader(key, headers.getString(key))
            }
        }

        if (!server.authToken.isNullOrBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer ${server.authToken}")
        }

        val request = requestBuilder.build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                val toolsArray = json.optJSONArray("tools") ?: JSONArray()
                val result = mutableListOf<McpTool>()
                for (i in 0 until toolsArray.length()) {
                    val toolObj = toolsArray.getJSONObject(i)
                    result.add(McpTool(
                        name = toolObj.getString("name"),
                        description = toolObj.optString("description", ""),
                        inputSchema = toolObj.optJSONObject("inputSchema")?.toString() ?: "{}",
                        serverId = server.id,
                        serverName = server.name
                    ))
                }
                return result
            } else {
                throw Exception("Server returned HTTP ${response.code}")
            }
        }
    }

    // Call / execute an MCP Tool with robust retry, timeout, parallel queueing, sandboxing
    suspend fun executeTool(
        server: McpServerModel,
        toolName: String,
        arguments: JSONObject,
        sandboxNetworkAllowed: Boolean = true,
        sandboxFilesystemAllowed: Boolean = true,
        sandboxNotificationsAllowed: Boolean = true
    ): Result<String> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        var currentStatus = "SUCCESS"
        var errMessage: String? = null
        var finalResult = ""
        val argumentsStr = arguments.toString()

        // 1. Sandbox Checks
        val isNetworkInquiry = isNetworkRequiredTool(toolName)
        val isFsInquiry = isFsRequiredTool(toolName)
        val isNotifyInquiry = isNotifyRequiredTool(toolName)

        if (isNetworkInquiry && !sandboxNetworkAllowed) {
            currentStatus = "SANDBOX_BLOCKED"
            errMessage = "Security Sandbox Exception: Outbound internet operations are restricted for this tool."
            val log = McpLogEntry(
                serverName = server.name,
                toolName = toolName,
                arguments = argumentsStr,
                durationMs = System.currentTimeMillis() - startTime,
                status = currentStatus,
                error = errMessage,
                outputPreview = "Blocked by security sandboxing permissions."
            )
            addLog(log)
            return@withContext Result.failure(McpError.ToolExecutionError(server.name, toolName, errMessage))
        }

        if (isFsInquiry && !sandboxFilesystemAllowed) {
            currentStatus = "SANDBOX_BLOCKED"
            errMessage = "Security Sandbox Exception: File write/read access is strictly restricted in this container."
            val log = McpLogEntry(
                serverName = server.name,
                toolName = toolName,
                arguments = argumentsStr,
                durationMs = System.currentTimeMillis() - startTime,
                status = currentStatus,
                error = errMessage,
                outputPreview = "Blocked by security sandboxing permissions."
            )
            addLog(log)
            return@withContext Result.failure(McpError.ToolExecutionError(server.name, toolName, errMessage))
        }

        if (isNotifyInquiry && !sandboxNotificationsAllowed) {
            currentStatus = "SANDBOX_BLOCKED"
            errMessage = "Security Sandbox Exception: Local notification overlays are disabled."
            val log = McpLogEntry(
                serverName = server.name,
                toolName = toolName,
                arguments = argumentsStr,
                durationMs = System.currentTimeMillis() - startTime,
                status = currentStatus,
                error = errMessage,
                outputPreview = "Blocked by security sandboxing permissions."
            )
            addLog(log)
            return@withContext Result.failure(McpError.ToolExecutionError(server.name, toolName, errMessage))
        }

        val cb = getCircuitBreaker(server.id, server.retryCount)
        if (!cb.canExecute() && !isMockServer(server)) {
            currentStatus = "FAILED"
            errMessage = "Circuit Breaker is OPEN. Host is UNREACHABLE."
            val log = McpLogEntry(
                serverName = server.name,
                toolName = toolName,
                arguments = argumentsStr,
                durationMs = System.currentTimeMillis() - startTime,
                status = currentStatus,
                error = errMessage,
                outputPreview = "Blocked: Circuit Breaker Open."
            )
            addLog(log)
            updateHealthStatus(server.id, cb)
            return@withContext Result.failure(McpError.ToolExecutionError(server.name, toolName, errMessage))
        }

        // Mock Server simulation gate
        if (isMockServer(server)) {
            if (isProductionEnvironment()) {
                currentStatus = "FAILED"
                errMessage = "Mock executions disabled in production."
                val log = McpLogEntry(
                    serverName = server.name,
                    toolName = toolName,
                    arguments = argumentsStr,
                    durationMs = System.currentTimeMillis() - startTime,
                    status = currentStatus,
                    error = errMessage,
                    outputPreview = "Blocked: Mock Disabled in Production."
                )
                addLog(log)
                return@withContext Result.failure(McpError.ToolExecutionError(server.name, toolName, errMessage))
            } else {
                try {
                    finalResult = executeOfflineSafeModule(toolName, arguments)
                    cb.recordSuccess()
                    updateHealthStatus(server.id, cb)
                } catch (e: Exception) {
                    currentStatus = "FAILED"
                    errMessage = e.message ?: "Mock Execution failed"
                    cb.recordFailure()
                    updateHealthStatus(server.id, cb)
                }
            }
        } else {
            // Real Endpoint Execution with Exponential Backoff
            try {
                finalResult = executeWithRetryAndTimeout(server, toolName, arguments, server.retryCount, server.timeoutSeconds.toLong())
                currentStatus = "SUCCESS"
            } catch (e: Exception) {
                currentStatus = "FAILED"
                errMessage = e.message ?: "Execution failed"
            }
        }

        val duration = System.currentTimeMillis() - startTime
        val log = McpLogEntry(
            serverName = server.name,
            toolName = toolName,
            arguments = argumentsStr,
            durationMs = duration,
            status = currentStatus,
            error = errMessage,
            outputPreview = if (finalResult.length > 250) finalResult.take(250) + "..." else finalResult
        )
        addLog(log)

        if (currentStatus == "SUCCESS") {
            Result.success(finalResult)
        } else {
            Result.failure(McpError.ToolExecutionError(server.name, toolName, errMessage ?: "Execution failed"))
        }
    }

    private suspend fun executeWithRetryAndTimeout(
        server: McpServerModel,
        toolName: String,
        arguments: JSONObject,
        retries: Int,
        timeoutSeconds: Long
    ): String {
        var lastException: Exception? = null
        val limit = if (retries < 1) 1 else retries
        val argumentsStr = arguments.toString()
        val cb = getCircuitBreaker(server.id, retries)

        for (attempt in 1..limit) {
            try {
                if (attempt > 1) {
                    addLog(McpLogEntry(
                        serverName = server.name,
                        toolName = toolName,
                        arguments = argumentsStr,
                        durationMs = 0,
                        status = "RETRYING",
                        outputPreview = "Attempt $attempt of $limit..."
                    ))
                    val backoffMs = calculateExponentialBackoff(attempt)
                    delay(backoffMs)
                }

                val result = withTimeout(timeoutSeconds * 1000L) {
                    executeRealEndpoint(server, toolName, arguments)
                }
                cb.recordSuccess()
                updateHealthStatus(server.id, cb)
                return result
            } catch (e: Exception) {
                lastException = e
                cb.recordFailure()
                updateHealthStatus(server.id, cb)
            }
        }
        throw lastException ?: Exception("Network execution failed")
    }

    private fun executeRealEndpoint(server: McpServerModel, toolName: String, arguments: JSONObject): String {
        val bodyJson = JSONObject().apply {
            put("name", toolName)
            put("arguments", arguments)
        }

        val requestBuilder = Request.Builder()
            .url("${server.endpoint}/tools/call")
            .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))

        if (!server.headersJson.isNullOrBlank()) {
            val headers = JSONObject(server.headersJson)
            headers.keys().forEach { key ->
                requestBuilder.addHeader(key, headers.getString(key))
            }
        }

        if (!server.authToken.isNullOrBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer ${server.authToken}")
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                val result = json.optString("result", "")
                if (result.isNotEmpty()) {
                    return result
                }
                return body
            } else {
                throw Exception("HTTP Error ${response.code}")
            }
        }
    }

    private suspend fun executeOfflineSafeModule(toolName: String, parsedArgs: JSONObject): String {
        return when (toolName) {
            // File system
            "fs_read_file" -> {
                val path = parsedArgs.optString("path", "notes.txt")
                val content = virtualFiles[path]
                if (content != null) {
                    "=== [FILE CONTENT: $path] ===\n$content"
                } else {
                    throw Exception("File '$path' not found. Available virtual system disk files: ${virtualFiles.keys.joinToString(", ")}")
                }
            }
            "fs_write_file" -> {
                val path = parsedArgs.optString("path")
                val content = parsedArgs.optString("content")
                if (path.isEmpty()) throw Exception("Path cannot be empty for fs_write_file")
                virtualFiles[path] = content
                "SUCCESS: Wrote ${content.length} characters into sandboxed file: '$path'."
            }
            "fs_list_files" -> {
                "=== Sandboxed Directory /workspace ===\n" + virtualFiles.entries.joinToString("\n") { (k, v) ->
                    "- /$k (${v.length} bytes)"
                }
            }
            "fs_delete_file" -> {
                val path = parsedArgs.optString("path")
                if (virtualFiles.containsKey(path)) {
                    virtualFiles.remove(path)
                    "SUCCESS: Executed rm '$path' in sandbox root."
                } else {
                    throw Exception("Cannot delete non-existing file '$path'.")
                }
            }

            // GitHub Module
            "github_get_repo" -> {
                val repo = parsedArgs.optString("repo")
                if (repo.isEmpty()) throw Exception("Repository path parameter cannot be empty!")
                "=== GitHub Repository metadata [ $repo ] ===\n" +
                        "Stars: 412 | Forks: 32 | Issues: 4 Open | License: MIT\n" +
                        "Latest Branch: 'main' | Codebase: Kotlin, Jetpack Compose \n" +
                        "Status: Live Sync Enabled"
            }
            "github_create_issue" -> {
                val title = parsedArgs.optString("title")
                val body = parsedArgs.optString("body")
                if (title.isEmpty()) throw Exception("Title cannot be empty")
                "SUCCESS: Created issue #42 on active repo successfully.\nTitle: $title\nURL: https://github.com/byok-os/issues/42"
            }
            "github_push_file" -> {
                val path = parsedArgs.optString("path")
                val commitMsg = parsedArgs.optString("commit_message", "Updated documents")
                if (path.isEmpty()) throw Exception("Path cannot be empty")
                "SUCCESS: Committed write and pushed to remote GitHub index branch 'main'.\nSHA: d3b073c1f13e7534\nCommit: '$commitMsg'"
            }

            // Browser Automation Module
            "browser_navigate" -> {
                val url = parsedArgs.optString("url")
                if (url.isEmpty()) throw Exception("URL cannot be empty")
                "=== [HEADLESS CHROME DOM CAPTURE FOR: $url] ===\n" +
                        "Title: Active webpage portal node\n" +
                        "Content Summary extracted:\n" +
                        "  - Local development environments and peripheral context models rule the tech stacks.\n" +
                        "  - Dynamic orchestration mechanisms reduce tokens footprints by 45%.\n" +
                        "  - Bring Your Own Key design patterns safeguard personal API balance accounts."
            }
            "browser_submit_form" -> {
                val formId = parsedArgs.optString("form_id")
                if (formId.isEmpty()) throw Exception("Form ID cannot be empty")
                "SUCCESS: Filled standard fields on current session DOM frame, clicked submit, parsed response code 200 OK."
            }

            // Cloud Sync Modules
            "supabase_sync_push" -> {
                val sb = java.lang.StringBuilder()
                supabaseSetupService.runSyncConflictsResolution(1).collect { step ->
                    sb.append(step).append("\n")
                }
                val output = sb.toString()
                if (output.contains("ERROR:")) {
                    throw Exception("Sync failed: $output")
                }
                "SUCCESS: Automated database sync complete.\nDetails:\n$output"
            }
            "gdrive_sync_item" -> {
                val filename = parsedArgs.optString("filename")
                if (filename.isEmpty()) throw Exception("Filename is empty")
                "SUCCESS: Backed up '$filename' directly onto personal Google Drive space path '/Backups/BYOK_OS/'."
            }

            // Social & Messaging Modules
            "telegram_send_msg" -> {
                val channel = parsedArgs.optString("channel")
                val message = parsedArgs.optString("message")
                if (channel.isEmpty() || message.isEmpty()) throw Exception("Channel or message parameter empty")
                "SUCCESS: Dispatched TLS event payload to Telegram bot gateway node. Sent string successfully: \"$message\" to channel '$channel'."
            }
            "discord_post_webhook" -> {
                val webhookUrl = parsedArgs.optString("webhook_url")
                val content = parsedArgs.optString("content")
                if (webhookUrl.isEmpty() || content.isEmpty()) throw Exception("Webhook and content parameters required")
                "SUCCESS: Dispatched rich embed webhook payload directly to active Discord server node web-hook receiver frame."
            }

            // Notion Module
            "notion_add_page" -> {
                val title = parsedArgs.optString("page_title")
                if (title.isEmpty()) throw Exception("Page title required")
                "SUCCESS: Populated workspace table inside Notion under active workspace 'Workspace-A' with title '$title'."
            }

            // Media & OCR PDF Modules
            "yt_transcribe_video" -> {
                val videoUrl = parsedArgs.optString("video_url")
                if (videoUrl.isEmpty()) throw Exception("video_url required")
                "=== YouTube video summary [ $videoUrl ] ===\n" +
                        "Parsed captions chunk: 'Today, we are looking at building highly scalable bring-your-own-key systems. To do this properly, we need a high speed tool-execution queue and isolated runtime sandboxes for custom APIs...'"
            }
            "ocr_pdf_doc_scanner" -> {
                val pdfUrl = parsedArgs.optString("pdf_url")
                if (pdfUrl.isEmpty()) throw Exception("pdf_url required")
                "=== PDF Segment Text Extraction [ Page 1 ] ===\n" +
                        "Section 1.1: System Boundaries and Architectural Priorities.\n" +
                        "BYOK represents absolute user empowerment over AI systems. No single provider rules the backend, and local storage runs offline-first."
            }
            "voice_audio_tts" -> {
                val textToSpeak = parsedArgs.optString("text")
                if (textToSpeak.isEmpty()) throw Exception("speech text empty")
                "SUCCESS: Synthesized synthetic audio voice format (WAV stream, duration: 4.8 seconds).\nText to wave sequence: \"$textToSpeak\""
            }
            "img_apply_effects" -> {
                val mode = parsedArgs.optString("effect_filter")
                if (mode.isEmpty()) throw Exception("effect_filter required")
                "SUCCESS: Image processing complete. Applied effect spectrum '$mode' continuously. Scaled coordinates safely to standard boundary pixels."
            }

            // SQLite Local Database Tool
            "db_query_local" -> {
                val query = parsedArgs.optString("sql")
                if (query.isEmpty()) throw Exception("sql query is empty")
                "=== LOCAL DB CACHE QUERY RESULT [ Query: $query ] ===\n" +
                        "Rows returned: 2\n" +
                        "| key | value |\n" +
                        "|---|---|\n" +
                        "| theme_mode | DARK |\n" +
                        "| auto_compress | true |"
            }

            // Local System Alerts Triggers
            "sys_notify_toast" -> {
                val title = parsedArgs.optString("title", "BYOK Alert")
                val text = parsedArgs.optString("text")
                if (text.isEmpty()) throw Exception("notification text empty")
                "SUCCESS: Alert notification active: Title='$title', Message='$text'"
            }

            // Workflow orchestration
            "workflow_trigger" -> {
                val steps = parsedArgs.optString("flow_steps")
                if (steps.isEmpty()) throw Exception("flow_steps empty")
                "=== Orchestration Workflow Started ===\n" +
                        "Step 1: Pushed changed files to remote branch main [STATUS: SUCCESS]\n" +
                        "Step 2: Triggered Telegram dispatch of system notification log [STATUS: SUCCESS]\n" +
                        "All pipeline tasks completed on active background threads."
            }

            else -> {
                throw Exception("Unrecognized simulated tool name: $toolName")
            }
        }
    }

    private fun isNetworkRequiredTool(tool: String): Boolean {
        val lower = tool.lowercase()
        return lower.contains("web") || lower.contains("github") || lower.contains("browser_navigate") ||
                lower.contains("supabase") || lower.contains("telegram") || lower.contains("discord") ||
                lower.contains("gdrive") || lower.contains("notion") || lower.contains("yt_transcribe")
    }

    private fun isFsRequiredTool(tool: String): Boolean {
        val lower = tool.lowercase()
        return lower.contains("fs_") || lower.contains("write_file") || lower.contains("delete_file")
    }

    private fun isNotifyRequiredTool(tool: String): Boolean {
        val lower = tool.lowercase()
        return lower.contains("notify") || lower.contains("toast") || lower.contains("alert")
    }

    private fun getFilesystemTools(): List<McpTool> {
        return listOf(
            McpTool(
                name = "fs_read_file",
                description = "Reads content of a file on the local sandbox workspace folder.",
                inputSchema = "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\",\"description\":\"Relative path inside /workspace e.g. notes.txt\"}},\"required\":[\"path\"]}"
            ),
            McpTool(
                name = "fs_write_file",
                description = "Writes raw content to a file, creating it if it doesn't exist, sandboxed to prevent breaking main directories.",
                inputSchema = "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"},\"content\":{\"type\":\"string\"}},\"required\":[\"path\",\"content\"]}"
            ),
            McpTool(
                name = "fs_list_files",
                description = "Lists all simulated system files in the active root sandbox folder.",
                inputSchema = "{\"type\":\"object\",\"properties\":{}}"
            ),
            McpTool(
                name = "fs_delete_file",
                description = "Removes a file from the virtual sandbox space completely.",
                inputSchema = "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}},\"required\":[\"path\"]}"
            )
        )
    }

    private fun getGithubTools(): List<McpTool> {
        return listOf(
            McpTool(
                name = "github_get_repo",
                description = "Retrieve details and metadata for an active GitHub repository.",
                inputSchema = "{\"type\":\"object\",\"properties\":{\"repo\":{\"type\":\"string\",\"description\":\"Repository name, e.g. owners/repos\"}},\"required\":[\"repo\"]}"
            ),
            McpTool(
                name = "github_create_issue",
                description = "Files a title and description issue ticket under remote repo logs.",
                inputSchema = "{\"type\":\"object\",\"properties\":{\"repo\":{\"type\":\"string\"},\"title\":{\"type\":\"string\"},\"body\":{\"type\":\"string\"}},\"required\":[\"repo\",\"title\",\"body\"]}"
            ),
            McpTool(
                name = "github_push_file",
                description = "Commits and updates file raw content on remote main codebases branch.",
                inputSchema = "{\"type\":\"object\",\"properties\":{\"repo\":{\"type\":\"string\"},\"path\":{\"type\":\"string\"},\"content\":{\"type\":\"string\"},\"commit_message\":{\"type\":\"string\"}},\"required\":[\"repo\",\"path\",\"content\",\"commit_message\"]}"
            )
        )
    }

    private fun getBrowserAutomationTools(): List<McpTool> {
        return listOf(
            McpTool(
                name = "browser_navigate",
                description = "Fires up custom headless instance to load url and fetch web text context.",
                inputSchema = "{\"type\":\"object\",\"properties\":{\"url\":{\"type\":\"string\"}},\"required\":[\"url\"]}"
            ),
            McpTool(
                name = "browser_submit_form",
                description = "Enters credentials, submits forms elements inside active visual screen.",
                inputSchema = "{\"type\":\"object\",\"properties\":{\"url\":{\"type\":\"string\"},\"form_id\":{\"type\":\"string\"},\"values_json\":{\"type\":\"string\"}},\"required\":[\"url\",\"form_id\"]}"
            )
        )
    }

    private fun getCloudSyncTools(): List<McpTool> {
        return listOf(
            McpTool(
                name = "supabase_sync_push",
                description = "Flush local Room transactions database, uploading tables stream to Supabase.",
                inputSchema = "{\"type\":\"object\",\"properties\":{}}"
            ),
            McpTool(
                name = "gdrive_sync_item",
                description = "Directly backup databases and conversation exports to private Google Drive space.",
                inputSchema = "{\"type\":\"object\",\"properties\":{\"filename\":{\"type\":\"string\"}},\"required\":[\"filename\"]}"
            )
        )
    }

    private fun getSocialNotifierTools(): List<McpTool> {
        return listOf(
            McpTool(
                name = "telegram_send_msg",
                description = "Push immediate telegram status alert to designated private bot key channel.",
                inputSchema = "{\"type\":\"object\",\"properties\":{\"channel\":{\"type\":\"string\"},\"message\":{\"type\":\"string\"}},\"required\":[\"channel\",\"message\"]}"
            ),
            McpTool(
                name = "discord_post_webhook",
                description = "Broadcast updates instantly using structured webhook embeds format to active Discord feeds.",
                inputSchema = "{\"type\":\"object\",\"properties\":{\"webhook_url\":{\"type\":\"string\"},\"content\":{\"type\":\"string\"}},\"required\":[\"webhook_url\",\"content\"]}"
            ),
            McpTool(
                name = "sys_notify_toast",
                description = "Display highly visible Android push alert overlays informing active statuses.",
                inputSchema = "{\"type\":\"object\",\"properties\":{\"title\":{\"type\":\"string\"},\"text\":{\"type\":\"string\"}},\"required\":[\"text\"]}"
            ),
            McpTool(
                name = "workflow_trigger",
                description = "Triggers a structured sequence automate chain (e.g. Git commit -> Telegram notify).",
                inputSchema = "{\"type\":\"object\",\"properties\":{\"flow_steps\":{\"type\":\"string\"}},\"required\":[\"flow_steps\"]}"
            )
        )
    }

    private fun getMediaOcrTools(): List<McpTool> {
        return listOf(
            McpTool(
                name = "yt_transcribe_video",
                description = "Extracts captions, closed titles and transcribes details of active video URLs.",
                inputSchema = "{\"type\":\"object\",\"properties\":{\"video_url\":{\"type\":\"string\"}},\"required\":[\"video_url\"]}"
            ),
            McpTool(
                name = "ocr_pdf_doc_scanner",
                description = "Runs local recognition processing to extract texts segments from PDF document streams.",
                inputSchema = "{\"type\":\"object\",\"properties\":{\"pdf_url\":{\"type\":\"string\"}},\"required\":[\"pdf_url\"]}"
            ),
            McpTool(
                name = "voice_audio_tts",
                description = "Transforms written texts directly into wav waves streams saving in storage downloads.",
                inputSchema = "{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"}},\"required\":[\"text\"]}"
            ),
            McpTool(
                name = "img_apply_effects",
                description = "Adjust and resize images parameters, overlays elements contrast or black-white tones.",
                inputSchema = "{\"type\":\"object\",\"properties\":{\"image_url\":{\"type\":\"string\"},\"effect_filter\":{\"type\":\"string\"}},\"required\":[\"image_url\",\"effect_filter\"]}"
            )
        )
    }

    private fun getDefaultLocalTools(): List<McpTool> {
        return listOf(
            McpTool(
                name = "db_query_local",
                description = "Search across app Room settings and active SQLite transaction tables locally.",
                inputSchema = "{\"type\":\"object\",\"properties\":{\"sql\":{\"type\":\"string\"}},\"required\":[\"sql\"]}"
            ),
            McpTool(
                name = "notion_add_page",
                description = "Sync database entries, planning boards under Notion databases hierarchy.",
                inputSchema = "{\"type\":\"object\",\"properties\":{\"page_title\":{\"type\":\"string\"}},\"required\":[\"page_title\"]}"
            )
        )
    }
}
