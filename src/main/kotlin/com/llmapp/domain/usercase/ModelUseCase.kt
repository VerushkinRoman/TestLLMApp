package com.llmapp.domain.usercase

import com.llmapp.chat.ChatSession
import com.llmapp.model.freeModels
import com.llmapp.ui.viewmodel.ChatViewState

class ModelUseCase(
    private val chatSession: ChatSession,
    private val onTokenStatsUpdate: () -> Unit
) {

    fun changeModel(state: ChatViewState, modelId: String): ChatViewState {
        if (state.isDemoRunning) return state

        chatSession.changeModel(modelId)
        onTokenStatsUpdate()
        return state.copy(
            currentModel = modelId,
            availableModels = freeModels
        )
    }
}
