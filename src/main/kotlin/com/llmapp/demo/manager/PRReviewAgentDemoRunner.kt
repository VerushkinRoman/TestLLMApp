package com.llmapp.demo.manager

import com.llmapp.pr_review.PRReviewAgent2

/**
 * Демо-раннер для ассистента ревью PR через MCP.
 * Агент сам ходит в CalendarKMP, получает diff и пишет саммари.
 */
class PRReviewAgentDemoRunner(
    onMessageAdded: (com.llmapp.ui.models.ChatMessageUI) -> Unit,
    onTypingStateChanged: (Boolean) -> Unit,
    private val prNumber: Int = 0,
) : BaseDemoRunner(onMessageAdded, onTypingStateChanged, delayMs = 0) {

    override suspend fun run() {
        addMessage("assistant", "🤖 **Ассистент ревью Pull Request**")
        addMessage("assistant", "Запрашиваю данные из CalendarKMP...")

        val agent = PRReviewAgent2()
        val resolvedPrNumber = if (prNumber > 0) prNumber else {
            addMessage("assistant", "Номер PR не указан, проверяю последний открытый PR...")
            getLatestPrNumber()
        }
        val messages = mutableListOf<String>()
        val result = agent.review(
            prNumber = resolvedPrNumber,
            onMessage = { msg -> messages.add(msg) }
        )
        for (msg in messages) {
            addMessage("assistant", msg)
        }

        addMessage(
            role = "assistant",
            content = """
**📋 Результат ревью PR #${result.prNumber}: ${result.prTitle}**
**Оценка: ${result.score}/100**

---

${result.summary}
            """.trimIndent(),
            metadata = "PR Review #${result.prNumber}",
        )
    }

    private suspend fun getLatestPrNumber(): Int {
        val prs = com.llmapp.rag.data.GitHubApi.listOpenPullRequests()
        val number = prs.maxOfOrNull { it.number } ?: 0
        if (number == 0) {
            addMessage("assistant", "❌ Нет открытых PR в CalendarKMP")
        }
        return number
    }
}
