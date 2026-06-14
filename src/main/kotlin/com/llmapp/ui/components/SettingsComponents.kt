package com.llmapp.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.v2.ScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llmapp.api.ApiConfig
import com.llmapp.api.KeyUsageMonitor
import com.llmapp.model.ResponseControl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun SettingsPanel(
    control: ResponseControl,
    onEnableChanged: (Boolean) -> Unit,
    onFormatChanged: (String) -> Unit,
    onMaxTokensChanged: (Int?) -> Unit,
    onStopSequencesChanged: (List<String>?) -> Unit,
    onTemperatureChanged: (Double?) -> Unit,
    onPresetLoaded: (Int) -> Unit,
    onResetToDefault: () -> Unit,
    compressionEnabled: Boolean,
    keepLastMessages: Int,
    summarizeEvery: Int,
    compressionStats: com.llmapp.agent.CompressedChatHistory.CompressionStats?,
    onCompressionToggle: (Boolean) -> Unit,
    onKeepLastMessagesChange: (Int) -> Unit,
    onSummarizeEveryChange: (Int) -> Unit,
    onRotateToNextKey: () -> Unit,
    onResetKeyRotation: () -> Unit,
    onShowKeyStats: () -> Unit
) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Настройки",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ResponseControlCard(
                        control = control,
                        onEnableChanged = onEnableChanged,
                        onFormatChanged = onFormatChanged,
                        onMaxTokensChanged = onMaxTokensChanged,
                        onStopSequencesChanged = onStopSequencesChanged,
                        onTemperatureChanged = onTemperatureChanged,
                        onPresetLoaded = onPresetLoaded,
                        onResetToDefault = onResetToDefault
                    )

                    CompressionSettingsCard(
                        compressionEnabled = compressionEnabled,
                        keepLastMessages = keepLastMessages,
                        summarizeEvery = summarizeEvery,
                        onCompressionToggle = onCompressionToggle,
                        onKeepLastMessagesChange = onKeepLastMessagesChange,
                        onSummarizeEveryChange = onSummarizeEveryChange,
                        compressionStats = compressionStats
                    )

                    ApiKeysStatusCard()

                    ApiKeysControlCard(
                        onRotateKey = onRotateToNextKey,
                        onResetRotation = onResetKeyRotation,
                        onShowStats = onShowKeyStats
                    )

                    InfoCard(
                        title = "Model Comparison",
                        content = "Use /compare command in chat to see response comparison with different settings"
                    )

                    InfoCard(
                        title = "Tips",
                        content = """
                            • Temperature: Lower values (0.1-0.3) for consistent responses
                            • Temperature: Higher values (0.7-0.9) for creative responses
                            • Max Tokens: Limits response length
                            • Stop Sequences: Words that will stop response generation
                            • Format Description: Instructions for response formatting
                        """.trimIndent()
                    )

                    InfoCard(
                        title = "Presets Guide",
                        content = """
                            Strict: Short, format-controlled responses
                            Creative: Longer, creative responses with examples
                            Technical: Precise, code-friendly responses
                            Casual: Friendly, emoji-rich responses
                            Kotlin Dev: Professional Kotlin/Compose development assistant
                        """.trimIndent()
                    )

                    InfoCard(
                        title = "Compression Guide",
                        content = """
                            • Сжатие контекста помогает экономить токены в длинных диалогах
                            • "Хранить последних сообщений": сколько последних сообщений оставить без сжатия
                            • "Резюме каждые N сообщений": через сколько сообщений создавать summary
                            • Для моделей с большим контекстом (1M+) можно увеличить оба параметра
                        """.trimIndent()
                    )

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            VerticalScrollbar(
                modifier = Modifier.width(12.dp),
                adapter = rememberScrollbarAdapter(scrollState, coroutineScope),
                style = ScrollbarStyle(
                    minimalHeight = 60.dp,
                    thickness = 12.dp,
                    shape = MaterialTheme.shapes.small,
                    hoverDurationMillis = 300,
                    unhoverColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    hoverColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                )
            )
        }
    }
}

