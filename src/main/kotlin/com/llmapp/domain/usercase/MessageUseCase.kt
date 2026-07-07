package com.llmapp.domain.usercase

import com.llmapp.chat.ChatSession
import com.llmapp.memory.TaskMemory
import com.llmapp.memory.TaskMemoryTracker
import com.llmapp.model.TokenStats
import com.llmapp.ui.models.ChatMessageUI
import com.llmapp.ui.viewmodel.ChatViewState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

class MessageUseCase(
    private val chatSession: ChatSession,
    private val onStateUpdate: (ChatViewState) -> Unit,
    private val onTokenStatsUpdate: () -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.Main)
    private var currentGenerationJob: kotlinx.coroutines.Job? = null

    /**
     * Отправить сообщение (асинхронно)
     * ВНИМАНИЕ: Этот метод НЕ обрабатывает команды (/command).
     * Команды должны быть обработаны в ChatViewModel перед вызовом этого метода.
     */
    fun sendMessage(
        state: ChatViewState,
        text: String,
        addToHistory: Boolean = true,
        onResult: (ChatViewState) -> Unit = {}
    ) {
        if (state.isGenerating || state.isDemoRunning) {
            onResult(state)
            return
        }

        var newState = state

        // Добавляем сообщение пользователя в историю
        if (addToHistory) {
            val userMessage = ChatMessageUI(
                id = UUID.randomUUID().toString(),
                role = "user",
                content = text
            )
            newState = newState.copy(
                messages = newState.messages + userMessage,
                isGenerating = true,
                isTyping = true
            )
            onStateUpdate(newState)
        }

        // Отправляем запрос к LLM
        currentGenerationJob = scope.launch {
            try {
                chatSession.taskMemorySummary = TaskMemoryTracker.getMemory().summarize()
                val response = chatSession.ask(text, isRegeneration = !addToHistory)

                val ragSources = response.ragSources?.map { src ->
                    com.llmapp.ui.models.RagSourceUI(
                        title = src.title,
                        section = src.section,
                        score = src.score,
                    )
                }

                val cleanContent = parseMemoryMarkers(text, response.content)
                TaskMemoryTracker.processMessage()

                val hasCompression = response.compressionNotification != null
                if (hasCompression) {
                    TaskMemoryTracker.processCompressionSummary(response.compressionNotification)
                }
                val llmMemory = TaskMemoryTracker.getMemory()

                val assistantMessage = ChatMessageUI(
                    id = UUID.randomUUID().toString(),
                    role = "assistant",
                    content = cleanContent,
                    metadata = buildMetadata(chatSession),
                    promptTokens = response.promptTokens,
                    completionTokens = response.completionTokens,
                    totalTokens = response.totalTokens,
                    responseTimeMs = response.responseTimeMs,
                    isDemoMessage = false,
                    ragSources = ragSources,
                )

                var updatedMessages = newState.messages + assistantMessage
                if (hasCompression) {
                    val formattedContent = TaskMemoryTracker.formatCompressionAsMarkdown()
                    val notificationMessage = ChatMessageUI(
                        id = "compression-${UUID.randomUUID()}",
                        role = "system",
                        content = formattedContent,
                        metadata = "Контекст сжат",
                        isDemoMessage = false,
                    )
                    updatedMessages = updatedMessages + notificationMessage
                }
                newState = newState.copy(
                    messages = updatedMessages,
                    isGenerating = false,
                    isTyping = false,
                    taskMemory = llmMemory,
                )
                onStateUpdate(newState)
                onTokenStatsUpdate()
                onResult(newState)

                if (hasCompression) {
                    delay(10.seconds)
                }

            } catch (e: Exception) {
                val errorMessage = ChatMessageUI(
                    id = UUID.randomUUID().toString(),
                    role = "assistant",
                    content = "Ошибка: ${e.message}",
                    metadata = "Произошла ошибка"
                )
                newState = newState.copy(
                    messages = newState.messages + errorMessage,
                    isGenerating = false,
                    isTyping = false
                )
                onStateUpdate(newState)
                onResult(newState)
            }
        }
    }

    /**
     * Регенерировать сообщение (асинхронно)
     */
    fun regenerateMessage(
        state: ChatViewState,
        messageId: String,
        onResult: (ChatViewState) -> Unit = {}
    ) {
        if (state.isGenerating || state.isDemoRunning) {
            onResult(state)
            return
        }

        val assistantIndex = state.messages.indexOfFirst { it.id == messageId }
        if (assistantIndex == -1) {
            onResult(state)
            return
        }

        var userMessage: ChatMessageUI? = null
        for (i in assistantIndex - 1 downTo 0) {
            if (state.messages[i].role == "user") {
                userMessage = state.messages[i]
                break
            }
        }

        if (userMessage == null) {
            onResult(state)
            return
        }

        val userIndex = state.messages.indexOf(userMessage)
        val newMessages = state.messages.take(userIndex + 1)
        val newState = state.copy(messages = newMessages)
        onStateUpdate(newState)

        // Отправляем с обновленным состоянием
        sendMessage(newState, userMessage.content, addToHistory = false, onResult = onResult)
    }

    /**
     * Редактировать сообщение пользователя (асинхронно)
     */
    fun editUserMessage(
        state: ChatViewState,
        messageId: String,
        newContent: String,
        onResult: (ChatViewState) -> Unit = {}
    ) {
        if (state.isDemoRunning) {
            onResult(state)
            return
        }

        val messageIndex = state.messages.indexOfFirst { it.id == messageId }
        if (messageIndex == -1 || state.messages[messageIndex].role != "user") {
            onResult(state)
            return
        }

        val newMessages = state.messages.take(messageIndex + 1)
            .mapIndexed { index, msg ->
                if (index == messageIndex) msg.copy(content = newContent) else msg
            }

        val newState = state.copy(messages = newMessages)
        onStateUpdate(newState)

        // Перестраиваем историю и отправляем
        val uiMessages = newMessages.map { it.role to it.content }
        chatSession.rebuildHistoryFromUiMessages(uiMessages)

        sendMessage(newState, newContent, addToHistory = false, onResult = onResult)
    }

    /**
     * Остановить генерацию
     */
    fun stopGeneration(state: ChatViewState): ChatViewState {
        if (state.isDemoRunning) return state
        currentGenerationJob?.cancel()
        currentGenerationJob = null
        return state.copy(isTyping = false, isGenerating = false)
    }

    /**
     * Очистить историю
     */
    fun clearHistory(state: ChatViewState): ChatViewState {
        if (state.isDemoRunning) return state

        chatSession.clearHistory()
        TaskMemoryTracker.reset()

        return state.copy(
            messages = emptyList(),
            tokenStats = TokenStats(),
            tokenHistory = emptyList(),
            taskMemory = TaskMemory(),
        )
    }

    /**
     * Построить метаданные для сообщения
     */
    private fun buildMetadata(chatSession: ChatSession): String {
        return buildString {
            val size = chatSession.getHistorySize()
            append("Сообщений в истории: $size")
            if (chatSession.isCompressionEnabled()) {
                append(" • Компрессия: вкл")
            }
        }
    }

    private fun extractGoalFromUserMessage(text: String): String? {
        if (text.length < 15 || text.matches(
                Regex(
                    "(привет|здравствуй|hello|hi| help|bye|пока)\\s*",
                    RegexOption.IGNORE_CASE
                )
            )
        )
            return null
        val goalPatterns = listOf(
            Regex(
                "(?:хочу|нужно|необходимо|требуется|помоги|помогите)\\s*(.+?)(?:\\.|!|\\?|$)",
                RegexOption.IGNORE_CASE
            ),
            Regex(
                "(?:разработать|создать|написать|сделать|реализовать|настроить|установить|научиться|изучить)\\s*(.+?)(?:\\.|!|\\?|$)",
                RegexOption.IGNORE_CASE
            ),
        )
        for (pattern in goalPatterns) {
            val match = pattern.find(text)
            if (match != null) {
                val extracted = match.groupValues[1].trim()
                if (extracted.length in 11..<200) return extracted
            }
        }
        return null
    }

    private fun extractGoalFromAssistantResponse(text: String): String? {
        val patterns = listOf(
            Regex("цель[^:]*:\\s*(.+?)(?:\n|$)", RegexOption.IGNORE_CASE),
            Regex("ваша цель[^:]*[—\\-:]\\s*(.+?)(?:\n|$)", RegexOption.IGNORE_CASE),
        )
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) {
                val extracted = match.groupValues[1].trim()
                if (extracted.length in 6..<200) return extracted
            }
        }
        return null
    }

    private fun parseMemoryMarkers(userText: String, content: String): String {
        val goalRegex = Regex("\\[GOAL](.*?)\\[/GOAL]", RegexOption.DOT_MATCHES_ALL)
        val constraintRegex =
            Regex("\\[CONSTRAINT](.*?)\\[/CONSTRAINT]", RegexOption.DOT_MATCHES_ALL)
        val decisionRegex = Regex("\\[DECISION](.*?)\\[/DECISION]", RegexOption.DOT_MATCHES_ALL)
        val contextRegex = Regex("\\[CONTEXT](.*?)\\[/CONTEXT]", RegexOption.DOT_MATCHES_ALL)
        val progDoneRegex =
            Regex("\\[PROGRESS_DONE](.*?)\\[/PROGRESS_DONE]", RegexOption.DOT_MATCHES_ALL)
        val progInRegex = Regex(
            "\\[PROGRESS_IN_PROGRESS](.*?)\\[/PROGRESS_IN_PROGRESS]",
            RegexOption.DOT_MATCHES_ALL
        )
        val progBlockedRegex =
            Regex("\\[PROGRESS_BLOCKED](.*?)\\[/PROGRESS_BLOCKED]", RegexOption.DOT_MATCHES_ALL)

        val hasAnyMarker = content.contains("[GOAL]") || content.contains("[CONSTRAINT]") ||
                content.contains("[DECISION]") || content.contains("[CONTEXT]") ||
                content.contains("[PROGRESS_DONE]")
        println("🧠 parseMemoryMarkers: hasAnyMarker=$hasAnyMarker, content=${content.take(200)}...")

        val markerGoal = goalRegex.find(content)?.groupValues?.getOrNull(1)?.trim()
        val assistantFallback =
            if (markerGoal == null) extractGoalFromAssistantResponse(content) else null
        val userFallback =
            if (markerGoal == null && assistantFallback == null) extractGoalFromUserMessage(userText) else null
        val goal = markerGoal ?: assistantFallback ?: userFallback

        val constraintsAndPrefs = constraintRegex.findAll(content).mapNotNull {
            it.groupValues.getOrNull(1)?.trim().takeIf { c -> c != null && c.length > 3 }
        }.toList().takeIf { it.isNotEmpty() }

        val decisions = decisionRegex.findAll(content).mapNotNull {
            it.groupValues.getOrNull(1)?.trim().takeIf { c -> c != null && c.length > 3 }
        }.toList().takeIf { it.isNotEmpty() }

        val criticalContext = contextRegex.findAll(content).mapNotNull {
            it.groupValues.getOrNull(1)?.trim().takeIf { c -> c != null && c.length > 1 }
        }.toList().takeIf { it.isNotEmpty() }

        val progDone = progDoneRegex.findAll(content).mapNotNull {
            it.groupValues.getOrNull(1)?.trim().takeIf { c -> c != null && c.length > 3 }
        }.toList().takeIf { it.isNotEmpty() }

        val progIn = progInRegex.findAll(content).mapNotNull {
            it.groupValues.getOrNull(1)?.trim().takeIf { c -> c != null && c.length > 3 }
        }.toList().takeIf { it.isNotEmpty() }

        val progBlocked = progBlockedRegex.findAll(content).mapNotNull {
            it.groupValues.getOrNull(1)?.trim().takeIf { c -> c != null && c.length > 3 }
        }.toList().takeIf { it.isNotEmpty() }

        val allowGoalOverride = TaskMemoryTracker.isExplicitGoalChange(userText)
        println("🧠 allowGoalOverride=$allowGoalOverride (userText: ${userText.take(60)})")
        TaskMemoryTracker.processLLMResult(
            goal = goal,
            constraintsAndPrefs = constraintsAndPrefs,
            progressDone = progDone,
            progressInProgress = progIn,
            progressBlocked = progBlocked,
            decisions = decisions,
            criticalContext = criticalContext,
            allowGoalOverride = allowGoalOverride,
            replaceItems = false,
        )

        if (goal != null) println("🧠 Goal: ${goal.take(60)}")
        if (constraintsAndPrefs != null) println("🧠 Constraints: ${constraintsAndPrefs.size}")

        return content
            .replace(goalRegex, "")
            .replace(constraintRegex, "")
            .replace(decisionRegex, "")
            .replace(contextRegex, "")
            .replace(progDoneRegex, "")
            .replace(progInRegex, "")
            .replace(progBlockedRegex, "")
            .trim()
    }
}
