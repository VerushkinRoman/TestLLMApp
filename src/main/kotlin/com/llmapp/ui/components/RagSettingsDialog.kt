package com.llmapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llmapp.rag.RagMode
import com.llmapp.rag.domain.RerankerType

@Composable
fun RagSettingsDialog(
    ragEnabled: Boolean,
    ragMode: RagMode,
    rerankerType: RerankerType,
    similarityThreshold: Float,
    topKBefore: Int,
    topKAfter: Int,
    onToggleRag: (Boolean) -> Unit,
    onSetRagMode: (RagMode) -> Unit,
    onSetRerankerType: (RerankerType) -> Unit,
    onSetThreshold: (Float) -> Unit,
    onSetTopKBefore: (Int) -> Unit,
    onSetTopKAfter: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(if (ragEnabled) "📚" else "📄", fontSize = 20.sp)
                Text("Настройки RAG", fontSize = 18.sp)
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = ragEnabled,
                    onCheckedChange = onToggleRag,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Режим поиска:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    RagMode.entries.forEach { mode ->
                        FilterChip(
                            selected = ragMode == mode,
                            onClick = { onSetRagMode(mode) },
                            label = { Text(mode.label, fontSize = 11.sp) },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }

                if (ragMode != RagMode.BASIC) {
                    Text(
                        "Тип реранкера:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(
                            RerankerType.SIMILARITY_THRESHOLD,
                            RerankerType.HEURISTIC
                        ).forEach { type ->
                            FilterChip(
                                selected = rerankerType == type,
                                onClick = { onSetRerankerType(type) },
                                label = { Text(type.name, fontSize = 11.sp) },
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }

                    Text("Порог сходства: ${"%.2f".format(similarityThreshold)}", fontSize = 11.sp)
                    Slider(
                        value = similarityThreshold,
                        onValueChange = onSetThreshold,
                        valueRange = 0f..0.9f,
                        modifier = Modifier.height(24.dp)
                    )

                    Text("Top-K (до фильтрации): $topKBefore", fontSize = 11.sp)
                    Slider(
                        value = topKBefore.toFloat(),
                        onValueChange = { onSetTopKBefore(it.toInt()) },
                        valueRange = 5f..50f,
                        modifier = Modifier.height(24.dp)
                    )
                }

                Text("Top-K (после фильтрации): $topKAfter", fontSize = 11.sp)
                Slider(
                    value = topKAfter.toFloat(),
                    onValueChange = { onSetTopKAfter(it.toInt()) },
                    valueRange = 1f..20f,
                    modifier = Modifier.height(24.dp)
                )

                Spacer(Modifier.height(4.dp))

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                ) {
                    Text(
                        text = "Настройки применяются в реальном времени.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Готово") }
        }
    )
}
