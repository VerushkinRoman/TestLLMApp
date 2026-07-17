package com.llmapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.llmapp.chat.ChatSession
import com.llmapp.controller.ChatStorageManager
import com.llmapp.invariants.InvariantSet
import com.llmapp.memory.UserProfile
import com.llmapp.rag.ui.IndexScreen
import com.llmapp.ui.components.AppNavigationRail
import com.llmapp.ui.components.ConstraintsEditDialog
import com.llmapp.ui.components.CreateTaskDialog
import com.llmapp.ui.components.InvariantManagerDialog
import com.llmapp.ui.components.ProfileEditDialog
import com.llmapp.ui.components.ProfileManagerDialog
import com.llmapp.ui.components.ProfileWelcomeDialog
import com.llmapp.ui.components.SavedChatsPanel
import com.llmapp.ui.components.SettingsPanel
import com.llmapp.ui.components.TransitionsDialog
import com.llmapp.ui.models.Screen
import com.llmapp.ui.screens.ChatScreen
import com.llmapp.ui.screens.DemoScreen
import com.llmapp.ui.screens.McpScreen
import com.llmapp.ui.viewmodel.ChatViewModel
import com.llmapp.ui.viewmodel.ViewEvent
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

    var chatSession: ChatSession? = null
    viewModel.handleEvent(
        ViewEvent.GetChatSession { session ->
            chatSession = session
        }
    )

    val chatMemoryService = remember {
        ChatMemoryAgent(
            chatSession ?: error("ChatSession not initialized"),
            storageManager
        )
    }

    LaunchedEffect(Unit) {
        chatMemoryService.loadChats()
        viewModel.handleEvent(ViewEvent.SetChatMemoryService(chatMemoryService))

        val (lastChatId, lastMessages) = chatMemoryService.loadLastChat()
        if (lastChatId != null && lastMessages.isNotEmpty()) {
            val nonDemoMessages = lastMessages.filter { !it.isDemoMessage }
            if (nonDemoMessages.isNotEmpty()) {
                // Добавляем сообщения в состояние через ViewEvent
                nonDemoMessages.forEach { message ->
                    viewModel.handleEvent(ViewEvent.AddMessage(message))
                }
                val uiMessages = nonDemoMessages.map { it.role to it.content }
                // Используем ViewEvent для восстановления истории
                viewModel.handleEvent(ViewEvent.RebuildHistoryFromUiMessages(uiMessages))
            }
        }
    }

    Window(
        title = "LLM Chat - KodikRouter Client",
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
                primary = Color(0xFF2E7D32),
                secondary = Color(0xFF388E3C),
                tertiary = Color(0xFF43A047),
                background = Color(0xFF1E1E1E),
                surface = Color(0xFF2D2D2D),
                primaryContainer = Color(0xFF1B5E20),
                onPrimary = Color(0xFFFFFFFF),
                secondaryContainer = Color(0xFF2E7D32),
                tertiaryContainer = Color(0xFF4CAF50),
                onSurfaceVariant = Color(0xFFA5D6A7)
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

    var showProfileDialog by remember { mutableStateOf(false) }
    var showConstraintsDialog by remember { mutableStateOf(false) }

    val state by viewModel.state.collectAsState()
    val taskState by viewModel.taskState.collectAsState()

    val savedChats by chatMemoryService.savedChats.collectAsState(initial = emptyList())

    var showInvariantManager by remember { mutableStateOf(false) }
    var invariantSets by remember { mutableStateOf(emptyList<InvariantSet>()) }
    val invariantManager = remember { com.llmapp.invariants.InvariantManager() }

    var editingProfile by remember { mutableStateOf<UserProfile?>(null) }

    // Функция-помощник для отправки событий
    fun sendEvent(event: ViewEvent) {
        viewModel.handleEvent(event)
    }

    LaunchedEffect(showInvariantManager) {
        if (showInvariantManager) {
            invariantSets = invariantManager.getAllInvariantSets()
        }
    }

    LaunchedEffect(state.messages.size, state.isTyping) {
        if (state.messages.isNotEmpty() &&
            currentScreen == Screen.Chat &&
            !state.isTyping &&
            !state.isDemoRunning
        ) {
            val nonDemoMessages = state.messages.filter { !it.isDemoMessage }.toList()
            if (nonDemoMessages.isNotEmpty()) {
                chatMemoryService.saveCurrentChatDebounced(nonDemoMessages, state.currentModel)
            }
        }
    }

    // Диалоги
    if (showProfileDialog) {
        ProfileEditDialog(
            profile = editingProfile ?: state.userProfile,
            onDismiss = {
                showProfileDialog = false
                editingProfile = null
            },
            onSave = { profile ->
                if (editingProfile != null && profile.name.isNotEmpty()) {
                    val existing = state.allProfiles.find { it.name == profile.name }
                    if (existing != null) {
                        sendEvent(ViewEvent.UpdateExistingProfile(profile))
                    } else {
                        sendEvent(ViewEvent.SwitchToProfile(profile))
                    }
                } else if (profile.name.isNotEmpty()) {
                    sendEvent(ViewEvent.SwitchToProfile(profile))
                }
                showProfileDialog = false
                editingProfile = null
            }
        )
    }

    if (showConstraintsDialog) {
        ConstraintsEditDialog(
            constraints = state.projectConstraints,
            onDismiss = { showConstraintsDialog = false },
            onSave = { constraints ->
                sendEvent(ViewEvent.UpdateProjectConstraints(constraints))
                showConstraintsDialog = false
            }
        )
    }

    if (state.showWelcomeDialog) {
        ProfileWelcomeDialog(
            onSetupProfile = {
                sendEvent(ViewEvent.DismissWelcomeDialog)
                sendEvent(ViewEvent.ToggleProfileManager)
            },
            onSkip = {
                sendEvent(ViewEvent.DismissWelcomeDialog)
            }
        )
    }

    if (state.showProfileManager) {
        ProfileManagerDialog(
            profiles = state.allProfiles,
            activeProfile = state.activeProfile,
            onSelectProfile = { profile ->
                sendEvent(ViewEvent.SwitchToProfile(profile))
                sendEvent(ViewEvent.DismissProfileManager)
            },
            onDeleteProfile = { name ->
                sendEvent(ViewEvent.DeleteProfile(name))
            },
            onEditProfile = { profile ->
                editingProfile = profile
                sendEvent(ViewEvent.DismissProfileManager)
                showProfileDialog = true
            },
            onCreateProfile = {
                editingProfile = UserProfile()
                sendEvent(ViewEvent.DismissProfileManager)
                showProfileDialog = true
            },
            onLoadPreset = { preset ->
                sendEvent(ViewEvent.LoadPresetProfile(preset))
                sendEvent(ViewEvent.DismissProfileManager)
            },
            onDismiss = { sendEvent(ViewEvent.DismissProfileManager) }
        )
    }

    if (state.showCreateTaskDialog) {
        CreateTaskDialog(
            onDismiss = { sendEvent(ViewEvent.ToggleCreateTaskDialog) },
            onCreate = { name, description ->
                sendEvent(ViewEvent.CreateTask(name, description))
            }
        )
    }

    // GitHub Token Dialog
    if (state.showGitHubTokenDialog) {
        AlertDialog(
            onDismissRequest = { sendEvent(ViewEvent.DismissGitHubTokenDialog) },
            title = { Text("GitHub Token", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Репозиторий приватный. Введите GitHub Personal Access Token с правами 'repo'.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Токен уже открыт в браузере. Создайте его и вставьте сюда.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedTextField(
                        value = state.githubTokenInput,
                        onValueChange = { sendEvent(ViewEvent.UpdateGitHubTokenInput(it)) },
                        label = { Text("GitHub Token") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        sendEvent(ViewEvent.ClearHistory)
                        sendEvent(ViewEvent.InitDemoManager { message ->
                            sendEvent(ViewEvent.AddDemoMessage(message))
                        })
                        sendEvent(ViewEvent.StartProjectDemo(token = state.githubTokenInput))
                        currentScreen = Screen.Chat
                    },
                    enabled = state.githubTokenInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Text("Запустить")
                }
            },
            dismissButton = {
                TextButton(onClick = { sendEvent(ViewEvent.DismissGitHubTokenDialog) }) {
                    Text("Отмена")
                }
            }
        )
    }

    if (showInvariantManager) {
        InvariantManagerDialog(
            invariantSets = invariantSets,
            activeSetName = state.activeInvariantSetName,
            onSelect = { set ->
                sendEvent(ViewEvent.SelectInvariantSet(set))
                showInvariantManager = false
            },
            onDelete = { name ->
                if (invariantManager.deleteInvariantSet(name)) {
                    if (state.activeInvariantSetName == name) {
                        sendEvent(ViewEvent.ClearActiveInvariantSet)
                    }
                    invariantSets = invariantManager.getAllInvariantSets()
                    // Удаляем: sendEvent(ViewEvent.RefreshInvariantSets)
                }
            },
            onCreatePreset = { presetName ->
                val set = when (presetName.lowercase()) {
                    "android" -> com.llmapp.invariants.InvariantPresets.getAndroidKMPInvariants()
                    "web" -> com.llmapp.invariants.InvariantPresets.getWebInvariants()
                    else -> com.llmapp.invariants.InvariantPresets.getBaseInvariants()
                }

                invariantManager.deleteInvariantSet(set.name)
                invariantManager.saveInvariantSet(set)

                invariantSets = invariantManager.getAllInvariantSets()
                // Удаляем: sendEvent(ViewEvent.RefreshInvariantSets)
                sendEvent(ViewEvent.SelectInvariantSet(set))
            },
            onDismiss = { showInvariantManager = false }
        )
    }

    if (state.showTransitionsDialog) {
        TransitionsDialog(
            currentPhase = taskState?.phase ?: com.llmapp.state.TaskPhase.INIT,
            availableTransitions = state.availableTransitions,
            onTransition = { phase ->
                sendEvent(ViewEvent.SafeTransitionTo(phase))
            },
            onDismiss = { sendEvent(ViewEvent.DismissTransitionsDialog) }
        )
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
                sendEvent(ViewEvent.ClearHistory)
                if (state.messages.isEmpty()) {
                    chatMemoryService.saveCurrentChat(emptyList(), state.currentModel)
                }
                selectedChatId = null
            }
        )

        when (currentScreen) {
            Screen.Chat -> ChatScreen(
                viewState = state,
                onEvent = { event -> viewModel.handleEvent(event) }
            )

            Screen.Demo -> DemoScreen(
                onStartProjectDemo = {
                    sendEvent(ViewEvent.ClearHistory)
                    sendEvent(ViewEvent.InitDemoManager { message ->
                        sendEvent(ViewEvent.AddDemoMessage(message))
                    })
                    sendEvent(ViewEvent.StartProjectDemo())
                    currentScreen = Screen.Chat
                },
                onStartPRReview = { prNumber ->
                    sendEvent(ViewEvent.ClearHistory)
                    sendEvent(ViewEvent.InitDemoManager { message ->
                        sendEvent(ViewEvent.AddDemoMessage(message))
                    })
                    sendEvent(ViewEvent.StartPRReview(prNumber))
                    currentScreen = Screen.Chat
                },
                onStartPRReviewAgent = { prNumber ->
                    sendEvent(ViewEvent.ClearHistory)
                    sendEvent(ViewEvent.InitDemoManager { message ->
                        sendEvent(ViewEvent.AddDemoMessage(message))
                    })
                    sendEvent(ViewEvent.StartPRReviewAgent(prNumber))
                    currentScreen = Screen.Chat
                },
                onStartFileAssistant = {
                    sendEvent(ViewEvent.ClearHistory)
                    sendEvent(ViewEvent.InitDemoManager { message ->
                        sendEvent(ViewEvent.AddDemoMessage(message))
                    })
                    sendEvent(ViewEvent.StartFileAssistantDemo)
                    currentScreen = Screen.Chat
                },
                isDemoRunning = state.isDemoRunning,
                currentDemoName = viewModel.demoManagerCurrentDemo.value?.displayName,
                demoProgress = viewModel.demoManagerProgress.value,
                onCancelDemo = {
                    sendEvent(ViewEvent.CancelDemo)
                },
                onClearHistory = {
                    sendEvent(ViewEvent.ClearHistory)
                }
            )

            Screen.Agents -> SavedChatsPanel(
                savedChats = savedChats,
                selectedChatId = selectedChatId,
                onChatSelected = { chat ->
                    selectedChatId = chat.id
                    val loadedMessages = chatMemoryService.loadChat(chat.id)
                    // Очищаем и добавляем загруженные сообщения
                    sendEvent(ViewEvent.ClearHistory)
                    loadedMessages.forEach { message ->
                        sendEvent(ViewEvent.AddMessage(message))
                    }
                    sendEvent(ViewEvent.RefreshTokenStats)
                    currentScreen = Screen.Chat
                },
                onDeleteChat = { chatId ->
                    chatMemoryService.deleteChat(chatId)
                    if (selectedChatId == chatId) {
                        selectedChatId = null
                        if (state.messages.isEmpty()) {
                            sendEvent(ViewEvent.ClearHistory)
                        }
                    }
                },
                onRenameChat = { chatId, newTitle ->
                    chatMemoryService.renameChat(chatId, newTitle)
                },
                onNewChat = {
                    chatMemoryService.createNewChat()
                    sendEvent(ViewEvent.ClearHistory)
                    selectedChatId = null
                    currentScreen = Screen.Chat
                }
            )

            Screen.Models -> PlaceholderScreen(
                "Модели",
                "Выбор модели отключен (используется серверная конфигурация)"
            )

            Screen.Mcp -> McpScreen()

            Screen.Index -> IndexScreen()

            Screen.Settings -> SettingsPanel(
                control = state.responseControl,
                onEnableChanged = { sendEvent(ViewEvent.SetControlEnabled(it)) },
                onFormatChanged = { sendEvent(ViewEvent.SetFormatDescription(it)) },
                onMaxTokensChanged = { sendEvent(ViewEvent.SetMaxTokens(it)) },
                onStopSequencesChanged = { sendEvent(ViewEvent.SetStopSequences(it)) },
                onTemperatureChanged = { sendEvent(ViewEvent.SetTemperature(it)) },
                onPresetLoaded = { sendEvent(ViewEvent.LoadPreset(it)) },
                onResetToDefault = { sendEvent(ViewEvent.ResetToDefault) },
                compressionEnabled = state.compressionEnabled,
                keepLastMessages = state.keepLastMessages,
                compressAfterTokens = state.compressAfterTokens,
                compressionStats = state.compressionStats,
                onCompressionToggle = { sendEvent(ViewEvent.ToggleCompression(it)) },
                onKeepLastMessagesChange = {
                    sendEvent(ViewEvent.UpdateCompressionParams(it, state.compressAfterTokens))
                },
                onCompressAfterTokensChange = {
                    sendEvent(ViewEvent.UpdateCompressionParams(state.keepLastMessages, it))
                },
            )
        }
    }
}

@Composable
fun PlaceholderScreen(title: String, message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
