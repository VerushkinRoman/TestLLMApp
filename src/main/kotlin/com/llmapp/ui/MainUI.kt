package com.llmapp.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.llmapp.agent.ChatMemoryAgent
import com.llmapp.controller.ChatStorageManager
import com.llmapp.ui.components.AppNavigationRail
import com.llmapp.ui.components.ModelsPanel
import com.llmapp.ui.components.SavedChatsPanel
import com.llmapp.ui.components.SettingsPanel
import com.llmapp.ui.models.Screen
import com.llmapp.ui.screens.ChatScreen
import com.llmapp.ui.screens.DemoScreen
import com.llmapp.ui.viewmodel.ChatViewModel
import java.util.prefs.Preferences

class WindowSettings {
    companion object {
        private const val PREFS_NODE = "com.llmapp.ui"

        fun getPreferences(): Preferences {
            return Preferences.userRoot().node(PREFS_NODE)
        }

        fun saveWindowState(width: Int, height: Int, x: Int, y: Int, placement: String) {
            val prefs = getPreferences()
            prefs.putInt("window_width", width)
            prefs.putInt("window_height", height)
            prefs.putInt("window_x", x)
            prefs.putInt("window_y", y)
            prefs.put("window_placement", placement)
        }

        fun loadWindowState(): WindowStateData {
            val prefs = getPreferences()
            return WindowStateData(
                width = prefs.getInt("window_width", 1366),
                height = prefs.getInt("window_height", 768),
                x = prefs.getInt("window_x", 50),
                y = prefs.getInt("window_y", 50),
                placement = prefs.get("window_placement", WindowPlacement.Floating.name)
            )
        }
    }

    data class WindowStateData(
        val width: Int,
        val height: Int,
        val x: Int,
        val y: Int,
        val placement: String
    )
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() = application {
    val savedState = WindowSettings.loadWindowState()

    val placement = try {
        WindowPlacement.valueOf(savedState.placement)
    } catch (_: Exception) {
        WindowPlacement.Floating
    }

    val windowState = WindowState(
        placement = placement,
        isMinimized = false,
        position = WindowPosition(
            x = savedState.x.dp,
            y = savedState.y.dp
        ),
        size = DpSize(savedState.width.dp, savedState.height.dp)
    )

    val viewModel = remember { ChatViewModel() }
    val storageManager = remember { ChatStorageManager() }

    val chatMemoryService = remember {
        ChatMemoryAgent(viewModel.getChatSession(), storageManager)
    }

    LaunchedEffect(Unit) {
        chatMemoryService.loadChats()

        val (lastChatId, lastMessages) = chatMemoryService.loadLastChat()
        if (lastChatId != null && lastMessages.isNotEmpty()) {
            viewModel.messages.clear()
            viewModel.messages.addAll(lastMessages)

            val uiMessages = lastMessages.map { it.role to it.content }
            viewModel.getChatSession().rebuildHistoryFromUiMessages(uiMessages)
        }
    }

    Window(
        title = "LLM Chat - OpenRouter Client",
        onCloseRequest = {
            WindowSettings.saveWindowState(
                width = windowState.size.width.value.toInt(),
                height = windowState.size.height.value.toInt(),
                x = windowState.position.x.value.toInt(),
                y = windowState.position.y.value.toInt(),
                placement = windowState.placement.name
            )
            exitApplication()
        },
        state = windowState,
        resizable = true,
        transparent = false,
        focusable = true,
        alwaysOnTop = false
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
                MainScreen(
                    viewModel = viewModel,
                    chatMemoryService = chatMemoryService
                )
            }
        }
    }
}

