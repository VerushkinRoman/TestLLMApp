// src/main/kotlin/com/llmapp/ui/screens/ChatScreen.kt

package com.llmapp.ui.screens

import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llmapp.rag.RagMode
import com.llmapp.rag.domain.RerankerType
import com.llmapp.state.TaskPhase
import com.llmapp.ui.components.ChatTopBar
import com.llmapp.ui.components.ConstraintsEditDialog
import com.llmapp.ui.components.MemorySettings
import com.llmapp.ui.components.MessageBubble
import com.llmapp.ui.components.MessageInput
import com.llmapp.ui.components.ProfileEditDialog
import com.llmapp.ui.components.SnapshotDialog
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

    // Подписка на изменения состояния
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
            currentModel = viewState.currentModel,
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
            }
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

        MemoryLayersIndicator(
            useShortTerm = true,
            useWorkingMemory = true,
            useLongTerm = true
        )

        RagSettingsPanel(
            ragEnabled = viewState.ragEnabled,
            ragMode = viewState.ragMode,
            rerankerType = viewState.rerankerType,
            similarityThreshold = viewState.similarityThreshold,
            topKBefore = viewState.topKBefore,
            topKAfter = viewState.topKAfter,
            expanded = viewState.ragSettingsExpanded,
            onToggle = { onEvent(ViewEvent.ToggleRagSettings) },
            onToggleRag = { onEvent(ViewEvent.ToggleRagMode(it)) },
            onSetRagMode = { onEvent(ViewEvent.SetRagMode(it)) },
            onSetRerankerType = { onEvent(ViewEvent.SetRerankerType(it)) },
            onSetThreshold = { onEvent(ViewEvent.SetSimilarityThreshold(it)) },
            onSetTopKBefore = { onEvent(ViewEvent.SetTopKBefore(it)) },
            onSetTopKAfter = { onEvent(ViewEvent.SetTopKAfter(it)) },
        )

        Row(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
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

    // Диалог управления переходами
    if (viewState.showTransitionsDialog) {
        TransitionsDialog(
            currentPhase = viewState.taskState?.phase ?: TaskPhase.INIT,
            availableTransitions = viewState.availableTransitions,
            onTransition = { phase -> onEvent(ViewEvent.SafeTransitionTo(phase)) },
            onDismiss = { onEvent(ViewEvent.DismissTransitionsDialog) }
        )
    }

    // Диалог управления снимками
    if (viewState.showSnapshotDialog) {
        SnapshotDialog(
            snapshots = viewState.snapshots,
            onRestore = { id -> onEvent(ViewEvent.RestoreSnapshot(id)) },
            onCreate = { name -> onEvent(ViewEvent.CreateSnapshot(name)) },
            onDismiss = { onEvent(ViewEvent.DismissSnapshotDialog) },
        )
    }

    // Диалоги редактирования памяти
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
}

@Composable
fun MemoryLayersIndicator(
    useShortTerm: Boolean,
    useWorkingMemory: Boolean,
    useLongTerm: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MemoryLayerChip(
                icon = "💬",
                label = "Краткосрочная",
                active = useShortTerm,
                color = Color(0xFF2E7D32)
            )
            MemoryLayerChip(
                icon = "💼",
                label = "Рабочая",
                active = useWorkingMemory,
                color = Color(0xFFFF9800)
            )
            MemoryLayerChip(
                icon = "📚",
                label = "Долговременная",
                active = useLongTerm,
                color = Color(0xFF43A047)
            )
        }
    }
}

@Composable
fun MemoryLayerChip(
    icon: String,
    label: String,
    active: Boolean,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(icon, fontSize = 12.sp)
        Text(
            label,
            fontSize = 10.sp,
            color = if (active) color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
fun RagSettingsPanel(
    ragEnabled: Boolean,
    ragMode: RagMode,
    rerankerType: RerankerType,
    similarityThreshold: Float,
    topKBefore: Int,
    topKAfter: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onToggleRag: (Boolean) -> Unit,
    onSetRagMode: (RagMode) -> Unit,
    onSetRerankerType: (RerankerType) -> Unit,
    onSetThreshold: (Float) -> Unit,
    onSetTopKBefore: (Int) -> Unit,
    onSetTopKAfter: (Int) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        color = if (ragEnabled)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(if (ragEnabled) "📚" else "📄", fontSize = 12.sp)
                    Text(
                        text = if (ragEnabled) "RAG: вкл" else "RAG: выкл",
                        fontSize = 11.sp,
                        color = if (ragEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Switch(
                        checked = ragEnabled,
                        onCheckedChange = onToggleRag,
                        modifier = Modifier.height(20.dp)
                    )
                    Text(
                        text = if (expanded) "▲" else "▼",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (expanded) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Режим поиска:",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    RagMode.entries.forEach { mode ->
                        FilterChip(
                            selected = ragMode == mode,
                            onClick = { onSetRagMode(mode) },
                            label = { Text(mode.label, fontSize = 9.sp) },
                            modifier = Modifier.height(26.dp)
                        )
                    }
                }

                if (ragMode != RagMode.BASIC) {
                    Text(
                        "Тип реранкера:",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        listOf(
                            RerankerType.SIMILARITY_THRESHOLD,
                            RerankerType.HEURISTIC
                        ).forEach { type ->
                            FilterChip(
                                selected = rerankerType == type,
                                onClick = { onSetRerankerType(type) },
                                label = { Text(type.name, fontSize = 9.sp) },
                                modifier = Modifier.height(26.dp)
                            )
                        }
                    }

                    Text("Порог сходства: ${"%.2f".format(similarityThreshold)}", fontSize = 10.sp)
                    Slider(
                        value = similarityThreshold,
                        onValueChange = onSetThreshold,
                        valueRange = 0f..0.9f,
                        modifier = Modifier.height(20.dp)
                    )

                    Text("Top-K (до/базовый): $topKBefore", fontSize = 10.sp)
                    Slider(
                        value = topKBefore.toFloat(),
                        onValueChange = { onSetTopKBefore(it.toInt()) },
                        valueRange = 5f..50f,
                        modifier = Modifier.height(20.dp)
                    )
                }

                Text("Top-K после фильтрации: $topKAfter", fontSize = 10.sp)
                Slider(
                    value = topKAfter.toFloat(),
                    onValueChange = { onSetTopKAfter(it.toInt()) },
                    valueRange = 1f..20f,
                    modifier = Modifier.height(20.dp)
                )
            }
        }
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
