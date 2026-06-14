package com.llmapp.demo

import com.llmapp.agent.ContextStrategyType
import com.llmapp.agent.StrategicLLMAgent
import com.llmapp.api.ApiConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
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
) {
    fun getFormatted(): String = """
        
        ${"═".repeat(50)}
        🎯 $strategyName
        ${"═".repeat(50)}
        • Запросов: $requestCount
        • Всего токенов: $totalTokens
        • Prompt: $totalPromptTokens | Completion: $totalCompletionTokens
        • Время: ${formatTime(responseTimeMs)}
        • Финальный контекст: ~${contextSizeAtEnd} токенов
        • Стоимость: $${"%.6f".format(estimatedCost)}
    """.trimIndent()

    private fun formatTime(ms: Long): String = when {
        ms < 1000 -> "${ms}мс"
        ms < 60000 -> "${ms / 1000}.${(ms % 1000) / 100}с"
        else -> "${ms / 60000}м ${(ms % 60000) / 1000}с"
    }
}

/**
 * Сценарий для тестирования стратегий: сбор ТЗ на приложение
 */
object StrategyTestScenario {
    val TZQuestions = listOf(
        "Я хочу разработать приложение для заметок на Kotlin Multiplatform",
        "Приложение должно синхронизироваться между устройствами",
        "Какую архитектуру вы посоветуете использовать?",
        "Нужна офлайн работа. Данные должны сохраняться локально",
        "Хочу использовать Material Design 3 с поддержкой темной темы",
        "Добавьте возможность создавать теги для заметок",
        "Как организовать поиск по заметкам?",
        "Нужна поддержка вложений (изображения, файлы)",
        "Какие библиотеки для KMP синхронизации вы посоветуете?",
        "Как обеспечить безопасность пользовательских данных?",
        "Сколько примерно времени займет разработка MVP?",
        "Какие сложности могут возникнуть при разработке?"
    )
}

class StrategyDemo {
    suspend fun runComparison(): List<StrategyTestResult> {
        val apiKey = ApiConfig.getApiKey()
        val model = "openai/gpt-oss-20b:free"

        println("\n" + "═".repeat(100))
        println("🧪 ЭКСПЕРИМЕНТ: Сравнение стратегий управления контекстом")
        println("═".repeat(100))
        println(
            """
            
            📋 Сценарий тестирования: Сбор требований к KMP приложению для заметок
            • ${StrategyTestScenario.TZQuestions.size} вопросов
            • Оценка качества ответов, стабильности и расхода токенов
            
        """.trimIndent()
        )

        val results = mutableListOf<StrategyTestResult>()

        // Тест 1: Sliding Window
        println("\n" + "━".repeat(60))
        println("🔵 ТЕСТ 1: Sliding Window Strategy")
        println("━".repeat(60))
        val slidingResult = testStrategy(
            apiKey = apiKey,
            model = model,
            strategy = ContextStrategyType.SLIDING_WINDOW,
            scenario = StrategyTestScenario.TZQuestions
        )
        results.add(slidingResult)

        delay(2.seconds)

        // Тест 2: Sticky Facts
        println("\n" + "━".repeat(60))
        println("🟢 ТЕСТ 2: Sticky Facts Strategy")
        println("━".repeat(60))
        val factsResult = testStrategy(
            apiKey = apiKey,
            model = model,
            strategy = ContextStrategyType.STICKY_FACTS,
            scenario = StrategyTestScenario.TZQuestions
        )
        results.add(factsResult)

        delay(2.seconds)

        // Тест 3: Branching
        println("\n" + "━".repeat(60))
        println("🟣 ТЕСТ 3: Branching Strategy")
        println("━".repeat(60))
        val branchingResult = testStrategy(
            apiKey = apiKey,
            model = model,
            strategy = ContextStrategyType.BRANCHING,
            scenario = StrategyTestScenario.TZQuestions
        )
        results.add(branchingResult)

        printComparison(results)

        println("\n" + "═".repeat(100))
        println("🌿 ДЕМОНСТРАЦИЯ ВЕТВЛЕНИЯ (Branching Strategy)")
        println("═".repeat(100))
        demoBranching(apiKey, model)

        return results
    }

