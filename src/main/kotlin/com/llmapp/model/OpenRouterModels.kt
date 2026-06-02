package com.llmapp.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OpenRouterRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val stop: List<String>? = null,
    val temperature: Double? = null,
)

@Serializable
data class OpenRouterResponse(
    val choices: List<Choice>? = null,
    val error: ErrorResponse? = null,
    val usage: Usage? = null
)

@Serializable
data class Choice(
    val message: ResponseMessage?,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class ResponseMessage(
    val content: String?
)

@Serializable
data class ErrorResponse(
    val message: String,
    val code: Int? = null
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int? = null,
    @SerialName("completion_tokens") val completionTokens: Int? = null,
    @SerialName("total_tokens") val totalTokens: Int? = null
)
