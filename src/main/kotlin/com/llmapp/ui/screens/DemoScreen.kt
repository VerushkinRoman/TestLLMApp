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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DemoScreen(
    onStartTokenDemo: () -> Unit,
    onStartCompressionDemo: () -> Unit,
    onStartStrategyDemo: () -> Unit,
    isDemoRunning: Boolean,
    onClearHistory: () -> Unit = {}
) {
    val demos = listOf(
        DemoItem(
            id = "token",
            title = "📊 Отслеживание токенов",
            icon = Icons.Default.BarChart,
            description = "Показывает потребление токенов в диалогах разной длины",
            features = listOf(
                "Короткий диалог",
                "Длинный диалог",
                "Доп. запросы"
            ),
            color = Color(0xFF4CAF50),
            onStart = onStartTokenDemo
        ),
        DemoItem(
            id = "compression",
            title = "🗜️ Сжатие контекста",
            icon = Icons.Default.Compress,
            description = "Сравнение работы с компрессией и без",
            features = listOf(
                "Без компрессии",
                "С компрессией",
                "Сравнение"
            ),
            color = Color(0xFF2196F3),
            onStart = onStartCompressionDemo
        ),
        DemoItem(
            id = "strategies",
            title = "🎯 Стратегии контекста",
            icon = Icons.Default.Timeline,
            description = "Сравнение 3 стратегий управления контекстом",
            features = listOf(
                "Sliding Window",
                "Sticky Facts",
                "Branching"
            ),
            color = Color(0xFF9C27B0),
            onStart = onStartStrategyDemo
        )
    )

    val gridState = rememberLazyGridState()

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

        Box(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 320.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(demos) { demo ->
                    CompactDemoCard(
                        demo = demo,
                        isRunning = isDemoRunning,
                        onStart = {
                            onClearHistory()
                            demo.onStart()
                        }
                    )
                }
            }

            VerticalScrollbar(
                modifier = Modifier.width(12.dp).align(Alignment.CenterEnd),
                adapter = rememberScrollbarAdapter(gridState),
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
fun CompactDemoCard(
    demo: DemoItem,
    isRunning: Boolean,
    onStart: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        demo.icon,
                        contentDescription = null,
                        modifier = Modifier
                            .width(28.dp)
                            .height(28.dp),
                        tint = demo.color
                    )
                    Text(
                        text = demo.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = demo.color
                    )
                }

                if (isRunning) {
                    Text(
                        text = "▶️",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(
                text = demo.description,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                demo.features.forEach { feature ->
                    CompactFeatureChip(
                        text = feature,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Кнопка запуска
            TextButton(
                onClick = onStart,
                enabled = !isRunning,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (isRunning) "Выполняется..." else "▶ Запустить",
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun CompactFeatureChip(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 10.sp,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

data class DemoItem(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val description: String,
    val features: List<String>,
    val color: Color,
    val onStart: () -> Unit
)
