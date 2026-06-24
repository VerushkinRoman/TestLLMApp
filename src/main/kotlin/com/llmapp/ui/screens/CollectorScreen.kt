package com.llmapp.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llmapp.ui.components.CollectorPanel
import com.llmapp.ui.viewmodel.ViewEvent

@Composable
fun CollectorScreen(
    isRunning: Boolean,
    intervalMinutes: Double,
    log: List<String>,
    summary: String?,
    onEvent: (ViewEvent) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        Text(
            text = "Периодический сбор матчей",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Автоматический сбор данных о матчах ЧМ-2026 через MCP с сохранением в JSON",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        CollectorPanel(
            isRunning = isRunning,
            intervalMinutes = intervalMinutes,
            log = log,
            summary = summary,
            onStart = { interval -> onEvent(ViewEvent.StartCollector(interval)) },
            onStop = { onEvent(ViewEvent.StopCollector) },
            onCollectNow = { onEvent(ViewEvent.CollectNow) },
            onClearLog = { onEvent(ViewEvent.ClearCollectorLog) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Как это работает",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "1. Нажмите «Запустить» для начала периодического сбора данных\n" +
                            "2. Каждые N минут система запрашивает get_games через MCP\n" +
                            "3. Данные сохраняются в ~/.llm_chat_app/match_collector/snapshots/\n" +
                            "4. Генерируется сводка с результатами, бомбардирами и изменениями\n" +
                            "5. Нажмите «Собрать сейчас» для внеочередного сбора",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
