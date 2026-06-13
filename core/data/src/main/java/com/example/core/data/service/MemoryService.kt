package com.example.core.data.service

import com.example.core.domain.models.MemoryModel
import java.util.UUID

class MemoryService {
    // Regex matches to parse user language patterns and capture facts/preferences automatically
    fun extractMemories(text: String): List<MemoryModel> {
        val memories = mutableListOf<MemoryModel>()
        val lowercase = text.lowercase()

        // 1. Name matching
        val nameRegex = "my name is ([a-zA-Z\\s]{2,15})".toRegex(RegexOption.IGNORE_CASE)
        nameRegex.find(text)?.let { match ->
            val name = match.groupValues[1].trim()
            memories.add(MemoryModel(
                id = UUID.randomUUID().toString(),
                content = "User's name is $name",
                type = "fact"
            ))
        }

        // 2. Tech stacks and work
        val workRegex = "i work (?:on|with) ([a-zA-Z\\d\\s\\.\\#\\+]{2,15})".toRegex(RegexOption.IGNORE_CASE)
        workRegex.find(text)?.let { match ->
            val tech = match.groupValues[1].trim()
            memories.add(MemoryModel(
                id = UUID.randomUUID().toString(),
                content = "Works on $tech",
                type = "preference"
            ))
        }

        // 3. Location settings
        val liveRegex = "i live in ([a-zA-Z\\s]{2,20})|i'm located in ([a-zA-Z\\s]{2,20})".toRegex(RegexOption.IGNORE_CASE)
        liveRegex.find(text)?.let { match ->
            val firstGroup = match.groupValues[1].trim()
            val secondGroup = match.groupValues[2].trim()
            val location = firstGroup.ifEmpty { secondGroup }
            if (location.isNotEmpty()) {
                memories.add(MemoryModel(
                    id = UUID.randomUUID().toString(),
                    content = "Located in $location",
                    type = "fact"
                ))
            }
        }

        // 4. Instructions "remember that..."
        val rememberRegex = "remember that i ([a-zA-Z\\d\\s]{3,40})".toRegex(RegexOption.IGNORE_CASE)
        rememberRegex.find(text)?.let { match ->
            val fact = match.groupValues[1].trim()
            memories.add(MemoryModel(
                id = UUID.randomUUID().toString(),
                content = "User $fact",
                type = "preference"
            ))
        }

        return memories
    }
}
