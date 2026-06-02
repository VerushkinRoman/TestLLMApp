package com.llmapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llmapp.model.ResponseControl

@Composable
fun SettingsPanel(
    control: ResponseControl,
    onEnableChanged: (Boolean) -> Unit,
    onFormatChanged: (String) -> Unit,
    onMaxTokensChanged: (Int?) -> Unit,
    onStopSequencesChanged: (List<String>?) -> Unit,
    onTemperatureChanged: (Double?) -> Unit,
    onPresetLoaded: (Int) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Response Control Settings",
            fontSize = 24.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        ResponseControlCard(
            control = control,
            onEnableChanged = onEnableChanged,
            onFormatChanged = onFormatChanged,
            onMaxTokensChanged = onMaxTokensChanged,
            onStopSequencesChanged = onStopSequencesChanged,
            onTemperatureChanged = onTemperatureChanged,
            onPresetLoaded = onPresetLoaded
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
            """.trimIndent()
        )

        Spacer(modifier = Modifier.height(32.dp))
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
    onPresetLoaded: (Int) -> Unit
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
                temp != null && temp > 1.0 -> 1.0
                else -> temp
            }
            onValueChange(clampedTemp)
        },
        label = { Text("Temperature (0.0-1.0)") },
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
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
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
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
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
