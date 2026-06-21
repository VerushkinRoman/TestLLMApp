package com.llmapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llmapp.api.ApiConfig
import com.llmapp.api.KeyUsageMonitor

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
                                        Color(0xFF2E7D32),
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
