package com.llmapp.rag.data

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

data class GitHubEntry(
    val name: String,
    val path: String,
    val type: String,
)

object GitHubApi {

    private const val REPO_OWNER = "VerushkinRoman"
    private const val REPO_NAME = "CalendarKMP"
    private const val REPO_BRANCH = "master"
    private const val API_BASE = "https://api.github.com"

    private var httpClient: HttpClient? = null

    private var _cachedToken: String? = null
    var cachedToken: String?
        get() = _cachedToken
        set(value) { _cachedToken = value }

    fun getToken(): String? {
        _cachedToken?.let {
            println("🔑 Token from cache: ${it.take(10)}...")
            return it
        }

        // 1. Env vars
        val envToken = System.getenv("GITHUB_TOKEN")
            ?: System.getenv("GITHUB_PERSONAL_ACCESS_TOKEN")
        if (envToken != null) {
            println("🔑 Token from env: ${envToken.take(10)}...")
            _cachedToken = envToken
            return envToken
        }

        // 2. keys.properties
        val fileToken = getTokenFromKeys()
        if (fileToken != null) {
            println("🔑 Token from keys.properties: ${fileToken.take(10)}...")
            _cachedToken = fileToken
            return fileToken
        }

        // 3. git credential fill (macOS Keychain, etc.)
        val gitToken = getTokenFromGitCredential()
        if (gitToken != null) {
            println("🔑 Token from git credential: ${gitToken.take(10)}...")
            _cachedToken = gitToken
            return gitToken
        }

        println("🔑 No token found")
        return null
    }

