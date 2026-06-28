package com.llmapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llmapp.mcp.McpClient
import com.llmapp.service.TranslationService
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private data class McpServerConfig(
    val id: String,
    val label: String,
    val url: String,
    val icon: String,
    val description: String
)

private val servers = listOf(
    McpServerConfig(
        id = "data",
        label = "Data Server",
        url = "https://alcoserver.ru:4454/mcp",
        icon = "🗄️",
        description = "World Cup 2026 API — группы, команды, матчи, стадионы"
    ),
    McpServerConfig(
        id = "pipeline",
        label = "Pipeline Server",
        url = "https://alcoserver.ru:4456/mcp",
        icon = "⚙️",
        description = "Обработка данных — суммаризация, сохранение в файл"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun McpScreen() {
    var selectedServerId by remember { mutableStateOf(servers.first().id) }
    val selectedServer = servers.first { it.id == selectedServerId }

    var connections by remember {
        mutableStateOf(servers.associate { it.id to McpConnectionState() })
    }

    var tools by remember { mutableStateOf<List<Tool>>(emptyList()) }
    var log by remember { mutableStateOf(listOf("World Cup 2026 MCP Client")) }

    var selectedTool by remember { mutableStateOf<Tool?>(null) }
    var paramsText by remember { mutableStateOf("") }
    var responseText by remember { mutableStateOf("") }
    var dropdownExpanded by remember { mutableStateOf(false) }

    var translatingDescription by remember { mutableStateOf(false) }
    var showingTranslation by remember { mutableStateOf(false) }
    val translationService = remember { TranslationService() }

    val scope = rememberCoroutineScope()

    fun addLog(msg: String) { log = log + msg }

    fun connectTo(server: McpServerConfig) {
        val cur = connections[server.id] ?: return
        connections = connections + (server.id to cur.copy(connecting = true))
        addLog("🚀 ${server.icon} Подключение к ${server.label} (${server.url})...")
        scope.launch {
            try {
                val mcpClient = McpClient(serverUrl = server.url, onLog = { msg -> addLog(msg) })
                val initResult = mcpClient.initialize()
                val ver = "${initResult.name} v${initResult.version}"
                addLog("✅ ${server.icon} Подключено: $ver")
                val toolList = mcpClient.listTools()
                addLog("📋 ${server.icon} Инструментов: ${toolList.size}")
                connections = connections + (server.id to McpConnectionState(
                    connected = true, connecting = false,
                    serverName = ver, client = mcpClient
                ))
                if (selectedServerId == server.id) {
                    tools = toolList
                }
            } catch (e: Exception) {
                addLog("❌ ${server.icon} Ошибка: ${e.message ?: e.javaClass.simpleName}")
                connections = connections + (server.id to McpConnectionState(connecting = false))
            }
        }
    }

    fun disconnectFrom(serverId: String) {
        val cur = connections[serverId] ?: return
        cur.client?.close()
        connections = connections + (serverId to McpConnectionState())
        if (selectedServerId == serverId) {
            tools = emptyList()
            selectedTool = null
            paramsText = ""
            responseText = ""
        }
        val cfg = servers.first { it.id == serverId }
        addLog("🔌 ${cfg.icon} Отключено")
    }

    fun switchTo(serverId: String) {
        selectedServerId = serverId
        val conn = connections[serverId] ?: return
        if (conn.connected && conn.client != null) {
            scope.launch {
                try {
                    tools = conn.client.listTools()
                } catch (_: Exception) {
                    tools = emptyList()
                }
            }
        } else {
            tools = emptyList()
        }
        selectedTool = null
        paramsText = ""
        responseText = ""
    }

    fun callSelectedTool() {
        val tool = selectedTool ?: return
        val conn = connections[selectedServerId] ?: return
        val mcp = conn.client ?: return
        val parsed = parseParams(paramsText)
        addLog("📞 ${selectedServer.icon} Вызов: ${tool.name} $parsed")
        responseText = "⏳ Выполнение..."
        scope.launch {
            try {
                val result = mcp.callTool(tool.name, parsed)
                val sb = StringBuilder()
                var hasText = false
                for (content in result.content) {
                    if (content is TextContent) {
                        sb.appendLine(content.text)
                        hasText = true
                    }
                }
                val output = sb.toString().trimEnd()
                responseText = if (hasText) output else "⚠️ Пустой ответ"
                addLog("✅ Ответ получен (${output.length} симв.)")
            } catch (e: Exception) {
                responseText = "❌ ${e.message}"
                addLog("❌ Ошибка: ${e.message}")
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "World Cup 2026 MCP",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Выберите сервер и подключитесь для просмотра инструментов",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (server in servers) {
                val conn = connections[server.id] ?: return@Row
                FilterChip(
                    selected = selectedServerId == server.id,
                    onClick = { switchTo(server.id) },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(server.icon, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(server.label, fontSize = 12.sp)
                            if (conn.connected) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("•", fontSize = 14.sp, color = Color(0xFF2E7D32))
                            }
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = selectedServer.icon + " " + selectedServer.description,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        val currentConn = connections[selectedServerId] ?: return@Column
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (currentConn.connected) {
                OutlinedButton(
                    onClick = { disconnectFrom(selectedServerId) },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.LinkOff, "Отключиться", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Отключиться")
                }
            } else {
                Button(
                    onClick = { connectTo(selectedServer) },
                    enabled = !currentConn.connecting
                ) {
                    if (currentConn.connecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Подключение...")
                    } else {
                        Icon(Icons.Default.Link, "Подключиться", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Подключиться")
                    }
                }
            }
        }

        if (currentConn.connected && currentConn.serverName.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Подключено: ${currentConn.serverName}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "| Инструментов: ${tools.size}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Вызов инструмента",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    ExposedDropdownMenuBox(
                        expanded = dropdownExpanded,
                        onExpandedChange = { dropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedTool?.name ?: "Выберите инструмент",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                            textStyle = TextStyle(fontSize = 13.sp),
                            singleLine = true,
                        )
                        ExposedDropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false }
                        ) {
                            tools.forEach { tool ->
                                val desc = tool.description
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                tool.name,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            if (desc != null) {
                                                val firstLine = desc.lineSequence().first()
                                                    .let { if (it.length > 80) it.take(80) + "..." else it }
                                                Text(
                                                    firstLine,
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        selectedTool = tool
                                        dropdownExpanded = false
                                        paramsText = describeToolParams(tool).third
                                        responseText = ""
                                        showingTranslation = translationService.hasCached(tool.name)
                                    }
                                )
                            }
                        }
                    }

                    if (selectedTool != null) {
                        val tool = selectedTool!!
                        val desc = tool.description
                        if (desc != null) {
                            val cached = translationService.getCached(tool.name)
                            Spacer(modifier = Modifier.height(6.dp))
                            Column {
                                Text(
                                    text = if (showingTranslation && cached != null) cached else desc,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                if (translatingDescription) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "Перевод...",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                } else if (cached != null) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        OutlinedButton(
                                            onClick = { showingTranslation = !showingTranslation },
                                            contentPadding = ButtonDefaults.TextButtonContentPadding,
                                        ) {
                                            Text(
                                                if (showingTranslation) "Оригинал" else "Перевод",
                                                fontSize = 10.sp,
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                translationService.clearCache(tool.name)
                                                showingTranslation = false
                                            },
                                            contentPadding = ButtonDefaults.TextButtonContentPadding,
                                        ) {
                                            Text("Сбросить кэш", fontSize = 10.sp)
                                        }
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            translatingDescription = true
                                            scope.launch {
                                                try {
                                                    translationService.translate(tool.name, desc)
                                                    showingTranslation = true
                                                } catch (e: Exception) {
                                                    addLog("❌ Ошибка перевода: ${e.message}")
                                                } finally {
                                                    translatingDescription = false
                                                }
                                            }
                                        },
                                        contentPadding = ButtonDefaults.TextButtonContentPadding,
                                    ) {
                                        Text("Перевести", fontSize = 10.sp)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        val (requiredParams, optionalParams, _) = describeToolParams(tool)
                        if (requiredParams.isNotEmpty() || optionalParams.isNotEmpty()) {
                            Text(
                                text = "Ожидаемые параметры:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            for (p in requiredParams) {
                                Text(
                                    text = "• ${p.name} (обязательный): ${p.type} — ${p.desc}",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            for (p in optionalParams) {
                                Text(
                                    text = "• ${p.name} (опциональный): ${p.type} — ${p.desc}",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = paramsText,
                        onValueChange = { paramsText = it },
                        label = { Text("Параметры (key=value, по одному на строке)") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                        textStyle = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                        maxLines = 6,
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { callSelectedTool() },
                        enabled = selectedTool != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Выполнить")
                    }

                    if (responseText.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Результат",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        SelectionContainer {
                            Text(
                                text = responseText,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Лог",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp, max = 200.dp)
                    .background(
                        color = Color(0xFF1A1A1A),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(8.dp)
            ) {
                SelectionContainer {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(log) { line ->
                            val isError = line.startsWith("❌")
                            val isSuccess = line.startsWith("✅")
                            Text(
                                text = line,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = when {
                                    isError -> Color(0xFFE57373)
                                    isSuccess -> Color(0xFF81C784)
                                    else -> Color(0xFFBDBDBD)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class McpConnectionState(
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val serverName: String = "",
    val client: McpClient? = null
)

private data class ParamInfo(
    val name: String,
    val type: String,
    val desc: String,
)

private fun describeToolParams(tool: Tool): Triple<List<ParamInfo>, List<ParamInfo>, String> {
    val required = tool.inputSchema.required?.toSet() ?: emptySet()
    val props = tool.inputSchema.properties ?: return Triple(emptyList(), emptyList(), "")
    val requiredParams = mutableListOf<ParamInfo>()
    val optionalParams = mutableListOf<ParamInfo>()
    val template = StringBuilder()
    for ((key, value) in props) {
        val obj = value.jsonObject
        val type = obj["type"]?.jsonPrimitive?.content ?: "string"
        val description = obj["description"]?.jsonPrimitive?.content ?: ""
        val info = ParamInfo(key, type, description)
        if (key in required) {
            requiredParams.add(info)
            template.appendLine("$key=")
        } else {
            optionalParams.add(info)
            template.appendLine("# $key= (опционально)")
        }
    }
    return Triple(requiredParams, optionalParams, template.toString().trimEnd())
}

private fun parseParams(text: String): Map<String, String> {
    val map = mutableMapOf<String, String>()
    for (line in text.lines()) {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
        val eq = trimmed.indexOf('=')
        if (eq > 0) {
            val key = trimmed.substring(0, eq).trim()
            val value = trimmed.substring(eq + 1).trim()
            if (key.isNotEmpty()) map[key] = value
        }
    }
    return map
}
