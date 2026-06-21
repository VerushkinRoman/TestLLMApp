package com.llmapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
                                (keepLastMessages - 1).coerceAtLeast(2)
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
                                (keepLastMessages + 1).coerceAtMost(20)
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
                                (summarizeEvery - 1).coerceAtLeast(3)
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
                                (summarizeEvery + 1).coerceAtMost(15)
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
                        color = Color(0xFF2E7D32)
                    )

                    LinearProgressIndicator(
                        progress = { (1 - compressionStats.compressionRatio).toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF2E7D32)
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
