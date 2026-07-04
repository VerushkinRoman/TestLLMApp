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
            compressAfterTokens = state.compressAfterTokens
        )
        onChatSessionUpdate(newSession)
        onTokenStatsUpdate()

        return state.copy(
            compressionEnabled = enabled,
            keepLastMessages = state.keepLastMessages,
            compressAfterTokens = state.compressAfterTokens
        )
    }

    fun updateCompressionParams(
        state: ChatViewState,
        keepLast: Int,
        compressAfterTokens: Int
    ): ChatViewState {
        if (state.isDemoRunning) return state

        val newSession = ChatSession(
            apiKey = ApiConfig.getApiKey(),
            compressionEnabled = state.compressionEnabled,
            keepLastMessages = keepLast,
            compressAfterTokens = compressAfterTokens
        )
        onChatSessionUpdate(newSession)
        onTokenStatsUpdate()

        return state.copy(
            keepLastMessages = keepLast,
            compressAfterTokens = compressAfterTokens
        )
    }
}
