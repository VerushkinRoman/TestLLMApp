package com.llmapp.ui.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.llmapp.api.ApiConfig
import com.llmapp.chat.ChatSession
import com.llmapp.controller.PresetManager
import com.llmapp.model.freeModels
import com.llmapp.ui.models.ChatMessageUI
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel : ViewModel() {
    private val apiKey = ApiConfig.getApiKey()
    private val chatSession = ChatSession(apiKey)

    val messages = mutableStateListOf<ChatMessageUI>()
    val isTyping = mutableStateOf(false)
    val currentModel = mutableStateOf(chatSession.getCurrentModel())
    val responseControl = mutableStateOf(chatSession.getResponseControl())
    val controlEnabled = mutableStateOf(responseControl.value.enabled)
    val availableModels = mutableStateOf(freeModels)

    val draftMessage = mutableStateOf("")
    val cursorPosition = mutableStateOf(0)

    val isGenerating = mutableStateOf(false)
    private var currentGenerationJob: kotlinx.coroutines.Job? = null

    fun updateDraft(message: String, cursorPos: Int = cursorPosition.value) {
        draftMessage.value = message
        cursorPosition.value = cursorPos
    }

    fun sendMessage(userMessage: String, addToHistory: Boolean = true) {
        if (isGenerating.value) return

        if (addToHistory) {
            messages.add(
                ChatMessageUI(
                    id = UUID.randomUUID().toString(),
                    role = "user",
                    content = userMessage
                )
            )
        }

        currentGenerationJob = viewModelScope.launch {
            isGenerating.value = true
            isTyping.value = true

            try {
                val response = chatSession.ask(userMessage, isRegeneration = !addToHistory)

                val metadata = buildString {
                    val size = chatSession.getHistorySize()
                    append("Сообщений в истории: $size")
                }

                messages.add(
                    ChatMessageUI(
                        id = UUID.randomUUID().toString(),
                        role = "assistant",
                        content = response.content,
                        metadata = metadata,
                        promptTokens = response.promptTokens,
                        completionTokens = response.completionTokens,
                        totalTokens = response.totalTokens,
                        responseTimeMs = response.responseTimeMs
                    )
                )
            } catch (e: Exception) {
                messages.add(
                    ChatMessageUI(
                        id = UUID.randomUUID().toString(),
                        role = "assistant",
                        content = "Ошибка: ${e.message}",
                        metadata = "Произошла ошибка"
                    )
                )
            } finally {
                isTyping.value = false
                isGenerating.value = false
            }
        }
    }

    fun regenerateMessage(assistantMessageId: String) {
        if (isGenerating.value) return

        val assistantIndex = messages.indexOfFirst { it.id == assistantMessageId }
        if (assistantIndex == -1) return

        var userMessage: ChatMessageUI? = null
        for (i in assistantIndex - 1 downTo 0) {
            if (messages[i].role == "user") {
                userMessage = messages[i]
                break
            }
        }

        if (userMessage == null) return

        val userIndex = messages.indexOf(userMessage)
        while (messages.size > userIndex + 1) {
            messages.removeAt(messages.size - 1)
        }

        syncHistoryWithMessages()

        sendMessage(userMessage.content, addToHistory = false)
    }

    private fun syncHistoryWithMessages() {
        val uiMessages = messages.map { it.role to it.content }
        chatSession.rebuildHistoryFromUiMessages(uiMessages)
    }

    fun editUserMessage(messageId: String, newContent: String) {
        val messageIndex = messages.indexOfFirst { it.id == messageId }
        if (messageIndex == -1 || messages[messageIndex].role != "user") return

        while (messages.size > messageIndex + 1) {
            messages.removeAt(messages.size - 1)
        }

        messages[messageIndex] = messages[messageIndex].copy(content = newContent)

        syncHistoryWithMessages()

        sendMessage(newContent, addToHistory = false)
    }

    fun stopGeneration() {
        currentGenerationJob?.cancel()
        isTyping.value = false
        isGenerating.value = false
    }

    fun clearHistory() {
        chatSession.clearHistory()
        messages.clear()
    }

    fun changeModel(modelId: String) {
        chatSession.changeModel(modelId)
        currentModel.value = modelId
    }

    fun setControlEnabled(enabled: Boolean) {
        val newControl = responseControl.value.copy(enabled = enabled)
        chatSession.setResponseControl(newControl)
        responseControl.value = newControl
        controlEnabled.value = enabled
    }

    fun setFormatDescription(format: String) {
        val newControl = responseControl.value.copy(
            formatDescription = format.ifBlank { null },
            enabled = true
        )
        chatSession.setResponseControl(newControl)
        responseControl.value = newControl
        controlEnabled.value = true
    }

    fun setMaxTokens(tokens: Int?) {
        val newControl = responseControl.value.copy(
            maxTokens = tokens,
            enabled = true
        )
        chatSession.setResponseControl(newControl)
        responseControl.value = newControl
        controlEnabled.value = true
    }

    fun setStopSequences(stops: List<String>?) {
        val newControl = responseControl.value.copy(
            stopSequences = stops,
            enabled = true
        )
        chatSession.setResponseControl(newControl)
        responseControl.value = newControl
        controlEnabled.value = true
    }

    fun setTemperature(temp: Double?) {
        val newControl = responseControl.value.copy(
            temperature = temp,
            enabled = true
        )
        chatSession.setResponseControl(newControl)
        responseControl.value = newControl
        controlEnabled.value = true
    }

    fun loadPreset(presetNumber: Int) {
        val preset = PresetManager.getPreset(presetNumber)
        if (preset != null) {
            chatSession.setResponseControl(preset)
            responseControl.value = preset
            controlEnabled.value = preset.enabled
        }
    }

    fun resetToDefault() {
        val defaultControl = PresetManager.getDefaultControl()
        chatSession.setResponseControl(defaultControl)
        responseControl.value = defaultControl
        controlEnabled.value = defaultControl.enabled
    }

    fun getChatSession(): ChatSession = chatSession
}
