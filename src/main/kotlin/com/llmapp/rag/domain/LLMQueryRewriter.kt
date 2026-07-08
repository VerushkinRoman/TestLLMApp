package com.llmapp.rag.domain

import com.llmapp.api.ClientFactory
import com.llmapp.model.ChatMessage
import com.llmapp.model.RouterRequest

class LLMQueryRewriter(
    private val model: String = "openai/gpt-oss-20b:free",
    private val useLocal: Boolean = false,
) : QueryRewriter {

    override val description: String =
        "LLM-rewrite (${if (useLocal) "локальная" else "облачная"} модель: $model)"

    override suspend fun rewrite(query: String): String {
        val switchingClient = ClientFactory.create()
        val savedLocal = (switchingClient as? com.llmapp.api.SwitchingClient)?.useLocal ?: false
        ClientFactory.setUseLocal(useLocal)

        try {
            val client = ClientFactory.create()
            val request = RouterRequest(
                model = model,
                messages = listOf(
                    ChatMessage("system", REWRITE_SYSTEM_PROMPT),
                    ChatMessage("user", "Запрос: $query"),
                ),
                maxTokens = 256,
                temperature = 0.3,
            )
            val response = client.sendRequest(request)
            response.error?.let { throw Exception(it.message) }

            val rewritten = response.choices?.firstOrNull()?.message?.content?.trim()
                ?: return query

            val cleaned = rewritten
                .removePrefix("Переписанный запрос:")
                .removePrefix("Расширенный запрос:")
                .removePrefix("Запрос:")
                .trim()
                .removeSurrounding("\"")

            println("🔍 LLMQueryRewriter: «$query» → «$cleaned»")
            return cleaned.ifBlank { query }
        } catch (e: Exception) {
            println("⚠️ LLMQueryRewriter: ${e.message}, использую исходный запрос")
            return query
        } finally {
            ClientFactory.setUseLocal(savedLocal)
        }
    }

    companion object {
        private val REWRITE_SYSTEM_PROMPT = """
Ты — эксперт по улучшению поисковых запросов для RAG-системы (Retrieval-Augmented Generation).

Твоя задача: переписать запрос пользователя так, чтобы он лучше подходил для поиска по базе знаний о чемпионатах мира по футболу.

Правила:
1. Раскрой аббревиатуры и сокращения
2. Добавь ключевые слова, которые помогут найти релевантные документы
3. Сохрани основной смысл запроса
4. Не добавляй лишней информации, которой нет в запросе
5. Если запрос и так хорош — верни его без изменений
6. Ответь ТОЛЬКО переписанным запросом, без пояснений

Примеры:
Запрос: Кто выиграл ЧМ-2022?
Ответ: Кто выиграл чемпионат мира по футболу 2022 года в Катаре

Запрос: Гол столетия
Ответ: Диего Марадона гол столетия 1986 год чемпионат мира Аргентина Англия четвертьфинал
        """.trimIndent()
    }
}
