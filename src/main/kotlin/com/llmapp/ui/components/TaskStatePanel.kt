package com.llmapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llmapp.state.TaskPhase
import com.llmapp.ui.models.TaskStateUI

@Composable
fun TaskStatePanel(
    state: TaskStateUI,
    onTransition: (TaskPhase) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onBlock: () -> Unit,
    onUnblock: () -> Unit,
    onShowSnapshots: () -> Unit,
    onShowTransitions: () -> Unit,
    isDemoRunning: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),  // уменьшены отступы
            verticalArrangement = Arrangement.spacedBy(4.dp)  // уменьшен интервал
        ) {
            // Строка 1: Название + статусы + прогресс
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Название и статусы в одной строке
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = state.phaseEmoji,
                        fontSize = 14.sp
                    )
                    Text(
                        text = state.taskName.take(20).ifEmpty { "Без названия" },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )

                    // Статусы в виде маленьких чипсов в одну строку
                    if (state.isPaused) {
                        StatusChip(
                            text = "⏸",
                            color = Color(0xFFFF9800)
                        )
                    }
                    if (state.isBlocked) {
                        StatusChip(
                            text = "🚫",
                            color = Color(0xFFF44336)
                        )
                    }
                }

                // Прогресс
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    CircularProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = getProgressColor(state.progress)
                    )
                    Text(
                        text = "${(state.progress * 100).toInt()}%",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = getProgressColor(state.progress)
                    )
                }
            }

            // Строка 2: Фаза + Шаг + Время
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PhaseBadge(
                    phase = state.phase,
                    label = state.displayPhase
                )
                Text(
                    text = "Шаг: ${state.step.take(25).ifEmpty { "—" }}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    text = "⏱ ${state.elapsedTime}",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            // Строка 3: Ожидаемое действие
            Text(
                text = "🎯 ${state.expectedAction.take(40).ifEmpty { "Ожидание..." }}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1
            )

            // Строка 4: Кнопки управления
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Кнопки переходов с валидацией
                state.availableTransitions
                    .filter { target ->
                        target != state.phase &&
                                target !in listOf(TaskPhase.PAUSED, TaskPhase.BLOCKED)
                    }
                    .forEach { target ->
                        SmallTransitionButton(
                            target = target,
                            onClick = { onTransition(target) },
                            enabled = !isDemoRunning && !state.isPaused && !state.isBlocked
                        )
                    }

                Spacer(modifier = Modifier.weight(1f))

                // Новая кнопка "Доступные переходы"
                SmallIconButton(
                    icon = Icons.Default.Info,
                    label = "Переходы",
                    onClick = onShowTransitions,
                    enabled = !isDemoRunning,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.weight(1f))

                // Маленькие иконки управления
                SmallIconButton(
                    icon = if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    label = if (state.isPaused) "Возобновить" else "Пауза",
                    onClick = if (state.isPaused) onResume else onPause,
                    enabled = !isDemoRunning && (!state.isBlocked || state.isPaused),
                    color = if (state.isPaused) Color(0xFF4CAF50) else Color(0xFFFF9800)
                )

                SmallIconButton(
                    icon = if (state.isBlocked) Icons.Default.PlayArrow else Icons.Default.Block,
                    label = if (state.isBlocked) "Разблок." else "Блок",
                    onClick = if (state.isBlocked) onUnblock else onBlock,
                    enabled = !isDemoRunning && (!state.isPaused || state.isBlocked),
                    color = if (state.isBlocked) Color(0xFF4CAF50) else Color(0xFFF44336)
                )

                SmallIconButton(
                    icon = Icons.Default.Timeline,
                    label = "Снимки",
                    onClick = onShowSnapshots,
                    enabled = !isDemoRunning,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (!state.isPaused && !state.isBlocked && state.availableTransitions.isNotEmpty()) {
                val hint = when (state.phase) {
                    TaskPhase.INIT -> "💡 Опишите требования к задаче"
                    TaskPhase.PLANNING -> "💡 Создайте план или используйте /approve-plan"
                    TaskPhase.EXECUTION -> "💡 Выполняйте задачу или перейдите в VALIDATION"
                    TaskPhase.VALIDATION -> "💡 Проверьте результат или используйте /validate"
                    TaskPhase.DONE -> "✅ Задача завершена!"
                    else -> ""
                }
                if (hint.isNotEmpty()) {
                    Text(
                        text = hint,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun StatusChip(
    text: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.15f),
        modifier = Modifier.height(16.dp)
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 0.dp),
            color = color
        )
    }
}

@Composable
fun PhaseBadge(phase: TaskPhase, label: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = getPhaseColor(phase).copy(alpha = 0.15f),
        modifier = Modifier.height(18.dp)
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            color = getPhaseColor(phase),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 0.dp)
        )
    }
}

@Composable
fun SmallTransitionButton(
    target: TaskPhase,
    onClick: () -> Unit,
    enabled: Boolean
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.height(22.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = getPhaseColor(target).copy(alpha = 0.12f),
            contentColor = getPhaseColor(target)
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 6.dp,
            vertical = 0.dp
        )
    ) {
        Text(
            text = target.displayName.take(8),
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
fun SmallIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    color: Color
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(22.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(14.dp),
            tint = if (enabled) color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        )
    }
}

fun getProgressColor(progress: Float): Color = when {
    progress < 0.3f -> Color(0xFF2196F3)
    progress < 0.7f -> Color(0xFFFF9800)
    else -> Color(0xFF4CAF50)
}
