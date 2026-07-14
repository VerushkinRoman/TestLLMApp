package com.llmapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llmapp.ui.models.ChatMessageUI
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.datatransfer.StringSelection
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MessageBubble(
    message: ChatMessageUI,
    currentModel: String,
    onRegenerate: (() -> Unit)? = null,
    isRegenerating: Boolean = false,
    isDemoRunning: Boolean = false
) {
    val isUser = message.role == "user"
    val isSystem = message.role == "system"
    val clipboard = LocalClipboard.current
    var showCopiedTooltip by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                when {
                    isUser -> Modifier.padding(start = 48.dp)
                    isSystem -> Modifier.padding(horizontal = 24.dp)
                    else -> Modifier.padding(end = 48.dp)
                }
            ),
        horizontalArrangement = when {
            isUser -> Arrangement.End
            isSystem -> Arrangement.Center
            else -> Arrangement.Start
        }
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = if (isSystem) 12.dp else 16.dp,
                topEnd = if (isSystem) 12.dp else 16.dp,
                bottomStart = when {
                    isSystem -> 12.dp
                    isUser -> 16.dp
                    else -> 4.dp
                },
                bottomEnd = when {
                    isSystem -> 12.dp
                    isUser -> 4.dp
                    else -> 16.dp
                }
            ),
            color = when {
                isSystem -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                isUser -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            tonalElevation = 1.dp,
            modifier = Modifier.widthIn(max = 1200.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when {
                            isUser -> "Вы"
                            isSystem -> "⚡ Система"
                            else -> "Ассистент"
                        },
                        fontSize = 12.sp,
                        color = when {
                            isSystem -> MaterialTheme.colorScheme.onTertiaryContainer
                            isUser -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.secondary
                        },
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                when {
                    isUser -> SelectionContainer {
                        Text(
                            text = message.content,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    isSystem -> SelectionContainer {
                            Text(
                                text = message.content,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }

                        else -> FormattedMessage(message.content)
                    }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isSystem) Arrangement.Center else Arrangement.spacedBy(
                        4.dp
                    )
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                val transferable = StringSelection(message.content)
                                val clipEntry = ClipEntry(transferable)
                                clipboard.setClipEntry(clipEntry)
                                showCopiedTooltip = true
                                delay(1.5.seconds)
                                showCopiedTooltip = false
                            }
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 28.dp),
                        enabled = true,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Копировать",
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Копировать", fontSize = 11.sp)
                            if (showCopiedTooltip) {
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "✓",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    if (!isSystem) {
                        if (!isUser && onRegenerate != null) {
                            Button(
                                onClick = onRegenerate,
                                modifier = Modifier.weight(1f).heightIn(min = 28.dp),
                                enabled = !isRegenerating && !isDemoRunning,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isDemoRunning)
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    else
                                        MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = if (isDemoRunning)
                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                                    else
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    if (isRegenerating) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(12.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text("Генерация...", fontSize = 11.sp)
                                    } else {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = "Перегенерировать",
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text("Перегенерировать", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                if (isSystem && !message.isDemoMessage) {
                    message.metadata?.let { meta ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = meta,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f)
                        )
                    }
                }

                if (!isUser && !isSystem) {
                    Spacer(modifier = Modifier.height(8.dp))

                    val infoItems = mutableListOf<String>()
                    var tokensText: String?
                    var timeText: String?

                    if (!message.isDemoMessage) {
                        message.metadata?.let { infoItems.add(it) }
                    }

                    if (message.totalTokens != null) {
                        tokensText =
                            if (message.promptTokens != null && message.completionTokens != null) {
                                "🔢 ${message.totalTokens} (↑${message.promptTokens}/↓${message.completionTokens})"
                            } else {
                                "🔢 ${message.totalTokens}"
                            }
                        infoItems.add(tokensText)
                    }

                    message.getFormattedResponseTime()?.let { time ->
                        val speedIcon = when {
                            message.responseTimeMs!! < 10.seconds.inWholeMilliseconds -> "🚀"
                            message.responseTimeMs < 20.seconds.inWholeMilliseconds -> "⚡"
                            else -> "🐢"
                        }
                        timeText = "$speedIcon $time"
                        infoItems.add(timeText)
                    }

                    if (infoItems.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = infoItems.joinToString(" • "),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.weight(1f)
                            )

                            CopyMetricsButton(
                                content = message.content,
                                modelName = currentModel,
                                responseTimeMs = message.responseTimeMs,
                                promptTokens = message.promptTokens,
                                completionTokens = message.completionTokens,
                                totalTokens = message.totalTokens,
                            )
                        }
                    }

                    message.ragSources?.let { sources ->
                        if (sources.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = "📚 Источники (${sources.size}):",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    sources.forEachIndexed { i, src ->
                                        Text(
                                            text = "[${i + 1}] ${src.title} — ${src.section} (score: ${
                                                "%.3f".format(
                                                    src.score
                                                )
                                            })",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(
                                                alpha = 0.8f
                                            ),
                                            maxLines = 2
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CopyMetricsButton(
    content: String,
    modelName: String,
    responseTimeMs: Long?,
    promptTokens: Int?,
    completionTokens: Int?,
    totalTokens: Int?,
) {
    val clipboard = LocalClipboard.current
    var showCopied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val copyText = buildString {
        appendLine(content)
        appendLine()
        append("Модель: $modelName")

        responseTimeMs?.let { ms ->
            val timeSec = ms / 1000.0
            append(" | Время: ${String.format("%.2f", timeSec)}с")
        }

        promptTokens?.let { append(" | Prompt: $it") }
        completionTokens?.let { append(" | Completion: $it") }
        totalTokens?.let { append(" | Total: $it") }
    }

    IconButton(
        onClick = {
            scope.launch {
                val transferable = StringSelection(copyText)
                val clipEntry = ClipEntry(transferable)
                clipboard.setClipEntry(clipEntry)
                showCopied = true
                delay(1.seconds)
                showCopied = false
            }
        },
        modifier = Modifier.size(20.dp),
    ) {
        Icon(
            if (showCopied) Icons.Default.Check else Icons.Default.ContentCopy,
            contentDescription = if (showCopied) "Скопировано!" else "Копировать метрики",
            modifier = Modifier.size(14.dp),
            tint = if (showCopied)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}
