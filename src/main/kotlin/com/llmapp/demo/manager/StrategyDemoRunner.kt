package com.llmapp.demo.manager

import com.llmapp.agent.ContextStrategyType
import com.llmapp.agent.StrategicLLMAgent
import com.llmapp.api.ApiConfig
import com.llmapp.demo.StrategyTestScenario
import com.llmapp.ui.models.ChatMessageUI
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

data class StrategyTestResult(
    val strategyName: String,
    val responses: List<String>,
    val totalTokens: Int,
    val totalPromptTokens: Int,
    val totalCompletionTokens: Int,
    val responseTimeMs: Long,
    val requestCount: Int,
    val contextSizeAtEnd: Int,
    val estimatedCost: Double
)

/**
 * Демонстрация стратегий управления контекстом
 */
class StrategyDemoRunner(
    onMessageAdded: (ChatMessageUI) -> Unit,
    onTypingStateChanged: (Boolean) -> Unit
) : BaseDemoRunner(onMessageAdded, onTypingStateChanged) {

    override suspend fun run() {
        val apiKey = ApiConfig.getApiKey()
        val model = "openai/gpt-oss-20b:free"

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

        val results = mutableListOf<StrategyTestResult>()

        // ========== Тест 1: Sliding Window ==========
        addMessage(
            role = "assistant",
            content = "\n${"━".repeat(60)}\n🔵 ТЕСТ 1: Sliding Window Strategy\n${"━".repeat(60)}",
            metadata = "Тест 1/3"
        )
        delayMedium()

        val slidingResult = runStrategyTest(
            apiKey = apiKey,
            model = model,
            strategy = ContextStrategyType.SLIDING_WINDOW,
            strategyName = "Sliding Window",
            scenario = StrategyTestScenario.TZQuestions
        )
        results.add(slidingResult)

        delay(2.seconds)

        // ========== Тест 2: Sticky Facts ==========
        addMessage(
            role = "assistant",
            content = "\n${"━".repeat(60)}\n🟢 ТЕСТ 2: Sticky Facts Strategy\n${"━".repeat(60)}",
            metadata = "Тест 2/3"
        )
        delayMedium()

        val factsResult = runStrategyTest(
            apiKey = apiKey,
            model = model,
            strategy = ContextStrategyType.STICKY_FACTS,
            strategyName = "Sticky Facts",
            scenario = StrategyTestScenario.TZQuestions
        )
        results.add(factsResult)

        delay(2.seconds)

        // ========== Тест 3: Branching ==========
        addMessage(
            role = "assistant",
            content = "\n${"━".repeat(60)}\n🟣 ТЕСТ 3: Branching Strategy\n${"━".repeat(60)}",
            metadata = "Тест 3/3"
        )
        delayMedium()

        val branchingResult = runStrategyTest(
            apiKey = apiKey,
            model = model,
            strategy = ContextStrategyType.BRANCHING,
            strategyName = "Branching",
            scenario = StrategyTestScenario.TZQuestions
        )
        results.add(branchingResult)

        delay(2.seconds)

        // ========== Сравнительный анализ ==========
        printComparison(results)

        // ========== Демонстрация ветвления ==========
        addMessage(
            role = "assistant",
            content = "\n${"=".repeat(100)}\n🌿 ДЕМОНСТРАЦИЯ ВЕТВЛЕНИЯ (Branching Strategy)\n${
                "=".repeat(
                    100
                )
            }",
            metadata = "Ветвление"
        )
        delay(2.seconds)

        demoBranching(apiKey, model)
    }

    private suspend fun runStrategyTest(
        apiKey: String,
        model: String,
        strategy: ContextStrategyType,
        strategyName: String,
        scenario: List<String>
    ): StrategyTestResult {
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
        delayMedium()

        val responses = mutableListOf<String>()
        var totalPromptTokens = 0
        var totalCompletionTokens = 0
        var totalTokens = 0
        val startTime = System.currentTimeMillis()

        for ((index, question) in scenario.withIndex()) {
            addMessage(
                role = "user",
                content = question,
                metadata = "[${index + 1}/${scenario.size}]"
            )
            delayMedium()

            onTypingStateChanged(true)
            delayMedium()

            try {
                val response = agent.processRequest(question)
                onTypingStateChanged(false)
                responses.add(response.content)

                totalPromptTokens += response.promptTokens ?: 0
                totalCompletionTokens += response.completionTokens ?: 0
                totalTokens += response.totalTokens ?: 0

                addMessage(
                    role = "assistant",
                    content = response.content,
                    metadata = "📊 Токены: ↑${response.promptTokens} ↓${response.completionTokens} Σ${response.totalTokens} | Стратегия: ${response.strategyUsed}",
                    promptTokens = response.promptTokens,
                    completionTokens = response.completionTokens,
                    totalTokens = response.totalTokens,
                    responseTimeMs = response.responseTimeMs
                )

                if (strategy == ContextStrategyType.STICKY_FACTS && index % 3 == 0 && index > 0) {
                    val facts = agent.getFacts()
                    if (facts.isNotEmpty()) {
                        val factsText = facts.entries.joinToString("\n") {
                            "• ${it.key}: ${it.value.take(80)}"
                        }
                        addMessage(
                            role = "assistant",
                            content = "📝 Извлеченные факты из диалога:\n$factsText",
                            metadata = "Автоматическое извлечение фактов"
                        )
                    }
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

        val endTime = System.currentTimeMillis()
        val stats = agent.getTokenStats()

        addMessage(
            role = "assistant",
            content = """
            📊 ИТОГ ТЕСТА ($strategyName)
            ${"─".repeat(40)}
            • Запросов: ${scenario.size}
            • Всего токенов: $totalTokens
            • Prompt: $totalPromptTokens | Completion: $totalCompletionTokens
            """.trimIndent(),
            metadata = "Итоговая статистика"
        )

        return StrategyTestResult(
            strategyName = agent.getCurrentStrategyName(),
            responses = responses,
            totalTokens = stats.totalTokens,
            totalPromptTokens = stats.totalPromptTokens,
            totalCompletionTokens = stats.totalCompletionTokens,
            responseTimeMs = endTime - startTime,
            requestCount = stats.requestCount,
            contextSizeAtEnd = agent.getStrategyStats().contextSizeTokens,
            estimatedCost = stats.estimatedCostUsd
        )
    }

    private suspend fun demoBranching(apiKey: String, model: String) {
        val agent = StrategicLLMAgent(
            apiKey = apiKey,
            model = model,
            systemPrompt = "Ты полезный ассистент. Отвечай кратко."
        )
        agent.setStrategy(ContextStrategyType.BRANCHING)

        // Шаг 1: Начало диалога
        addMessage(
            role = "assistant",
            content = "📍 Шаг 1: Начальный диалог",
            metadata = "Ветвление"
        )
        delayMedium()

        val response1 =
            agent.processRequest("Давайте обсудим приложение для заметок. Нужна офлайн работа и синхронизация.")
        addMessage(
            role = "assistant",
            content = "🤖: ${response1.content}",
            metadata = "Ответ"
        )

        // Шаг 2: Создаем чекпоинт
        addMessage(
            role = "assistant",
            content = "📍 Шаг 2: Создаем чекпоинт 'После обсуждения архитектуры'",
            metadata = "Чекпоинт"
        )
        val checkpointId = agent.createCheckpoint("После обсуждения архитектуры")
        if (checkpointId == null) {
            addMessage(
                role = "assistant",
                content = "❌ Не удалось создать чекпоинт",
                metadata = "Ошибка"
            )
            return
        }
        addMessage(
            role = "assistant",
            content = "✅ Чекпоинт создан: $checkpointId",
            metadata = "Чекпоинт"
        )

        // Шаг 3: Продолжаем диалог (ветка 1)
        addMessage(
            role = "assistant",
            content = "📍 Шаг 3: Ветка 1 - SQLDelight подход",
            metadata = "Ветка 1"
        )
        delayMedium()

        val response2 =
            agent.processRequest("Я думаю использовать SQLDelight для локального хранения. Как настроить синхронизацию?")
        addMessage(
            role = "assistant",
            content = "🤖: ${response2.content}",
            metadata = "Ветка 1 - ответ"
        )

        // Шаг 4: Создаем ветку от чекпоинта
        addMessage(
            role = "assistant",
            content = "📍 Шаг 4: Создаем новую ветку 'Room подход' от чекпоинта",
            metadata = "Создание ветки"
        )
        val branchId = agent.createBranch(checkpointId, "Room подход")
        if (branchId == null) {
            addMessage(
                role = "assistant",
                content = "❌ Не удалось создать ветку",
                metadata = "Ошибка"
            )
            return
        }
        val switched = agent.switchToBranch(branchId)
        addMessage(
            role = "assistant",
            content = "✅ Переключение на ветку: ${if (switched) "успешно" else "не удалось"}",
            metadata = "Переключение"
        )

        // Шаг 5: Альтернативный путь (ветка 2)
        addMessage(
            role = "assistant",
            content = "📍 Шаг 5: Ветка 2 - Room подход",
            metadata = "Ветка 2"
        )
        delayMedium()

        val response3 =
            agent.processRequest("А что если использовать Room для локального хранения? Как быть с синхронизацией?")
        addMessage(
            role = "assistant",
            content = "🤖: ${response3.content}",
            metadata = "Ветка 2 - ответ"
        )

        // Шаг 6: Показываем все ветки
        addMessage(
            role = "assistant",
            content = "📍 Шаг 6: Все доступные ветки",
            metadata = "Список веток"
        )
        delayMedium()

        agent.getAllBranches().forEach { branch ->
            val marker = if (branch.isCurrent) "⭐ ТЕКУЩАЯ" else "  "
            addMessage(
                role = "assistant",
                content = "$marker 🌿 ${branch.name} (${branch.messageCount} сообщений)${branch.parentBranchName?.let { " ← от $it" } ?: ""}",
                metadata = "Ветка"
            )
        }

        // Шаг 7: Сравнение ответов
        addMessage(
            role = "assistant",
            content = """
            📊 СРАВНЕНИЕ ОТВЕТОВ ИЗ РАЗНЫХ ВЕТОК:
            Ветка 'SQLDelight':
              ${response2.content.take(150)}...
            
            Ветка 'Room':
              ${response3.content.take(150)}...
            """.trimIndent(),
            metadata = "Сравнение"
        )

        val finalStats = agent.getStrategyStats()
        addMessage(
            role = "assistant",
            content = "📊 Итоговая статистика:\n  • Сообщений: ${finalStats.totalMessages}\n  • Контекст: ${finalStats.contextSizeTokens} токенов",
            metadata = "Итог"
        )
    }

    private suspend fun printComparison(results: List<StrategyTestResult>) {
        addMessage(
            role = "assistant",
            content = "\n${"=".repeat(100)}\n📊 СРАВНИТЕЛЬНЫЙ АНАЛИЗ СТРАТЕГИЙ\n${"=".repeat(100)}",
            metadata = "Сравнительный анализ"
        )
        delayMedium()

        results.forEach { result ->
            addMessage(
                role = "assistant",
                content = result.getFormatted(),
                metadata = "Стратегия"
            )
        }

        val sliding = results.find { it.strategyName.contains("Sliding") }
        val facts = results.find { it.strategyName.contains("Facts") }
        val branching = results.find { it.strategyName.contains("Branching") }

        if (sliding != null && facts != null && branching != null) {
            val tokensSaved = sliding.totalTokens - facts.totalTokens
            val percentSaved = if (sliding.totalTokens > 0) {
                (tokensSaved.toDouble() / sliding.totalTokens) * 100
            } else 0.0

            addMessage(
                role = "assistant",
                content = """
                1️⃣ Sliding Window (Скользящее окно)
                   ✅ Плюсы: Предсказуемый расход токенов, простота реализации
                   ❌ Минусы: Теряет контекст из начала диалога
                   📊 Токенов: ${sliding.totalTokens} | Время: ${formatTime(sliding.responseTimeMs)}
                   🎯 Качество: Среднее - забывает ранние требования
                
                2️⃣ Sticky Facts (Фиксация фактов)
                   ✅ Плюсы: Сохраняет ключевую информацию, экономит токены
                   ❌ Минусы: Требует хорошего извлечения фактов
                   📊 Токенов: ${facts.totalTokens} | Время: ${formatTime(facts.responseTimeMs)}
                   🎯 Качество: Хорошее - помнит важные решения
                   💾 Экономия токенов: $tokensSaved (${"%.1f".format(percentSaved)}%)
                
                3️⃣ Branching (Ветвление)
                   ✅ Плюсы: Позволяет исследовать альтернативы, гибкость
                   ❌ Минусы: Самый высокий расход токенов, сложность
                   📊 Токенов: ${branching.totalTokens} | Время: ${formatTime(branching.responseTimeMs)}
                   🎯 Качество: Лучшее - полный контекст в каждой ветке
                   🌿 Удобство: Идеален для исследования вариантов
                
                💡 РЕКОМЕНДАЦИИ:
                • Для коротких диалогов (до 10 сообщений) - любая стратегия подходит
                • Для длинных диалогов (20+ сообщений) - используйте Sticky Facts
                • Для исследовательских задач и A/B тестирования - Branching
                • Если важна предсказуемость расхода токенов - Sliding Window
                """.trimIndent(),
                metadata = "Выводы"
            )
        }
    }
}

private fun StrategyTestResult.getFormatted(): String = """
    ${"=".repeat(50)}
    🎯 $strategyName
    ${"=".repeat(50)}
    • Запросов: $requestCount
    • Всего токенов: $totalTokens
    • Prompt: $totalPromptTokens | Completion: $totalCompletionTokens
    • Время: ${formatTimeStr(responseTimeMs)}
    • Финальный контекст: ~${contextSizeAtEnd} токенов
    • Стоимость: $${"%.6f".format(estimatedCost)}
""".trimIndent()

private fun formatTimeStr(ms: Long): String = when {
    ms < 1000 -> "${ms}мс"
    ms < 60000 -> "${ms / 1000}.${(ms % 1000) / 100}с"
    else -> "${ms / 60000}м ${(ms % 60000) / 1000}с"
}
