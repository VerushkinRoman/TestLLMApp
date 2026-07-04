package com.llmapp.api

import com.llmapp.model.RouterRequest
import com.llmapp.model.RouterResponse

interface RouterClient {
    suspend fun sendRequest(request: RouterRequest): RouterResponse
}
