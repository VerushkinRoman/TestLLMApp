package com.llmapp.ui.components

import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectorPanel(
    isRunning: Boolean,
    intervalMinutes: Double,
    log: List<String>,
    summary: String?,
    onStart: (Double) -> Unit,
    onStop: () -> Unit,
    onCollectNow: () -> Unit,
    onClearLog: () -> Unit
) {
    var showIntervalPicker by remember { mutableStateOf(false) }
    var selectedInterval by remember { mutableStateOf(intervalMinutes) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Сбор матчей",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (isRunning) {
                    Surface(
                        color = Color(0xFF2E7D32).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(10.dp),
                                strokeWidth = 1.5.dp,
                                color = Color(0xFF2E7D32)
                            )
                            Text(
                                text = "Активен",
                                fontSize = 10.sp,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isRunning) {
                    Button(
                        onClick = { showIntervalPicker = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = ButtonDefaults.TextButtonContentPadding,
                        modifier = Modifier.height(32.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(2.dp))
                            Text(
                                "Запустить",
                                fontSize = 11.sp,
                                style = TextStyle(baselineShift = BaselineShift(0.4f))
                            )
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = onStop,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        contentPadding = ButtonDefaults.TextButtonContentPadding,
                        modifier = Modifier.height(32.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Stop, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(2.dp))
                            Text(
                                "Остановить",
                                fontSize = 11.sp,
                                style = TextStyle(baselineShift = BaselineShift(0.4f))
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = onCollectNow,
                    enabled = true,
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                    modifier = Modifier.height(32.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(2.dp))
                        Text(
                            "Собрать сейчас",
                            fontSize = 11.sp,
                            style = TextStyle(baselineShift = BaselineShift(0.4f))
                        )
                    }
                }

                OutlinedButton(
                    onClick = onClearLog,
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                    modifier = Modifier.height(32.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(2.dp))
                        Text(
                            "Очистить лог",
                            fontSize = 11.sp,
                            style = TextStyle(baselineShift = BaselineShift(0.4f))
                        )
                    }
                }
            }

            if (isRunning) {
                Spacer(modifier = Modifier.height(4.dp))
                val intervalLabel =
                    if (intervalMinutes < 1.0) "${(intervalMinutes * 60).toLong()} сек" else "${intervalMinutes.toLong()} мин"
                Text(
                    text = "Интервал: $intervalLabel | Для остановки нажмите «Остановить»",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            summary?.let { text ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Последняя сводка",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                val summaryScrollState = rememberScrollState()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                ) {
                    SelectionContainer {
                        Text(
                            text = text,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(summaryScrollState)
                        )
                    }
                    VerticalScrollbar(
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        adapter = rememberScrollbarAdapter(summaryScrollState),
                        style = ScrollbarStyle(
                            minimalHeight = 30.dp,
                            thickness = 8.dp,
                            shape = MaterialTheme.shapes.small,
                            hoverDurationMillis = 300,
                            unhoverColor = Color.Gray.copy(alpha = 0.4f),
                            hoverColor = Color.Gray.copy(alpha = 0.7f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Лог сбора",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            val logListState = rememberLazyListState()
            LaunchedEffect(log.size) {
                if (log.isNotEmpty()) {
                    logListState.animateScrollToItem(log.size - 1)
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp, max = 300.dp)
                    .background(
                        color = Color(0xFF1A1A1A),
                        shape = RoundedCornerShape(8.dp)
                    )
            ) {
                LazyColumn(
                    state = logListState,
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    items(log) { line ->
                        val isError = line.contains("Ошибка") || line.contains("❌")
                        Text(
                            text = line,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (isError) Color(0xFFE57373) else Color(0xFFBDBDBD)
                        )
                    }
                }
                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(logListState),
                    style = ScrollbarStyle(
                        minimalHeight = 30.dp,
                        thickness = 8.dp,
                        shape = MaterialTheme.shapes.small,
                        hoverDurationMillis = 300,
                        unhoverColor = Color(0xFF6B6B6B),
                        hoverColor = Color(0xFF9E9E9E)
                    )
                )
            }
        }
    }

    if (showIntervalPicker) {
        AlertDialog(
            onDismissRequest = { showIntervalPicker = false },
            title = { Text("Интервал сбора (минуты)") },
            text = {
                Column {
                    listOf(0.5, 5.0, 10.0, 15.0, 30.0, 60.0).forEach { interval ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            RadioButton(
                                selected = selectedInterval == interval,
                                onClick = { selectedInterval = interval }
                            )
                            Spacer(Modifier.width(8.dp))
                            val label =
                                if (interval < 1.0) "${(interval * 60).toLong()} сек" else "${interval.toLong()} мин"
                            Text(label, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showIntervalPicker = false
                    onStart(selectedInterval)
                }) {
                    Text("Запустить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showIntervalPicker = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}
