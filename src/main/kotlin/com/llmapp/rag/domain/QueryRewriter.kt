package com.llmapp.rag.domain

interface QueryRewriter {
    suspend fun rewrite(query: String): String
    val description: String
}

class SimpleQueryRewriter : QueryRewriter {
    override val description: String = "Расширение запроса (аббревиатуры → полные названия)"

    private val expansions = mapOf(
        "ЧМ" to "чемпионат мира по футболу",
        "ЧМ-2026" to "чемпионат мира 2026 года по футболу",
        "ЧМ-2022" to "чемпионат мира 2022 года по футболу",
        "ЧМ-2018" to "чемпионат мира 2018 года по футболу",
        "ЧМ-2014" to "чемпионат мира 2014 года по футболу",
        "ЧМ-2010" to "чемпионат мира 2010 года по футболу",
        "ЧМ-2006" to "чемпионат мира 2006 года по футболу",
        "ЧМ-1950" to "чемпионат мира 1950 года по футболу",
        "VAR" to "VAR система видеопомощи арбитрам",
        "ФИФА" to "ФИФА Международная федерация футбола",
        "фифа" to "ФИФА Международная федерация футбола",
        "Аргентина 2022" to "победа Аргентины на чемпионате мира 2022",
        "Марадона" to "Диего Марадона чемпионат мира по футболу",
        "Месси" to "Лионель Месси чемпионат мира по футболу",
        "Клозе" to "Мирослав Клозе рекорд голов на чемпионатах мира",
        "Роналдо" to "Роналдо феномен чемпионат мира",
        "Пеле" to "Пеле король футбола чемпионат мира",
        "SAOT" to "SAOT полуавтоматическая система определения офсайда",
        "Al Rihla" to "умный мяч Al Rihla с 500 датчиками",
        "ETFE" to "кровля ETFE из этилен-тетрафторэтилена",
        "GOAT" to "величайший игрок всех времён GOAT",
        "Waka Waka" to "песня Waka Waka Шакиры",
        "Поццо" to "Витторио Поццо чемпионат мира 1934 1938",
        "Михелс" to "Ринус Михелс тотальный футбол чемпионат мира",
        "Зидан" to "Зинедин Зидан чемпионат мира 1998 2006",
    )

    override suspend fun rewrite(query: String): String {
        var rewritten = query.trim()
        expansions.forEach { (abbr, full) ->
            if (rewritten.contains(abbr, ignoreCase = true)) {
                rewritten = rewritten.replace(abbr, full, ignoreCase = true)
            }
        }
        if (rewritten.length < 20 && !rewritten.contains("чемпионат", ignoreCase = true) &&
            !rewritten.contains("футбол", ignoreCase = true) &&
            (rewritten.contains("мир", ignoreCase = true) || rewritten.contains("кубок", ignoreCase = true))
        ) {
            rewritten = "$rewritten чемпионат мира по футболу"
        }
        return rewritten
    }
}
