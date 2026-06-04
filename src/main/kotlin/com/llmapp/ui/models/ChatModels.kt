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
    val totalTokens: Int? = null
)

enum class Screen {
    Chat, Models, Settings
}