package com.llmapp.api

import java.io.File
import java.util.Properties

object ApiConfig {
    private const val PROPS_FILE = "openrouter.properties"

    fun getApiKey(): String {
        val propsFile = File(PROPS_FILE)
        val props = Properties()
        props.load(propsFile.inputStream())
        return props.getProperty("api.key")
    }
}
