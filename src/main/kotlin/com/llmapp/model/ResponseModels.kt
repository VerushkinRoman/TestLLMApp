package com.llmapp.model

data class ResponseControl(
    val formatDescription: String? = null,
    val maxTokens: Int? = null,
    val stopSequences: List<String>? = null,
    val temperature: Double? = null,
    val enabled: Boolean = true
)
