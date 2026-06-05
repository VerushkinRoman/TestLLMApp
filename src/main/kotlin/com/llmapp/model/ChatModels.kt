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
        "NVIDIA Nemotron 3 Super",
        "120B MoE, 12B активных, 1M контекста. Лучшая для сложных агентных задач"
    ),
    ModelInfo(
        "nvidia/nemotron-3-ultra-550b-a55b:free",
        "NVIDIA Nemotron 3 Ultra",
        "550B MoE, 55B активных, 1M контекста. Фронтир-рассуждение и оркестрация"
    ),
    ModelInfo(
        "nvidia/nemotron-3-nano-30b-a3b:free",
        "NVIDIA Nemotron 3 Nano 30B",
        "30B MoE (3B активных), 256K контекста. Макс. эффективность"
    ),
    ModelInfo(
        "nvidia/nemotron-3-nano-omni:free",
        "NVIDIA Nemotron 3 Nano Omni",
        "30B-A3B мультимодальная (текст, изображение, видео, аудио), 300K контекста"
    ),
    ModelInfo(
        "nvidia/nemotron-nano-9b-v2:free",
        "NVIDIA Nemotron Nano 9B V2",
        "9B dense, 128K контекста. Reasoning + non-reasoning в одной модели"
    ),
    ModelInfo(
        "nvidia/nemotron-nano-12b-2-vl:free",
        "NVIDIA Nemotron Nano 12B 2 VL",
        "12B мультимодальная, 128K контекста. Лучшая для видео и OCR"
    ),
    ModelInfo(
        "openai/gpt-oss-120b:free",
        "OpenAI GPT-OSS 120B",
        "117B MoE (5.1B активных), 131K контекста. Configurable reasoning depth"
    ),
    ModelInfo(
        "openai/gpt-oss-20b:free",
        "OpenAI GPT-OSS 20B",
        "21B MoE (3.6B активных), 131K контекста. Легкий и быстрый"
    ),
    ModelInfo(
        "google/gemma-4-31b-it:free",
        "Google Gemma 4 31B",
        "30.7B dense мультимодальная, 256K контекста. Лучшая для кода и рассуждений"
    ),
    ModelInfo(
        "google/gemma-4-26b-a4b-it:free",
        "Google Gemma 4 26B MoE",
        "25.2B MoE (3.8B активных), 256K контекста. Баланс качества и скорости"
    ),
    ModelInfo(
        "poolside/laguna-m.1:free",
        "Poolside Laguna M.1",
        "Флагманский кодинг-агент, 128K контекста, до 8K токенов ответа"
    ),
    ModelInfo(
        "poolside/laguna-xs.2:free",
        "Poolside Laguna XS.2",
        "Компактный кодинг-агент, 128K контекста, Apache 2.0"
    ),
    ModelInfo(
        "openrouter/owl-alpha",
        "Owl Alpha",
        "High-performance foundation model для agentic workloads, 1.05M контекста"
    ),
    ModelInfo(
        "z-ai/glm-4.5-air:free",
        "Z.ai GLM 4.5 Air",
        "MoE архитектура, hybrid inference (thinking/non-thinking modes), 131K контекста"
    ),
    ModelInfo(
        "moonshotai/kimi-k2.6:free",
        "MoonshotAI Kimi K2.6",
        "Мультимодальная для long-horizon coding и multi-agent orchestration"
    ),
)
