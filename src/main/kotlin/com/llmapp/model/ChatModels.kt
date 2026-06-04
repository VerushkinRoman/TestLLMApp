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

val freeModels = listOf(
    ModelInfo(
        "nvidia/nemotron-3-super-120b-a12b:free",
        "NVIDIA Nemotron 3",
        "Мощная модель от NVIDIA, 120B параметров"
    ),
    ModelInfo("openrouter/owl-alpha", "Owl Alpha", "Отличная модель для общих задач"),
    ModelInfo(
        "poolside/laguna-m.1:free",
        "Poolside Laguna M.1",
        "Хороший баланс скорости и качества"
    ),
    ModelInfo("openai/gpt-oss-120b:free", "GPT-OSS 120B", "Open source альтернатива от OpenAI"),
    ModelInfo("z-ai/glm-4.5-air:free", "Z-AI GLM 4.5 Air", "Быстрая модель от Zhipu AI"),
    ModelInfo("poolside/laguna-xs.2:free", "Poolside Laguna XS.2", "Компактная и быстрая модель"),
    ModelInfo(
        "google/gemma-4-26b-a4b-it:free",
        "Google Gemma 4",
        "Современная модель от Google, 26B"
    )
)
