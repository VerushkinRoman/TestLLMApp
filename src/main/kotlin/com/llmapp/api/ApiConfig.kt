package com.llmapp.api

import java.io.File
import java.util.Properties

object ApiConfig {
    private const val PROPS_FILE = "openrouter.properties"

    private var apiKeys: List<String> = emptyList()
    private var currentKeyIndex = 0

    private fun loadApiKeys() {
        if (apiKeys.isEmpty()) {
            val propsFile = File(PROPS_FILE)
            val props = Properties()
            props.load(propsFile.inputStream())

            apiKeys = buildList {
                props.getProperty("api.key")?.let { add(it) }
                props.getProperty("api.key2")?.let { add(it) }
                var i = 3
                while (true) {
                    val key = props.getProperty("api.key$i")
                    if (key != null) add(key) else break
                    i++
                }
            }

            if (apiKeys.isEmpty()) {
                throw IllegalStateException("No API keys found in $PROPS_FILE")
            }

            println("✅ Загружено ${apiKeys.size} API ключей")
        }
    }

    fun getApiKey(): String {
        loadApiKeys()
        return apiKeys[currentKeyIndex]
    }

    fun rotateToNextKey(): String {
        loadApiKeys()
        val oldIndex = currentKeyIndex
        currentKeyIndex = (currentKeyIndex + 1) % apiKeys.size
        println("🔄 Переключение с ключа #${oldIndex + 1} на ключ #${currentKeyIndex + 1}")
        return apiKeys[currentKeyIndex]
    }

    fun getCurrentKeyIndex(): Int = currentKeyIndex + 1

    fun getTotalKeysCount(): Int {
        loadApiKeys()
        return apiKeys.size
    }

    fun resetKeyRotation() {
        currentKeyIndex = 0
        println("🔄 Сброс ротации ключей к первому")
    }
}
