package com.llmapp.codeguardian

import com.llmapp.demo.manager.BaseDemoRunner
import com.llmapp.ui.models.ChatMessageUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

/**
 * Демо-раннер для Code Guardian — локального AI-ревьюера.
 *
 * Сценарии:
 * 1) Показ изменённых файлов + git diff stat
 * 2) AI-анализ diff: архитектура, именование, код-качество
 * 3) Генерация отчёта с оценкой и рекомендациями
 */
class CodeGuardianRunner(
    onMessageAdded: (ChatMessageUI) -> Unit,
    onTypingStateChanged: (Boolean) -> Unit,
    private val projectPath: String = "/Users/posse/StudioProjects/CalendarKMP",
) : BaseDemoRunner(onMessageAdded, onTypingStateChanged) {

    private lateinit var agent: CodeGuardianAgent
    private lateinit var git: GitWrapper

    override suspend fun run() {
        val resolvedPath = java.io.File(projectPath).absolutePath

        if (!java.io.File(resolvedPath).exists()) {
            addMessage("assistant", buildString {
                appendLine("**Ошибка:** путь не существует:")
                appendLine("`$resolvedPath`")
                appendLine()
                appendLine("Проверьте путь к проекту и попробуйте снова.")
            }, isDemoMessage = true)
            return
        }

        agent = CodeGuardianAgent(
            projectPath = resolvedPath,
            onProgress = { _ -> }
        )
        git = GitWrapper(resolvedPath)

        // ═══════════════════════════════════════════
        // ШАГ 1: Вступление + проверка git
        // ═══════════════════════════════════════════
        addMessage("assistant", buildString {
            appendLine("**KMP Code Guardian — Демонстрация**")
            appendLine()
            appendLine("Локальный AI-ревьюер перед коммитом.")
            appendLine("Проверяет архитектуру, именование, код-качество, безопасность.")
            appendLine()
            appendLine("**Проект:** `$projectPath`")
        }, isDemoMessage = true)
        delay(2.seconds)

        addMessage("assistant", "Проверяю git-репозиторий...", isDemoMessage = true)

        if (!git.isGitRepo()) {
            addMessage("assistant", "**Ошибка:** проект не является git-репозиторием.", isDemoMessage = true)
            return
        }

        val branch = withContext(Dispatchers.IO) { git.getBranch() }
        addMessage("assistant", "**Ветка:** $branch", isDemoMessage = true)
        delay(1.seconds)

        // ═══════════════════════════════════════════
        // ШАГ 2: Анализ git status + diff stat
        // ═══════════════════════════════════════════
        addMessage("assistant", buildString {
            appendLine("---")
            appendLine()
            appendLine("**Шаг 1: Сбор изменений**")
            appendLine()
            appendLine("Анализирую git diff и определяю изменённые файлы...")
        }, isDemoMessage = true)
        delay(1.seconds)

        val changedFiles = withContext(Dispatchers.IO) { git.getChangedFiles() }
        val untrackedFiles = withContext(Dispatchers.IO) { git.getUntrackedFiles() }
        val diffStat = withContext(Dispatchers.IO) { git.getDiffStat() }

        addMessage("assistant", buildString {
            if (changedFiles.isEmpty() && untrackedFiles.isEmpty()) {
                appendLine("**Результат:** Нет uncommitted изменений.")
                appendLine()
                appendLine("Для демонстрации покажу анализ на основе последних коммитов.")
            } else {
                appendLine("**Найдено:**")
                appendLine("- Изменённых файлов: ${changedFiles.size}")
                appendLine("- Untracked файлов: ${untrackedFiles.size}")
                appendLine()
                if (changedFiles.isNotEmpty()) {
                    appendLine("**Изменённые файлы:**")
                    changedFiles.forEach { appendLine("- `$it`") }
                    appendLine()
                }
                if (untrackedFiles.isNotEmpty()) {
                    appendLine("**Новые файлы:**")
                    untrackedFiles.take(10).forEach { appendLine("- `$it`") }
                    if (untrackedFiles.size > 10) appendLine("- ... и ещё ${untrackedFiles.size - 10}")
                    appendLine()
                }
                if (diffStat.isNotBlank()) {
                    appendLine("**Статистика:**")
                    appendLine("```")
                    appendLine(diffStat)
                    appendLine("```")
                }
            }
        }, isDemoMessage = true)
        delay(2.seconds)

        // ═══════════════════════════════════════════
        // ШАГ 3: AI-ревью через CodeGuardianAgent
        // ═══════════════════════════════════════════
        addMessage("assistant", buildString {
            appendLine("---")
            appendLine()
            appendLine("**Шаг 2: AI-ревью**")
            appendLine()
            appendLine("Отправляю diff в LLM для анализа...")
            appendLine("Проверяю: архитектуру (Clean Architecture + MVI), именование,")
            appendLine("код-качество, безопасность, дублирование.")
        }, isDemoMessage = true)
        delay(1.seconds)

        val report = withContext(Dispatchers.IO) {
            agent.review()
        }

        // ═══════════════════════════════════════════
        // ШАГ 4: Отображение результатов
        // ═══════════════════════════════════════════
        addMessage("assistant", buildString {
            appendLine("---")
            appendLine()
            appendLine("**Результат Code Review**")
            appendLine()
            appendLine("**Оценка: ${report.score}/10**")
            appendLine()
            appendLine("**Статистика:**")
            appendLine("- Файлов проверено: ${report.stats.filesReviewed}")
            appendLine("- Проблем найдено: ${report.stats.issuesFound}")
            appendLine("- Критичных: ${report.stats.criticalIssues}")
            appendLine("- Предупреждений: ${report.stats.warnings}")
            appendLine("- Размер diff: ${report.stats.diffSize} символов")
        }, isDemoMessage = true)
        delay(2.seconds)

        // Саммари
        if (report.summary.isNotBlank()) {
            addMessage("assistant", buildString {
                appendLine("**Саммари:**")
                appendLine()
                appendLine(report.summary)
            }, isDemoMessage = true)
            delay(2.seconds)
        }

        // Детали по секциям
        if (report.sections.isNotEmpty()) {
            addMessage("assistant", buildString {
                appendLine("**Детали проверок:**")
                appendLine()
                for (section in report.sections) {
                    val statusIcon = when (section.status) {
                        ReviewStatus.OK -> "✅"
                        ReviewStatus.WARNING -> "⚠️"
                        ReviewStatus.ERROR -> "❌"
                    }
                    appendLine("### $statusIcon ${section.title}")
                    section.items.forEach { appendLine("- $it") }
                    appendLine()
                }
            }, isDemoMessage = true)
            delay(3.seconds)
        }

        // Полный raw-ответ LLM (сокращённый)
        if (report.rawResponse.isNotBlank()) {
            val trimmed = report.rawResponse.take(4000)
            addMessage("assistant", buildString {
                appendLine("**Полный отчёт LLM** (${report.rawResponse.length} символов):")
                appendLine()
                appendLine("<details><summary>Развернуть</summary>")
                appendLine()
                appendLine(trimmed)
                if (report.rawResponse.length > 4000) {
                    appendLine()
                    appendLine("... (ещё ${report.rawResponse.length - 4000} символов)")
                }
                appendLine()
                appendLine("</details>")
            }, isDemoMessage = true)
            delay(2.seconds)
        }

        // ═══════════════════════════════════════════
        // ИТОГИ
        // ═══════════════════════════════════════════
        addMessage("assistant", buildString {
            appendLine("---")
            appendLine()
            appendLine("**Итоги демонстрации**")
            appendLine()
            appendLine("Code Guardian проанализировал ${report.stats.filesReviewed} файлов")
            appendLine("и нашёл ${report.stats.issuesFound} проблем")
            appendLine("(из них ${report.stats.criticalIssues} критичных).")
            appendLine()
            appendLine("**Общая оценка: ${report.score}/10**")
            appendLine()
            appendLine("---")
            appendLine("Для интерактивного использования: `/guardian` в чате.")
        }, isDemoMessage = true)
    }
}
