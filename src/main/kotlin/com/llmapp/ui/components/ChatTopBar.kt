package com.llmapp.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.llmapp.memory.UserProfile
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    controlEnabled: Boolean,
    memorySettings: MemorySettings,
    onMemorySettingChanged: (MemorySettings) -> Unit,
    onEditProfile: () -> Unit,
    onEditConstraints: () -> Unit,
    activeProfile: UserProfile,
    onShowProfileManager: () -> Unit,
    onShowInvariantManager: () -> Unit,
    onCreateTask: () -> Unit,
    dataMcpConnected: Boolean = false,
    onToggleDataMcp: () -> Unit = {},
    pipelineMcpConnected: Boolean = false,
    onTogglePipelineMcp: () -> Unit = {},
    ragEnabled: Boolean = false,
    onToggleRag: (Boolean) -> Unit = {},
    onOpenRagSettings: () -> Unit = {},
    onOpenTaskMemory: () -> Unit = {},
    useLocalModel: Boolean = false,
    onToggleLocalModel: () -> Unit = {},
) {
    var showMemoryMenu by remember { mutableStateOf(false) }
    val buttonPosition = remember { mutableStateOf(Offset.Zero) }
    val buttonSize = remember { mutableStateOf(IntSize.Zero) }

    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.heightIn(max = 48.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (controlEnabled) Color(0xFF2E7D32) else Color(0xFFF44336),
                    modifier = Modifier.size(8.dp)
                ) {}
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (controlEnabled) "Response: вкл" else "Response: выкл",
                    fontSize = 11.sp,
                    color = if (controlEnabled) Color(0xFF2E7D32) else Color(0xFFF44336),
                    maxLines = 1
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        actions = {
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onShowInvariantManager,
                modifier = Modifier.size(36.dp)
            ) {
                Text("🔒", fontSize = 20.sp)
            }

            IconButton(
                onClick = onToggleDataMcp,
                modifier = Modifier.size(36.dp)
            ) {
                Text(if (dataMcpConnected) "🗄️" else "📁", fontSize = 20.sp)
            }

            IconButton(
                onClick = onTogglePipelineMcp,
                modifier = Modifier.size(36.dp)
            ) {
                Text(if (pipelineMcpConnected) "⚙️" else "🔧", fontSize = 20.sp)
            }

            IconButton(
                onClick = onCreateTask,
                modifier = Modifier.size(36.dp)
            ) {
                Text("📋", fontSize = 20.sp)
            }

            IconButton(
                onClick = onShowProfileManager,
                modifier = Modifier.size(36.dp)
            ) {
                Box {
                    Text(
                        text = "👤",
                        fontSize = 20.sp
                    )
                    if (activeProfile.name.isNotEmpty()) {
                        Surface(
                            modifier = Modifier
                                .size(8.dp)
                                .align(Alignment.BottomEnd),
                            shape = MaterialTheme.shapes.small,
                            color = Color(0xFF2E7D32)
                        ) {}
                    }
                }
            }

            // RAG toggle
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (ragEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (ragEnabled) "📚 RAG" else "📄 RAG",
                        fontSize = 12.sp,
                        color = if (ragEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.clickable(
                            enabled = ragEnabled,
                            onClick = onOpenRagSettings
                        )
                    )
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = ragEnabled,
                        onCheckedChange = onToggleRag,
                        modifier = Modifier.size(width = 36.dp, height = 20.dp)
                    )
                }
            }

            // Local / Cloud model toggle
            Spacer(Modifier.width(12.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (useLocalModel) Color(0xFF1B5E20).copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Box(modifier = Modifier.width(92.dp)) {
                        Text(
                            text = "🖥️ Локально",
                            fontSize = 12.sp,
                            color = if (useLocalModel) Color(0xFFA5D6A7)
                            else Color.Transparent,
                            maxLines = 1
                        )
                        Text(
                            text = "☁️ Облако",
                            fontSize = 12.sp,
                            color = if (useLocalModel) Color.Transparent
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Switch(
                        checked = useLocalModel,
                        onCheckedChange = { onToggleLocalModel() },
                        modifier = Modifier.size(width = 36.dp, height = 20.dp)
                    )
                }
            }

            // Memory settings button
            IconButton(
                onClick = { showMemoryMenu = !showMemoryMenu },
                modifier = Modifier
                    .size(36.dp)
                    .onGloballyPositioned { coordinates ->
                        buttonPosition.value = coordinates.positionInWindow()
                        buttonSize.value = coordinates.size
                    }
            ) {
                Icon(
                    Icons.Default.Memory,
                    contentDescription = "Настройки памяти",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // TaskMemory button
            IconButton(
                onClick = onOpenTaskMemory,
                modifier = Modifier.size(36.dp)
            ) {
                Text("🧠", fontSize = 20.sp)
            }
        })

    if (showMemoryMenu) {
        Popup(
            alignment = Alignment.TopStart,
            offset = IntOffset(
                x = (buttonPosition.value.x + buttonSize.value.width).roundToInt() - 280,
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
                        text = {
                            Text(
                                "🧠 Настройки памяти",
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        },
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
