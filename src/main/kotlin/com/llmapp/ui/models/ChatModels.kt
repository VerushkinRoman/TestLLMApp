package com.llmapp.ui.models

data class ChatMessageUI(
    val role: String,
    val content: String,
    val metadata: String? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null
)

enum class Screen {
    Chat, Models, Settings
}
