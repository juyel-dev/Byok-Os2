package com.example.core.common

import java.net.URL

object ProviderValidator {
    const val KEY_SENTINEL = "UNCONFIGURED"

    fun isValidUrl(url: String): Boolean {
        if (url.isBlank()) return false
        return try {
            val parsedUrl = URL(url)
            val protocol = parsedUrl.protocol
            val host = parsedUrl.host
            (protocol == "http" || protocol == "https") && host.isNotBlank()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if a decrypted API key is fully configured.
     */
    fun isConfigured(key: String): Boolean {
        val trimmed = key.trim()
        return trimmed.isNotEmpty() && trimmed != KEY_SENTINEL
    }

    /**
     * Checks if a decrypted API key is unconfigured.
     */
    fun isUnconfigured(key: String): Boolean {
        return key.trim() == KEY_SENTINEL
    }

    /**
     * Checks if a decrypted API key is in an invalid state.
     */
    fun isInvalidKey(key: String): Boolean {
        return key.trim().isEmpty()
    }

    /**
     * Validates whether a provider's settings would be valid.
     */
    fun validate(baseUrl: String, decryptedApiKey: String): ValidationResult {
        if (!isValidUrl(baseUrl)) {
            return ValidationResult.Invalid("Invalid Provider URL format.")
        }
        val trimmedKey = decryptedApiKey.trim()
        if (trimmedKey.isEmpty()) {
            return ValidationResult.Invalid("API Key cannot be blank.")
        }
        if (trimmedKey == KEY_SENTINEL) {
            return ValidationResult.Unconfigured
        }
        return ValidationResult.Valid
    }

    sealed class ValidationResult {
        object Valid : ValidationResult()
        object Unconfigured : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }
}
