package com.llmapp.codeguardian

import java.io.File

/**
 * Обёртка над git CLI для получения diff, status, log.
 * Работает через Runtime.exec — не требует библиотек.
 */
class GitWrapper(private val projectPath: String) {

    private val rootDir: File
        get() = File(projectPath)

    fun isGitRepo(): Boolean {
        return File(projectPath, ".git").exists()
    }

    fun getBranch(): String {
        return runGit("rev-parse", "--abbrev-ref", "HEAD").trim()
    }

    fun getDiffStaged(): String {
        return runGit("diff", "--cached")
    }

    fun getDiffWorking(): String {
        return runGit("diff")
    }

    fun getDiffAll(): String {
        val staged = getDiffStaged()
        val working = getDiffWorking()
        return buildString {
            if (staged.isNotBlank()) {
                appendLine("=== STAGED ===")
                appendLine(staged)
            }
            if (working.isNotBlank()) {
                appendLine("=== WORKING TREE ===")
                appendLine(working)
            }
            if (staged.isBlank() && working.isBlank()) {
                appendLine("(нет изменений)")
            }
        }
    }

    fun getDiffStat(): String {
        return runGit("diff", "--stat")
    }

    fun getChangedFiles(): List<String> {
        val output = runGit("diff", "--name-only")
        val staged = runGit("diff", "--cached", "--name-only")
        val all = mutableSetOf<String>()
        if (output.isNotBlank()) all.addAll(output.lines().filter { it.isNotBlank() })
        if (staged.isNotBlank()) all.addAll(staged.lines().filter { it.isNotBlank() })
        return all.sorted()
    }

    fun getUntrackedFiles(): List<String> {
        val output = runGit("ls-files", "--others", "--exclude-standard")
        return output.lines().filter { it.isNotBlank() }.sorted()
    }

    fun getAddedFiles(): List<String> = getFilesByDiffFilter("A")

    fun getDeletedFiles(): List<String> = getFilesByDiffFilter("D")

    fun getModifiedFiles(): List<String> = getFilesByDiffFilter("M")

    private fun getFilesByDiffFilter(filter: String): List<String> {
        val output = runGit("diff", "--cached", "--diff-filter=$filter", "--name-only")
        val working = runGit("diff", "--diff-filter=$filter", "--name-only")
        val all = mutableSetOf<String>()
        if (output.isNotBlank()) all.addAll(output.lines().filter { it.isNotBlank() })
        if (working.isNotBlank()) all.addAll(working.lines().filter { it.isNotBlank() })
        return all.sorted()
    }

    private fun runGit(vararg args: String): String {
        return try {
            val process = ProcessBuilder(listOf("git") + args.toList())
                .directory(rootDir)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output
        } catch (e: Exception) {
            "Ошибка git: ${e.message}"
        }
    }
}
