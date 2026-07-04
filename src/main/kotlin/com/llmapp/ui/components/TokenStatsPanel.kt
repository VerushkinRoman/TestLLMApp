package com.llmapp.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llmapp.agent.TokenSnapshot
import com.llmapp.model.TokenStats

@Composable
fun TokenStatsPanel(
    stats: TokenStats,
    history: List<TokenSnapshot>,
    contextWarning: String,
    onClearHistory: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (expanded) "▼" else "▶",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "💰 Статистика токенов",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                StatusIndicator(contextWarning)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatBadge(
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    value = stats.requestCount.toString(),
                    label = "Запросов"
                )
                StatBadge(
                    icon = Icons.Default.Numbers,
                    value = stats.totalTokens.toString(),
                    label = "Всего токенов"
                )
                StatBadge(
                    icon = Icons.Default.AttachMoney,
                    value = stats.getFormattedCost(),
                    label = "Стоимость*"
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(6.dp))

                DetailStats(stats)

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "📈 История запросов:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )

                LazyColumn(
                    modifier = Modifier.heightIn(max = 150.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(history.takeLast(10).reversed()) { snapshot ->
                        HistoryItem(snapshot)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "* Примерная стоимость",
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    TextButton(
                        onClick = onClearHistory,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Сбросить", fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun StatBadge(icon: ImageVector, value: String, label: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = value,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = label,
                fontSize = 8.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DetailStats(stats: TokenStats) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        TokenProgressRow(
            label = "Prompt",
            value = stats.totalPromptTokens,
            total = stats.totalTokens.coerceAtLeast(1),
            color = MaterialTheme.colorScheme.primary
        )
        TokenProgressRow(
            label = "Completion",
            value = stats.totalCompletionTokens,
            total = stats.totalTokens.coerceAtLeast(1),
            color = MaterialTheme.colorScheme.tertiary
        )
    }
}

@Composable
fun TokenProgressRow(
    label: String,
    value: Int,
    total: Int,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 10.sp, modifier = Modifier.width(70.dp))
        androidx.compose.material3.LinearProgressIndicator(
            progress = { value.toFloat() / total },
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .padding(end = 8.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Text(
            text = when {
                value >= 1_000_000 -> "${value / 1_000_000}M"
                value >= 1_000 -> "${value / 1_000}K"
                else -> value.toString()
            },
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(40.dp)
        )
    }
}

@Composable
fun HistoryItem(snapshot: TokenSnapshot) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "#${snapshot.requestNumber}",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${snapshot.promptTokens}+${snapshot.completionTokens}",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "∑${snapshot.totalTokens}",
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${"%.4f".format(snapshot.cumulativeCost)}$",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (snapshot.contextUsagePercent > 0) {
                Text(
                    text = "${snapshot.contextUsagePercent}%",
                    fontSize = 8.sp,
                    color = when {
                        snapshot.contextUsagePercent > 80 -> Color(0xFFF44336)
                        snapshot.contextUsagePercent > 50 -> Color(0xFFFF9800)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
fun StatusIndicator(warning: String) {
    if (warning.isNotEmpty()) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = when {
                warning.contains("красный", ignoreCase = true) ||
                warning.contains("опасн", ignoreCase = true) ||
                warning.contains("критическ", ignoreCase = true) -> Color(0xFFF44336).copy(alpha = 0.2f)

                warning.contains("оранжевый", ignoreCase = true) ||
                warning.contains("вниман", ignoreCase = true) ||
                warning.contains("предупрежд", ignoreCase = true) -> Color(0xFFFF9800).copy(alpha = 0.2f)

                else -> Color(0xFF2E7D32).copy(alpha = 0.2f)
            }
        ) {
            Text(
                text = warning.take(30),
                fontSize = 8.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                color = when {
                    warning.contains("красный", ignoreCase = true) ||
                    warning.contains("опасн", ignoreCase = true) ||
                    warning.contains("критическ", ignoreCase = true) -> Color(0xFFF44336)

                    warning.contains("оранжевый", ignoreCase = true) ||
                    warning.contains("вниман", ignoreCase = true) ||
                    warning.contains("предупрежд", ignoreCase = true) -> Color(0xFFFF9800)

                    else -> Color(0xFF2E7D32)
                }
            )
        }
    }
}
