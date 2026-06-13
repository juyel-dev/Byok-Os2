package com.example.feature.settings.viewmodel

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.domain.models.*
import com.example.core.domain.repository.ChatRepository
import com.example.core.domain.exceptions.BackupCorruptedException
import com.example.core.data.service.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class SettingsViewModel(
    application: Application,
    private val repository: ChatRepository,
    private val llmService: LlmService,
    private val supabaseService: SupabaseSetupService,
    private val securePrefs: SupabaseSecurePrefs
) : AndroidViewModel(application) {

    // Onboarding status
    private val _onboardingCompleted = MutableStateFlow(false)
    val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted.asStateFlow()

    // Providers & Mcp Servers flows for list-size counters on SettingsHome page
    val providers: StateFlow<List<LlmProviderModel>> = repository.allProvidersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mcpServers: StateFlow<List<McpServerModel>> = repository.allServersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Memories
    val memories: StateFlow<List<MemoryModel>> = repository.allMemories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Settings Parameters
    private val _themeMode = MutableStateFlow("DARK")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _temperature = MutableStateFlow(0.7f)
    val temperature: StateFlow<Float> = _temperature.asStateFlow()

    private val _maxTokens = MutableStateFlow(4096)
    val maxTokens: StateFlow<Int> = _maxTokens.asStateFlow()

    private val _topP = MutableStateFlow(0.9f)
    val topP: StateFlow<Float> = _topP.asStateFlow()

    private val _topK = MutableStateFlow(40)
    val topK: StateFlow<Int> = _topK.asStateFlow()

    private val _presencePenalty = MutableStateFlow(0.0f)
    val presencePenalty: StateFlow<Float> = _presencePenalty.asStateFlow()

    private val _frequencyPenalty = MutableStateFlow(0.0f)
    val frequencyPenalty: StateFlow<Float> = _frequencyPenalty.asStateFlow()

    private val _systemPrompt = MutableStateFlow("You are a helpful BYOK OS assistant. Give clear, elegant answers. Make use of available tools efficiently.")
    val systemPrompt: StateFlow<String> = _systemPrompt.asStateFlow()

    private val _autoCompressEnabled = MutableStateFlow(true)
    val autoCompressEnabled: StateFlow<Boolean> = _autoCompressEnabled.asStateFlow()

    private val _compressThreshold = MutableStateFlow(0.8f)
    val compressThreshold: StateFlow<Float> = _compressThreshold.asStateFlow()

    private val _keepLastN = MutableStateFlow(20)
    val keepLastN: StateFlow<Int> = _keepLastN.asStateFlow()

    // Supabase States
    private val _supabaseUrl = MutableStateFlow("")
    val supabaseUrl: StateFlow<String> = _supabaseUrl.asStateFlow()

    private val _supabaseServiceRoleKey = MutableStateFlow("")
    val supabaseServiceRoleKey: StateFlow<String> = _supabaseServiceRoleKey.asStateFlow()

    private val _supabasePat = MutableStateFlow("")
    val supabasePat: StateFlow<String> = _supabasePat.asStateFlow()

    private val _supabaseSyncEnabled = MutableStateFlow(false)
    val supabaseSyncEnabled: StateFlow<Boolean> = _supabaseSyncEnabled.asStateFlow()

    private val _supabaseLog = MutableStateFlow<List<String>>(emptyList())
    val supabaseLog: StateFlow<List<String>> = _supabaseLog.asStateFlow()

    private val _isSupabaseConfigured = MutableStateFlow(false)
    val isSupabaseConfigured: StateFlow<Boolean> = _isSupabaseConfigured.asStateFlow()

    // Notifications status
    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    init {
        viewModelScope.launch {
            loadConfiguredSettings()
            repairExistingInstallationsIfNeeded()
            prepopulatePresetsIfNeeded()
        }
    }

    private suspend fun repairExistingInstallationsIfNeeded() {
        try {
            val currentProvList = repository.getAllProviders()
            for (provider in currentProvList) {
                val rawKey = provider.encryptedApiKey.trim()
                if (rawKey.isEmpty()) {
                    // Re-save with proper sentinel state
                    val repaired = provider.copy(encryptedApiKey = com.example.core.common.ProviderValidator.KEY_SENTINEL)
                    repository.updateProvider(repaired)
                    android.util.Log.i("SettingsViewModel", "Repaired legacy blank API key for provider ${provider.displayName}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SettingsViewModel", "Error repairing existing installations", e)
        }
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

    private suspend fun loadConfiguredSettings() {
        _onboardingCompleted.value = repository.getSetting("onboarding_completed", "false").toBoolean()
        _themeMode.value = repository.getSetting("theme_mode", "DARK")
        _temperature.value = repository.getSetting("temperature", "0.7").toFloatOrNull() ?: 0.7f
        _maxTokens.value = repository.getSetting("max_tokens", "4096").toIntOrNull() ?: 4096
        _topP.value = repository.getSetting("top_p", "0.9").toFloatOrNull() ?: 0.9f
        _topK.value = repository.getSetting("top_k", "40").toIntOrNull() ?: 40
        _presencePenalty.value = repository.getSetting("presence_penalty", "0.0").toFloatOrNull() ?: 0.0f
        _frequencyPenalty.value = repository.getSetting("frequency_penalty", "0.0").toFloatOrNull() ?: 0.0f
        _systemPrompt.value = repository.getSetting("system_prompt", _systemPrompt.value)
        _autoCompressEnabled.value = repository.getSetting("auto_compress", "true").toBoolean()
        _compressThreshold.value = repository.getSetting("compress_threshold", "0.8").toFloatOrNull() ?: 0.8f
        _keepLastN.value = repository.getSetting("keep_last_n", "20").toIntOrNull() ?: 20

        _supabaseUrl.value = securePrefs.getUrl()
        _supabaseServiceRoleKey.value = securePrefs.getServiceRoleKey()
        _supabasePat.value = securePrefs.getPat()
        _supabaseSyncEnabled.value = repository.getSetting("supabase_sync", "false").toBoolean()
        _isSupabaseConfigured.value = securePrefs.isConfigured()
    }

    private suspend fun prepopulatePresetsIfNeeded() {
        val currentProvList = repository.getAllProviders()
        
        // Auto-upgrade any existing deprecated Nvidia/MiniMax provider model names to active ones
        for (provider in currentProvList) {
            var needsUpdate = false
            var updatedModel = provider.modelName
            var updatedDisplayName = provider.displayName

            if (provider.modelName == "nvidia/nemotron-3-super-120b-a12b" || (provider.id == "preset_nvidia" && provider.modelName.contains("nemotron-3"))) {
                updatedModel = "nvidia/llama-3.1-nemotron-70b-instruct"
                updatedDisplayName = "Nvidia Nemotron"
                needsUpdate = true
            }
            if (provider.modelName == "minimaxai/minimax-m3" || (provider.id == "preset_nvidia_minimax" && provider.modelName == "minimaxai/minimax-m3")) {
                updatedModel = "minimax/minicp-m3"
                updatedDisplayName = "Nvidia MiniMax M3"
                needsUpdate = true
            }

            if (needsUpdate) {
                try {
                    repository.updateProvider(
                        provider.copy(
                            modelName = updatedModel,
                            displayName = updatedDisplayName
                        )
                    )
                    android.util.Log.i("SettingsViewModel", "Auto-upgraded deprecated provider model for ${provider.id} to $updatedModel")
                } catch (e: Exception) {
                    android.util.Log.e("SettingsViewModel", "Failed to auto-upgrade deprecated provider model for ${provider.id}", e)
                }
            }
        }

        // Fetch refreshed list of current providers for ID checking
        val refreshedProvList = repository.getAllProviders()
        val currentIds = refreshedProvList.map { it.id }.toSet()

        val presets = listOf(
            LlmProviderModel(
                id = "preset_openai",
                displayName = "My OpenAI",
                baseUrl = "https://api.openai.com/v1",
                encryptedApiKey = com.example.core.common.ProviderValidator.KEY_SENTINEL,
                modelName = "gpt-4o",
                isActive = refreshedProvList.isEmpty()
            ),
            LlmProviderModel(
                id = "preset_gemini",
                displayName = "Google Gemini Pro",
                baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai/",
                encryptedApiKey = com.example.core.common.ProviderValidator.KEY_SENTINEL,
                modelName = "gemini-1.5-pro",
                isActive = false
            ),
            LlmProviderModel(
                id = "preset_ollama",
                displayName = "Ollama Localhost",
                baseUrl = "http://10.0.2.2:11434/v1",
                encryptedApiKey = "ollama",
                modelName = "llama3.2",
                isActive = false
            ),
            LlmProviderModel(
                id = "preset_nvidia",
                displayName = "Nvidia Nemotron",
                baseUrl = "https://integrate.api.nvidia.com/v1",
                encryptedApiKey = com.example.core.common.ProviderValidator.KEY_SENTINEL,
                modelName = "nvidia/llama-3.1-nemotron-70b-instruct",
                isActive = false
            ),
            LlmProviderModel(
                id = "preset_nvidia_minimax",
                displayName = "Nvidia MiniMax M3",
                baseUrl = "https://integrate.api.nvidia.com/v1",
                encryptedApiKey = com.example.core.common.ProviderValidator.KEY_SENTINEL,
                modelName = "minimax/minicp-m3",
                isActive = false
            ),
            LlmProviderModel(
                id = "preset_deepseek",
                displayName = "DeepSeek Chat",
                baseUrl = "https://api.deepseek.com/v1",
                encryptedApiKey = com.example.core.common.ProviderValidator.KEY_SENTINEL,
                modelName = "deepseek-chat",
                isActive = false
            )
        )

        for (preset in presets) {
            if (!currentIds.contains(preset.id)) {
                val validationResult = com.example.core.common.ProviderValidator.validate(preset.baseUrl, preset.encryptedApiKey)
                if (validationResult is com.example.core.common.ProviderValidator.ValidationResult.Invalid) {
                    android.util.Log.e("SettingsViewModel", "Skipping corrupted preset ${preset.displayName}: ${validationResult.reason}")
                    continue
                }
                try {
                    repository.insertProvider(preset)
                } catch (e: Exception) {
                    android.util.Log.e("SettingsViewModel", "Failed to insert preset ${preset.displayName}", e)
                }
            }
        }

        // Post-insert verification
        val postInsertedList = repository.getAllProviders()
        for (inserted in postInsertedList) {
            val valResult = com.example.core.common.ProviderValidator.validate(inserted.baseUrl, inserted.encryptedApiKey)
            if (valResult is com.example.core.common.ProviderValidator.ValidationResult.Invalid) {
                android.util.Log.e("SettingsViewModel", "Verification FAILED for provider ${inserted.displayName}: ${valResult.reason}")
                repository.deleteProviderById(inserted.id)
            }
        }

        val currentServers = repository.getAllServers()
        if (currentServers.isEmpty()) {
            repository.insertServer(McpServerModel(
                id = "server_files",
                name = "Filesystem MCP",
                endpoint = "http://10.0.2.2:3000",
                transport = "HTTP",
                isEnabled = true
            ))
            repository.insertServer(McpServerModel(
                id = "server_github",
                name = "GitHub MCP",
                endpoint = "https://mcp.github.com",
                transport = "SSE",
                isEnabled = false
            ))
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            repository.saveSetting("onboarding_completed", "true")
            _onboardingCompleted.value = true
            val activePrv = repository.getAllProviders().firstOrNull { it.isActive }
            val provider = activePrv?.id ?: "preset_openai"
            val newSession = ChatSessionModel(
                id = java.util.UUID.randomUUID().toString(),
                title = "New Conversation",
                providerId = provider
            )
            repository.insertSession(newSession)
        }
    }

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        viewModelScope.launch {
            repository.saveSetting("theme_mode", mode)
        }
    }

    // Onboarding welcome integration helpers
    fun addProvider(name: String, url: String, key: String, model: String): Boolean {
        val trimmedUrl = url.trim()
        val trimmedKey = key.trim()
        val trimmedName = name.trim()
        val trimmedModel = model.trim()

        val validationResult = com.example.core.common.ProviderValidator.validate(trimmedUrl, trimmedKey)
        if (validationResult is com.example.core.common.ProviderValidator.ValidationResult.Invalid) {
            showToast("Error: ${validationResult.reason}")
            return false
        }

        viewModelScope.launch {
            val updatedUrl = if (trimmedUrl.endsWith("/")) trimmedUrl else "$trimmedUrl/"
            val provider = LlmProviderModel(
                id = java.util.UUID.randomUUID().toString(),
                displayName = trimmedName.ifBlank { "Custom Ai" },
                baseUrl = updatedUrl,
                encryptedApiKey = trimmedKey,
                modelName = trimmedModel.ifBlank { "gpt-4o" }
            )
            repository.insertProvider(provider)
            // Make newly added provider active
            val prvList = repository.getAllProviders()
            for (prv in prvList) {
                repository.updateProvider(prv.copy(isActive = prv.id == provider.id))
            }
            showToast("Added provider: ${provider.displayName}")
        }
        return true
    }

    suspend fun testProviderConnection(provider: LlmProviderModel): Result<String> {
        val trimmedUrl = provider.baseUrl.trim()
        if (!com.example.core.common.Validator.isValidUrl(trimmedUrl)) {
            return Result.failure(Exception("Error: Invalid Provider URL!"))
        }
        return llmService.testConnection(provider)
    }

    fun updateSystemParameters(
        prompt: String,
        temp: Float,
        maxToks: Int,
        topP: Float,
        topK: Int,
        presencePenalty: Float,
        frequencyPenalty: Float
    ) {
        viewModelScope.launch {
            _systemPrompt.value = prompt
            _temperature.value = temp
            _maxTokens.value = maxToks
            _topP.value = topP
            _topK.value = topK
            _presencePenalty.value = presencePenalty
            _frequencyPenalty.value = frequencyPenalty
            repository.saveSetting("system_prompt", prompt)
            repository.saveSetting("temperature", temp.toString())
            repository.saveSetting("max_tokens", maxToks.toString())
            repository.saveSetting("top_p", topP.toString())
            repository.saveSetting("top_k", topK.toString())
            repository.saveSetting("presence_penalty", presencePenalty.toString())
            repository.saveSetting("frequency_penalty", frequencyPenalty.toString())
            showToast("System boundaries updated successfully.")
        }
    }

    fun updateCompressionParameters(enabled: Boolean, threshold: Float, keep: Int) {
        viewModelScope.launch {
            _autoCompressEnabled.value = enabled
            _compressThreshold.value = threshold
            _keepLastN.value = keep
            repository.saveSetting("auto_compress", enabled.toString())
            repository.saveSetting("compress_threshold", threshold.toString())
            repository.saveSetting("keep_last_n", keep.toString())
            showToast("Auto Compress behaviors updated.")
        }
    }

    // Memories block
    fun addManualMemory(content: String, type: String = "fact") {
        viewModelScope.launch {
            val memory = MemoryModel(
                id = java.util.UUID.randomUUID().toString(),
                content = content,
                type = type
            )
            repository.insertMemory(memory)
            showToast("Memory saved successfully.")
        }
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch {
            repository.deleteMemoryById(id)
        }
    }

    fun clearAllMemories() {
        viewModelScope.launch {
            repository.clearAllMemories()
            showToast("All memories deleted.")
        }
    }

    // Supabase Setup triggers
    fun triggerSupabaseSetup(url: String, serviceRoleKey: String, pat: String) {
        viewModelScope.launch {
            _supabaseUrl.value = url.trim()
            _supabaseServiceRoleKey.value = serviceRoleKey.trim()
            _supabasePat.value = pat.trim()

            _supabaseLog.value = emptyList()
            supabaseService.runAutoSetup(url.trim(), serviceRoleKey.trim(), pat.trim()).collect { step ->
                _supabaseLog.value = _supabaseLog.value + step
                if (step.startsWith("DONE") || step.startsWith("SUCCESS: Setup complete.")) {
                    _isSupabaseConfigured.value = true
                    _supabaseSyncEnabled.value = true
                    repository.saveSetting("supabase_sync", "true")
                }
            }
        }
    }

    fun toggleSupabaseSync() {
        viewModelScope.launch {
            val nextState = !_supabaseSyncEnabled.value
            _supabaseSyncEnabled.value = nextState
            repository.saveSetting("supabase_sync", nextState.toString())
            showToast(if (nextState) "Cloud synchronization is enabled." else "Cloud sync was deactivated.")
        }
    }

    fun triggerManualCloudSync() {
        viewModelScope.launch {
            val url = securePrefs.getUrl()
            val serviceRoleKey = securePrefs.getServiceRoleKey()
            if (url.isBlank() || serviceRoleKey.isBlank()) {
                showToast("Please configure the Project URL and Service Role Key first!")
                return@launch
            }

            _supabaseLog.value = emptyList()
            val sessionsCount = repository.allSessions.firstOrNull()?.size ?: 0
            supabaseService.runSyncConflictsResolution(sessionsCount).collect { step ->
                _supabaseLog.value = _supabaseLog.value + step
            }
            showToast("Cloud synchronization complete!")
        }
    }

    // Export database backup
    suspend fun exportDatabaseBackup(): String {
        return withContext(Dispatchers.IO) {
            try {
                repository.exportDatabaseBackup()
            } catch (e: Exception) {
                ""
            }
        }
    }

    suspend fun importDatabaseBackup(jsonStr: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                repository.importDatabaseBackup(jsonStr)
                loadConfiguredSettings()
                true
            } catch (e: BackupCorruptedException) {
                withContext(Dispatchers.Main) {
                    showToast("❌ Refused: ${e.message}")
                }
                false
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("❌ Refused: Backup file is corrupted or incomplete")
                }
                false
            }
        }
    }
}
