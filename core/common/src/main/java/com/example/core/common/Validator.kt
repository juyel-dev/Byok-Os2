package com.example.core.common

import java.net.URL

object Validator {
    /**
     * Checks if a URL is valid. Supports hostname, IP address, and custom ports.
     */
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
     * Checks if an API key is valid (not blank / empty).
     */
    fun isValidApiKey(key: String): Boolean {
        return !key.isBlank()
    }

    /**
     * Escapes critical characters to prevent raw JSON payload injection.
     */
    fun sanitizeJson(input: String): String {
        if (input.isBlank()) return ""
        val stringBuilder = StringBuilder()
        for (ch in input) {
            when (ch) {
                '\\' -> stringBuilder.append("\\\\")
                '\"' -> stringBuilder.append("\\\"")
                '/' -> stringBuilder.append("\\/")
                '\b' -> stringBuilder.append("\\b")
                '\n' -> stringBuilder.append("\\n")
                '\r' -> stringBuilder.append("\\r")
                '\t' -> stringBuilder.append("\\t")
                else -> {
                    if (ch.code < 32 || ch.code in 127..159) {
                        val hex = Integer.toHexString(ch.code)
                        stringBuilder.append("\\u")
                        stringBuilder.append("0".repeat(4 - hex.length))
                        stringBuilder.append(hex)
                    } else {
                        stringBuilder.append(ch)
                    }
                }
            }
        }
        return stringBuilder.toString()
    }

    /**
     * Sanitizes raw string fields (such as model response prompts or messages)
     * by filtering out harmful low-level control characters, keeping valid newlines,
     * tabs, and multi-language characters intact.
     */
    fun sanitizeInput(input: String): String {
        if (input.isBlank()) return ""
        return input.filter { ch ->
            val code = ch.code
            code == 10 || code == 13 || code == 9 || (code in 32..126) || (code >= 160)
        }
    }
}