@Composable
fun MainScreen(
    viewModel: ChatViewModel,
    chatMemoryService: ChatMemoryAgent
) {
    var currentScreen by remember { mutableStateOf(Screen.Chat) }
    var selectedChatId by remember { mutableStateOf<String?>(null) }

    val messages = viewModel.messages
    val isTyping by viewModel.isTyping
    val currentModel by viewModel.currentModel
    val controlEnabled by viewModel.controlEnabled
    val responseControl by viewModel.responseControl
    val availableModels by viewModel.availableModels

    val tokenStats by viewModel.tokenStatsFlow.collectAsState()
    val tokenHistory by viewModel.tokenHistoryFlow.collectAsState()
    val contextWarning by viewModel.contextWarningFlow.collectAsState()

    val savedChats by chatMemoryService.savedChats.collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        viewModel.setChatMemoryService(chatMemoryService)
    }

    LaunchedEffect(messages.size, isTyping) {
        if (messages.isNotEmpty() && currentScreen == Screen.Chat && !isTyping) {
            chatMemoryService.saveCurrentChatDebounced(messages.toList(), currentModel)
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        AppNavigationRail(
            currentScreen = currentScreen,
            onScreenSelected = { screen ->
                currentScreen = screen
                if (screen == Screen.Agents) {
                    chatMemoryService.loadChats()
                }
            },
            onClearHistory = {
                viewModel.clearHistory()
                if (messages.isNotEmpty()) {
                    chatMemoryService.saveCurrentChat(emptyList(), currentModel)
                }
                selectedChatId = null
            }
        )

        when (currentScreen) {
            Screen.Chat -> ChatScreen(
                messages = messages,
                isTyping = isTyping,
                currentModel = currentModel,
                controlEnabled = controlEnabled,
                currentAgentName = "LLM Agent",
                currentAgentIcon = "🤖",
                inputText = viewModel.draftMessage.value,
                cursorPosition = viewModel.cursorPosition.value,
                onInputTextChange = { text, cursorPos ->
                    viewModel.updateDraft(text, cursorPos)
                },
                onSendMessage = { message ->
                    if (message.isNotBlank() && !viewModel.isGenerating.value && !viewModel.isDemoRunning.value) {
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
                isGenerating = viewModel.isGenerating.value,
                isDemoRunning = viewModel.isDemoRunning.value,
                tokenStats = tokenStats,
                tokenHistory = tokenHistory,
                contextWarning = contextWarning,
                onClearTokenStats = { viewModel.clearTokenStats() }
            )

            Screen.Demo -> DemoScreen(
                onStartTokenDemo = {
                    viewModel.clearHistory()
                    viewModel.initDemoManager { message ->
                        viewModel.addDemoMessage(message)
                    }
                    viewModel.startTokenDemo()
                    currentScreen = Screen.Chat
                },
                onStartCompressionDemo = {
                    viewModel.clearHistory()
                    viewModel.initDemoManager { message ->
                        viewModel.addDemoMessage(message)
                    }
                    viewModel.startCompressionDemo()
                    currentScreen = Screen.Chat
                },
                onStartStrategyDemo = {
                    viewModel.clearHistory()
                    viewModel.initDemoManager { message ->
                        viewModel.addDemoMessage(message)
                    }
                    viewModel.startStrategyDemo()
                    currentScreen = Screen.Chat
                },
                isDemoRunning = viewModel.isDemoRunning.value,
                onClearHistory = {
                    viewModel.clearHistory()
                }
            )

            Screen.Agents -> SavedChatsPanel(
                savedChats = savedChats,
                selectedChatId = selectedChatId,
                onChatSelected = { chat ->
                    selectedChatId = chat.id
                    val loadedMessages = chatMemoryService.loadChat(chat.id)
                    viewModel.messages.clear()
                    viewModel.messages.addAll(loadedMessages)
                    viewModel.refreshTokenStats()
                    currentScreen = Screen.Chat
                },
                onDeleteChat = { chatId ->
                    chatMemoryService.deleteChat(chatId)
                    if (selectedChatId == chatId) {
                        selectedChatId = null
                        if (viewModel.messages.isEmpty()) {
                            viewModel.clearHistory()
                        }
                    }
                },
                onRenameChat = { chatId, newTitle ->
                    chatMemoryService.renameChat(chatId, newTitle)
                },
                onNewChat = {
                    chatMemoryService.createNewChat()
                    viewModel.clearHistory()
                    selectedChatId = null
                    currentScreen = Screen.Chat
                }
            )

            Screen.Models -> ModelsPanel(
                models = availableModels,
                currentModel = currentModel,
                onModelSelected = {
                    viewModel.changeModel(it)
                    viewModel.refreshTokenStats()
                }
            )

            Screen.Settings -> SettingsPanel(
                control = responseControl,
                onEnableChanged = { viewModel.setControlEnabled(it) },
                onFormatChanged = { viewModel.setFormatDescription(it) },
                onMaxTokensChanged = { viewModel.setMaxTokens(it) },
                onStopSequencesChanged = { viewModel.setStopSequences(it) },
                onTemperatureChanged = { viewModel.setTemperature(it) },
                onPresetLoaded = { viewModel.loadPreset(it) },
                onResetToDefault = { viewModel.resetToDefault() },
                compressionEnabled = viewModel.compressionEnabled.value,
                keepLastMessages = viewModel.keepLastMessages.value,
                summarizeEvery = viewModel.summarizeEvery.value,
                compressionStats = viewModel.compressionStats.value,
                onCompressionToggle = { viewModel.toggleCompression(it) },
                onKeepLastMessagesChange = {
                    viewModel.updateCompressionParams(
                        it,
                        viewModel.summarizeEvery.value
                    )
                },
                onSummarizeEveryChange = {
                    viewModel.updateCompressionParams(
                        viewModel.keepLastMessages.value,
                        it
                    )
                }
            )
        }
    }
}