    private fun getTokenFromKeys(): String? {
        return try {
            val file = java.io.File("keys.properties")
            if (!file.exists()) return null
            file.readLines()
                .firstOrNull { it.startsWith("github.token=") }
                ?.substringAfter("=")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun getTokenFromGitCredential(): String? {
        return try {
            val process = ProcessBuilder("git", "credential", "fill")
                .redirectErrorStream(true)
                .start()

            val input = "protocol=https\nhost=github.com\n"
            process.outputStream.buffered().use { it.write(input.toByteArray()); it.flush() }

            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()

            // password field from git credential
            output.lines()
                .firstOrNull { it.startsWith("password=") }
                ?.substringAfter("=")
                ?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    fun saveTokenToKeys(token: String) {
        try {
            val keysFile = java.io.File("keys.properties")
            val lines = if (keysFile.exists()) keysFile.readLines().toMutableList() else mutableListOf()
            lines.removeAll { it.startsWith("github.token=") }
            lines.add("github.token=$token")
            keysFile.writeText(lines.joinToString("\n"))
            println("✅ Токен сохранён в keys.properties")
        } catch (e: Exception) {
            println("⚠️ Не удалось сохранить токен: ${e.message}")
        }
    }

    fun ensureToken(): Boolean {
        if (getToken() != null) return true

        // Попробовать gh CLI
        val ghToken = getTokenFromGhCli()
        if (ghToken != null) {
            _cachedToken = ghToken
            saveTokenToKeys(ghToken)
            return true
        }

        return false
    }

    private fun getTokenFromGhCli(): String? {
        return try {
            val process = ProcessBuilder("gh", "auth", "token")
                .redirectErrorStream(true)
                .start()
            val token = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            token.takeIf { it.isNotBlank() && !it.contains("error", ignoreCase = true) }
        } catch (_: Exception) {
            null
        }
    }

    fun openBrowserForToken() {
        val url = "https://github.com/settings/tokens/new?scopes=repo&description=TestLLMApp"
        try {
            val os = System.getProperty("os.name").lowercase()
            when {
                os.contains("mac") -> Runtime.getRuntime().exec(arrayOf("open", url))
                os.contains("win") -> Runtime.getRuntime().exec(arrayOf("cmd", "/c", "start", url))
                else -> Runtime.getRuntime().exec(arrayOf("xdg-open", url))
            }
            println("\n🔑 Открыт браузер для создания токена.")
            println("   1. Создайте токен в браузере (отметьте 'repo')")
            println("   2. Скопируйте токен")
            println("   3. Вставьте в поле ввода в приложении\n")
        } catch (_: Exception) {
            println("\n🔑 Откройте вручную: $url")
        }
    }

    private fun getHttpClient(): HttpClient {
        httpClient?.let { return it }
        val client = HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        httpClient = client
        return client
    }

    suspend fun readFile(path: String, branch: String = REPO_BRANCH): String? {
        val client = getHttpClient()
        val url = "$API_BASE/repos/$REPO_OWNER/$REPO_NAME/contents/$path?ref=$branch"
        val token = getToken()
        println("🔍 GitHub DEBUG: url=$url, token=${token?.take(10)}..., tokenLen=${token?.length}")
        return try {
            val response = client.get(url) {
                header("Accept", "application/vnd.github.v3+json")
                token?.let { header("Authorization", "Bearer $it") }
            }
            println("🔍 GitHub DEBUG: ${response.status} for $path")
            if (!response.status.isSuccess()) {
                val body = response.bodyAsText()
                println("⚠️ GitHub API ${response.status} для $path: ${body.take(300)}")
                return null
            }
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val encoding = json["encoding"]?.jsonPrimitive?.content
            if (encoding == "base64") {
                val raw = json["content"]?.jsonPrimitive?.content ?: return null
                String(Base64.getDecoder().decode(raw.replace("\n", "")))
            } else {
                json["content"]?.jsonPrimitive?.content
            }
        } catch (e: Exception) {
            println("⚠️ GitHub API ошибка для $path: ${e.message}")
            null
        }
    }

    suspend fun getFileSha(path: String, branch: String = REPO_BRANCH): String? {
        val client = getHttpClient()
        val url = "$API_BASE/repos/$REPO_OWNER/$REPO_NAME/contents/$path?ref=$branch"
        return try {
            val response = client.get(url) {
                header("Accept", "application/vnd.github.v3+json")
                getToken()?.let { header("Authorization", "Bearer $it") }
            }
            if (!response.status.isSuccess()) return null
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            json["sha"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            println("⚠️ GitHub getFileSha ошибка: ${e.message}")
            null
        }
    }

    @Suppress("unused")
    suspend fun commitFile(
        path: String,
        content: String,
        message: String,
        branch: String = REPO_BRANCH
    ): Boolean {
        val client = getHttpClient()
        val url = "$API_BASE/repos/$REPO_OWNER/$REPO_NAME/contents/$path"
        val sha = getFileSha(path, branch)
        val token = getToken() ?: run {
            println("❌ GITHUB_TOKEN не задан — коммит невозможен")
            return false
        }
        return try {
            val body = buildMap {
                put("message", message)
                put("content", Base64.getEncoder().encodeToString(content.toByteArray()))
                put("branch", branch)
                sha?.let { put("sha", it) }
            }
            val response = client.put(url) {
                header("Accept", "application/vnd.github.v3+json")
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(JsonObject.serializer(), JsonObject(body.mapValues { JsonPrimitive(it.value) })))
            }
            if (response.status.isSuccess()) {
                println("✅ Файл $path закоммичен в $branch")
                true
            } else {
                println("❌ GitHub API ${response.status} при коммите $path")
                println(response.bodyAsText())
                false
            }
        } catch (e: Exception) {
            println("❌ Ошибка коммита $path: ${e.message}")
            false
        }
    }

    @Suppress("unused")
    suspend fun createBranch(branchName: String, fromBranch: String = REPO_BRANCH): Boolean {
        val client = getHttpClient()
        val token = getToken() ?: run {
            println("❌ GITHUB_TOKEN не задан")
            return false
        }
        return try {
            val baseSha = getBranchSha(fromBranch) ?: return false
            val url = "$API_BASE/repos/$REPO_OWNER/$REPO_NAME/git/refs"
            val body = buildMap {
                put("ref", "refs/heads/$branchName")
                put("sha", baseSha)
            }
            val response = client.post(url) {
                header("Accept", "application/vnd.github.v3+json")
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(JsonObject.serializer(), JsonObject(body.mapValues { JsonPrimitive(it.value) })))
            }
            if (response.status.isSuccess()) {
                println("✅ Ветка $branchName создана из $fromBranch")
                true
            } else {
                println("❌ GitHub API ${response.status} при создании ветки")
                println(response.bodyAsText())
                false
            }
        } catch (e: Exception) {
            println("❌ Ошибка создания ветки: ${e.message}")
            false
        }
    }

    @Suppress("unused")
    suspend fun createPullRequest(
        title: String,
        body: String,
        headBranch: String,
        baseBranch: String = REPO_BRANCH
    ): String? {
        val client = getHttpClient()
        val token = getToken() ?: run {
            println("❌ GITHUB_TOKEN не задан")
            return null
        }
        return try {
            val url = "$API_BASE/repos/$REPO_OWNER/$REPO_NAME/pulls"
            val requestBody = buildMap {
                put("title", title)
                put("body", body)
                put("head", headBranch)
                put("base", baseBranch)
            }
            val response = client.post(url) {
                header("Accept", "application/vnd.github.v3+json")
                header("Authorization", "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(Json.encodeToString(JsonObject.serializer(), JsonObject(requestBody.mapValues { JsonPrimitive(it.value) })))
            }
            if (response.status.isSuccess()) {
                val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                val prUrl = json["html_url"]?.jsonPrimitive?.content
                println("✅ PR создан: $prUrl")
                prUrl
            } else {
                println("❌ GitHub API ${response.status} при создании PR")
                println(response.bodyAsText())
                null
            }
        } catch (e: Exception) {
            println("❌ Ошибка создания PR: ${e.message}")
            null
        }
    }

    private suspend fun getBranchSha(branch: String): String? {
        val client = getHttpClient()
        val url = "$API_BASE/repos/$REPO_OWNER/$REPO_NAME/branches/$branch"
        return try {
            val response = client.get(url) {
                header("Accept", "application/vnd.github.v3+json")
                getToken()?.let { header("Authorization", "Bearer $it") }
            }
            if (!response.status.isSuccess()) return null
            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            json["commit"]?.jsonObject?.get("sha")?.jsonPrimitive?.content
        } catch (e: Exception) {
            println("⚠️ getBranchSha ошибка: ${e.message}")
            null
        }
    }

    @Suppress("unused")
    suspend fun listPullRequests(state: String = "open"): List<JsonObject> {
        val client = getHttpClient()
        val url = "$API_BASE/repos/$REPO_OWNER/$REPO_NAME/pulls?state=$state"
        return try {
            val response = client.get(url) {
                header("Accept", "application/vnd.github.v3+json")
                getToken()?.let { header("Authorization", "Bearer $it") }
            }
            if (!response.status.isSuccess()) return emptyList()
            val jsonArray = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            jsonArray.map { element -> element.jsonObject }
        } catch (e: Exception) {
            println("⚠️ listPullRequests ошибка: ${e.message}")
            emptyList()
        }
    }

    suspend fun listDirectory(path: String = "", branch: String = REPO_BRANCH): List<GitHubEntry> {
        val client = getHttpClient()
        val url = if (path.isEmpty()) {
            "$API_BASE/repos/$REPO_OWNER/$REPO_NAME/contents?ref=$branch"
        } else {
            "$API_BASE/repos/$REPO_OWNER/$REPO_NAME/contents/$path?ref=$branch"
        }
        return try {
            val response = client.get(url) {
                header("Accept", "application/vnd.github.v3+json")
                getToken()?.let { header("Authorization", "Bearer $it") }
            }
            if (!response.status.isSuccess()) return emptyList()
            val jsonArray = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            jsonArray.map { element ->
                val obj = element.jsonObject
                GitHubEntry(
                    name = obj["name"]?.jsonPrimitive?.content ?: "",
                    path = obj["path"]?.jsonPrimitive?.content ?: "",
                    type = obj["type"]?.jsonPrimitive?.content ?: "",
                )
            }
        } catch (e: Exception) {
            println("⚠️ listDirectory ошибка для $path: ${e.message}")
            emptyList()
        }
    }

    suspend fun getLatestCommitSha(branch: String = REPO_BRANCH): String? {
        val client = getHttpClient()
        val url = "$API_BASE/repos/$REPO_OWNER/$REPO_NAME/commits?sha=$branch&per_page=1"
        return try {
            val response = client.get(url) {
                header("Accept", "application/vnd.github.v3+json")
                getToken()?.let { header("Authorization", "Bearer $it") }
            }
            if (!response.status.isSuccess()) return null
            val jsonArray = Json.parseToJsonElement(response.bodyAsText()).jsonArray
            jsonArray.firstOrNull()?.jsonObject?.get("sha")?.jsonPrimitive?.content
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("unused")
    fun close() {
        httpClient?.close()
        httpClient = null
    }
}