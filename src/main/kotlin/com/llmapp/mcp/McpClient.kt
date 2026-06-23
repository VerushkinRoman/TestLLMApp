package com.llmapp.mcp

import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

class McpClient(
    private val onLog: ((String) -> Unit)? = null
) {
    private var client: Client? = null
    private var transport: StreamableHttpClientTransport? = null
    private var httpClient: HttpClient? = null

    private fun log(msg: String) = onLog?.invoke(msg)

    suspend fun initialize(timeoutSeconds: Long = 15): InitResult {
        val url = "http://127.0.0.1:4455/mcp"
        log("🔗 Подключение к $url")

        val httpClient = HttpClient {
            install(SSE)
        }
        this.httpClient = httpClient

        val tr = StreamableHttpClientTransport(
            client = httpClient,
            url = url
        )
        transport = tr

        val mcpClient = Client(
            clientInfo = Implementation(
                name = "llm-chat-app",
                version = "1.0.0",
            )
        )
        client = mcpClient

        try {
            withTimeout(timeoutSeconds.seconds) {
                mcpClient.connect(tr)
            }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            throw McpException("Timeout after ${timeoutSeconds}s")
        }

        val serverVersion = mcpClient.serverVersion
        val result = InitResult(
            name = serverVersion?.name ?: "unknown",
            version = serverVersion?.version ?: "0.0.0",
        )
        log("✅ Подключено: ${result.name} v${result.version}")
        return result
    }

    suspend fun listTools(timeoutSeconds: Long = 15): List<Tool> {
        val mcpClient = client ?: throw McpException("Not connected")
        return try {
            withTimeout(timeoutSeconds.seconds) {
                mcpClient.listTools().tools
            }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            throw McpException("Timeout after ${timeoutSeconds}s")
        }
    }

    suspend fun callTool(
        name: String,
        arguments: Map<String, Any?> = emptyMap(),
        timeoutSeconds: Long = 30,
    ): CallToolResult {
        val mcpClient = client ?: throw McpException("Not connected")
        return try {
            withTimeout(timeoutSeconds.seconds) {
                mcpClient.callTool(name, arguments)
            }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            throw McpException("Timeout after ${timeoutSeconds}s")
        }
    }

    fun close() {
        log("👋 Закрытие...")
        runBlocking {
            transport?.close()
            client?.close()
            httpClient?.close()
        }
        client = null
        transport = null
        httpClient = null
        log("👋 Закрыто")
    }
}

class McpException(message: String) : Exception(message)

data class InitResult(
    val name: String,
    val version: String,
)
