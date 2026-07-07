package com.llmapp.demo.evaluation

import com.llmapp.api.ClientFactory
import com.llmapp.model.ChatMessage
import com.llmapp.model.RouterRequest

data class TestCase(
    val id: Int,
    val description: String,
    val expectedBehavior: String,
    val actualResponse: String,
    val metrics: Map<String, String> = emptyMap(),
)

class DemoEvaluator(
    private val model: String = "openai/gpt-oss-20b:free",
) {

    suspend fun evaluate(
        demoName: String,
        testCases: List<TestCase>,
        additionalContext: String = "",
    ): String {
        val prompt = buildPrompt(demoName, testCases, additionalContext)
        val client = ClientFactory.create()

        val request = RouterRequest(
            model = model,
            messages = listOf(
                ChatMessage("system", SYSTEM_PROMPT),
                ChatMessage("user", prompt),
            ),
            maxTokens = 4096,
            temperature = 0.3,
        )

        val response = client.sendRequest(request)
        response.error?.let { throw Exception(it.message) }

        return response.choices?.firstOrNull()?.message?.content?.trim()
            ?: throw Exception("Empty evaluation response")
    }

    private fun buildPrompt(
        demoName: String,
        testCases: List<TestCase>,
        additionalContext: String,
    ): String = buildString {
        appendLine("## Демонстрация: $demoName")
        appendLine()
        if (additionalContext.isNotBlank()) {
            appendLine(additionalContext)
            appendLine()
        }
        appendLine("Проведи оценку качества работы AI-системы на основе тестовых кейсов ниже.")
        appendLine()
        appendLine("### Твоя задача:")
        appendLine("1. Для каждого кейса поставь оценку **от 0 до 100** и вердикт ✅/⚠️/❌")
        appendLine("2. Напиши краткое обоснование оценки (1-2 предложения)")
        appendLine("3. В конце дай общую оценку, выдели сильные/слабые стороны, дай рекомендации")
        appendLine()
        appendLine("Будь строгим и объективным. Если система ответила не по ожиданиям — снижай оценку.")
        appendLine()
        appendLine("### Формат ответа:")
        appendLine("Используй Markdown.")
        appendLine()
        appendLine("### Тестовые кейсы:")
        appendLine()

        testCases.forEach { tc ->
            appendLine("---")
            appendLine("### Кейс ${tc.id}: ${tc.description}")
            appendLine("- **Ожидалось:** ${tc.expectedBehavior}")
            appendLine("- **Фактически:** ${tc.actualResponse.take(500)}")
            if (tc.metrics.isNotEmpty()) {
                appendLine("- **Метрики:**")
                tc.metrics.forEach { (key, value) ->
                    appendLine("  - $key: $value")
                }
            }
            appendLine()
        }
    }

    companion object {
        private val SYSTEM_PROMPT = """
Ты — строгий эксперт по оценке качества AI-систем (QA Engineer для LLM).
Твоя специализация — сравнивать ожидаемое и фактическое поведение языковых моделей.

Правила оценки:
- 90-100: идеально, полностью соответствует ожиданиям
- 70-89: хорошо, есть мелкие недочеты
- 50-69: удовлетворительно, есть существенные отклонения
- 30-49: плохо, система не справляется с задачей
- 0-29: критично, полное несоответствие

Будь объективен, аргументируй каждую оценку.
Используй русский язык для ответа.
""".trimIndent()
    }
}
