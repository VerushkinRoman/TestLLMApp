package com.llmapp.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DemoScreen(
    onStartTokenDemo: () -> Unit,
    onStartCompressionDemo: () -> Unit,
    isDemoRunning: Boolean,
    onClearHistory: () -> Unit = {}
) {
    val listState = rememberLazyListState()
    var selectedDemo by remember { mutableStateOf<String?>(null) }

    val demos = listOf(
        DemoItem(
            id = "token",
            title = "📊 Демонстрация отслеживания токенов",
            icon = Icons.Default.BarChart,
            description = "Показывает, как отслеживаются токены в диалогах разной длины",
            features = listOf(
                "• Короткий диалог (3 сообщения) - базовое потребление токенов",
                "• Длинный диалог (20 сообщений) - рост контекста и стоимости",
                "• Дополнительные запросы (5 сообщений) - накопление контекста",
                "• Анализ статистики моделью с выводами"
            ),
            color = androidx.compose.ui.graphics.Color(0xFF4CAF50),
            onStart = onStartTokenDemo
        ),
        DemoItem(
            id = "compression",
            title = "🗜️ Демонстрация сжатия контекста",
            icon = Icons.Default.Compress,
            description = "Сравнивает работу агента с компрессией и без неё",
            features = listOf(
                "• Тест без компрессии - обычный агент",
                "• Тест с компрессией (keepLast=8, summarizeEvery=6)",
                "• Сравнение токенов, времени и стоимости",
                "• Адаптивная компрессия под разные модели"
            ),
            color = androidx.compose.ui.graphics.Color(0xFF2196F3),
            onStart = onStartCompressionDemo
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "🧪 Демонстрации",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Выберите демонстрацию для запуска. Во время демонстрации чат будет заблокирован.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(demos) { demo ->
                        DemoCard(
                            demo = demo,
                            isRunning = isDemoRunning && selectedDemo == demo.id,
                            onStart = {
                                selectedDemo = demo.id
                                onClearHistory()
                                demo.onStart()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            VerticalScrollbar(
                modifier = Modifier.width(12.dp),
                adapter = rememberScrollbarAdapter(listState),
                style = ScrollbarStyle(
                    minimalHeight = 30.dp,
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
fun DemoCard(
    demo: DemoItem,
    isRunning: Boolean,
    onStart: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        demo.icon,
                        contentDescription = null,
                        modifier = Modifier
                            .width(32.dp)
                            .height(32.dp),
                        tint = demo.color
                    )
                    Text(
                        text = demo.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = demo.color
                    )
                }

                if (isRunning) {
                    Text(
                        text = "▶️ ЗАПУЩЕНО...",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(
                text = demo.description,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Что включает:",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )

            demo.features.forEach { feature ->
                Text(
                    text = feature,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = onStart,
                enabled = !isRunning,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (isRunning) "Демонстрация выполняется..." else "▶ Запустить демонстрацию",
                    fontSize = 14.sp
                )
            }
        }
    }
}

data class DemoItem(
    val id: String,
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val description: String,
    val features: List<String>,
    val color: androidx.compose.ui.graphics.Color,
    val onStart: () -> Unit
)
