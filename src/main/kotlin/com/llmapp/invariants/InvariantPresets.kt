package com.llmapp.invariants

object InvariantPresets {
    fun getAndroidKMPInvariants(): InvariantSet = InvariantSet(
        name = "Android/KMP",
        description = "Инварианты для Android/KMP разработки",
        version = "1.0",
        invariants = listOf(
            Invariant(
                id = "tech_001",
                name = "Технологический стек",
                description = "Использовать Kotlin, Compose, Ktor, Coroutines, Flow",
                type = InvariantType.TECH_STACK,
                severity = Invariant.Severity.ERROR,
                allowedValues = listOf("kotlin", "compose", "ktor", "coroutines")
            ),
            Invariant(
                id = "arch_001",
                name = "Архитектура MVI",
                description = "Использовать MVI с Clean Architecture",
                type = InvariantType.ARCHITECTURE,
                severity = Invariant.Severity.ERROR,
                allowedValues = listOf("mvi", "clean architecture", "mvvm")
            ),
            Invariant(
                id = "code_001",
                name = "Кодинг стандарты",
                description = "Не использовать устаревшие подходы",
                type = InvariantType.CODING_STANDARD,
                severity = Invariant.Severity.WARNING,
                checkPatterns = listOf("async task", "deprecated")
            ),
            Invariant(
                id = "business_001",
                name = "Кроссплатформенность",
                description = "Код должен быть кроссплатформенным",
                type = InvariantType.BUSINESS_RULE,
                severity = Invariant.Severity.ERROR,
                allowedValues = listOf("expect", "actual", "common", "kmp")
            ),
            Invariant(
                id = "security_001",
                name = "Безопасность данных",
                description = "Использовать безопасное хранение",
                type = InvariantType.SECURITY,
                severity = Invariant.Severity.WARNING,
                forbiddenValues = listOf("sharedpreferences", "plain text")
            )
        )
    )

    fun getWebInvariants(): InvariantSet = InvariantSet(
        name = "Web/Fullstack",
        description = "Инварианты для веб-разработки",
        version = "1.0",
        invariants = listOf(
            Invariant(
                id = "arch_web_001",
                name = "Архитектура",
                description = "Использовать микросервисную архитектуру",
                type = InvariantType.ARCHITECTURE,
                allowedValues = listOf("microservice", "rest", "graphql")
            ),
            Invariant(
                id = "tech_web_001",
                name = "Стек технологий",
                description = "Использовать только TypeScript и React",
                type = InvariantType.TECH_STACK,
                allowedValues = listOf("typescript", "react", "node.js"),
                forbiddenValues = listOf("php", "jquery", "asp.net")
            )
        )
    )

    fun getBaseInvariants(): InvariantSet = InvariantSet(
        name = "Base Rules",
        description = "Базовые правила для любого проекта",
        version = "1.0",
        invariants = listOf(
            Invariant(
                id = "base_001",
                name = "Язык ответов",
                description = "Отвечать на русском языке (код и технические термины могут быть на английском)",
                type = InvariantType.CUSTOM,
                severity = Invariant.Severity.ERROR,
                customCheck = "language_russian"
            ),
            Invariant(
                id = "base_002",
                name = "Краткость",
                description = "Ответы должны быть краткими и по делу",
                type = InvariantType.CUSTOM,
                severity = Invariant.Severity.WARNING,
                checkPatterns = listOf("очень длинный", "расписывать", "подробно объяснять")
            )
        )
    )
}
