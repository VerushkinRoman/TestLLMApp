package com.llmapp.demo.manager

import com.llmapp.api.ApiConfig
import com.llmapp.chat.ChatSession
import com.llmapp.demo.evaluation.DemoEvaluator
import com.llmapp.demo.evaluation.TestCase
import com.llmapp.memory.TaskMemory
import com.llmapp.memory.TaskMemoryTracker
import com.llmapp.model.TokenStats
import com.llmapp.model.TokenUsage
import com.llmapp.rag.RAGEnhancer
import com.llmapp.rag.RagMode
import com.llmapp.rag.data.JsonIndexRepository
import com.llmapp.rag.data.TechArticlesDocuments
import com.llmapp.rag.domain.RerankerConfig
import com.llmapp.rag.domain.RerankerType
import com.llmapp.ui.models.ChatMessageUI
import com.llmapp.ui.models.RagSourceUI
import kotlinx.coroutines.delay
import java.io.File
import kotlin.time.Duration.Companion.seconds

private data class DemoQuestion(
    val msgIndex: Int,
    val userMessage: String,
    val topicLabel: String,
)

private val questions = listOf(
    DemoQuestion(
        1,
        "Привет! Я хочу разработать мобильное приложение для управления задачами на Kotlin Multiplatform — iOS и Android. Нужно офлайн-хранение, синхронизация с сервером, тёмная тема. С чего начать и какой стек выбрать?",
        "📋 KMP приложение"
    ),
    DemoQuestion(
        2,
        "Нравится идея MVI + Clean Architecture. Как организовать Dependency Injection в KMP? Kodein-DI достаточно или нужен Koin?",
        "📋 KMP приложение"
    ),
    DemoQuestion(
        3,
        "Какие библиотеки для навигации в Compose Multiplatform сейчас актуальны? Voyager или Decompose? Или можно что-то попроще?",
        "📋 KMP приложение"
    ),
    DemoQuestion(
        4,
        "Для сети буду использовать Ktor. А для локального хранения — SQLDelight. Как правильно организовать репозиторий, который умеет работать и офлайн, и онлайн с синхронизацией?",
        "📋 KMP приложение"
    ),
    DemoQuestion(
        5,
        "Нужно шифрование локальных данных пользователя. SQLDelight дружит с SQLCipher? Или есть альтернативы?",
        "📋 KMP приложение"
    ),
    DemoQuestion(
        6,
        "Как организовать логирование ошибок и метрики в KMP? Crashlytics нет, Firebase Analytics под вопросом.",
        "📋 KMP приложение"
    ),
    DemoQuestion(
        7,
        "Тёмная тема и адаптивный UI — как это делается в Compose Multiplatform? Нужно поддерживать планшеты.",
        "📋 KMP приложение"
    ),
    DemoQuestion(
        8,
        "Итак, стек: KMP + MVI + Kodein + Ktor + SQLDelight + Voyager. Ещё нужно офлайн + синхронизация. Ничего не упустил?",
        "📋 KMP приложение"
    ),

    DemoQuestion(
        9,
        "Допустим, приложение готово. Нужно настроить CI/CD для сборки APK и IPA при каждом пуше. GitHub Actions подойдёт? Какие шаги обязательны?",
        "🔧 CI/CD"
    ),
    DemoQuestion(
        10,
        "Как настроить матрицу сборки в GitHub Actions для Android и iOS? iOS же требует macOS — это как-то решается?",
        "🔧 CI/CD"
    ),
    DemoQuestion(
        11,
        "Хочу добавить Detekt и ktlint в пайплайн. Как настроить, чтобы CI падал при нарушениях?",
        "🔧 CI/CD"
    ),
    DemoQuestion(
        12,
        "Нужны автоматические тесты в CI. Юнит-тесты KMP запускаются на всех платформах? А UI-тесты?",
        "🔧 CI/CD"
    ),
    DemoQuestion(
        13,
        "Как организовать деплой: Android — в Firebase App Distribution для тестировщиков, iOS — через TestFlight?",
        "🔧 CI/CD"
    ),

    DemoQuestion(
        14,
        "От CI перейдём к тестированию. Как правильно тестировать ViewModel в MVI? Нужно проверять StateFlow и события.",
        "🧪 Тесты"
    ),
    DemoQuestion(15, "А как тестировать корутины и Flow? Использовать Turbine?", "🧪 Тесты"),
    DemoQuestion(
        16,
        "Как быть с UI-тестами на Compose? Они кросс-платформенные или нужно писать отдельно для Android и iOS?",
        "🧪 Тесты"
    ),
    DemoQuestion(17, "Скриншотное тестирование в KMP — есть готовые решения?", "🧪 Тесты"),

    DemoQuestion(
        18,
        "Всё протестировали. Теперь подготовка к релизу. Google Play и App Store — какие общие требования? Разные аккаунты же нужны?",
        "📦 Релиз"
    ),
    DemoQuestion(
        19,
        "Как подготавливать иконки и скриншоты для двух сторов? Есть инструменты автоматизации?",
        "📦 Релиз"
    ),
    DemoQuestion(
        20,
        "После релиза нужна аналитика. Что выбрать: Firebase Analytics, Mixpanel, или самописное?",
        "📦 Релиз"
    ),
    DemoQuestion(
        21,
        "Сбор краш-репортов тоже нужен. Firebase Crashlytics нет для KMP. Какие альтернативы?",
        "📦 Релиз"
    ),

    DemoQuestion(
        22,
        "Хорошо, вернёмся к нашему приложению для управления задачами. Мы обсуждали синхронизацию — как лучше реализовать Sync при возвращении сети после офлайна? Конфликты данных как решать?",
        "📋 Возврат к KMP"
    ),
    DemoQuestion(
        23,
        "Помнишь, мы выбрали Voyager для навигации? У нас будет сложный экран задачи с тегами, напоминаниями, подзадачами — Voyager справится с такой вложенностью?",
        "📋 Возврат к KMP"
    ),
    DemoQuestion(
        24,
        "Финальный вопрос: я хочу запустить проект. Какой был итоговый стек и первым шагом что сделать — модули, архитектура, DI? Напомни всё, что мы обсуждали.",
        "📋 Возврат к KMP"
    ),
)

