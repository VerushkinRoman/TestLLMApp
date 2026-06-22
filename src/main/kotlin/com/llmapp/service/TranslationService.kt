package com.llmapp.service

import com.llmapp.api.ApiConfig
import com.llmapp.api.OpenRouterClient
import com.llmapp.model.ChatMessage
import com.llmapp.model.OpenRouterRequest
import kotlinx.serialization.json.Json
import java.io.File

class TranslationService {
    private val json = Json { ignoreUnknownKeys = true }

    private val client by lazy {
        try {
            OpenRouterClient(ApiConfig.getApiKey())
        } catch (e: Exception) {
            println("⚠️ TranslationService: ${e.message}")
            null
        }
    }

    private val cacheFile: File by lazy {
        File(System.getProperty("user.home"), ".llmapp/translation_cache.json")
    }

    private val cache: MutableMap<String, String> by lazy { loadCache() }

    private fun loadCache(): MutableMap<String, String> {
        if (!cacheFile.exists()) return mutableMapOf()
        return try {
            json.decodeFromString<Map<String, String>>(cacheFile.readText()).toMutableMap()
        } catch (e: Exception) {
            println("⚠️ Failed to load translation cache: ${e.message}")
            mutableMapOf()
        }
    }

    private fun saveCache() {
        cacheFile.parentFile.mkdirs()
        cacheFile.writeText(json.encodeToString(cache))
    }

    fun getCached(key: String): String? = cache[key]

    fun hasCached(key: String): Boolean = key in cache

    suspend fun translate(key: String, text: String, targetLang: String = "ru"): String {
        val c = client
            ?: throw RuntimeException("OpenRouter не настроен. Добавьте openrouter.properties с API ключом.")

        val request = OpenRouterRequest(
            model = "openai/gpt-oss-20b:free",
            messages = listOf(
                ChatMessage(
                    "system",
                    "Translate the following text to $targetLang. Return ONLY the translated text. No explanations, no quotes, no formatting."
                ),
                ChatMessage("user", text),
            ),
            maxTokens = 512,
            temperature = 0.1,
        )

        val response = c.sendRequest(request)
        val result = response.choices?.firstOrNull()?.message?.content?.trim()
            ?: response.error?.message?.let { throw RuntimeException(it) }
            ?: throw RuntimeException("Translation failed: empty response")

        cache[key] = result
        saveCache()
        return result
    }

    fun clearCache(key: String) {
        cache.remove(key)
        saveCache()
    }
}
