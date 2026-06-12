package com.llmapp.demo

object DemoData {
    val allFreeModels = listOf(
        "nvidia/nemotron-3-nano-30b-a3b:free",
        "nvidia/nemotron-3-super-120b-a12b:free",
        "nvidia/nemotron-3-ultra-550b-a55b:free",
        "nvidia/nemotron-3-nano-omni:free",
        "nvidia/nemotron-nano-9b-v2:free",
        "nvidia/nemotron-nano-12b-2-vl:free",
        "openai/gpt-oss-20b:free",
        "openai/gpt-oss-120b:free",
        "google/gemma-4-26b-a4b-it:free",
        "google/gemma-4-31b-it:free",
        "poolside/laguna-xs.2:free",
        "poolside/laguna-m.1:free",
        "openrouter/owl-alpha",
        "z-ai/glm-4.5-air:free",
        "moonshotai/kimi-k2.6:free"
    )

    val shortDialogue = listOf(
        "Привет! Как дела?",
        "Расскажи кратко о Kotlin",
        "Спасибо, пока!"
    )

    val longDialogueTopics = listOf(
        "Расскажи подробно о функциональном программировании в Kotlin",
        "Приведи 5 примеров использования лямбд и функций высшего порядка",
        "Теперь про корутины: как они работают под капотом?",
        "Объясни разницу между launch, async и produce",
        "Покажи пример обработки ошибок в корутинах",
        "Как тестировать код с корутинами?",
        "Расскажи про StateFlow и SharedFlow",
        "Как реализовать паттерн MVI с Flow?",
        "Сравни Compose и традиционный XML подход",
        "Какие best practices для большого Compose проекта?",
        "Объясни принципы SOLID в Kotlin с примерами",
        "Как работает type-safe builder в Kotlin? Покажи пример DSL",
        "Расскажи про инлайн классы и когда их использовать",
        "Что такое сериализация в Kotlin? Как использовать kotlinx.serialization?",
        "Как оптимизировать производительность Compose приложений?",
        "Что такое делегирование в Kotlin?",
        "Расскажи про sealed классы и интерфейсы",
        "Как работают реифицированные типы?",
        "Объясни концепцию компонентных функций",
        "Что такое оператор invoke в Kotlin?"
    )

    val extraQuestions = listOf(
        "Что такое корутины в Kotlin?",
        "Как работает Flow?",
        "Расскажи про Compose UI",
        "Что такое функции расширения?",
        "Как работает делегирование в Kotlin?"
    )

    fun getModelShortName(modelId: String): String {
        return when {
            modelId.contains("nemotron-3-nano-30b") -> "NVIDIA Nano 30B"
            modelId.contains("nemotron-3-super-120b") -> "NVIDIA Super 120B"
            modelId.contains("nemotron-3-ultra-550b") -> "NVIDIA Ultra 550B"
            modelId.contains("nemotron-3-nano-omni") -> "NVIDIA Nano Omni"
            modelId.contains("nemotron-nano-9b") -> "NVIDIA Nano 9B"
            modelId.contains("nemotron-nano-12b") -> "NVIDIA Nano 12B VL"
            modelId.contains("gpt-oss-20b") -> "GPT-OSS 20B"
            modelId.contains("gpt-oss-120b") -> "GPT-OSS 120B"
            modelId.contains("gemma-4-26b") -> "Gemma 4 26B"
            modelId.contains("gemma-4-31b") -> "Gemma 4 31B"
            modelId.contains("laguna-xs") -> "Laguna XS"
            modelId.contains("laguna-m") -> "Laguna M"
            modelId.contains("owl-alpha") -> "Owl Alpha"
            modelId.contains("glm-4.5-air") -> "GLM 4.5 Air"
            modelId.contains("kimi-k2.6") -> "Kimi K2.6"
            else -> modelId.take(30)
        }
    }
}
