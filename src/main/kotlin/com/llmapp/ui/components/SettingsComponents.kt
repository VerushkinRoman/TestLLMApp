package com.llmapp.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
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
import androidx.compose.foundation.v2.ScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                        title = "Сравнение моделей",
                        content = "Используйте /compare в чате для сравнения ответов с разными настройками"
                    )

                    InfoCard(
                        title = "Советы",
                        content = """
                            • Temperature: Низкие значения (0.1-0.3) для стабильных ответов
                            • Temperature: Высокие значения (0.7-0.9) для креативных ответов
                            • Max Tokens: Ограничивает длину ответа
                            • Stop Sequences: Слова, останавливающие генерацию
                            • Format Description: Инструкции по форматированию ответа
                        """.trimIndent()
                    )

                    InfoCard(
                        title = "Гайд по пресетам",
                        content = """
                            Строгий: Короткие ответы с контролем формата
                            Креативный: Длинные креативные ответы с примерами
                            Технический: Точные ответы, удобные для кода
                            Неформальный: Дружелюбные ответы с эмодзи
                            Kotlin Dev: Профессиональный ассистент Kotlin/Compose
                        """.trimIndent()
                    )

                    InfoCard(
                        title = "Гайд по сжатию",
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
                Text("Включить управление ответами")
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
                            contentDescription = "Сброс",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Сбросить")
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
        label = { Text("Описание формата") },
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
        label = { Text("Макс. токенов") },
        placeholder = { Text("Без лимита") },
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
        label = { Text("Стоп-последовательности (через запятую)") },
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
        placeholder = { Text("По умолчанию") },
        modifier = Modifier.fillMaxWidth(),
        supportingText = {
            Text(
                text = "Текущее: ${value?.toString() ?: "по умолчанию"}",
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
            text = "Быстрые пресеты",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val presets = listOf(
                1 to "Строгий",
                2 to "Креативный",
                3 to "Технический",
                4 to "Неформальный"
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

