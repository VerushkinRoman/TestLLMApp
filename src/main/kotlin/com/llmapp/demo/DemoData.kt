package com.llmapp.demo

import com.llmapp.agent.CompressedChatHistory

object DemoData {
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

    val shortDialogue = listOf(
        "Привет! Как дела?",
        "Расскажи кратко о Kotlin",
        "Спасибо, пока!"
    )
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
        "Как оптимизировать производительность Compose приложений?",
        "Что такое делегирование в Kotlin?",
        "Расскажи про sealed классы и интерфейсы",
        "Как работают реифицированные типы?",
        "Объясни концепцию компонентных функций",
        "Что такое оператор invoke в Kotlin?"
    )

    val extraQuestions = listOf(
        "Что такое корутины в Kotlin?",
        "Как работает Flow?",
        "Расскажи про Compose UI",
        "Что такое функции расширения?",
        "Как работает делегирование в Kotlin?"
    )

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

// Token Demo Texts

    fun getTokenDemoIntro(): String = """
        🔬 **Запуск демонстрации отслеживания токенов**
        
        Я покажу, как отслеживаются токены в диалогах разной длины. 
        Демонстрация включает:
        • Короткий диалог (3 сообщения)
        • Длинный диалог (${longDialogueTopics.size} сообщений)
        • Дополнительные запросы (5 сообщений)
        
        Начинаем...
    """.trimIndent()

    fun getShortDialogueIntro(): String = """
        📝 **ТЕСТ 1: Короткий диалог (3 сообщения)**
        
        Будут заданы простые вопросы, чтобы показать базовое потребление токенов.
        ---
    """.trimIndent()

    fun formatTokenMetadata(
        promptTokens: Int,
        completionTokens: Int,
        totalTokens: Int,
        cumulativeTotal: Int,
        cost: Double
    ): String =
        "📊 Токены: $totalTokens (↑$promptTokens/↓$completionTokens) | Всего: $cumulativeTotal токенов | 💰 ${
            String.format(
                "%.6f",
                cost
            )
        }"

    fun getShortDialogueSummary(
        totalTokens: Int,
        promptTokens: Int,
        completionTokens: Int,
        cost: Double
    ): String = """
        📊 **Итог короткого диалога**
        
        • Всего токенов: $totalTokens
        • Prompt токенов: $promptTokens
        • Completion токенов: $completionTokens
        • Стоимость: ${String.format("%.6f", cost)}
        
        ---
        Переходим к длинному диалогу...
    """.trimIndent()

    fun getLongDialogueIntro(count: Int): String = """
        📝 **ТЕСТ 2: Длинный диалог ($count сообщений)**
        
        Сейчас будет задано много вопросов подряд. Вы увидите, как растет контекст и стоимость.
        ---
    """.trimIndent()

    fun formatLongDialogueMetadata(
        current: Int,
        total: Int,
        promptTokens: Int,
        completionTokens: Int,
        totalTokens: Int,
        cumulativeTotal: Int,
        cost: Double,
        contextWarning: String
    ): String = buildString {
        append("📊 [$current/$total] ")
        append("Токены: $totalTokens (↑$promptTokens/↓$completionTokens)")
        append(" | Всего: $cumulativeTotal")
        append(" | 💰 ${String.format("%.6f", cost)}")
        if (contextWarning.contains("⚠️") || contextWarning.contains("🔴")) {
            append("\n⚠️ $contextWarning")
        }
    }

    fun getFinalTokenStats(
        requestCount: Int,
        totalTokens: Int,
        promptTokens: Int,
        completionTokens: Int,
        cost: Double
    ): String = """
        📊 **ФИНАЛЬНАЯ СТАТИСТИКА**
        
┌────────────────────────────────────────────────────────┐
│ ИТОГИ ДЕМОНСТРАЦИИ ТОКЕНОВ │
├────────────────────────────────────────────────────────┤
│ 📊 Всего запросов: $requestCount││📝Всегот окенов: $totalTokens │
│ ⬆ Prompt токенов: $promptTokens││⬇Completion токенов: $completionTokens │
│ 💰 Общая стоимость: ${String.format("%.6f", cost)} │
└────────────────────────────────────────────────────────┘

""".trimIndent()

    fun getExtraQuestionsIntro(): String = """
📝 **ТЕСТ 3: Дополнительные запросы**

Теперь задам 5 вопросов, чтобы показать накопление контекста.
---
""".trimIndent()

    fun getTokenDemoConclusion(
        requestCount: Int,
        totalTokens: Int,
        promptTokens: Int,
        completionTokens: Int,
        cost: Double,
        contextWarning: String
    ): String = """
📊 **ВЫВОДЫ ПО ДЕМОНСТРАЦИИ**

**Ключевые наблюдения:**

1️⃣ **Рост токенов**: Всего использовано $totalTokens токенов за $requestCount запросов

2️⃣ **Соотношение**: Prompt токенов в ${
        if (promptTokens > 0) promptTokens / completionTokens.coerceAtLeast(
            1
        ) else 0
    }x больше Completion

3️⃣ **Стоимость**: ${String.format("%.6f", cost)} (демонстрационная оценка)

4️⃣ **Контекст**: $contextWarning

**Что это значит для ваших проектов?**

• 💰 При длинных диалогах стоимость растет пропорционально количеству сообщений
• 📊 Prompt токены обычно доминируют (история диалога)
• 🚀 Для экономии используйте сжатие контекста
• ⚡ Короткие ответы модели значительно дешевле

---
✨ **Демонстрация завершена!**
""".trimIndent()

