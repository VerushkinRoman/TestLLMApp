package com.llmapp.ui.screens

import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llmapp.state.TaskPhase
import com.llmapp.ui.components.ChatTopBar
import com.llmapp.ui.components.ConstraintsEditDialog
import com.llmapp.ui.components.MemorySettings
import com.llmapp.ui.components.MessageBubble
import com.llmapp.ui.components.MessageInput
import com.llmapp.ui.components.ProfileEditDialog
import com.llmapp.ui.components.RagSettingsDialog
import com.llmapp.ui.components.SnapshotDialog
import com.llmapp.ui.components.TaskMemoryDialog
import com.llmapp.ui.components.TaskStatePanel
import com.llmapp.ui.components.TokenStatsPanel
import com.llmapp.ui.components.TransitionsDialog
import com.llmapp.ui.components.TypingIndicator
import com.llmapp.ui.viewmodel.ChatViewState
import com.llmapp.ui.viewmodel.ViewEvent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewState: ChatViewState,
    onEvent: (ViewEvent) -> Unit
) {
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showConstraintsDialog by remember { mutableStateOf(false) }
    var showRagDialog by remember { mutableStateOf(false) }
    var showTaskMemoryDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewState.messages.size) {
        if (viewState.messages.isNotEmpty()) {
            listState.animateScrollToItem(viewState.messages.size - 1)
        }
    }

    LaunchedEffect(viewState.isTyping) {
        if (viewState.isTyping) {
            listState.animateScrollToItem(viewState.messages.size)
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ChatTopBar(
            controlEnabled = viewState.controlEnabled,
            memorySettings = MemorySettings(
                useShortTerm = true,
                useWorkingMemory = true,
                useLongTerm = true
            ),
            onMemorySettingChanged = { settings ->
                onEvent(ViewEvent.UpdateMemorySettings(settings))
                if (settings.resetWorkingMemory) {
                    onEvent(ViewEvent.ResetWorkingMemory)
                }
            },
            onEditProfile = { showProfileDialog = true },
            onEditConstraints = { showConstraintsDialog = true },
            activeProfile = viewState.activeProfile,
            onShowProfileManager = { onEvent(ViewEvent.ToggleProfileManager) },
            onCreateTask = { onEvent(ViewEvent.ToggleCreateTaskDialog) },
            onShowInvariantManager = { /* onEvent(ViewEvent.ShowInvariantManager) */ },
            dataMcpConnected = viewState.dataMcpConnected,
            onToggleDataMcp = {
                if (viewState.dataMcpConnected) {
                    onEvent(ViewEvent.DisconnectDataMcp)
                } else {
                    onEvent(ViewEvent.ConnectDataMcp)
                }
            },
            pipelineMcpConnected = viewState.pipelineMcpConnected,
            onTogglePipelineMcp = {
                if (viewState.pipelineMcpConnected) {
                    onEvent(ViewEvent.DisconnectPipelineMcp)
                } else {
                    onEvent(ViewEvent.ConnectPipelineMcp)
                }
            },
            ragEnabled = viewState.ragEnabled,
            onToggleRag = { onEvent(ViewEvent.ToggleRagMode(it)) },
            onOpenRagSettings = { showRagDialog = true },
            onOpenTaskMemory = { showTaskMemoryDialog = true },
        )

        viewState.taskState?.let { taskState ->
            TaskStatePanel(
                state = taskState,
                onTransition = { phase -> onEvent(ViewEvent.TransitionTo(phase)) },
                onPause = { onEvent(ViewEvent.PauseTask()) },
                onResume = { onEvent(ViewEvent.ResumeTask) },
                onBlock = { onEvent(ViewEvent.BlockTask()) },
                onUnblock = { onEvent(ViewEvent.UnblockTask) },
                onShowSnapshots = { onEvent(ViewEvent.ToggleSnapshotDialog) },
                onShowTransitions = { onEvent(ViewEvent.ShowTransitionsDialog) },
                isDemoRunning = viewState.isDemoRunning
            )
        }

        TokenStatsPanel(
            stats = viewState.tokenStats,
            history = viewState.tokenHistory,
            contextWarning = viewState.contextWarning,
            onClearHistory = { onEvent(ViewEvent.ClearTokenStats) }
        )

        Row(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize().padding(end = 8.dp)
                ) {
                    items(
                        items = viewState.messages,
                        key = { it.id }
                    ) { message ->
                        MessageBubble(
                            message = message,
                            currentModel = viewState.currentModel,
                            onRegenerate = if (message.role == "assistant") {
                                { onEvent(ViewEvent.RegenerateMessage(message.id)) }
                            } else null,
                            onEdit = if (message.role == "user") {
                                { newText ->
                                    onEvent(
                                        ViewEvent.EditUserMessage(
                                            message.id,
                                            newText
                                        )
                                    )
                                }
                            } else null,
                            isRegenerating = false,
                            isDemoRunning = viewState.isDemoRunning
                        )
                    }

                    if (viewState.isTyping) {
                        item(key = "typing_indicator") {
                            TypingIndicator()
                        }
                    }

                    if (viewState.mcpLog.isNotEmpty()) {
                        item(key = "mcp_log") {
                            McpLogBanner(logs = viewState.mcpLog.takeLast(8))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            VerticalScrollbar(
                modifier = Modifier.width(12.dp),
                adapter = rememberScrollbarAdapter(listState),
                style = ScrollbarStyle(
                    minimalHeight = 30.dp,
                    thickness = 12.dp,
                    shape = MaterialTheme.shapes.small,
                    hoverDurationMillis = 300,
                    unhoverColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    hoverColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                )
            )
        }

        MessageInput(
            inputText = viewState.draftMessage,
            cursorPosition = viewState.cursorPosition,
            onInputChange = { text, cursorPos ->
                onEvent(ViewEvent.UpdateDraft(text, cursorPos))
            },
            onSendMessage = {
                if (viewState.draftMessage.isNotBlank() && !viewState.isGenerating && !viewState.isDemoRunning) {
                    onEvent(ViewEvent.SendMessage(viewState.draftMessage))
                    onEvent(ViewEvent.UpdateDraft("", 0))
                    focusRequester.requestFocus()
                }
            },
            onStopGeneration = { onEvent(ViewEvent.StopGeneration) },
            isGenerating = viewState.isGenerating,
            isDemoRunning = viewState.isDemoRunning,
            modifier = Modifier.imePadding(),
            focusRequester = focusRequester
        )
    }

    if (viewState.showTransitionsDialog) {
        TransitionsDialog(
            currentPhase = viewState.taskState?.phase ?: TaskPhase.INIT,
            availableTransitions = viewState.availableTransitions,
            onTransition = { phase -> onEvent(ViewEvent.SafeTransitionTo(phase)) },
            onDismiss = { onEvent(ViewEvent.DismissTransitionsDialog) }
        )
    }

    if (viewState.showSnapshotDialog) {
        SnapshotDialog(
            snapshots = viewState.snapshots,
            onRestore = { id -> onEvent(ViewEvent.RestoreSnapshot(id)) },
            onCreate = { name -> onEvent(ViewEvent.CreateSnapshot(name)) },
            onDismiss = { onEvent(ViewEvent.DismissSnapshotDialog) },
        )
    }

    if (showProfileDialog) {
        ProfileEditDialog(
            profile = viewState.userProfile,
            onDismiss = { showProfileDialog = false },
            onSave = { profile ->
                onEvent(ViewEvent.UpdateUserProfile(profile))
                showProfileDialog = false
            }
        )
    }

    if (showConstraintsDialog) {
        ConstraintsEditDialog(
            constraints = viewState.projectConstraints,
            onDismiss = { showConstraintsDialog = false },
            onSave = { constraints ->
                onEvent(ViewEvent.UpdateProjectConstraints(constraints))
                showConstraintsDialog = false
            }
        )
    }

    if (showRagDialog) {
        RagSettingsDialog(
            ragEnabled = viewState.ragEnabled,
            ragMode = viewState.ragMode,
            rerankerType = viewState.rerankerType,
            similarityThreshold = viewState.similarityThreshold,
            topKBefore = viewState.topKBefore,
            topKAfter = viewState.topKAfter,
            onToggleRag = { onEvent(ViewEvent.ToggleRagMode(it)) },
            onSetRagMode = { onEvent(ViewEvent.SetRagMode(it)) },
            onSetRerankerType = { onEvent(ViewEvent.SetRerankerType(it)) },
            onSetThreshold = { onEvent(ViewEvent.SetSimilarityThreshold(it)) },
            onSetTopKBefore = { onEvent(ViewEvent.SetTopKBefore(it)) },
            onSetTopKAfter = { onEvent(ViewEvent.SetTopKAfter(it)) },
            onDismiss = { showRagDialog = false },
        )
    }

    if (showTaskMemoryDialog) {
        TaskMemoryDialog(
            memory = viewState.taskMemory,
            onDismiss = { showTaskMemoryDialog = false },
        )
    }
}

@Composable
fun McpLogBanner(logs: List<String>) {
    val displayLogs = logs.takeLast(6)
    if (displayLogs.isEmpty()) return
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            Text("🔧 MCP:", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
            displayLogs.forEach { log ->
                Text(
                    log.take(150),
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1
                )
            }
        }
    }
}