class ContextRetentionDemoRunner(
    onMessageAdded: (ChatMessageUI) -> Unit,
    onTypingStateChanged: (Boolean) -> Unit,
    private val chatSession: ChatSession,
    private val onTaskMemoryUpdated: ((TaskMemory) -> Unit)? = null,
    private val onStatsUpdated: ((TokenStats) -> Unit)? = null,
) : BaseDemoRunner(onMessageAdded, onTypingStateChanged, delayMs = 0) {

    private var accumulatedStats = TokenStats()

    override suspend fun run() {
        val techIndexDir = System.getProperty("user.home") + "/.llm_chat_app/tech_rag_index"

        val enhancer = RAGEnhancer(
            repository = JsonIndexRepository(indexDir = techIndexDir),
            documentProvider = { TechArticlesDocuments.getAll() },
            topK = 10,
            rerankerConfig = RerankerConfig(
                type = RerankerType.HEURISTIC,
                similarityThreshold = 0.3f,
                topKBefore = 20,
                topKAfter = 5,
            ),
            mode = RagMode.REWRITE_FILTER,
        )

        val indexDir = File(techIndexDir)
        if (indexDir.exists() && (indexDir.listFiles()?.isNotEmpty() == true)) {
            addMessage(
                "assistant",
                "🔄 Загружаю RAG-индекс технических статей...",
                metadata = "Индексация",
                isDemoMessage = true
            )
        } else {
            addMessage(
                "assistant",
                "🔄 RAG-индекс не найден, запускаю индексацию технических статей...",
                metadata = "Индексация",
                isDemoMessage = true
            )
        }

        try {
            enhancer.ensureIndexLoaded()
        } catch (e: Exception) {
            addMessage(
                "assistant",
                "⚠️ Ошибка загрузки индекса: ${e.message}",
                metadata = "Ошибка",
                isDemoMessage = true
            )
            return
        }

        addMessage("assistant", buildString {
            appendLine("🎯 **Демонстрация удержания контекста: 24 сообщения, 4 темы**")
            appendLine()
            appendLine("**Сценарий:**")
            appendLine("  📋 **Тема 1** (1-8):  KMP приложение для задач — ставим задачу")
            appendLine("  🔧 **Тема 2** (9-13):  CI/CD инфраструктура")
            appendLine("  🧪 **Тема 3** (14-17): Тестирование")
            appendLine("  📦 **Тема 4** (18-21): Релиз и аналитика")
            appendLine("  📋 **Возврат** (22-24): Проверка памяти — модель помнит контекст")
            appendLine()
            appendLine("Буду искать информацию в базе знаний, показывать источники и отслеживать задачу через TaskMemory.")
        }, metadata = "Старт", isDemoMessage = true)

        val savedRagEnabled = chatSession.ragEnabled
        chatSession.ragEnabled = false

        val firstQuestionOfTopic = setOf(1, 9, 14, 18, 22)

        for (q in questions) {
            checkCancelled()

            if (q.msgIndex in firstQuestionOfTopic) {
                val headerLine = when (q.msgIndex) {
                    1 -> "┏━━━━━━━━━ ТЕМА 1: KMP-приложение для задач ━━━━━━━━━┓"
                    9 -> "┏━━━━━━━━━ ТЕМА 2: CI/CD инфраструктура ━━━━━━━━━┓"
                    14 -> "┏━━━━━━━━━ ТЕМА 3: Тестирование ━━━━━━━━━┓"
                    18 -> "┏━━━━━━━━━ ТЕМА 4: Релиз и аналитика ━━━━━━━━━┓"
                    22 -> "┏━━━━━━━━━ ВОЗВРАТ К ТЕМЕ 1: Проверка контекста ━━━━━━━━━┓"
                    else -> ""
                }
                addMessage("assistant", headerLine, metadata = q.topicLabel, isDemoMessage = true)
            }

            val ragAnswer = try {
                enhancer.searchWithStructuredContext(q.userMessage)
            } catch (e: Exception) {
                addMessage(
                    "assistant",
                    "❌ Ошибка RAG: ${e.message}",
                    metadata = "Ошибка",
                    isDemoMessage = true
                )
                continue
            }

            val ragPrompt = buildRagPrompt(q.userMessage, ragAnswer)
            addMessage(
                "user",
                q.userMessage,
                metadata = "✏️ ${q.msgIndex}/24 | ${q.topicLabel}",
                isDemoMessage = true
            )

            onTypingStateChanged(true)
            val response = try {
                chatSession.ask(ragPrompt, isRegeneration = false)
            } catch (e: Exception) {
                onTypingStateChanged(false)
                addMessage(
                    "assistant",
                    "Ошибка: ${e.message}",
                    metadata = "Ошибка",
                    isDemoMessage = true
                )
                continue
            }
            onTypingStateChanged(false)

            accumulatedStats = accumulatedStats.addUsage(
                usage = TokenUsage(
                    promptTokens = response.promptTokens ?: 0,
                    completionTokens = response.completionTokens ?: 0,
                    totalTokens = response.totalTokens ?: 0,
                ),
            )
            onStatsUpdated?.invoke(accumulatedStats)

            val sourceUIs = ragAnswer.sources.map { src ->
                RagSourceUI(title = src.title, section = src.section, score = src.score)
            }

            val meta = buildString {
                append("📊 ${q.msgIndex}/24")
                if (sourceUIs.isNotEmpty()) append(" 📚 ${sourceUIs.size} ист.")
                append(" | ${q.topicLabel}")
            }

            val cleanContent = stripMemoryMarkers(response.content)

            addMessage(
                role = "assistant",
                content = cleanContent,
                metadata = meta,
                promptTokens = response.promptTokens,
                completionTokens = response.completionTokens,
                totalTokens = response.totalTokens,
                responseTimeMs = response.responseTimeMs,
                isDemoMessage = true,
                ragSources = sourceUIs,
            )

            if (response.compressionNotification != null) {
                TaskMemoryTracker.processCompressionSummary(response.compressionNotification)
                val formattedContent = TaskMemoryTracker.formatCompressionAsMarkdown()
                addMessage(
                    "system",
                    formattedContent,
                    metadata = "Компрессия",
                    isDemoMessage = true
                )
            }

            TaskMemoryTracker.processMessage()
            extractDemoMemoryFromResponse(response.content)
            onTaskMemoryUpdated?.invoke(TaskMemoryTracker.getMemory())

            delay(0.5.seconds)
        }

        chatSession.ragEnabled = savedRagEnabled

        val memory = TaskMemoryTracker.getMemory()
        val p = memory.progress
        addMessage("assistant", buildString {
            appendLine("━".repeat(60))
            appendLine("**🎯 Демонстрация завершена — 24 сообщения, 4 темы**")
            appendLine("━".repeat(60))
            appendLine()
            appendLine("**📊 TaskMemory — извлечено автоматом из диалога:**")
            appendLine()
            if (memory.goal.isNotEmpty()) appendLine("🎯 **Goal:** ${memory.goal}")
            if (memory.constraintsAndPrefs.isNotEmpty()) {
                appendLine("🔒 **Constraints (${memory.constraintsAndPrefs.size}):**")
                memory.constraintsAndPrefs.take(5).forEach { appendLine("  • $it") }
            }
            if (p.done.isNotEmpty()) {
                appendLine("✅ **Done (${p.done.size}):**")
                p.done.take(5).forEach { appendLine("  • $it") }
            }
            if (p.inProgress.isNotEmpty()) {
                appendLine("🔄 **In Progress (${p.inProgress.size}):**")
                p.inProgress.take(5).forEach { appendLine("  • $it") }
            }
            if (p.blocked.isNotEmpty()) {
                appendLine("🚫 **Blocked (${p.blocked.size}):**")
                p.blocked.take(5).forEach { appendLine("  • $it") }
            }
            if (memory.decisions.isNotEmpty()) {
                appendLine("✅ **Key Decisions (${memory.decisions.size}):**")
                memory.decisions.take(5).forEach { appendLine("  • $it") }
            }
            if (memory.criticalContext.isNotEmpty()) {
                appendLine("📖 **Context (${memory.criticalContext.size}):**")
                memory.criticalContext.take(10).forEach { appendLine("  • $it") }
            }
            appendLine()
            appendLine("**🔄 Смена тем:**")
            appendLine("  1️⃣ 📋 KMP приложение (1-8)")
            appendLine("  2️⃣ 🔧 CI/CD (9-13)")
            appendLine("  3️⃣ 🧪 Тестирование (14-17)")
            appendLine("  4️⃣ 📦 Релиз (18-21)")
            appendLine("  🔙 📋 Возврат к теме 1 (22-24) — контекст сохранён ✅")
            appendLine()
        }, metadata = "ИТОГИ", isDemoMessage = true)

        delay(2.seconds)

        addMessage(
            "assistant",
            "🤖 **Агент-оценщик анализирует удержание контекста...**",
            metadata = "Оценка LLM",
            isDemoMessage = true
        )
        delay(1.seconds)

        try {
            val compressionMessages = questions.filterIndexed { _, q ->
                q.msgIndex in setOf(8, 17, 21)
            }

            val testCases = compressionMessages.mapIndexed { idx, q ->
                val currentGoal = memory.goal
                val goalOk = currentGoal.isNotBlank() && (
                        currentGoal.contains("кроссплатформенн", ignoreCase = true) ||
                                currentGoal.contains("Kotlin Multiplatform", ignoreCase = true) ||
                                currentGoal.contains("управления задача", ignoreCase = true) ||
                                currentGoal.contains("KMP", ignoreCase = true) ||
                                currentGoal.contains("Multiplatform", ignoreCase = true)
                        )
                val goalStatus = if (goalOk) "✅" else "❌ (цель потеряна)"
                TestCase(
                    id = idx + 1,
                    description = "Сжатие после темы ${q.topicLabel} (сообщение ${q.msgIndex})",
                    expectedBehavior = "Цель должна остаться: «Разработать кроссплатформенное приложение для управления задачами с офлайн-хранением, синхронизацией и тёмной темой». "
                            + "Все ключевые технические детали (офлайн-хранение, синхронизация, тёмная тема, кроссплатформенность) должны сохраняться. "
                            + "При смене темы на CI/CD/тесты/релиз — цель НЕ должна меняться или упрощаться.",
                    actualResponse = "Goal: «${currentGoal.take(80)}» $goalStatus. "
                            + "Constraints: ${memory.constraintsAndPrefs.size}, "
                            + "Decisions: ${memory.decisions.size}, "
                            + "Context: ${memory.criticalContext.size}",
                    metrics = mapOf(
                        "Constraints" to memory.constraintsAndPrefs.size.toString(),
                        "Done" to p.done.size.toString(),
                        "InProgress" to p.inProgress.size.toString(),
                        "Blocked" to p.blocked.size.toString(),
                        "Decisions" to memory.decisions.size.toString(),
                        "Context" to memory.criticalContext.size.toString(),
                    ),
                )
            }

            val evaluator = DemoEvaluator(apiKey = ApiConfig.getApiKey())
            val evalResult = evaluator.evaluate(
                demoName = "Удержание контекста (24 сообщения, 4 темы)",
                testCases = testCases,
                additionalContext = "Если цель в памяти не совпадает с исходной — это баг сжатия. "
                        + "Оцени, насколько хорошо TaskMemory сохранил сквозную цель сквозь смены тем.",
            )

            addMessage(
                "assistant",
                "📊 **ОЦЕНКА АГЕНТА**\n\n$evalResult",
                metadata = "Оценка LLM",
                isDemoMessage = true
            )
        } catch (e: Exception) {
            addMessage(
                "assistant",
                "❌ **Ошибка при оценке агентом:** ${e.message}",
                metadata = "Ошибка оценки",
                isDemoMessage = true
            )
        }
    }

    private fun buildRagPrompt(
        userQuery: String,
        ragAnswer: com.llmapp.rag.domain.RagAnswer
    ): String {
        val sourcesText = ragAnswer.sources
            .mapIndexed { i, s -> "[${i + 1}] ${s.title} — ${s.section} (score: ${"%.3f".format(s.score)})" }
            .joinToString("\n")

        val hasRelevantSources =
            ragAnswer.sources.isNotEmpty() && ragAnswer.sources.any { it.score > 0.3f }

        return buildString {
            appendLine("Ты — ассистент. Отвечай на русском языке, кратко и по делу.")
            appendLine()

            if (hasRelevantSources) {
                appendLine("=== КОНТЕКСТ ИЗ БАЗЫ ЗНАНИЙ (используй его если релевантен) ===")
                appendLine(ragAnswer.answer)
                appendLine("=== КОНЕЦ КОНТЕКСТА ===")
                appendLine()
                appendLine("=== ИСТОЧНИКИ ===")
                appendLine(sourcesText)
                appendLine()
                appendLine("Если информация из контекста релевантна вопросу — используй её и ОБЯЗАТЕЛЬНО укажи источники в формате [1], [2] и т.д.")
                appendLine("Если контекст не относится к вопросу или его недостаточно — ответь на основе своих знаний, просто не ссылайся на источники.")
            } else {
                appendLine("(Релевантных результатов в базе знаний не найдено. Ответь на основе своих знаний.)")
            }

            appendLine()
            appendLine("Вопрос пользователя: $userQuery")
            appendLine()
            appendLine("Твой ответ:")
        }
    }

    private fun extractDemoMemoryFromResponse(content: String) {
        val goalRegex = Regex("\\[GOAL](.*?)\\[/GOAL]", RegexOption.DOT_MATCHES_ALL)
        val constraintRegex =
            Regex("\\[CONSTRAINT](.*?)\\[/CONSTRAINT]", RegexOption.DOT_MATCHES_ALL)
        val decisionRegex = Regex("\\[DECISION](.*?)\\[/DECISION]", RegexOption.DOT_MATCHES_ALL)
        val contextRegex = Regex("\\[CONTEXT](.*?)\\[/CONTEXT]", RegexOption.DOT_MATCHES_ALL)
        val progDoneRegex =
            Regex("\\[PROGRESS_DONE](.*?)\\[/PROGRESS_DONE]", RegexOption.DOT_MATCHES_ALL)
        val progInRegex = Regex(
            "\\[PROGRESS_IN_PROGRESS](.*?)\\[/PROGRESS_IN_PROGRESS]",
            RegexOption.DOT_MATCHES_ALL
        )
        val progBlockedRegex =
            Regex("\\[PROGRESS_BLOCKED](.*?)\\[/PROGRESS_BLOCKED]", RegexOption.DOT_MATCHES_ALL)

        val goal = goalRegex.find(content)?.groupValues?.getOrNull(1)?.trim()
        val constraintsAndPrefs = constraintRegex.findAll(content).mapNotNull {
            it.groupValues.getOrNull(1)?.trim().takeIf { c -> c != null && c.length > 3 }
        }.toList().takeIf { it.isNotEmpty() }
        val decisions = decisionRegex.findAll(content).mapNotNull {
            it.groupValues.getOrNull(1)?.trim().takeIf { c -> c != null && c.length > 3 }
        }.toList().takeIf { it.isNotEmpty() }
        val criticalContext = contextRegex.findAll(content).mapNotNull {
            it.groupValues.getOrNull(1)?.trim().takeIf { c -> c != null && c.length > 1 }
        }.toList().takeIf { it.isNotEmpty() }
        val progDone = progDoneRegex.findAll(content).mapNotNull {
            it.groupValues.getOrNull(1)?.trim().takeIf { c -> c != null && c.length > 3 }
        }.toList().takeIf { it.isNotEmpty() }
        val progIn = progInRegex.findAll(content).mapNotNull {
            it.groupValues.getOrNull(1)?.trim().takeIf { c -> c != null && c.length > 3 }
        }.toList().takeIf { it.isNotEmpty() }
        val progBlocked = progBlockedRegex.findAll(content).mapNotNull {
            it.groupValues.getOrNull(1)?.trim().takeIf { c -> c != null && c.length > 3 }
        }.toList().takeIf { it.isNotEmpty() }

        if (goal != null || constraintsAndPrefs != null || decisions != null || criticalContext != null ||
            progDone != null || progIn != null || progBlocked != null
        ) {
            TaskMemoryTracker.processLLMResult(
                goal = goal,
                constraintsAndPrefs = constraintsAndPrefs,
                progressDone = progDone,
                progressInProgress = progIn,
                progressBlocked = progBlocked,
                decisions = decisions,
                criticalContext = criticalContext,
                replaceItems = false,
            )
        }
    }

    private fun stripMemoryMarkers(text: String): String {
        return text
            .replace(Regex("\\[GOAL].*?\\[/GOAL]", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("\\[CONSTRAINT].*?\\[/CONSTRAINT]", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("\\[DECISION].*?\\[/DECISION]", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("\\[CONTEXT].*?\\[/CONTEXT]", RegexOption.DOT_MATCHES_ALL), "")
            .replace(
                Regex(
                    "\\[PROGRESS_DONE].*?\\[/PROGRESS_DONE]",
                    RegexOption.DOT_MATCHES_ALL
                ), ""
            )
            .replace(
                Regex(
                    "\\[PROGRESS_IN_PROGRESS].*?\\[/PROGRESS_IN_PROGRESS]",
                    RegexOption.DOT_MATCHES_ALL
                ), ""
            )
            .replace(
                Regex(
                    "\\[PROGRESS_BLOCKED].*?\\[/PROGRESS_BLOCKED]",
                    RegexOption.DOT_MATCHES_ALL
                ), ""
            )
            .trim()
    }
}
