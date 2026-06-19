package com.llmapp.domain.usercase

import com.llmapp.chat.ChatSession
import com.llmapp.model.TokenStats
import com.llmapp.ui.models.ChatMessageUI
import com.llmapp.ui.viewmodel.ChatViewState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

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
                val response = chatSession.ask(text, isRegeneration = !addToHistory)

                val assistantMessage = ChatMessageUI(
                    id = UUID.randomUUID().toString(),
                    role = "assistant",
                    content = response.content,
                    metadata = buildMetadata(chatSession),
                    promptTokens = response.promptTokens,
                    completionTokens = response.completionTokens,
                    totalTokens = response.totalTokens,
                    responseTimeMs = response.responseTimeMs,
                    isDemoMessage = false
                )

                newState = newState.copy(
                    messages = newState.messages + assistantMessage,
                    isGenerating = false,
                    isTyping = false
                )
                onStateUpdate(newState)
                onTokenStatsUpdate()
                onResult(newState)

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

        val demoMessages = state.messages.filter { it.isDemoMessage }
        chatSession.clearHistory()

        return state.copy(
            messages = demoMessages,
            tokenStats = TokenStats(),
            tokenHistory = emptyList()
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
}
