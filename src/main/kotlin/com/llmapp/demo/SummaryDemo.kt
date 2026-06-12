package com.llmapp.demo

import com.llmapp.agent.CompressedChatHistory
import com.llmapp.agent.CompressedLLMAgent
import com.llmapp.agent.LLMAgent
import com.llmapp.api.ApiConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

data class TestResponse(
    val content: String,
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val compressionStats: CompressedChatHistory.CompressionStats? = null
)

class ComparisonResult(
    val name: String,
    val totalTokens: Int,
    val totalPromptTokens: Int,
    val totalCompletionTokens: Int,
    val requestCount: Int,
    val totalTimeMs: Long,
    val averageTokensPerRequest: Double,
    val estimatedCost: Double,
    val compressionStats: String? = null
) {
    fun getFormatted(): String = """
        📊 $name:
           • Запросов: $requestCount
           • Всего токенов: $totalTokens
           • Prompt токенов: $totalPromptTokens
           • Completion токенов: $totalCompletionTokens
           • Среднее на запрос: ${"%.1f".format(averageTokensPerRequest)}
           • Общее время: ${formatTime(totalTimeMs)}
           • Примерная стоимость: $${String.format("%.6f", estimatedCost)}
           ${if (compressionStats != null) "• $compressionStats" else ""}
    """.trimIndent()

    private fun formatTime(ms: Long): String = when {
        ms < 1000 -> "${ms}мс"
        ms < 60000 -> "${ms / 1000}.${(ms % 1000) / 100}с"
        else -> "${ms / 60000}м ${(ms % 60000) / 1000}с"
    }
}

class SummaryDemo {

    suspend fun runComparison() {
        val apiKey = ApiConfig.getApiKey()
        val primaryModel = "openai/gpt-oss-20b:free"

        println("\n" + "=".repeat(100))
        println("🧪 ЭКСПЕРИМЕНТ: Сравнение работы с компрессией контекста и без")
        println("=".repeat(100))

        println("\n📌 Параметры эксперимента:")
        println("   • Модель: ${DemoData.getModelShortName(primaryModel)}")
        println("   • Количество сообщений: ${DemoData.longDialogueTopics.size}")
        println("   • Компрессия: последние 8 сообщений как есть, summary каждые 6 сообщений")

        // Тест 1: Без компрессии
        println("\n" + "━".repeat(100))
        println("🔵 ТЕСТ 1: Обычный агент (БЕЗ компрессии контекста)")
        println("━".repeat(100))

        val regularAgent = LLMAgent(
            apiKey = apiKey,
            model = primaryModel,
            systemPrompt = "Ты полезный ассистент. Отвечай на русском языке кратко и по делу.",
            maxHistorySize = 200
        )

        val regularResult = runTestWithAgent(regularAgent, "БЕЗ компрессии")

        // Пауза между тестами
        delay(5.seconds)

        // Тест 2: С компрессией
        println("\n" + "━".repeat(100))
        println("🟢 ТЕСТ 2: Сжатый агент (С компрессией контекста)")
        println("━".repeat(100))

        val compressedAgent = CompressedLLMAgent(
            apiKey = apiKey,
            model = primaryModel,
            systemPrompt = "Ты полезный ассистент. Отвечай на русском языке кратко и по делу.",
            maxHistorySize = 200,
            keepLastMessages = 8,
            summarizeEvery = 6
        )
        compressedAgent.compressionEnabled = true

        val compressedResult = runTestWithAgent(compressedAgent, "С компрессией")

        // Сравнение результатов
        println("\n" + "=".repeat(100))
        println("📊 СРАВНИТЕЛЬНЫЙ АНАЛИЗ")
        println("=".repeat(100))

        compareResults(regularResult, compressedResult)

        // Тест 3: Проверка адаптивности к контексту модели
        println("\n" + "=".repeat(100))
        println("🎯 ТЕСТ 3: Адаптивная компрессия под разные модели")
        println("=".repeat(100))

        testAdaptiveCompression(apiKey)
    }

