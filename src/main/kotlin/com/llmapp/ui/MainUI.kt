package com.llmapp.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.llmapp.ui.components.AppNavigationRail
import com.llmapp.ui.components.ModelsPanel
import com.llmapp.ui.components.SettingsPanel
import com.llmapp.ui.models.Screen
import com.llmapp.ui.screens.ChatScreen
import com.llmapp.ui.viewmodel.ChatViewModel

fun main() = application {
    val viewModel = remember { ChatViewModel() }

    Window(
        title = "LLM Chat - OpenRouter Client",
        onCloseRequest = ::exitApplication
    ) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = Color(0xFF4CAF50),
                secondary = Color(0xFF2196F3),
                tertiary = Color(0xFF9C27B0),
                background = Color(0xFF1E1E1E),
                surface = Color(0xFF2D2D2D)
            )
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                MainScreen(viewModel)
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: ChatViewModel) {
    var currentScreen by remember { mutableStateOf(Screen.Chat) }

    val messages = viewModel.messages
    val isTyping by viewModel.isTyping
    val currentModel by viewModel.currentModel
    val controlEnabled by viewModel.controlEnabled
    val responseControl by viewModel.responseControl
    val availableModels by viewModel.availableModels

    Row(modifier = Modifier.fillMaxSize()) {
        AppNavigationRail(
            currentScreen = currentScreen,
            onScreenSelected = {
                currentScreen = it
            },
            onClearHistory = { viewModel.clearHistory() }
        )

        when (currentScreen) {
            Screen.Chat -> ChatScreen(
                messages = messages,
                isTyping = isTyping,
                currentModel = currentModel,
                controlEnabled = controlEnabled,
                inputText = viewModel.draftMessage.value,
                cursorPosition = viewModel.cursorPosition.value,
                onInputTextChange = { text, cursorPos ->
                    viewModel.updateDraft(text, cursorPos)
                },
                onSendMessage = { message ->
                    if (message.isNotBlank() && !viewModel.isGenerating.value) {
                        viewModel.sendMessage(message)
                        viewModel.updateDraft("", 0)
                    }
                },
                onRegenerateMessage = { assistantMessageId ->
                    viewModel.regenerateMessage(assistantMessageId)
                },
                onEditMessage = { messageId, newContent ->
                    viewModel.editUserMessage(messageId, newContent)
                },
                onStopGeneration = {
                    viewModel.stopGeneration()
                },
                isGenerating = viewModel.isGenerating.value
            )

            Screen.Models -> ModelsPanel(
                models = availableModels,
                currentModel = currentModel,
                onModelSelected = { viewModel.changeModel(it) }
            )

            Screen.Settings -> SettingsPanel(
                control = responseControl,
                onEnableChanged = { viewModel.setControlEnabled(it) },
                onFormatChanged = { viewModel.setFormatDescription(it) },
                onMaxTokensChanged = { viewModel.setMaxTokens(it) },
                onStopSequencesChanged = { viewModel.setStopSequences(it) },
                onTemperatureChanged = { viewModel.setTemperature(it) },
                onPresetLoaded = { viewModel.loadPreset(it) }
            )
        }
    }
}
