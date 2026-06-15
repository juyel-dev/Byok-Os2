package com.example.feature.settings.viewmodel

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.domain.models.*
import com.example.core.domain.repository.ChatRepository
import com.example.core.data.service.LlmService
import com.example.core.common.Validator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ProviderViewModel(
    application: Application,
    private val repository: ChatRepository,
    private val llmService: LlmService
) : AndroidViewModel(application) {

    val providers: StateFlow<List<LlmProviderModel>> = repository.allProvidersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    val selectedProviderId: StateFlow<String?> = repository.allProvidersFlow
        .map { list -> list.firstOrNull { it.isActive }?.id ?: list.firstOrNull()?.id }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val themeMode: StateFlow<String> = flow {
        emit(repository.getSetting("theme_mode", "DARK"))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "DARK")

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

    fun selectProvider(providerId: String) {
        viewModelScope.launch {
            val prvList = repository.getAllProviders()
            for (prv in prvList) {
                repository.updateProvider(prv.copy(isActive = prv.id == providerId))
            }
        }
    }

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
            selectProvider(provider.id)
            showToast("Added provider: ${provider.displayName}")
        }
        return true
    }

    fun updateProviderKey(providerId: String, newKey: String): Boolean {
        val trimmedKey = newKey.trim()
        if (trimmedKey.isEmpty()) {
            showToast("Error: API Key cannot be blank!")
            return false
        }
        viewModelScope.launch {
            val list = repository.getAllProviders()
            val target = list.firstOrNull { it.id == providerId }
            if (target != null) {
                val validationResult = com.example.core.common.ProviderValidator.validate(target.baseUrl, trimmedKey)
                if (validationResult is com.example.core.common.ProviderValidator.ValidationResult.Invalid) {
                    showToast("Error: ${validationResult.reason}")
                    return@launch
                }
                val updated = target.copy(encryptedApiKey = trimmedKey)
                repository.updateProvider(updated)
                showToast("Updated API Key for ${target.displayName}")
            }
        }
        return true
    }

    fun updateProviderDetails(providerId: String, name: String, url: String, key: String, model: String): Boolean {
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
            val list = repository.getAllProviders()
            val target = list.firstOrNull { it.id == providerId }
            if (target != null) {
                val updatedUrl = if (trimmedUrl.endsWith("/")) trimmedUrl else "$trimmedUrl/"
                val updated = target.copy(
                    displayName = trimmedName.ifBlank { target.displayName },
                    baseUrl = updatedUrl,
                    encryptedApiKey = trimmedKey,
                    modelName = trimmedModel.ifBlank { target.modelName }
                )
                repository.updateProvider(updated)
                showToast("Updated details for ${updated.displayName}")
            }
        }
        return true
    }

    fun deleteProvider(id: String) {
        viewModelScope.launch {
            repository.deleteProviderById(id)
            val left = repository.getAllProviders()
            if (left.isNotEmpty()) {
                selectProvider(left.first().id)
            }
        }
    }

    suspend fun testProviderConnection(provider: LlmProviderModel): Result<String> {
        val trimmedUrl = provider.baseUrl.trim()
        if (!com.example.core.common.ProviderValidator.isValidUrl(trimmedUrl)) {
            return Result.failure(Exception("Error: Invalid Provider URL!"))
        }
        return llmService.testConnection(provider)
    }
}
