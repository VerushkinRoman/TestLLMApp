package com.llmapp.demo.manager

import com.llmapp.api.OllamaClient
import com.llmapp.chat.ChatSession
import com.llmapp.model.ChatMessage
import com.llmapp.model.RouterRequest
import com.llmapp.ui.models.ChatMessageUI

class OptimizationDemoRunner(
    onMessageAdded: (ChatMessageUI) -> Unit,
    onTypingStateChanged: (Boolean) -> Unit,
    private val chatSession: ChatSession,
    private val onLocalModeChanged: ((Boolean) -> Unit)? = null,
) : BaseDemoRunner(onMessageAdded, onTypingStateChanged, delayMs = 0) {

    private val ollama = OllamaClient()

    private data class AnswerData(
        val text: String,
        val timeMs: Long,
        val tokens: Int,
    )

    override suspend fun run() {
        onLocalModeChanged?.invoke(true)
        val questions = listOf(
            "Спроектируй архитектуру чат-бота для техподдержки интернет-магазина с интеграцией CRM и обработкой заказов. Какие компоненты, паттерны и технологии выберешь? Опиши подробно.",
            "Напиши код конечного автомата (state machine) для управления диалогом чат-бота на Python. Состояния: приветствие, выбор категории, уточнение, подтверждение, обратная связь. Реализуй переходы и контекст.",
            "Как эффективно управлять контекстом диалога в чат-боте при лимите 8000 токенов? Опиши алгоритмы сжатия, суммаризации и стратегии управления окном контекста. Приведи псевдокод."
        )

        // ============================================================
        // ФАЗА 1: НЕОПТИМИЗИРОВАННАЯ МОДЕЛЬ
        // ============================================================
        val unoptimizedSystem = "Ты полезный ассистент. Отвечай кратко и по делу на русском языке."
        val unoptimizedQA = mutableListOf<AnswerData>()
        var unoptimizedTotalTime = 0L
        var unoptimizedTotalTokens = 0

        addMessage("assistant", buildString {
            appendLine("## ⚙️ ФАЗА 1: НЕОПТИМИЗИРОВАННАЯ МОДЕЛЬ")
            appendLine()
            appendLine("**Параметры:**")
            appendLine("- Temperature: `0.7` (значение по умолчанию)")
            appendLine("- Max tokens: не ограничен (дефолт модели)")
            appendLine("- System prompt: базовый")
            appendLine()
            appendLine("Модель: `gemma4:26b` (локально через Ollama)")
        }, metadata = "⚙️ ФАЗА 1")

        for ((i, question) in questions.withIndex()) {
            onTypingStateChanged(true)
            addMessage("user", "**ВОПРОС ${i + 1}:** $question")

            try {
                val startTime = System.currentTimeMillis()
                val response = ollama.sendRequest(RouterRequest(
                    model = "gemma4:26b",
                    messages = listOf(
                        ChatMessage("system", unoptimizedSystem),
                        ChatMessage("user", question)
                    ),
                    temperature = null,
                    maxTokens = null
                ))
                val endTime = System.currentTimeMillis()
                val responseTime = endTime - startTime
                val answer = response.choices?.firstOrNull()?.message?.content ?: "[пустой ответ]"
                val tokens = response.usage?.totalTokens ?: 0
                unoptimizedTotalTime += responseTime
                unoptimizedTotalTokens += tokens

                val clean = stripMarkers(answer)
                addMessage("assistant", clean,
                    metadata = "⚙️ НЕОПТ | ${responseTime}ms | ${tokens} токенов",
                    totalTokens = tokens,
                    promptTokens = response.usage?.promptTokens,
                    completionTokens = response.usage?.completionTokens,
                    responseTimeMs = responseTime)
                unoptimizedQA.add(AnswerData(clean, responseTime, tokens))
            } catch (e: Exception) {
                addMessage("assistant", "❌ Ошибка: ${e.message}", metadata = "Ошибка")
                unoptimizedQA.add(AnswerData("[Ошибка: ${e.message}]", 0, 0))
            }
            onTypingStateChanged(false)
        }

        addMessage("assistant", buildString {
            appendLine("**📊 Статистика Фазы 1 (Неоптимизировано):**")
            appendLine("- Общее время: ${unoptimizedTotalTime}ms (${unoptimizedTotalTime / 1000.0} сек)")
            appendLine("- Всего токенов: $unoptimizedTotalTokens")
            appendLine("- Среднее время на ответ: ${unoptimizedTotalTime / questions.size}ms")
        }, metadata = "📊 Статистика")

        // ============================================================
        // ФАЗА 2: ОПТИМИЗИРОВАННАЯ МОДЕЛЬ
        // ============================================================
        val optimizedSystem = """ТЫ — ЭКСПЕРТ ПО РАЗРАБОТКЕ ЧАТ-БОТОВ.
Специализация: архитектура AI-систем, NLP, диалоговые системы, обработка естественного языка.
Требования к ответам:
- Максимально подробные, структурированные, с примерами кода
- Используй профессиональную терминологию
- Предлагай конкретные технологии, библиотеки, паттерны
- Код на Python/Kotlin с пояснениями
- Форматируй Markdown
- Отвечай на русском языке"""

        val optimizedQA = mutableListOf<AnswerData>()
        var optimizedTotalTime = 0L
        var optimizedTotalTokens = 0

        addMessage("assistant", buildString {
            appendLine("## 🚀 ФАЗА 2: ОПТИМИЗИРОВАННАЯ МОДЕЛЬ")
            appendLine()
            appendLine("**Параметры:**")
            appendLine("- Temperature: `0.2` (детерминированный, точность)")
            appendLine("- Max tokens: `4096` (развернутые ответы)")
            appendLine("- System prompt: кастомный (эксперт по чат-ботам)")
            appendLine()
            appendLine("Модель: `gemma4:26b` (локально через Ollama)")
        }, metadata = "🚀 ФАЗА 2")

        for ((i, question) in questions.withIndex()) {
            onTypingStateChanged(true)
            addMessage("user", "**ВОПРОС ${i + 1} (оптимизировано):** $question")

            try {
                val startTime = System.currentTimeMillis()
                val response = ollama.sendRequest(RouterRequest(
                    model = "gemma4:26b",
                    messages = listOf(
                        ChatMessage("system", optimizedSystem),
                        ChatMessage("user", question)
                    ),
                    temperature = 0.2,
                    maxTokens = 4096
                ))
                val endTime = System.currentTimeMillis()
                val responseTime = endTime - startTime
                val answer = response.choices?.firstOrNull()?.message?.content ?: "[пустой ответ]"
                val tokens = response.usage?.totalTokens ?: 0
                optimizedTotalTime += responseTime
                optimizedTotalTokens += tokens

                val clean = stripMarkers(answer)
                addMessage("assistant", clean,
                    metadata = "🚀 ОПТ | ${responseTime}ms | ${tokens} токенов",
                    totalTokens = tokens,
                    promptTokens = response.usage?.promptTokens,
                    completionTokens = response.usage?.completionTokens,
                    responseTimeMs = responseTime)
                optimizedQA.add(AnswerData(clean, responseTime, tokens))
            } catch (e: Exception) {
                addMessage("assistant", "❌ Ошибка: ${e.message}", metadata = "Ошибка")
                optimizedQA.add(AnswerData("[Ошибка: ${e.message}]", 0, 0))
            }
            onTypingStateChanged(false)
        }

        addMessage("assistant", buildString {
            appendLine("**📊 Статистика Фазы 2 (Оптимизировано):**")
            appendLine("- Общее время: ${optimizedTotalTime}ms (${optimizedTotalTime / 1000.0} сек)")
            appendLine("- Всего токенов: $optimizedTotalTokens")
            appendLine("- Среднее время на ответ: ${optimizedTotalTime / questions.size}ms")
        }, metadata = "📊 Статистика")

        // ============================================================
        // ФАЗА 3: СРАВНЕНИЕ И ОЦЕНКА ОБЛАЧНОЙ МОДЕЛЬЮ
        // ============================================================
        chatSession.switchLocalMode(false)
        onLocalModeChanged?.invoke(false)

        val comparisonData = buildString {
            appendLine("# СРАВНЕНИЕ ОПТИМИЗАЦИИ ЛОКАЛЬНОЙ LLM")
            appendLine()
            appendLine("## Параметры эксперимента")
            appendLine()
            appendLine("### Неоптимизировано:")
            appendLine("- temperature: null (дефолт модели, ~0.7)")
            appendLine("- max_tokens: null (дефолт модели)")
            appendLine("- system prompt: базовый ('Ты полезный ассистент')")
            appendLine("- Общее время: ${unoptimizedTotalTime}ms")
            appendLine("- Всего токенов: $unoptimizedTotalTokens")
            appendLine()
            appendLine("### Оптимизировано:")
            appendLine("- temperature: 0.2 (детерминированный)")
            appendLine("- max_tokens: 4096")
            appendLine("- system prompt: кастомный (эксперт по чат-ботам)")
            appendLine("- Общее время: ${optimizedTotalTime}ms")
            appendLine("- Всего токенов: $optimizedTotalTokens")
            appendLine()
            appendLine("## Ответы модели")
            appendLine()

            for (i in questions.indices) {
                val unopt = unoptimizedQA.getOrElse(i) { AnswerData("Нет данных", 0, 0) }
                val opt = optimizedQA.getOrElse(i) { AnswerData("Нет данных", 0, 0) }
                appendLine("### Вопрос ${i + 1}")
                appendLine()
                appendLine("**Текст:** ${questions[i]}")
                appendLine()
                appendLine("**Метрики:**")
                appendLine("| | Время | Токены |")
                appendLine("|---|---|---|")
                appendLine("| Неоптимизировано | ${unopt.timeMs}ms | ${unopt.tokens} |")
                appendLine("| Оптимизировано | ${opt.timeMs}ms | ${opt.tokens} |")
                appendLine()
                appendLine("#### Неоптимизированный ответ:")
                appendLine(unopt.text)
                appendLine()
                appendLine("#### Оптимизированный ответ:")
                appendLine(opt.text)
                appendLine()
            }

            appendLine("## Задание для оценки")
            appendLine()
            appendLine("Оцени каждую пару ответов (неоптимизированный vs оптимизированный) по следующим критериям:")
            appendLine("1. **Глубина** (насколько подробный и содержательный ответ) — оценка 1-10")
            appendLine("2. **Точность** (насколько ответ соответствует вопросу) — оценка 1-10")
            appendLine("3. **Структурированность** (логичность, форматирование) — оценка 1-10")
            appendLine("4. **Практическая ценность** (можно ли использовать в реальном проекте) — оценка 1-10")
            appendLine()
            appendLine("Для каждого вопроса выведи: **Оценка неопт | Оценка опт | Комментарий**")
            appendLine("В конце выведи **общий итог**: какая конфигурация лучше и насколько.")
        }

        addMessage("user", comparisonData)
        onTypingStateChanged(true)

        try {
            val evalResponse = chatSession.ask(comparisonData)
            onTypingStateChanged(false)
            addMessage("assistant", stripMarkers(evalResponse.content),
                metadata = "🤖 ОЦЕНКА ОБЛАЧНОЙ МОДЕЛЬЮ",
                totalTokens = evalResponse.totalTokens,
                promptTokens = evalResponse.promptTokens,
                completionTokens = evalResponse.completionTokens,
                responseTimeMs = evalResponse.responseTimeMs)
        } catch (e: Exception) {
            onTypingStateChanged(false)
            addMessage("assistant", "❌ Ошибка оценки: ${e.message}", metadata = "Ошибка")
        }
    }

    companion object {
        private val markerRegex = Regex(
            "\\[GOAL].*?\\[/GOAL]|\\[CONSTRAINT].*?\\[/CONSTRAINT]|" +
                    "\\[DECISION].*?\\[/DECISION]|\\[CONTEXT].*?\\[/CONTEXT]|" +
                    "\\[PROGRESS_DONE].*?\\[/PROGRESS_DONE]|\\[PROGRESS_IN_PROGRESS].*?\\[/PROGRESS_IN_PROGRESS]|" +
                    "\\[PROGRESS_BLOCKED].*?\\[/PROGRESS_BLOCKED]|\\[PROGRESSDONE].*?\\[/PROGRESSDONE]",
            RegexOption.DOT_MATCHES_ALL
        )

        private fun stripMarkers(content: String): String {
            return content.replace(markerRegex, "").trim()
        }
    }
}
