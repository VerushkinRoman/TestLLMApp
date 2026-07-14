package com.llmapp.demo.manager

import com.llmapp.chat.ChatSession
import com.llmapp.rag.data.ProjectDocuments
import com.llmapp.rag.domain.ChunkerFactory
import com.llmapp.rag.domain.ChunkingStrategy
import com.llmapp.rag.domain.Document
import com.llmapp.ui.models.ChatMessageUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ProjectDemoRunner(
    onMessageAdded: (ChatMessageUI) -> Unit,
    onTypingStateChanged: (Boolean) -> Unit,
    private val chatSession: ChatSession,
) : BaseDemoRunner(onMessageAdded, onTypingStateChanged) {

    private lateinit var documents: List<Document>

    private val questions = listOf(
        "Какая архитектура используется в проекте?",
        "Какие фичи есть в приложении?",
        "Какие use case'ы используются?",
        "Как организована навигация?",
        "Какие DataSource используются?",
    )

    private data class QARecord(
        val question: String,
        val answer: String,
        val sources: String,
        val ragChunks: Int,
        val topScore: Float,
        val promptTokens: Int?,
        val completionTokens: Int?,
        val responseTimeMs: Long,
    )

    override suspend fun run() {
        // ═══════════════════════════════════════════
        // STEP 1: Introduction
        // ═══════════════════════════════════════════
        addMessage("assistant", buildString {
            appendLine("👨‍💻 **Ассистент разработчика: CalendarKMP**")
            appendLine()
            appendLine("Демонстрация полного пайплайна AI-ассистента:")
            appendLine()
            appendLine("1. 📄 **RAG** — документация проекта (README + docs)")
            appendLine("2. 🔧 **MCP Tools** — чтение исходного кода через GitHub API")
            appendLine("3. 🧠 **LLM** — генерация ответа на основе найденного контекста")
            appendLine()
            appendLine("Демо не сохраняется в историю чата.")
        }, isDemoMessage = true)
        delay(3.seconds)

        // ═══════════════════════════════════════════
        // STEP 2: Load docs from GitHub (IO)
        // ═══════════════════════════════════════════
        addMessage("assistant", buildString {
            appendLine("📄 **Шаг 1/4: Загрузка документации (RAG)**")
            appendLine()
            appendLine("Загружаю документы из GitHub...")
        }, isDemoMessage = true)

        documents = withContext(Dispatchers.IO) {
            ProjectDocuments.getAll()
        }

        val totalChars = documents.sumOf { it.content.length }
        val totalSections = documents.sumOf { it.sections.size }

        addMessage("assistant", buildString {
            appendLine("📄 **Шаг 1/4: Загрузка документации (RAG)**")
            appendLine()
            if (documents.isEmpty()) {
                appendLine("⚠️ Не удалось загрузить документы из GitHub.")
                appendLine("Проверьте токен и подключение к интернету.")
            } else {
                appendLine("Загружено **${documents.size}** документов из GitHub:")
                documents.forEachIndexed { i, doc ->
                    appendLine("  ${i + 1}. **${doc.title}** — ${doc.content.length} символов, ${doc.sections.size} разделов")
                }
                appendLine()
                appendLine("**Итого:** $totalChars символов, $totalSections разделов")
            }
        }, isDemoMessage = true)
        delay(2.seconds)

        if (documents.isEmpty()) {
            addMessage("assistant", buildString {
                appendLine("❌ **Демо прервано:** не удалось загрузить документы из GitHub.")
                appendLine()
                appendLine("Возможные причины:")
                appendLine("  • Неверный или истёкший токен GitHub")
                appendLine("  • Нет подключения к интернету")
                appendLine("  • Репозиторий недоступен")
                appendLine()
                appendLine("Попробуйте запустить демо снова после проверки токена.")
            }, isDemoMessage = true)
            return
        }

        // ═══════════════════════════════════════════
        // STEP 3: Indexing
        // ═══════════════════════════════════════════
        val strategy = ChunkingStrategy.Structural()
        val chunks = documents.flatMap { doc ->
            ChunkerFactory.create(strategy).chunk(doc, strategy)
        }

        addMessage("assistant", buildString {
            appendLine("✂️ **Шаг 2/4: Индексация**")
            appendLine()
            appendLine("Стратегия: **Structural** → **${chunks.size}** чанков")
            documents.forEach { doc ->
                val n = chunks.filter { it.documentId == doc.id }.size
                appendLine("  • ${doc.title} → $n чанков")
            }
        }, isDemoMessage = true)
        delay(2.seconds)

        // ═══════════════════════════════════════════
        // STEP 4: MCP tools demo
        // ═══════════════════════════════════════════
        addMessage("assistant", buildString {
            appendLine("🔧 **Шаг 3/4: MCP Tools — подключение к проекту**")
            appendLine()
            appendLine("Доступные инструменты:")
            appendLine("  • `github_read_file` — чтение файлов из GitHub")
            appendLine("  • `github_list_dir` — просмотр директорий")
            appendLine("  • `github_search_source` — поиск по исходному коду")
            appendLine("  • `github_list_docs` — список документации")
            appendLine("  • `github_list_source_tree` — дерево исходного кода")
        }, isDemoMessage = true)
        delay(2.seconds)

        // Real MCP call — list docs
        addMessage("assistant", "📡 Демонстрирую MCP tool: `github_list_docs`...", isDemoMessage = true)
        delay(500.milliseconds)
        val mcpIntegration = chatSession.mcpIntegration
        if (mcpIntegration != null) {
            try {
                val docsResult = mcpIntegration.executeToolCall(
                    """{"tool": "github_list_docs", "arguments": {}}"""
                )
                addMessage("assistant", buildString {
                    appendLine("✅ **Результат `github_list_docs`:**")
                    appendLine()
                    appendLine(docsResult)
                }, isDemoMessage = true)
                delay(2.seconds)
            } catch (e: Exception) {
                addMessage("assistant", "⚠️ MCP tool недоступен: ${e.message}", isDemoMessage = true)
                delay(1.seconds)
            }
        }

        // Real MCP call — list source tree
        addMessage("assistant", "📡 Демонстрирую MCP tool: `github_list_source_tree`...", isDemoMessage = true)
        delay(500.milliseconds)
        if (mcpIntegration != null) {
            try {
                val treeResult = mcpIntegration.executeToolCall(
                    """{"tool": "github_list_source_tree", "arguments": {"path": "domain"}}"""
                )
                addMessage("assistant", buildString {
                    appendLine("✅ **Результат `github_list_source_tree`:**")
                    appendLine()
                    appendLine(treeResult.take(2000))
                    if (treeResult.length > 2000) appendLine("\n// [truncated]")
                }, isDemoMessage = true)
                delay(2.seconds)
            } catch (e: Exception) {
                addMessage("assistant", "⚠️ MCP tool недоступен: ${e.message}", isDemoMessage = true)
                delay(1.seconds)
            }
        }

        // ═══════════════════════════════════════════
        // STEP 5: Real Q&A with RAG + MCP
        // ═══════════════════════════════════════════
        addMessage("assistant", buildString {
            appendLine("❓ **Шаг 4/4: Ответы на вопросы (RAG + MCP → LLM)**")
            appendLine()
            appendLine("Каждый вопрос обрабатывается: RAG ищет релевантные чанки,")
            appendLine("MCP tools читают исходный код, LLM генерирует ответ.")
        }, isDemoMessage = true)
        delay(2.seconds)

        val savedRagEnabled = chatSession.ragEnabled
        chatSession.ragEnabled = true
        val records = mutableListOf<QARecord>()

        try {
            for ((index, question) in questions.withIndex()) {
                addMessage("user", question, isDemoMessage = true)
                delay(1.seconds)

                addMessage("assistant", buildString {
                    appendLine("**${index + 1}/${questions.size}:** $question")
                    appendLine()
                    appendLine("🔍 RAG + MCP tools → mistral-large-latest...")
                }, isDemoMessage = true)
                delay(500.milliseconds)

                val response = withContext(Dispatchers.IO) {
                    chatSession.ask(question, saveToHistory = false)
                }

                val sourceNames = response.ragSources?.map { it.title }?.distinct()?.joinToString(", ") ?: ""

                records.add(
                    QARecord(
                        question = question,
                        answer = stripServiceTags(response.content),
                        sources = sourceNames,
                        ragChunks = response.ragSources?.size ?: 0,
                        topScore = response.ragSources?.maxOfOrNull { it.score } ?: 0f,
                        promptTokens = response.promptTokens,
                        completionTokens = response.completionTokens,
                        responseTimeMs = response.responseTimeMs,
                    )
                )

                addMessage("assistant", buildString {
                    appendLine("**${index + 1}/${questions.size}:** $question")
                    appendLine()
                    if (sourceNames.isNotEmpty()) appendLine("📎 *Источники RAG: $sourceNames*")
                    appendLine()
                    appendLine(stripServiceTags(response.content))
                    appendLine()
                    appendLine("---")
                    val tok = mutableListOf<String>()
                    response.promptTokens?.let { tok.add("$it prompt") }
                    response.completionTokens?.let { tok.add("$it completion") }
                    response.totalTokens?.let { tok.add("=$it total") }
                    if (tok.isNotEmpty()) appendLine("📊 *Токены: ${tok.joinToString(" + ")}*")
                    appendLine("⏱️ *${response.responseTimeMs}мс*")
                }, isDemoMessage = true)
                delay(4.seconds)
            }
        } finally {
            chatSession.ragEnabled = savedRagEnabled
        }

        // ═══════════════════════════════════════════
        // SUMMARY — LLM-generated evaluation
        // ═══════════════════════════════════════════
        val totalPrompt = records.sumOf { it.promptTokens ?: 0 }
        val totalCompletion = records.sumOf { it.completionTokens ?: 0 }
        val totalTime = records.sumOf { it.responseTimeMs }
        val avgTime = if (records.isNotEmpty()) totalTime / records.size else 0

        addMessage("assistant", "📝 **Итоги демонстрации** — анализирую результаты...", isDemoMessage = true)
        delay(500.milliseconds)

        val qaText = records.withIndex().joinToString("\n\n") { (i, r) ->
            val scoreStr = if (r.topScore > 0) "RAG score=${"%.3f".format(r.topScore)}" else "без RAG"
            buildString {
                appendLine("=== Вопрос ${i + 1} ===")
                appendLine(r.question)
                appendLine()
                appendLine("=== Ответ ${i + 1} ===")
                appendLine(r.answer.take(600))
                appendLine()
                appendLine("Источники RAG: ${r.sources.ifEmpty { "нет" }}")
                appendLine("Чанков в контексте: ${r.ragChunks}")
                appendLine("Оценка поиска: $scoreStr")
                appendLine("Токены: ${r.promptTokens ?: "?"} prompt + ${r.completionTokens ?: "?"} completion")
                appendLine("Время ответа: ${r.responseTimeMs}мс")
            }
        }

        val evalPrompt = """
            Подведи итоги демо AI-ассистента для разработчика.

            КОНФИГУРАЦИЯ:
            - Модель: mistral/mistral-large-latest
            - RAG: ${documents.size} документов → ${chunks.size} чанков (Structural), эмбеддинги granite-embedding-97m
            - MCP: github_read_file, github_list_dir, github_search_source, github_list_docs, github_list_source_tree

            РЕЗУЛЬТАТЫ ПО ВОПРОСАМ:
            $qaText

            ОБЩИЕ МЕТРИКИ:
            - Вопросов: ${records.size}
            - Токенов: $totalPrompt prompt + $totalCompletion completion (всего ${totalPrompt + totalCompletion})
            - Среднее время: ${avgTime}мс | Мин: ${records.minOfOrNull { it.responseTimeMs } ?: 0}мс | Макс: ${records.maxOfOrNull { it.responseTimeMs } ?: 0}мс
            - Общее время: ${totalTime / 1000}с

            ФОРМАТ ОТЧЁТА:

            ### Оценка по вопросам
            Для каждого вопроса: оценка 1-10, что хорошо, что не хватает.

            ### Метрики пайплайна
            По каждому критерию (1-10) с обоснованием:
            - RAG: точность поиска, релевантность чанков, покрытие тем
            - LLM: полнота, точность, структурированность, язык
            - Скорость: анализ времени по вопросам, bottleneck
            - Стоимость: токены на вопрос, разумность, projection на 100 вопросов

            ### Рекомендации
            Конкретные шаги для улучшения (макс. 5).

            ### Общая оценка
            Итоговый балл 1-10 с одной фразой-резюме.
        """.trimIndent()

        val evalResponse = withContext(Dispatchers.IO) {
            chatSession.ask(evalPrompt, saveToHistory = false)
        }

        addMessage("assistant", buildString {
            appendLine(stripServiceTags(evalResponse.content))
            appendLine()
            appendLine("---")
            appendLine("📊 *Итоговый анализ сгенерирован LLM*")
            evalResponse.promptTokens?.let { p ->
                evalResponse.completionTokens?.let { c ->
                    appendLine("📈 *Токены анализа: $p prompt + $c completion*")
                }
            }
            appendLine("⏱️ *${evalResponse.responseTimeMs}мс*")
        }, isDemoMessage = true)
    }
}
