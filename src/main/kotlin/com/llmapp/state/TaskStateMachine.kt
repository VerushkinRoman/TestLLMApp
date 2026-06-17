package com.llmapp.state

import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class TaskStateMachine(
    private val storageDir: File = File(System.getProperty("user.home"), ".llm_task_states")
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private var currentState: TaskState = TaskState()
    private var isPaused: Boolean = false
    private var pauseReason: String = ""

    // Хранилище снимков для возобновления
    private val snapshots = mutableMapOf<String, TaskSnapshot>()

    // Разрешенные переходы
    private val transitions = setOf(
        AllowedTransition(
            TaskPhase.INIT,
            TaskPhase.PLANNING,
            requiresUserInput = true,
            description = "Требования собраны → планирование"
        ),
        AllowedTransition(TaskPhase.INIT, TaskPhase.PAUSED, description = "Пауза на этапе сбора"),
        AllowedTransition(
            TaskPhase.PLANNING,
            TaskPhase.EXECUTION,
            requiresUserInput = true,
            description = "План утвержден → выполнение"
        ),
        AllowedTransition(
            TaskPhase.PLANNING,
            TaskPhase.INIT,
            requiresUserInput = false,
            description = "План требует уточнения"
        ),
        AllowedTransition(
            TaskPhase.PLANNING,
            TaskPhase.PAUSED,
            description = "Пауза на этапе планирования"
        ),
        AllowedTransition(
            TaskPhase.EXECUTION,
            TaskPhase.VALIDATION,
            requiresUserInput = true,
            description = "Код написан → проверка"
        ),
        AllowedTransition(
            TaskPhase.EXECUTION,
            TaskPhase.PLANNING,
            requiresUserInput = false,
            description = "Выполнение требует перепланирования"
        ),
        AllowedTransition(
            TaskPhase.EXECUTION,
            TaskPhase.PAUSED,
            description = "Пауза на этапе выполнения"
        ),
        AllowedTransition(
            TaskPhase.VALIDATION,
            TaskPhase.DONE,
            requiresUserInput = true,
            description = "Проверка пройдена → завершение"
        ),
        AllowedTransition(
            TaskPhase.VALIDATION,
            TaskPhase.EXECUTION,
            requiresUserInput = false,
            description = "Проверка не пройдена → доработка"
        ),
        AllowedTransition(
            TaskPhase.VALIDATION,
            TaskPhase.PAUSED,
            description = "Пауза на этапе проверки"
        ),
        AllowedTransition(
            TaskPhase.PAUSED,
            TaskPhase.INIT,
            requiresUserInput = true,
            description = "Возобновление → сбор"
        ),
        AllowedTransition(
            TaskPhase.PAUSED,
            TaskPhase.PLANNING,
            requiresUserInput = true,
            description = "Возобновление → планирование"
        ),
        AllowedTransition(
            TaskPhase.PAUSED,
            TaskPhase.EXECUTION,
            requiresUserInput = true,
            description = "Возобновление → выполнение"
        ),
        AllowedTransition(
            TaskPhase.PAUSED,
            TaskPhase.VALIDATION,
            requiresUserInput = true,
            description = "Возобновление → проверка"
        ),
        AllowedTransition(
            TaskPhase.BLOCKED,
            TaskPhase.INIT,
            requiresUserInput = true,
            description = "Разблокировка → сбор"
        ),
        AllowedTransition(
            TaskPhase.BLOCKED,
            TaskPhase.PLANNING,
            requiresUserInput = true,
            description = "Разблокировка → планирование"
        ),
        AllowedTransition(
            TaskPhase.BLOCKED,
            TaskPhase.EXECUTION,
            requiresUserInput = true,
            description = "Разблокировка → выполнение"
        ),
        AllowedTransition(
            TaskPhase.BLOCKED,
            TaskPhase.VALIDATION,
            requiresUserInput = true,
            description = "Разблокировка → проверка"
        ),
        AllowedTransition(
            TaskPhase.DONE,
            TaskPhase.PAUSED,
            requiresUserInput = false,
            description = "Завершена → пауза (архивация)"
        )
    )

    init {
        if (!storageDir.exists()) storageDir.mkdirs()
        loadCurrentState()
    }

    // ============================================================
    // УПРАВЛЕНИЕ СОСТОЯНИЕМ
    // ============================================================

    fun getCurrentState(): TaskState = currentState

    fun getPhase(): TaskPhase = currentState.phase
    fun canTransitionTo(targetPhase: TaskPhase): Boolean {
        if (isPaused && targetPhase != TaskPhase.PAUSED) {
            return false
        }
        return transitions.any { it.from == currentState.phase && it.to == targetPhase }
    }
    fun getStep(): String = currentState.step
    fun getExpectedAction(): String = currentState.expectedAction
    fun isPaused(): Boolean = isPaused
    fun getPauseReason(): String = pauseReason

    fun hasSnapshot(snapshotId: String): Boolean {
        return snapshots.containsKey(snapshotId) || loadSnapshotFromFile(snapshotId) != null
    }

    fun createTask(
        taskName: String,
        description: String = "",
        initialContext: Map<String, String> = emptyMap(),
        parentTaskId: String? = null
    ): TaskState {
        val newState = TaskState(
            taskId = UUID.randomUUID().toString(),
            taskName = taskName,
            phase = TaskPhase.INIT,
            step = "Сбор требований",
            expectedAction = "Опишите, что нужно сделать. Уточните требования.",
            description = description,
            context = initialContext,
            parentTaskId = parentTaskId
        )
        currentState = newState
        isPaused = false
        pauseReason = ""
        saveCurrentState()
        createSnapshot("initial_${newState.taskId}")
        return newState
    }

    fun getAvailableTransitions(): List<TaskPhase> {
        return transitions
            .filter { it.from == currentState.phase }
            .map { it.to }
            .distinct()
    }

    // ============================================================
    // ПЕРЕХОДЫ С ВАЛИДАЦИЕЙ
    // ============================================================

    fun transitionTo(
        targetPhase: TaskPhase,
        reason: String = "",
        triggeredBy: String = "system"
    ): TransitionResult {
        // Если на паузе, разрешаем только выход из паузы
        if (isPaused && targetPhase != TaskPhase.PAUSED) {
            return TransitionResult(
                success = false,
                message = "⚠️ Задача на паузе. Сначала возобновите выполнение.",
                state = currentState
            )
        }

        // Проверяем разрешен ли переход
        val allowed = transitions.find { it.from == currentState.phase && it.to == targetPhase }
        if (allowed == null) {
            return TransitionResult(
                success = false,
                message = "🚫 Переход из ${currentState.phase.displayName} в ${targetPhase.displayName} не разрешен",
                state = currentState
            )
        }

        // Запоминаем переход
        val transition = StateTransition(
            fromPhase = currentState.phase,
            toPhase = targetPhase,
            reason = reason,
            triggeredBy = triggeredBy
        )

        // Обновляем состояние
        val updatedState = currentState.copy(
            phase = targetPhase,
            lastUpdated = System.currentTimeMillis(),
            history = currentState.history + transition,
            step = getDefaultStepForPhase(targetPhase),
            expectedAction = getDefaultActionForPhase(targetPhase)
        )

        currentState = updatedState

        // Если это пауза, запоминаем причину
        if (targetPhase == TaskPhase.PAUSED) {
            isPaused = true
            pauseReason = reason
            currentState = currentState.copy(
                context = currentState.context + ("pause_reason" to reason)
            )
            createSnapshot("pause_${currentState.taskId}")
        } else if (isPaused) {
            isPaused = false
            pauseReason = ""
        }

        saveCurrentState()
        return TransitionResult(
            success = true,
            message = "✅ Переход: ${allowed.description}",
            state = currentState,
            transition = transition
        )
    }

    fun pause(reason: String = "Пользователь приостановил выполнение"): TransitionResult {
        if (isPaused) {
            return TransitionResult(
                success = false,
                message = "⚠️ Задача уже на паузе",
                state = currentState
            )
        }
        return transitionTo(TaskPhase.PAUSED, reason, "user")
    }

    fun resume(): TransitionResult {
        if (!isPaused) {
            return TransitionResult(
                success = false,
                message = "⚠️ Задача не на паузе",
                state = currentState
            )
        }

        // Возвращаемся к предыдущей фазе
        val lastHistory = currentState.history.lastOrNull()
        val previousPhase = lastHistory?.fromPhase ?: TaskPhase.INIT

        val result = transitionTo(previousPhase, "Возобновление после паузы", "user")
        if (result.success) {
            // Восстанавливаем шаг, который был до паузы
            val restoredState = currentState.copy(
                step = lastHistory?.let {
                    getDefaultStepForPhase(previousPhase)
                } ?: "Продолжение работы"
            )
            currentState = restoredState
            saveCurrentState()
            return result.copy(
                message = "▶️ Задача возобновлена на фазе ${previousPhase.displayName}",
                state = currentState
            )
        }
        return result
    }

    fun block(reason: String): TransitionResult {
        return transitionTo(TaskPhase.BLOCKED, reason, "system")
    }

    fun unblock(): TransitionResult {
        if (currentState.phase != TaskPhase.BLOCKED) {
            return TransitionResult(
                success = false,
                message = "⚠️ Задача не заблокирована",
                state = currentState
            )
        }

        val lastHistory = currentState.history.lastOrNull()
        val previousPhase = lastHistory?.fromPhase ?: TaskPhase.INIT

        return transitionTo(previousPhase, "Разблокировка", "user")
    }

    // ============================================================
    // ОБНОВЛЕНИЕ ШАГА И ОЖИДАНИЙ
    // ============================================================

    fun updateStep(step: String): TaskState {
        currentState = currentState.copy(step = step, lastUpdated = System.currentTimeMillis())
        saveCurrentState()
        return currentState
    }

    fun updateExpectedAction(action: String): TaskState {
        currentState =
            currentState.copy(expectedAction = action, lastUpdated = System.currentTimeMillis())
        saveCurrentState()
        return currentState
    }

    fun updateContext(key: String, value: String): TaskState {
        currentState = currentState.copy(
            context = currentState.context + (key to value),
            lastUpdated = System.currentTimeMillis()
        )
        saveCurrentState()
        return currentState
    }

    fun getContext(key: String): String? = currentState.context[key]

    // ============================================================
    // СНИМКИ ДЛЯ ВОЗОБНОВЛЕНИЯ
    // ============================================================

    fun createSnapshot(name: String, messages: List<Pair<String, String>> = emptyList()): String {
        val id = UUID.randomUUID().toString()
        val snapshot = TaskSnapshot(
            state = currentState,
            messages = messages,
            timestamp = System.currentTimeMillis()
        )
        snapshots[name] = snapshot
        snapshots[id] = snapshot
        saveSnapshot(name, snapshot)
        saveSnapshot(id, snapshot)
        return id
    }

    fun loadSnapshot(nameOrId: String): TaskSnapshot? {
        return snapshots[nameOrId] ?: loadSnapshotFromFile(nameOrId)
    }

    fun restoreFromSnapshot(snapshotId: String): Boolean {
        val snapshot = loadSnapshot(snapshotId) ?: return false
        currentState = snapshot.state
        isPaused = currentState.phase == TaskPhase.PAUSED
        pauseReason = if (isPaused) "Восстановлено из снимка" else ""
        saveCurrentState()
        return true
    }

    fun getAllSnapshots(): List<Pair<String, TaskSnapshot>> {
        val all = mutableListOf<Pair<String, TaskSnapshot>>()
        snapshots.forEach { (key, value) ->
            all.add(key to value)
        }
        // Также загружаем из файлов
        val files = storageDir.listFiles { file -> file.name.endsWith(".snapshot.json") }
        files?.forEach { file ->
            try {
                val content = file.readText()
                val snapshot = json.decodeFromString<TaskSnapshot>(content)
                val name = file.name.removeSuffix(".snapshot.json")
                if (!snapshots.containsKey(name)) {
                    all.add(name to snapshot)
                }
            } catch (_: Exception) {
                // игнорируем
            }
        }
        return all
    }

    // ============================================================
    // ПРОВЕРКИ
    // ============================================================

    fun isTaskComplete(): Boolean = currentState.phase == TaskPhase.DONE

    fun getProgress(): Float = currentState.getPhaseProgress()

    // ============================================================
    // ХРАНЕНИЕ
    // ============================================================

    private fun saveCurrentState() {
        try {
            val file = File(storageDir, "current_state.json")
            val jsonString = json.encodeToString(TaskState.serializer(), currentState)
            file.writeText(jsonString)
        } catch (e: Exception) {
            println("⚠️ Ошибка сохранения состояния: ${e.message}")
        }
    }

    private fun loadCurrentState() {
        val file = File(storageDir, "current_state.json")
        if (file.exists()) {
            try {
                val content = file.readText()
                currentState = json.decodeFromString(TaskState.serializer(), content)
                isPaused = currentState.phase == TaskPhase.PAUSED
                pauseReason = if (isPaused) {
                    currentState.context["pause_reason"] ?: "Восстановлено"
                } else ""
            } catch (e: Exception) {
                println("⚠️ Ошибка загрузки состояния: ${e.message}")
                currentState = TaskState()
                isPaused = false
                pauseReason = ""
            }
        }
    }

    private fun saveSnapshot(name: String, snapshot: TaskSnapshot) {
        try {
            val file = File(storageDir, "$name.snapshot.json")
            val jsonString = json.encodeToString(TaskSnapshot.serializer(), snapshot)
            file.writeText(jsonString)
        } catch (e: Exception) {
            println("⚠️ Ошибка сохранения снимка: ${e.message}")
        }
    }

    private fun loadSnapshotFromFile(nameOrId: String): TaskSnapshot? {
        val file = File(storageDir, "$nameOrId.snapshot.json")
        if (file.exists()) {
            try {
                val content = file.readText()
                return json.decodeFromString(TaskSnapshot.serializer(), content)
            } catch (e: Exception) {
                println("⚠️ Ошибка загрузки снимка: ${e.message}")
                return null
            }
        }
        return null
    }

    // ============================================================
    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ
    // ============================================================

    private fun getDefaultStepForPhase(phase: TaskPhase): String {
        return when (phase) {
            TaskPhase.INIT -> "Сбор и уточнение требований"
            TaskPhase.PLANNING -> "Разработка плана"
            TaskPhase.EXECUTION -> "Выполнение задач"
            TaskPhase.VALIDATION -> "Проверка результата"
            TaskPhase.DONE -> "Завершено"
            TaskPhase.PAUSED -> "Приостановлено"
            TaskPhase.BLOCKED -> "Заблокировано"
        }
    }

    private fun getDefaultActionForPhase(phase: TaskPhase): String {
        return when (phase) {
            TaskPhase.INIT -> "Опишите задачу. Что нужно сделать?"
            TaskPhase.PLANNING -> "Утвердите план или уточните детали"
            TaskPhase.EXECUTION -> "Ожидайте выполнение задачи"
            TaskPhase.VALIDATION -> "Проверьте результат и подтвердите"
            TaskPhase.DONE -> "Задача завершена ✅"
            TaskPhase.PAUSED -> "Задача приостановлена"
            TaskPhase.BLOCKED -> "Требуется вмешательство для разблокировки"
        }
    }
}

// ============================================================
// DATA CLASSES
// ============================================================

data class TransitionResult(
    val success: Boolean,
    val message: String,
    val state: TaskState,
    val transition: StateTransition? = null
)
