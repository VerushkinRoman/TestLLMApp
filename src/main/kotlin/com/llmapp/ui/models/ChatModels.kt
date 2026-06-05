package com.llmapp.ui.models

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
data class ChatMessageUI(
    val id: String = Uuid.random().toString(),
    val role: String,
    val content: String,
    val metadata: String? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val responseTimeMs: Long? = null
) {
    fun getFormattedResponseTime(): String? {
        return responseTimeMs?.let { ms ->
            when {
                ms < 1000 -> "${ms}ms"
                ms < 60000 -> "${ms / 1000}.${(ms % 1000) / 100}s"
                else -> "${ms / 60000}m ${(ms % 60000) / 1000}s"
            }
        }
    }
}

enum class Screen {
    Chat, Models, Settings
}
