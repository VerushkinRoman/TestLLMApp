package com.llmapp.rag.data

import com.llmapp.rag.domain.EmbeddingService
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URL
import java.util.Properties
import javax.net.ssl.HttpsURLConnection

private val hfJson = Json { ignoreUnknownKeys = true }

class HuggingFaceEmbeddingService(
    private val modelId: String = "ibm-granite/granite-embedding-97m-multilingual-r2",
    private val batchSize: Int = 10,
) : EmbeddingService {

    override val dimension: Int = 768

    private val apiToken: String = run {
        val props = Properties()
        File("keys.properties").inputStream().use { props.load(it) }
        props.getProperty("huggingface.key")
            ?: error("huggingface.key не найден в keys.properties")
    }

    private val hostname = "router.huggingface.co"
    private val fallbackIps = listOf(
        // router.huggingface.co
        "18.239.36.56",
        "18.239.36.12",
        "18.239.36.63",
        "18.239.36.44",
        // huggingface.co (запасные)
        "18.239.50.16",
        "18.239.50.103",
        "18.239.50.49",
        "18.239.50.80",
    )

    private val client = HttpClient {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            socketTimeoutMillis = 120_000
        }
    }

    override suspend fun embed(text: String): List<Float> {
        return embedBatch(listOf(text)).first()
    }

    private val sniSocketFactory: javax.net.ssl.SSLSocketFactory by lazy {
        val ctx = javax.net.ssl.SSLContext.getInstance("TLS")
        ctx.init(null, null, null)
        val sniHostname = javax.net.ssl.SNIHostName(hostname)
        object : javax.net.ssl.SSLSocketFactory() {
            private val delegate = ctx.socketFactory
            override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
            override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites
            override fun createSocket(s: java.net.Socket, host: String, port: Int, autoClose: Boolean): java.net.Socket {
                val socket = delegate.createSocket(s, host, port, autoClose) as javax.net.ssl.SSLSocket
                socket.sslParameters = socket.sslParameters.apply { serverNames = listOf(sniHostname) }
                return socket
            }
            override fun createSocket(host: String, port: Int): java.net.Socket {
                val socket = delegate.createSocket(host, port) as javax.net.ssl.SSLSocket
                socket.sslParameters = socket.sslParameters.apply { serverNames = listOf(sniHostname) }
                return socket
            }
            override fun createSocket(host: String, port: Int, localHost: java.net.InetAddress, localPort: Int): java.net.Socket {
                val socket = delegate.createSocket(host, port, localHost, localPort) as javax.net.ssl.SSLSocket
                socket.sslParameters = socket.sslParameters.apply { serverNames = listOf(sniHostname) }
                return socket
            }
            override fun createSocket(host: java.net.InetAddress, port: Int): java.net.Socket {
                val socket = delegate.createSocket(host, port) as javax.net.ssl.SSLSocket
                socket.sslParameters = socket.sslParameters.apply { serverNames = listOf(sniHostname) }
                return socket
            }
            override fun createSocket(host: java.net.InetAddress, port: Int, localHost: java.net.InetAddress, localPort: Int): java.net.Socket {
                val socket = delegate.createSocket(host, port, localHost, localPort) as javax.net.ssl.SSLSocket
                socket.sslParameters = socket.sslParameters.apply { serverNames = listOf(sniHostname) }
                return socket
            }
        }
    }

    private fun postBatchJavaHttp(chunk: List<String>, ip: String): String {
        val jsonBody = hfJson.encodeToString(mapOf("inputs" to chunk))

        val url = URL("https://$ip/hf-inference/models/$modelId")
        val conn = url.openConnection() as HttpsURLConnection

        conn.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
        conn.sslSocketFactory = sniSocketFactory
        conn.setRequestProperty("Host", hostname)
        conn.setRequestProperty("Authorization", "Bearer $apiToken")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 30_000
        conn.readTimeout = 120_000

        conn.outputStream.use { it.write(jsonBody.toByteArray()) }

        val code = conn.responseCode
        if (code in 200..299) {
            return conn.inputStream.use { it.readAllBytes().toString(Charsets.UTF_8) }
        }
        val err = conn.errorStream?.use { it.readAllBytes().toString(Charsets.UTF_8) } ?: ""
        throw RuntimeException("HF API error $code: $err")
    }

    private suspend fun postBatch(chunk: List<String>): String {
        return withContext(Dispatchers.IO) {
            client.post("https://$hostname/hf-inference/models/$modelId") {
                header(HttpHeaders.Authorization, "Bearer $apiToken")
                contentType(ContentType.Application.Json)
                setBody(mapOf("inputs" to chunk))
            }.let { response ->
                if (!response.status.isSuccess()) {
                    val errorBody = response.bodyAsText()
                    throw RuntimeException("HF API error ${response.status}: $errorBody")
                }
                response.bodyAsText()
            }
        }
    }

    override suspend fun embedBatch(texts: List<String>): List<List<Float>> {
        val results = mutableListOf<List<Float>>()
        val bodyTexts = texts.map { t ->
            t.replace("\"", "'").take(2048)
        }
        val chunks = bodyTexts.chunked(batchSize)

        println("🤗 [HF] Отправляю ${bodyTexts.size} текстов на $modelId, батчами по $batchSize (${chunks.size} запросов)...")

        for ((i, chunk) in chunks.withIndex()) {
            println("  🤗 [HF] Батч ${i + 1}/${chunks.size} (${chunk.size} текстов)...")

            val raw = try {
                postBatch(chunk)
            } catch (e: Exception) {
                println("  ⚠️ [HF] DNS/сеть: ${e::class.simpleName}: ${e.message}")
                println("  ⚠️ [HF] Пробую fallback через IP (Java HttpURLConnection с Host header)...")
                try {
                    val ip = fallbackIps[chunk.hashCode().and(Int.MAX_VALUE) % fallbackIps.size]
                    postBatchJavaHttp(chunk, ip)
                } catch (e2: Exception) {
                    println("  ❌ [HF] Fallback тоже не сработал: ${e2::class.simpleName}: ${e2.message}")
                    e2.printStackTrace()
                    throw RuntimeException("HF API недоступен (DNS + IP fallback): ${e2.message}", e2)
                }
            }

            println("  ✅ [HF] Ответ (первые 200): ${raw.take(200)}")
            val batchResult: List<List<Float>> = try {
                Json.decodeFromString(raw)
            } catch (e: Exception) {
                println("  ❌ [HF] Ошибка парсинга ответа: ${e.message}")
                println("  ❌ [HF] Сырой ответ: $raw")
                throw RuntimeException("HF response parse error: ${e.message}", e)
            }
            results.addAll(batchResult)
        }

        println("🤗 [HF] Успешно: ${results.size} векторов, ${results.firstOrNull()?.size} размерность")
        return results
    }
}
