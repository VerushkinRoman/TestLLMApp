package com.llmapp.agent

import com.llmapp.model.ResponseControl

interface IAgent {
    suspend fun processRequest(userInput: String): LLMResponse
    fun changeModel(newModel: String)
    fun updateResponseControl(control: ResponseControl)
    fun clearHistory()
    fun refreshApiKey(newApiKey: String)
}
