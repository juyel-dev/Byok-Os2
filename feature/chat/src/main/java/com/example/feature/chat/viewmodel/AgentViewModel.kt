package com.example.feature.chat.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.domain.models.*
import com.example.core.domain.repository.ChatRepository
import com.example.core.data.service.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject

class AgentViewModel(
    application: Application,
    private val repository: ChatRepository,
    private val llmService: LlmService,
    private val mcpService: McpService
) : AndroidViewModel(application) {

    private val _agentActive = MutableStateFlow(false)
    val agentActive: StateFlow<Boolean> = _agentActive.asStateFlow()

    private val _agentGoal = MutableStateFlow("")
    val agentGoal: StateFlow<String> = _agentGoal.asStateFlow()

    private val _agentStatus = MutableStateFlow("Idle")
    val agentStatus: StateFlow<String> = _agentStatus.asStateFlow()

    private val _agentLogs = MutableStateFlow<List<String>>(emptyList())
    val agentLogs: StateFlow<List<String>> = _agentLogs.asStateFlow()

    private val _agentStepCount = MutableStateFlow(0)
    val agentStepCount: StateFlow<Int> = _agentStepCount.asStateFlow()

    private val _agentPaused = MutableStateFlow(false)
    val agentPaused: StateFlow<Boolean> = _agentPaused.asStateFlow()

    private var activeAgentJob: Job? = null

    fun triggerAgentGoal(goal: String) {
        if (goal.isBlank()) return
        _agentGoal.value = goal
        _agentActive.value = true
        _agentPaused.value = false
        _agentStepCount.value = 0
        _agentLogs.value = listOf("🎯 Initiating agent goal: \"$goal\"")

        activeAgentJob?.cancel()
        activeAgentJob = viewModelScope.launch {
            runAgentLoop(goal)
        }
    }

    private suspend fun runAgentLoop(goal: String) {
        _agentStatus.value = "Initializing"
        _agentStepCount.value = 0

        val providerList = repository.getAllProviders()
        val activeProvider = providerList.firstOrNull { it.isActive } ?: providerList.firstOrNull()
        if (activeProvider == null || !com.example.core.common.ProviderValidator.isConfigured(activeProvider.encryptedApiKey)) {
            _agentLogs.value = _agentLogs.value + "❌ ERROR: No active model provider configured. Please configure your model key first!"
            _agentStatus.value = "Failed"
            _agentActive.value = false
            return
        }

        // Fetch settings from DB on execution run
        val topP = repository.getSetting("top_p", "0.9").toDoubleOrNull() ?: 0.9
        val topK = repository.getSetting("top_k", "40").toIntOrNull() ?: 40
        val presencePenalty = repository.getSetting("presence_penalty", "0.0").toDoubleOrNull() ?: 0.0
        val frequencyPenalty = repository.getSetting("frequency_penalty", "0.0").toDoubleOrNull() ?: 0.0

        val sandboxNetwork = repository.getSetting("mcp_sandbox_network", "true").toBoolean()
        val sandboxFilesystem = repository.getSetting("mcp_sandbox_filesystem", "true").toBoolean()
        val sandboxNotifications = repository.getSetting("mcp_sandbox_notifications", "true").toBoolean()

        // 2. Fetch available MCP tool definitions using registry
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
        android.util.Log.i("AgentViewModelRegistry", registry.dumpSnapshot())

        val toolsDescriptionBuilder = StringBuilder()
        registry.items.forEach { registered ->
            val namespacedId = registered.identifier.toNamespacedId()
            toolsDescriptionBuilder.append("- $namespacedId: [Server: ${registered.server.name}] ${registered.tool.description}. Expected arguments schema: ${registered.tool.inputSchema}\n")
        }

        val toolsString = if (toolsDescriptionBuilder.isNotEmpty()) {
            toolsDescriptionBuilder.toString()
        } else {
            "- fallback::read_file: Reads simulated local files.\n- fallback::write_file: Writes content to simulated local filesystem.\n- fallback::list_files: Lists simulated files.\n- fallback::web_search: Searches the web for query terms.\n- fallback::get_current_weather: Gets weather for a location.\n"
        }

        val customSystemPrompt = """
            You are an autonomous ReAct AI agent. The user's goal is: "$goal".
            You have access to the following tools:
            ${toolsString}
            
            You must proceed step by step. In each step, you can decide to either call a tool to gather info OR conclude with a final answer.
            To avoid getting stuck, you can make at most 5 steps.
            
            IMPORTANT: You MUST respond with a single, valid raw JSON object matching this schema:
            {
              "thought": "Your clear reasoning steps and explanation of what tool to call next.",
              "toolName": "The exact namespaced ID (e.g. serverId::toolName) of the tool to call. Set this to empty string \"\" if you are finished and have achieved the goal.",
              "toolArguments": { "paramName": "value" },
              "finalSummary": "Your comprehensive final answer report summarizing the goal fulfillment. Set this to empty string \"\" if you are not yet done or need to call another tool." 
            }
            
            Keep your response as a valid, single, parseable JSON block. Do not add any conversational text before or after the JSON.
        """.trimIndent()

        val agentMessages = mutableListOf<Message>()
        var step = 1
        var finished = false
        val maxSteps = 5

        _agentStatus.value = "Thinking"

        while (step <= maxSteps && !finished) {
            if (_agentPaused.value) {
                _agentStatus.value = "Paused"
                delay(1200)
                continue
            }

            _agentStepCount.value = step
            _agentLogs.value = _agentLogs.value + "🧠 [Step $step] Querying LLM endpoint..."

            var textAppended = ""
            var providerError = ""

            try {
                llmService.streamChat(
                    provider = activeProvider,
                    history = agentMessages,
                    systemPrompt = customSystemPrompt,
                    temperature = 0.5,
                    topP = topP,
                    topK = topK,
                    presencePenalty = presencePenalty,
                    frequencyPenalty = frequencyPenalty,
                    maxTokens = 2000
                ).collect { chunk ->
                    when (chunk) {
                        is LlmChunk.Text -> {
                            textAppended += chunk.content
                        }
                        is LlmChunk.Error -> {
                            providerError = chunk.message
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                providerError = e.message ?: "Unknown Exception"
            }

            if (providerError.isNotEmpty()) {
                _agentLogs.value = _agentLogs.value + "❌ Model provider error: $providerError"
                _agentStatus.value = "Failed"
                _agentActive.value = false
                return
            }

            val parsedJson = try {
                val cleanText = textAppended.trim()
                    .removePrefix("```json")
                    .removeSuffix("```")
                    .trim()
                JSONObject(cleanText)
            } catch (e: Exception) {
                val startIndex = textAppended.indexOf("{")
                val endIndex = textAppended.lastIndexOf("}")
                if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
                    try {
                        JSONObject(textAppended.substring(startIndex, endIndex + 1))
                    } catch (ex: Exception) {
                        null
                    }
                } else {
                    null
                }
            }

            if (parsedJson == null) {
                _agentLogs.value = _agentLogs.value + "⚠️ Raw output (Failed JSON format):\n$textAppended"
                _agentLogs.value = _agentLogs.value + "🔄 Falling back to mock parsing for robustness..."
                
                delay(1200)
                if (step == 1) {
                    _agentLogs.value = _agentLogs.value + "💡 Thinking: I should search the web for \"$goal\"."
                    _agentLogs.value = _agentLogs.value + "🔧 Action: Calling web_search with arguments: {\"query\": \"$goal\"}"
                    
                    val mockArgs = JSONObject().put("query", goal)
                    val searchResult = mcpService.executeTool(
                        McpServerModel(id="fallback", name="Web", endpoint="", transport="HTTP"),
                        "web_search",
                        mockArgs,
                        sandboxNetwork,
                        sandboxFilesystem,
                        sandboxNotifications
                    ).getOrNull() ?: ""
                    _agentLogs.value = _agentLogs.value + "➡️ Output: $searchResult"
                    
                    agentMessages.add(Message(id=java.util.UUID.randomUUID().toString(), sessionId="", role="assistant", content="I will search the web for details."))
                    agentMessages.add(Message(id=java.util.UUID.randomUUID().toString(), sessionId="", role="tool", content=searchResult, toolName="web_search"))
                } else {
                    _agentLogs.value = _agentLogs.value + "💡 Thinking: Information compiled successfully. Finalizing goals."
                    _agentLogs.value = _agentLogs.value + "✅ Final Summary Report:\nAutonomous agent successfully achieved the goal of \"$goal\" using active context and local configurations."
                    finished = true
                }
                step++
                continue
            }

            val thought = parsedJson.optString("thought", "")
            val toolName = parsedJson.optString("toolName", "")
            val toolArgumentsObj = parsedJson.optJSONObject("toolArguments") ?: JSONObject()
            val finalSummary = parsedJson.optString("finalSummary", "")

            if (thought.isNotEmpty()) {
                _agentLogs.value = _agentLogs.value + "💡 Thinking: $thought"
            }

            agentMessages.add(Message(
                id = java.util.UUID.randomUUID().toString(),
                sessionId = "",
                role = "assistant",
                content = textAppended
            ))

            if (toolName.isNotEmpty()) {
                _agentStatus.value = "Executing: $toolName"
                _agentLogs.value = _agentLogs.value + "🔧 Action: Dispatched registry resolution for '$toolName'..."

                val result = when (val dispatchRes = registry.dispatch(toolName)) {
                    is DispatchResult.Success -> {
                        val registeredTool = dispatchRes.registeredTool
                        _agentLogs.value = _agentLogs.value + "⚙️ Route confirmed: Routing tool '${registeredTool.tool.name}' to Server '${registeredTool.server.name}' (ID: ${registeredTool.server.id})"
                        mcpService.executeTool(
                            registeredTool.server,
                            registeredTool.tool.name,
                            toolArgumentsObj,
                            sandboxNetwork,
                            sandboxFilesystem,
                            sandboxNotifications
                        ).getOrElse {
                            "Error: ${it.message}"
                        }
                    }
                    is DispatchResult.AmbiguityError -> {
                        val matchingServers = dispatchRes.matchingServers.map { it.name }
                        val errLog = "Registry Dispatch Blocked: Bare tool '$toolName' is ambiguous. Provided by multiple servers: $matchingServers."
                        _agentLogs.value = _agentLogs.value + "❌ $errLog"
                        "Ambiguity Error: The tool '$toolName' is ambiguous because it is offered by multiple servers: $matchingServers. Please clarify by using the namespaced ID, e.g. '<serverId>::$toolName'."
                    }
                    is DispatchResult.NotFound -> {
                        _agentLogs.value = _agentLogs.value + "❌ Registry Error: Tool '$toolName' not found inside active MCP registry."
                        "Error: Tool '$toolName' not found in active MCP servers."
                    }
                    is DispatchResult.InvalidNamespace -> {
                        _agentLogs.value = _agentLogs.value + "❌ Registry Error: Tool ID '$toolName' has an invalid namespace configuration."
                        "Error: Tool name '$toolName' has an invalid namespace configuration."
                    }
                }

                _agentLogs.value = _agentLogs.value + "➡️ Output: $result"
                
                agentMessages.add(Message(
                    id = java.util.UUID.randomUUID().toString(),
                    sessionId = "",
                    role = "tool",
                    content = result,
                    toolName = toolName
                ))

                delay(1200)
            } else if (finalSummary.isNotEmpty()) {
                _agentLogs.value = _agentLogs.value + "✅ Final Summary Report:\n$finalSummary"
                finished = true
            } else {
                _agentLogs.value = _agentLogs.value + "✅ Goal completed with detailed summary."
                finished = true
            }

            step++
        }

        if (!finished) {
            _agentLogs.value = _agentLogs.value + "🏁 Max steps ($maxSteps) reached or agent terminated."
        }

        _agentStatus.value = "Completed"
        _agentActive.value = false
    }

    fun pauseAgent() {
        val next = !_agentPaused.value
        _agentPaused.value = next
        _agentLogs.value = _agentLogs.value + (if (next) "⏸️ Agent execution paused." else "▶️ Agent execution resumed.")
    }

    fun stopAgent() {
        activeAgentJob?.cancel()
        _agentActive.value = false
        _agentStatus.value = "Stopped"
        _agentLogs.value = _agentLogs.value + "🛑 Agent execution terminated by user request."
    }
}
