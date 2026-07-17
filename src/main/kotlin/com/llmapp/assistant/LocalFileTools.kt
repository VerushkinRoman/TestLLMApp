package com.llmapp.assistant

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.relativeTo

/**
 * Инструменты для работы с локальными файлами проекта.
 * Позволяет читать, искать, листать и записывать файлы.
 */
class LocalFileTools(private val projectRoot: String) {

    private val rootPath: Path = Paths.get(projectRoot).toAbsolutePath().normalize()

    init {
        require(rootPath.toFile().exists()) { "Project root does not exist: $projectRoot" }
    }

    data class FileMatch(
        val filePath: String,
        val lineNumber: Int,
        val lineContent: String,
        val context: String? = null,
    )

    /**
     * Получить дерево проекта (список файлов с фильтрацией).
     */
    fun listFiles(
        pattern: String? = null,
        maxDepth: Int = 15,
        extensions: Set<String>? = null,
    ): List<String> {
        val results = mutableListOf<String>()
        val walkable = Files.walk(rootPath, maxDepth)

        for (path in walkable) {
            val relative = path.relativeTo(rootPath).toString()
            if (relative.isBlank()) continue

            // Пропускаем служебные директории
            if (relative.startsWith(".") || relative.contains("/.")) continue
            if (relative.contains("build/") || relative.contains("build\\")) continue
            if (relative.contains(".gradle") || relative.contains("node_modules")) continue

            if (path.isRegularFile()) {
                val ext = path.extension.lowercase()
                if (extensions != null && extensions.isNotEmpty() && ext !in extensions) continue
                if (pattern != null && !relative.contains(pattern, ignoreCase = true)) continue
                results.add(relative)
            }
        }
        return results.sorted()
    }

    /**
     * Прочитать содержимое файла.
     */
    fun readFile(relativePath: String, maxLines: Int = 500): String {
        val file = resolveFile(relativePath)
        val lines = file.readLines()
        val truncated = lines.size > maxLines
        val content = if (truncated) {
            lines.take(maxLines).joinToString("\n") + "\n\n// [truncated at $maxLines lines, total: ${lines.size}]"
        } else {
            lines.joinToString("\n")
        }
        return content
    }

    /**
     * Поиск по содержимому файлов (grep-like).
     */
    fun searchContent(
        query: String,
        extensions: Set<String>? = setOf("kt", "kts"),
        maxResults: Int = 50,
    ): List<FileMatch> {
        val regex = try {
            Regex(query, RegexOption.IGNORE_CASE)
        } catch (_: Exception) {
            Regex(Regex.escape(query), RegexOption.IGNORE_CASE)
        }

        val results = mutableListOf<FileMatch>()
        val kotlinFiles = listFiles(extensions = extensions)

        for (relativePath in kotlinFiles) {
            if (results.size >= maxResults) break

            val file = resolveFile(relativePath)
            val lines = file.readLines()

            for ((index, line) in lines.withIndex()) {
                if (results.size >= maxResults) break
                if (regex.containsMatchIn(line)) {
                    val contextStart = maxOf(0, index - 1)
                    val contextEnd = minOf(lines.size - 1, index + 1)
                    val context = lines.subList(contextStart, contextEnd + 1).joinToString("\n")

                    results.add(
                        FileMatch(
                            filePath = relativePath,
                            lineNumber = index + 1,
                            lineContent = line.trim(),
                            context = context,
                        )
                    )
                }
            }
        }
        return results
    }

    /**
     * Поиск всех мест использования класса/интерфейса/функции.
     */
    fun findUsages(symbolName: String): List<FileMatch> {
        // Ищем точное имя как класс, импорт, или вызов
        val patterns = listOf(
            "\\b${Regex.escape(symbolName)}\\b",       // anywhere
            "import.*${Regex.escape(symbolName)}\\b",   // import
        )

        val results = mutableListOf<FileMatch>()
        val seen = mutableSetOf<String>()

        for (pattern in patterns) {
            val matches = searchContent(pattern, maxResults = 200)
            for (match in matches) {
                val key = "${match.filePath}:${match.lineNumber}"
                if (key !in seen) {
                    seen.add(key)
                    results.add(match)
                }
            }
        }
        return results.sortedBy { it.filePath }
    }

