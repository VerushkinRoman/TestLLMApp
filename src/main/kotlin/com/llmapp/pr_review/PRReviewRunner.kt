package com.llmapp.pr_review

import com.llmapp.api.ApiConfig
import com.llmapp.rag.data.GitHubApi
import java.io.File

/**
 * CLI entry point for running PR review from command line or GitHub Actions.
 *
 * Usage: PRNumber [outputDir] [model]
 *   - PRNumber: required, the PR number to review
 *   - outputDir: optional, default "build/reports/pr_review"
 *   - model: optional, default "mistral/mistral-large-latest"
 */
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: PRReviewRunner <PRNumber> [outputDir] [model]")
        kotlin.system.exitProcess(1)
    }

    val prNumber = args[0].toIntOrNull()
    if (prNumber == null) {
        println("Error: PR number must be an integer, got '${args[0]}'")
        kotlin.system.exitProcess(1)
    }

    val outputDir = if (args.size > 1) File(args[1]) else File("build/reports/pr_review")
    val model = if (args.size > 2) args[2] else "mistral/mistral-large-latest"

    println("=" .repeat(60))
    println("  AI Code Review Pipeline")
    println("=" .repeat(60))
    println("  PR #$prNumber")
    println("  Model: $model")
    println("  Output: ${outputDir.absolutePath}")
    println()

    // Check GitHub token
    val token = GitHubApi.getToken()
    if (token == null) {
        println("Error: GitHub token not found. Set GITHUB_TOKEN env var or keys.properties")
        kotlin.system.exitProcess(1)
    }
    println("  GitHub token: ${token.take(10)}...")

    // Check LLM credentials
    try {
        val pwd = ApiConfig.getLlmUserPassword()
        println("  LLM credentials: ok")
    } catch (e: Exception) {
        println("Error: LLM credentials not found: ${e.message}")
        kotlin.system.exitProcess(1)
    }

    println()

    // Run review
    kotlinx.coroutines.runBlocking {
        try {
            println("  Step 1/3: Fetching PR #$prNumber diff...")
            val diffResult = GitHubApi.getPullRequestDiff(prNumber)
            if (diffResult == null) {
                println("  Error: Failed to fetch PR #$prNumber")
                kotlin.system.exitProcess(1)
            }
            println("    Title: ${diffResult.title}")
            println("    Author: ${diffResult.author}")
            println("    Files: ${diffResult.files.size}")
            println("    Changes: +${diffResult.totalAdditions}/-${diffResult.totalDeletions}")
            println()

            println("  Step 2/3: Running AI review...")
            val agent = PRReviewAgent(
                config = ReviewConfig(model = model)
            )

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

            val startTime = System.currentTimeMillis()
            val report = agent.reviewFromDiff(diff)
            val elapsed = System.currentTimeMillis() - startTime
            println("    Done in ${elapsed}ms")
            println()

            println("  Step 3/3: Saving report...")
            outputDir.mkdirs()

            // Markdown report
            val mdReport = buildMarkdownReport(report, diff)
            val mdFile = File(outputDir, "review_$prNumber.md")
            mdFile.writeText(mdReport)
            println("    Report: ${mdFile.absolutePath}")

            // JSON report
            val jsonReport = buildJsonReport(report, diff)
            val jsonFile = File(outputDir, "review_$prNumber.json")
            jsonFile.writeText(jsonReport)
            println("    JSON: ${jsonFile.absolutePath}")

            // Summary
            println()
            println("-" .repeat(60))
            println("  Review Summary")
            println("-" .repeat(60))
            println("  Score: ${report.overallScore}/100")
            println("  Issues: ${report.issues.size}")
            println("  Critical: ${report.issues.count { it.severity == ReviewSeverity.CRITICAL }}")
            println("  Warnings: ${report.issues.count { it.severity == ReviewSeverity.WARNING }}")
            println("  Info: ${report.issues.count { it.severity == ReviewSeverity.INFO }}")
            println("  Suggestions: ${report.issues.count { it.severity == ReviewSeverity.SUGGESTION }}")
            println("-" .repeat(60))

        } catch (e: Exception) {
            println("  Error: ${e.message}")
            e.printStackTrace()
            kotlin.system.exitProcess(1)
        }
    }
}

