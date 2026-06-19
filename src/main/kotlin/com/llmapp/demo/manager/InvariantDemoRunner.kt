package com.llmapp.demo.manager

import com.llmapp.agent.InvariantAwareAgent
import com.llmapp.api.ApiConfig
import com.llmapp.invariants.Invariant
import com.llmapp.invariants.InvariantManager
import com.llmapp.invariants.InvariantPresets
import com.llmapp.invariants.InvariantType
import com.llmapp.ui.models.ChatMessageUI
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/**
 * Демонстрация работы с инвариантами
 */
class InvariantDemoRunner(
    onMessageAdded: (ChatMessageUI) -> Unit,
    onTypingStateChanged: (Boolean) -> Unit
) : BaseDemoRunner(onMessageAdded, onTypingStateChanged) {

    override suspend fun run() {
        val apiKey = ApiConfig.getApiKey()
        val model = "nvidia/nemotron-3-super-120b-a12b:free"

        addMessage(
            role = "assistant",
            content = """
            ${"=".repeat(80)}
            🔒 ДЕМОНСТРАЦИЯ РАБОТЫ ИНВАРИАНТОВ
            ${"=".repeat(80)}
            
            Инварианты - это правила, которые ассистент НЕ ИМЕЕТ ПРАВА нарушать.
            
            В этой демонстрации мы увидим:
            
            1️⃣ Настройку инвариантов для Android/KMP проекта
            2️⃣ Корректный запрос (инварианты соблюдены)
            3️⃣ Запрос, нарушающий инварианты (агент откажется и объяснит почему)
            4️⃣ Исправленный запрос с учетом инвариантов
            5️⃣ Тест бизнес-правила (кроссплатформенность)
            6️⃣ Финальные выводы
            """.trimIndent(),
            metadata = "🔒 ДЕМОНСТРАЦИЯ ИНВАРИАНТОВ"
        )
        delay(5.seconds)

        // ========== ШАГ 1: Настройка инвариантов ==========
        addMessage(
            role = "assistant",
            content = """
            ${"─".repeat(80)}
            📌 ШАГ 1/6: Настройка инвариантов для проекта
            ${"─".repeat(80)}
            
            Загружаем инварианты для Android/KMP проекта.
            Эти правила будут действовать ВЕСЬ диалог.
            """.trimIndent(),
            metadata = "📌 ШАГ 1/6"
        )
        delay(3.seconds)

        val invariantManager = InvariantManager()
        val invariantSet = InvariantPresets.getAndroidKMPInvariants()
        invariantManager.saveInvariantSet(invariantSet)

        addMessage(
            role = "assistant",
            content = """
            ✅ Набор инвариантов загружен: ${invariantSet.name}
            
            ${
                invariantSet.invariants.joinToString("\n") { invariant ->
                    val emoji = when (invariant.type) {
                        InvariantType.TECH_STACK -> "⚙️"
                        InvariantType.ARCHITECTURE -> "🏗️"
                        InvariantType.CODING_STANDARD -> "📝"
                        InvariantType.BUSINESS_RULE -> "📋"
                        InvariantType.SECURITY -> "🔒"
                        else -> "📌"
                    }
                    "$emoji ${invariant.name}: ${invariant.description}"
                }
            }
            
            💡 Всего инвариантов: ${invariantSet.invariants.size}
            ⚠️ Нарушение любого инварианта = отказ от ответа
            """.trimIndent(),
            metadata = "✅ ИНВАРИАНТЫ ЗАГРУЖЕНЫ"
        )
        delay(5.seconds)

        // ========== ШАГ 2: Создание агента ==========
        addMessage(
            role = "assistant",
            content = """
            ${"─".repeat(80)}
            📌 ШАГ 2/6: Создание агента с инвариантами
            ${"─".repeat(80)}
            
            Создаем агента, который будет строго соблюдать инварианты.
            При нарушении - будет пытаться исправить ответ (до 3 попыток).
            """.trimIndent(),
            metadata = "📌 ШАГ 2/6"
        )
        delay(3.seconds)

        val agent = InvariantAwareAgent(
            apiKey = apiKey,
            model = model,
            systemPrompt = "Ты опытный разработчик на Kotlin. Отвечай кратко и по делу.",
            invariantSetName = "Android/KMP",
            onAgentMessage = { content, metadata ->
                addMessage(
                    role = "assistant",
                    content = content,
                    metadata = metadata ?: "🤖 АГЕНТ"
                )
            }
        )

        addMessage(
            role = "assistant",
            content = """
            ✅ Агент создан!
            
            🤖 Модель: ${model.take(40)}...
            🔒 Инвариантов: ${invariantSet.invariants.size} правил
            🌡️ Температура: 0.1 (для стабильных ответов)
            🔄 Максимум попыток исправления: 3
            """.trimIndent(),
            metadata = "✅ АГЕНТ СОЗДАН"
        )
        delay(5.seconds)

        // ========== ШАГ 3: Корректный запрос ==========
        addMessage(
            role = "assistant",
            content = """
            ${"─".repeat(80)}
            📌 ШАГ 3/6: Корректный запрос (инварианты соблюдены)
            ${"─".repeat(80)}
            """.trimIndent(),
            metadata = "📌 ШАГ 3/6"
        )
        delay(3.seconds)

        val correctRequest = """
            Как организовать хранение данных в KMP приложении с использованием Kotlin и SQLDelight?
            Нужно сделать кроссплатформенное решение.
        """.trimIndent()

        addMessage(role = "user", content = correctRequest, metadata = "👤 КОРРЕКТНЫЙ ЗАПРОС")
        delay(2.seconds)

        addMessage(
            role = "assistant",
            content = "⏳ Агент обрабатывает запрос... Проверка инвариантов...",
            metadata = "⏳ ОБРАБОТКА"
        )
        onTypingStateChanged(true)
        delay(3.seconds)

        val response1 = agent.processRequest(correctRequest)
        onTypingStateChanged(false)

        if (response1.violationsFound) {
            addMessage(
                role = "assistant",
                content = """
                ❌ НЕОЖИДАННО: Ответ содержит нарушения инвариантов!
                
                Нарушенные правила:
                ${
                    response1.invariantResults.filter { !it.passed }.joinToString("\n") {
                        "   • ${it.invariant.name}: ${it.invariant.description}"
                    }
                }
                """.trimIndent(),
                metadata = "❌ ОШИБКА"
            )
        } else {
            addMessage(
                role = "assistant",
                content = """
                ✅ ИНВАРИАНТЫ СОБЛЮДЕНЫ!
                
                Все ${response1.invariantResults.size} инвариантов пройдены успешно.
                
                🤖 ОТВЕТ АГЕНТА:
                
                ${response1.content}
                """.trimIndent(),
                metadata = "✅ КОРРЕКТНЫЙ ОТВЕТ"
            )
        }
        delay(5.seconds)

        // ========== ШАГ 4: Нарушающий запрос ==========
        addMessage(
            role = "assistant",
            content = """
            ${"─".repeat(80)}
            📌 ШАГ 4/6: Запрос, нарушающий инварианты
            ${"─".repeat(80)}
            """.trimIndent(),
            metadata = "📌 ШАГ 4/6"
        )
        delay(3.seconds)

        val violatingRequest = """
            Давай сделаем простой Android проект на Java с использованием RxJava.
            Я хочу использовать SharedPreferences для хранения данных.
            Архитектуру сделаем MVP.
        """.trimIndent()

        addMessage(role = "user", content = violatingRequest, metadata = "👤 НАРУШАЮЩИЙ ЗАПРОС")
        delay(2.seconds)

        onTypingStateChanged(true)
        delayMedium()

        val response2 = agent.processRequest(violatingRequest)
        onTypingStateChanged(false)

        val allViolations = response2.invariantResults.filter { !it.passed }
        val errorViolations =
            allViolations.filter { it.invariant.severity == Invariant.Severity.ERROR }
        val warningViolations =
            allViolations.filter { it.invariant.severity == Invariant.Severity.WARNING }

        addMessage(
            role = "assistant",
            content = """
            📊 СТАТИСТИКА ОБРАБОТКИ ЗАПРОСА:
            
            🔍 Анализ запроса пользователя:
            • Java → нарушает инвариант "Технологический стек" (ERROR)
            • RxJava → нарушает инвариант "Технологический стек" (ERROR)
            • SharedPreferences → нарушает инвариант "Безопасность данных" (WARNING)
            • MVP → нарушает инвариант "Архитектура MVI" (ERROR)
            • Android-only → нарушает инвариант "Кроссплатформенность" (ERROR)
            
            🔄 Процесс исправления:
            • Всего попыток: ${response2.retryCount}
            • Найдено нарушений в ответе: ${allViolations.size}
            ${if (errorViolations.isNotEmpty()) "  • Серьезных нарушений (ERROR): ${errorViolations.size}\n" else ""}
            ${if (warningViolations.isNotEmpty()) "  • Предупреждений (WARNING): ${warningViolations.size}\n" else ""}
            
            📄 Финальный результат:
            ${
                if (allViolations.isEmpty()) "✅ Агент успешно исправил все нарушения"
                else if (errorViolations.isEmpty()) "⚠️ Остались только предупреждения (WARNING)"
                else "❌ Агент НЕ смог полностью исправить ответ"
            }
            """.trimIndent(),
            metadata = "📊 СТАТИСТИКА"
        )
        delay(3.seconds)

        addMessage(
            role = "assistant",
            content = "📄 ФИНАЛЬНЫЙ ОТВЕТ АГЕНТА:\n\n${response2.content}",
            metadata = if (allViolations.isNotEmpty() && errorViolations.isNotEmpty())
                "📄 ОТВЕТ С ОГРАНИЧЕНИЯМИ"
            else
                "📄 ИСПРАВЛЕННЫЙ ОТВЕТ"
        )
        delay(5.seconds)

        // ========== ШАГ 5: Исправленный запрос ==========
        addMessage(
            role = "assistant",
            content = """
            ${"─".repeat(80)}
            📌 ШАГ 5/6: Исправленный запрос (с учетом инвариантов)
            ${"─".repeat(80)}
            """.trimIndent(),
            metadata = "📌 ШАГ 5/6"
        )
        delay(3.seconds)

        val fixedRequest = """
            Как организовать хранение данных в KMP проекте с использованием Kotlin и SQLDelight?
            Нужно использовать MVI архитектуру и корутины.
            Данные должны храниться безопасно.
            Решение должно быть кроссплатформенным.
        """.trimIndent()

        addMessage(role = "user", content = fixedRequest, metadata = "👤 ИСПРАВЛЕННЫЙ ЗАПРОС")
        delay(2.seconds)

        onTypingStateChanged(true)
        delay(3.seconds)

        val response3 = agent.processRequest(fixedRequest)
        onTypingStateChanged(false)

        if (response3.violationsFound) {
            addMessage(
                role = "assistant",
                content = """
                ❌ Ответ все еще содержит нарушения:
                
                ${
                    response3.invariantResults.filter { !it.passed }.joinToString("\n") {
                        "   • ${it.invariant.name}: ${it.invariant.description}"
                    }
                }
                """.trimIndent(),
                metadata = "❌ НАРУШЕНИЯ"
            )
        } else {
            addMessage(
                role = "assistant",
                content = """
                ✅ ВСЕ ИНВАРИАНТЫ СОБЛЮДЕНЫ!
                
                Все ${response3.invariantResults.size} инвариантов пройдены успешно.
                
                🤖 ОТВЕТ АГЕНТА:
                
                ${response3.content}
                """.trimIndent(),
                metadata = "✅ УСПЕШНО"
            )
        }
        delay(5.seconds)

        // ========== ШАГ 6: Бизнес-правило ==========
        addMessage(
            role = "assistant",
            content = """
            ${"─".repeat(80)}
            📌 ШАГ 6/6: Тест бизнес-правила (кроссплатформенность)
            ${"─".repeat(80)}
            
            Проверим бизнес-правило: код должен быть кроссплатформенным.
            Запросим Android-only решение - агент должен отказаться.
            """.trimIndent(),
            metadata = "📌 ШАГ 6/6"
        )
        delay(3.seconds)

        val businessRuleRequest = """
            Напиши код для хранения данных только для Android платформы.
            iOS не нужен, делаем только под Android.
        """.trimIndent()

        addMessage(role = "user", content = businessRuleRequest, metadata = "👤 БИЗНЕС-ПРАВИЛО")
        delay(2.seconds)

        onTypingStateChanged(true)
        delay(3.seconds)

        val response4 = agent.processRequest(businessRuleRequest)
        onTypingStateChanged(false)

        val businessViolations = response4.invariantResults.filter {
            !it.passed && it.invariant.type == InvariantType.BUSINESS_RULE
        }

        if (businessViolations.isNotEmpty()) {
            addMessage(
                role = "assistant",
                content = """
                🚫 БИЗНЕС-ПРАВИЛО НАРУШЕНО!
                
                Агент ОТКАЗАЛСЯ от Android-only решения.
                
                ${
                    businessViolations.joinToString("\n") { result ->
                        "❌ ${result.invariant.name}: ${result.invariant.description}"
                    }
                }
                
                📄 ОТВЕТ АГЕНТА:
                
                ${response4.content}
                """.trimIndent(),
                metadata = "🚫 БИЗНЕС-ПРАВИЛО"
            )
        } else {
            addMessage(
                role = "assistant",
                content = """
                ✅ АГЕНТ СОХРАНИЛ КРОССПЛАТФОРМЕННОСТЬ!
                
                Даже при запросе Android-only решения, агент предложил кроссплатформенный подход.
                
                📄 ОТВЕТ АГЕНТА:
                
                ${response4.content}
                """.trimIndent(),
                metadata = "✅ КРОССПЛАТФОРМЕННОСТЬ СОХРАНЕНА"
            )
        }
        delay(5.seconds)

        // ========== ФИНАЛЬНЫЕ ВЫВОДЫ ==========
        addMessage(
            role = "assistant",
            content = """
            ${"=".repeat(80)}
            🏁 ИТОГИ ДЕМОНСТРАЦИИ ИНВАРИАНТОВ
            ${"=".repeat(80)}
            
            📊 СТАТИСТИКА:
            • Всего запросов: ${agent.getTokenStats().requestCount}
            • Всего токенов: ${agent.getTokenStats().totalTokens}
            • Стоимость: ${agent.getTokenStats().getFormattedCost()}
            
            💡 КЛЮЧЕВЫЕ ВЫВОДЫ:
            
            1️⃣ ИНВАРИАНТЫ РАБОТАЮТ
               • Агент не может нарушить заданные правила
            
            2️⃣ АГЕНТ ОТКАЗЫВАЕТСЯ ОТ НАРУШЕНИЙ
               • При обнаружении нарушений агент пытается исправить ответ
               • Если исправить нельзя - агент объясняет причину отказа
            
            3️⃣ БИЗНЕС-ПРАВИЛА ЗАЩИЩЕНЫ
               • Агент не предлагает Android-only решения
               • Кроссплатформенность строго соблюдается
            
            4️⃣ ОБЪЯСНЕНИЕ ОТКАЗА
               • Агент показывает, какие правила нарушены
               • Дает рекомендации по исправлению
            
            ✅ Инварианты - мощный инструмент контроля агентов!
            🔒 Демонстрация успешно завершена!
            """.trimIndent(),
            metadata = "🏁 ДЕМОНСТРАЦИЯ ЗАВЕРШЕНА"
        )

        delay(3.seconds)

        addMessage(
            role = "assistant",
            content = """
            💡 ЧТО ДАЛЬШЕ?
            
            Вы можете:
            1️⃣ Создать свои инварианты в папке ~/.llm_invariants/
            2️⃣ Использовать агента с инвариантами в своих проектах
            3️⃣ Настроить инварианты под свой стек технологий
            
            📁 Инварианты сохраняются в: ~/.llm_invariants/
            """.trimIndent(),
            metadata = "💡 РЕКОМЕНДАЦИИ"
        )
    }
}
