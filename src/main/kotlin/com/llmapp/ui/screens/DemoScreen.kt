package com.llmapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class DemoCardData(
    val title: String,
    val shortTitle: String,
    val description: String,
    val features: List<String>,
    val icon: @Composable () -> Unit,
    val hasInput: Boolean = false,
    val inputLabel: String = "",
)

@Composable
fun DemoScreen(
    isDemoRunning: Boolean,
    currentDemoName: String?,
    demoProgress: String?,
    onCancelDemo: (() -> Unit)?,
    onClearHistory: () -> Unit,
    onStartProjectDemo: (() -> Unit)? = null,
    onStartPRReview: ((prNumber: Int) -> Unit)? = null,
    onStartPRReviewAgent: ((prNumber: Int) -> Unit)? = null,
    onStartFileAssistant: (() -> Unit)? = null,
) {
    var prNumber by remember { mutableStateOf("2") }
    var agentPrNumber by remember { mutableStateOf("") }

    val cards = listOf(
        DemoCardData(
            title = "Ассистент разработчика",
            shortTitle = "Dev",
            description = "RAG по документации, ответы на вопросы о проекте, Git-контекст.",
            features = listOf("RAG", "Docs", "Q&A"),
            icon = { Icon(Icons.Default.Code, null, modifier = Modifier.size(20.dp), tint = Color(0xFF2E7D32)) },
        ),
        DemoCardData(
            title = "AI Code Review",
            shortTitle = "Review",
            description = "Автоматическое ревью PR: diff, RAG, LLM-анализ, оценка.",
            features = listOf("PR", "RAG", "LLM"),
            icon = { Icon(Icons.Default.RateReview, null, modifier = Modifier.size(20.dp), tint = Color(0xFF1565C0)) },
            hasInput = true,
            inputLabel = "PR #",
        ),
        DemoCardData(
            title = "Ассистент ревью PR",
            shortTitle = "Agent",
            description = "Агент ходит в CalendarKMP через MCP, пишет саммари ревью.",
            features = listOf("MCP", "Git", "LLM"),
            icon = { Icon(Icons.Default.RateReview, null, modifier = Modifier.size(20.dp), tint = Color(0xFF2E7D32)) },
            hasInput = true,
            inputLabel = "PR # (0=последний)",
        ),
        DemoCardData(
            title = "AI Файл-ассистент",
            shortTitle = "Files",
            description = "Работа с файлами: поиск, генерация README, проверка MVI.",
            features = listOf("Agent", "Search", "RAG"),
            icon = { Icon(Icons.Filled.FolderOpen, null, modifier = Modifier.size(20.dp), tint = Color(0xFF6A1B9A)) },
        ),
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Header
        Text(
            text = "Демонстрации",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Text(
            text = "Выберите демонстрацию. Во время выполнения чат заблокирован.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        // Status bar
        if (isDemoRunning && currentDemoName != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Text(text = "▶ ${demoProgress ?: currentDemoName}", fontSize = 12.sp)
                    }
                    if (onCancelDemo != null) {
                        TextButton(onClick = onCancelDemo) {
                            Text("Отменить", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Grid: 4 cards per row
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(cards) { card ->
                CompactDemoCard(
                    card = card,
                    isDemoRunning = isDemoRunning,
                    prNumber = if (card.inputLabel == "PR #") prNumber else agentPrNumber,
                    onPrNumberChange = {
                        if (card.inputLabel == "PR #") prNumber = it else agentPrNumber = it
                    },
                    onClick = {
                        onClearHistory()
                        when (card.shortTitle) {
                            "Dev" -> onStartProjectDemo?.invoke()
                            "Review" -> onStartPRReview?.invoke(prNumber.toIntOrNull() ?: 2)
                            "Agent" -> onStartPRReviewAgent?.invoke(agentPrNumber.toIntOrNull() ?: 0)
                            "Files" -> onStartFileAssistant?.invoke()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun CompactDemoCard(
    card: DemoCardData,
    isDemoRunning: Boolean,
    prNumber: String,
    onPrNumberChange: (String) -> Unit,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isDemoRunning)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            card.icon()

            Text(
                text = card.shortTitle,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDemoRunning) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else Color(0xFF2E7D32),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = card.description,
                fontSize = 9.sp,
                color = if (isDemoRunning) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 11.sp,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                card.features.forEach { feature ->
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = if (isDemoRunning)
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                        else
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = feature,
                            fontSize = 7.sp,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            color = if (isDemoRunning)
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            else
                                MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }

            if (card.hasInput) {
                OutlinedTextField(
                    value = prNumber,
                    onValueChange = { onPrNumberChange(it.filter { c -> c.isDigit() }.take(5)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    ),
                )
            }

            Button(
                onClick = onClick,
                enabled = !isDemoRunning,
                modifier = Modifier.fillMaxWidth().height(30.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDemoRunning)
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    else
                        Color(0xFF2E7D32),
                    contentColor = Color.White,
                    disabledContainerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                ),
                contentPadding = ButtonDefaults.TextButtonContentPadding,
            ) {
                Text(
                    text = if (isDemoRunning) "..." else "▶",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
