package com.llmapp.collector

import kotlinx.serialization.json.Json
import java.io.File

class MatchStore(storageDir: File) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val snapshotsDir = File(storageDir, "snapshots")

    init {
        storageDir.mkdirs()
        snapshotsDir.mkdirs()
    }

    fun saveSnapshot(snapshot: CollectorSnapshot) {
        val file = File(snapshotsDir, "snapshot_${snapshot.timestamp}.json")
        file.writeText(json.encodeToString(CollectorSnapshot.serializer(), snapshot))
        cleanOldSnapshots()
    }

    fun loadLatestSnapshot(): CollectorSnapshot? {
        val files = snapshotsDir.listFiles()?.sortedByDescending { it.name } ?: return null
        if (files.isEmpty()) return null
        return try {
            json.decodeFromString(CollectorSnapshot.serializer(), files.first().readText())
        } catch (_: Exception) {
            null
        }
    }

    private fun cleanOldSnapshots(maxCount: Int = 50) {
        val files = snapshotsDir.listFiles()?.sortedByDescending { it.name } ?: return
        if (files.size <= maxCount) return
        files.drop(maxCount).forEach { it.delete() }
    }
}
