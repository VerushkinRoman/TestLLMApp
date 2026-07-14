package com.llmapp.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llmapp.mcp.GitHubMcpTools
import com.llmapp.mcp.McpIntegration
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpScreen() {
    val tools = remember { GitHubMcpTools.getToolDefinitions() }

    var selectedTool by remember { mutableStateOf<McpIntegration.McpToolInfo?>(null) }
    var paramsText by remember { mutableStateOf("") }
    var responseText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val integration = remember { McpIntegration() }

    fun callTool() {
        val tool = selectedTool ?: return
        val parsed = parseParams(paramsText)
        isLoading = true
        responseText = "⏳ Выполнение..."
        scope.launch {
            try {
                val json = buildString {
                    append("""{"tool": "${tool.name}", "arguments": $parsed}""")
                }
                val result = integration.executeToolCall(json)
                responseText = result.ifEmpty { "⚠️ Пустой ответ" }
            } catch (e: Exception) {
                responseText = "❌ ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "GitHub MCP Tools",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Инструменты для чтения исходного кода из GitHub",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Tool selector
        ExposedDropdownMenuBox(
            expanded = dropdownExpanded,
            onExpandedChange = { dropdownExpanded = it }
        ) {
            OutlinedTextField(
                value = selectedTool?.name ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Инструмент") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                )
            )
            ExposedDropdownMenu(
                expanded = dropdownExpanded,
                onDismissRequest = { dropdownExpanded = false }
            ) {
                tools.forEach { tool ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(tool.name, fontWeight = FontWeight.Medium)
                                Text(
                                    tool.description.take(80),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            selectedTool = tool
                            paramsText = if (tool.requiredParams.isNullOrEmpty()) "{}" else
                                tool.requiredParams.joinToString(", ") { "\"$it\": \"\"" }
                                    .let { "{$it}" }
                            dropdownExpanded = false
                            responseText = ""
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Tool info
        selectedTool?.let { tool ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        tool.description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (tool.requiredParams?.isNotEmpty() == true) {
                        Text(
                            "Обязательные: ${tool.requiredParams.joinToString(", ")}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Parameters
            OutlinedTextField(
                value = paramsText,
                onValueChange = { paramsText = it },
                label = { Text("Параметры (JSON)") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                singleLine = false,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { callTool() },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Выполнить")
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Response
            if (responseText.isNotEmpty()) {
                Text("Результат:", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
                SelectionContainer {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        LazyColumn(
                            modifier = Modifier.padding(12.dp).heightIn(max = 400.dp)
                        ) {
                            item {
                                Text(
                                    text = responseText,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        if (selectedTool == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Выберите инструмент для вызова",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun parseParams(text: String): String {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || trimmed == "{}") return "{}"
    return try {
        kotlinx.serialization.json.Json.parseToJsonElement(trimmed)
        trimmed
    } catch (_: Exception) {
        """{"input": "$trimmed"}"""
    }
}
