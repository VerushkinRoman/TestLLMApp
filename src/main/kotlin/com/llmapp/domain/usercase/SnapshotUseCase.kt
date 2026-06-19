package com.llmapp.domain.usercase

import com.llmapp.agent.StatefulMemoryAgent
import com.llmapp.ui.viewmodel.ChatViewState

class SnapshotUseCase(
    private val statefulAgent: StatefulMemoryAgent
) {

    fun toggleSnapshotDialog(state: ChatViewState): ChatViewState {
        val show = !state.showSnapshotDialog
        val snapshots = if (show) statefulAgent.getSnapshots() else emptyList()
        return state.copy(
            showSnapshotDialog = show,
            snapshots = snapshots
        )
    }

    fun dismissSnapshotDialog(state: ChatViewState): ChatViewState {
        return state.copy(showSnapshotDialog = false)
    }

    fun createSnapshot(state: ChatViewState, name: String): ChatViewState {
        if (name.isNotBlank()) {
            statefulAgent.createSnapshot(name)
            val snapshots = statefulAgent.getSnapshots()
            return state.copy(snapshots = snapshots)
        }
        return state
    }

    fun restoreSnapshot(state: ChatViewState, id: String): ChatViewState {
        if (statefulAgent.restoreFromSnapshot(id)) {
            val snapshots = statefulAgent.getSnapshots()
            return state.copy(
                snapshots = snapshots,
                showSnapshotDialog = false
            )
        }
        return state.copy(showSnapshotDialog = false)
    }
}
