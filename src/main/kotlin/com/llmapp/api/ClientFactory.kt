package com.llmapp.api

object ClientFactory {
    fun create(apiKey: String): RouterClient = KodikRouterClient(apiKey)
}
