package com.llmapp.domain.usercase

import com.llmapp.agent.MemoryAwareAgent
import com.llmapp.memory.ProjectConstraints
import com.llmapp.memory.UserProfile
import com.llmapp.ui.components.MemorySettings
import com.llmapp.ui.viewmodel.ChatViewState

class MemoryUseCase(
    private val memoryAwareAgent: MemoryAwareAgent
) {

    fun updateMemorySettings(state: ChatViewState, settings: MemorySettings): ChatViewState {
        memoryAwareAgent.useShortTerm = settings.useShortTerm
        memoryAwareAgent.useWorkingMemory = settings.useWorkingMemory
        memoryAwareAgent.useLongTerm = settings.useLongTerm

        println("📊 Настройки памяти обновлены: STM=${settings.useShortTerm}, WM=${settings.useWorkingMemory}, LTM=${settings.useLongTerm}")
        return state
    }

    fun updateUserProfile(state: ChatViewState, profile: UserProfile): ChatViewState {
        memoryAwareAgent.updateProfile(profile)
        println("👤 Профиль обновлен: ${profile.name}")
        return state.copy(userProfile = profile)
    }

    fun updateProjectConstraints(
        state: ChatViewState,
        constraints: ProjectConstraints
    ): ChatViewState {
        memoryAwareAgent.updateConstraints(constraints)
        println("🔧 Ограничения обновлены")
        return state.copy(projectConstraints = constraints)
    }

    fun resetWorkingMemory(state: ChatViewState): ChatViewState {
        memoryAwareAgent.clearWorkingMemory()
        println("💼 Рабочая память сброшена")
        return state
    }
}
