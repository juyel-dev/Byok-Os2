package com.example.feature.settings.viewmodel

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.domain.models.*
import com.example.core.domain.repository.ChatRepository
import com.example.core.data.service.*
import com.example.core.common.Validator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class McpViewModel(
    application: Application,
    private val repository: ChatRepository,
    private val mcpService: McpService
) : AndroidViewModel(application) {

    val mcpServers: StateFlow<List<McpServerModel>> = repository.allServersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mcpServerHealths: StateFlow<Map<String, McpHealthStatus>> = mcpService.serverHealths
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _mcpSandboxNetworkAllowed = MutableStateFlow(true)
    val mcpSandboxNetworkAllowed: StateFlow<Boolean> = _mcpSandboxNetworkAllowed.asStateFlow()

    private val _mcpSandboxFilesystemAllowed = MutableStateFlow(true)
    val mcpSandboxFilesystemAllowed: StateFlow<Boolean> = _mcpSandboxFilesystemAllowed.asStateFlow()

    private val _mcpSandboxNotificationsAllowed = MutableStateFlow(true)
    val mcpSandboxNotificationsAllowed: StateFlow<Boolean> = _mcpSandboxNotificationsAllowed.asStateFlow()

    val mcpExecutionLogs = mcpService.executionLogs

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    init {
        viewModelScope.launch {
            _mcpSandboxNetworkAllowed.value = repository.getSetting("mcp_sandbox_network", "true").toBoolean()
            _mcpSandboxFilesystemAllowed.value = repository.getSetting("mcp_sandbox_filesystem", "true").toBoolean()
            _mcpSandboxNotificationsAllowed.value = repository.getSetting("mcp_sandbox_notifications", "true").toBoolean()
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

    fun addMcpServer(name: String, endpoint: String, transport: String) {
        val trimmedName = name.trim()
        val trimmedEndpoint = endpoint.trim()
        val trimmedTransport = transport.trim()

        if (trimmedName.isBlank()) {
            showToast("Error: MCP Server name cannot be empty!")
            return
        }
        if (!Validator.isValidUrl(trimmedEndpoint)) {
            showToast("Error: Invalid MCP Server endpoint URL!")
            return
        }

        viewModelScope.launch {
            val server = McpServerModel(
                id = java.util.UUID.randomUUID().toString(),
                name = trimmedName,
                endpoint = trimmedEndpoint,
                transport = trimmedTransport.ifBlank { "SSE" },
                isEnabled = true
            )
            repository.insertServer(server)
            showToast("MCP Server configured.")
        }
    }

    fun toggleMcpServer(server: McpServerModel) {
        viewModelScope.launch {
            repository.updateServer(server.copy(isEnabled = !server.isEnabled))
            showToast("Toggled ${server.name}")
        }
    }

    fun deleteMcpServer(id: String) {
        viewModelScope.launch {
            repository.deleteServerById(id)
        }
    }

    fun updateMcpSandboxPermissions(network: Boolean, filesystem: Boolean, notifications: Boolean) {
        viewModelScope.launch {
            _mcpSandboxNetworkAllowed.value = network
            _mcpSandboxFilesystemAllowed.value = filesystem
            _mcpSandboxNotificationsAllowed.value = notifications
            repository.saveSetting("mcp_sandbox_network", network.toString())
            repository.saveSetting("mcp_sandbox_filesystem", filesystem.toString())
            repository.saveSetting("mcp_sandbox_notifications", notifications.toString())
            _toastMessage.value = "MCP Sandbox settings saved successfully."
        }
    }
}
