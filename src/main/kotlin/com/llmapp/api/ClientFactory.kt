package com.llmapp.api

object ClientFactory {
    @Suppress("UNUSED_PARAMETER")
    fun create(apiKey: String? = null): RouterClient = ApiClient()
}
