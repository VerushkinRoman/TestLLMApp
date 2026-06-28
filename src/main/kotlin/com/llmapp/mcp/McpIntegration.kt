package com.llmapp.mcp

import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class McpIntegration(
    private val name: String = "mcp",
    serverUrl: String = "https://alcoserver.ru:4456/mcp",
    private val onLog: ((String) -> Unit)? = null
) {
    private val client = McpClient(serverUrl, onLog)
    private var connected = false
    private var tools: List<McpToolInfo> = emptyList()
    private val pipelineTools = setOf("save_data")

    fun getToolNames(): List<String> = tools.map { it.name }
    fun getTools(): List<McpToolInfo> = tools
    fun hasPipelineTools(): Boolean = tools.map { it.name }.toSet().containsAll(pipelineTools)

    private fun log(msg: String) = onLog?.invoke(msg)

    private fun parseTools(rawTools: List<Tool>) {
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
    }

    suspend fun connect(): InitResult {
        val result = client.initialize()
        val rawTools = client.listTools()
        parseTools(rawTools)
        connected = true
        return result
    }

    suspend fun refreshTools(): Boolean {
        if (!connected) return false
        return try {
            val rawTools = client.listTools()
            parseTools(rawTools)
            true
        } catch (e: Exception) {
            println("⚠️ MCP refresh failed for $name: ${e.message?.take(100)}")
            false
        }
    }

    fun disconnect() {
        client.close()
        connected = false
        tools = emptyList()
    }

    fun isConnected(): Boolean = connected

    suspend fun executeToolCall(jsonString: String): String {
        val json = Json { ignoreUnknownKeys = true }
        println("🔧 MCP -> server: $jsonString")
        val obj = json.parseToJsonElement(jsonString).jsonObject
        val toolName =
            obj["tool"]?.jsonPrimitive?.content ?: throw McpException("Missing 'tool' in MCP call")
        val rawArgs =
            (obj["arguments"]?.jsonObject ?: obj["args"]?.jsonObject)?.mapValues { (_, v) ->
                when (v) {
                    is JsonPrimitive -> v.content
                    is JsonObject -> json.encodeToString(v)
                    is JsonArray -> json.encodeToString(v)
                }
            } ?: emptyMap()

        val args = if (toolName == "get_group" && "name" !in rawArgs && "group" in rawArgs) {
            println("🔧 MCP: auto-remap group→name for get_group")
            rawArgs.mapKeys { (k, _) -> if (k == "group") "name" else k }
        } else rawArgs

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
