package com.llmapp.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class McpIntegration(
    private val onLog: ((String) -> Unit)? = null,
) {
    private var connected = false

    fun getToolNames(): List<String> = GitHubMcpTools.getToolNames()

    fun getTools(): List<McpToolInfo> = GitHubMcpTools.getToolDefinitions()

    fun connect(): InitResult {
        connected = true
        return InitResult(name = "github-tools", version = "1.0.0")
    }

    fun disconnect() {
        connected = false
    }

    fun isConnected(): Boolean = connected

    suspend fun executeToolCall(jsonString: String): String {
        val json = Json { ignoreUnknownKeys = true }
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

        log("🔧 Tool call: $toolName $rawArgs")
        val result = GitHubMcpTools.executeTool(toolName, rawArgs)
        log("📦 Result: ${result.take(200)}")
        return result
    }

    private fun log(msg: String) {
        onLog?.invoke(msg)
        println(msg)
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

    data class InitResult(
        val name: String,
        val version: String
    )

    class McpException(message: String) : Exception(message)
}
