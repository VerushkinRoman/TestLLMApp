package com.llmapp.mcp

import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlin.time.Duration.Companion.seconds

class McpClient(
    private val onLog: ((String) -> Unit)? = null
) {
    private var client: Client? = null
    private var transport: StdioClientTransport? = null
    private var process: Process? = null

    private fun log(msg: String) = onLog?.invoke(msg)

    suspend fun initialize(timeoutSeconds: Long = 15): InitResult {
        val command = resolveCommand()
        log("✅ Команда: $command")
        val parts = command.split(" ").filter { it.isNotBlank() }
        val proc = withContext(Dispatchers.IO) {
            val pb = ProcessBuilder(parts)
            pb.redirectErrorStream(true)
            pb.start()
        }
        process = proc

        log("📖 Чтение баннера...")
        val banner = readBanner(proc.inputStream)
        if (banner != null) {
            log("ℹ️ Сервер: $banner")
        } else {
            throw McpException("Process exited without banner")
        }

        log("📖 Подключение через StdioClientTransport...")
        val input = proc.inputStream.asSource().buffered()
        val output = proc.outputStream.asSink().buffered()
        val tr = StdioClientTransport(
            input = input,
            output = output,
            error = null,
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
        }
        process?.destroyForcibly()
        process = null
        client = null
        transport = null
        log("👋 Закрыто")
    }

    private fun readBanner(inputStream: java.io.InputStream): String? {
        val baos = java.io.ByteArrayOutputStream()
        var b = inputStream.read()
        while (b != -1 && b != 10) {
            if (b != 13) baos.write(b)
            b = inputStream.read()
        }
        return if (baos.size() > 0) baos.toString("UTF-8") else null
    }

    private fun resolveCommand(): String {
        val brewPath = "/opt/homebrew/bin/onside-football-mcp"
        if (java.io.File(brewPath).canExecute()) {
            log("✅ Найден: $brewPath")
            return brewPath
        }
        val paths = System.getenv("PATH")?.split(":") ?: emptyList()
        for (dir in paths) {
            val candidate = "$dir/onside-football-mcp"
            if (java.io.File(candidate).canExecute()) {
                log("✅ Найден: $candidate")
                return candidate
            }
        }
        log("⚠️ onside-football-mcp не найден, через npx...")
        return "npx -y onside-football-mcp"
    }
}

class McpException(message: String) : Exception(message)

data class InitResult(
    val name: String,
    val version: String,
)
