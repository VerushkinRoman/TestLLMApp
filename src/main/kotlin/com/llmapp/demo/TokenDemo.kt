package com.llmapp.demo

import com.llmapp.agent.LLMAgent
import com.llmapp.api.ApiConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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

class LLMAgentWithFallback(
    apiKey: String,
    initialModel: String,
    systemPrompt: String,
    maxHistorySize: Int = 100
) {
    private var currentAgent = LLMAgent(
        apiKey = apiKey,
        model = initialModel,
        systemPrompt = systemPrompt,
        maxHistorySize = maxHistorySize
    )
    private var currentModelIndex = allFreeModels.indexOf(initialModel).takeIf { it >= 0 } ?: 0

    suspend fun processRequest(userInput: String): com.llmapp.agent.LLMResponse {
        var lastException: Exception? = null

        for (i in currentModelIndex until allFreeModels.size) {
            val model = allFreeModels[i]

            try {
                if (i != currentModelIndex) {
                    println("🔄 Переключение на fallback модель: ${getModelShortName(model)}")
                    currentAgent.changeModel(model)
                    currentModelIndex = i
                }

                return currentAgent.processRequest(userInput)

            } catch (e: Exception) {
                lastException = e
                println("⚠️ Модель ${getModelShortName(model)} не работает: ${e.message}")

                if (i < allFreeModels.size - 1) {
                    println("🔄 Пробуем следующую модель...")
                    delay(1.seconds)
                }
            }
        }

        throw lastException ?: Exception("All models failed")
    }

    fun clearHistory() {
        currentAgent.clearHistory()
    }

    fun getTokenStats() = currentAgent.getTokenStats()
    fun getContextWarning() = currentAgent.getContextWarning()
    fun getContextStatus() = currentAgent.getContextStatus()

    private fun getModelShortName(modelId: String): String {
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

suspend fun demonstrateTokenTracking() {
    val apiKey = ApiConfig.getApiKey()
    val primaryModel = "openai/gpt-oss-20b:free"

    println("\n✅ Используем модель: ${getModelShortName(primaryModel)}")

    val agent = LLMAgentWithFallback(
        apiKey = apiKey,
        initialModel = primaryModel,
        systemPrompt = "Ты полезный ассистент. Отвечай на русском языке.",
        maxHistorySize = 100
    )

    println("\n" + "=".repeat(80))
    println("ДЕМОНСТРАЦИЯ ОТСЛЕЖИВАНИЯ ТОКЕНОВ")
    println("=".repeat(80))

    // Собираем статистику
    val shortDialogueStats = mutableListOf<Triple<Int, Int, Int>>() // prompt, completion, total
    val longDialogueStats = mutableListOf<Triple<Int, Int, Int>>()
    val extraStats = mutableListOf<Triple<Int, Int, Int>>()
    val overflowStats = mutableListOf<Triple<Int, Int, Int>>()
    var overflowMessage = ""
    var maxTokensReached = 0

    println("\n📝 ТЕСТ 1: Короткий диалог (3 сообщения)")
    println("-".repeat(40))

    val shortDialogue = listOf(
        "Привет! Как дела?",
        "Расскажи кратко о Kotlin",
        "Спасибо, пока!"
    )

    for (message in shortDialogue) {
        println("\n👤 Пользователь: $message")
        try {
            val response = agent.processRequest(message)
            println("🤖 Ассистент: ${response.content.take(100)}...")
            println("📊 Токены: prompt=${response.promptTokens}, completion=${response.completionTokens}, total=${response.totalTokens}")
            shortDialogueStats.add(
                Triple(
                    response.promptTokens ?: 0,
                    response.completionTokens ?: 0,
                    response.totalTokens ?: 0
                )
            )
            println(agent.getContextWarning())
        } catch (e: Exception) {
            println("❌ Ошибка: ${e.message}")
            return
        }
    }

    println("\n📊 ИТОГО после короткого диалога:")
    println(agent.getTokenStats().getFormattedTokens())
    println("Стоимость: ${agent.getTokenStats().getFormattedCost()}")

    println("\n" + "=".repeat(80))
    println("📝 ТЕСТ 2: Длинный диалог (15 сообщений с контекстом)")
    println("-".repeat(40))

    agent.clearHistory()
    longDialogueStats.clear()

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
        "Как оптимизировать производительность Compose приложений?"
    )

    for ((index, question) in longDialogueTopics.withIndex()) {
        println("\n[${index + 1}] 👤: ${question.take(50)}...")
        try {
            val response = agent.processRequest(question)
            println("🤖: ${response.content.take(80)}...")
            println("📊 Токены: prompt=${response.promptTokens}, completion=${response.completionTokens}, total=${response.totalTokens}")
            longDialogueStats.add(
                Triple(
                    response.promptTokens ?: 0,
                    response.completionTokens ?: 0,
                    response.totalTokens ?: 0
                )
            )
            println("📊 Статус: ${agent.getContextWarning()}")

            if (agent.getTokenStats().totalTokens > 10000) {
                println("⚠️ Накоплено ${agent.getTokenStats().totalTokens} токенов!")
            }

            delay(500.milliseconds)
        } catch (e: Exception) {
            println("❌ Ошибка: ${e.message}")
            println("💡 Пропускаем этот вопрос и продолжаем...")
        }
    }

    println("\n📊 ФИНАЛЬНАЯ СТАТИСТИКА после длинного диалога:")
    println(agent.getTokenStats().getFormattedTokens())
    println("Стоимость: ${agent.getTokenStats().getFormattedCost()}")
    println("Статус контекста: ${agent.getContextStatus()}")

    println("\n" + "=".repeat(80))
    println("📝 ТЕСТ 3: ДОПОЛНИТЕЛЬНЫЕ ЗАПРОСЫ")
    println("-".repeat(40))

    val extraQuestions = listOf(
        "Что такое корутины в Kotlin?",
        "Как работает Flow?",
        "Расскажи про Compose UI",
        "Что такое функции расширения?",
        "Как работает делегирование в Kotlin?"
    )

    for (question in extraQuestions) {
        println("\n👤: $question")
        try {
            val response = agent.processRequest(question)
            println("🤖: ${response.content.take(100)}")
            println("📊 Токены: prompt=${response.promptTokens}, completion=${response.completionTokens}, total=${response.totalTokens}")
            extraStats.add(
                Triple(
                    response.promptTokens ?: 0,
                    response.completionTokens ?: 0,
                    response.totalTokens ?: 0
                )
            )
        } catch (e: Exception) {
            println("❌ Ошибка: ${e.message}")
        }
        delay(500.milliseconds)
    }

    println("\n" + "=".repeat(80))
    println("📊 ТЕСТ 4: ПРОВЕРКА ПЕРЕПОЛНЕНИЯ КОНТЕКСТА")
    println("-".repeat(40))

    var counter = longDialogueTopics.size + 1
    var overflowOccurred = false

    while (counter < 20) {
        val longText = "Расскажи очень подробно о Kotlin. " +
                "Опиши все фичи языка: функции расширения, делегирование, sealed классы, " +
                "инлайновые функции, реифицированные типы, оператор invoke, компонентные функции. " +
                "Приведи примеры для каждой фичи. Добавим много текста для заполнения контекста. " +
                "Повторяем важную информацию чтобы увеличить количество токенов. ${"X".repeat(500)}"

        println("\n[${counter}] Запрос #$counter (проверка лимита контекста)...")
        try {
            val response = agent.processRequest(longText)
            println("✅ Ответ получен. Всего токенов: ${agent.getTokenStats().totalTokens}")
            println("📊 Токены запроса: prompt=${response.promptTokens}, completion=${response.completionTokens}, total=${response.totalTokens}")
            overflowStats.add(
                Triple(
                    response.promptTokens ?: 0,
                    response.completionTokens ?: 0,
                    response.totalTokens ?: 0
                )
            )
            println(agent.getContextWarning())
            maxTokensReached = agent.getTokenStats().totalTokens

            if (agent.getTokenStats().totalTokens > 100000) {
                println("⚠️ Достигнуто 100k+ токенов! Контекст почти полон!")
            }

            delay(500.milliseconds)
        } catch (e: Exception) {
            println("❌ ОШИБКА: ${e.message}")
            if (e.message?.contains("429") == true || e.message?.contains("rate") == true) {
                overflowMessage = "Достигнут лимит запросов API (50 в день)"
                println("⚠️ $overflowMessage")
                break
            }
            if (e.message?.contains("context") == true || e.message?.contains("token") == true ||
                e.message?.contains("400") == true || e.message?.contains("length") == true
            ) {
                overflowOccurred = true
                overflowMessage =
                    "ПЕРЕПОЛНЕНИЕ КОНТЕКСТА на $maxTokensReached токенах! Модель начала 'забывать' начало диалога"
                println("💥 $overflowMessage")
                break
            }
        }
        counter++
    }

    // Формируем статистику для отправки модели
    val shortTotalTokens = shortDialogueStats.sumOf { it.third }
    val shortAvgTokens =
        if (shortDialogueStats.isNotEmpty()) shortTotalTokens / shortDialogueStats.size else 0

    val longTotalTokens = longDialogueStats.sumOf { it.third }
    val longAvgTokens =
        if (longDialogueStats.isNotEmpty()) longTotalTokens / longDialogueStats.size else 0

    val extraTotalTokens = extraStats.sumOf { it.third }
    val extraAvgTokens = if (extraStats.isNotEmpty()) extraTotalTokens / extraStats.size else 0

    val growthRate = if (shortTotalTokens > 0) {
        (longTotalTokens.toDouble() / shortTotalTokens)
    } else 0.0

    val statsReport = """
        📊 СТАТИСТИКА ДЛЯ АНАЛИЗА:
        
        🔹 КОРОТКИЙ ДИАЛОГ (3 сообщения):
           • Всего токенов: $shortTotalTokens
           • Среднее на сообщение: $shortAvgTokens
           • Стоимость: ${agent.getTokenStats().getFormattedCost()}
        
        🔹 ДЛИННЫЙ ДИАЛОГ (15 сообщений):
           • Всего токенов: $longTotalTokens
           • Среднее на сообщение: $longAvgTokens
           • Рост токенов: ${"%.1f".format(growthRate)}x
        
        🔹 ДОПОЛНИТЕЛЬНЫЕ ЗАПРОСЫ (5 сообщений):
           • Всего токенов: $extraTotalTokens
           • Среднее на сообщение: $extraAvgTokens
        
        🔹 ПЕРЕПОЛНЕНИЕ КОНТЕКСТА:
           • Произошло: ${if (overflowOccurred) "ДА" else "НЕТ"}
           • Максимум токенов: $maxTokensReached
           • ${if (overflowOccurred) overflowMessage else "Переполнение не достигнуто"}
        
        🔹 ОБЩАЯ СТАТИСТИКА:
           • Всего запросов: ${agent.getTokenStats().requestCount}
           • Общая стоимость: ${agent.getTokenStats().getFormattedCost()}
        
        Вопросы для анализа:
        1. Сравните короткий и длинный диалог. Как растет количество токенов?
        2. Что происходит при переполнении контекста?
        3. Как стоимость зависит от длины диалога?
        4. Какие выводы можно сделать для оптимизации использования токенов?
        
        Пожалуйста, сделай подробные выводы на русском языке.
    """.trimIndent()

    println("\n" + "=".repeat(80))
    println("📊 ПЕРЕДАЕМ СТАТИСТИКУ МОДЕЛИ ДЛЯ АНАЛИЗА")
    println("=".repeat(80))
    println("\n$statsReport")

    // Создаем нового агента для анализа (чистая история)
    val analysisAgent = LLMAgentWithFallback(
        apiKey = apiKey,
        initialModel = primaryModel,
        systemPrompt = "Ты эксперт по анализу данных. Делай подробные, структурированные выводы.",
        maxHistorySize = 50
    )

    println("\n🤖 МОДЕЛЬ АНАЛИЗИРУЕТ СТАТИСТИКУ...")
    println("-".repeat(80))

    try {
        val analysisResponse = analysisAgent.processRequest(statsReport)
        println("\n📊 ВЫВОДЫ МОДЕЛИ:")
        println("=".repeat(80))
        println(analysisResponse.content)
        println("=".repeat(80))
        println("\n📊 Токены на анализ: prompt=${analysisResponse.promptTokens}, completion=${analysisResponse.completionTokens}, total=${analysisResponse.totalTokens}")
    } catch (e: Exception) {
        println("❌ Ошибка при анализе: ${e.message}")
    }

    println("\n✅ Демонстрация завершена!")
}

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

fun main() = runBlocking {
    try {
        demonstrateTokenTracking()
    } catch (e: Exception) {
        println("\n❌ Критическая ошибка: ${e.message}")
        println("\n💡 Возможные решения:")
        println("   1. Проверьте API ключ в файле openrouter.properties")
        println("   2. Убедитесь, что есть интернет-соединение")
        println("   3. Проверьте лимиты запросов на https://openrouter.io/limits")
    }
}
