package com.llmapp.demo

import com.llmapp.agent.LLMAgent
import com.llmapp.api.ApiConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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
    private var currentModelIndex =
        DemoData.allFreeModels.indexOf(initialModel).takeIf { it >= 0 } ?: 0

    suspend fun processRequest(userInput: String): com.llmapp.agent.LLMResponse {
        var lastException: Exception? = null

        for (i in currentModelIndex until DemoData.allFreeModels.size) {
            val model = DemoData.allFreeModels[i]

            try {
                if (i != currentModelIndex) {
                    println("🔄 Переключение на fallback модель: ${DemoData.getModelShortName(model)}")
                    currentAgent.changeModel(model)
                    currentModelIndex = i
                }

                return currentAgent.processRequest(userInput)

            } catch (e: Exception) {
                lastException = e
                println("⚠️ Модель ${DemoData.getModelShortName(model)} не работает: ${e.message}")

                if (i < DemoData.allFreeModels.size - 1) {
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
}

suspend fun demonstrateTokenTracking() {
    val apiKey = ApiConfig.getApiKey()
    val primaryModel = "openai/gpt-oss-20b:free"

    println("\n✅ Используем модель: ${DemoData.getModelShortName(primaryModel)}")

    val agent = LLMAgentWithFallback(
        apiKey = apiKey,
        initialModel = primaryModel,
        systemPrompt = "Ты полезный ассистент. Отвечай на русском языке.",
        maxHistorySize = 100
    )

    println(DemoData.getTokenDemoHeader())

    // Собираем статистику
    val shortDialogueStats = mutableListOf<Triple<Int, Int, Int>>()
    val longDialogueStats = mutableListOf<Triple<Int, Int, Int>>()
    val extraStats = mutableListOf<Triple<Int, Int, Int>>()

    // ТЕСТ 1: Короткий диалог
    println(DemoData.getShortDialogueTestHeader())
    println("-".repeat(40))

    for (message in DemoData.shortDialogue) {
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

    val stats1 = agent.getTokenStats()
    println(
        DemoData.formatShortDialogueSummary(
            stats1.totalTokens,
            stats1.totalPromptTokens,
            stats1.totalCompletionTokens,
            stats1.estimatedCostUsd
        )
    )

    // ТЕСТ 2: Длинный диалог
    println(DemoData.getLongDialogueTestHeader(DemoData.longDialogueTopics.size))
    println("-".repeat(40))

    agent.clearHistory()
    longDialogueStats.clear()

    for ((index, question) in DemoData.longDialogueTopics.withIndex()) {
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

    val finalStats = agent.getTokenStats()
    val contextStatusMessage = when (agent.getContextStatus()) {
        com.llmapp.agent.ContextStatus.SAFE -> "✅ Безопасно"
        com.llmapp.agent.ContextStatus.WARNING -> "⚠️ Предупреждение"
        com.llmapp.agent.ContextStatus.CRITICAL -> "🔴 Критично"
        com.llmapp.agent.ContextStatus.OVERFLOW -> "💥 Переполнение"
    }

    println(
        DemoData.getLongDialogueFinalStats(
            finalStats.totalTokens,
            finalStats.totalPromptTokens,
            finalStats.totalCompletionTokens,
            finalStats.estimatedCostUsd,
            contextStatusMessage
        )
    )

    // ТЕСТ 3: Дополнительные запросы
    println(DemoData.getExtraQuestionsTestHeader())
    println("-".repeat(40))

    for (question in DemoData.extraQuestions) {
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

    val statsReport = DemoData.getStatsReport(
        shortTotalTokens, shortAvgTokens,
        longTotalTokens, longAvgTokens, growthRate,
        extraTotalTokens, extraAvgTokens,
        agent.getTokenStats().requestCount,
        agent.getTokenStats().totalTokens,
        agent.getTokenStats().estimatedCostUsd
    )

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
