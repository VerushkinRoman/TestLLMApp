package com.llmapp.demo.manager

import com.llmapp.agent.LLMAgent
import com.llmapp.api.ApiConfig
import com.llmapp.demo.DemoData
import com.llmapp.ui.models.ChatMessageUI
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/**
 * Демонстрация отслеживания токенов
 */
class TokenDemoRunner(
    onMessageAdded: (ChatMessageUI) -> Unit,
    onTypingStateChanged: (Boolean) -> Unit,
    private val onStatsUpdated: ((com.llmapp.model.TokenStats) -> Unit)? = null,
    private val onTokenHistoryUpdated: ((List<com.llmapp.agent.TokenSnapshot>) -> Unit)? = null,
    private val onContextWarningUpdated: ((String) -> Unit)? = null
) : BaseDemoRunner(onMessageAdded, onTypingStateChanged) {

    override suspend fun run() {
        val apiKey = ApiConfig.getApiKey()
        val primaryModel = "openai/gpt-oss-20b:free"

        // ========== ВВЕДЕНИЕ ==========
        addMessage(
            role = "assistant",
            content = DemoData.getTokenDemoHeader() + "\n\n" + DemoData.getTokenDemoIntro(),
            metadata = "ДЕМОНСТРАЦИЯ ТОКЕНОВ"
        )
        delayLong()

        val agent = LLMAgentWithFallback(
            apiKey = apiKey,
            initialModel = primaryModel,
            systemPrompt = "Ты полезный ассистент. Отвечай на русском языке.",
            maxHistorySize = 100
        )

        // Собираем статистику
        val shortDialogueStats = mutableListOf<Triple<Int, Int, Int>>()
        val longDialogueStats = mutableListOf<Triple<Int, Int, Int>>()
        val extraStats = mutableListOf<Triple<Int, Int, Int>>()

        // ========== ТЕСТ 1: Короткий диалог ==========
        addMessage(
            role = "assistant",
            content = DemoData.getShortDialogueIntro(),
            metadata = "Тест 1/3"
        )
        delayMedium()

        for (message in DemoData.shortDialogue) {
            addMessage(role = "user", content = message)
            delayShort()
            onTypingStateChanged(true)
            delayMedium()

            try {
                val response = agent.processRequest(message)
                onTypingStateChanged(false)

                val cumulativeStats = agent.getTokenStats()
                val metadata = DemoData.formatTokenMetadata(
                    response.promptTokens ?: 0,
                    response.completionTokens ?: 0,
                    response.totalTokens ?: 0,
                    cumulativeStats.totalTokens,
                    cumulativeStats.estimatedCostUsd
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

                shortDialogueStats.add(
                    Triple(
                        response.promptTokens ?: 0,
                        response.completionTokens ?: 0,
                        response.totalTokens ?: 0
                    )
                )

                updateStats(agent)

            } catch (e: Exception) {
                onTypingStateChanged(false)
                addMessage(
                    role = "assistant",
                    content = "❌ Ошибка: ${e.message}",
                    metadata = "Ошибка"
                )
            }
            delayShort()
        }

        val stats1 = agent.getTokenStats()
        addMessage(
            role = "assistant",
            content = DemoData.getShortDialogueSummary(
                stats1.totalTokens,
                stats1.totalPromptTokens,
                stats1.totalCompletionTokens,
                stats1.estimatedCostUsd
            ),
            metadata = "Итог короткого диалога"
        )
        delayLong()

        // ========== ТЕСТ 2: Длинный диалог ==========
        addMessage(
            role = "assistant",
            content = DemoData.getLongDialogueIntro(DemoData.longDialogueTopics.size),
            metadata = "Тест 2/3"
        )
        delayMedium()

        agent.clearHistory()
        longDialogueStats.clear()

        for ((index, question) in DemoData.longDialogueTopics.withIndex()) {
            addMessage(
                role = "user",
                content = question.take(50) + if (question.length > 50) "..." else "",
                metadata = "[${index + 1}/${DemoData.longDialogueTopics.size}]"
            )
            delayShort()
            onTypingStateChanged(true)
            delayMedium()

            try {
                val response = agent.processRequest(question)
                onTypingStateChanged(false)

                val cumulativeStats = agent.getTokenStats()
                val contextWarning = agent.getContextWarning()
                val metadata = DemoData.formatLongDialogueMetadata(
                    index + 1,
                    DemoData.longDialogueTopics.size,
                    response.promptTokens ?: 0,
                    response.completionTokens ?: 0,
                    response.totalTokens ?: 0,
                    cumulativeStats.totalTokens,
                    cumulativeStats.estimatedCostUsd,
                    contextWarning
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

                longDialogueStats.add(
                    Triple(
                        response.promptTokens ?: 0,
                        response.completionTokens ?: 0,
                        response.totalTokens ?: 0
                    )
                )

                updateStats(agent)

            } catch (e: Exception) {
                onTypingStateChanged(false)
                addMessage(
                    role = "assistant",
                    content = "❌ Ошибка: ${e.message}",
                    metadata = "Ошибка"
                )
            }
            delayShort()
        }

        val finalStats = agent.getTokenStats()
        val contextStatusMessage = when (agent.getContextStatus()) {
            com.llmapp.agent.ContextStatus.SAFE -> "✅ Безопасно"
            com.llmapp.agent.ContextStatus.WARNING -> "⚠️ Предупреждение"
            com.llmapp.agent.ContextStatus.CRITICAL -> "🔴 Критично"
            com.llmapp.agent.ContextStatus.OVERFLOW -> "💥 Переполнение"
        }

        addMessage(
            role = "assistant",
            content = DemoData.getLongDialogueFinalStats(
                finalStats.totalTokens,
                finalStats.totalPromptTokens,
                finalStats.totalCompletionTokens,
                finalStats.estimatedCostUsd,
                contextStatusMessage
            ),
            metadata = "Итог длинного диалога"
        )
        delayLong()

        // ========== ТЕСТ 3: Дополнительные запросы ==========
        addMessage(
            role = "assistant",
            content = DemoData.getExtraQuestionsIntro(),
            metadata = "Тест 3/3"
        )
        delayMedium()

        for (question in DemoData.extraQuestions) {
            addMessage(role = "user", content = question)
            delayShort()
            onTypingStateChanged(true)
            delayMedium()

            try {
                val response = agent.processRequest(question)
                onTypingStateChanged(false)

                val cumulativeStats = agent.getTokenStats()
                val metadata = DemoData.formatTokenMetadata(
                    response.promptTokens ?: 0,
                    response.completionTokens ?: 0,
                    response.totalTokens ?: 0,
                    cumulativeStats.totalTokens,
                    cumulativeStats.estimatedCostUsd
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

                extraStats.add(
                    Triple(
                        response.promptTokens ?: 0,
                        response.completionTokens ?: 0,
                        response.totalTokens ?: 0
                    )
                )

            } catch (e: Exception) {
                onTypingStateChanged(false)
                addMessage(
                    role = "assistant",
                    content = "❌ Ошибка: ${e.message}",
                    metadata = "Ошибка"
                )
            }
            delayShort()
        }

        // ========== ИТОГОВАЯ СТАТИСТИКА ==========
        val finalStats2 = agent.getTokenStats()
        addMessage(
            role = "assistant",
            content = DemoData.getFinalTokenStats(
                finalStats2.requestCount,
                finalStats2.totalTokens,
                finalStats2.totalPromptTokens,
                finalStats2.totalCompletionTokens,
                finalStats2.estimatedCostUsd
            ),
            metadata = "Финальная статистика"
        )
        delayLong()

        // ========== АНАЛИЗ ОТ LLM ==========
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

        addMessage(
            role = "assistant",
            content = "📊 ПЕРЕДАЕМ СТАТИСТИКУ МОДЕЛИ ДЛЯ АНАЛИЗА",
            metadata = "Анализ"
        )
        delayMedium()

        addMessage(
            role = "assistant",
            content = statsReport,
            metadata = "Статистика для анализа"
        )
        delayLong()

        // Создаем нового агента для анализа
        val analysisAgent = LLMAgentWithFallback(
            apiKey = apiKey,
            initialModel = primaryModel,
            systemPrompt = "Ты эксперт по анализу данных. Делай подробные, структурированные выводы.",
            maxHistorySize = 50
        )

        addMessage(
            role = "assistant",
            content = "🤖 МОДЕЛЬ АНАЛИЗИРУЕТ СТАТИСТИКУ...",
            metadata = "Анализ"
        )
        delayMedium()

        try {
            val analysisResponse = analysisAgent.processRequest(statsReport)
            addMessage(
                role = "assistant",
                content = "📊 ВЫВОДЫ МОДЕЛИ:\n\n${analysisResponse.content}",
                metadata = "Анализ LLM",
                promptTokens = analysisResponse.promptTokens,
                completionTokens = analysisResponse.completionTokens,
                totalTokens = analysisResponse.totalTokens,
                responseTimeMs = analysisResponse.responseTimeMs
            )
        } catch (e: Exception) {
            addMessage(
                role = "assistant",
                content = "❌ Ошибка при анализе: ${e.message}",
                metadata = "Ошибка"
            )
        }

        // ========== ЗАКЛЮЧЕНИЕ ==========
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

    private fun updateStats(agent: LLMAgentWithFallback) {
        onStatsUpdated?.invoke(agent.getTokenStats())
        onTokenHistoryUpdated?.invoke(agent.getTokenHistory())
        onContextWarningUpdated?.invoke(agent.getContextWarning())
    }
}

/**
 * Вспомогательный класс с fallback моделями
 */
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
    fun getTokenHistory() = currentAgent.getTokenHistory()
}
