package com.llmapp.mcp

import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class McpIntegration(
    private val onLog: ((String) -> Unit)? = null
) {
    private val client = McpClient(onLog)
    private var connected = false
    private var tools: List<McpToolInfo> = emptyList()
    private val pipelineTools = setOf("search_data", "summarize_data", "save_data")

    fun getToolNames(): List<String> = tools.map { it.name }
    fun hasPipelineTools(): Boolean = tools.map { it.name }.toSet().containsAll(pipelineTools)

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
                parameters = paramList,
                requiredParams = tool.inputSchema.required
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
        val firstTool = tools.first()
        val firstArg = firstTool.parameters.firstOrNull()
        val exampleCall = if (firstArg != null) {
            "{\"tool\": \"${firstTool.name}\", \"arguments\": {\"${firstArg.name}\": \"${exampleValue(firstArg)}\"}}"
        } else {
            "{\"tool\": \"${firstTool.name}\", \"arguments\": {}}"
        }

        sb.appendLine("You have access to the following MCP tools. Call ONE tool per response.")
        sb.appendLine()
        sb.appendLine("FORMAT: Your ENTIRE response must be EXACTLY this (nothing before, nothing after):")
        sb.appendLine("[MCP_CALL]")
        sb.appendLine(exampleCall)
        sb.appendLine()
        sb.appendLine("Do NOT add any text before or after. Do NOT think out loud. Do NOT explain. Just the block above.")
        sb.appendLine()
        sb.appendLine("Available tools:")
        sb.appendLine()
        for (tool in tools) {
            val reqCount = tool.parameters.count { it.name in (tool.requiredParams ?: emptyList()) }
            val optCount = tool.parameters.size - reqCount
            sb.appendLine("=== ${tool.name} (${reqCount} required, $optCount optional) ===")
            sb.appendLine("  Description: ${tool.description}")
            if (tool.parameters.isNotEmpty()) {
                sb.appendLine("  Parameters:")
                for (p in tool.parameters) {
                    val req = if (p.name in (tool.requiredParams
                            ?: emptyList())
                    ) " (required)" else " (optional)"
                    sb.appendLine("    - ${p.name}: ${p.type}$req — ${p.description}")
                }
                val example = buildExampleCall(tool)
                if (example != null) sb.appendLine("  Example: $example")
            } else {
                sb.appendLine("  Parameters: NONE")
                sb.appendLine("  Example: {\"tool\": \"${tool.name}\", \"arguments\": {}}")
            }
            sb.appendLine()
        }
        sb.appendLine("CRITICAL RULES:")
        sb.appendLine("- Your FIRST response MUST be ONLY the [MCP_CALL] block shown above. No thinking, no English text.")
        sb.appendLine("- Do NOT call any tool twice with the same arguments.")
        sb.appendLine("- Once you have all the data to answer the user, stop calling tools and just answer in Russian.")
        sb.appendLine("- The JSON MUST be valid — every quote and brace MUST be closed.")
        sb.appendLine()
        sb.appendLine("After calling a tool, you will receive the result. Use the data to continue or answer in Russian.")
        return sb.toString()
    }

    private fun buildExampleCall(tool: McpToolInfo): String? {
        val req = tool.parameters.filter { it.name in (tool.requiredParams ?: emptyList()) }
        if (req.isEmpty()) return null
        val args = req.joinToString(", ") { "\"${it.name}\": \"${exampleValue(it)}\"" }
        return "{\"tool\": \"${tool.name}\", \"arguments\": {$args}}"
    }

    private fun exampleValue(param: McpToolParam): String = when (param.name.lowercase()) {
        "name" -> "example"
        "id" -> "123"
        "data_type" -> "all"
        "format" -> "json"
        "raw_data", "summary_data", "raw_json", "summary_json" -> "<output from previous step>"
        else -> "value"
    }

    suspend fun executeToolCall(jsonString: String): String {
        val json = Json { ignoreUnknownKeys = true }
        println("🔧 MCP -> server: $jsonString")
        val obj = json.parseToJsonElement(jsonString).jsonObject
        val toolName =
            obj["tool"]?.jsonPrimitive?.content ?: throw McpException("Missing 'tool' in MCP call")
        val args =
            obj["arguments"]?.jsonObject?.mapValues { (_, v) ->
                when (v) {
                    is JsonPrimitive -> v.content
                    is JsonObject -> json.encodeToString(v)
                    is JsonArray -> json.encodeToString(v)
                }
            } ?: emptyMap()
        log("🔧 MCP вызов: $toolName $args")
        println("🔧 MCP -> server: $toolName($args)")
        val result = client.callTool(toolName, args)
        val text = result.content.filterIsInstance<TextContent>().joinToString("\n") { it.text }
        log("📦 MCP результат: ${text.take(200)}")
        println("📦 MCP <- server: ${text.take(500)}")
        return text
    }

    data class McpToolInfo(
        val name: String,
        val description: String,
        val parameters: List<McpToolParam>,
        val requiredParams: List<String>? = null
    )

    data class McpToolParam(
        val name: String,
        val type: String,
        val description: String
    )
}
