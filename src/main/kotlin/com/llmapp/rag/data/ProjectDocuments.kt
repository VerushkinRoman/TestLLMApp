package com.llmapp.rag.data

import com.llmapp.rag.domain.Document
import com.llmapp.rag.domain.Section
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.Base64

object ProjectDocuments {

    private const val REPO_OWNER = "VerushkinRoman"
    private const val REPO_NAME = "CalendarKMP"
    private const val REPO_BRANCH = "master"
    private const val DOCS_DIR = "project/docs"

    private val DOC_FILES = listOf(
        "README.md" to "Обзор проекта",
        "$DOCS_DIR/architecture.md" to "Архитектура",
        "$DOCS_DIR/data-schemas.md" to "Схемы данных",
        "$DOCS_DIR/api.md" to "API",
        "$DOCS_DIR/features.md" to "Фичи",
        "$DOCS_DIR/classes.md" to "Справочник классов",
        "$DOCS_DIR/navigation.md" to "Навигация",
        "$DOCS_DIR/use-cases.md" to "Use Cases",
    )

    private var httpClient: HttpClient? = null

    fun getAll(): List<Document> = runBlocking {
        println("🌐 Читаю документы из GitHub...")

        if (!GitHubApi.ensureToken()) {
            println("❌ GitHub токен не задан. Установите GITHUB_TOKEN или введите токен.")
            return@runBlocking emptyList()
        }

        val docs = loadDocsFromGitHub()
        if (docs.isEmpty()) {
            println("❌ Не удалось загрузить документы из GitHub")
        }
        docs
    }

    private suspend fun loadDocsFromGitHub(): List<Document> {
        val client = getHttpClient()
        val docs = mutableListOf<Document>()

        for ((path, title) in DOC_FILES) {
            val content = fetchFileFromGitHub(client, path)
            if (content != null) {
                docs.add(Document(
                    id = docId(path),
                    title = "CalendarKMP — $title",
                    source = File(path).name,
                    content = content,
                    sections = if (File(path).name == "classes.md") {
                        parseClassSections(content)
                    } else {
                        extractSections(content)
                    }
                ))
            }
        }
        return docs
    }

    private suspend fun fetchFileFromGitHub(client: HttpClient, path: String): String? {
        val url = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/contents/$path?ref=$REPO_BRANCH"
        val token = GitHubApi.getToken()
        return try {
            val response = client.get(url) {
                header("Accept", "application/vnd.github.v3+json")
                token?.let { header("Authorization", "Bearer $it") }
            }
            if (!response.status.isSuccess()) {
                println("⚠️ GitHub API ${response.status} для $path")
                return null
            }
            val jsonElement = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val encoding = jsonElement["encoding"]?.jsonPrimitive?.content
            if (encoding == "base64") {
                val rawContent = jsonElement["content"]?.jsonPrimitive?.content ?: return null
                String(Base64.getDecoder().decode(rawContent.replace("\n", "")))
            } else {
                jsonElement["content"]?.jsonPrimitive?.content
            }
        } catch (e: Exception) {
            println("⚠️ GitHub API ошибка для $path: ${e.message}")
            null
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

    private fun docId(path: String): String {
        val name = File(path).nameWithoutExtension
        return "calendar_kmp_${name}"
    }

    private fun parseClassSections(content: String): List<Section> {
        val sections = mutableListOf<Section>()
        val featurePattern = Regex("""## (\d+)\. Feature: ([^\n]+)""")
        val matches = featurePattern.findAll(content).toList()

        matches.forEachIndexed { index, match ->
            val start = match.range.first
            val end = if (index < matches.size - 1) {
                matches[index + 1].range.first
            } else {
                content.length
            }
            sections.add(Section(
                heading = match.groupValues[2],
                content = content.substring(start, end).trim()
            ))
        }
        return sections
    }

    private fun extractSections(content: String): List<Section> {
        val sections = mutableListOf<Section>()
        val lines = content.lines()
        var currentTitle = ""
        val currentContent = StringBuilder()
        var parentTitle = ""

        for (line in lines) {
            when {
                line.startsWith("## ") -> {
                    if (currentTitle.isNotEmpty() && currentContent.isNotEmpty()) {
                        sections.add(Section(
                            heading = currentTitle,
                            content = currentContent.toString().trim()
                        ))
                    }
                    parentTitle = line.removePrefix("## ").trim()
                    currentTitle = parentTitle
                    currentContent.clear()
                }
                line.startsWith("### ") -> {
                    if (currentTitle.isNotEmpty() && currentContent.isNotEmpty()) {
                        sections.add(Section(
                            heading = currentTitle,
                            content = currentContent.toString().trim()
                        ))
                    }
                    val subTitle = line.removePrefix("### ").trim()
                    currentTitle = if (parentTitle.isNotEmpty()) "$parentTitle — $subTitle" else subTitle
                    currentContent.clear()
                }
                else -> currentContent.appendLine(line)
            }
        }

        if (currentTitle.isNotEmpty() && currentContent.isNotEmpty()) {
            sections.add(Section(
                heading = currentTitle,
                content = currentContent.toString().trim()
            ))
        }
        return sections
    }
}