package com.llmapp.domain.usercase

import com.llmapp.memory.UserProfile
import com.llmapp.ui.components.NamedProfile
import com.llmapp.ui.components.ProfileManager
import com.llmapp.ui.viewmodel.ChatViewState

class ProfileUseCase(
    private val profileManager: ProfileManager
) {

    fun loadPresetProfile(state: ChatViewState, preset: NamedProfile): ChatViewState {
        val profile = profileManager.createProfileFromPreset(preset)
        profileManager.setActiveProfile(profile)
        return state.copy(
            activeProfile = profile,
            allProfiles = profileManager.getAllProfiles()
        )
    }

    fun updateExistingProfile(state: ChatViewState, profile: UserProfile): ChatViewState {
        if (profile.name.isEmpty()) return state

        if (profileManager.updateProfile(profile)) {
            profileManager.setActiveProfile(profile)
            return state.copy(
                activeProfile = profile,
                allProfiles = profileManager.getAllProfiles()
            )
        }
        return state
    }

    fun switchToProfile(state: ChatViewState, profile: UserProfile): ChatViewState {
        profileManager.setActiveProfile(profile)
        return state.copy(
            activeProfile = profile,
            allProfiles = profileManager.getAllProfiles()
        )
    }

    fun deleteProfile(state: ChatViewState, name: String): ChatViewState {
        var newState = state

        if (name == state.activeProfile.name) {
            val emptyProfile = UserProfile()
            profileManager.setActiveProfile(emptyProfile)
            newState = newState.copy(activeProfile = emptyProfile)
        }

        profileManager.deleteProfile(name)
        return newState.copy(allProfiles = profileManager.getAllProfiles())
    }

    fun dismissWelcomeDialog(state: ChatViewState): ChatViewState {
        profileManager.setFirstLaunchCompleted()
        val defaultProfile = UserProfile(
            name = "Пользователь",
            experience = "Разработчик",
            preferredStyle = com.llmapp.memory.ResponseStyle.BALANCED
        )
        profileManager.setActiveProfile(defaultProfile)
        return state.copy(
            activeProfile = defaultProfile,
            showWelcomeDialog = false,
            allProfiles = profileManager.getAllProfiles()
        )
    }

    fun toggleProfileManager(state: ChatViewState): ChatViewState {
        val show = !state.showProfileManager
        val profiles = if (show) profileManager.getAllProfiles() else state.allProfiles
        return state.copy(
            showProfileManager = show,
            allProfiles = profiles
        )
    }

    fun dismissProfileManager(state: ChatViewState): ChatViewState {
        return state.copy(showProfileManager = false)
    }
}
