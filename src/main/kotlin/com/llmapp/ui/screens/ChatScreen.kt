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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llmapp.agent.TokenSnapshot
import com.llmapp.memory.ProjectConstraints
import com.llmapp.memory.UserProfile
import com.llmapp.model.TokenStats
import com.llmapp.ui.components.ChatTopBar
import com.llmapp.ui.components.ConstraintsEditDialog
import com.llmapp.ui.components.MemorySettings
import com.llmapp.ui.components.MessageBubble
import com.llmapp.ui.components.MessageInput
import com.llmapp.ui.components.ProfileEditDialog
import com.llmapp.ui.components.TokenStatsPanel
import com.llmapp.ui.components.TypingIndicator
import com.llmapp.ui.models.ChatMessageUI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    messages: List<ChatMessageUI>,
    isTyping: Boolean,
    currentModel: String,
    controlEnabled: Boolean,
    currentAgentName: String? = null,
    currentAgentIcon: String? = null,
    inputText: String,
    cursorPosition: Int,
    onInputTextChange: (String, Int) -> Unit,
    onSendMessage: (String) -> Unit,
    onRegenerateMessage: ((String) -> Unit)? = null,
    onEditMessage: ((String, String) -> Unit)? = null,
    onStopGeneration: (() -> Unit)? = null,
    isGenerating: Boolean = false,
    isDemoRunning: Boolean = false,
    tokenStats: TokenStats,
    tokenHistory: List<TokenSnapshot>,
    contextWarning: String,
    onClearTokenStats: () -> Unit,
    memorySettings: MemorySettings = MemorySettings(),
    onMemorySettingChanged: (MemorySettings) -> Unit = {},
    userProfile: UserProfile = UserProfile(),
    projectConstraints: ProjectConstraints = ProjectConstraints(),
    onUpdateProfile: (UserProfile) -> Unit = {},
    onUpdateConstraints: (ProjectConstraints) -> Unit = {},
    onResetWorkingMemory: () -> Unit = {}
) {
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showConstraintsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ChatTopBar(
            controlEnabled = controlEnabled,
            currentModel = currentModel,
            currentAgentName = currentAgentName,
            currentAgentIcon = currentAgentIcon,
            memorySettings = memorySettings,
            onMemorySettingChanged = { settings ->
                onMemorySettingChanged(settings)
                if (settings.resetWorkingMemory) {
                    onResetWorkingMemory()
                }
            },
            onEditProfile = { showProfileDialog = true },
            onEditConstraints = { showConstraintsDialog = true }
        )

        TokenStatsPanel(
            stats = tokenStats,
            history = tokenHistory,
            contextWarning = contextWarning,
            onClearHistory = onClearTokenStats
        )

        MemoryLayersIndicator(
            useShortTerm = memorySettings.useShortTerm,
            useWorkingMemory = memorySettings.useWorkingMemory,
            useLongTerm = memorySettings.useLongTerm
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
                        items = messages,
                        key = { it.id }
                    ) { message ->
                        MessageBubble(
                            message = message,
                            currentModel = currentModel,
                            onRegenerate = if (message.role == "assistant" && onRegenerateMessage != null) {
                                { onRegenerateMessage(message.id) }
                            } else null,
                            onEdit = if (message.role == "user" && onEditMessage != null) {
                                { newText -> onEditMessage(message.id, newText) }
                            } else null,
                            isRegenerating = false,
                            isDemoRunning = isDemoRunning
                        )
                    }

                    if (isTyping) {
                        item(key = "typing_indicator") {
                            TypingIndicator()
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
            inputText = inputText,
            cursorPosition = cursorPosition,
            onInputChange = onInputTextChange,
            onSendMessage = {
                if (inputText.isNotBlank() && !isGenerating && !isDemoRunning) {
                    onSendMessage(inputText)
                    focusRequester.requestFocus()
                }
            },
            onStopGeneration = onStopGeneration,
            isGenerating = isGenerating,
            isDemoRunning = isDemoRunning,
            modifier = Modifier.imePadding(),
            focusRequester = focusRequester
        )
    }

    // Диалоги редактирования памяти
    if (showProfileDialog) {
        ProfileEditDialog(
            profile = userProfile,
            onDismiss = { showProfileDialog = false },
            onSave = { profile ->
                onUpdateProfile(profile)
                showProfileDialog = false
            }
        )
    }

    if (showConstraintsDialog) {
        ConstraintsEditDialog(
            constraints = projectConstraints,
            onDismiss = { showConstraintsDialog = false },
            onSave = { constraints ->
                onUpdateConstraints(constraints)
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
                color = Color(0xFF4CAF50)
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
                color = Color(0xFF9C27B0)
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
