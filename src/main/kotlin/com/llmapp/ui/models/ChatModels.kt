package com.llmapp.ui.models

data class ChatMessageUI(
    val role: String,
    val content: String,
    val metadata: String? = null
)

enum class Screen {
    Chat, Models, Settings
}
