package com.llmapp.api

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object KeyUsageMonitor {
    data class KeyStats(
        val keyIndex: Int,
        val requestsCount: Int = 0,
        val errorsCount: Int = 0,
        val lastUsed: Long = 0,
        val isRateLimited: Boolean = false
    )

    private val _keyStats = MutableStateFlow<List<KeyStats>>(emptyList())
    val keyStats: StateFlow<List<KeyStats>> = _keyStats.asStateFlow()

    fun recordRequest(keyIndex: Int) {
        val current = _keyStats.value.toMutableList()
        val index = current.indexOfFirst { it.keyIndex == keyIndex }
        if (index >= 0) {
            current[index] = current[index].copy(
                requestsCount = current[index].requestsCount + 1,
                lastUsed = System.currentTimeMillis()
            )
        } else {
            current.add(KeyStats(keyIndex, 1, 0, System.currentTimeMillis()))
        }
        _keyStats.value = current
    }

    fun recordError(keyIndex: Int, isRateLimit: Boolean = false) {
        val current = _keyStats.value.toMutableList()
        val index = current.indexOfFirst { it.keyIndex == keyIndex }
        if (index >= 0) {
            current[index] = current[index].copy(
                errorsCount = current[index].errorsCount + 1,
                isRateLimited = isRateLimit
            )
        }
        _keyStats.value = current
    }

    fun getStatsReport(): String {
        return buildString {
            appendLine("📊 Статистика использования API ключей:")
            appendLine("=".repeat(50))
            _keyStats.value.forEach { stats ->
                appendLine("Ключ #${stats.keyIndex}:")
                appendLine("  • Запросов: ${stats.requestsCount}")
                appendLine("  • Ошибок: ${stats.errorsCount}")
                appendLine("  • Лимит: ${if (stats.isRateLimited) "⚠️ Достигнут" else "✅ ОК"}")
                appendLine("  • Последнее использование: ${formatTime(stats.lastUsed)}")
            }
        }
    }

    private fun formatTime(timestamp: Long): String {
        if (timestamp == 0L) return "никогда"
        val diff = System.currentTimeMillis() - timestamp
        return when {
            diff < 60_000 -> "${diff / 1000} сек назад"
            diff < 3600_000 -> "${diff / 60_000} мин назад"
            else -> "${diff / 3600_000} ч назад"
        }
    }
}