    /**
     * Подсчитать статистику проекта (по расширениям).
     */
    fun projectStats(): Map<String, Pair<Int, Long>> {
        val extensions = setOf("kt", "kts", "java", "xml", "json", "toml", "yml", "yaml", "md")
        val files = listFiles(extensions = extensions)
        val stats = mutableMapOf<String, Pair<Int, Long>>() // ext -> (count, totalBytes)

        for (relativePath in files) {
            val file = resolveFile(relativePath)
            val ext = file.extension.lowercase()
            val current = stats[ext] ?: (0 to 0L)
            stats[ext] = (current.first + 1) to (current.second + file.length())
        }
        return stats
    }

    /**
     * Записать/создать файл в проекте.
     */
    fun writeFile(relativePath: String, content: String): String {
        val file = resolveFileSafe(relativePath)
        file.parentFile?.mkdirs()
        file.writeText(content)
        return "File created/updated: $relativePath (${content.lines().size} lines)"
    }

    /**
     * Переместить/переименовать файл или директорию.
     * Создаёт целевую директорию при необходимости.
     */
    fun moveFile(sourceRelative: String, destRelative: String): String {
        val source = resolveFile(sourceRelative)
        val dest = resolveFile(destRelative)
        require(source.exists()) { "Source does not exist: $sourceRelative" }
        require(!dest.exists()) { "Destination already exists: $destRelative" }
        require(dest.parentFile != null) { "Invalid destination path: $destRelative" }

        dest.parentFile?.mkdirs()
        val success = source.renameTo(dest)
        return if (success) {
            "Moved: $sourceRelative → $destRelative"
        } else {
            // Fallback: copy + delete (works across file systems)
            source.copyTo(dest, overwrite = false)
            source.deleteRecursively()
            "Moved (copy+delete): $sourceRelative → $destRelative"
        }
    }

    /**
     * Удалить файл или директорию.
     */
    fun deleteFile(relativePath: String): String {
        val file = resolveFile(relativePath)
        require(file.exists()) { "File does not exist: $relativePath" }
        val isDir = file.isDirectory
        file.deleteRecursively()
        return if (isDir) "Deleted directory: $relativePath" else "Deleted file: $relativePath"
    }

    /**
     * Собрать архитектурный обзор проекта: все классы, интерфейсы, DI модули.
     */
    fun architecturalOverview(): Map<String, List<String>> {
        val classes = mutableMapOf<String, MutableList<String>>()
        val kotlinFiles = listFiles(extensions = setOf("kt"))

        for (relativePath in kotlinFiles) {
            val content = resolveFile(relativePath).readText()
            val packageName = Regex("package\\s+([\\w.]+)").find(content)?.groupValues?.get(1) ?: "unknown"

            val classMatches = Regex("(?:class|interface|object|enum class)\\s+(\\w+)").findAll(content)
            for (match in classMatches) {
                val name = match.groupValues[1]
                classes.getOrPut(packageName) { mutableListOf() }.add(name)
            }
        }
        return classes
    }

    private fun resolveFile(relativePath: String): File {
        val normalized = relativePath.replace("\\", "/")
        val file = rootPath.resolve(normalized).toAbsolutePath().normalize().toFile()
        require(file.exists()) { "File not found: $relativePath" }
        require(file.path.startsWith(rootPath.toString())) { "Path traversal not allowed: $relativePath" }
        return file
    }

    /**
     * Безопасное разрешение пути для write_file — проверяет path traversal,
     * но НЕ требует существования файла (для создания новых).
     */
    private fun resolveFileSafe(relativePath: String): File {
        val normalized = relativePath.replace("\\", "/")
        val file = rootPath.resolve(normalized).toAbsolutePath().normalize().toFile()
        require(file.path.startsWith(rootPath.toString())) { "Path traversal not allowed: $relativePath" }
        return file
    }
}
