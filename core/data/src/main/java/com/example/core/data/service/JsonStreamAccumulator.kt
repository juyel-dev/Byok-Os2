package com.example.core.data.service

import org.json.JSONObject
import org.json.JSONArray

// Structured Error System
sealed class McpStreamException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class StreamingParseFailure(
        message: String,
        val chunkIndex: Int,
        val parserState: String,
        val failurePosition: Int,
        val unmatchedStructureInfo: String
    ) : McpStreamException(
        "Streaming parsing failed at chunk $chunkIndex (pos: $failurePosition): $message. State: $parserState. Unmatched: $unmatchedStructureInfo"
    )

    class MalformedToolArguments(
        message: String,
        val rawArguments: String
    ) : McpStreamException("Malformed tool arguments JSON: $message. Got: '$rawArguments'")

    class BufferExceeded(
        message: String
    ) : McpStreamException(message)
}

data class StreamTelemetry(
    var parseSuccess: Boolean = false,
    var repairAttempted: Boolean = false,
    var repairSuccessful: Boolean = false,
    var chunkCount: Int = 0,
    var streamDurationMs: Long = 0,
    var malformedPayloadStatsCount: Int = 0,
    var totalBytesProcessed: Int = 0
)

class JsonStreamAccumulator(
    val maxBufferSize: Int = 1024 * 1024, // 1MB buffer limit
    val chunkLimit: Int = 10000            // 10K chunk limit
) {
    private val buffer = StringBuilder()
    private var braceDepth = 0
    private var bracketDepth = 0
    private var inQuotes = false
    private var isEscaped = false
    private var chunkCount = 0
    private val startTime = System.currentTimeMillis()
    val telemetry = StreamTelemetry()

    @Synchronized
    fun append(chunk: String) {
        if (chunkCount >= chunkLimit) {
            throw McpStreamException.BufferExceeded("Chunk limit of $chunkLimit exceeded.")
        }
        if (buffer.length + chunk.length > maxBufferSize) {
            throw McpStreamException.BufferExceeded("Buffer size limit of $maxBufferSize bytes exceeded.")
        }

        chunkCount++
        telemetry.chunkCount = chunkCount
        telemetry.totalBytesProcessed += chunk.length

        for (i in chunk.indices) {
            val c = chunk[i]
            val absolutePos = buffer.length + i

            if (isEscaped) {
                isEscaped = false
                buffer.append(c)
                continue
            }

            if (c == '\\') {
                isEscaped = true
                buffer.append(c)
                continue
            }

            if (c == '"') {
                inQuotes = !inQuotes
                buffer.append(c)
                continue
            }

            if (!inQuotes) {
                when (c) {
                    '{' -> braceDepth++
                    '}' -> {
                        braceDepth--
                        if (braceDepth < 0) {
                            telemetry.malformedPayloadStatsCount++
                            throw McpStreamException.StreamingParseFailure(
                                message = "Unmatched closing brace '}'",
                                chunkIndex = chunkCount,
                                parserState = getParserStateString(),
                                failurePosition = absolutePos,
                                unmatchedStructureInfo = "braceDepth became negative"
                            )
                        }
                    }
                    '[' -> bracketDepth++
                    ']' -> {
                        bracketDepth--
                        if (bracketDepth < 0) {
                            telemetry.malformedPayloadStatsCount++
                            throw McpStreamException.StreamingParseFailure(
                                message = "Unmatched closing bracket ']'",
                                chunkIndex = chunkCount,
                                parserState = getParserStateString(),
                                failurePosition = absolutePos,
                                unmatchedStructureInfo = "bracketDepth became negative"
                            )
                        }
                    }
                }
            }
            buffer.append(c)
        }
    }

    private fun getParserStateString(): String {
        return "braceDepth=$braceDepth, bracketDepth=$bracketDepth, inQuotes=$inQuotes, isEscaped=$isEscaped, bufferLength=${buffer.length}"
    }

    @Synchronized
    fun getRawString(): String {
        return buffer.toString()
    }

    @Synchronized
    fun getValidatedJson(): JSONObject {
        val raw = buffer.toString().trim()
        telemetry.streamDurationMs = System.currentTimeMillis() - startTime
        
        if (raw.isEmpty()) {
            return JSONObject()
        }

        try {
            val parsedResult = JSONObject(raw)
            telemetry.parseSuccess = true
            return parsedResult
        } catch (e: Exception) {
            telemetry.repairAttempted = true
            McpRegistryLogger.w("JsonStreamAccumulator", "Initial JSON parse failed: ${e.message}. Attempting repair...")
            
            val repairedString = lenientRepair(raw)
            McpRegistryLogger.i("JsonStreamAccumulator", "Repaired JSON string: $repairedString")
            
            try {
                val parsedResult = JSONObject(repairedString)
                telemetry.parseSuccess = true
                telemetry.repairSuccessful = true
                McpRegistryLogger.i("JsonStreamAccumulator", "Lenient repair succeeded.")
                return parsedResult
            } catch (e2: Exception) {
                telemetry.malformedPayloadStatsCount++
                McpRegistryLogger.e("JsonStreamAccumulator", "Repair failed: ${e2.message}")
                throw McpStreamException.MalformedToolArguments(
                    message = "Failed to parse or repair JSON: ${e2.message}",
                    rawArguments = raw
                )
            }
        }
    }

    fun lenientRepair(raw: String): String {
        var cleaned = raw.trim()

        if (cleaned.isEmpty()) return "{}"

        val firstBraceIdx = cleaned.indexOf('{')
        if (firstBraceIdx > 0) {
            cleaned = cleaned.substring(firstBraceIdx)
        }

        var localInQuotes = false
        var localIsEscaped = false
        var localBraceDepth = 0
        var localBracketDepth = 0

        val repairedSb = StringBuilder()

        for (i in cleaned.indices) {
            val c = cleaned[i]
            if (localIsEscaped) {
                localIsEscaped = false
                repairedSb.append(c)
                continue
            }
            if (c == '\\') {
                localIsEscaped = true
                repairedSb.append(c)
                continue
            }
            if (c == '"') {
                localInQuotes = !localInQuotes
                repairedSb.append(c)
                continue
            }
            if (!localInQuotes) {
                when (c) {
                    '{' -> localBraceDepth++
                    '}' -> {
                        localBraceDepth--
                        if (localBraceDepth < 0) {
                            localBraceDepth = 0
                            continue
                        }
                    }
                    '[' -> localBracketDepth++
                    ']' -> {
                        localBracketDepth--
                        if (localBracketDepth < 0) {
                            localBracketDepth = 0
                            continue
                        }
                    }
                }
            }
            repairedSb.append(c)
        }

        if (localInQuotes) {
            repairedSb.append('"')
        }

        var result = repairedSb.toString().trim()

        result = result.replace(Regex(",\\s*}"), "}")
        result = result.replace(Regex(",\\s*]"), "]")

        val finalSb = StringBuilder(result)
        while (localBracketDepth > 0) {
            finalSb.append(']')
            localBracketDepth--
        }
        while (localBraceDepth > 0) {
            finalSb.append('}')
            localBraceDepth--
        }

        var repaired = finalSb.toString().trim()

        if (repaired.endsWith(":")) {
            repaired += "null}"
        } else if (repaired.endsWith(",")) {
            repaired = repaired.substring(0, repaired.length - 1).trim()
            if (!repaired.endsWith("}") && !repaired.endsWith("]")) {
                repaired += "}"
            }
        }

        if (!repaired.startsWith("{")) {
            repaired = "{$repaired"
        }
        if (!repaired.endsWith("}")) {
            repaired = "$repaired}"
        }

        repaired = repaired.replace(Regex(",\\s*}"), "}")

        return repaired
    }
}
