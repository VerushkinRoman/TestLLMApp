package com.llmapp.ui

import com.llmapp.agent.CompressedLLMAgent
import com.llmapp.agent.ContextStrategyType
import com.llmapp.agent.LLMAgent
import com.llmapp.agent.MemoryAwareAgent
import com.llmapp.agent.StrategicLLMAgent
import com.llmapp.api.ApiConfig
import com.llmapp.demo.DemoData
import com.llmapp.demo.StrategyTestScenario
import com.llmapp.memory.ProjectConstraints
import com.llmapp.memory.ResponseStyle
import com.llmapp.memory.TaskState
import com.llmapp.memory.UserProfile
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
    object MemoryComparison : DemoType()
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

    fun startMemoryDemo() {
        if (_isRunning.value) return
        _isRunning.value = true
        _currentDemo.value = DemoType.MemoryComparison
        onDemoStarted()

        scope.launch {
            try {
                runMemoryDemonstration()
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

        var cumulativePromptTokens = 0
        var cumulativeCompletionTokens = 0
        var cumulativeTotalTokens = 0
        var requestCounter = 0

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

            cumulativePromptTokens += response.promptTokens ?: 0
            cumulativeCompletionTokens += response.completionTokens ?: 0
            cumulativeTotalTokens += response.totalTokens ?: 0
            requestCounter++

            val stats = agent.getTokenStats()
            val currentStats = TokenStats(
                totalPromptTokens = cumulativePromptTokens,
                totalCompletionTokens = cumulativeCompletionTokens,
                totalTokens = cumulativeTotalTokens,
                estimatedCostUsd = stats.estimatedCostUsd,
                requestCount = requestCounter
            )
            onStatsUpdated?.invoke(currentStats)

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

            cumulativePromptTokens += response.promptTokens ?: 0
            cumulativeCompletionTokens += response.completionTokens ?: 0
            cumulativeTotalTokens += response.totalTokens ?: 0
            requestCounter++

            val stats = agent.getTokenStats()
            val currentStats = TokenStats(
                totalPromptTokens = cumulativePromptTokens,
                totalCompletionTokens = cumulativeCompletionTokens,
                totalTokens = cumulativeTotalTokens,
                estimatedCostUsd = stats.estimatedCostUsd,
                requestCount = requestCounter
            )
            onStatsUpdated?.invoke(currentStats)

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

            cumulativePromptTokens += response.promptTokens ?: 0
            cumulativeCompletionTokens += response.completionTokens ?: 0
            cumulativeTotalTokens += response.totalTokens ?: 0
            requestCounter++

            val stats = agent.getTokenStats()
            val currentStats = TokenStats(
                totalPromptTokens = cumulativePromptTokens,
                totalCompletionTokens = cumulativeCompletionTokens,
                totalTokens = cumulativeTotalTokens,
                estimatedCostUsd = stats.estimatedCostUsd,
                requestCount = requestCounter
            )
            onStatsUpdated?.invoke(currentStats)

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

            val currentStats = TokenStats(
                totalPromptTokens = regularTotalPrompt,
                totalCompletionTokens = regularTotalCompletion,
                totalTokens = regularTotalTokens,
                estimatedCostUsd = (regularTotalPrompt + regularTotalCompletion) * 0.3e-6,
                requestCount = index + 1
            )
            onStatsUpdated?.invoke(currentStats)

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

            val currentStats = TokenStats(
                totalPromptTokens = compressedTotalPrompt,
                totalCompletionTokens = compressedTotalCompletion,
                totalTokens = compressedTotalTokens,
                estimatedCostUsd = (compressedTotalPrompt + compressedTotalCompletion) * 0.3e-6,
                requestCount = index + 1
            )
            onStatsUpdated?.invoke(currentStats)

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

    private suspend fun runMemoryDemonstration() {
        addMessage(
            role = "assistant",
            content = """
        🧠 ЗАПУСК ПОЛНОЙ ДЕМОНСТРАЦИИ ТРЕХСЛОЙНОЙ МОДЕЛИ ПАМЯТИ
        
        Эта демонстрация покажет ВСЕ возможности системы памяти:
        
        1. Настройку профиля пользователя
        2. Настройку ограничений проекта
        3. Создание рабочей задачи с контекстом
        4. Добавление решений в рабочую память
        5. Добавление знаний в долговременную память
        6. Полноценный диалог по проекту (8 вопросов)
        7. Работу с контекстом (updateWorkingContext/getWorkingContext)
        8. Сохранение решений в долговременную память
        9. Сравнение ответов С памятью и БЕЗ памяти
        10. Очистку контекста рабочей памяти
        
        Начинаем...
        """.trimIndent(),
            metadata = "ДЕМОНСТРАЦИЯ ПАМЯТИ"
        )
        delay(4.seconds)

        // ========== СОЗДАЕМ АГЕНТА С ПАМЯТЬЮ ==========
        val agent = MemoryAwareAgent(
            apiKey = apiKey,
            model = primaryModel,
            systemPrompt = "Ты опытный технический архитектор. Отвечай на русском языке, по делу, с примерами кода если нужно."
        )

        // Функция для обновления статистики
        fun updateStats() {
            val stats = agent.getTokenStats()
            val currentStats = TokenStats(
                totalPromptTokens = stats.totalPromptTokens,
                totalCompletionTokens = stats.totalCompletionTokens,
                totalTokens = stats.totalTokens,
                estimatedCostUsd = stats.estimatedCostUsd,
                requestCount = stats.requestCount
            )
            onStatsUpdated?.invoke(currentStats)
        }

        // ========== ШАГ 1: ПРОФИЛЬ ПОЛЬЗОВАТЕЛЯ ==========
        addMessage(
            role = "assistant",
            content = """
        ${"═".repeat(80)}
        📝 ШАГ 1: НАСТРОЙКА ПРОФИЛЯ ПОЛЬЗОВАТЕЛЯ
        ${"═".repeat(80)}
        
        Сохраняем информацию о пользователе в ДОЛГОВРЕМЕННУЮ ПАМЯТЬ.
        Эти данные будут использоваться во всех диалогах и сохранятся между сессиями.
        """.trimIndent(),
            metadata = "Шаг 1/10"
        )
        delay(3.seconds)

        val profile = UserProfile(
            name = "Алексей",
            experience = "Middle Android разработчик",
            preferredStyle = ResponseStyle.TECHNICAL,
            preferredTech = listOf("Kotlin", "Compose", "KMP", "Ktor", "Flow", "Coroutines"),
            commonGoals = listOf(
                "Изучить разработку агентов",
                "Улучшить архитектуру",
                "Перейти на KMP"
            ),
            customNotes = "Предпочитаю примеры кода и практические решения. Нужны объяснения почему выбран тот или иной подход."
        )
        agent.updateProfile(profile)

        addMessage(
            role = "assistant",
            content = """
        ✅ ПРОФИЛЬ СОХРАНЕН:
        
        👤 Имя: ${profile.name}
        📊 Опыт: ${profile.experience}
        🛠️ Технологии: ${profile.preferredTech.joinToString(", ")}
        🎯 Цели: ${profile.commonGoals.joinToString(", ")}
        📝 Заметки: ${profile.customNotes}
        
        📁 Файл: ~/.llm_memory/profile.md
        """.trimIndent(),
            metadata = "Профиль сохранен"
        )
        delay(4.seconds)

        // ========== ШАГ 2: ОГРАНИЧЕНИЯ ПРОЕКТА ==========
        addMessage(
            role = "assistant",
            content = """
        ${"═".repeat(80)}
        🔧 ШАГ 2: НАСТРОЙКА ОГРАНИЧЕНИЙ ПРОЕКТА
        ${"═".repeat(80)}
        
        Сохраняем технические ограничения в ДОЛГОВРЕМЕННУЮ ПАМЯТЬ.
        Агент будет учитывать их при генерации ответов.
        """.trimIndent(),
            metadata = "Шаг 2/10"
        )
        delay(3.seconds)

        val constraints = ProjectConstraints(
            techStack = listOf("Kotlin", "KMP", "Compose Multiplatform", "Ktor", "SQLDelight"),
            forbiddenTech = listOf("Java", "RxJava", "XML layouts (для новых UI)", "Java Spring"),
            architecture = "MVI с Clean Architecture (data/domain/presentation)",
            codingStandards = "Kotlin Coding Conventions, 4 пробела, максимальная длина строки 120 символов, использование Detekt",
            specialRules = """
            1. Все новые фичи должны иметь модульные тесты
            2. Интеграционные тесты обязательны для API слоя
            3. Код должен быть кроссплатформенным (where possible)
            4. Документация на русском языке для всех public API
            5. Использовать sealed classes для состояния и событий
        """.trimIndent()
        )
        agent.updateConstraints(constraints)

        addMessage(
            role = "assistant",
            content = """
        ✅ ОГРАНИЧЕНИЯ СОХРАНЕНЫ:
        
        📚 Разрешенный стек: ${constraints.techStack.joinToString(", ")}
        🚫 Запрещено: ${constraints.forbiddenTech.joinToString(", ")}
        🏗️ Архитектура: ${constraints.architecture}
        📏 Стандарты: ${constraints.codingStandards.take(80)}...
        📋 Особые правила: ${constraints.specialRules.take(100)}...
        
        📁 Файл: ~/.llm_memory/constraints.md
        """.trimIndent(),
            metadata = "Ограничения сохранены"
        )
        delay(4.seconds)

        // ========== ШАГ 3: ДОБАВЛЕНИЕ ЗНАНИЙ ==========
        addMessage(
            role = "assistant",
            content = """
        ${"═".repeat(80)}
        📚 ШАГ 3: ДОБАВЛЕНИЕ ЗНАНИЙ В БАЗУ ЗНАНИЙ
        ${"═".repeat(80)}
        
        Добавляем знания в Knowledge Base через addKnowledge().
        Эти знания будут доступны агенту во всех диалогах.
        """.trimIndent(),
            metadata = "Шаг 3/10"
        )
        delay(3.seconds)

        agent.addKnowledge(
            "лучшая_практика_kmp",
            "Для KMP проектов используй expect/actual механизм для платформенно-зависимого кода"
        )
        agent.addKnowledge(
            "кодинг_стандарт_проекта",
            "Используем 4 пробела для отступов, максимальная длина строки 120 символов. Запрещены табуляции."
        )
        agent.addKnowledge(
            "архитектурное_решение",
            "Применяем Clean Architecture с разделением на data (репозитории), domain (use cases), presentation (ViewModels + Compose UI)"
        )
        agent.addKnowledge(
            "тестирование_политика",
            "Unit тесты на use cases и ViewModels. Интеграционные тесты на репозитории с фейковыми API. E2E тесты на UI."
        )
        agent.addKnowledge(
            "корутины_правила",
            "Используем structured concurrency, никогда не используем GlobalScope, всегда привязываемся к lifecycle"
        )

        val bestPractice = agent.getKnowledge("лучшая_практика_kmp")
        addMessage(
            role = "assistant",
            content = """
        ✅ ЗНАНИЯ ДОБАВЛЕНЫ (${agent.getAllKnowledge().size} записей):
        
        • лучшая_практика_kmp: "$bestPractice"
        • кодинг_стандарт_проекта: "${agent.getKnowledge("кодинг_стандарт_проекта")}"
        • архитектурное_решение: "${agent.getKnowledge("архитектурное_решение")?.take(80)}..."
        • тестирование_политика: "${agent.getKnowledge("тестирование_политика")?.take(80)}..."
        • корутины_правила: "${agent.getKnowledge("корутины_правила")?.take(80)}..."
        
        📁 Файл: ~/.llm_memory/knowledge.md
        """.trimIndent(),
            metadata = "Знания добавлены"
        )
        delay(5.seconds)

        // ========== ШАГ 4: СОЗДАНИЕ ЗАДАЧИ И РАБОЧЕЙ ПАМЯТИ ==========
        addMessage(
            role = "assistant",
            content = """
        ${"═".repeat(80)}
        💼 ШАГ 4: СОЗДАНИЕ ЗАДАЧИ И РАБОЧЕЙ ПАМЯТИ
        ${"═".repeat(80)}
        
        Создаем задачу в РАБОЧЕЙ ПАМЯТИ.
        Рабочая память хранит контекст ТЕКУЩЕЙ задачи и временные решения.
        """.trimIndent(),
            metadata = "Шаг 4/10"
        )
        delay(3.seconds)

        agent.startNewTask(
            taskName = "Разработка чат-бота с трехслойной памятью",
            initialContext = mapOf(
                "требования" to "Чат-бот должен помнить контекст диалога между сессиями",
                "ограничения" to "Использовать только бесплатные модели OpenRouter",
                "бюджет" to "Бесплатно (используем free модели)",
                "срок" to "2 недели на MVP"
            )
        )
        agent.updateTaskState(TaskState.PLANNING)

        addMessage(
            role = "assistant",
            content = """
        ✅ ЗАДАЧА СОЗДАНА:
        
        📋 Название: ${agent.getWorkingMemory().taskName}
        📍 Состояние: ${agent.getWorkingMemory().currentState.displayName}
        🎯 Контекст задачи:
           • требования: ${agent.getWorkingMemory().contextData["requirements"] ?: agent.getWorkingMemory().contextData["требования"]}
           • ограничения: ${agent.getWorkingMemory().contextData["ограничения"]}
           • срок: ${agent.getWorkingMemory().contextData["срок"]}
        
        Рабочая память активна! Агент теперь знает, над чем мы работаем.
        """.trimIndent(),
            metadata = "Задача создана"
        )
        delay(4.seconds)

        // ========== ШАГ 5: РАБОТА С КОНТЕКСТОМ ==========
        addMessage(
            role = "assistant",
            content = """
        ${"═".repeat(80)}
        📝 ШАГ 5: РАБОТА С КОНТЕКСТОМ РАБОЧЕЙ ПАМЯТИ
        ${"═".repeat(80)}
        
        Добавляем динамические данные в контекст через updateWorkingContext().
        Эти данные характеризуют ТЕКУЩЕЕ состояние задачи.
        """.trimIndent(),
            metadata = "Шаг 5/10"
        )
        delay(3.seconds)

        agent.updateWorkingContext("текущий_спринт", "Спринт #42 - Разработка архитектуры памяти")
        agent.updateWorkingContext("дедлайн_спринта", "2026-07-15")
        agent.updateWorkingContext(
            "текущие_задачи",
            "1. Спроектировать MemoryAwareAgent, 2. Написать тесты, 3. Интегрировать с UI"
        )
        agent.updateWorkingContext("блокеры", "Нужно согласование API ключей OpenRouter")
        agent.updateWorkingContext(
            "выполнено",
            "Создан прототип MemoryAwareAgent, реализованы три слоя памяти"
        )

        addMessage(
            role = "assistant",
            content = """
        ✅ КОНТЕКСТ ОБНОВЛЕН (${agent.getWorkingMemory().contextData.size} ключей):
        
        • текущий_спринт = "${agent.getWorkingContext("текущий_спринт")}"
        • дедлайн_спринта = "${agent.getWorkingContext("дедлайн_спринта")}"
        • текущие_задачи = "${agent.getWorkingContext("текущие_задачи")?.take(60)}..."
        • блокеры = "${agent.getWorkingContext("блокеры")}"
        • выполнено = "${agent.getWorkingContext("выполнено")?.take(60)}..."
        """.trimIndent(),
            metadata = "Контекст обновлен"
        )
        delay(5.seconds)

        // ========== ШАГ 6: ДОБАВЛЕНИЕ РЕШЕНИЙ В РАБОЧУЮ ПАМЯТЬ ==========
        addMessage(
            role = "assistant",
            content = """
        ${"═".repeat(80)}
        💡 ШАГ 6: ДОБАВЛЕНИЕ РЕШЕНИЙ В РАБОЧУЮ ПАМЯТЬ
        ${"═".repeat(80)}
        
        Добавляем решения через addDecisionToWorkingMemory().
        Это временные решения для ТЕКУЩЕЙ задачи.
        """.trimIndent(),
            metadata = "Шаг 6/10"
        )
        delay(3.seconds)

        agent.addDecisionToWorkingMemory(
            topic = "Выбор архитектуры",
            decision = "Используем Clean Architecture с тремя слоями: data, domain, presentation",
            context = "Обсудили на техмитинге, выбрали из 3 вариантов"
        )
        agent.addDecisionToWorkingMemory(
            topic = "Управление состоянием",
            decision = "MVI с использованием StateFlow и SharedFlow. События через SharedFlow, состояние через StateFlow",
            context = "Выбрали после сравнения с MVVM"
        )
        agent.addDecisionToWorkingMemory(
            topic = "Хранение данных",
            decision = "SQLDelight для кроссплатформенного хранения истории диалогов",
            context = "Room не работает на iOS, SQLDelight - лучший выбор для KMP"
        )

        addMessage(
            role = "assistant",
            content = """
        ✅ РЕШЕНИЯ ДОБАВЛЕНЫ В РАБОЧУЮ ПАМЯТЬ (${agent.getWorkingMemory().decisions.size} решений):
        
        ${
                agent.getWorkingMemory().decisions.joinToString("\n\n") {
                    "   📌 ${it.topic}:\n      Решение: ${it.decision.take(80)}...\n      Контекст: ${it.context}"
                }
            }
        
        ⚠️ Эти решения временные! Они будут потеряны при создании новой задачи.
        Для постоянного хранения используйте saveDecisionToLongTerm().
        """.trimIndent(),
            metadata = "Решения добавлены"
        )
        delay(6.seconds)

        // ========== ШАГ 7: ДИАЛОГ ПО ПРОЕКТУ ==========
        addMessage(
            role = "assistant",
            content = """
        ${"═".repeat(80)}
        💬 ШАГ 7: ДИАЛОГ С АГЕНТОМ ПО ПРОЕКТУ
        ${"═".repeat(80)}
        
        Теперь зададим агенту 8 вопросов по проекту.
        Агент будет использовать ВСЕ три слоя памяти:
        • Долговременную (профиль, ограничения, знания)
        • Рабочую (задача, контекст, решения)
        • Краткосрочную (текущий диалог)
        """.trimIndent(),
            metadata = "Шаг 7/10"
        )
        delay(4.seconds)

        val projectQuestions = listOf(
            "Какая архитектура лучше всего подойдет для нашего чат-бота с памятью?",
            "Как организовать кроссплатформенное хранение истории диалогов?",
            "Как тестировать агента с памятью? Напиши пример теста.",
            "Какие могут быть проблемы с управлением состоянием в MVI?",
            "Как оптимизировать потребление токенов при длинных диалогах?",
            "Как организовать обработку ошибок при работе с OpenRouter API?",
            "Как сохранять контекст между перезапусками приложения?",
            "Какие best practices для корутин в KMP проекте?"
        )

        for ((index, question) in projectQuestions.withIndex()) {
            addMessage(role = "user", content = question)
            delay(1.seconds)

            onTypingStateChanged(true)
            delay(1.seconds)

            try {
                val response = agent.processRequest(question)
                updateStats()

                val usedMemoryLayers = buildString {
                    val layers = mutableListOf<String>()
                    if (response.memoryUsed.longTermUsed) layers.add("Долговременная")
                    if (response.memoryUsed.workingMemoryUsed) layers.add("Рабочая")
                    if (response.memoryUsed.shortTermUsed) layers.add("Краткосрочная")
                    append("🧠 ${layers.joinToString(" + ")}")
                }

                addMessage(
                    role = "assistant",
                    content = response.content,
                    metadata = "📊 [${index + 1}/${projectQuestions.size}] Токены: ↑${response.promptTokens} ↓${response.completionTokens} Σ${response.totalTokens} | $usedMemoryLayers",
                    promptTokens = response.promptTokens,
                    completionTokens = response.completionTokens,
                    totalTokens = response.totalTokens,
                    responseTimeMs = response.responseTimeMs
                )
            } catch (e: Exception) {
                addMessage(
                    role = "assistant",
                    content = "❌ Ошибка: ${e.message}",
                    metadata = "Ошибка"
                )
            } finally {
                onTypingStateChanged(false)
            }

            delay(2.seconds)
        }

        // ========== ШАГ 8: СОХРАНЕНИЕ РЕШЕНИЙ В LTM ==========
        addMessage(
            role = "assistant",
            content = """
        ${"═".repeat(80)}
        💾 ШАГ 8: СОХРАНЕНИЕ РЕШЕНИЙ В ДОЛГОВРЕМЕННУЮ ПАМЯТЬ
        ${"═".repeat(80)}
        
        Сохраняем важные решения через saveDecisionToLongTerm().
        Эти решения сохранятся НАВСЕГДА и будут доступны в следующих сессиях.
        """.trimIndent(),
            metadata = "Шаг 8/10"
        )
        delay(3.seconds)

        agent.saveDecisionToLongTerm(
            topic = "Архитектура финальное решение",
            decision = "Clean Architecture + MVI + трехслойная память (Short-term, Working, Long-term)",
            context = "На основе анализа требований и 8 вопросов из диалога"
        )
        agent.saveDecisionToLongTerm(
            topic = "Хранение данных финальное решение",
            decision = "SQLDelight для истории + Realm для кэша (опционально)",
            context = "Для кроссплатформенности и производительности"
        )
        agent.saveDecisionToLongTerm(
            topic = "Тестирование стратегия",
            decision = "Unit тесты на UseCases и ViewModels + Integration тесты на репозитории с TestCoroutineDispatcher",
            context = "Для обеспечения качества"
        )

        val allDecisions = agent.getAllDecisions()
        addMessage(
            role = "assistant",
            content = """
        ✅ РЕШЕНИЯ СОХРАНЕНЫ В LTM (${allDecisions.size} всего):
        
        Новые решения:
        ${
                allDecisions.takeLast(3)
                    .joinToString("\n") { "   • ${it.topic}: ${it.decision.take(80)}..." }
            }
        
        📁 Эти решения сохранены в ~/.llm_memory/knowledge.md
        и будут доступны при следующем запуске!
        """.trimIndent(),
            metadata = "Решения сохранены"
        )
        delay(5.seconds)

        // ========== ШАГ 9: СРАВНЕНИЕ С АГЕНТОМ БЕЗ ПАМЯТИ ==========
        addMessage(
            role = "assistant",
            content = """
    ${"═".repeat(80)}
    ⚖️ ШАГ 9: СРАВНЕНИЕ - АГЕНТ С ПАМЯТЬЮ VS АГЕНТ БЕЗ ПАМЯТИ
    ${"═".repeat(80)}
    
    Создадим второго агента БЕЗ долговременной памяти и зададим ему тот же вопрос.
    Вы увидите РАЗНИЦУ в ответах!
    """.trimIndent(),
            metadata = "Шаг 9/10"
        )
        delay(4.seconds)

// Создаем агента без памяти
        val agentNoMemory = MemoryAwareAgent(
            apiKey = apiKey,
            model = primaryModel,
            systemPrompt = "Ты опытный технический архитектор. Отвечай на русском языке."
        )
        agentNoMemory.useLongTerm = false
        agentNoMemory.useWorkingMemory =
            false // Отключаем и рабочую память для чистоты эксперимента

        addMessage(
            role = "assistant",
            content = """
    🔴 АГЕНТ БЕЗ ПАМЯТИ СОЗДАН:
    • Долговременная память: ОТКЛЮЧЕНА
    • Рабочая память: ОТКЛЮЧЕНА
    • Только краткосрочная память (текущий диалог)
    • Нет профиля, нет ограничений, нет знаний
    """.trimIndent(),
            metadata = "Агент без памяти"
        )
        delay(3.seconds)

// Задаем вопрос агенту С памятью
        addMessage(
            role = "assistant",
            content = "🔵 ЗАПРОС К АГЕНТУ С ПАМЯТЬЮ:",
            metadata = "Вопрос агенту A"
        )
        addMessage(
            role = "user",
            content = "Какой стек технологий я предпочитаю и какие у нас ограничения? Напиши кратко."
        )
        delay(1.seconds)
        onTypingStateChanged(true)
        delay(1.seconds)

// Объявляем переменные ДО try, чтобы они были доступны после
        var responseWithMemory: com.llmapp.agent.MemoryAwareResponse? = null
        var responseWithoutMemory: com.llmapp.agent.MemoryAwareResponse? = null

        try {
            responseWithMemory =
                agent.processRequest("Какой стек технологий я предпочитаю и какие у нас ограничения? Напиши кратко.")
            updateStats()
            addMessage(
                role = "assistant",
                content = "✅ С ПАМЯТЬЮ:\n\n${responseWithMemory.content}",
                metadata = "📊 Токены: ↑${responseWithMemory.promptTokens} ↓${responseWithMemory.completionTokens} Σ${responseWithMemory.totalTokens}",
                promptTokens = responseWithMemory.promptTokens,
                completionTokens = responseWithMemory.completionTokens,
                totalTokens = responseWithMemory.totalTokens,
                responseTimeMs = responseWithMemory.responseTimeMs
            )
        } catch (e: Exception) {
            addMessage(role = "assistant", content = "❌ Ошибка: ${e.message}")
        } finally {
            onTypingStateChanged(false)
        }
        delay(4.seconds)

// Задаем вопрос агенту БЕЗ памяти
        addMessage(
            role = "assistant",
            content = "🔴 ЗАПРОС К АГЕНТУ БЕЗ ПАМЯТИ:",
            metadata = "Вопрос агенту Б"
        )
        addMessage(
            role = "user",
            content = "Какой стек технологий я предпочитаю и какие у нас ограничения? Напиши кратко."
        )
        delay(1.seconds)
        onTypingStateChanged(true)
        delay(1.seconds)

        try {
            responseWithoutMemory =
                agentNoMemory.processRequest("Какой стек технологий я предпочитаю и какие у нас ограничения? Напиши кратко.")
            addMessage(
                role = "assistant",
                content = "❌ БЕЗ ПАМЯТИ:\n\n${responseWithoutMemory.content}",
                metadata = "📊 Токены: ↑${responseWithoutMemory.promptTokens} ↓${responseWithoutMemory.completionTokens} Σ${responseWithoutMemory.totalTokens}",
                promptTokens = responseWithoutMemory.promptTokens,
                completionTokens = responseWithoutMemory.completionTokens,
                totalTokens = responseWithoutMemory.totalTokens,
                responseTimeMs = responseWithoutMemory.responseTimeMs
            )
        } catch (e: Exception) {
            addMessage(role = "assistant", content = "❌ Ошибка: ${e.message}")
        } finally {
            onTypingStateChanged(false)
        }
        delay(4.seconds)

// Анализ различий - теперь переменные доступны
        val withMemoryContent = responseWithMemory?.content ?: "Ошибка получения ответа"
        val withoutMemoryContent = responseWithoutMemory?.content ?: "Ошибка получения ответа"

        addMessage(
            role = "assistant",
            content = """
    📊 АНАЛИЗ РАЗНИЦЫ В ОТВЕТАХ:
    
    🔵 АГЕНТ С ПАМЯТЬЮ:
    • ЗНАЕТ, что пользователя зовут ${profile.name}
    • ЗНАЕТ, что пользователь ${profile.experience}
    • ЗНАЕТ предпочитаемые технологии: ${profile.preferredTech.take(3).joinToString(", ")}...
    • ЗНАЕТ запрещенные технологии: ${constraints.forbiddenTech.take(2).joinToString(", ")}...
    • ЗНАЕТ архитектуру: ${constraints.architecture.take(50)}...
    • Длина ответа: ${withMemoryContent.length} символов
    • Ответ начинается с: "${withMemoryContent.take(100)}..."
    
    🔴 АГЕНТ БЕЗ ПАМЯТИ:
    • НЕ ЗНАЕТ пользователя
    • ДАЕТ общие рекомендации без персонализации
    • НЕ УЧИТЫВАЕТ запреты (может предложить Java/RxJava)
    • НЕ ЗНАЕТ ограничения проекта
    • Длина ответа: ${withoutMemoryContent.length} символов
    • Ответ начинается с: "${withoutMemoryContent.take(100)}..."
    
    💡 ВЫВОД: Без долговременной памяти агент не может персонализировать ответы!
    Разница в ответах очевидна - агент с памятью знает контекст,
    а агент без памяти дает общие, неперсонализированные рекомендации.
    """.trimIndent(),
            metadata = "Анализ"
        )
        delay(6.seconds)

        // ========== ШАГ 10: ОЧИСТКА КОНТЕКСТА ==========
        addMessage(
            role = "assistant",
            content = """
        ${"═".repeat(80)}
        🗑️ ШАГ 10: ОЧИСТКА КОНТЕКСТА РАБОЧЕЙ ПАМЯТИ
        ${"═".repeat(80)}
        
        Демонстрация clearWorkingContext() - очистка контекста задачи.
        Рабочая память очищается, но долговременная остается!
        """.trimIndent(),
            metadata = "Шаг 10/10"
        )
        delay(3.seconds)

        addMessage(
            role = "assistant",
            content = """
        📋 ДО ОЧИСТКИ:
        • Контекстных данных: ${agent.getWorkingMemory().contextData.size}
        • Текущий спринт: "${agent.getWorkingContext("текущий_спринт") ?: "не задан"}"
        • Дедлайн: "${agent.getWorkingContext("дедлайн_спринта") ?: "не задан"}"
        • Выполнено: "${agent.getWorkingContext("выполнено")?.take(50) ?: "не задано"}..."
        """.trimIndent(),
            metadata = "До очистки"
        )
        delay(3.seconds)

        agent.clearWorkingContext()

        addMessage(
            role = "assistant",
            content = """
        🗑️ ПОСЛЕ ОЧИСТКИ clearWorkingContext():
        • Контекстных данных: ${agent.getWorkingMemory().contextData.size}
        • Текущий спринт: "${agent.getWorkingContext("текущий_спринт") ?: "не задан"}"
        • Дедлайн: "${agent.getWorkingContext("дедлайн_спринта") ?: "не задан"}"
        
        ⚠️ Рабочая память очищена, но ДОЛГОВРЕМЕННАЯ ПАМЯТЬ сохранилась!
        • Профиль: ${agent.getUserProfile().name}
        • Ограничения: ${if (agent.getProjectConstraints().techStack.isNotEmpty()) "сохранены" else "нет"}
        • Знаний: ${agent.getAllKnowledge().size}
        """.trimIndent(),
            metadata = "После очистки"
        )
        delay(5.seconds)

        // ========== ФИНАЛЬНЫЙ ОТЧЕТ ==========
        addMessage(
            role = "assistant",
            content = """
        
        ${"═".repeat(100)}
        📊 ФИНАЛЬНЫЙ ОТЧЕТ ПО ДЕМОНСТРАЦИИ
        ${"═".repeat(100)}
        
        💼 РАБОЧАЯ ПАМЯТЬ (Working Memory):
        • Название задачи: ${agent.getWorkingMemory().taskName}
        • Состояние: ${agent.getWorkingMemory().currentState.displayName}
        • Решений в WM: ${agent.getWorkingMemory().decisions.size}
        • Контекстных данных: ${agent.getWorkingMemory().contextData.size}
        
        📚 ДОЛГОВРЕМЕННАЯ ПАМЯТЬ (Long-term Memory):
        • Профиль: ${agent.getUserProfile().name} (${agent.getUserProfile().experience})
        • Технологии: ${agent.getUserProfile().preferredTech.joinToString(", ")}
        • Ограничений: ${if (agent.getProjectConstraints().techStack.isNotEmpty()) "установлены" else "нет"}
        • Запрещено: ${agent.getProjectConstraints().forbiddenTech.joinToString(", ")}
        • Сохраненных знаний: ${agent.getAllKnowledge().size}
        • Сохраненных решений: ${agent.getAllDecisions().size}
        
        📊 СТАТИСТИКА ЗАПРОСОВ:
        • Всего запросов: ${agent.getTokenStats().requestCount}
        • Всего токенов: ${agent.getTokenStats().totalTokens}
        • Prompt токенов: ${agent.getTokenStats().totalPromptTokens}
        • Completion токенов: ${agent.getTokenStats().totalCompletionTokens}
        
        📁 ФАЙЛЫ СОХРАНЕНЫ В ~/.llm_memory/:
        • profile.md - профиль пользователя
        • constraints.md - ограничения проекта
        • knowledge.md - база знаний и решений
        
        """.trimIndent(),
            metadata = "Финальный отчет"
        )
        delay(6.seconds)

        // ========== ИТОГОВЫЕ ВЫВОДЫ ==========
        addMessage(
            role = "assistant",
            content = """
        
        ${"═".repeat(100)}
        🎯 ИТОГОВЫЕ ВЫВОДЫ ПО ТРЕХСЛОЙНОЙ МОДЕЛИ ПАМЯТИ
        ${"═".repeat(100)}
        
        1️⃣ КРАТКОСРОЧНАЯ ПАМЯТЬ (Short-term)
           📍 Хранит: текущий диалог (${projectQuestions.size} вопросов)
           🎯 Роль: связность разговора, контекст последних сообщений
           ⏰ Время жизни: до очистки истории чата
        
        2️⃣ РАБОЧАЯ ПАМЯТЬ (Working Memory)
           📍 Хранит: задачу, состояние, контекст, временные решения
           🎯 Роль: агент знает этап работы и текущий контекст
           ⏰ Время жизни: до создания новой задачи или clearWorkingMemory()
           🔧 API: addDecisionToWorkingMemory(), updateWorkingContext(), 
                  getWorkingContext(), clearWorkingContext()
        
        3️⃣ ДОЛГОВРЕМЕННАЯ ПАМЯТЬ (Long-term)
           📍 Хранит: профиль, ограничения, знания, постоянные решения
           🎯 Роль: персонализация ответов, соблюдение правил
           ⏰ Время жизни: ПЕРМАНЕНТНО (между сессиями!)
           🔧 API: saveDecisionToLongTerm(), addKnowledge(), 
                  getKnowledge(), getAllKnowledge(), getAllDecisions()
        
        💡 ГЛАВНЫЙ ВЫВОД:
        
        Без долговременной памяти агент НЕ ЗНАЕТ:
        • Кто вы (имя, опыт, предпочтения)
        • Какие технологии МОЖНО использовать
        • Какие технологии ЗАПРЕЩЕНЫ
        • Какая архитектура выбрана
        • Сохраненные решения и знания
        
        С полной трехслойной памятью агент дает:
        • ПЕРСОНАЛИЗИРОВАННЫЕ ответы
        • Учитывает ОГРАНИЧЕНИЯ проекта
        • Использует НАКОПЛЕННЫЕ знания
        • Знает КОНТЕКСТ текущей задачи
        
        ✨ ДЕМОНСТРАЦИЯ УСПЕШНО ЗАВЕРШЕНА!
        
        """.trimIndent(),
            metadata = "Итоговые выводы"
        )

        // Финальное обновление статистики
        updateStats()
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
