package com.example.core.data.service

import com.example.core.domain.models.Message
import com.example.core.domain.models.LlmProviderModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

sealed class LlmChunk {
    data class Text(val content: String) : LlmChunk()
    data class ToolCall(val id: String, val name: String, val arguments: String) : LlmChunk()
    data class Error(val message: String) : LlmChunk()
    object Done : LlmChunk()
}

class LlmService(private val client: OkHttpClient) {

    // Test a provider's connection
    suspend fun testConnection(provider: LlmProviderModel): Result<String> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        try {
            val url = if (provider.baseUrl.contains("/chat/completions")) {
                provider.baseUrl
            } else if (provider.baseUrl.endsWith("/")) {
                "${provider.baseUrl}chat/completions"
            } else {
                "${provider.baseUrl}/chat/completions"
            }

            val requestBodyJson = JSONObject().apply {
                put("model", provider.modelName)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "ping")
                    })
                })
                put("max_tokens", 5)
            }

            val apiKey = provider.encryptedApiKey.trim()
            val authHeader = if (apiKey.startsWith("Bearer ", ignoreCase = true)) apiKey else "Bearer $apiKey"

            val request = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", authHeader)
                .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success("Success: Connection valid.")
                } else {
                    val errorBody = response.body?.string() ?: ""
                    Result.failure(Exception("HTTP Error ${response.code}: $errorBody"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Stream completions from any OpenAI-compatible provider
    fun streamChat(
        provider: LlmProviderModel,
        history: List<Message>,
        systemPrompt: String = "You are a helpful assistant.",
        temperature: Double = 0.7,
        topP: Double = 0.9,
        topK: Int = 40,
        presencePenalty: Double = 0.0,
        frequencyPenalty: Double = 0.0,
        maxTokens: Int = 4096,
        toolsJson: String? = null, // Optional list of tools parameter
        context: android.content.Context? = null
    ): Flow<LlmChunk> = flow {
        try {
            val url = if (provider.baseUrl.contains("/chat/completions")) {
                provider.baseUrl
            } else if (provider.baseUrl.endsWith("/")) {
                "${provider.baseUrl}chat/completions"
            } else {
                "${provider.baseUrl}/chat/completions"
            }

            val messagesArray = JSONArray().apply {
                // Add System Prompt
                if (systemPrompt.isNotEmpty()) {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                }
                // Add Past History
                for (msg in history) {
                    put(JSONObject().apply {
                        put("role", msg.role)
                        val contentText = msg.content
                        if (context != null && contentText.startsWith("[IMAGE: ") && contentText.contains("]")) {
                            val closingIndex = contentText.indexOf("]")
                            val uriString = contentText.substring("[IMAGE: ".length, closingIndex)
                            val textPrompt = contentText.substring(closingIndex + 1).trim()
                            try {
                                val uri = android.net.Uri.parse(uriString)
                                val inputStream = context.contentResolver.openInputStream(uri)
                                val bytes = inputStream?.readBytes()
                                inputStream?.close()
                                if (bytes != null) {
                                    val base64Str = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                                    val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                                    
                                    val contentArray = JSONArray().apply {
                                        put(JSONObject().apply {
                                            put("type", "text")
                                            put("text", textPrompt)
                                        })
                                        put(JSONObject().apply {
                                            put("type", "image_url")
                                            put("image_url", JSONObject().apply {
                                                put("url", "data:$mimeType;base64,$base64Str")
                                            })
                                        })
                                    }
                                    put("content", contentArray)
                                } else {
                                    put("content", textPrompt)
                                }
                            } catch (e: Exception) {
                                put("content", textPrompt)
                            }
                        } else {
                            put("content", msg.content)
                        }
                    })
                }
            }

            val requestBodyJson = JSONObject().apply {
                put("model", provider.modelName)
                put("messages", messagesArray)
                put("temperature", temperature)
                put("top_p", topP)
                
                val omitTopK = provider.baseUrl.contains("openai.com") || 
                               provider.baseUrl.contains("nvidia.com") || 
                               provider.baseUrl.contains("deepseek") || 
                               provider.baseUrl.contains("groq.com") || 
                               provider.baseUrl.contains("perplexity") ||
                               provider.modelName.contains("minimax", ignoreCase = true)
                if (topK > 0 && !omitTopK) {
                    put("top_k", topK)
                }

                put("presence_penalty", presencePenalty)
                put("frequency_penalty", frequencyPenalty)
                put("max_tokens", maxTokens)
                put("stream", true)

                // Inject active tools if present
                if (!toolsJson.isNullOrBlank()) {
                    try {
                        val toolsArray = JSONArray(toolsJson)
                        if (toolsArray.length() > 0) {
                            put("tools", toolsArray)
                        }
                    } catch (e: Exception) {
                        // ignore malformed tools JSON
                    }
                }
            }

            val apiKey = provider.encryptedApiKey.trim()
            val authHeader = if (apiKey.startsWith("Bearer ", ignoreCase = true)) apiKey else "Bearer $apiKey"

            val request = Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", authHeader)
                .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorMsg = response.body?.string() ?: "Unknown error"
                emit(LlmChunk.Error("HTTP Error ${response.code}: $errorMsg"))
                return@flow
            }

            val responseBody = response.body
            if (responseBody == null) {
                emit(LlmChunk.Error("Response body is empty"))
                return@flow
            }

            val reader = BufferedReader(InputStreamReader(responseBody.byteStream()))
            var line: String?

            // Tool call accumulator state
            var isBuildingToolCall = false
            var toolCallId = ""
            var toolCallName = ""
            val jsonAccumulator = JsonStreamAccumulator()

            while (reader.readLine().also { line = it } != null) {
                val currentLine = line?.trim() ?: continue
                if (currentLine.isEmpty()) continue
                if (!currentLine.startsWith("data:")) continue

                val dataContent = currentLine.substring(5).trim()
                if (dataContent == "[DONE]") {
                    break
                }

                try {
                    val jsonObj = JSONObject(dataContent)
                    val choices = jsonObj.optJSONArray("choices")
                    if (choices != null && choices.length() > 0) {
                        val choice = choices.getJSONObject(0)
                        val delta = choice.optJSONObject("delta")
                        
                        if (delta != null) {
// Check for content tokens safely to avoid JSONObject native "null" string representation
                            if (delta.has("content") && !delta.isNull("content")) {
                                val content = delta.getString("content")
                                if (content.isNotEmpty() && content != "null") {
                                    emit(LlmChunk.Text(content))
                                }
                            }

                            // Check for streaming tool calls
                            val toolCalls = delta.optJSONArray("tool_calls")
                            if (toolCalls != null && toolCalls.length() > 0) {
                                val tCall = toolCalls.getJSONObject(0)
                                isBuildingToolCall = true
                                
                                val id = tCall.optString("id")
                                if (id.isNotEmpty()) {
                                    toolCallId = id
                                }

                                val function = tCall.optJSONObject("function")
                                if (function != null) {
                                    val name = function.optString("name")
                                    if (name.isNotEmpty()) {
                                        toolCallName = name
                                    }
                                    val arguments = function.optString("arguments")
                                    if (arguments.isNotEmpty()) {
                                        jsonAccumulator.append(arguments)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Skip lines that aren't valid JSON (e.g. metadata)
                }
            }

            responseBody.close()

            if (isBuildingToolCall && toolCallName.isNotEmpty()) {
                val finalArgs = try {
                    jsonAccumulator.getValidatedJson().toString()
                } catch (e: Exception) {
                    // Safe fallback to raw if completamente unparseable
                    jsonAccumulator.getRawString()
                }
                emit(LlmChunk.ToolCall(
                    id = toolCallId.ifEmpty { "call_" + System.currentTimeMillis() },
                    name = toolCallName,
                    arguments = finalArgs
                ))
            } else {
                emit(LlmChunk.Done)
            }

        } catch (e: Exception) {
            emit(LlmChunk.Error("Network error: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)
}

