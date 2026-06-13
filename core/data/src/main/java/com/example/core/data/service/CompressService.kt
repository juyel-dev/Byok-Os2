package com.example.core.data.service

import com.example.core.domain.models.Message
import java.util.UUID

class CompressService {
    // Basic word-based token estimator (Rough tiktoken approximation of 4 characters ~ 1 token, or 1 word ~ 1.3 tokens)
    fun estimateTokens(text: String): Int {
        if (text.isBlank()) return 0
        val words = text.trim().split("\\s+".toRegex())
        return (words.size * 1.3).toInt()
    }

    // Compresses older messages in a list into an compact meta-summary message
    fun compressMessages(
        messages: List<Message>,
        keepLastN: Int = 10
    ): Pair<Message, List<Message>> {
        if (messages.size <= keepLastN) {
            throw IllegalArgumentException("Message history size is smaller than keep count, compression not needed.")
        }

        val compressCount = messages.size - keepLastN
        val compressionTargets = messages.take(compressCount)
        val keptMessages = messages.drop(compressCount)

        // Build a highly compact system summary based on the older pruned messages
        val keyTopics = mutableSetOf<String>()
        compressionTargets.forEach { msg ->
            if (msg.role == "user") {
                val words = msg.content.trim().split("\\s+".toRegex()).take(3)
                if (words.isNotEmpty()) {
                    keyTopics.add(words.joinToString(" "))
                }
            }
        }

        val topicsStr = if (keyTopics.isNotEmpty()) {
            keyTopics.joinToString(", ") { "'$it'" }
        } else {
            "Development topics"
        }

        val summaryContent = "📦 [COMPRESSED HISTORIC CONTEXT] — The first $compressCount messages of this session were archived to optimize context space. Key discussion vectors touched upon: $topicsStr."

        val summaryMessage = Message(
            id = UUID.randomUUID().toString(),
            sessionId = messages.first().sessionId,
            role = "system",
            content = summaryContent,
            timestamp = System.currentTimeMillis() - 1000 // Ensure it sits just before the kept history
        )

        return Pair(summaryMessage, keptMessages)
    }
}