    private suspend fun runTestWithAgent(
        agent: Any,
        testName: String
    ): ComparisonResult {
        val startTime = System.currentTimeMillis()

        when (agent) {
            is LLMAgent -> agent.clearHistory()
            is CompressedLLMAgent -> agent.clearHistory()
        }

        var totalTokens = 0
        var totalPromptTokens = 0
        var totalCompletionTokens = 0
        var requestCount = 0
        var compressionStats: String?

        println("\n📝 Начинаем диалог из ${DemoData.longDialogueTopics.size} вопросов...")
        println("-".repeat(80))

        for ((index, question) in DemoData.longDialogueTopics.withIndex()) {
            println("\n[${index + 1}] 👤: ${question.take(60)}...")

            try {
                val response = when (agent) {
                    is LLMAgent -> {
                        val resp = agent.processRequest(question)
                        TestResponse(
                            content = resp.content,
                            promptTokens = resp.promptTokens ?: 0,
                            completionTokens = resp.completionTokens ?: 0,
                            totalTokens = resp.totalTokens ?: 0,
                            compressionStats = null
                        )
                    }

                    is CompressedLLMAgent -> {
                        val resp = agent.processRequest(question)
                        TestResponse(
                            content = resp.content,
                            promptTokens = resp.promptTokens ?: 0,
                            completionTokens = resp.completionTokens ?: 0,
                            totalTokens = resp.totalTokens ?: 0,
                            compressionStats = resp.compressionStats
                        )
                    }

                    else -> error("Unknown agent type")
                }

                println("🤖: ${response.content.take(80)}...")
                println("📊 Токены: ↑${response.promptTokens} / ↓${response.completionTokens} / Σ${response.totalTokens}")

                totalTokens += response.totalTokens
                totalPromptTokens += response.promptTokens
                totalCompletionTokens += response.completionTokens
                requestCount++

                if (response.compressionStats != null && index % 5 == 0 && index > 0) {
                    compressionStats =
                        response.compressionStats.getFormatted().lines().drop(1).joinToString(" ")
                            .take(80)
                    println("💾 $compressionStats")
                }

                delay(500.milliseconds)

            } catch (e: Exception) {
                println("❌ Ошибка: ${e.message}")
                println("💡 Продолжаем со следующим вопросом...")
            }
        }

        val endTime = System.currentTimeMillis()

        val tokenStats = when (agent) {
            is LLMAgent -> agent.getTokenStats()
            is CompressedLLMAgent -> agent.getTokenStats()
            else -> error("Unknown agent type")
        }

        val finalCompressionStats = if (agent is CompressedLLMAgent) {
            agent.getCompressionStats().getFormatted()
        } else null

        println("\n✅ Тест '$testName' завершен!")
        println("📈 Финальная статистика токенов:")
        println(tokenStats.getFormattedTokens())
        println("💰 Стоимость: ${tokenStats.getFormattedCost()}")

        if (finalCompressionStats != null) {
            println("\n${finalCompressionStats}")
        }

        return ComparisonResult(
            name = testName,
            totalTokens = totalTokens,
            totalPromptTokens = totalPromptTokens,
            totalCompletionTokens = totalCompletionTokens,
            requestCount = requestCount,
            totalTimeMs = endTime - startTime,
            averageTokensPerRequest = if (requestCount > 0) totalTokens.toDouble() / requestCount else 0.0,
            estimatedCost = tokenStats.estimatedCostUsd,
            compressionStats = finalCompressionStats?.lines()?.getOrNull(1)
        )
    }

    private fun compareResults(regular: ComparisonResult, compressed: ComparisonResult) {
        println("\n📈 СРАВНЕНИЕ:")
        println("-".repeat(80))

        val tokensSaved = regular.totalTokens - compressed.totalTokens
        val tokensSavedPercent = if (regular.totalTokens > 0) {
            (tokensSaved.toDouble() / regular.totalTokens) * 100
        } else 0.0

        val costSaved = regular.estimatedCost - compressed.estimatedCost
        val timeSaved = regular.totalTimeMs - compressed.totalTimeMs

        println(regular.getFormatted())
        println()
        println(compressed.getFormatted())

        println("\n📊 РАЗНИЦА:")
        println("-".repeat(80))
        println(
            """
            | Показатель                    | Без компрессии     | С компрессией      | Экономия
            | ${"─".repeat(70)}
            | Всего токенов                 | ${formatNumber(regular.totalTokens)}        | ${
                formatNumber(
                    compressed.totalTokens
                )
            }        | ${formatNumber(tokensSaved)} (${"%.1f".format(tokensSavedPercent)}%)
            | Prompt токенов                | ${formatNumber(regular.totalPromptTokens)}        | ${
                formatNumber(
                    compressed.totalPromptTokens
                )
            }        | ${formatNumber(regular.totalPromptTokens - compressed.totalPromptTokens)}
            | Completion токенов            | ${formatNumber(regular.totalCompletionTokens)}        | ${
                formatNumber(
                    compressed.totalCompletionTokens
                )
            }        | ${formatNumber(regular.totalCompletionTokens - compressed.totalCompletionTokens)}
            | Среднее на запрос             | ${"%.1f".format(regular.averageTokensPerRequest)}      | ${
                "%.1f".format(
                    compressed.averageTokensPerRequest
                )
            }      | ${"%.1f".format(regular.averageTokensPerRequest - compressed.averageTokensPerRequest)}
            | Общее время                   | ${formatTime(regular.totalTimeMs)}     | ${
                formatTime(
                    compressed.totalTimeMs
                )
            }     | ${if (timeSaved > 0) "-" else "+"}${formatTime(kotlin.math.abs(timeSaved))}
            | Стоимость                     | $${"%.6f".format(regular.estimatedCost)} | $${
                "%.6f".format(
                    compressed.estimatedCost
                )
            } | $${"%.6f".format(costSaved)}
        """.trimMargin()
        )

        if (compressed.compressionStats != null) {
            println("\n📊 Детали компрессии:")
            println("   ${compressed.compressionStats}")
        }

        println("\n💡 ВЫВОДЫ:")
        println("-".repeat(80))

        when {
            tokensSavedPercent > 30 -> println(
                "   ✅ ОТЛИЧНО! Компрессия сократила расход токенов на ${
                    "%.1f".format(
                        tokensSavedPercent
                    )
                }%"
            )

            tokensSavedPercent > 15 -> println(
                "   👍 ХОРОШО! Компрессия сократила расход токенов на ${
                    "%.1f".format(
                        tokensSavedPercent
                    )
                }%"
            )

