package com.llmapp.codeguardian

import com.llmapp.api.ClientFactory
import com.llmapp.api.RouterClient
import com.llmapp.model.ChatMessage
import com.llmapp.model.RouterRequest

/**
 * AI-агент для локального code review KMP-проектов.
 *
 * Этапы:
 * 1) Сбор контекста: git diff + полные файлы + RAG
 * 2) Первый проход: LLM анализирует и может запрашивать файлы через тулзы
 * 3) Верификация: выполняем тулзы, LLM проверяет свои утверждения
 * 4) Финальный отчёт
 */
class CodeGuardianAgent(
    projectPath: String,
    private val onProgress: ((String) -> Unit)? = null,
) {
    private val projectPath: String = java.io.File(projectPath).absolutePath
    private val git = GitWrapper(this.projectPath)
    private val tools = com.llmapp.assistant.LocalFileTools(this.projectPath)
    private val index = com.llmapp.assistant.ProjectIndex(this.projectPath)
    private val apiClient: RouterClient = ClientFactory.create()

    private val toolSchemas = """
        ТЫ — AI-ревьюер KMP-проекта. У тебя есть тулзы для проверки кода.

        ╔══════════════════════════════════════════════════════════════════╗
        ║  ТУЛЗЫ (используй ДО выдачи финального отчёта)                ║
        ╠══════════════════════════════════════════════════════════════════╣
        ║  read_file — прочитать файл целиком.                           ║
        ║  Формат: [TOOL_CALL] op=read_file path="src/Main.kt"         ║
        ║                                                                ║
        ║  search — поиск по содержимому файлов проекта.                 ║
        ║  Формат: [TOOL_CALL] op=search query="BaseViewModel"          ║
        ║                                                                ║
        ║  ИСПОЛЬЗУЙ тулзы когда:                                       ║
        ║  - Видишь упоминание класса/функции и хочешь проверить        ║
        ║    существует ли он/она и как используется                    ║
        ║  - Сомневаешься в наследовании или имплементации               ║
        ║  - Хочешь проверить полный контекст вокруг изменения           ║
        ║  - Нужно убедиться что метод действительно вызывается          ║
        ╚══════════════════════════════════════════════════════════════════╝

        ╔══════════════════════════════════════════════════════════════════╗
        ║  ПРАВИЛА                                                       ║
        ╠══════════════════════════════════════════════════════════════════╣
        ║  1. Сначала проанализируй diff и файлы.                        ║
        ║  2. Если сомневаешься — вызови тулз и проверь.                 ║
        ║  3. НЕ ПИШИ утверждения о коде который не видел.               ║
        ║  4. Каждая проблема должна иметь конкретный файл:строка.       ║
        ║  5. Когда готов — выдай финальный отчёт БЕЗ тулзов.           ║
        ╚══════════════════════════════════════════════════════════════════╝
    """.trimIndent()

    /**
     * Выполнить полный code review с верификацией.
     */
    suspend fun review(): ReviewReport {
        onProgress?.invoke("Проверяю git-репозиторий...")

        if (!git.isGitRepo()) {
            return ReviewReport(
                summary = "Проект не является git-репозиторием.",
                score = 0, sections = emptyList(),
                stats = ReviewStats(0, 0, 0, 0, 0)
            )
        }

        val branch = git.getBranch()
        val changedFiles = git.getChangedFiles()
        val untrackedFiles = git.getUntrackedFiles()
        val diff = git.getDiffAll()
        val diffStat = git.getDiffStat()

        onProgress?.invoke("Найдено ${changedFiles.size} изменённых файлов")

        if (changedFiles.isEmpty() && untrackedFiles.isEmpty()) {
            return ReviewReport(
                summary = "Нет изменений для review. Ветка: $branch",
                score = 10,
                sections = listOf(
                    ReviewSection(
                        "Статус",
                        ReviewStatus.OK,
                        listOf("Нет изменений")
                    )
                ),
                stats = ReviewStats(0, 0, 0, 0, 0)
            )
        }

        // === ШАГ 1: Сбор контекста ===
        onProgress?.invoke("Читаю изменённые файлы...")
        val fileContexts = mutableMapOf<String, String>()
        for (filePath in changedFiles) {
            try {
                val content = tools.readFile(filePath)
                fileContexts[filePath] = content
            } catch (_: Exception) {
            }
        }

        onProgress?.invoke("Ищу релевантный контекст через RAG...")
        val relevantChunks = index.search(
            query = "KMP architecture ${changedFiles.joinToString(" ")}",
            maxResults = 10
        )
        val ragContext = relevantChunks.joinToString("\n") { it.chunk.content }.take(5000)

        val addedFiles = git.getAddedFiles()
        val deletedFiles = git.getDeletedFiles()
        val modifiedFiles = git.getModifiedFiles()

        // === ШАГ 2: Первый проход LLM (с тулзами) ===
        onProgress?.invoke("Анализирую код (этап 1/2)...")
        val initialPrompt = buildReviewPrompt(
            branch, changedFiles, addedFiles, deletedFiles, modifiedFiles,
            diff, diffStat, fileContexts, ragContext
        )

        val allToolResults = mutableListOf<String>()
        var currentPrompt = "$toolSchemas\n\n$initialPrompt"
        val allLlmResponses = mutableListOf<String>()

        // Петля: LLM вызывает тулзы, мы выполняем, отдаём результат обратно
        repeat(5) { _ ->
            val llmResponse = callLlm(currentPrompt)
            allLlmResponses.add(llmResponse)

            val toolCalls = extractToolCalls(llmResponse)
            if (toolCalls.isEmpty()) {
                // LLM закончил — выдаёт финальный ответ
                onProgress?.invoke("Анализ завершён (этап 2/2)...")
                val finalReport =
                    parseReviewReport(llmResponse, branch, changedFiles.size, diff.length)
                onProgress?.invoke("Review завершён. Оценка: ${finalReport.score}/10")
                return finalReport
            }

            // Выполняем тулзы и собираем результаты
            for (call in toolCalls) {
                val result = executeToolCall(call)
                allToolResults.add("[${call.op}] ${call.args}: $result")
                onProgress?.invoke("Проверяю: ${call.args["path"] ?: call.args["query"] ?: call.op}...")
            }

            currentPrompt = buildVerificationPrompt(
                initialPrompt, allLlmResponses.last(), allToolResults
            )
        }

        // Fallback: если за 5 итераций не выдал финальный ответ
        val lastResponse = allLlmResponses.lastOrNull() ?: ""
        val report = parseReviewReport(lastResponse, branch, changedFiles.size, diff.length)
        onProgress?.invoke("Review завершён. Оценка: ${report.score}/10")
        return report
    }

    // ═══════════════════════════════════════════
    // Тулзы
    // ═══════════════════════════════════════════

    private data class ToolCall(val op: String, val args: Map<String, String>)

    private fun extractToolCalls(text: String): List<ToolCall> {
        val pattern = Regex(
            """\[TOOL_CALL]\s*op=(\w+)\s*(.*)""",
            RegexOption.DOT_MATCHES_ALL
        )
        return pattern.findAll(text).map { match ->
            val op = match.groupValues[1]
            val argsStr = match.groupValues[2]
            val args = parseToolArgs(argsStr)
            ToolCall(op, args)
        }.toList()
    }

    private fun parseToolArgs(argsStr: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val pattern = Regex("""(\w+)="([^"]*)"""")
        for (match in pattern.findAll(argsStr)) {
            result[match.groupValues[1]] = match.groupValues[2]
        }
        return result
    }

    private fun executeToolCall(call: ToolCall): String {
        return when (call.op) {
            "read_file" -> {
                val path = call.args["path"] ?: return "Ошибка: не указан path"
                try {
                    val content = tools.readFile(path)
                    "=== $path ===\n$content"
                } catch (_: Exception) {
                    "Файл не найден: $path"
                }
            }

            "search" -> {
                val query = call.args["query"] ?: return "Ошибка: не указан query"
                try {
                    val results = tools.searchContent(query)
                    if (results.isEmpty()) "Ничего не найдено по запросу: $query"
                    else results.joinToString("\n") { "${it.filePath}:${it.lineNumber}: ${it.lineContent}" }
                } catch (_: Exception) {
                    "Ошибка поиска: $query"
                }
            }

            else -> "Неизвестный тулз: ${call.op}"
        }
    }

    // ═══════════════════════════════════════════
    // Промпты
    // ═══════════════════════════════════════════

    private fun buildReviewPrompt(
        branch: String,
        changedFiles: List<String>,
        addedFiles: List<String>,
        deletedFiles: List<String>,
        modifiedFiles: List<String>,
        diff: String,
        diffStat: String,
        fileContexts: Map<String, String>,
        ragContext: String
    ): String = buildString {
        appendLine("═══════════════════════════════════════════════════════════")
        appendLine("ЗАДАЧА: Проведи code review изменений в git.")
        appendLine("═══════════════════════════════════════════════════════════")
        appendLine()
        appendLine("ВЕТКА: $branch")
        appendLine("ИЗМЕНЁННЫХ ФАЙЛОВ: ${changedFiles.size}")
        appendLine("ДОБАВЛЕНО: ${addedFiles.size}, УДАЛЕНО: ${deletedFiles.size}, ИЗМЕНЕНО: ${modifiedFiles.size}")
        appendLine()
        appendLine("── Diff Stat ──")
        appendLine(diffStat)
        appendLine()

        appendLine("── Изменённые файлы ──")
        changedFiles.forEach { appendLine("- $it") }
        appendLine()

        if (fileContexts.isNotEmpty()) {
            appendLine("── Полное содержимое изменённых файлов ──")
            fileContexts.forEach { (path, content) ->
                appendLine("=== $path ===")
                appendLine(content)
                appendLine()
            }
        }

        if (ragContext.isNotBlank()) {
            appendLine("── RAG-контекст проекта (связанные файлы) ──")
            appendLine(ragContext)
            appendLine()
        }

        appendLine("── Diff ──")
        appendLine(diff.take(20000))
        appendLine()

        appendLine("═══════════════════════════════════════════════════════════")
        appendLine("ИНСТРУКЦИЯ:")
        appendLine("═══════════════════════════════════════════════════════════")
        appendLine()
        appendLine("1. Изучи diff и полные содержимое файлов выше.")
        appendLine("2. Если видишь упоминание класса/метода и сомневаешься —")
        appendLine("   используй [TOOL_CALL] чтобы прочитать файл или найти usage.")
        appendLine("3. НЕ ПИШИ проблему если ты не видел код лично.")
        appendLine("4. Когда достаточно информации — выдай финальный отчёт.")
        appendLine()
        appendLine("ФОРМАТ ОТВЕТА (строго markdown):")
        appendLine()
        appendLine("## ОБЩАЯ ОЦЕНКА: X/10")
        appendLine("## КРАТКОЕ САММАРИ")
        appendLine("## ПРОВЕРКИ")
        appendLine("### Архитектура (Clean Architecture + MVI)")
        appendLine("### Именование")
        appendLine("### Код-качество")
        appendLine("### Безопасность")
        appendLine("## ПРОБЛЕМЫ (список)")
        appendLine("## РЕКОМЕНДАЦИИ")
        appendLine("## ХВАЛА")
    }

    private fun buildVerificationPrompt(
        initialPrompt: String,
        lastLlmResponse: String,
        toolResults: List<String>
    ): String = buildString {
        appendLine(toolSchemas)
        appendLine()
        appendLine(initialPrompt)
        appendLine()
        appendLine("═══ ТВОЙ ПРОШЛЫЙ ОТВЕТ ═══")
        appendLine(lastLlmResponse.take(3000))
        appendLine()
        appendLine("═══ РЕЗУЛЬТАТЫ ТУЛЗОВ ═══")
        toolResults.forEach { appendLine(it) }
        appendLine()
        appendLine("═══ ПРОДОЛЖИ ═══")
        appendLine("Проанализируй результаты тулзов.")
        appendLine("Если достаточно информации — выдай финальный отчёт.")
        appendLine("Если нужны ещё проверки — вызови тулзы.")
    }

    // ═══════════════════════════════════════════
    // LLM + парсинг
    // ═══════════════════════════════════════════

    private suspend fun callLlm(prompt: String): String {
        return try {
            val request = RouterRequest(
                model = "mistral/mistral-large-latest",
                messages = listOf(ChatMessage(role = "user", content = prompt)),
                temperature = 0.0,
                maxTokens = 8192,
            )
            val response = apiClient.sendRequest(request)
            if (response.error != null) {
                "Ошибка LLM: ${response.error.message}"
            } else {
                response.choices?.firstOrNull()?.message?.content ?: ""
            }
        } catch (e: Exception) {
            "Ошибка при обращении к LLM: ${e.message}"
        }
    }

    private fun parseReviewReport(
        text: String,
        branch: String,
        fileCount: Int,
        diffLength: Int
    ): ReviewReport {
        val score = Regex("""ОБЩАЯ ОЦЕНКА:\s*(\d+)""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 5
        val sections = extractSections(text)
        val summary = extractSummary(text)

        return ReviewReport(
            summary = summary,
            score = score,
            sections = sections,
            stats = ReviewStats(
                filesReviewed = fileCount,
                issuesFound = sections.sumOf { s ->
                    s.items.count {
                        it.contains("🔴") || it.contains(
                            "🟡"
                        )
                    }
                },
                criticalIssues = sections.sumOf { s -> s.items.count { it.contains("🔴") } },
                warnings = sections.sumOf { s -> s.items.count { it.contains("🟡") } },
                diffSize = diffLength
            ),
            branch = branch,
            rawResponse = text
        )
    }

    private fun extractSummary(text: String): String {
        val marker = "КРАТКОЕ САММАРИ"
        val start = text.indexOf(marker)
        if (start == -1) return text.take(500)
        val afterMarker = text.substring(start + marker.length).trim()
        val end = afterMarker.indexOf("\n##")
        return if (end > 0) afterMarker.substring(0, end).trim() else afterMarker.take(500).trim()
    }

    private fun extractSections(text: String): List<ReviewSection> {
        val sections = mutableListOf<ReviewSection>()
        val sectionPattern = Regex("""###\s+(.+)""")
        val lines = text.lines()
        var currentTitle: String? = null
        val currentItems = mutableListOf<String>()

        for (line in lines) {
            val match = sectionPattern.find(line)
            if (match != null) {
                if (currentTitle != null && currentItems.isNotEmpty()) {
                    sections.add(
                        ReviewSection(
                            currentTitle,
                            determineStatus(currentItems),
                            currentItems.toList()
                        )
                    )
                }
                currentTitle = match.groupValues[1].trim()
                currentItems.clear()
            } else if (currentTitle != null && line.isNotBlank() && line.startsWith("- ")) {
                currentItems.add(line.removePrefix("- ").trim())
            }
        }
        if (currentTitle != null && currentItems.isNotEmpty()) {
            sections.add(
                ReviewSection(
                    currentTitle,
                    determineStatus(currentItems),
                    currentItems.toList()
                )
            )
        }
        return sections
    }

    private fun determineStatus(items: List<String>): ReviewStatus = when {
        items.any { it.contains("🔴") } -> ReviewStatus.ERROR
        items.any { it.contains("🟡") } -> ReviewStatus.WARNING
        else -> ReviewStatus.OK
    }
}

data class ReviewReport(
    val summary: String,
    val score: Int,
    val sections: List<ReviewSection>,
    val stats: ReviewStats,
    val branch: String = "",
    val rawResponse: String = ""
)

data class ReviewSection(
    val title: String,
    val status: ReviewStatus,
    val items: List<String>
)

enum class ReviewStatus { OK, WARNING, ERROR }

data class ReviewStats(
    val filesReviewed: Int,
    val issuesFound: Int,
    val criticalIssues: Int,
    val warnings: Int,
    val diffSize: Int
)