@Composable
fun rememberScrollbarAdapter(
    scrollState: ScrollState,
    coroutineScope: CoroutineScope
): ScrollbarAdapter {
    return remember(scrollState, coroutineScope) {
        object : ScrollbarAdapter {
            override val scrollOffset: Double
                get() = scrollState.value.toDouble()

            override val contentSize: Double
                get() = scrollState.maxValue.toDouble() + scrollState.viewportSize.toDouble()

            override val viewportSize: Double
                get() = scrollState.viewportSize.toDouble()

            override suspend fun scrollTo(scrollOffset: Double) {
                val coercedOffset = scrollOffset.coerceIn(0.0, contentSize - viewportSize)
                coroutineScope.launch {
                    scrollState.scrollTo(coercedOffset.toInt())
                }
            }
        }
    }
}

@Composable
fun ResponseControlCard(
    control: ResponseControl,
    onEnableChanged: (Boolean) -> Unit,
    onFormatChanged: (String) -> Unit,
    onMaxTokensChanged: (Int?) -> Unit,
    onStopSequencesChanged: (List<String>?) -> Unit,
    onTemperatureChanged: (Double?) -> Unit,
    onPresetLoaded: (Int) -> Unit,
    onResetToDefault: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Enable Response Control")
                Switch(
                    checked = control.enabled,
                    onCheckedChange = onEnableChanged
                )
            }

            if (control.enabled) {
                FormatDescriptionField(
                    value = control.formatDescription,
                    onValueChange = onFormatChanged
                )

                MaxTokensField(
                    value = control.maxTokens,
                    onValueChange = onMaxTokensChanged
                )

                StopSequencesField(
                    value = control.stopSequences,
                    onValueChange = onStopSequencesChanged
                )

                TemperatureField(
                    value = control.temperature,
                    onValueChange = onTemperatureChanged
                )

                PresetButtons(onPresetLoaded = onPresetLoaded)

                OutlinedButton(
                    onClick = onResetToDefault,
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Restore,
                            contentDescription = "Reset",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reset to Default")
                    }
                }
            }
        }
    }
}

@Composable
fun FormatDescriptionField(
    value: String?,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value ?: "",
        onValueChange = onValueChange,
        label = { Text("Format Description") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 3,
        maxLines = 5
    )
}

