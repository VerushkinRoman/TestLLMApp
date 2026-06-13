package com.llmapp.ui.models

data class ChatMessageUI(
    val id: String,
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
                else -> "${ms / 60000}м ${(ms % 60000) / 1000}s"
            }
        }
    }
}

enum class Screen {
    Chat, Models, Settings, Agents, Demo
}
