package com.llmapp.ui

import com.llmapp.agent.CompressedLLMAgent
import com.llmapp.agent.LLMAgent
import com.llmapp.api.ApiConfig
import com.llmapp.demo.DemoData
import com.llmapp.ui.models.ChatMessageUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

sealed class DemoType {
    object TokenComparison : DemoType()
    object CompressionComparison : DemoType()
}

class DemoManager(
    private val onMessageAdded: (ChatMessageUI) -> Unit,
    private val onDemoStarted: () -> Unit,
    private val onDemoFinished: () -> Unit,
    private val onTypingStateChanged: (Boolean) -> Unit
) {
    private val apiKey = ApiConfig.getApiKey()
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _isRunning = MutableStateFlow(false)

    private val _currentDemo = MutableStateFlow<DemoType?>(null)

    private val primaryModel = "openai/gpt-oss-20b:free"

    fun startTokenDemo() {
        if (_isRunning.value) return
        _isRunning.value = true
        _currentDemo.value = DemoType.TokenComparison
        onDemoStarted()

        scope.launch {
            try {
                runTokenComparison()
            } catch (e: Exception) {
                addMessage(
                    role = "assistant",
                    content = "❌ Ошибка при выполнении демонстрации: ${e.message}",
                    metadata = "Ошибка"
                )
            } finally {
                _isRunning.value = false
                _currentDemo.value = null
                onDemoFinished()
            }
        }
    }

    fun startCompressionDemo() {
        if (_isRunning.value) return
        _isRunning.value = true
        _currentDemo.value = DemoType.CompressionComparison
        onDemoStarted()

        scope.launch {
            try {
                runCompressionComparison()
            } catch (e: Exception) {
                addMessage(
                    role = "assistant",
                    content = "❌ Ошибка при выполнении демонстрации: ${e.message}",
                    metadata = "Ошибка"
                )
            } finally {
                _isRunning.value = false
                _currentDemo.value = null
                onDemoFinished()
            }
        }
    }

    private suspend fun runTokenComparison() {
        addMessage(
            role = "assistant",
            content = DemoData.getTokenDemoIntro(),
            metadata = "ДЕМОНСТРАЦИЯ ТОКЕНОВ"
        )
        delay(1.seconds)

        val agent = LLMAgent(
            apiKey = apiKey,
            model = primaryModel,
            systemPrompt = "Ты полезный ассистент. Отвечай на русском языке кратко и по делу. Отвечай максимально лаконично.",
            maxHistorySize = 100
        )

        // ТЕСТ 1: Короткий диалог
        addMessage(
            role = "assistant",
            content = DemoData.getShortDialogueIntro(),
            metadata = "Тест 1/3"
        )
        delay(1.seconds)

        for (message in DemoData.shortDialogue) {
            addMessage(role = "user", content = message)
            delay(300.milliseconds)
            onTypingStateChanged(true)
            delay(500.milliseconds)

            val response = agent.processRequest(message)

            val stats = agent.getTokenStats()
            val metadata = DemoData.formatTokenMetadata(
                response.promptTokens ?: 0,
                response.completionTokens ?: 0,
                response.totalTokens ?: 0,
                stats.totalTokens,
                stats.estimatedCostUsd
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
            onTypingStateChanged(false)
            delay(500.milliseconds)
        }

        // Промежуточная статистика
        val stats1 = agent.getTokenStats()
        addMessage(
            role = "assistant",
            content = DemoData.getShortDialogueSummary(
                stats1.totalTokens,
                stats1.totalPromptTokens,
                stats1.totalCompletionTokens,
                stats1.estimatedCostUsd
            ),
            metadata = "Промежуточная статистика"
        )
        delay(2.seconds)

        // ТЕСТ 2: Длинный диалог
        agent.clearHistory()

        addMessage(
            role = "assistant",
            content = DemoData.getLongDialogueIntro(DemoData.longDialogueTopics.size),
            metadata = "Тест 2/3"
        )
        delay(1.seconds)

        for ((index, question) in DemoData.longDialogueTopics.withIndex()) {
            addMessage(
                role = "user",
                content = question.take(80) + if (question.length > 80) "..." else ""
            )
            delay(300.milliseconds)
            onTypingStateChanged(true)
            delay(500.milliseconds)

            val response = try {
                agent.processRequest(question)
            } catch (e: Exception) {
                addMessage(
                    role = "assistant",
                    content = "⚠️ Ошибка: ${e.message?.take(100)}. Продолжаем...",
                    metadata = "Ошибка"
                )
                onTypingStateChanged(false)
                delay(500.milliseconds)
                continue
            }

            val stats = agent.getTokenStats()
            val contextStatus = agent.getContextWarning()
            val metadata = DemoData.formatLongDialogueMetadata(
                index + 1,
                DemoData.longDialogueTopics.size,
                response.promptTokens ?: 0,
                response.completionTokens ?: 0,
                response.totalTokens ?: 0,
                stats.totalTokens,
                stats.estimatedCostUsd,
                contextStatus
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
            onTypingStateChanged(false)
            delay(300.milliseconds)
        }

        // Финальная статистика
        val finalStats = agent.getTokenStats()
        addMessage(
            role = "assistant",
            content = DemoData.getFinalTokenStats(
                finalStats.requestCount,
                finalStats.totalTokens,
                finalStats.totalPromptTokens,
                finalStats.totalCompletionTokens,
                finalStats.estimatedCostUsd
            ),
            metadata = "Финальная статистика"
        )

        // ТЕСТ 3: Дополнительные запросы
        addMessage(
            role = "assistant",
            content = DemoData.getExtraQuestionsIntro(),
            metadata = "Тест 3/3"
        )
        delay(1.seconds)

        for (question in DemoData.extraQuestions) {
            addMessage(role = "user", content = question)
            delay(300.milliseconds)
            onTypingStateChanged(true)
            delay(500.milliseconds)

            val response = agent.processRequest(question)
            val stats = agent.getTokenStats()

            addMessage(
                role = "assistant",
                content = response.content,
                metadata = "📊 Всего токенов: ${stats.totalTokens} | 💰 ${
                    String.format(
                        "%.6f",
                        stats.estimatedCostUsd
                    )
                }",
                promptTokens = response.promptTokens,
                completionTokens = response.completionTokens,
                totalTokens = response.totalTokens,
                responseTimeMs = response.responseTimeMs
            )
            onTypingStateChanged(false)
            delay(300.milliseconds)
        }

        val finalStats2 = agent.getTokenStats()

        addMessage(
            role = "assistant",
            content = DemoData.getTokenDemoConclusion(
                finalStats2.requestCount,
                finalStats2.totalTokens,
                finalStats2.totalPromptTokens,
                finalStats2.totalCompletionTokens,
                finalStats2.estimatedCostUsd,
                agent.getContextWarning()
            ),
            metadata = "Итоговый анализ"
        )
    }

    private suspend fun runCompressionComparison() {
        addMessage(
            role = "assistant",
            content = DemoData.getCompressionDemoIntro(DemoData.longDialogueTopics.size),
            metadata = "ДЕМОНСТРАЦИЯ КОМПРЕССИИ"
        )
        delay(2.seconds)

        // Тест 1: Без компрессии
        addMessage(
            role = "assistant",
            content = DemoData.getNoCompressionTestIntro(),
            metadata = "Тест без компрессии"
        )
        delay(1.seconds)

        val regularAgent = LLMAgent(
            apiKey = apiKey,
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
                content = question.take(60) + if (question.length > 60) "..." else ""
            )
            delay(300.milliseconds)
            onTypingStateChanged(true)
            delay(500.milliseconds)

            val response = regularAgent.processRequest(question)

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
            onTypingStateChanged(false)
            delay(300.milliseconds)
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

        // Тест 2: С компрессией
        addMessage(
            role = "assistant",
            content = DemoData.getCompressionTestIntro(8, 6),
            metadata = "Тест с компрессией"
        )
        delay(1.seconds)

        val compressedAgent = CompressedLLMAgent(
            apiKey = apiKey,
            model = primaryModel,
            systemPrompt = "Ты полезный ассистент. Отвечай на русском языке кратко и по делу.",
            maxHistorySize = 200,
            keepLastMessages = 8,
            summarizeEvery = 6
        )
        compressedAgent.compressionEnabled = true

        var compressedTotalTokens = 0
        var compressedTotalPrompt = 0
        var compressedTotalCompletion = 0
        val compressedStartTime = System.currentTimeMillis()

        for ((index, question) in DemoData.longDialogueTopics.withIndex()) {
            addMessage(
                role = "user",
                content = question.take(60) + if (question.length > 60) "..." else ""
            )
            delay(300.milliseconds)
            onTypingStateChanged(true)
            delay(500.milliseconds)

            val response = compressedAgent.processRequest(question)

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
            onTypingStateChanged(false)
            delay(300.milliseconds)
        }

        val compressedEndTime = System.currentTimeMillis()
        val compressedTime = compressedEndTime - compressedStartTime
        val compressedCost = compressedAgent.getTokenStats().estimatedCostUsd

        val tokensSaved = regularTotalTokens - compressedTotalTokens
        val tokensSavedPercent = if (regularTotalTokens > 0) {
            (tokensSaved.toDouble() / regularTotalTokens) * 100
        } else 0.0

        val compressionStats = compressedAgent.getCompressionStats()

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
    }

    private suspend fun addMessage(
        role: String,
        content: String,
        metadata: String? = null,
        promptTokens: Int? = null,
        completionTokens: Int? = null,
        totalTokens: Int? = null,
        responseTimeMs: Long? = null
    ) {
        val message = ChatMessageUI(
            id = UUID.randomUUID().toString(),
            role = role,
            content = content,
            metadata = metadata,
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            totalTokens = totalTokens,
            responseTimeMs = responseTimeMs
        )
        onMessageAdded(message)
        delay(300.milliseconds)
    }
}