            tokensSavedPercent > 0 -> println(
                "   👌 НЕПЛОХО! Компрессия сократила расход токенов на ${
                    "%.1f".format(
                        tokensSavedPercent
                    )
                }%"
            )

            else -> println("   ⚠️ Компрессия не дала экономии токенов в этом тесте")
        }

        if (costSaved > 0) {
            println("   💰 Экономия средств: $${"%.6f".format(costSaved)}")
        }

        println(
            """
            |
            | Рекомендации:
            |   1. Компрессия особенно эффективна для длинных диалогов (>20 сообщений)
            |   2. Настройте keepLastMessages (8-12) и summarizeEvery (5-8) под ваши задачи
            |   3. Для моделей с большим контекстом можно увеличить keepLastMessages
            |   4. Для коротких диалогов компрессию можно отключать
        """.trimMargin()
        )
    }

    private suspend fun testAdaptiveCompression(apiKey: String) {
        val models = listOf(
            "nvidia/nemotron-3-super-120b-a12b:free" to 1_000_000,
            "openai/gpt-oss-20b:free" to 131_072,
            "google/gemma-4-26b-a4b-it:free" to 256_000
        )

        for ((modelId, contextSize) in models) {
            println("\n📌 Тестируем модель: ${DemoData.getModelShortName(modelId)}")
            println("   Контекстное окно: ${formatNumber(contextSize)} токенов")

            val keepLastMessages = when {
                contextSize >= 1_000_000 -> 15
                contextSize >= 200_000 -> 10
                else -> 6
            }

            val summarizeEvery = when {
                contextSize >= 1_000_000 -> 10
                contextSize >= 200_000 -> 8
                else -> 5
            }

            println("   Адаптивные настройки: keepLast=$keepLastMessages, summarizeEvery=$summarizeEvery")

            val agent = CompressedLLMAgent(
                apiKey = apiKey,
                model = modelId,
                systemPrompt = "Ты полезный ассистент. Отвечай на русском языке кратко.",
                keepLastMessages = keepLastMessages,
                summarizeEvery = summarizeEvery
            )

            val testQuestions = DemoData.longDialogueTopics.take(8)

            for (question in testQuestions) {
                try {
                    val response = agent.processRequest(question)
                    println("   ✓ Ответ получен (${response.totalTokens ?: 0} токенов)")
                    delay(300.milliseconds)
                } catch (e: Exception) {
                    println("   ⚠️ Ошибка: ${e.message?.take(50)}")
                }
            }

            val stats = agent.getCompressionStats()
            println("   📊 Итоговая компрессия:")
            stats.getFormatted().lines().forEach { line ->
                println("      $line")
            }

            delay(2.seconds)
        }
    }

    private fun formatNumber(num: Int): String {
        return when {
            num >= 1_000_000 -> "${num / 1_000_000}M"
            num >= 1_000 -> "${num / 1_000}K"
            else -> num.toString()
        }
    }

    private fun formatTime(ms: Long): String = when {
        ms < 1000 -> "${ms}мс"
        ms < 60000 -> "${ms / 1000}.${(ms % 1000) / 100}с"
        else -> "${ms / 60000}м ${(ms % 60000) / 1000}с"
    }
}

fun main() = runBlocking {
    try {
        val demo = SummaryDemo()
        demo.runComparison()
    } catch (e: Exception) {
        println("\n❌ Критическая ошибка: ${e.message}")
        e.printStackTrace()
    }
}
