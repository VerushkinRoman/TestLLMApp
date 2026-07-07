package com.llmapp.demo.manager

import com.llmapp.agent.CompressedLLMAgent
import com.llmapp.agent.LLMAgent
import com.llmapp.demo.DemoData
import com.llmapp.ui.models.ChatMessageUI
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Демонстрация сравнения с компрессией контекста и без
 */
class CompressionDemoRunner(
    onMessageAdded: (ChatMessageUI) -> Unit,
    onTypingStateChanged: (Boolean) -> Unit
) : BaseDemoRunner(onMessageAdded, onTypingStateChanged) {

    override suspend fun run() {
        val primaryModel = "openai/gpt-oss-20b:free"

        // ========== ВВЕДЕНИЕ ==========
        addMessage(
            role = "assistant",
            content = DemoData.getCompressionDemoIntro(DemoData.longDialogueTopics.size),
            metadata = "ДЕМОНСТРАЦИЯ КОМПРЕССИИ"
        )
        delay(2.seconds)

        // ========== ТЕСТ 1: Без компрессии ==========
        addMessage(
            role = "assistant",
            content = DemoData.getNoCompressionTestIntro(),
            metadata = "Тест без компрессии"
        )
        delayMedium()

        val regularAgent = LLMAgent(

            model = primaryModel,
            systemPrompt = "Ты полезный ассистент. Отвечай на русском языке кратко и по делу.",
            maxHistorySize = 200
        )

        var regularTotalTokens = 0
        var regularTotalPrompt = 0
        var regularTotalCompletion = 0
        val regularStartTime = System.currentTimeMillis()

        for ((index, question) in DemoData.longDialogueTopics.withIndex()) {
            addMessage(
                role = "user",
                content = question.take(60) + if (question.length > 60) "..." else "",
                metadata = "[${index + 1}/${DemoData.longDialogueTopics.size}]"
            )
            delayShort()
            onTypingStateChanged(true)
            delayMedium()

            try {
                val response = regularAgent.processRequest(question)
                onTypingStateChanged(false)

                regularTotalTokens += response.totalTokens ?: 0
                regularTotalPrompt += response.promptTokens ?: 0
                regularTotalCompletion += response.completionTokens ?: 0

                val metadata = DemoData.formatCompressionTestMetadata(
                    index + 1,
                    DemoData.longDialogueTopics.size,
                    response.promptTokens ?: 0,
                    response.completionTokens ?: 0,
                    response.totalTokens ?: 0,
                    regularTotalTokens
                )

                addMessage(
                    role = "assistant",
                    content = response.content,
                    metadata = metadata,
                    promptTokens = response.promptTokens,
                    completionTokens = response.completionTokens,
                    totalTokens = response.totalTokens,
                    responseTimeMs = response.responseTimeMs
                )

                delay(500.milliseconds)

            } catch (e: Exception) {
                onTypingStateChanged(false)
                addMessage(
                    role = "assistant",
                    content = "❌ Ошибка: ${e.message}",
                    metadata = "Ошибка"
                )
            }
        }

        val regularEndTime = System.currentTimeMillis()
        val regularTime = regularEndTime - regularStartTime
        val regularCost = regularAgent.getTokenStats().estimatedCostUsd

        addMessage(
            role = "assistant",
            content = DemoData.getNoCompressionResults(
                DemoData.longDialogueTopics.size,
                regularTotalTokens,
                regularTotalPrompt,
                regularTotalCompletion,
                regularTime,
                regularCost
            ),
            metadata = "Итог без компрессии"
        )
        delay(3.seconds)

        // ========== ТЕСТ 2: С компрессией ==========
        addMessage(
            role = "assistant",
            content = DemoData.getCompressionTestIntro(8, 6),
            metadata = "Тест с компрессией"
        )
        delayMedium()

        val compressedAgent = CompressedLLMAgent(

            model = primaryModel,
            systemPrompt = "Ты полезный ассистент. Отвечай на русском языке кратко и по делу.",
            maxHistorySize = 200,
            keepLastMessages = 8,
            compressAfterTokens = 4500
        )
        compressedAgent.compressionEnabled = true

        var compressedTotalTokens = 0
        var compressedTotalPrompt = 0
        var compressedTotalCompletion = 0
        val compressedStartTime = System.currentTimeMillis()

        for ((index, question) in DemoData.longDialogueTopics.withIndex()) {
            addMessage(
                role = "user",
                content = question.take(60) + if (question.length > 60) "..." else "",
                metadata = "[${index + 1}/${DemoData.longDialogueTopics.size}]"
            )
            delayShort()
            onTypingStateChanged(true)
            delayMedium()

            try {
                val response = compressedAgent.processRequest(question)
                onTypingStateChanged(false)

                compressedTotalTokens += response.totalTokens ?: 0
                compressedTotalPrompt += response.promptTokens ?: 0
                compressedTotalCompletion += response.completionTokens ?: 0

                val metadata = DemoData.formatCompressionTestMetadata(
                    index + 1,
                    DemoData.longDialogueTopics.size,
                    response.promptTokens ?: 0,
                    response.completionTokens ?: 0,
                    response.totalTokens ?: 0,
                    compressedTotalTokens
                )

                addMessage(
                    role = "assistant",
                    content = response.content,
                    metadata = metadata,
                    promptTokens = response.promptTokens,
                    completionTokens = response.completionTokens,
                    totalTokens = response.totalTokens,
                    responseTimeMs = response.responseTimeMs
                )

                if (response.compressionStats != null && index % 5 == 0 && index > 0) {
                    val compressionLine = response.compressionStats.getFormatted()
                        .lines().drop(1).joinToString(" ").take(80)
                    addMessage(
                        role = "assistant",
                        content = "💾 $compressionLine",
                        metadata = "Статистика компрессии"
                    )
                }

                delay(500.milliseconds)

            } catch (e: Exception) {
                onTypingStateChanged(false)
                addMessage(
                    role = "assistant",
                    content = "❌ Ошибка: ${e.message}",
                    metadata = "Ошибка"
                )
            }
        }

        val compressedEndTime = System.currentTimeMillis()
        val compressedTime = compressedEndTime - compressedStartTime
        val compressedCost = compressedAgent.getTokenStats().estimatedCostUsd

        val tokensSaved = regularTotalTokens - compressedTotalTokens
        val tokensSavedPercent = if (regularTotalTokens > 0) {
            (tokensSaved.toDouble() / regularTotalTokens) * 100
        } else 0.0

        val compressionStats = compressedAgent.getCompressionStats()

        // ========== Сравнение результатов ==========
        addMessage(
            role = "assistant",
            content = "\n${"=".repeat(100)}\n📊 СРАВНИТЕЛЬНЫЙ АНАЛИЗ\n${"=".repeat(100)}",
            metadata = "Сравнение"
        )
        delayMedium()

        addMessage(
            role = "assistant",
            content = DemoData.getCompressionComparisonResults(
                regularTotalTokens, regularTotalPrompt, regularTotalCompletion,
                compressedTotalTokens, compressedTotalPrompt, compressedTotalCompletion,
                regularTime, compressedTime,
                regularCost, compressedCost,
                tokensSaved, tokensSavedPercent,
                compressionStats
            ),
            metadata = "Сравнительный анализ"
        )

        // ========== ТЕСТ 3: Адаптивная компрессия ==========
        addMessage(
            role = "assistant",
            content = DemoData.getAdaptiveCompressionHeader(),
            metadata = "Адаптивная компрессия"
        )
        delayMedium()

        testAdaptiveCompression()
    }

    private suspend fun testAdaptiveCompression() {
        val models = listOf(
            "nvidia/nemotron-3-super-120b-a12b:free" to 1_000_000,
            "openai/gpt-oss-20b:free" to 131_072,
            "google/gemma-4-26b-a4b-it:free" to 256_000
        )

        for ((modelId, contextSize) in models) {
            addMessage(
                role = "assistant",
                content = "\n📌 Тестируем модель: ${DemoData.getModelShortName(modelId)}\n   Контекстное окно: ${
                    formatNumber(
                        contextSize
                    )
                } токенов",
                metadata = "Адаптивный тест"
            )
            delayMedium()

            val keepLastMessages = when {
                contextSize >= 1_000_000 -> 15
                contextSize >= 200_000 -> 10
                else -> 6
            }

            val compressAfterTokens = when {
                contextSize >= 1_000_000 -> 6000
                contextSize >= 200_000 -> 4000
                else -> 3000
            }

            addMessage(
                role = "assistant",
                content = "   Адаптивные настройки: keepLast=$keepLastMessages, compressAfterTokens=${compressAfterTokens}",
                metadata = "Настройки"
            )

            val agent = CompressedLLMAgent(

                model = modelId,
                systemPrompt = "Ты полезный ассистент. Отвечай на русском языке кратко.",
                keepLastMessages = keepLastMessages,
                compressAfterTokens = compressAfterTokens
            )

            val testQuestions = DemoData.longDialogueTopics.take(8)

            for (question in testQuestions) {
                onTypingStateChanged(true)
                try {
                    val response = agent.processRequest(question)
                    onTypingStateChanged(false)
                    addMessage(
                        role = "assistant",
                        content = "   ✓ Ответ получен (${response.totalTokens ?: 0} токенов)",
                        metadata = "Ответ"
                    )
                    delay(300.milliseconds)
                } catch (e: Exception) {
                    onTypingStateChanged(false)
                    addMessage(
                        role = "assistant",
                        content = "   ⚠️ Ошибка: ${e.message?.take(50)}",
                        metadata = "Ошибка"
                    )
                }
            }

            val stats = agent.getCompressionStats()
            addMessage(
                role = "assistant",
                content = "   📊 Итоговая компрессия:\n${stats.getFormatted()}",
                metadata = "Итог"
            )

            delay(2.seconds)
        }
    }
}
