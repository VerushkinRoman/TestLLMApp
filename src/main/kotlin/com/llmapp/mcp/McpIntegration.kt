package com.llmapp.mcp

import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class McpIntegration(
    private val onLog: ((String) -> Unit)? = null
) {
    private val client = McpClient(onLog)
    private var connected = false
    private var tools: List<McpToolInfo> = emptyList()

    private fun log(msg: String) = onLog?.invoke(msg)

    suspend fun connect(): InitResult {
        val result = client.initialize()
        val rawTools = client.listTools()
        tools = rawTools.map { tool ->
            val paramList = mutableListOf<McpToolParam>()
            tool.inputSchema.properties?.forEach { (key, value) ->
                val obj = value.jsonObject
                paramList.add(
                    McpToolParam(
                        name = key,
                        type = obj["type"]?.jsonPrimitive?.content ?: "string",
                        description = obj["description"]?.jsonPrimitive?.content ?: ""
                    )
                )
            }
            McpToolInfo(
                name = tool.name,
                description = tool.description ?: "",
                parameters = paramList
            )
        }
        connected = true
        return result
    }

    fun disconnect() {
        client.close()
        connected = false
        tools = emptyList()
    }

    fun isConnected(): Boolean = connected

    fun getToolDescriptions(): String {
        if (tools.isEmpty()) return ""
        val sb = StringBuilder()
        sb.appendLine("You have access to the World Cup 2026 MCP server. Call ONE tool per response using this format:")
        sb.appendLine()
        sb.appendLine("[MCP_CALL]")
        sb.appendLine("{\"tool\": \"tool_name\", \"arguments\": {\"key\": \"value\"}}")
        sb.appendLine()
        sb.appendLine("Available tools:")
        sb.appendLine()
        sb.appendLine("=== TEAM INFO (returns: name, flag, group letter only — NO points or position) ===")
        sb.appendLine("- get_teams: List all 48 teams with their group letter")
        sb.appendLine("  Parameters: none")
        sb.appendLine("- get_team: Get team metadata by name")
        sb.appendLine("  Parameters: name (string, required) — team name like 'Argentina', 'Brazil'")
        sb.appendLine()
        sb.appendLine("=== GROUP STANDINGS (returns: position, team, MP, W, D, L, GF, GA, GD, PTS) ===")
        sb.appendLine("- get_groups: List ALL groups with full standings (points, position, W/D/L, goals)")
        sb.appendLine("  Parameters: none")
        sb.appendLine("- get_group: Get a specific group by letter")
        sb.appendLine("  Parameters: name (string, required) — group letter A-L")
        sb.appendLine()
        sb.appendLine("=== MATCHES ===")
        sb.appendLine("- get_games: List all matches")
        sb.appendLine("  Parameters: none")
        sb.appendLine("- get_game: Get match details by MongoDB ID")
        sb.appendLine("  Parameters: id (string, required)")
        sb.appendLine()
        sb.appendLine("=== STADIUMS ===")
        sb.appendLine("- get_stadiums: List all stadiums with location and capacity")
        sb.appendLine("  Parameters: none")
        sb.appendLine()
        sb.appendLine("=== STRATEGY ===")
        sb.appendLine("- For group letter: use get_team or get_teams")
        sb.appendLine("- For points, position, W/D/L, goals: use get_groups or get_group")
        sb.appendLine("- To find a team's position+points: first call get_team to get the group letter, then call get_group with that letter to get standings")
        sb.appendLine()
        sb.appendLine("If the user asks about points/position/standings, ALWAYS use get_groups or get_group — get_team does NOT return points or position.")
        sb.appendLine()
        sb.appendLine("After calling a tool, you will receive the result. Use the data to answer in Russian.")
        return sb.toString()
    }

    suspend fun executeToolCall(jsonString: String): String {
        val json = Json { ignoreUnknownKeys = true }
        val obj = json.parseToJsonElement(jsonString).jsonObject
        val toolName =
            obj["tool"]?.jsonPrimitive?.content ?: throw McpException("Missing 'tool' in MCP call")
        val args =
            obj["arguments"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap()
        log("🔧 MCP вызов: $toolName $args")
        val result = client.callTool(toolName, args)
        val text = result.content.filterIsInstance<TextContent>().joinToString("\n") { it.text }
        log("📦 MCP результат: ${text.take(200)}")
        return text
    }

    data class McpToolInfo(
        val name: String,
        val description: String,
        val parameters: List<McpToolParam>
    )

    data class McpToolParam(
        val name: String,
        val type: String,
        val description: String
    )
}
