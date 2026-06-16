package com.llmapp.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.llmapp.memory.UserProfile
import com.llmapp.ui.models.ChatMessageUI
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.datatransfer.StringSelection
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    controlEnabled: Boolean,
    currentModel: String,
    currentAgentName: String? = null,
    currentAgentIcon: String? = null,
    memorySettings: MemorySettings = MemorySettings(),
    onMemorySettingChanged: (MemorySettings) -> Unit = {},
    onEditProfile: () -> Unit = {},
    onEditConstraints: () -> Unit = {},
    activeProfile: UserProfile = UserProfile(),
    onShowProfileManager: () -> Unit = {}
) {
    var showMemoryMenu by remember { mutableStateOf(false) }
    val buttonPosition = remember { mutableStateOf(Offset.Zero) }
    val buttonSize = remember { mutableStateOf(IntSize.Zero) }

    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("LLM Chat Assistant")
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = CircleShape,
                    color = if (controlEnabled) Color(0xFF4CAF50) else Color(0xFFF44336),
                    modifier = Modifier.size(8.dp)
                ) {}

                if (currentAgentName != null) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = currentAgentIcon ?: "🤖",
                                fontSize = 12.sp
                            )
                            Text(
                                text = currentAgentName,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        actions = {
            IconButton(
                onClick = onShowProfileManager,
                modifier = Modifier.size(36.dp)
            ) {
                Box {
                    Text(
                        text = if (activeProfile.name.isNotEmpty()) "👤" else "👤",
                        fontSize = 20.sp
                    )
                    if (activeProfile.name.isNotEmpty()) {
                        Surface(
                            modifier = Modifier
                                .size(8.dp)
                                .align(Alignment.BottomEnd),
                            shape = MaterialTheme.shapes.small,
                            color = Color(0xFF4CAF50)
                        ) {}
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .onGloballyPositioned { coordinates ->
                        buttonPosition.value = coordinates.positionInWindow()
                        buttonSize.value = coordinates.size
                    }
            ) {
                IconButton(
                    onClick = { showMemoryMenu = !showMemoryMenu },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        Icons.Default.Memory,
                        contentDescription = "Memory Settings",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            MemoryStatusChip(
                label = "STM",
                active = memorySettings.useShortTerm,
                color = Color(0xFF4CAF50)
            )

            MemoryStatusChip(
                label = "WM",
                active = memorySettings.useWorkingMemory,
                color = Color(0xFFFF9800)
            )

            MemoryStatusChip(
                label = "LTM",
                active = memorySettings.useLongTerm,
                color = Color(0xFF9C27B0)
            )

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text(
                    text = "🔑${com.llmapp.api.ApiConfig.getCurrentKeyIndex()}",
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Text(
                text = currentModel,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )

    if (showMemoryMenu) {
        Popup(
            alignment = Alignment.TopStart,
            offset = IntOffset(
                x = (buttonPosition.value.x + buttonSize.value.width).roundToInt() - 280, // 280 - ширина меню
                y = (buttonPosition.value.y + buttonSize.value.height).roundToInt()
            ),
            onDismissRequest = { showMemoryMenu = false }
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 280.dp)
                ) {
                    DropdownMenuItem(
                        text = { Text("🧠 Настройки памяти", fontWeight = FontWeight.Bold) },
                        onClick = { showMemoryMenu = false },
                        enabled = false
                    )
                    HorizontalDivider()

                    DropdownMenuItem(
                        text = {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("💬 Краткосрочная память")
                                Switch(
                                    checked = memorySettings.useShortTerm,
                                    onCheckedChange = {
                                        onMemorySettingChanged(memorySettings.copy(useShortTerm = it))
                                    }
                                )
                            }
                        },
                        onClick = { }
                    )

                    DropdownMenuItem(
                        text = {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("💼 Рабочая память")
                                Switch(
                                    checked = memorySettings.useWorkingMemory,
                                    onCheckedChange = {
                                        onMemorySettingChanged(memorySettings.copy(useWorkingMemory = it))
                                    }
                                )
                            }
                        },
                        onClick = { }
                    )

                    DropdownMenuItem(
                        text = {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("📚 Долговременная память")
                                Switch(
                                    checked = memorySettings.useLongTerm,
                                    onCheckedChange = {
                                        onMemorySettingChanged(memorySettings.copy(useLongTerm = it))
                                    }
                                )
                            }
                        },
                        onClick = { }
                    )

                    HorizontalDivider()

                    DropdownMenuItem(
                        text = {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("✏️ Редактировать профиль")
                                Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                            }
                        },
                        onClick = {
                            onEditProfile()
                            showMemoryMenu = false
                        }
                    )

                    DropdownMenuItem(
                        text = {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("🔧 Редактировать ограничения")
                                Icon(Icons.Default.Settings, null, modifier = Modifier.size(16.dp))
                            }
                        },
                        onClick = {
                            onEditConstraints()
                            showMemoryMenu = false
                        }
                    )

                    HorizontalDivider()

                    DropdownMenuItem(
                        text = {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("🗑️ Сбросить рабочую память")
                                Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                            }
                        },
                        onClick = {
                            onMemorySettingChanged(memorySettings.copy(resetWorkingMemory = true))
                            showMemoryMenu = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MemoryStatusChip(
    label: String,
    active: Boolean,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (active) color.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.padding(horizontal = 2.dp)
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) color else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

data class MemorySettings(
    val useShortTerm: Boolean = true,
    val useWorkingMemory: Boolean = true,
    val useLongTerm: Boolean = true,
    val resetWorkingMemory: Boolean = false
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MessageBubble(
    message: ChatMessageUI,
    currentModel: String,
    onRegenerate: (() -> Unit)? = null,
    onEdit: ((String) -> Unit)? = null,
    isRegenerating: Boolean = false,
    isDemoRunning: Boolean = false
) {
    val isUser = message.role == "user"
    val clipboard = LocalClipboard.current
    var showCopiedTooltip by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var isEditing by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf(message.content) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isEditing) {
        if (isEditing) focusRequester.requestFocus()
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 1.dp,
            modifier = Modifier.widthIn(max = 1200.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUser) "You" else "Assistant",
                        fontSize = 12.sp,
                        color = if (isUser)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (isEditing && isUser && onEdit != null) {
                    Column {
                        OutlinedTextField(
                            value = editText,
                            onValueChange = { editText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            minLines = 2,
                            maxLines = 10
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    isEditing = false
                                    editText = message.content
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = {
                                    onEdit.invoke(editText)
                                    isEditing = false
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Save")
                            }
                        }
                    }
                } else {
                    if (isUser) {
                        SelectionContainer {
                            Text(
                                text = message.content,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        FormattedMessage(message.content)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                val transferable = StringSelection(message.content)
                                val clipEntry = ClipEntry(transferable)
                                clipboard.setClipEntry(clipEntry)
                                showCopiedTooltip = true
                                delay(1.5.seconds)
                                showCopiedTooltip = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = true,  // Всегда активна
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy")
                            if (showCopiedTooltip) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "✓",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    if (isUser && onEdit != null) {
                        Button(
                            onClick = { isEditing = true },
                            modifier = Modifier.weight(1f),
                            enabled = !isDemoRunning,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDemoRunning)
                                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                                else
                                    MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = if (isDemoRunning)
                                    MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.5f)
                                else
                                    MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edit")
                            }
                        }
                    }

                    if (!isUser && onRegenerate != null) {
                        Button(
                            onClick = onRegenerate,
                            modifier = Modifier.weight(1f),
                            enabled = !isRegenerating && !isDemoRunning,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDemoRunning)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else
                                    MaterialTheme.colorScheme.primaryContainer,
                                contentColor = if (isDemoRunning)
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                                else
                                    MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (isRegenerating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Regenerating...")
                                } else {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = "Regenerate",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Regenerate")
                                }
                            }
                        }
                    }
                }

                if (!isUser) {
                    Spacer(modifier = Modifier.height(8.dp))

                    val infoItems = mutableListOf<String>()
                    var tokensText: String?
                    var timeText: String?

                    message.metadata?.let { infoItems.add(it) }

                    if (message.totalTokens != null) {
                        tokensText =
                            if (message.promptTokens != null && message.completionTokens != null) {
                                "🔢 ${message.totalTokens} (↑${message.promptTokens}/↓${message.completionTokens})"
                            } else {
                                "🔢 ${message.totalTokens}"
                            }
                        infoItems.add(tokensText)
                    }

                    message.getFormattedResponseTime()?.let { time ->
                        val speedIcon = when {
                            message.responseTimeMs!! < 10.seconds.inWholeMilliseconds -> "🚀"
                            message.responseTimeMs < 20.seconds.inWholeMilliseconds -> "⚡"
                            else -> "🐢"
                        }
                        timeText = "$speedIcon $time"
                        infoItems.add(timeText)
                    }

                    if (infoItems.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = infoItems.joinToString(" • "),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.weight(1f)
                            )

                            CopyMetricsButton(
                                modelName = currentModel,
                                responseTimeMs = message.responseTimeMs,
                                promptTokens = message.promptTokens,
                                completionTokens = message.completionTokens,
                                totalTokens = message.totalTokens,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CopyMetricsButton(
    modelName: String,
    responseTimeMs: Long?,
    promptTokens: Int?,
    completionTokens: Int?,
    totalTokens: Int?,
) {
    val clipboard = LocalClipboard.current
    var showCopied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val copyText = buildString {
        append("Модель: $modelName")

        responseTimeMs?.let { ms ->
            val timeSec = ms / 1000.0
            append(" | Время: ${String.format("%.2f", timeSec)}с")
        }

        promptTokens?.let { append(" | Prompt: $it") }
        completionTokens?.let { append(" | Completion: $it") }
        totalTokens?.let { append(" | Total: $it") }
    }

    IconButton(
        onClick = {
            scope.launch {
                val transferable = StringSelection(copyText)
                val clipEntry = ClipEntry(transferable)
                clipboard.setClipEntry(clipEntry)
                showCopied = true
                delay(1.seconds)
                showCopied = false
            }
        },
        modifier = Modifier.size(20.dp),
    ) {
        Icon(
            if (showCopied) Icons.Default.Check else Icons.Default.ContentCopy,
            contentDescription = if (showCopied) "Copied!" else "Copy metrics",
            modifier = Modifier.size(14.dp),
            tint = if (showCopied)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")

    val dots = listOf(0, 1, 2)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 140.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Thinking",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )

                dots.forEach { index ->
                    val delay = index * 200

                    val scale by infiniteTransition.animateFloat(
                        initialValue = 0.5f,
                        targetValue = 1.2f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(
                                durationMillis = 600,
                                easing = FastOutSlowInEasing
                            ),
                            repeatMode = RepeatMode.Reverse,
                            initialStartOffset = StartOffset(delay)
                        ),
                        label = "scale_$index"
                    )

                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(
                                durationMillis = 600,
                                easing = FastOutSlowInEasing
                            ),
                            repeatMode = RepeatMode.Reverse,
                            initialStartOffset = StartOffset(delay)
                        ),
                        label = "alpha_$index"
                    )

                    Box(
                        modifier = Modifier
                            .size(6.dp * scale)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                            )
                    )
                }
            }
        }
    }
}

@Composable
fun MessageInput(
    inputText: String,
    cursorPosition: Int,
    onInputChange: (String, Int) -> Unit,
    onSendMessage: () -> Unit,
    onStopGeneration: (() -> Unit)? = null,
    isGenerating: Boolean = false,
    isDemoRunning: Boolean = false,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isDemoRunning) {
                OutlinedTextField(
                    value = "🔬 Демонстрация запущена... Ожидайте завершения",
                    onValueChange = {},
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (focusRequester != null) Modifier.focusRequester(focusRequester)
                            else Modifier
                        ),
                    placeholder = { Text("Демонстрация запущена...") },
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        focusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(24.dp),
                    readOnly = true,
                    enabled = false
                )

                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                var textFieldValue by remember(inputText) {
                    mutableStateOf(
                        TextFieldValue(
                            text = inputText,
                            selection = TextRange(cursorPosition)
                        )
                    )
                }

                LaunchedEffect(inputText) {
                    if (textFieldValue.text != inputText) {
                        textFieldValue = TextFieldValue(
                            text = inputText,
                            selection = TextRange(cursorPosition)
                        )
                    }
                }

                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { newValue ->
                        if (!isGenerating) {
                            textFieldValue = newValue
                            onInputChange(newValue.text, newValue.selection.start)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (focusRequester != null) Modifier.focusRequester(focusRequester)
                            else Modifier
                        )
                        .onKeyEvent { keyEvent ->
                            when (keyEvent.key) {
                                Key.Enter if !keyEvent.isCtrlPressed && !isGenerating -> {
                                    onSendMessage()
                                    true
                                }

                                Key.Enter if keyEvent.isCtrlPressed -> {
                                    if (!isGenerating) {
                                        val newValue = textFieldValue.copy(
                                            text = textFieldValue.text + "\n",
                                            selection = TextRange(textFieldValue.text.length + 1)
                                        )
                                        textFieldValue = newValue
                                        onInputChange(newValue.text, newValue.selection.start)
                                    }
                                    true
                                }

                                else -> false
                            }
                        },
                    placeholder = { Text("Type your message...") },
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (!isGenerating)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        unfocusedBorderColor = if (!isGenerating)
                            MaterialTheme.colorScheme.outline
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        focusedTextColor = if (!isGenerating)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        unfocusedTextColor = if (!isGenerating)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(
                        imeAction = if (isGenerating) ImeAction.None else ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (!isGenerating) {
                                onSendMessage()
                            }
                        }
                    ),
                    singleLine = false,
                    maxLines = 5,
                    readOnly = isGenerating
                )

                if (isGenerating && onStopGeneration != null) {
                    Surface(
                        modifier = Modifier
                            .size(48.dp)
                            .clickable(onClick = onStopGeneration),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.error,
                        shadowElevation = 6.dp
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(
                                        color = Color.White,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                            )
                        }
                    }
                } else {
                    Surface(
                        modifier = Modifier
                            .size(48.dp)
                            .clickable(enabled = !isGenerating) {
                                if (!isGenerating) {
                                    onSendMessage()
                                }
                            },
                        shape = CircleShape,
                        color = if (!isGenerating)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        shadowElevation = if (!isGenerating) 6.dp else 0.dp
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                "Send",
                                modifier = Modifier.size(24.dp),
                                tint = if (!isGenerating)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}
