package com.llmapp.ui.components

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llmapp.invariants.InvariantSet

@Composable
fun InvariantSetCard(
    invariantSet: InvariantSet,
    isActive: Boolean = false,
    onDelete: () -> Unit,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isActive) 4.dp else 2.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isActive) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF4CAF50),
                            modifier = Modifier.size(8.dp)
                        ) {}
                    }
                    Column {
                        Text(
                            text = invariantSet.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isActive)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = invariantSet.description,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Инвариантов: ${invariantSet.invariants.size} | Версия: ${invariantSet.version}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isActive) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF4CAF50).copy(alpha = 0.2f)
                        ) {
                            Text(
                                "Активен",
                                fontSize = 10.sp,
                                color = Color(0xFF4CAF50),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onSelect,
                        modifier = Modifier.height(32.dp),
                        enabled = !isActive
                    ) {
                        Text(if (isActive) "✓" else "Выбрать", fontSize = 11.sp)
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.height(32.dp),
                        enabled = !isActive
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Удалить",
                            modifier = Modifier.size(16.dp),
                            tint = if (!isActive)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                invariantSet.invariants.forEach { invariant ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = getInvariantTypeEmoji(invariant.type),
                            fontSize = 12.sp
                        )
                        Text(
                            text = "${invariant.name}: ${invariant.description}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun getInvariantTypeEmoji(type: com.llmapp.invariants.InvariantType): String {
    return when (type) {
        com.llmapp.invariants.InvariantType.ARCHITECTURE -> "🏗️"
        com.llmapp.invariants.InvariantType.TECH_STACK -> "⚙️"
        com.llmapp.invariants.InvariantType.CODING_STANDARD -> "📝"
        com.llmapp.invariants.InvariantType.BUSINESS_RULE -> "📋"
        com.llmapp.invariants.InvariantType.SECURITY -> "🔒"
        com.llmapp.invariants.InvariantType.PERFORMANCE -> "⚡"
        com.llmapp.invariants.InvariantType.CUSTOM -> "📌"
    }
}

@Composable
fun InvariantManagerDialog(
    invariantSets: List<InvariantSet>,
    activeSetName: String?,
    onSelect: (InvariantSet) -> Unit,
    onDelete: (String) -> Unit,
    onCreatePreset: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🔒 Управление инвариантами")
                if (activeSetName != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF4CAF50).copy(alpha = 0.2f),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = "✅ Активен: ${activeSetName.take(20)}",
                            fontSize = 11.sp,
                            color = Color(0xFF4CAF50),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(450.dp)
            ) {
                Text(
                    text = "📋 Создать из пресета:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onCreatePreset("android") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("📱 Android/KMP", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = { onCreatePreset("web") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("🌐 Web/Fullstack", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = { onCreatePreset("base") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("📋 Base Rules", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Доступные наборы инвариантов (${invariantSets.size}):",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (invariantSets.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("📭 Нет сохраненных наборов", fontSize = 16.sp)
                            Text(
                                "Создайте набор из пресета выше",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(invariantSets) { set ->
                            InvariantSetCard(
                                invariantSet = set,
                                isActive = set.name == activeSetName,
                                onDelete = { onDelete(set.name) },
                                onSelect = { onSelect(set) }
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
