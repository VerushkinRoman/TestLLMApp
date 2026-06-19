package com.llmapp.domain.usercase

import com.llmapp.api.ApiConfig
import com.llmapp.chat.ChatSession
import com.llmapp.ui.viewmodel.ChatViewState

class CompressionUseCase(
    private val onChatSessionUpdate: (ChatSession) -> Unit,
    private val onTokenStatsUpdate: () -> Unit
) {

    fun toggleCompression(state: ChatViewState, enabled: Boolean): ChatViewState {
        if (state.isDemoRunning) return state

        val newSession = ChatSession(
            apiKey = ApiConfig.getApiKey(),
            compressionEnabled = enabled,
            keepLastMessages = state.keepLastMessages,
            summarizeEvery = state.summarizeEvery
        )
        onChatSessionUpdate(newSession)
        onTokenStatsUpdate()

        return state.copy(
            compressionEnabled = enabled,
            keepLastMessages = state.keepLastMessages,
            summarizeEvery = state.summarizeEvery
        )
    }

    fun updateCompressionParams(
        state: ChatViewState,
        keepLast: Int,
        summarizeEvery: Int
    ): ChatViewState {
        if (state.isDemoRunning) return state

        val newSession = ChatSession(
            apiKey = ApiConfig.getApiKey(),
            compressionEnabled = state.compressionEnabled,
            keepLastMessages = keepLast,
            summarizeEvery = summarizeEvery
        )
        onChatSessionUpdate(newSession)
        onTokenStatsUpdate()

        return state.copy(
            keepLastMessages = keepLast,
            summarizeEvery = summarizeEvery
        )
    }
}
