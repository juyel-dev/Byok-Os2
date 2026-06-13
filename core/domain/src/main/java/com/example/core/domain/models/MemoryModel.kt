package com.example.core.domain.models

data class MemoryModel(
    val id: String,
    val content: String,
    val type: String = "fact",
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)
