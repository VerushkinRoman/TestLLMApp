package com.llmapp.rag.data

import com.llmapp.rag.domain.RagAnswer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SourceCodeProvider {

    private const val KMP_ROOT = "composeApp/src/commonMain/kotlin/com/posse/kotlin1/calendar"
    private const val MAX_BYTES_PER_FILE = 4000
    private const val MAX_TOTAL_BYTES = 12000
    private val classPathCache = mutableListOf<Pair<String, String>>()
    private var cacheLoaded = false

    private suspend fun ensureCacheLoaded() {
        if (cacheLoaded) return
        withContext(Dispatchers.IO) {
            scanDir(KMP_ROOT)
        }
        cacheLoaded = true
    }

    private suspend fun scanDir(path: String) {
        val items = GitHubApi.listDirectory(path)
        for (item in items) {
            when {
                item.type == "dir" && !item.name.startsWith(".") -> scanDir(item.path)
                item.type == "file" && item.name.endsWith(".kt") -> {
                    val className = item.name.removeSuffix(".kt")
                    classPathCache.add(className to item.path)
                }
            }
        }
    }

    suspend fun findRelevantFiles(
        question: String,
        ragAnswer: RagAnswer?,
        maxFiles: Int = 3,
    ): List<Pair<String, String>> {
        ensureCacheLoaded()

        val keywords = mutableSetOf<String>()
        question.split("\\s+".toRegex()).filter { it.length > 3 }.forEach { keywords.add(it.lowercase()) }
        ragAnswer?.sources?.forEach { src ->
            src.title.split("\\s+").filter { it.length > 3 }.forEach { keywords.add(it.lowercase()) }
            src.section.split("\\s+").filter { it.length > 3 }.forEach { keywords.add(it.lowercase()) }
        }
        val pascalPattern = Regex("[A-Z][a-z]+[A-Z][a-zA-Z]*")
        pascalPattern.findAll(question).forEach { keywords.add(it.value.lowercase()) }

        val scored = classPathCache.map { (className, path) ->
            val nameLower = className.lowercase()
            val score = keywords.count { kw -> nameLower.contains(kw) || kw.contains(nameLower) }
            Triple(className, path, score)
        }.filter { it.third > 0 }
            .sortedByDescending { it.third }
            .take(maxFiles)

        return withContext(Dispatchers.IO) {
            var totalBytes = 0
            scored.mapNotNull { (_, path, _) ->
                if (totalBytes >= MAX_TOTAL_BYTES) return@mapNotNull null
                val raw = GitHubApi.readFile(path) ?: return@mapNotNull null
                val truncated = truncateFile(raw)
                totalBytes += truncated.length
                path to truncated
            }
        }
    }

    private fun truncateFile(content: String): String {
        if (content.length <= MAX_BYTES_PER_FILE) return content

        val lines = content.lines()
        val header = mutableListOf<String>()
        val publicMembers = mutableListOf<String>()
        var currentMember = mutableListOf<String>()
        var braceDepth = 0
        var inMember = false

        for (line in lines) {
            val trimmed = line.trim()
            if (braceDepth == 0 && !inMember) {
                if (trimmed.startsWith("package ") || trimmed.startsWith("import ") || trimmed.isEmpty()) {
                    header.add(line)
                    continue
                }
                if (trimmed.startsWith("package ") || trimmed.startsWith("import ")) {
                    header.add(line)
                    continue
                }
            }

            if (braceDepth == 0 && (trimmed.startsWith("class ") || trimmed.startsWith("interface ") ||
                        trimmed.startsWith("object ") || trimmed.startsWith("enum ") ||
                        trimmed.startsWith("data class ") || trimmed.startsWith("sealed ") ||
                        trimmed.startsWith("abstract class ") || trimmed.startsWith("open class ") ||
                        trimmed.startsWith("annotation class ") || trimmed.startsWith("value class ") ||
                        trimmed.startsWith("fun ") || trimmed.startsWith("suspend fun ") ||
                        trimmed.startsWith("val ") || trimmed.startsWith("var ") ||
                        trimmed.startsWith("private fun ") || trimmed.startsWith("internal fun ") ||
                        trimmed.startsWith("protected fun ") || trimmed.startsWith("override fun ")
                )) {
                if (currentMember.isNotEmpty()) {
                    publicMembers.add(currentMember.joinToString("\n"))
                }
                currentMember = mutableListOf(line)
                inMember = true
            } else if (inMember) {
                currentMember.add(line)
            }

            braceDepth += trimmed.count { it == '{' } - trimmed.count { it == '}' }
            if (braceDepth <= 0 && inMember) {
                inMember = false
                braceDepth = 0
            }
        }
        if (currentMember.isNotEmpty()) {
            publicMembers.add(currentMember.joinToString("\n"))
        }

        val sb = StringBuilder()
        sb.appendLine(header.joinToString("\n"))
        sb.appendLine()
        sb.appendLine("// [truncated — full file ${content.length} chars]")
        sb.appendLine()

        var usedChars = sb.length
        for (member in publicMembers) {
            if (usedChars + member.length > MAX_BYTES_PER_FILE) {
                sb.appendLine("// ... more members truncated")
                break
            }
            sb.appendLine(member)
            sb.appendLine()
            usedChars += member.length
        }

        return sb.toString()
    }
}

