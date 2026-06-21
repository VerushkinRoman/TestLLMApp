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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
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
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "💰 Статистика токенов",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                StatusIndicator(contextWarning)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
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
                Spacer(modifier = Modifier.height(12.dp))

                DetailStats(stats)

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "📈 История запросов:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                LazyColumn(
                    modifier = Modifier.heightIn(max = 200.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(history.takeLast(10).reversed()) { snapshot ->
                        HistoryItem(snapshot)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onClearHistory,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Сбросить статистику", fontSize = 12.sp)
                }

                Text(
                    text = "* Примерная стоимость (как если бы модель была платной)",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun StatBadge(icon: ImageVector, value: String, label: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Column {
                Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(label, fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun DetailStats(stats: TokenStats) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ProgressBar(
            label = "Prompt токены",
            value = stats.totalPromptTokens.toDouble(),
            max = (stats.totalTokens).toDouble(),
            color = Color(0xFF2E7D32)
        )
        ProgressBar(
            label = "Completion токены",
            value = stats.totalCompletionTokens.toDouble(),
            max = stats.totalTokens.toDouble(),
            color = Color(0xFF388E3C)
        )
    }
}

@Composable
fun ProgressBar(label: String, value: Double, max: Double, color: Color) {
    val percent = if (max > 0) (value / max) * 100 else 0.0

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 11.sp)
            Text("${value.toInt()} / ${max.toInt()} (${"%.1f".format(percent)}%)", fontSize = 11.sp)
        }
        Spacer(modifier = Modifier.height(2.dp))
        LinearProgressIndicator(
            progress = { (percent / 100).toFloat() },
            modifier = Modifier.fillMaxWidth(),
            color = color,
            trackColor = MaterialTheme.colorScheme.surface,
            strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
        )
    }
}

@Composable
fun HistoryItem(snapshot: TokenSnapshot) {
    val statusColor = when {
        snapshot.contextUsagePercent > 90 -> Color(0xFFF44336)
        snapshot.contextUsagePercent > 70 -> Color(0xFFFF9800)
        else -> Color(0xFF2E7D32)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "#${snapshot.requestNumber}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${snapshot.promptTokens}→${snapshot.completionTokens}",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "${snapshot.totalTokens} токенов",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = snapshot.getFormattedCost(),
                    fontSize = 10.sp,
                    color = Color(0xFF2E7D32)
                )
                Text(
                    text = "🔋 ${"%.1f".format(snapshot.contextUsagePercent)}%",
                    fontSize = 9.sp,
                    color = statusColor
                )
            }
        }
    }
}

@Composable
fun StatusIndicator(warning: String) {
    val color = when {
        warning.contains("✅") -> Color(0xFF2E7D32)
        warning.contains("⚠️") -> Color(0xFFFF9800)
        warning.contains("🔴") -> Color(0xFFF44336)
        warning.contains("💥") -> Color(0xFF43A047)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.2f),
        modifier = Modifier.widthIn(max = 400.dp)
    ) {
        Text(
            text = warning,
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = color,
            softWrap = true,
        )
    }
}