// Compression Demo Texts

    fun getCompressionDemoIntro(messageCount: Int): String = """
🔬 **Запуск демонстрации сравнения с компрессией контекста**

Я покажу разницу между обычным агентом и агентом со сжатием контекста.

**Параметры эксперимента:**
• Модель: GPT-OSS 20B
• Количество сообщений: $messageCount
• Компрессия: последние 8 сообщений как есть, summary каждые 6 сообщений

Сначала запустим тест **БЕЗ компрессии**...
""".trimIndent()

    fun getNoCompressionTestIntro(): String = """
🔵 **ТЕСТ 1: Обычный агент (БЕЗ компрессии контекста)**
---
Начинаем диалог из ${longDialogueTopics.size} вопросов...
""".trimIndent()

    fun formatCompressionTestMetadata(
        current: Int,
        total: Int,
        promptTokens: Int,
        completionTokens: Int,
        totalTokens: Int,
        cumulativeTotal: Int
    ): String =
        "📊 [$current/$total] Токены: ↑$promptTokens/↓$completionTokens/Σ$totalTokens | Всего: $cumulativeTotal"

    fun getNoCompressionResults(
        requestCount: Int,
        totalTokens: Int,
        promptTokens: Int,
        completionTokens: Int,
        timeMs: Long,
        cost: Double
    ): String = """
📊 **Итог теста БЕЗ компрессии:**

• Запросов: $requestCount
• Всего токенов: $totalTokens
• Prompt токенов: $promptTokens
• Completion токенов: $completionTokens
• Общее время: ${formatTime(timeMs)}
• Примерная стоимость: $${String.format("%.6f", cost)}

---
🟢 Переходим к тесту **С компрессией контекста**...
""".trimIndent()

    fun getCompressionTestIntro(keepLast: Int, summarizeEvery: Int): String = """
🟢 **ТЕСТ 2: Сжатый агент (С компрессией контекста)**
---
Параметры: keepLast=$keepLast, summarizeEvery=$summarizeEvery

Начинаем тот же диалог...
""".trimIndent()

    fun getCompressionComparisonResults(
        regularTotal: Int, regularPrompt: Int, regularCompletion: Int,
        compressedTotal: Int, compressedPrompt: Int, compressedCompletion: Int,
        regularTime: Long, compressedTime: Long,
        regularCost: Double, compressedCost: Double,
        tokensSaved: Int, tokensSavedPercent: Double,
        compressionStats: CompressedChatHistory.CompressionStats?
    ): String = """
📊 **СРАВНИТЕЛЬНЫЙ АНАЛИЗ**

${"═".repeat(50)}

**БЕЗ КОМПРЕССИИ:**
• Всего токенов: $regularTotal
• Prompt: $regularPrompt | Completion: $regularCompletion
• Время: ${formatTime(regularTime)}
• Стоимость: $${String.format("%.6f", regularCost)}

**С КОМПРЕССИЕЙ:**
• Всего токенов: $compressedTotal
• Prompt: $compressedPrompt | Completion: $compressedCompletion
• Время: ${formatTime(compressedTime)}
• Стоимость: $${String.format("%.6f", compressedCost)}

${"═".repeat(50)}

**📈 ЭКОНОМИЯ:**
• Токенов: $tokensSaved (${"%.1f".format(tokensSavedPercent)}%)
• Стоимость: $${String.format("%.6f", regularCost - compressedCost)}
• Время: ${
        if (regularTime > compressedTime) "✓ Быстрее на ${formatTime(regularTime - compressedTime)}" else "✗ Медленнее на ${
            formatTime(
                compressedTime - regularTime
            )
        }"
    }

${compressionStats?.getFormatted() ?: ""}

---
**💡 ВЫВОДЫ:**

${
        when {
            tokensSavedPercent > 30 -> "✅ ОТЛИЧНО! Компрессия сократила расход токенов на ${
                "%.1f".format(
                    tokensSavedPercent
                )
            }%"

            tokensSavedPercent > 15 -> "👍 ХОРОШО! Компрессия сократила расход токенов на ${
                "%.1f".format(
                    tokensSavedPercent
                )
            }%"

            tokensSavedPercent > 0 -> "👌 НЕПЛОХО! Компрессия сократила расход токенов на ${
                "%.1f".format(
                    tokensSavedPercent
                )
            }%"

            else -> "⚠️ Компрессия не дала экономии токенов в этом тесте"
        }
    }

**Рекомендации:**
1. Компрессия особенно эффективна для длинных диалогов (>20 сообщений)
2. Настройте keepLastMessages (8-12) и summarizeEvery (5-8) под ваши задачи
3. Для моделей с большим контекстом можно увеличить keepLastMessages
4. Для коротких диалогов компрессию можно отключать

---
✨ **Демонстрация завершена!**
""".trimIndent()

    private fun formatTime(ms: Long): String = when {
        ms < 1000 -> "${ms}мс"
        ms < 60000 -> "${ms / 1000}.${(ms % 1000) / 100}с"
        else -> "${ms / 60000}м ${(ms % 60000) / 1000}с"
    }

    // Добавить в DemoData.kt:

    fun getCompressionDemoHeader(modelName: String, messageCount: Int): String = """
    
    ${"=".repeat(100)}
    🧪 ЭКСПЕРИМЕНТ: Сравнение работы с компрессией контекста и без
    ${"=".repeat(100)}
    
    📌 Параметры эксперимента:
       • Модель: ${getModelShortName(modelName)}
       • Количество сообщений: $messageCount
       • Компрессия: последние 8 сообщений как есть, summary каждые 6 сообщений
""".trimIndent()

    fun getNoCompressionTestHeader(): String = """
    
    ${"━".repeat(100)}
    🔵 ТЕСТ 1: Обычный агент (БЕЗ компрессии контекста)
""".trimIndent()

    fun getCompressionTestHeader(): String = """
    
    ${"━".repeat(100)}
    🟢 ТЕСТ 2: Сжатый агент (С компрессией контекста)
""".trimIndent()

    fun getAdaptiveCompressionHeader(): String = """
    
    ${"=".repeat(100)}
    🎯 ТЕСТ 3: Адаптивная компрессия под разные модели
    ${"=".repeat(100)}
""".trimIndent()

    fun getCompressionConclusions(tokensSavedPercent: Double, costSaved: Double): String = """
    
    💡 ВЫВОДЫ:
    ${"-".repeat(80)}
    
    ${
        when {
            tokensSavedPercent > 30 -> "   ✅ ОТЛИЧНО! Компрессия сократила расход токенов на ${
                "%.1f".format(
                    tokensSavedPercent
                )
            }%"

            tokensSavedPercent > 15 -> "   👍 ХОРОШО! Компрессия сократила расход токенов на ${
                "%.1f".format(
                    tokensSavedPercent
                )
            }%"

            tokensSavedPercent > 0 -> "   👌 НЕПЛОХО! Компрессия сократила расход токенов на ${
                "%.1f".format(
                    tokensSavedPercent
                )
            }%"

            else -> "   ⚠️ Компрессия не дала экономии токенов в этом тесте"
        }
    }
    
    ${if (costSaved > 0) "   💰 Экономия средств: $${"%.6f".format(costSaved)}" else ""}
    
    |
    | Рекомендации:
    |   1. Компрессия особенно эффективна для длинных диалогов (>20 сообщений)
    |   2. Настройте keepLastMessages (8-12) и summarizeEvery (5-8) под ваши задачи
    |   3. Для моделей с большим контекстом можно увеличить keepLastMessages
    |   4. Для коротких диалогов компрессию можно отключать
""".trimMargin()

    fun getTokenDemoHeader(): String = """
    
    ${"=".repeat(80)}
    ДЕМОНСТРАЦИЯ ОТСЛЕЖИВАНИЯ ТОКЕНОВ
    ${"=".repeat(80)}
""".trimIndent()

    fun getShortDialogueTestHeader(): String = """
    
    📝 ТЕСТ 1: Короткий диалог (3 сообщения)
""".trimIndent()

    fun getLongDialogueTestHeader(count: Int): String = """
    
    ${"=".repeat(80)}
    📝 ТЕСТ 2: Длинный диалог ($count сообщений с контекстом)
""".trimIndent()

    fun getStatsReport(
        shortTotal: Int, shortAvg: Int,
        longTotal: Int, longAvg: Int, growthRate: Double,
        extraTotal: Int, extraAvg: Int,
        requestCount: Int, totalTokens: Int, cost: Double
    ): String = """
    📊 СТАТИСТИКА ДЛЯ АНАЛИЗА:
    
    🔹 КОРОТКИЙ ДИАЛОГ (3 сообщения):
       • Всего токенов: $shortTotal
       • Среднее на сообщение: $shortAvg
    
    🔹 ДЛИННЫЙ ДИАЛОГ (${longDialogueTopics.size} сообщений):
       • Всего токенов: $longTotal
       • Среднее на сообщение: $longAvg
       • Рост токенов: ${"%.1f".format(growthRate)}x
    
    🔹 ДОПОЛНИТЕЛЬНЫЕ ЗАПРОСЫ (5 сообщений):
       • Всего токенов: $extraTotal
       • Среднее на сообщение: $extraAvg
    
    🔹 ОБЩАЯ СТАТИСТИКА:
       • Всего запросов: $requestCount
       • Всего токенов: $totalTokens
       • Общая стоимость: ${String.format("%.6f", cost)}
    
    Вопросы для анализа:
    1. Сравните короткий и длинный диалог. Как растет количество токенов?
    2. Как стоимость зависит от длины диалога?
    3. Какие выводы можно сделать для оптимизации использования токенов?
    
    Пожалуйста, сделай подробные выводы на русском языке.
""".trimIndent()

    fun formatShortDialogueSummary(
        totalTokens: Int,
        promptTokens: Int,
        completionTokens: Int,
        cost: Double
    ): String = """
    
    📊 ИТОГО после короткого диалога:
    Prompt: $promptTokens | Completion: $completionTokens | Total: $totalTokens
    Стоимость: ${String.format("%.6f", cost)}
""".trimIndent()

    fun getLongDialogueFinalStats(
        totalTokens: Int,
        promptTokens: Int,
        completionTokens: Int,
        cost: Double,
        contextStatus: String
    ): String = """
    
    📊 ФИНАЛЬНАЯ СТАТИСТИКА после длинного диалога:
    Prompt: $promptTokens | Completion: $completionTokens | Total: $totalTokens
    Стоимость: ${String.format("%.6f", cost)}
    Статус контекста: $contextStatus
""".trimIndent()

    fun getExtraQuestionsTestHeader(): String = """
    
    ${"=".repeat(80)}
    📝 ТЕСТ 3: ДОПОЛНИТЕЛЬНЫЕ ЗАПРОСЫ
    ${"=".repeat(80)}
""".trimIndent()
}