@Composable
fun MaxTokensField(
    value: Int?,
    onValueChange: (Int?) -> Unit
) {
    OutlinedTextField(
        value = value?.toString() ?: "",
        onValueChange = { text ->
            val tokens = text.toIntOrNull()
            onValueChange(tokens)
        },
        label = { Text("Max Tokens") },
        placeholder = { Text("No limit") },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun StopSequencesField(
    value: List<String>?,
    onValueChange: (List<String>?) -> Unit
) {
    OutlinedTextField(
        value = value?.joinToString(", ") ?: "",
        onValueChange = { text ->
            val stops = if (text.isBlank()) null else text.split(",").map { it.trim() }
            onValueChange(stops)
        },
        label = { Text("Stop Sequences (comma-separated)") },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun TemperatureField(
    value: Double?,
    onValueChange: (Double?) -> Unit
) {
    OutlinedTextField(
        value = value?.toString() ?: "",
        onValueChange = { text ->
            val temp = text.toDoubleOrNull()
            val clampedTemp = when {
                temp != null && temp < 0.0 -> 0.0
                temp != null && temp > 2.0 -> 2.0
                else -> temp
            }
            onValueChange(clampedTemp)
        },
        label = { Text("Temperature (0.0-2.0)") },
        placeholder = { Text("Default") },
        modifier = Modifier.fillMaxWidth(),
        supportingText = {
            Text(
                text = "Current: ${value?.toString() ?: "default"}",
                fontSize = 12.sp
            )
        }
    )
}

@Composable
fun PresetButtons(onPresetLoaded: (Int) -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Quick Presets",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val presets = listOf(
                1 to "Strict",
                2 to "Creative",
                3 to "Technical",
                4 to "Casual"
            )
            presets.forEach { (preset, name) ->
                OutlinedButton(
                    onClick = { onPresetLoaded(preset) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(name)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { onPresetLoaded(5) },
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Code,
                        contentDescription = "Kotlin Dev",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Kotlin Dev")
                }
            }
        }
    }
}

@Composable
fun InfoCard(title: String, content: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                content,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun CompressionSettingsCard(
    compressionEnabled: Boolean,
    keepLastMessages: Int,
    summarizeEvery: Int,
    onCompressionToggle: (Boolean) -> Unit,
    onKeepLastMessagesChange: (Int) -> Unit,
    onSummarizeEveryChange: (Int) -> Unit,
    compressionStats: com.llmapp.agent.CompressedChatHistory.CompressionStats?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📊 Сжатие контекста",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Switch(
                    checked = compressionEnabled,
                    onCheckedChange = onCompressionToggle
                )
            }

            if (compressionEnabled) {
                Text(
                    text = "Экономит токены, сжимая старые сообщения в краткое резюме",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Хранить последних сообщений:", fontSize = 14.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            onKeepLastMessagesChange(
                                (keepLastMessages - 1).coerceAtLeast(
                                    2
                                )
                            )
                        }) {
                            Text("-", fontSize = 20.sp)
                        }
                        Text(
                            "$keepLastMessages",
                            fontSize = 16.sp,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        IconButton(onClick = {
                            onKeepLastMessagesChange(
                                (keepLastMessages + 1).coerceAtMost(
                                    20
                                )
                            )
                        }) {
                            Text("+", fontSize = 20.sp)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Резюме каждые N сообщений:", fontSize = 14.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            onSummarizeEveryChange(
                                (summarizeEvery - 1).coerceAtLeast(
                                    3
                                )
                            )
                        }) {
                            Text("-", fontSize = 20.sp)
                        }
                        Text(
                            "$summarizeEvery",
                            fontSize = 16.sp,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                        IconButton(onClick = {
                            onSummarizeEveryChange(
                                (summarizeEvery + 1).coerceAtMost(
                                    15
                                )
                            )
                        }) {
                            Text("+", fontSize = 20.sp)
                        }
                    }
                }

                if (compressionStats != null && compressionStats.totalMessages > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "📈 Текущая эффективность:",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Text(
                        text = "Сэкономлено ~${compressionStats.tokensSaved} токенов (${
                            "%.1f".format(
                                (1 - compressionStats.compressionRatio) * 100
                            )
                        }%)",
                        fontSize = 12.sp,
                        color = Color(0xFF4CAF50)
                    )

                    LinearProgressIndicator(
                        progress = (1 - compressionStats.compressionRatio).toFloat(),
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF4CAF50)
                    )
                }
            } else {
                Text(
                    text = "Включите сжатие для экономии токенов в длинных диалогах",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ApiKeysStatusCard() {
    val keyStats by KeyUsageMonitor.keyStats.collectAsState()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "🔑 API Ключи",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            keyStats.forEach { stats ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = if (stats.isRateLimited)
                                        Color(0xFFF44336)
                                    else
                                        Color(0xFF4CAF50),
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ключ #${stats.keyIndex}")
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${stats.requestsCount} запросов",
                            fontSize = 12.sp
                        )
                        if (stats.errorsCount > 0) {
                            Text(
                                text = "${stats.errorsCount} ошибок",
                                fontSize = 10.sp,
                                color = Color(0xFFFF9800)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ApiKeysControlCard(
    onRotateKey: () -> Unit,
    onResetRotation: () -> Unit,
    onShowStats: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "🔄 Управление API ключами",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onResetRotation,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Сбросить ротацию", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onRotateKey,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Следующий ключ →", fontSize = 12.sp)
                }
            }

            OutlinedButton(
                onClick = onShowStats,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("📊 Показать статистику (в консоль)", fontSize = 12.sp)
            }

            Text(
                text = "Текущий ключ: #${ApiConfig.getCurrentKeyIndex()} из ${ApiConfig.getTotalKeysCount()}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
