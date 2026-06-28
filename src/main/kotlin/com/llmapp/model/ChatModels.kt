package com.llmapp.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val role: String,
    val content: String
)

data class ModelInfo(
    val id: String,
    val name: String,
    val description: String
)

val freeModels: List<ModelInfo> get() = ModelList.modelInfos
