package com.llmapp.model

object ModelList {
    val all = listOf(
        ModelEntry("nvidia/nemotron-3-super-120b-a12b:free", "NVIDIA Nemotron 3 Super", "120B MoE, 12B активных, 1M контекста"),
        ModelEntry("openai/gpt-oss-120b:free", "OpenAI GPT-OSS 120B", "117B MoE (5.1B активных), 131K контекста"),
        ModelEntry("moonshotai/kimi-k2.6:free", "MoonshotAI Kimi K2.6", "Мультимодальная для long-horizon coding"),
        ModelEntry("nvidia/nemotron-3-nano-30b-a3b:free", "NVIDIA Nemotron 3 Nano 30B", "30B MoE (3B активных), 256K контекста"),
        ModelEntry("poolside/laguna-m.1:free", "Poolside Laguna M.1", "Флагманский кодинг-агент, 128K контекста"),
        ModelEntry("nousresearch/hermes-3-llama-3.1-405b:free", "Nous Hermes 3 405B", "405B, фронтирная модель с большим контекстом"),
        ModelEntry("openai/gpt-oss-20b:free", "OpenAI GPT-OSS 20B", "21B MoE (3.6B активных), 131K контекста. Легкий"),
        ModelEntry("poolside/laguna-xs.2:free", "Poolside Laguna XS.2", "Компактный кодинг-агент, 128K контекста"),
        ModelEntry("google/gemma-4-31b-it:free", "Gemma 4 31B", "31B, отличное следование инструкциям"),
        ModelEntry("google/gemma-4-26b-a4b-it:free", "Google Gemma 4 26B MoE", "25.2B MoE (3.8B активных), 256K контекста"),
        ModelEntry("liquid/lfm-2.5-1.2b-instruct:free", "Liquid LFM 2.5 1.2B Instruct", "1.2B, самая легкая модель для быстрых ответов"),
        ModelEntry("tencent/hy3-preview:free", "Tencent Hy3 Preview", "Новая модель от Tencent, preview версия"),
        ModelEntry("minimax/minimax-m2.5:free", "MiniMax M2.5", "Универсальная модель общего назначения"),
        ModelEntry("arcee-ai/trinity-large-thinking:free", "Arcee Trinity Large Thinking", "Модель с режимом глубокого рассуждения"),
        ModelEntry("qwen/qwen3-next-80b-a3b-instruct:free", "Qwen3 Next 80B", "80B MoE (3B активных),前沿 модель"),
        ModelEntry("cognitivecomputations/dolphin-mistral-24b-venice-edition:free", "Dolphin Mistral 24B", "24B, fine-tuned для инструкций. Быстрая и эффективная"),
        ModelEntry("meta-llama/llama-3.2-3b-instruct:free", "Meta LLaMA 3.2 3B", "3B, сверхлегкая модель для простых задач"),
        ModelEntry("meta-llama/llama-3.3-70b-instruct:free", "Meta LLaMA 3.3 70B", "70B, мощная модель общего назначения"),
        ModelEntry("liquid/lfm-2.5-1.2b-thinking:free", "Liquid LFM 2.5 1.2B Thinking", "1.2B с режимом рассуждения. Компактная и быстрая"),
    )

    val ids: List<String> get() = all.map { it.id }
    val modelInfos: List<ModelInfo> get() = all.map { ModelInfo(it.id, it.name, it.description) }
    val pricing: Map<String, Pricing> get() = all.associate { it.id to Pricing(0.0, 0.0) }
}

data class ModelEntry(
    val id: String,
    val name: String,
    val description: String,
)
