package com.llmapp.mcp

import com.llmapp.rag.data.GitHubApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GitHubMcpTools {

    private val toolDefinitions = listOf(
        McpIntegration.McpToolInfo(
            name = "github_read_file",
            description = "Read file content from GitHub repository (CalendarKMP). Returns raw file content.",
            parameters = listOf(
                McpIntegration.McpToolParam(
                    name = "path",
                    type = "string",
                    description = "File path in repo, e.g. 'project/docs/architecture.md' or 'composeApp/src/commonMain/kotlin/com/posse/kotlin1/calendar/domain/utils/Result.kt'"
                )
            ),
            requiredParams = listOf("path")
        ),
        McpIntegration.McpToolInfo(
            name = "github_list_dir",
            description = "List files and directories in a GitHub repository path. Returns list of entries with name, path, and type (file/dir).",
            parameters = listOf(
                McpIntegration.McpToolParam(
                    name = "path",
                    type = "string",
                    description = "Directory path in repo, e.g. 'project/docs' or '' for root. Empty string = repo root."
                )
            ),
            requiredParams = listOf("path")
        ),
        McpIntegration.McpToolInfo(
            name = "github_search_source",
            description = "Search Kotlin source files by keyword. Scans the codebase for files matching the keyword in name or content. Returns file paths and truncated content of top matches.",
            parameters = listOf(
                McpIntegration.McpToolParam(
                    name = "keyword",
                    type = "string",
                    description = "Search keyword (e.g. 'ViewModel', 'Repository', 'GetStartCalendarData')"
                ),
                McpIntegration.McpToolParam(
                    name = "max_results",
                    type = "string",
                    description = "Max files to return (default: 3)"
                )
            ),
            requiredParams = listOf("keyword")
        ),
        McpIntegration.McpToolInfo(
            name = "github_list_docs",
            description = "List all documentation files in project/docs directory.",
            parameters = emptyList(),
            requiredParams = null
        ),
        McpIntegration.McpToolInfo(
            name = "github_list_source_tree",
            description = "List Kotlin source code tree under composeApp/src/commonMain/kotlin. Shows directory structure.",
            parameters = listOf(
                McpIntegration.McpToolParam(
                    name = "path",
                    type = "string",
                    description = "Sub-path within source tree, e.g. 'domain' or 'data'. Empty = full tree root."
                )
            ),
            requiredParams = listOf("path")
        ),
        McpIntegration.McpToolInfo(
            name = "github_list_prs",
            description = "List open Pull Requests in CalendarKMP repository. Returns PR number, title, author, and status.",
            parameters = listOf(
                McpIntegration.McpToolParam(
                    name = "max_results",
                    type = "string",
                    description = "Max PRs to return (default: 5)"
                )
            ),
            requiredParams = null
        ),
        McpIntegration.McpToolInfo(
            name = "github_get_pr",
            description = "Get detailed Pull Request information and diff from CalendarKMP. Returns PR metadata and full diff of changed files.",
            parameters = listOf(
                McpIntegration.McpToolParam(
                    name = "pr_number",
                    type = "string",
                    description = "PR number (e.g. '1', '2')"
                )
            ),
            requiredParams = listOf("pr_number")
        ),
    )

    fun getToolDefinitions(): List<McpIntegration.McpToolInfo> = toolDefinitions

    fun getToolNames(): List<String> = toolDefinitions.map { it.name }

    suspend fun executeTool(toolName: String, args: Map<String, String>): String {
        return when (toolName) {
            "github_read_file" -> executeReadFile(args)
            "github_list_dir" -> executeListDir(args)
            "github_search_source" -> executeSearchSource(args)
            "github_list_docs" -> executeListDocs()
            "github_list_source_tree" -> executeListSourceTree(args)
            "github_list_prs" -> executeListPRs(args)
            "github_get_pr" -> executeGetPR(args)
            else -> "❌ Unknown tool: $toolName"
        }
    }

    private suspend fun executeReadFile(args: Map<String, String>): String {
        val path = args["path"] ?: return "❌ Missing 'path' parameter"
        val content = GitHubApi.readFile(path)
            ?: return "❌ File not found: $path"
        val truncated = if (content.length > 6000) {
            content.take(6000) + "\n\n// [truncated — ${content.length} chars total]"
        } else {
            content
        }
        return "📄 $path:\n```\n$truncated\n```"
    }

    private suspend fun executeListDir(args: Map<String, String>): String {
        val path = args["path"] ?: ""
        val entries = GitHubApi.listDirectory(path)
        if (entries.isEmpty()) return "📁 Directory empty or not found: ${path.ifEmpty { "/" }}"
        val sb = StringBuilder("📁 ${path.ifEmpty { "/" }}:\n")
        entries.sortedBy { it.type + it.name }.forEach { entry ->
            val icon = if (entry.type == "dir") "📁" else "📄"
            sb.appendLine("$icon ${entry.name} (${entry.type})")
        }
        return sb.toString()
    }

    private suspend fun executeSearchSource(args: Map<String, String>): String {
        val keyword = args["keyword"] ?: return "❌ Missing 'keyword' parameter"
        val maxResults = args["max_results"]?.toIntOrNull() ?: 3
        val rootPath = "composeApp/src/commonMain/kotlin/com/posse/kotlin1/calendar"

        val matches = mutableListOf<Triple<String, String, String>>()

        suspend fun searchDir(path: String) {
            if (matches.size >= maxResults) return
            val entries = GitHubApi.listDirectory(path)
            for (entry in entries) {
                if (matches.size >= maxResults) return
                when {
                    entry.type == "dir" && !entry.name.startsWith(".") -> searchDir(entry.path)
                    entry.type == "file" && entry.name.endsWith(".kt") -> {
                        val nameMatch = entry.name.contains(keyword, ignoreCase = true)
                        if (nameMatch) {
                            val content = GitHubApi.readFile(entry.path) ?: continue
                            val truncated = content.take(3000)
                            matches.add(Triple(entry.name, entry.path, truncated))
                        }
                    }
                }
            }
        }

        withContext(Dispatchers.IO) { searchDir(rootPath) }

        if (matches.isEmpty()) {
            val allKtFiles = mutableListOf<Pair<String, String>>()
            suspend fun collectFiles(path: String) {
                val entries = GitHubApi.listDirectory(path)
                for (entry in entries) {
                    if (allKtFiles.size > 30) return
                    when {
                        entry.type == "dir" && !entry.name.startsWith(".") -> collectFiles(entry.path)
                        entry.type == "file" && entry.name.endsWith(".kt") -> {
                            allKtFiles.add(entry.name to entry.path)
                        }
                    }
                }
            }
            withContext(Dispatchers.IO) { collectFiles(rootPath) }

            for ((name, path) in allKtFiles) {
                if (matches.size >= maxResults) break
                val content = GitHubApi.readFile(path) ?: continue
                if (content.contains(keyword, ignoreCase = true)) {
                    val truncated = content.take(3000)
                    matches.add(Triple(name, path, truncated))
                }
            }
        }

        if (matches.isEmpty()) return "🔍 No source files found matching '$keyword'"

        val sb = StringBuilder("🔍 Found ${matches.size} files matching '$keyword':\n\n")
        for ((name, path, content) in matches) {
            sb.appendLine("--- $name ($path) ---")
            sb.appendLine("```kotlin")
            sb.appendLine(content)
            sb.appendLine("```\n")
        }
        return sb.toString()
    }

    private suspend fun executeListDocs(): String {
        val entries = GitHubApi.listDirectory("project/docs")
        if (entries.isEmpty()) return "📁 No docs found"
        val sb = StringBuilder("📁 project/docs:\n")
        entries.filter { it.type == "file" }.sortedBy { it.name }.forEach { entry ->
            sb.appendLine("📄 ${entry.name}")
        }
        return sb.toString()
    }

    private suspend fun executeListSourceTree(args: Map<String, String>): String {
        val subPath = args["path"] ?: ""
        val rootPath = "composeApp/src/commonMain/kotlin/com/posse/kotlin1/calendar"
        val fullPath = if (subPath.isEmpty()) rootPath else "$rootPath/$subPath"

        val sb = StringBuilder("📁 Source tree: ${subPath.ifEmpty { "root" }}:\n")
        suspend fun listDir(path: String, indent: String = "") {
            val entries = GitHubApi.listDirectory(path)
            entries.sortedBy { it.type + it.name }.forEach { entry ->
                if (entry.type == "dir") {
                    sb.appendLine("$indent📁 ${entry.name}/")
                    if (indent.length < 12) {
                        listDir(entry.path, "$indent  ")
                    }
                } else {
                    sb.appendLine("$indent📄 ${entry.name}")
                }
            }
        }
        withContext(Dispatchers.IO) { listDir(fullPath) }
        return sb.toString()
    }

    private suspend fun executeListPRs(args: Map<String, String>): String {
        val maxResults = args["max_results"]?.toIntOrNull() ?: 5
        val prs = GitHubApi.listOpenPullRequests()

        if (prs.isEmpty()) return "📭 No open Pull Requests in CalendarKMP"

        val sb = StringBuilder("🔀 Open Pull Requests in CalendarKMP:\n\n")
        prs.take(maxResults).forEach { pr ->
            sb.appendLine("PR #${pr.number}: ${pr.title}")
            sb.appendLine("   Author: ${pr.author}")
            sb.appendLine("   Created: ${pr.createdAt}")
            sb.appendLine()
        }
        if (prs.size > maxResults) {
            sb.appendLine("... and ${prs.size - maxResults} more")
        }
        return sb.toString()
    }

    private suspend fun executeGetPR(args: Map<String, String>): String {
        val prNumber = args["pr_number"]?.toIntOrNull()
            ?: return "❌ Invalid or missing 'pr_number'. Provide a number (e.g. '1')"

        val diffResult = GitHubApi.getPullRequestDiff(prNumber)
            ?: return "❌ Failed to fetch PR #$prNumber (not found or API error)"

        val sb = StringBuilder("🔀 PR #$prNumber: ${diffResult.title}\n")
        sb.appendLine("   Author: ${diffResult.author}")
        sb.appendLine("   Description: ${diffResult.description.take(500)}")
        sb.appendLine("   Changes: +${diffResult.totalAdditions}/-${diffResult.totalDeletions}")
        sb.appendLine("   Files: ${diffResult.files.size}")
        sb.appendLine()

        sb.appendLine("## Изменённые файлы (diff):\n")
        for (file in diffResult.files) {
            sb.appendLine("### ${file.filename} (${file.status}, +${file.additions}/-${file.deletions})")
            if (file.patch != null && file.patch.isNotBlank()) {
                val patchLines = file.patch.lines()
                val truncatedPatch = if (patchLines.size > 100) {
                    patchLines.take(80).joinToString("\n") + "\n... [truncated, ${patchLines.size} lines total]"
                } else {
                    file.patch
                }
                sb.appendLine("```diff")
                sb.appendLine(truncatedPatch)
                sb.appendLine("```")
            } else {
                sb.appendLine("*(binary or empty diff)*")
            }
            sb.appendLine()
        }

        return sb.toString()
    }
}