private fun buildMarkdownReport(report: ReviewReport, diff: PullRequestDiff): String = buildString {
    appendLine("# AI Code Review: PR #${report.prInfo.number}")
    appendLine()
    appendLine("> **${report.prInfo.title}** by ${report.prInfo.author}")
    appendLine()
    appendLine("## Summary")
    appendLine()
    appendLine("| Metric | Value |")
    appendLine("|--------|-------|")
    appendLine("| Overall Score | **${report.overallScore}/100** |")
    appendLine("| Issues Found | ${report.issues.size} |")
    appendLine("| Files Reviewed | ${diff.files.size} |")
    appendLine("| Changes | +${diff.totalAdditions}/-${diff.totalDeletions} |")
    appendLine()
    appendLine("### Issues by Severity")
    appendLine()
    for (severity in listOf(ReviewSeverity.CRITICAL, ReviewSeverity.WARNING, ReviewSeverity.INFO, ReviewSeverity.SUGGESTION)) {
        val count = report.issues.count { it.severity == severity }
        appendLine("- **${severity.name}**: $count")
    }
    appendLine()

    if (report.positiveHighlights.isNotEmpty()) {
        appendLine("## Positive Highlights")
        appendLine()
        for (h in report.positiveHighlights) {
            appendLine("- $h")
        }
        appendLine()
    }

    if (report.issues.isNotEmpty()) {
        appendLine("## Issues Found")
        appendLine()
        val sorted = report.issues.sortedByDescending { it.severity.ordinal }
        for (issue in sorted) {
            val icon = when (issue.severity) {
                ReviewSeverity.CRITICAL -> "🔴"
                ReviewSeverity.WARNING -> "🟡"
                ReviewSeverity.INFO -> "🔵"
                ReviewSeverity.SUGGESTION -> "🟢"
            }
            appendLine("### $icon [${issue.severity}] ${issue.category.name}: ${issue.title}")
            if (issue.filePath != null) appendLine("- **File:** `${issue.filePath}`${issue.lineNumber?.let { ":$it" } ?: ""}")
            appendLine("- **Description:** ${issue.description}")
            if (issue.suggestion != null) appendLine("- **Suggestion:** ${issue.suggestion}")
            appendLine()
        }
    }

    if (report.recommendations.isNotEmpty()) {
        appendLine("## Recommendations")
        appendLine()
        for (rec in report.recommendations) {
            appendLine("- $rec")
        }
        appendLine()
    }

    appendLine("---")
    appendLine("*Generated by AI Code Review Pipeline*")
}

private fun buildJsonReport(report: ReviewReport, diff: PullRequestDiff): String {
    val sb = StringBuilder()
    sb.appendLine("{")
    sb.appendLine("  \"prNumber\": ${report.prInfo.number},")
    sb.appendLine("  \"prTitle\": \"${report.prInfo.title.replace("\"", "\\\"")}\",")
    sb.appendLine("  \"author\": \"${report.prInfo.author}\",")
    sb.appendLine("  \"overallScore\": ${report.overallScore},")
    sb.appendLine("  \"totalIssues\": ${report.issues.size},")
    sb.appendLine("  \"totalFiles\": ${diff.files.size},")
    sb.appendLine("  \"totalAdditions\": ${diff.totalAdditions},")
    sb.appendLine("  \"totalDeletions\": ${diff.totalDeletions},")

    val bySeverity = report.issues.groupBy { it.severity }
    sb.appendLine("  \"issuesBySeverity\": {")
    for ((i, severity) in listOf(ReviewSeverity.CRITICAL, ReviewSeverity.WARNING, ReviewSeverity.INFO, ReviewSeverity.SUGGESTION).withIndex()) {
        val comma = if (i < 3) "," else ""
        sb.appendLine("    \"${severity.name.lowercase()}\": ${bySeverity[severity]?.size ?: 0}$comma")
    }
    sb.appendLine("  },")

    sb.appendLine("  \"summary\": \"${report.summary.take(200).replace("\"", "\\\"")}\"")
    sb.appendLine("}")
    return sb.toString()
}
