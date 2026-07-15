package com.llmapp.pr_review

import com.llmapp.api.ClientFactory
import com.llmapp.api.RouterClient
import com.llmapp.mcp.GitHubMcpTools
import com.llmapp.model.ChatMessage
import com.llmapp.model.RouterRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Упрощённый агент: пользователь просто говорит "проверь PR #N в CalendarKMP",
 * агент сам ходит через MCP-тулы и пишет саммари в чат.
 */
class PRReviewAgent2(
    private val apiClient: RouterClient = ClientFactory.create(),
) {
    private val model = "mistral/mistral-large-latest"

    data class ReviewResult(
        val summary: String,
        val score: Int,
        val prNumber: Int,
        val prTitle: String,
    )

    suspend fun review(prNumber: Int, onMessage: (String) -> Unit): ReviewResult {
        onMessage("🔍 Проверяю PR #$prNumber в CalendarKMP...")

        // Шаг 1: Получаем информацию о PR и diff через MCP-тулы
        val prDiff = getPrDiff(prNumber)
        onMessage("📦 Получен diff: ${prDiff.lines().size} строк, ${prDiff.count { it == '\n' } + 1} строк")

        // Шаг 2: Получаем контекст из документации
        onMessage("📚 Загружаю документацию CalendarKMP...")
        val docs = getRelevantDocs(prDiff)
        if (docs.isNotBlank()) {
            onMessage("📖 Найдена документация: ${docs.take(100)}...")
        }

        // Шаг 3: Отправляем в LLM
        onMessage("🤖 Анализирую через LLM...")
        val result = analyzeWithLLM(prNumber, prDiff, docs)

        onMessage("✅ Анализ завершён! Оценка: ${result.score}/100")
        return result
    }

    private suspend fun getPrDiff(prNumber: Int): String = withContext(Dispatchers.IO) {
        GitHubMcpTools.executeTool("github_get_pr", mapOf("pr_number" to prNumber.toString()))
    }

    private suspend fun getRelevantDocs(diff: String): String = withContext(Dispatchers.IO) {
        val diffLower = diff.lowercase()
        val topics = mutableSetOf<String>()

        if ("auth" in diffLower) topics.add("Auth")
        if ("calendar" in diffLower) topics.add("Calendar")
        if ("viewmodel" in diffLower) topics.add("ViewModel")
        if ("repository" in diffLower) topics.add("Repository")
        if ("usecase" in diffLower || "use_case" in diffLower) topics.add("UseCase")
        if ("navigation" in diffLower) topics.add("Navigation")

        val context = StringBuilder()
        // Загружаем общую архитектурную документацию один раз
        try {
            val docs = GitHubMcpTools.executeTool("github_list_docs", emptyMap())
            if (!docs.contains("No source files found") && !docs.contains("❌")) {
                context.appendLine(docs.take(3000))
            }
        } catch (_: Exception) { }

        context.toString()
    }

    private suspend fun analyzeWithLLM(
        prNumber: Int,
        prDiff: String,
        docs: String,
    ): ReviewResult = withContext(Dispatchers.IO) {
        val systemPrompt = """
Ты — Code Reviewer для Kotlin Multiplatform проектов с Clean Architecture + MVI.
Проанализируй Pull Request #$prNumber в репозитории CalendarKMP.

Оцени:
1. Архитектурное соответствие (Clean Architecture + MVI)
2. Потенциальные баги и логические ошибки
3. Проблемы безопасности
4. Стиль кода и соответствие конвенциям
5. Производительность

Формат ответа (строго):
Общая оценка: [0-100]

Позитивные моменты:
- ...

Проблемы:
- [severity] Описание (конкретные строки кода)

Рекомендации:
- ...

Анализ сделай в 1-3 абзаца, чтобы пользователь быстро понял суть.
""".trimIndent()

        val userPrompt = """
Pull Request diff:
```
${prDiff.take(8000)}
```
${if (docs.isNotBlank()) """
Контекст из документации:
$docs
""" else ""}
Проанализируй этот PR и напиши саммари на русском языке.
""".trimIndent()

        val messages = listOf(
            ChatMessage(role = "system", content = systemPrompt),
            ChatMessage(role = "user", content = userPrompt),
        )

        val response = apiClient.sendRequest(RouterRequest(
            model = model,
            messages = messages,
            maxTokens = 4000,
            temperature = 0.3,
        ))

        val content = response.choices?.firstOrNull()?.message?.content
            ?: "❌ Не удалось получить ответ от LLM"

        val score = parseScore(content)

        ReviewResult(
            summary = content,
            score = score,
            prNumber = prNumber,
            prTitle = extractTitle(prDiff),
        )
    }

    private fun parseScore(text: String): Int {
        val match = Regex("""Общая\s*оценка[:\s]*(\d+)""").find(text)
            ?: Regex("""оценк[а-я][:\s]*(\d+)""").find(text)
            ?: Regex("""(\d+)\s*/100""").find(text)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 50
    }

    private fun extractTitle(diff: String): String {
        val match = Regex("""PR #(\d+): (.+)""").find(diff)
        return match?.groupValues?.get(2)?.trim() ?: ""
    }
}
