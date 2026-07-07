package com.llmapp.demo.manager

import com.llmapp.chat.ChatSession
import com.llmapp.ui.models.ChatMessageUI
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

class LocalAgentFlowDemoRunner(
    private val chatSession: ChatSession,
    onMessageAdded: (ChatMessageUI) -> Unit,
    onTypingStateChanged: (Boolean) -> Unit,
    private val onLocalModeChanged: ((Boolean) -> Unit)? = null,
) : BaseDemoRunner(onMessageAdded, onTypingStateChanged, delayMs = 200) {

    private data class QAPair(val question: String, val answer: String)

    override suspend fun run() {
        val qaPairs = mutableListOf<QAPair>()

        try {
            addMessage("system", "🧪 АГЕНТСКИЙ ФЛОУ С ЛОКАЛЬНОЙ МОДЕЛЬЮ (Ollama)")
            addMessage("system", "Переключаюсь на локальную модель...")
            chatSession.switchLocalMode(true)
            onLocalModeChanged?.invoke(true)
            addMessage("system", "✅ Локальная модель активирована (gemma4:26b через Ollama)")
            delay(500.milliseconds)

            // ============================================================
            // ЭТАП 1: СОЗДАНИЕ ЗАДАЧИ
            // ============================================================
            addMessage("system", "━━━ ЭТАП 1: Создание задачи ━━━")
            val q1 = "Создай новую задачу: разработать архитектуру микросервисного приложения на Kotlin. Основные требования: REST API, PostgreSQL, Docker, CI/CD через GitHub Actions."
            addMessage("user", q1)
            onTypingStateChanged(true)
            try {
                val response1 = chatSession.ask("$q1 Используй формат: [GOAL]...[/GOAL], [CONSTRAINT]...[/CONSTRAINT], [DECISION]...[/DECISION]")
                addMessage("assistant", response1.content, totalTokens = response1.totalTokens, responseTimeMs = response1.responseTimeMs)
                qaPairs.add(QAPair(q1, response1.content))
            } catch (e: Exception) {
                addMessage("assistant", "❌ Ошибка: ${e.message}")
                qaPairs.add(QAPair(q1, "[Ошибка: ${e.message}]"))
            }
            onTypingStateChanged(false)
            delay(300.milliseconds)

            // ============================================================
            // ЭТАП 2: ПЛАНИРОВАНИЕ С ПАМЯТЬЮ
            // ============================================================
            addMessage("system", "━━━ ЭТАП 2: Планирование с памятью ━━━")
            val q2 = "Распиши детальный план по шагам для реализации этой архитектуры."
            addMessage("user", q2)
            onTypingStateChanged(true)
            try {
                val response2 = chatSession.ask("$q2 Укажи этапы: проектирование, разработка, тестирование, деплой.")
                addMessage("assistant", response2.content, totalTokens = response2.totalTokens, responseTimeMs = response2.responseTimeMs)
                qaPairs.add(QAPair(q2, response2.content))
            } catch (e: Exception) {
                addMessage("assistant", "❌ Ошибка: ${e.message}")
                qaPairs.add(QAPair(q2, "[Ошибка: ${e.message}]"))
            }
            onTypingStateChanged(false)
            delay(300.milliseconds)

            // ============================================================
            // ЭТАП 3: ПЕРЕХОД СОСТОЯНИЯ
            // ============================================================
            addMessage("system", "━━━ ЭТАП 3: Управление состоянием задачи ━━━")
            val q3 = "Мы переходим к этапу разработки. Какие технологии и библиотеки Kotlin ты рекомендуешь использовать для REST API микросервисов? Назови топ-5 библиотек."
            addMessage("user", q3)
            onTypingStateChanged(true)
            try {
                val response3 = chatSession.ask("$q3 Основываясь на предыдущем контексте, назови топ-5 библиотек с кратким обоснованием.")
                addMessage("assistant", response3.content, totalTokens = response3.totalTokens, responseTimeMs = response3.responseTimeMs)
                qaPairs.add(QAPair(q3, response3.content))
            } catch (e: Exception) {
                addMessage("assistant", "❌ Ошибка: ${e.message}")
                qaPairs.add(QAPair(q3, "[Ошибка: ${e.message}]"))
            }
            onTypingStateChanged(false)
            delay(300.milliseconds)

            // ============================================================
            // ЭТАП 4: КОД
            // ============================================================
            addMessage("system", "━━━ ЭТАП 4: Генерация кода ━━━")
            val q4 = "Напиши пример Dockerfile для Kotlin-микросервиса и docker-compose.yml для запуска сервиса с PostgreSQL."
            addMessage("user", q4)
            onTypingStateChanged(true)
            try {
                val response4 = chatSession.ask("$q4 Код должен быть готов к использованию.")
                addMessage("assistant", response4.content, totalTokens = response4.totalTokens, responseTimeMs = response4.responseTimeMs)
                qaPairs.add(QAPair(q4, response4.content))
            } catch (e: Exception) {
                addMessage("assistant", "❌ Ошибка: ${e.message}")
                qaPairs.add(QAPair(q4, "[Ошибка: ${e.message}]"))
            }
            onTypingStateChanged(false)
            delay(300.milliseconds)

            // ============================================================
            // ЭТАП 5: РЕФАКТОРИНГ
            // ============================================================
            addMessage("system", "━━━ ЭТАП 5: Рефакторинг с учетом контекста ━━━")
            val q5 = "Вспомни все что мы обсуждали. Предложи улучшения для архитектуры: какие ещё сервисы стоит добавить, какие паттерны применить? Ответ структурируй по категориям."
            addMessage("user", q5)
            onTypingStateChanged(true)
            try {
                val response5 = chatSession.ask("$q5 Основываясь на всем нашем разговоре (архитектура микросервисов, план, технологии, Docker), предложи улучшения.")
                addMessage("assistant", response5.content, totalTokens = response5.totalTokens, responseTimeMs = response5.responseTimeMs)
                qaPairs.add(QAPair(q5, response5.content))
            } catch (e: Exception) {
                addMessage("assistant", "❌ Ошибка: ${e.message}")
                qaPairs.add(QAPair(q5, "[Ошибка: ${e.message}]"))
            }
            onTypingStateChanged(false)
            delay(500.milliseconds)

            // ============================================================
            // ПЕРЕКЛЮЧЕНИЕ НА ОБЛАКО + ОЦЕНКА
            // ============================================================
            addMessage("system", "━━━ ОЦЕНКА ЛОКАЛЬНОЙ МОДЕЛИ ОБЛАЧНОЙ ━━━")
            addMessage("system", "Переключаюсь на облачную модель для оценки ответов локальной...")
            chatSession.switchLocalMode(false)
            onLocalModeChanged?.invoke(false)
            addMessage("system", "✅ Облачная модель активирована. Отправляю вопросы и ответы локальной модели на оценку...")
            delay(500.milliseconds)

            val qaText = qaPairs.withIndex().joinToString("\n\n") { (i, pair) ->
                "=== Вопрос ${i + 1} ===\n${pair.question}\n\n=== Ответ локальной модели ${i + 1} ===\n${pair.answer}"
            }

            val evalPrompt = """
                Ты — эксперт по оценке качества LLM. Оцени ответы локальной модели на 5 вопросов ниже.

                $qaText

                По каждому вопросу дай оценку от 1 до 10 и краткий комментарий (1-2 предложения).
                В конце подведи общий итог — среднюю оценку, сильные стороны и что можно улучшить.
                Оценивай по критериям: полнота, точность, понятность, релевантность контексту.
                Пиши на русском языке.
            """.trimIndent()

            addMessage("user", "Оцени качество ответов локальной модели по 5 вопросам (критерии: полнота, точность, понятность, релевантность).")
            onTypingStateChanged(true)
            try {
                chatSession.clearHistory()
                val evalResponse = chatSession.ask(evalPrompt)
                addMessage("assistant", evalResponse.content, totalTokens = evalResponse.totalTokens, responseTimeMs = evalResponse.responseTimeMs, metadata = "Оценка облачной модели")
            } catch (e: Exception) {
                addMessage("assistant", "❌ Ошибка оценки: ${e.message}")
            }
            onTypingStateChanged(false)

        } finally {
            chatSession.switchLocalMode(false)
            onLocalModeChanged?.invoke(false)
        }
    }
}