    private suspend fun testStrategy(
        apiKey: String,
        model: String,
        strategy: ContextStrategyType,
        scenario: List<String>
    ): StrategyTestResult {
        val agent = StrategicLLMAgent(
            apiKey = apiKey,
            model = model,
            systemPrompt = "Ты опытный технический архитектор. Отвечай кратко, по делу, на русском языке."
        )

        agent.setStrategy(strategy)

        println("\n📌 Стратегия: ${agent.getCurrentStrategyName()}")
        println("📝 Начинаем диалог из ${scenario.size} вопросов...")
        println("-".repeat(50))

        val responses = mutableListOf<String>()
        var totalPromptTokens = 0
        var totalCompletionTokens = 0
        var totalTokens = 0
        val startTime = System.currentTimeMillis()

        for ((index, question) in scenario.withIndex()) {
            println("\n[${index + 1}] 👤: ${question.take(70)}...")

            try {
                val response = agent.processRequest(question)
                responses.add(response.content)

                totalPromptTokens += response.promptTokens ?: 0
                totalCompletionTokens += response.completionTokens ?: 0
                totalTokens += response.totalTokens ?: 0

                println("🤖: ${response.content.take(100)}...")
                println("📊 Токены: ↑${response.promptTokens} ↓${response.completionTokens} Σ${response.totalTokens}")
                println("📊 Статистика: сообщений=${response.strategyStats.totalMessages}, контекст=${response.strategyStats.contextSizeTokens} токенов")

                delay(500.milliseconds)

            } catch (e: Exception) {
                println("❌ Ошибка: ${e.message}")
                responses.add("Ошибка: ${e.message}")
            }
        }

        val endTime = System.currentTimeMillis()
        val stats = agent.getTokenStats()

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
        println("\n📍 Шаг 1: Начальный диалог")
        println("-".repeat(40))

        val response1 =
            agent.processRequest("Давайте обсудим приложение для заметок. Нужна офлайн работа и синхронизация.")
        println("🤖: ${response1.content}")

        // Шаг 2: Создаем чекпоинт
        println("\n📍 Шаг 2: Создаем чекпоинт 'После обсуждения архитектуры'")
        val checkpointId = agent.createCheckpoint("После обсуждения архитектуры")
        if (checkpointId == null) {
            println("❌ Не удалось создать чекпоинт")
            return
        }
        println("✅ Чекпоинт создан: $checkpointId")

        // Шаг 3: Продолжаем диалог (ветка 1)
        println("\n📍 Шаг 3: Ветка 1 - SQLDelight подход")
        println("-".repeat(40))
        val response2 =
            agent.processRequest("Я думаю использовать SQLDelight для локального хранения. Как настроить синхронизацию?")
        println("🤖: ${response2.content}")

        // Шаг 4: Создаем ветку от чекпоинта
        println("\n📍 Шаг 4: Создаем новую ветку 'Room подход' от чекпоинта")
        val branchId = agent.createBranch(checkpointId, "Room подход")
        if (branchId == null) {
            println("❌ Не удалось создать ветку")
            return
        }
        val switched = agent.switchToBranch(branchId)
        println("✅ Переключение на ветку: ${if (switched) "успешно" else "не удалось"}")

        // Шаг 5: Альтернативный путь (ветка 2)
        println("\n📍 Шаг 5: Ветка 2 - Room подход")
        println("-".repeat(40))
        val response3 =
            agent.processRequest("А что если использовать Room для локального хранения? Как быть с синхронизацией?")
        println("🤖: ${response3.content}")

        // Шаг 6: Показываем все ветки
        println("\n📍 Шаг 6: Все доступные ветки")
        println("-".repeat(40))
        agent.getAllBranches().forEach { branch ->
            val marker = if (branch.isCurrent) "⭐ ТЕКУЩАЯ" else "  "
            println("$marker 🌿 ${branch.name} (${branch.messageCount} сообщений)${branch.parentBranchName?.let { " ← от $it" } ?: ""}")
        }

        // Шаг 7: Показываем ответы из разных веток
        println("\n📊 СРАВНЕНИЕ ОТВЕТОВ ИЗ РАЗНЫХ ВЕТОК:")
        println("-".repeat(40))
        println("Ветка 'SQLDelight':")
        println("  ${response2.content.take(150)}...")
        println("\nВетка 'Room':")
        println("  ${response3.content.take(150)}...")

        val finalStats = agent.getStrategyStats()
        println("\n📊 Итоговая статистика:")
        println("  • Сообщений: ${finalStats.totalMessages}")
        println("  • Контекст: ${finalStats.contextSizeTokens} токенов")
    }

    private fun printComparison(results: List<StrategyTestResult>) {
        println("\n" + "═".repeat(100))
        println("📊 СРАВНИТЕЛЬНЫЙ АНАЛИЗ СТРАТЕГИЙ")
        println("═".repeat(100))

        results.forEach { result ->
            println(result.getFormatted())
        }

        println("\n" + "═".repeat(100))
        println("📈 ВЫВОДЫ:")
        println("═".repeat(100))

        val sliding = results.find { it.strategyName.contains("Sliding") }
        val facts = results.find { it.strategyName.contains("Facts") }
        val branching = results.find { it.strategyName.contains("Branching") }

        if (sliding != null && facts != null && branching != null) {
            val tokensSaved = sliding.totalTokens - facts.totalTokens
            val percentSaved = if (sliding.totalTokens > 0) {
                (tokensSaved.toDouble() / sliding.totalTokens) * 100
            } else 0.0

            println(
                """
                
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
                
            """.trimIndent()
            )
        }
    }

    private fun formatTime(ms: Long): String = when {
        ms < 1000 -> "${ms}мс"
        ms < 60000 -> "${ms / 1000}.${(ms % 1000) / 100}с"
        else -> "${ms / 60000}м ${(ms % 60000) / 1000}с"
    }
}

fun main(): Unit = runBlocking {
    try {
        val demo = StrategyDemo()
        demo.runComparison()
    } catch (e: Exception) {
        println("\n❌ Критическая ошибка: ${e.message}")
        e.printStackTrace()
    }
}
