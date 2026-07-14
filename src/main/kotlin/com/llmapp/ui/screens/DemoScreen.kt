package com.llmapp.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Демонстрации",
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

        DemoCard(
            icon = { Icon(
                Icons.Default.Code,
                contentDescription = null,
                modifier = Modifier.width(36.dp).height(36.dp),
                tint = if (isDemoRunning)
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                else
                    Color(0xFF2E7D32)
            ) },
            title = "Ассистент разработчика",
            description = "AI-ассистент для CalendarKMP: RAG по документации (README + docs), ответы на вопросы о проекте, Git-контекст.",
            features = listOf("RAG", "Документация", "Q&A"),
            isDemoRunning = isDemoRunning,
            onClick = {
                onClearHistory()
                onStartProjectDemo?.invoke()
            }
        )

        var prNumber by remember { mutableStateOf("2") }

        DemoCard(
            icon = { Icon(
                Icons.Default.RateReview,
                contentDescription = null,
                modifier = Modifier.width(36.dp).height(36.dp),
                tint = if (isDemoRunning)
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                else
                    Color(0xFF1565C0)
            ) },
            title = "AI Code Review",
            description = "Автоматическое ревью Pull Request: загрузка PR diff, RAG-контекст по архитектуре проекта, LLM-анализ, оценка эффективности.",
            features = listOf("PR Diff", "RAG", "LLM", "Оценка"),
            isDemoRunning = isDemoRunning,
            additionalContent = {
                if (!isDemoRunning) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Номер PR",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = prNumber,
                            onValueChange = { prNumber = it.filter { c -> c.isDigit() }.take(5) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            textStyle = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            },
            onClick = {
                val num = prNumber.toIntOrNull() ?: 2
                onClearHistory()
                onStartPRReview?.invoke(num)
            }
        )

        // Карточка: Ассистент ревью PR (MCP)
        var agentPrNumber by remember { mutableStateOf("") }
        DemoCard(
            icon = { Icon(
                imageVector = Icons.Filled.RateReview,
                contentDescription = "PR Review Agent",
                modifier = Modifier.width(36.dp).height(36.dp),
                tint = if (isDemoRunning)
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                else
                    Color(0xFF2E7D32)
            ) },
            title = "Ассистент ревью PR",
            description = "Агент сам ходит в CalendarKMP через MCP-тулы, получает diff и пишет саммари ревью в чат.",
            features = listOf("MCP", "Git", "LLM", "Саммари"),
            isDemoRunning = isDemoRunning,
            additionalContent = {
                if (!isDemoRunning) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Номер PR (0 = последний)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = agentPrNumber,
                            onValueChange = { agentPrNumber = it.filter { c -> c.isDigit() }.take(5) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            textStyle = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            },
            onClick = {
                val num = agentPrNumber.toIntOrNull() ?: 0
                onClearHistory()
                onStartPRReviewAgent?.invoke(num)
            }
        )
    }
}

@Composable
private fun DemoCard(
    icon: @Composable () -> Unit,
    title: String,
    description: String,
    features: List<String>,
    isDemoRunning: Boolean,
    additionalContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isDemoRunning)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                icon()
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDemoRunning)
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    else
                        Color(0xFF2E7D32),
                    textAlign = TextAlign.Center
                )
            }

            Text(
                text = description,
                fontSize = 11.sp,
                color = if (isDemoRunning)
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    6.dp,
                    Alignment.CenterHorizontally
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                features.forEach { feature ->
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (isDemoRunning)
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                        else
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = feature.take(12),
                                fontSize = 10.sp,
                                maxLines = 1,
                                color = if (isDemoRunning)
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                else
                                    MaterialTheme.colorScheme.onSecondaryContainer,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            additionalContent?.invoke()

            Button(
                onClick = onClick,
                enabled = !isDemoRunning,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDemoRunning)
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    else
                        Color(0xFF2E7D32),
                    contentColor = if (isDemoRunning)
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    else
                        Color.White
                )
            ) {
                Text(
                    text = if (isDemoRunning) "▶ Выполняется..." else "▶ Запустить",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
