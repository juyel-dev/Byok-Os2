package com.example.feature.chat.viewmodel

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.domain.models.*
import com.example.core.domain.repository.ChatRepository
import com.example.core.data.service.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class ChatViewModel(
    application: Application,
    private val repository: ChatRepository,
    private val llmService: LlmService,
    private val mcpService: McpService,
    private val compressService: CompressService,
    private val memoryService: MemoryService
) : AndroidViewModel(application) {

    // Sessions & Messages flowing from Room directly
    val sessions: StateFlow<List<ChatSessionModel>> = repository.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    val currentMessages: StateFlow<List<Message>> = _currentSessionId
        .flatMapLatest { id ->
            if (id != null) repository.getMessagesForSessionFlow(id)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Providers
    val providers: StateFlow<List<LlmProviderModel>> = repository.allProvidersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mcpServers: StateFlow<List<McpServerModel>> = repository.allServersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeMcpToolsCount = MutableStateFlow<Int?>(null)
    val activeMcpToolsCount: StateFlow<Int?> = _activeMcpToolsCount.asStateFlow()

    private val _selectedProviderId = MutableStateFlow<String?>(null)
    val selectedProviderId: StateFlow<String?> = _selectedProviderId.asStateFlow()

    // Streaming state
    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _streamingMessageContent = MutableStateFlow("")
    val streamingMessageContent: StateFlow<String> = _streamingMessageContent.asStateFlow()

    // Attached image state (hoisted from ChatScreen)
    private val _attachedImageUri = MutableStateFlow<android.net.Uri?>(null)
    val attachedImageUri: StateFlow<android.net.Uri?> = _attachedImageUri.asStateFlow()

    private val _isProcessingImage = MutableStateFlow(false)
    val isProcessingImage: StateFlow<Boolean> = _isProcessingImage.asStateFlow()

    // One-time navigation events redirection
    private val _navigationEvents = MutableSharedFlow<NavigationEvent>(extraBufferCapacity = 1)
    val navigationEvents: SharedFlow<NavigationEvent> = _navigationEvents.asSharedFlow()

    // Toast and error dispatch
    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private var activeJob: Job? = null

    init {
        viewModelScope.launch {
            // Set first session as active if available and non-selected
            sessions.collectFirst { sessionList ->
                if (sessionList.isNotEmpty() && _currentSessionId.value == null) {
                    _currentSessionId.value = sessionList.first().id
                }
            }
        }
        viewModelScope.launch {
            // Synchronize active provider matching database state
            val list = repository.getAllProviders()
            val activePrv = list.firstOrNull { it.isActive } ?: list.firstOrNull()
            _selectedProviderId.value = activePrv?.id
        }
        viewModelScope.launch {
            mcpServers.collectLatest { servers ->
                _activeMcpToolsCount.value = null
                try {
                    val enabled = servers.filter { it.isEnabled }
                    if (enabled.isEmpty()) {
                        _activeMcpToolsCount.value = 0
                    } else {
                        var count = 0
                        for (srv in enabled) {
                            val listResult = mcpService.listTools(srv)
                            val size = listResult.getOrNull()?.size
                            if (size != null) {
                                count += size
                            } else {
                                val cachedCount = if (!srv.cachedToolsJson.isNullOrBlank()) {
                                    try {
                                        JSONArray(srv.cachedToolsJson).length()
                                    } catch (e: Exception) {
                                        0
                                    }
                                } else {
                                    // if unavailable, say -1 or fallback to 0
                                    -1
                                }
                                if (cachedCount != -1) {
                                    count += cachedCount
                                } else {
                                    _activeMcpToolsCount.value = -1
                                    return@collectLatest
                                }
                            }
                        }
                        _activeMcpToolsCount.value = count
                    }
                } catch (e: Exception) {
                    _activeMcpToolsCount.value = -1
                }
            }
        }
    }

    private suspend fun <T> Flow<T>.collectFirst(action: suspend (T) -> Unit) {
        take(1).collect(action)
    }

    fun showToast(msg: String) {
        _toastMessage.value = msg
        try {
            Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            // safe fallback
        }
        viewModelScope.launch {
            delay(2500)
            if (_toastMessage.value == msg) {
                _toastMessage.value = null
            }
        }
    }

    fun selectSession(sessionId: String) {
        _currentSessionId.value = sessionId
    }

    fun selectProvider(providerId: String) {
        _selectedProviderId.value = providerId
        viewModelScope.launch {
            val prvList = repository.getAllProviders()
            for (prv in prvList) {
                repository.updateProvider(prv.copy(isActive = prv.id == providerId))
            }
        }
    }

    fun createNewChat() {
        viewModelScope.launch {
            val provider = _selectedProviderId.value ?: "preset_openai"
            val newSession = ChatSessionModel(
                id = java.util.UUID.randomUUID().toString(),
                title = "New Conversation",
                providerId = provider
            )
            repository.insertSession(newSession)
            _currentSessionId.value = newSession.id
        }
    }

    fun deleteSession(id: String) {
        viewModelScope.launch {
            repository.deleteSessionById(id)
            if (_currentSessionId.value == id) {
                _currentSessionId.value = repository.allSessions.firstOrNull()?.firstOrNull()?.id
            }
        }
    }

    fun processAndValidateImage(context: android.content.Context, uri: android.net.Uri?) {
        if (uri == null) {
            _attachedImageUri.value = null
            return
        }
        viewModelScope.launch {
            _isProcessingImage.value = true
            delay(400) // Brief animation scan feedback
            try {
                val contentResolver = context.contentResolver
                val mimeType = contentResolver.getType(uri) ?: ""
                
                // 1. Validate Format (JPEG, PNG, WEBP)
                val isSupportedFormat = mimeType.contains("image/jpeg") || 
                        mimeType.contains("image/jpg") || 
                        mimeType.contains("image/png") || 
                        mimeType.contains("image/webp") || 
                        uri.path?.let { path ->
                            path.endsWith(".jpg", true) || 
                            path.endsWith(".jpeg", true) ||
                            path.endsWith(".png", true) || 
                            path.endsWith(".webp", true)
                        } ?: false

                if (!isSupportedFormat) {
                    showToast("❌ Rejected: Only JPEG, PNG, and WEBP formats are supported.")
                    _attachedImageUri.value = null
                    return@launch
                }

                // 2. Validate Size (Limit to 5 MB)
                val maxSize = 5 * 1024 * 1024
                var size: Long = 0
                contentResolver.openAssetFileDescriptor(uri, "r")?.use { fd ->
                    size = fd.length
                }
                if (size > maxSize) {
                    showToast("❌ Rejected: Image size exceeds maximum limit of 5MB.")
                    _attachedImageUri.value = null
                    return@launch
                }

                _attachedImageUri.value = uri
                showToast("📷 Image preview attached and validated!")
            } catch (e: Exception) {
                showToast("❌ Failed to process attachment: ${e.localizedMessage}")
                _attachedImageUri.value = null
            } finally {
                _isProcessingImage.value = false
            }
        }
    }

    fun clearAttachedImage() {
        _attachedImageUri.value = null
    }

    fun sendMessage(text: String) {
        val imageUri = _attachedImageUri.value
        val finalContent = if (imageUri != null) {
            "[IMAGE: $imageUri] $text"
        } else {
            text
        }
        val trimmedText = finalContent.trim()
        val textForTitle = text.trim()
        if (trimmedText.isBlank() && imageUri == null) return
        val sessionId = _currentSessionId.value ?: return

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            try {
                val providerList = repository.getAllProviders()
                val provider = providerList.firstOrNull { it.id == _selectedProviderId.value }
                if (provider == null || !com.example.core.common.ProviderValidator.isConfigured(provider.encryptedApiKey)) {
                    showToast("Configure your proprietary API Key in model settings first!")
                    _navigationEvents.tryEmit(NavigationEvent.NavigateToProviders)
                    return@launch
                }

                // Clear attached image since we are successfully starting the send pipeline!
                _attachedImageUri.value = null

                // 1. Insert user message
                val userMessage = Message(
                    id = java.util.UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    role = "user",
                    content = trimmedText
                )
                repository.insertMessage(userMessage)

                // Update session title dynamically if default
                val session = repository.getSessionById(sessionId)
                if (session != null && session.title == "New Conversation") {
                    val displayTitle = if (textForTitle.isNotEmpty()) textForTitle else "Image Attachment"
                    val croppedTitle = if (displayTitle.length > 25) displayTitle.take(22) + "..." else displayTitle
                    repository.updateSession(session.copy(title = croppedTitle, updatedAt = System.currentTimeMillis()))
                } else if (session != null) {
                    repository.updateSession(session.copy(updatedAt = System.currentTimeMillis()))
                }

                // 2. Scan content to save memories automatically (Agentic preference)
                val newMemories = memoryService.extractMemories(text)
                for (mem in newMemories) {
                    repository.insertMemory(mem)
                    showToast("💡 Fact captured: \"${mem.content}\"")
                }

                // Call stream generation
                runChatGenerationStream(sessionId, provider)

            } catch (e: Exception) {
                _isStreaming.value = false
            }
        }
    }

    private suspend fun runChatGenerationStream(sessionId: String, provider: LlmProviderModel) {
        _isStreaming.value = true
        _streamingMessageContent.value = ""

        // Fetch settings parameters from repository on generation trigger
        val systemPromptVal = repository.getSetting("system_prompt", "You are a helpful BYOK OS assistant. Give clear, elegant answers. Make use of available tools efficiently.")
        val tempVal = repository.getSetting("temperature", "0.7").toDoubleOrNull() ?: 0.7
        val maxTokensVal = repository.getSetting("max_tokens", "4096").toIntOrNull() ?: 4096
        val topPVal = repository.getSetting("top_p", "0.9").toDoubleOrNull() ?: 0.9
        val topKVal = repository.getSetting("top_k", "40").toIntOrNull() ?: 40
        val tempPresence = repository.getSetting("presence_penalty", "0.0").toDoubleOrNull() ?: 0.0
        val tempFrequency = repository.getSetting("frequency_penalty", "0.0").toDoubleOrNull() ?: 0.0

        val autoCompressEnabledVal = repository.getSetting("auto_compress", "true").toBoolean()
        val compressThresholdVal = repository.getSetting("compress_threshold", "0.8").toFloatOrNull() ?: 0.8f
        val keepLastNVal = repository.getSetting("keep_last_n", "20").toIntOrNull() ?: 20

        val sandboxNetwork = repository.getSetting("mcp_sandbox_network", "true").toBoolean()
        val sandboxFilesystem = repository.getSetting("mcp_sandbox_filesystem", "true").toBoolean()
        val sandboxNotifications = repository.getSetting("mcp_sandbox_notifications", "true").toBoolean()

        // Fetch active context memories
        val activeMems = repository.getActiveMemories()
        val memContext = if (activeMems.isNotEmpty()) {
            "\n\n[USER PERSONAL FACT DISPOSAL - INTEGRATE SILENTLY]\n" + activeMems.joinToString("\n") { "- " + it.content }
        } else ""

        val systemPromptConfig = systemPromptVal + memContext

        // Fetch active MCP tool lists to inject into parameters using unified registry
        val activeMcpServers = repository.getAllServers().filter { it.isEnabled }
        val registryBuilder = McpRegistry.Builder()

        for (srv in activeMcpServers) {
            val toolsResult = mcpService.listTools(srv)
            toolsResult.onSuccess { list ->
                list.forEach { mcpTool ->
                    registryBuilder.register(srv, mcpTool)
                }
            }
        }

        val registry = registryBuilder.build()
        android.util.Log.i("ChatViewModelRegistry", registry.dumpSnapshot())

        val toolDefinitionsArray = JSONArray()
        registry.items.forEach { registered ->
            val toolDef = JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", registered.identifier.toNamespacedId())
                    put("description", "[Server: ${registered.server.name}] ${registered.tool.description}")
                    put("parameters", JSONObject(registered.tool.inputSchema))
                })
            }
            toolDefinitionsArray.put(toolDef)
        }

        val toolsJsonStr = if (toolDefinitionsArray.length() > 0) toolDefinitionsArray.toString() else null

        // Recursive execution pipeline
        var executeLoop = true
        var depthCount = 0

        while (executeLoop && depthCount < 5) {
            depthCount++
            val history = repository.getMessagesForSession(sessionId)

            var toolCallId = ""
            var toolCallName = ""
            var toolCallArgs = ""
            var textAppended = ""

            llmService.streamChat(
                provider = provider,
                history = history,
                systemPrompt = systemPromptConfig,
                temperature = tempVal,
                topP = topPVal,
                topK = topKVal,
                presencePenalty = tempPresence,
                frequencyPenalty = tempFrequency,
                maxTokens = maxTokensVal,
                toolsJson = toolsJsonStr,
                context = getApplication()
            ).collect { chunk ->
                when (chunk) {
                    is LlmChunk.Text -> {
                        _streamingMessageContent.value = _streamingMessageContent.value + chunk.content
                        textAppended += chunk.content
                    }
                    is LlmChunk.ToolCall -> {
                        toolCallId = chunk.id
                        toolCallName = chunk.name
                        toolCallArgs = chunk.arguments
                    }
                    is LlmChunk.Error -> {
                        _streamingMessageContent.value = "[PROVIDER EXCEPTION]: " + chunk.message
                        textAppended += "[PROVIDER EXCEPTION]: " + chunk.message
                    }
                    else -> {}
                }
            }

            if (toolCallName.isNotEmpty()) {
                val toolCallMessage = Message(
                    id = java.util.UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    role = "assistant",
                    content = textAppended.ifEmpty { "🔍 Requesting tools..." },
                    isToolCall = true,
                    toolName = toolCallName,
                    toolArgumentsJson = toolCallArgs
                )
                repository.insertMessage(toolCallMessage)

                val executionOutput = when (val dispatchRes = registry.dispatch(toolCallName)) {
                    is DispatchResult.Success -> {
                        val registeredTool = dispatchRes.registeredTool
                        _streamingMessageContent.value = "\n🔧 Executing tool '${registeredTool.tool.name}' on server '${registeredTool.server.name}'..."
                        val jsonArgs = try {
                            JSONObject(toolCallArgs)
                        } catch (e: Exception) {
                            try {
                                val repairer = JsonStreamAccumulator()
                                repairer.append(toolCallArgs)
                                repairer.getValidatedJson()
                            } catch (e2: Exception) {
                                JSONObject()
                            }
                        }

                        mcpService.executeTool(
                            registeredTool.server,
                            registeredTool.tool.name,
                            jsonArgs,
                            sandboxNetwork,
                            sandboxFilesystem,
                            sandboxNotifications
                        ).getOrElse {
                            "Error executing tool: ${it.message}"
                        }
                    }
                    is DispatchResult.AmbiguityError -> {
                        val errLog = "Registry Dispatch Blocked: Tool '$toolCallName' is ambiguous. Provided by multiple servers: ${dispatchRes.matchingServers.map { it.name }}."
                        android.util.Log.e("ChatViewModel", errLog)
                        "Ambiguity Error: The tool '$toolCallName' is ambiguous because it is offered by multiple servers: [${dispatchRes.matchingServers.joinToString { it.name }}]. Please clarify which server to route to by using the namespaced ID: '<serverId>::$toolCallName'."
                    }
                    is DispatchResult.NotFound -> {
                        val errLog = "Registry Dispatch Error: Tool '$toolCallName' not found."
                        android.util.Log.e("ChatViewModel", errLog)
                        "Error: Tool '$toolCallName' not found in active MCP servers."
                    }
                    is DispatchResult.InvalidNamespace -> {
                        val errLog = "Registry Dispatch Error: Tool name '$toolCallName' has an invalid namespace configuration."
                        android.util.Log.e("ChatViewModel", errLog)
                        "Error: Tool name '$toolCallName' has an invalid namespace configuration."
                    }
                }

                _streamingMessageContent.value = ""

                val toolResultMsg = Message(
                    id = java.util.UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    role = "tool",
                    content = executionOutput,
                    toolName = toolCallName
                )
                repository.insertMessage(toolResultMsg)
            } else {
                val finalAssistantMsg = Message(
                    id = java.util.UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    role = "assistant",
                    content = textAppended
                )
                repository.insertMessage(finalAssistantMsg)
                executeLoop = false
            }
        }

        _isStreaming.value = false
        _streamingMessageContent.value = ""

        // Post Generation actions: Context Auto Compress Checks
        if (autoCompressEnabledVal) {
            val messagesForPrune = repository.getMessagesForSession(sessionId)
            val currentWordEstimate = compressService.estimateTokens(messagesForPrune.joinToString(" ") { it.content })
            val triggerBound = (128000 * compressThresholdVal).toInt().coerceAtMost(2500)
            
            if (currentWordEstimate > triggerBound && messagesForPrune.size > keepLastNVal + 5) {
                try {
                    val (compressedNode, keptNodes) = compressService.compressMessages(messagesForPrune, keepLastNVal)
                    repository.deleteMessagesForSession(sessionId)
                    repository.insertMessage(compressedNode)
                    for (node in keptNodes) {
                        repository.insertMessage(node)
                    }
                    showToast("📦 Active chat context auto-compressed to optimize token usage.")
                } catch (e: Exception) {
                    // Ignore pruning errors
                }
            }
        }
    }

    fun retryGeneration() {
        val sessionId = _currentSessionId.value ?: return
        viewModelScope.launch {
            val currentMsgs = repository.getMessagesForSession(sessionId)
            if (currentMsgs.isEmpty()) return@launch

            val lastMsgList = currentMsgs.takeLastWhile { it.role == "assistant" || it.role == "tool" }
            for (msg in lastMsgList) {
                repository.deleteMessageById(msg.id)
            }

            val providerList = repository.getAllProviders()
            val provider = providerList.firstOrNull { it.id == _selectedProviderId.value }
            if (provider != null) {
                runChatGenerationStream(sessionId, provider)
            }
        }
    }
}
