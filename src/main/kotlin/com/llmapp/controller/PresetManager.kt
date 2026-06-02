package com.llmapp.controller

import com.llmapp.model.ResponseControl

object PresetManager {
    fun getPreset(number: Int): ResponseControl? = when (number) {
        1 -> ResponseControl(
            formatDescription = "Ответь максимально кратко. Только ключевая информация. Без примеров.",
            maxTokens = 100,
            stopSequences = listOf("Пример:", "Например:"),
            temperature = 0.1,
            enabled = true
        )

        2 -> ResponseControl(
            formatDescription = "Ответь развернуто, с примерами и аналогиями. Прояви креативность.",
            maxTokens = 500,
            temperature = 0.9,
            enabled = true
        )

        3 -> ResponseControl(
            formatDescription = "Ответь технически точно. Если нужно, включи пример кода в тройных кавычках.",
            maxTokens = 400,
            temperature = 0.3,
            enabled = true
        )

        4 -> ResponseControl(
            formatDescription = "Отвечай в разговорном стиле, как будто общаешься с другом. Используй эмодзи.",
            temperature = 0.7,
            enabled = true
        )

        else -> null
    }
}
