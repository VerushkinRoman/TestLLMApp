package com.llmapp.invariants

import kotlinx.serialization.json.Json
import java.io.File

class InvariantManager(
    private val storageDir: File = File(System.getProperty("user.home"), ".llm_invariants")
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    init {
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
    }

    fun saveInvariantSet(invariantSet: InvariantSet): Boolean {
        return try {
            val file = File(storageDir, "${sanitizeName(invariantSet.name)}.json")
            file.writeText(json.encodeToString(InvariantSet.serializer(), invariantSet))
            true
        } catch (e: Exception) {
            println("❌ Ошибка сохранения инвариантов: ${e.message}")
            false
        }
    }

    fun loadInvariantSet(name: String): InvariantSet? {
        val file = File(storageDir, "${sanitizeName(name)}.json")
        if (!file.exists()) return null

        return try {
            json.decodeFromString(InvariantSet.serializer(), file.readText())
        } catch (e: Exception) {
            println("❌ Ошибка загрузки инвариантов: ${e.message}")
            null
        }
    }

    fun getAllInvariantSets(): List<InvariantSet> {
        val files = storageDir.listFiles { file -> file.extension == "json" }
        return files?.mapNotNull { file ->
            try {
                json.decodeFromString(InvariantSet.serializer(), file.readText())
            } catch (_: Exception) {
                null
            }
        } ?: emptyList()
    }

    fun deleteInvariantSet(name: String): Boolean {
        val file = File(storageDir, "${sanitizeName(name)}.json")
        return if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }

    private fun sanitizeName(name: String): String {
        return name.replace(Regex("[<>:\"/\\\\|?*]"), "_")
    }
}
