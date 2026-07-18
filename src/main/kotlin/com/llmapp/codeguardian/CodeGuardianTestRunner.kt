package com.llmapp.codeguardian

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * CLI-раннер для Code Guardian.
 *
 * Запуск: ./gradlew runGuardianTest
 * С аргументом (путь): ./gradlew runGuardianTest -Pargs="/path/to/project"
 */
fun main(args: Array<String>) = runBlocking {
    val projectPath = args.firstOrNull()
        ?: System.getProperty("user.dir")

    println("╔══════════════════════════════════════════════════════════╗")
    println("║  KMP Code Guardian — CLI Test Runner                   ║")
    println("╚══════════════════════════════════════════════════════════╝")
    println()
    println("Проект: $projectPath")
    println()

    val git = GitWrapper(projectPath)

    // Проверяем git
    if (!git.isGitRepo()) {
        println("❌ Проект не является git-репозиторием")
        return@runBlocking
    }

    val branch = withContext(Dispatchers.IO) { git.getBranch() }
    val changedFiles = withContext(Dispatchers.IO) { git.getChangedFiles() }
    val untrackedFiles = withContext(Dispatchers.IO) { git.getUntrackedFiles() }
    val diffStat = withContext(Dispatchers.IO) { git.getDiffStat() }

    println("═══════════════════════════════════════════════════════════")
    println("  GIT STATUS")
    println("═══════════════════════════════════════════════════════════")
    println("  Ветка: $branch")
    println("  Изменённых файлов: ${changedFiles.size}")
    println("  Untracked файлов: ${untrackedFiles.size}")
    println()

    if (changedFiles.isNotEmpty()) {
        println("  Изменённые файлы:")
        changedFiles.forEach { println("    - $it") }
        println()
    }

    if (untrackedFiles.isNotEmpty()) {
        println("  Новые файлы:")
        untrackedFiles.take(10).forEach { println("    - $it") }
        if (untrackedFiles.size > 10) println("    ... и ещё ${untrackedFiles.size - 10}")
        println()
    }

    if (diffStat.isNotBlank()) {
        println("  Diff stat:")
        println("    $diffStat")
        println()
    }

    println("═══════════════════════════════════════════════════════════")
    println("  AI-REVIEW")
    println("═══════════════════════════════════════════════════════════")
    println()

    val agent = CodeGuardianAgent(
        projectPath = projectPath,
        onProgress = { msg -> println("  → $msg") }
    )

    val report = agent.review()

    println()
    println("═══════════════════════════════════════════════════════════")
    println("  РЕЗУЛЬТАТ")
    println("═══════════════════════════════════════════════════════════")
    println()
    println("  Оценка: ${report.score}/10")
    println("  Файлов проверено: ${report.stats.filesReviewed}")
    println("  Проблем: ${report.stats.issuesFound} (критичных: ${report.stats.criticalIssues})")
    println()
    println("  Саммари:")
    println("  ${report.summary}")
    println()

    if (report.sections.isNotEmpty()) {
        println("  Детали:")
        for (section in report.sections) {
            val icon = when (section.status) {
                ReviewStatus.OK -> "✅"
                ReviewStatus.WARNING -> "⚠️"
                ReviewStatus.ERROR -> "❌"
            }
            println("  $icon ${section.title}")
            section.items.forEach { println("    - $it") }
            println()
        }
    }

    // Сохраняем отчёт
    val reportDir = java.io.File(projectPath, "build/reports/code_guardian")
    reportDir.mkdirs()
    val reportFile = java.io.File(reportDir, "review_${branch}_${System.currentTimeMillis()}.md")
    reportFile.writeText(buildString {
        appendLine("# Code Guardian Review")
        appendLine()
        appendLine("- **Ветка:** $branch")
        appendLine("- **Оценка:** ${report.score}/10")
        appendLine("- **Файлов:** ${report.stats.filesReviewed}")
        appendLine("- **Проблем:** ${report.stats.issuesFound}")
        appendLine()
        appendLine(report.rawResponse)
    })

    println("═══════════════════════════════════════════════════════════")
    println("  Отчёт сохранён: ${reportFile.absolutePath}")
    println("═══════════════════════════════════════════════════════════")
}
