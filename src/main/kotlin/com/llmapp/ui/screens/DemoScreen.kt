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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DemoScreen(
    onStartTokenDemo: () -> Unit,
    onStartCompressionDemo: () -> Unit,
    onStartStrategyDemo: () -> Unit,
    onStartMemoryDemo: () -> Unit,
    onStartPersonalizationDemo: () -> Unit,
    onStartStatefulDemo: () -> Unit,
    onStartInvariantDemo: () -> Unit,
    onStartTransitionDemo: () -> Unit,
    onStartRagDemo: () -> Unit,
    isDemoRunning: Boolean,
    currentDemoName: String?,
    demoProgress: String?,
    onCancelDemo: (() -> Unit)?,
    onClearHistory: () -> Unit
) {
    val demos = listOf(
        DemoItem(
            id = "token",
            title = "📊 Отслеживание токенов",
            icon = Icons.Default.BarChart,
            description = "Показывает потребление токенов в диалогах разной длины",
            features = listOf("Короткий диалог", "Длинный диалог", "Доп. запросы"),
            color = Color(0xFF4CAF50),
            onStart = onStartTokenDemo
        ),
        DemoItem(
            id = "compression",
            title = "🗜️ Сжатие контекста",
            icon = Icons.Default.Compress,
            description = "Сравнение работы с компрессией и без",
            features = listOf("Без компрессии", "С компрессией", "Сравнение"),
            color = Color(0xFF388E3C),
            onStart = onStartCompressionDemo
        ),
        DemoItem(
            id = "strategies",
            title = "🎯 Стратегии контекста",
            icon = Icons.Default.Timeline,
            description = "Сравнение 3 стратегий управления контекстом",
            features = listOf("Скользящее окно", "Фиксация фактов", "Ветвление"),
            color = Color(0xFF43A047),
            onStart = onStartStrategyDemo
        ),
        DemoItem(
            id = "memory",
            title = "🧠 Модель памяти",
            icon = Icons.Default.Memory,
            description = "Трехслойная модель памяти: краткосрочная, рабочая, долговременная",
            features = listOf("Профиль", "Рабочая задача", "Ограничения"),
            color = Color(0xFF66BB6A),
            onStart = onStartMemoryDemo
        ),
        DemoItem(
            id = "personalization",
            title = "👤 Персонализация",
            icon = Icons.Default.Person,
            description = "Демонстрация персонализации агента под профиль пользователя",
            features = listOf("Профили", "Стили", "Ограничения"),
            color = Color(0xFF66BB6A),
            onStart = onStartPersonalizationDemo
        ),
        DemoItem(
            id = "stateful",
            title = "🧠 Stateful Agent",
            icon = Icons.Default.Memory,
            description = "Полная демонстрация агента с конечным автоматом",
            features = listOf("Состояние задачи", "Пауза", "Снимки"),
            color = Color(0xFF2E7D32),
            onStart = onStartStatefulDemo
        ),
        DemoItem(
            id = "invariants",
            title = "🔒 Инварианты",
            icon = Icons.Default.Security,
            description = "Контроль поведения агента через инварианты",
            features = listOf("Архитектура", "Стек", "Бизнес-правила"),
            color = Color(0xFF388E3C),
            onStart = onStartInvariantDemo
        ),
        DemoItem(
            id = "transitions",
            title = "🔄 Управление переходами",
            icon = Icons.Default.Timeline,
            description = "Демонстрация контролируемого жизненного цикла задачи",
            features = listOf("Переходы", "Валидация", "Контроль"),
            color = Color(0xFF4CAF50),
            onStart = onStartTransitionDemo
        ),
        DemoItem(
            id = "rag",
            title = "🔍 RAG Pipeline",
            icon = Icons.Default.TravelExplore,
            description = "Индексация документов ЧМ: чанкинг, эмбеддинги, поиск, сравнение 2 стратегий",
            features = listOf("Чанкинг", "Эмбеддинги", "Поиск"),
            color = Color(0xFF43A047),
            onStart = onStartRagDemo
        ),
    )

    val gridState = rememberLazyGridState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "🧪 Демонстрации",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "Выберите демонстрацию для запуска. Во время демонстрации чат будет заблокирован.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        // ============================================================
        // ИНДИКАТОР ТЕКУЩЕЙ ДЕМОНСТРАЦИИ
        // ============================================================
        if (isDemoRunning && currentDemoName != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "▶ ${demoProgress ?: "Выполняется $currentDemoName..."}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    if (onCancelDemo != null) {
                        TextButton(
                            onClick = onCancelDemo,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Отменить", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 350.dp),
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
            .height(220.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isRunning)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    demo.icon,
                    contentDescription = null,
                    modifier = Modifier
                        .width(36.dp)
                        .height(36.dp),
                    tint = if (isRunning)
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    else
                        demo.color
                )
                Text(
                    text = demo.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isRunning)
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    else
                        demo.color,
                    textAlign = TextAlign.Center
                )
            }

            Text(
                text = demo.description,
                fontSize = 11.sp,
                color = if (isRunning)
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                demo.features.forEach { feature ->
                    CompactFeatureChip(
                        text = feature.take(12),
                        isDisabled = isRunning
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = onStart,
                enabled = !isRunning,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning)
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    else
                        demo.color,
                    contentColor = if (isRunning)
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    else
                        Color.White
                )
            ) {
                Text(
                    text = if (isRunning) "▶ Выполняется..." else "▶ Запустить",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun CompactFeatureChip(
    text: String,
    isDisabled: Boolean = false
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (isDisabled)
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
        else
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 10.sp,
                maxLines = 1,
                color = if (isDisabled)
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                else
                    MaterialTheme.colorScheme.onSecondaryContainer,
                textAlign = TextAlign.Center
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
