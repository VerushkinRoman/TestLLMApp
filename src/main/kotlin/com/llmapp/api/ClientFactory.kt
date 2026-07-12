package com.llmapp.api

import com.llmapp.model.RouterRequest
import com.llmapp.model.RouterResponse

object ClientFactory {
    private val switchingClient = SwitchingClient()

    fun create(): RouterClient = switchingClient

    fun setUseLocal(value: Boolean) {
        switchingClient.useLocal = value
        if (value) switchingClient.usePrivate = false
        println("🔀 Клиент переключен на ${modeName()}")
    }

    fun setUsePrivate(value: Boolean) {
        switchingClient.usePrivate = value
        if (value) switchingClient.useLocal = false
        println("🔀 Клиент переключен на ${modeName()}")
    }

    private fun modeName(): String = when {
        switchingClient.useLocal -> "локальный (Ollama)"
        switchingClient.usePrivate -> "приватный (LLMServer)"
        else -> "облачный (KodikRouter)"
    }
}

class SwitchingClient : RouterClient {
    @Volatile
    var useLocal: Boolean = false

    @Volatile
    var usePrivate: Boolean = false

    private val localClient = OllamaClient()
    private val cloudClient = ApiClient()
    private val privateClient = PrivateServerClient()

    override suspend fun sendRequest(request: RouterRequest): RouterResponse {
        return when {
            useLocal -> localClient.sendRequest(request)
            usePrivate -> privateClient.sendRequest(request)
            else -> cloudClient.sendRequest(request)
        }
    }
}
