package com.llmapp.api

import com.llmapp.model.RouterRequest
import com.llmapp.model.RouterResponse

object ClientFactory {
    private val switchingClient = SwitchingClient()

    fun create(): RouterClient = switchingClient

    fun setUseLocal(value: Boolean) {
        switchingClient.useLocal = value
        println("🔀 Клиент переключен на ${if (value) "локальный (Ollama)" else "облачный (KodikRouter)"}")
    }

}

class SwitchingClient : RouterClient {
    @Volatile
    var useLocal: Boolean = false

    private val localClient = OllamaClient()
    private val cloudClient = ApiClient()

    override suspend fun sendRequest(request: RouterRequest): RouterResponse {
        return if (useLocal) {
            localClient.sendRequest(request)
        } else {
            cloudClient.sendRequest(request)
        }
    }
}
