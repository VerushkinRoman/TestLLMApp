package com.llmapp.pr_review

import com.llmapp.api.ClientFactory
import com.llmapp.api.RouterClient
import com.llmapp.model.ChatMessage
import com.llmapp.model.RouterRequest
import com.llmapp.rag.RAGEnhancer

class PRReviewAgent(
    private val config: ReviewConfig = ReviewConfig(),
    private val apiClient: RouterClient = ClientFactory.create(),
    private val ragEnhancer: RAGEnhancer = RAGEnhancer(),
) {
    suspend fun review(prNumber: Int): ReviewReport {
        val diff = fetchPrDiff(prNumber)
        return reviewDiff(diff)
    }

    suspend fun reviewFromDiff(prDiff: PullRequestDiff): ReviewReport {
        return reviewDiff(prDiff)
    }

    private suspend fun fetchPrDiff(prNumber: Int): PullRequestDiff {
        val result = com.llmapp.rag.data.GitHubApi.getPullRequestDiff(prNumber)
            ?: throw Exception("Не удалось получить PR #$prNumber")

        val files = result.files.map { file ->
            ChangedFile(
                path = file.filename,
                status = file.status,
                additions = file.additions,
                deletions = file.deletions,
                patch = file.patch ?: "",
            )
        }

        val diffText = buildString {
            for (file in result.files) {
                appendLine("--- ${file.filename} (${file.status}, +${file.additions}/-${file.deletions}) ---")
                if (file.patch != null) {
                    appendLine(file.patch)
                }
                appendLine()
            }
        }

        return PullRequestDiff(
            pr = PullRequestInfo(
                number = prNumber,
                title = result.title,
                description = result.description,
                author = result.author,
                baseBranch = "",
                headBranch = "",
                state = "open",
                createdAt = "",
                updatedAt = "",
            ),
            files = files,
            diffText = diffText,
            totalAdditions = result.totalAdditions,
            totalDeletions = result.totalDeletions,
            totalChanges = result.totalChanges,
        )
    }

    private suspend fun reviewDiff(prDiff: PullRequestDiff): ReviewReport {
        val systemPrompt = buildReviewSystemPrompt(prDiff)
        val ragContext = buildRagContext(prDiff)
        val userPrompt = buildReviewPrompt(prDiff, ragContext)

        val request = RouterRequest(
            model = config.model,
            messages = listOf(
                ChatMessage("system", systemPrompt),
                ChatMessage("user", userPrompt),
            ),
            maxTokens = 8192,
            temperature = 0.2,
        )

        val startTime = System.currentTimeMillis()
        val response = apiClient.sendRequest(request)
        response.error?.let { throw Exception(it.message) }
        val content = response.choices?.firstOrNull()?.message?.content
            ?: throw Exception("Empty response")
        val elapsed = System.currentTimeMillis() - startTime

        return parseReviewResponse(content, prDiff, elapsed)
    }

    private suspend fun buildRagContext(prDiff: PullRequestDiff): String {
        return try {
            ragEnhancer.ensureIndexLoaded()
            val changedTopics = extractTopicsFromDiff(prDiff)
            val contexts = changedTopics.take(3).map { topic ->
                try {
                    val result = ragEnhancer.searchWithStructuredContext(topic)
                    result.answer
                } catch (_: Exception) { null }
            }.filterNotNull()

            if (contexts.isEmpty()) ""
            else contexts.joinToString("\n\n") { it }
        } catch (_: Exception) { "" }
    }

    private fun extractTopicsFromDiff(prDiff: PullRequestDiff): List<String> {
        val topics = mutableListOf<String>()
        for (file in prDiff.files) {
            val path = file.path.lowercase()
            when {
                "repository" in path -> topics.add("Repository pattern")
                "viewmodel" in path -> topics.add("ViewModel pattern")
                "usecase" in path -> topics.add("UseCase pattern")
                "datasource" in path -> topics.add("DataSource pattern")
                "domain" in path -> topics.add("Domain layer")
                "presentation" in path -> topics.add("Presentation layer")
                "data" in path -> topics.add("Data layer")
                "di" in path -> topics.add("Dependency injection")
                else -> topics.add("Architecture overview")
            }
        }
        return topics.distinct().ifEmpty { listOf("Code review best practices") }
    }

    private fun buildReviewSystemPrompt(prDiff: PullRequestDiff): String = """
Ты — строгий Code Reviewer для Kotlin Multiplatform проектов с Clean Architecture + MVI.

Твоя задача: найти проблемы в Pull Request. Будь придирчивым, но конструктивным.

ПРАВИЛА РЕВЬЮ:
1. Ищи РЕАЛЬНЫЕ проблемы, не выдумывай
2. Оценивай архитектуру, логику, безопасность, стиль
3. Используй контекст из базы знаний (архитектура проекта, паттерны)
4. Каждую проблему оценивай по severity и category
5. Предлагай конкретные исправления

ФОРМАТ ОТВЕТА — строго Markdown с секциями:

## Общая оценка
[оценка от 1 до 100]

## Позитивные моменты
- [что сделано хорошо]

## Найденные проблемы

### [severity] [category]: краткий заголовок
- **Файл:** [path]
- **Строка:** [line если известна]
- **Описание:** [подробно]
- **Рекомендация:** [как исправить]

...

## Архитектурный анализ
[анализ соответствия архитектуре проекта]

## Рекомендации
- [конкретный совет 1]
- [конкретный совет 2]

Severity: CRITICAL | WARNING | INFO | SUGGESTION
Category: BUG | ARCHITECTURE | PERFORMANCE | SECURITY | CODE_STYLE | TEST_COVERAGE | BEST_PRACTICE | POTENTIAL_ISSUE

Архитектура проекта: Clean Architecture, слои Presentation/Domain/Data, MVI (State/Event/Action), Result<D, E> для ошибок, BaseSharedViewModel, Kodein DI, Repository интерфейсы в domain, реализация в data.
""".trimIndent()

    private fun buildReviewPrompt(prDiff: PullRequestDiff, ragContext: String): String = buildString {
        appendLine("## Pull Request: ${prDiff.pr.title}")
        appendLine()
        appendLine("**Описание:** ${prDiff.pr.description.take(1000)}")
        appendLine("**Автор:** ${prDiff.pr.author}")
        appendLine("**Изменения:** +${prDiff.totalAdditions}/-${prDiff.totalDeletions} в ${prDiff.files.size} файлах")
        appendLine()

        if (prDiff.pr.description.isNotBlank()) {
            appendLine("### Описание PR")
            appendLine(prDiff.pr.description.take(2000))
            appendLine()
        }

        appendLine("### DIFF (изменённые файлы)")

        val maxTotalChars = 25000
        var totalChars = 0
        for (file in prDiff.files) {
            if (totalChars >= maxTotalChars) {
                appendLine("... [остальные файлы не показаны из-за лимита]")
                break
            }
            val maxFileChars = 8000
            appendLine("--- ${file.path} (${file.status}, +${file.additions}/-${file.deletions}) ---")
            val patchToShow = file.patch
            val filePortion = if (patchToShow.length > maxFileChars) {
                totalChars += maxFileChars
                patchToShow.take(maxFileChars) + "\n// [truncated, full: ${patchToShow.length} chars]"
            } else {
                totalChars += patchToShow.length
                patchToShow
            }
            appendLine(filePortion)
            appendLine()
        }

        if (ragContext.isNotBlank()) {
            appendLine()
            appendLine("### Контекст из базы знаний проекта")
            appendLine(ragContext.take(8000))
        }
    }

    private fun parseReviewResponse(
        content: String,
        prDiff: PullRequestDiff,
        responseTimeMs: Long,
    ): ReviewReport {
        val issues = mutableListOf<ReviewIssue>()

        val severityPattern = Regex(
            """###\s*(CRITICAL|WARNING|INFO|SUGGESTION)\s*\[(\w+)]\s*:\s*(.+?)(?=\n-|\n###|\Z)""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE),
        )
        for (match in severityPattern.findAll(content)) {
            val severity = try {
                ReviewSeverity.valueOf(match.groupValues[1])
            } catch (_: Exception) { null }
            val category = try {
                ReviewCategory.valueOf(match.groupValues[2])
            } catch (_: Exception) { null }
            val title = match.groupValues[3].trim()

            if (severity != null && title.length < 200) {
                val fileMatch = Regex("""\*\*Файл:\*\*\s*(.+?)(?:\n|$)""").find(match.value)
                val lineMatch = Regex("""\*\*Строка:\*\*\s*(\d+)""").find(match.value)
                val descMatch = Regex("""\*\*Описание:\*\*\s*(.+?)(?:\n\*\*|\n-|\n###|\Z)""", setOf(RegexOption.DOT_MATCHES_ALL)).find(match.value)

                issues.add(ReviewIssue(
                    severity = severity,
                    category = category ?: ReviewCategory.BEST_PRACTICE,
                    title = title,
                    description = descMatch?.groupValues?.get(1)?.trim() ?: "",
                    filePath = fileMatch?.groupValues?.get(1)?.trim(),
                    lineNumber = lineMatch?.groupValues?.get(1)?.toIntOrNull(),
                ))
            }
        }

        val summary = Regex("""## Общая оценка\s*\n\s*(\d+)""").find(content)
            ?.let { "Общая оценка: ${it.groupValues[1]}/100" } ?: "Ревью завершено"

        val score = Regex("""## Общая оценка\s*\n\s*(\d+)""").find(content)
            ?.groupValues?.get(1)?.toIntOrNull() ?: 50

        val highlights = mutableListOf<String>()
        val positiveSection = Regex("""## Позитивные моменты\s*\n(.*?)(?=\n## |\Z)""", setOf(RegexOption.DOT_MATCHES_ALL)).find(content)
        positiveSection?.let { section ->
            for (line in section.groupValues[1].lines()) {
                val trimmed = line.trimStart('-', ' ', '*').trim()
                if (trimmed.length > 5 && trimmed.length < 200) {
                    highlights.add(trimmed)
                }
            }
        }

        val recommendations = mutableListOf<String>()
        val recSection = Regex("""## Рекомендации\s*\n(.*?)(?=\n## |\Z)""", setOf(RegexOption.DOT_MATCHES_ALL)).find(content)
        recSection?.let { section ->
            for (line in section.groupValues[1].lines()) {
                val trimmed = line.trimStart('-', ' ', '*').trim()
                if (trimmed.length > 5 && trimmed.length < 300) {
                    recommendations.add(trimmed)
                }
            }
        }

        return ReviewReport(
            prInfo = prDiff.pr,
            issues = issues,
            summary = summary,
            positiveHighlights = highlights,
            recommendations = recommendations,
            overallScore = score.coerceIn(0, 100),
        )
    }
}
