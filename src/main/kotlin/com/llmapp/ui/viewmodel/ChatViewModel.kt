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

class ChatViewModel : ViewModel() {
    private val apiKey = ApiConfig.getApiKey()
    private val chatSession = ChatSession(apiKey)

    val messages = mutableStateListOf<ChatMessageUI>()
    val isTyping = mutableStateOf(false)
    val currentModel = mutableStateOf(chatSession.getCurrentModel())
    val responseControl = mutableStateOf(chatSession.getResponseControl())
    val controlEnabled = mutableStateOf(responseControl.value.enabled)
    val availableModels = mutableStateOf(freeModels)

    fun sendMessage(userMessage: String) {
        viewModelScope.launch {
            isTyping.value = true

            messages.add(ChatMessageUI(role = "user", content = userMessage))

            try {
                val answer = chatSession.ask(userMessage)

                val metadata = buildString {
                    val size = chatSession.getHistorySize()
                    append("Messages in history: $size")
                }

                messages.add(
                    ChatMessageUI(
                        role = "assistant",
                        content = answer,
                        metadata = metadata
                    )
                )
            } catch (e: Exception) {
                messages.add(
                    ChatMessageUI(
                        role = "assistant",
                        content = "Error: ${e.message}",
                        metadata = "Error occurred"
                    )
                )
            } finally {
                isTyping.value = false
            }
        }
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
}
