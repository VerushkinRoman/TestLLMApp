package com.llmapp.ui.viewmodel

import com.llmapp.chat.ChatSession
import com.llmapp.ui.models.ChatMessageUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID

class PrivateServerDemoRunner(
    private val chatSession: ChatSession,
    private val scope: CoroutineScope,
) {
    fun start(
        updateState: (ChatViewState.() -> ChatViewState) -> Unit,
        updateTokenStats: () -> Unit,
    ) {
        scope.launch {
            updateState { copy(isDemoRunning = true) }
            try {
                chatSession.switchPrivateMode(true)
                updateState {
                    copy(
                        usePrivateServer = true,
                        useLocalModel = false,
                        currentModel = "local",
                        messages = emptyList()
                    )
                }

                val questions = listOf(
                    "Ты локальная языковая модель. Скажи своими словами: какая ты модель и какая у тебя системная информация?",
                    "Напиши простую функцию на Python: FizzBuzz от 1 до 30.",
                    "Объясни разницу между TCP и UDP одним абзацем.",
                )

                val results = mutableListOf<Triple<String, String, Long>>()

                for (question in questions) {
                    val userMsg = ChatMessageUI(
                        id = UUID.randomUUID().toString(),
                        role = "user",
                        content = question,
                        isDemoMessage = true
                    )
                    updateState {
                        copy(
                            messages = messages + userMsg,
                            isGenerating = true,
                            isTyping = true
                        )
                    }

                    try {
                        val response = chatSession.ask(question)
                        val clean = stripMarkers(response.content)
                        val assistantMsg = ChatMessageUI(
                            id = UUID.randomUUID().toString(),
                            role = "assistant",
                            content = clean,
                            isDemoMessage = true,
                            totalTokens = response.totalTokens,
                            promptTokens = response.promptTokens,
                            completionTokens = response.completionTokens,
                            responseTimeMs = response.responseTimeMs,
                        )
                        updateState {
                            copy(
                                messages = messages + assistantMsg,
                                isGenerating = false,
                                isTyping = false
                            )
                        }
                        results.add(Triple(question, clean, response.responseTimeMs))
                    } catch (e: Exception) {
                        val errorMsg = ChatMessageUI(
                            id = UUID.randomUUID().toString(),
                            role = "assistant",
                            content = "Ошибка: ${e.message}",
                            isDemoMessage = true
                        )
                        updateState {
                            copy(
                                messages = messages + errorMsg,
                                isGenerating = false,
                                isTyping = false
                            )
                        }
                        results.add(Triple(question, "[Ошибка: ${e.message}]", 0L))
                    }
                }

                chatSession.switchPrivateMode(false)
                updateState {
                    copy(
                        usePrivateServer = false,
                        currentModel = "mistral/mistral-large-latest"
                    )
                }

                val qaText = results.withIndex().joinToString("\n\n") { (i, triple) ->
                    val (q, a, timeMs) = triple
                    "=== Вопрос ${i + 1} ===\n$q\n\n=== Ответ ${i + 1} ===\n$a\n=== Время ответа: ${timeMs}ms ==="
                }
                val evalPrompt = """
                    Оцени качество ответов приватного LLMServer на 3 вопроса ниже.
                    Сервер работает через nginx реверс-прокси на alcoserver.ru:18333, проксирует запросы к llama.cpp.
                    Рядом с каждым ответом указано время ответа в миллисекундах.

                    $qaText

                    По каждому вопросу дай оценку от 1 до 10 и краткий комментарий.
                    В конце подведи общий итог по трём критериям:
                    - Стабильность (работает ли без ошибок)
                    - Скорость (среднее время ответа, оцени по указанным_ms)
                    - Качество ответов (точность, полнота, язык)
                """.trimIndent()

                val evalUserMsg = ChatMessageUI(
                    id = UUID.randomUUID().toString(),
                    role = "user",
                    content = evalPrompt,
                    isDemoMessage = true
                )
                updateState {
                    copy(
                        messages = messages + evalUserMsg,
                        isGenerating = true,
                        isTyping = true
                    )
                }

                try {
                    val evalResponse = chatSession.ask(evalPrompt)
                    val clean = stripMarkers(evalResponse.content)
                    val evalAssistantMsg = ChatMessageUI(
                        id = UUID.randomUUID().toString(),
                        role = "assistant",
                        content = clean,
                        isDemoMessage = true,
                        totalTokens = evalResponse.totalTokens,
                        promptTokens = evalResponse.promptTokens,
                        completionTokens = evalResponse.completionTokens,
                        responseTimeMs = evalResponse.responseTimeMs,
                    )
                    updateState {
                        copy(
                            messages = messages + evalAssistantMsg,
                            isGenerating = false,
                            isTyping = false
                        )
                    }
                } catch (e: Exception) {
                    val errorMsg = ChatMessageUI(
                        id = UUID.randomUUID().toString(),
                        role = "assistant",
                        content = "Ошибка оценки: ${e.message}",
                        isDemoMessage = true
                    )
                    updateState {
                        copy(
                            messages = messages + errorMsg,
                            isGenerating = false,
                            isTyping = false
                        )
                    }
                }

                updateTokenStats()
            } finally {
                updateState { copy(isDemoRunning = false) }
            }
        }
    }

    companion object {
        private val markerRegex = Regex(
            "\\[GOAL].*?\\[/GOAL]|\\[CONSTRAINT].*?\\[/CONSTRAINT]|" +
                    "\\[DECISION].*?\\[/DECISION]|\\[CONTEXT].*?\\[/CONTEXT]|" +
                    "\\[PROGRESS_DONE].*?\\[/PROGRESS_DONE]|\\[PROGRESS_IN_PROGRESS].*?\\[/PROGRESS_IN_PROGRESS]|" +
                    "\\[PROGRESS_BLOCKED].*?\\[/PROGRESS_BLOCKED]|\\[PROGRESSDONE].*?\\[/PROGRESSDONE]",
            RegexOption.DOT_MATCHES_ALL
        )

        private fun stripMarkers(content: String): String {
            return content.replace(markerRegex, "").trim()
        }
    }
}
