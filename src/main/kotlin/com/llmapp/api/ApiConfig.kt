package com.llmapp.api

import java.io.File
import java.util.Properties

object ApiConfig {
    private val properties: Properties by lazy {
        Properties().apply {
            val f = File("keys.properties")
            if (f.exists()) f.inputStream().use { load(it) }
        }
    }

    fun getBaseUrl(): String = "https://alcoserver.ru:4001"

    fun getLlmUser(): String = "llm_user"

    fun getLlmUserPassword(): String =
        properties.getProperty("llm_user_pwd")
            ?: error("llm_user_pwd не найден в keys.properties")

    fun getPrivateServerUser(): String = "llmuser"

    fun getPrivateServerPassword(): String =
        properties.getProperty("llmuser_pwd")
            ?: error("llmuser_pwd не найден в keys.properties")
}
