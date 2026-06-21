package com.llmapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llmapp.agent.AvailableTransition
import com.llmapp.state.TaskPhase

@Composable
fun TransitionsDialog(
    currentPhase: TaskPhase,
    availableTransitions: List<AvailableTransition>,
    onTransition: (TaskPhase) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "🔄 Управление переходами",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(450.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Подсказка
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = getPhaseHint(currentPhase),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(8.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                // Текущая фаза
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = getPhaseColor(currentPhase).copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentPhase.emoji,
                            fontSize = 20.sp
                        )
                        Column {
                            Text(
                                text = "Текущая фаза",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = currentPhase.displayName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = getPhaseColor(currentPhase)
                            )
                        }
                    }
                }

                // Доступные переходы
                Text(
                    text = "Доступные переходы (${availableTransitions.filter { it.isValid }.size})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )

                if (availableTransitions.isEmpty()) {
                    Text(
                        text = "Нет доступных переходов",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(availableTransitions) { transition ->
                            TransitionItem(
                                transition = transition,
                                onSelect = {
                                    if (transition.isValid) {
                                        onTransition(transition.to)
                                        onDismiss()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}

private fun getPhaseHint(phase: TaskPhase): String {
    return when (phase) {
        TaskPhase.INIT -> "💡 Сейчас вы на этапе сбора требований. Опишите задачу в чате, затем перейдите в PLANNING."
        TaskPhase.PLANNING -> "💡 Создайте план. Когда план готов, используйте /approve-plan, затем перейдите в EXECUTION."
        TaskPhase.EXECUTION -> "💡 Выполняйте задачу. Когда готово, перейдите в VALIDATION."
        TaskPhase.VALIDATION -> "💡 Проверьте результат. Если все OK, используйте /validate, затем перейдите в DONE."
        TaskPhase.DONE -> "✅ Задача завершена! Создайте новую задачу через /task"
        TaskPhase.PAUSED -> "⏸️ Задача на паузе. Используйте /resume для продолжения."
        TaskPhase.BLOCKED -> "🚫 Задача заблокирована. Используйте /unblock для разблокировки."
    }
}

@Composable
fun TransitionItem(
    transition: AvailableTransition,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (transition.isValid)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (transition.isValid) 1.dp else 0.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (transition.isValid) "✅" else "🚫",
                        fontSize = 16.sp
                    )
                    Text(
                        text = "${transition.from.displayName} → ${transition.to.displayName}",
                        fontSize = 14.sp,
                        fontWeight = if (transition.isValid) FontWeight.Medium else FontWeight.Normal,
                        color = if (transition.isValid)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!transition.isValid) {
                    Text(
                        text = transition.reason,
                        fontSize = 11.sp,
                        color = Color(0xFFF44336),
                        modifier = Modifier.padding(start = 28.dp)
                    )
                    transition.suggestedAction?.let {
                        Text(
                            text = "💡 $it",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 28.dp)
                        )
                    }
                }
            }

            if (transition.isValid) {
                Button(
                    onClick = onSelect,
                    modifier = Modifier.height(32.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = getPhaseColor(transition.to),
                        contentColor = Color.White
                    )
                ) {
                    Text("Перейти", fontSize = 11.sp)
                }
            }
        }
    }
}

fun getPhaseColor(phase: TaskPhase): Color = when (phase) {
    TaskPhase.INIT -> Color(0xFF66BB6A)
    TaskPhase.PLANNING -> Color(0xFFFF9800)
    TaskPhase.EXECUTION -> Color(0xFF2E7D32)
    TaskPhase.VALIDATION -> Color(0xFF43A047)
    TaskPhase.DONE -> Color(0xFF2E7D32)
    TaskPhase.PAUSED -> Color(0xFFFF9800)
    TaskPhase.BLOCKED -> Color(0xFFF44336)
}
