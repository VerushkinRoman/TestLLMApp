package com.llmapp.ui

import com.llmapp.agent.CompressedLLMAgent
import com.llmapp.agent.ContextStrategyType
import com.llmapp.agent.LLMAgent
import com.llmapp.agent.StrategicLLMAgent
import com.llmapp.api.ApiConfig
import com.llmapp.demo.DemoData
import com.llmapp.demo.StrategyTestScenario
import com.llmapp.model.TokenStats
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
    object StrategyComparison : DemoType()
}

class DemoManager(
    private val onMessageAdded: (ChatMessageUI) -> Unit,
    private val onDemoStarted: () -> Unit,
    private val onDemoFinished: () -> Unit,
    private val onTypingStateChanged: (Boolean) -> Unit,
    private val onStatsUpdated: ((TokenStats) -> Unit)? = null
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
                    content = "❌ Ошибка: ${e.message}",
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
                    content = "❌ Ошибка: ${e.message}",
                    metadata = "Ошибка"
                )
            } finally {
                _isRunning.value = false
                _currentDemo.value = null
                onDemoFinished()
            }
        }
    }

    fun startStrategyDemo() {
        if (_isRunning.value) return
        _isRunning.value = true
        _currentDemo.value = DemoType.StrategyComparison
        onDemoStarted()

        scope.launch {
            try {
                runStrategyComparison()
            } catch (e: Exception) {
                addMessage(
                    role = "assistant",
                    content = "❌ Ошибка при выполнении демонстрации стратегий: ${e.message}",
                    metadata = "Ошибка"
                )
            } finally {
                _isRunning.value = false
                _currentDemo.value = null
                onDemoFinished()
            }
        }
    }

    private suspend fun runStrategyComparison() {
        addMessage(
            role = "assistant",
            content = """
            🧪 ЗАПУСК ДЕМОНСТРАЦИИ СТРАТЕГИЙ УПРАВЛЕНИЯ КОНТЕКСТОМ
            
            Будут протестированы 3 стратегии:
            1. Sliding Window - скользящее окно (только последние N сообщений)
            2. Sticky Facts - сохранение ключевых фактов
            3. Branching - ветвление диалога
            
            Сценарий: сбор требований к KMP приложению для заметок (12 вопросов)
        """.trimIndent(),
            metadata = "ДЕМОНСТРАЦИЯ СТРАТЕГИЙ"
        )
        delay(2.seconds)

        val apiKey = ApiConfig.getApiKey()
        val model = "openai/gpt-oss-20b:free"

        // Тест 1: Sliding Window
        addMessage(
            role = "assistant",
            content = "\n" + "━".repeat(60) + "\n🔵 ТЕСТ 1: Sliding Window Strategy\n" + "━".repeat(
                60
            ),
            metadata = "Тест 1/3"
        )
        delay(1.seconds)

        runStrategyTest(
            apiKey = apiKey,
            model = model,
            strategy = ContextStrategyType.SLIDING_WINDOW,
            strategyName = "Sliding Window",
            scenario = StrategyTestScenario.TZQuestions
        )

        delay(2.seconds)

        // Тест 2: Sticky Facts
        addMessage(
            role = "assistant",
            content = "\n" + "━".repeat(60) + "\n🟢 ТЕСТ 2: Sticky Facts Strategy\n" + "━".repeat(60),
            metadata = "Тест 2/3"
        )
        delay(1.seconds)

        runStrategyTest(
            apiKey = apiKey,
            model = model,
            strategy = ContextStrategyType.STICKY_FACTS,
            strategyName = "Sticky Facts",
            scenario = StrategyTestScenario.TZQuestions
        )

        delay(2.seconds)

        // Тест 3: Branching
        addMessage(
            role = "assistant",
            content = "\n" + "━".repeat(60) + "\n🟣 ТЕСТ 3: Branching Strategy\n" + "━".repeat(60),
            metadata = "Тест 3/3"
        )
        delay(1.seconds)

        runStrategyTest(
            apiKey = apiKey,
            model = model,
            strategy = ContextStrategyType.BRANCHING,
            strategyName = "Branching",
            scenario = StrategyTestScenario.TZQuestions
        )

        delay(2.seconds)

        addMessage(
            role = "assistant",
            content = """
            
            ${"═".repeat(100)}
            📊 СРАВНИТЕЛЬНЫЙ АНАЛИЗ СТРАТЕГИЙ
            ${"═".repeat(100)}
            
            💡 РЕКОМЕНДАЦИИ:
            • Для коротких диалогов (до 10 сообщений) - любая стратегия подходит
            • Для длинных диалогов (20+ сообщений) - используйте Sticky Facts
            • Для исследовательских задач и A/B тестирования - Branching
            • Если важна предсказуемость расхода токенов - Sliding Window
            
        """.trimIndent(),
            metadata = "Сравнительный анализ"
        )
    }

    private suspend fun runStrategyTest(
        apiKey: String,
        model: String,
        strategy: ContextStrategyType,
        strategyName: String,
        scenario: List<String>
    ) {
        val agent = StrategicLLMAgent(
            apiKey = apiKey,
            model = model,
            systemPrompt = "Ты опытный технический архитектор. Отвечай кратко, по делу, на русском языке."
        )

        agent.setStrategy(strategy)

        addMessage(
            role = "assistant",
            content = "📌 Стратегия: ${agent.getCurrentStrategyName()}\n📝 Начинаем диалог из ${scenario.size} вопросов...",
            metadata = "Начало теста"
        )
        delay(500.milliseconds)

        var cumulativePromptTokens = 0
        var cumulativeCompletionTokens = 0
        var cumulativeTotalTokens = 0

        for ((index, question) in scenario.withIndex()) {
            addMessage(
                role = "user",
                content = question,
                metadata = "[${index + 1}/${scenario.size}]"
            )
            delay(500.milliseconds)

            onTypingStateChanged(true)
            delay(500.milliseconds)

            try {
                val response = agent.processRequest(question)

                val promptTokens = response.promptTokens ?: 0
                val completionTokens = response.completionTokens ?: 0
                val totalTokens = response.totalTokens ?: 0

                cumulativePromptTokens += promptTokens
                cumulativeCompletionTokens += completionTokens
                cumulativeTotalTokens += totalTokens

                val currentStats = TokenStats(
                    totalPromptTokens = cumulativePromptTokens,
                    totalCompletionTokens = cumulativeCompletionTokens,
                    totalTokens = cumulativeTotalTokens,
                    estimatedCostUsd = (cumulativePromptTokens + cumulativeCompletionTokens) * 0.3e-6,
                    requestCount = index + 1
                )

                onStatsUpdated?.invoke(currentStats)

                val metadata = buildString {
                    append("📊 Токены: ↑$promptTokens ↓$completionTokens Σ$totalTokens")
                    append(" | Всего за тест: ↑$cumulativePromptTokens ↓$cumulativeCompletionTokens Σ$cumulativeTotalTokens")
                    append(" | Стратегия: ${response.strategyUsed}")
                }

                addMessage(
                    role = "assistant",
                    content = response.content,
                    metadata = metadata,
                    promptTokens = promptTokens,
                    completionTokens = completionTokens,
                    totalTokens = totalTokens,
                    responseTimeMs = response.responseTimeMs
                )

                if (strategy == ContextStrategyType.STICKY_FACTS && index % 3 == 0 && index > 0) {
                    val facts = agent.getFacts()
                    if (facts.isNotEmpty()) {
                        val factsText =
                            facts.entries.joinToString("\n") { "• ${it.key}: ${it.value.take(80)}" }
                        addMessage(
                            role = "assistant",
                            content = "📝 Извлеченные факты из диалога:\n$factsText",
                            metadata = "Автоматическое извлечение фактов"
                        )
                    }
                }

                delay(500.milliseconds)

            } catch (e: Exception) {
                addMessage(
                    role = "assistant",
                    content = "❌ Ошибка: ${e.message}",
                    metadata = "Ошибка"
                )
            } finally {
                onTypingStateChanged(false)
            }
        }

        val finalStats = TokenStats(
            totalPromptTokens = cumulativePromptTokens,
            totalCompletionTokens = cumulativeCompletionTokens,
            totalTokens = cumulativeTotalTokens,
            estimatedCostUsd = (cumulativePromptTokens + cumulativeCompletionTokens) * 0.3e-6,
            requestCount = scenario.size
        )
        onStatsUpdated?.invoke(finalStats)

        addMessage(
            role = "assistant",
            content = """
            
            📊 ИТОГ ТЕСТА ($strategyName)
            ${"─".repeat(40)}
            • Запросов: ${finalStats.requestCount}
            • Всего токенов: ${finalStats.totalTokens}
            • Prompt: ${finalStats.totalPromptTokens} | Completion: ${finalStats.totalCompletionTokens}
            • Стоимость: ${finalStats.getFormattedCost()}
            
        """.trimIndent(),
            metadata = "Итоговая статистика"
        )
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
