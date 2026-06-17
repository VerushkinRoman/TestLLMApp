package com.llmapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SnapshotDialog(
    snapshots: List<Pair<String, String>>,
    onRestore: (String) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
    onGetDetails: ((String) -> String)? = null
) {
    var newSnapshotName by remember { mutableStateOf("") }
    var expandedSnapshotId by remember { mutableStateOf<String?>(null) }
    var snapshotDetails by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("📸 Снимки состояния") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Создание нового снимка
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newSnapshotName,
                        onValueChange = { newSnapshotName = it },
                        placeholder = { Text("Название снимка") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            if (newSnapshotName.isNotBlank()) {
                                onCreate(newSnapshotName)
                                newSnapshotName = ""
                            }
                        },
                        enabled = newSnapshotName.isNotBlank()
                    ) {
                        Text("Создать")
                    }
                }

                // Список снимков
                if (snapshots.isEmpty()) {
                    Text(
                        text = "Нет сохраненных снимков",
                        fontSize = 14.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(snapshots) { (id, description) ->
                            SnapshotItem(
                                snapshotId = id,  // <-- переименовано для ясности
                                description = description,
                                onRestore = { onRestore(id) },
                                onToggleDetails = {
                                    if (expandedSnapshotId == id) {
                                        expandedSnapshotId = null
                                        snapshotDetails = null
                                    } else {
                                        expandedSnapshotId = id
                                        snapshotDetails =
                                            onGetDetails?.invoke(id) ?: "Детали недоступны"
                                    }
                                },
                                isExpanded = expandedSnapshotId == id,
                                details = snapshotDetails
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

@Composable
fun SnapshotItem(
    snapshotId: String,
    description: String,
    onRestore: () -> Unit,
    onToggleDetails: () -> Unit,
    isExpanded: Boolean,
    details: String?
) {
    Card(
        modifier = Modifier.fillMaxWidth()
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
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = description,
                        fontSize = 13.sp
                    )
                    // Показываем ID снимка мелким шрифтом
                    Text(
                        text = "ID: ${snapshotId.take(8)}...",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Кнопка деталей
                    IconButton(
                        onClick = onToggleDetails,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = if (isExpanded) "Скрыть детали" else "Показать детали",
                            modifier = Modifier
                        )
                    }

                    // Кнопка восстановления
                    Button(
                        onClick = onRestore,
                        modifier = Modifier
                    ) {
                        Text("Восст.", fontSize = 11.sp)
                    }
                }
            }

            // Детали снимка (разворачиваются)
            if (isExpanded && details != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = details,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(12.dp),
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}
