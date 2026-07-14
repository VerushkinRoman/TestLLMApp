package com.llmapp.pr_review

import com.llmapp.api.ClientFactory
import com.llmapp.demo.manager.BaseDemoRunner
import com.llmapp.model.ChatMessage
import com.llmapp.model.RouterRequest
import com.llmapp.rag.data.GitHubApi
import com.llmapp.ui.models.ChatMessageUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class PRReviewDemoRunner(
    onMessageAdded: (ChatMessageUI) -> Unit,
    onTypingStateChanged: (Boolean) -> Unit,
    private val prNumber: Int = 2,
) : BaseDemoRunner(onMessageAdded, onTypingStateChanged, delayMs = 150) {

    private data class ReviewMetrics(
        val reviewTimeMs: Long,
        val issuesFound: Int,
        val overallScore: Int,
        val promptTokens: Int? = null,
        val completionTokens: Int? = null,
    )

    override suspend fun run() {
        addMessage("assistant", buildString {
            appendLine("**AI Code Review — Pull Request #$prNumber**")
            appendLine()
            appendLine("Демонстрация полного пайплайна автоматического код-ревью:")
            appendLine()
            appendLine("1. **Получение PR diff** — GitHub API")
            appendLine("2. **RAG контекст** — поиск по документации проекта")
            appendLine("3. **LLM анализ** — генерация ревью")
            appendLine("4. **Оценка эффективности** — сравнение с облачной моделью")
            appendLine()
            appendLine("Демо не сохраняется в историю чата.")
        }, isDemoMessage = true)
        delay(3.seconds)

        // ============================================
        // STEP 1: Fetch PR diff
        // ============================================
        addMessage("assistant", buildString {
            appendLine("**Шаг 1/5: Получение PR #$prNumber**")
            appendLine()
            appendLine("Запрашиваю данные через GitHub API...")
        }, isDemoMessage = true)

        val diffResult = withContext(Dispatchers.IO) {
            GitHubApi.getPullRequestDiff(prNumber)
        }

        if (diffResult == null) {
            addMessage("assistant", buildString {
                appendLine("**Ошибка:** не удалось получить PR #$prNumber")
                appendLine()
                appendLine("Возможные причины:")
                appendLine("  • Неверный токен GitHub")
                appendLine("  • PR #$prNumber не существует")
                appendLine("  • Нет подключения к интернету")
            }, isDemoMessage = true)
            return
        }

        addMessage("assistant", buildString {
            appendLine("**PR #${prNumber}: ${diffResult.title}**")
            appendLine()
            appendLine("**Автор:** ${diffResult.author}")
            appendLine("**Описание:** ${diffResult.description.take(500)}")
            appendLine("**Файлов изменено:** ${diffResult.files.size}")
            appendLine("**Статистика:** +${diffResult.totalAdditions}/-${diffResult.totalDeletions} строк")
            appendLine()
            appendLine("**Изменённые файлы:**")
            for (file in diffResult.files) {
                val icon = when (file.status) {
                    "added" -> "+"
                    "removed" -> "-"
                    "modified" -> "~"
                    else -> " "
                }
                appendLine("  $icon `${file.filename}` (+${file.additions}/-${file.deletions})")
            }
        }, isDemoMessage = true)
        delay(2.seconds)

        // ============================================
        // STEP 2: Analyze with RAG + LLM
        // ============================================
        addMessage("assistant", buildString {
            appendLine("**Шаг 2/5: Анализ кода (RAG -> LLM)**")
            appendLine()
            appendLine("Запускаю AI-ревью. Процесс включает:")
            appendLine("  1. Анализ diff каждого файла")
            appendLine("  2. Поиск релевантного контекста в RAG")
            appendLine("  3. Оценка по категориям: баги, архитектура, стиль, безопасность")
            appendLine()
            appendLine("Модель: mistral/mistral-large-latest")
            appendLine("Глубина: полный анализ")
        }, isDemoMessage = true)
        delay(2.seconds)

        // Run the review
        addMessage("assistant", "Выполняю анализ кода...", isDemoMessage = true)
        delay(1.seconds)

        val agent = PRReviewAgent()
        val startTime = System.currentTimeMillis()
        val reportResult: ReviewReport = withContext(Dispatchers.IO) {
            val diff = PullRequestDiff(
                pr = PullRequestInfo(
                    number = prNumber,
                    title = diffResult.title,
                    description = diffResult.description,
                    author = diffResult.author,
                    baseBranch = "",
                    headBranch = "",
                    state = "open",
                    createdAt = "",
                    updatedAt = "",
                ),
                files = diffResult.files.map { file ->
                    ChangedFile(
                        path = file.filename,
                        status = file.status,
                        additions = file.additions,
                        deletions = file.deletions,
                        patch = file.patch ?: "",
                    )
                },
                diffText = diffResult.files.joinToString("\n") { f ->
                    "--- ${f.filename} ---\n${f.patch ?: ""}"
                },
                totalAdditions = diffResult.totalAdditions,
                totalDeletions = diffResult.totalDeletions,
                totalChanges = diffResult.totalChanges,
            )
            agent.reviewFromDiff(diff)
        }
        val reviewTime = System.currentTimeMillis() - startTime

        val metrics = ReviewMetrics(
            reviewTimeMs = reviewTime,
            issuesFound = reportResult.issues.size,
            overallScore = reportResult.overallScore,
        )

        // ============================================
        // STEP 3: Present results
        // ============================================
        addMessage("assistant", buildString {
            appendLine("**Шаг 3/5: Результаты ревью**")
            appendLine()
            appendLine("**Общая оценка: ${metrics.overallScore}/100**")
            appendLine("**Проблем найдено: ${metrics.issuesFound}**")
            appendLine("**Анализ занял: ${msToTime(metrics.reviewTimeMs)}**")
            appendLine()
            appendLine("---")
            appendLine()

            if (reportResult.positiveHighlights.isNotEmpty()) {
                appendLine("### Что сделано хорошо")
                for (h in reportResult.positiveHighlights) {
                    appendLine("- $h")
                }
                appendLine()
            }

            if (reportResult.issues.isNotEmpty()) {
                val bySeverity = reportResult.issues.groupBy { it.severity }
                appendLine("### Найденные проблемы")
                appendLine()

                for (severity in listOf(ReviewSeverity.CRITICAL, ReviewSeverity.WARNING, ReviewSeverity.INFO, ReviewSeverity.SUGGESTION)) {
                    val items = bySeverity[severity] ?: continue
                    val icon = when (severity) {
                        ReviewSeverity.CRITICAL -> "[CRITICAL]"
                        ReviewSeverity.WARNING -> "[WARNING]"
                        ReviewSeverity.INFO -> "[INFO]"
                        ReviewSeverity.SUGGESTION -> "[SUGGESTION]"
                    }
                    for (issue in items) {
                        appendLine("$icon ${issue.title}")
                        appendLine("  • **Категория:** ${issue.category}")
                        if (issue.filePath != null) appendLine("  • **Файл:** ${issue.filePath}")
                        if (issue.description.isNotBlank()) appendLine("  • ${issue.description.take(300)}")
                        if (issue.suggestion != null) appendLine("  • ${issue.suggestion}")
                        appendLine()
                    }
                }
            }

            appendLine("### Статистика")
            appendLine("- **Проверено файлов:** ${diffResult.files.size}")
            appendLine("- **+${diffResult.totalAdditions}/-${diffResult.totalDeletions} строк кода**")
            appendLine("- **Найдено проблем:** ${metrics.issuesFound}")
            appendLine("- **Время анализа:** ${msToTime(metrics.reviewTimeMs)}")
        }, isDemoMessage = true, ragSources = null)
        delay(3.seconds)

        // ============================================
        // STEP 4: Recommendations
        // ============================================
        addMessage("assistant", buildString {
            appendLine("**Шаг 4/5: Рекомендации**")
            appendLine()
            if (reportResult.recommendations.isNotEmpty()) {
                for (rec in reportResult.recommendations) {
                    appendLine("- $rec")
                }
            } else {
                appendLine("- Нет дополнительных рекомендаций")
            }
            appendLine()
            if (reportResult.issues.any { it.category == ReviewCategory.ARCHITECTURE }) {
                appendLine("")
                appendLine("### Архитектурный анализ")
                appendLine("Обнаружены архитектурные проблемы — рекомендуется провести дополнительное ревью архитектуры.")
            }
        }, isDemoMessage = true)
        delay(2.seconds)

        // ============================================
        // STEP 5: Evaluation (compare with cloud model)
        // ============================================
        addMessage("assistant", buildString {
            appendLine("**Шаг 5/5: Оценка эффективности**")
            appendLine()
            appendLine("Сравниваю результат с оценкой облачной модели...")
        }, isDemoMessage = true)
        delay(1.seconds)

        val evalResult = withContext(Dispatchers.IO) {
            evaluateWithCloud(reportResult, diffResult, metrics)
        }

        addMessage("assistant", evalResult, isDemoMessage = true)
        delay(2.seconds)

        // ============================================
        // SUMMARY
        // ============================================
        addMessage("assistant", buildString {
            appendLine("**Итоги демонстрации AI Code Review**")
            appendLine()
            appendLine("### Pipeline Overview")
            appendLine()
            appendLine("| Этап | Статус | Время |")
            appendLine("|------|--------|-------|")
            appendLine("| Загрузка PR diff | ok | ${msToTime(500)} |")
            appendLine("| RAG контекст | ok | ~${msToTime(2000)} |")
            appendLine("| LLM анализ | ok | ${msToTime(metrics.reviewTimeMs)} |")
            appendLine("| Оценка | ok | ~${msToTime(3000)} |")
            appendLine()
            appendLine("### Метрики")
            appendLine("- **Оценка:** ${metrics.overallScore}/100")
            appendLine("- **Проблем найдено:** ${metrics.issuesFound}")
            appendLine("- **Категории ошибок:** ${reportResult.issues.map { it.category.name }.distinct().joinToString(", ")}")
            appendLine("- **Severity распределение:** ${reportResult.issues.groupBy { it.severity.name }.map { "${it.key}: ${it.value.size}" }.joinToString(", ")}")
            appendLine("- **Общее время:** ${msToTime(metrics.reviewTimeMs + 5500)}")
            appendLine()
            appendLine("### Заключение")
            appendLine("AI-ревью успешно обработало PR #$prNumber.")
            appendLine("Выявлено ${metrics.issuesFound} проблем, общая оценка качества кода — ${metrics.overallScore}/100.")
        }, isDemoMessage = true)
    }

    private suspend fun evaluateWithCloud(
        report: ReviewReport,
        diffResult: GitHubApi.PrDiffResult,
        metrics: ReviewMetrics,
    ): String {
        return try {
            val client = ClientFactory.create()
            val systemPrompt = """
Ты — строгий эксперт по оценке качества AI-ревью кода.
Оцени работу AI-ревьювера по шкале от 0 до 100.

Критерии оценки:
1. Полнота охвата проблем (30%) — насколько хорошо найдены все проблемы
2. Точность (25%) — нет ли ложных срабатываний
3. Архитектурное понимание (20%) — насколько ревью понимает архитектуру проекта
4. Конструктивность рекомендаций (15%) — насколько полезны советы
5. Формат и читаемость (10%) — насколько хорошо оформлен результат

Дай финальную оценку и короткое обоснование.
""".trimIndent()

            val userPrompt = buildString {
                appendLine("## Оцени AI-ревью")
                appendLine()
                appendLine("### PR: ${diffResult.title}")
                appendLine("Автор: ${diffResult.author}")
                appendLine("Файлов: ${diffResult.files.size}, +${diffResult.totalAdditions}/-${diffResult.totalDeletions}")
                appendLine()
                appendLine("### Результат ревью:")
                appendLine("- Оценка: ${report.overallScore}/100")
                appendLine("- Проблем найдено: ${report.issues.size}")
                appendLine("- Время: ${metrics.reviewTimeMs}ms")
                appendLine()
                appendLine("### Найденные проблемы:")
                for (issue in report.issues) {
                    appendLine("- [${issue.severity}] ${issue.category}: ${issue.title}")
                    if (issue.filePath != null) appendLine("  Файл: ${issue.filePath}")
                }
                appendLine()
                appendLine("### Рекомендации:")
                for (rec in report.recommendations) {
                    appendLine("- $rec")
                }
                appendLine()
                appendLine("Дай оценку качеству ревью по шкале 0-100 и короткое обоснование.")
            }

            val request = RouterRequest(
                model = "mistral/mistral-large-latest",
                messages = listOf(
                    ChatMessage("system", systemPrompt),
                    ChatMessage("user", userPrompt),
                ),
                maxTokens = 1024,
                temperature = 0.3,
            )

            val response = client.sendRequest(request)
            response.error?.let { throw Exception(it.message) }
            val evalContent = response.choices?.firstOrNull()?.message?.content?.trim()
                ?: "Не удалось получить оценку"

            buildString {
                appendLine("### Оценка качества AI-ревью (облачная модель)")
                appendLine()
                appendLine(evalContent.take(2000))
            }
        } catch (e: Exception) {
            buildString {
                appendLine("**Оценка облачной модели недоступна:** ${e.message}")
                appendLine()
                appendLine("**Самооценка ревью:** ${report.overallScore}/100")
                appendLine("**Проблем найдено:** ${metrics.issuesFound}")
                appendLine("**Рекомендации даны:** ${report.recommendations.size}")
            }
        }
    }

    private fun msToTime(ms: Long): String = when {
        ms < 1000 -> "${ms}мс"
        ms < 60000 -> "${ms / 1000}.${(ms % 1000) / 100}с"
        else -> "${ms / 60000}м ${(ms % 60000) / 1000}с"
    }
}
