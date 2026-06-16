package com.llmapp.ui.components

import com.llmapp.memory.ResponseStyle
import com.llmapp.memory.UserProfile

object ProfilePresets {

    fun getAllPresets(): List<NamedProfile> = listOf(
        androidDev,
        fullstackDev,
        juniorDev,
        architect,
        dataScientist,
        devops
    )

    val androidDev = NamedProfile(
        name = "Android Developer",
        icon = "📱",
        profile = UserProfile(
            name = "Android Developer",
            experience = "Middle Android Developer, 3-5 лет",
            preferredStyle = ResponseStyle.TECHNICAL,
            preferredTech = listOf(
                "Kotlin",
                "Jetpack Compose",
                "Coroutines",
                "Flow",
                "Retrofit",
                "Room"
            ),
            commonGoals = listOf("Разработка мобильных приложений", "KMP", "Clean Architecture"),
            customNotes = "Предпочитаю примеры кода, практические решения. Важна производительность и оптимизация."
        )
    )

    val fullstackDev = NamedProfile(
        name = "Fullstack Developer",
        icon = "🌐",
        profile = UserProfile(
            name = "Fullstack Developer",
            experience = "Senior Fullstack Developer, 7+ лет",
            preferredStyle = ResponseStyle.DETAILED,
            preferredTech = listOf(
                "TypeScript",
                "React",
                "Node.js",
                "Python",
                "PostgreSQL",
                "Docker"
            ),
            commonGoals = listOf("Микросервисная архитектура", "DevOps", "Cloud Native"),
            customNotes = "Нужны объяснения на высоком уровне. Интересует архитектура и масштабирование."
        )
    )

    val juniorDev = NamedProfile(
        name = "Junior Developer",
        icon = "🌱",
        profile = UserProfile(
            name = "Junior Developer",
            experience = "Junior Developer, стаж 0-1 год",
            preferredStyle = ResponseStyle.CONCISE,
            preferredTech = listOf("Python", "JavaScript", "HTML/CSS", "Git"),
            commonGoals = listOf("Изучение основ программирования", "Практические проекты"),
            customNotes = "Нужны простые объяснения, без сложной терминологии. Желательны пошаговые инструкции."
        )
    )

    val architect = NamedProfile(
        name = "Software Architect",
        icon = "🏗️",
        profile = UserProfile(
            name = "Software Architect",
            experience = "Solutions Architect, 15+ лет",
            preferredStyle = ResponseStyle.TECHNICAL,
            preferredTech = listOf("Kotlin", "KMP", "Compose Multiplatform", "Ktor", "AWS"),
            commonGoals = listOf("Кроссплатформенные решения", "Агентные системы", "Архитектура"),
            customNotes = "Глубокий технический уровень. Нужна архитектурная обоснованность и best practices."
        )
    )

    val dataScientist = NamedProfile(
        name = "Data Scientist",
        icon = "📊",
        profile = UserProfile(
            name = "Data Scientist",
            experience = "Data Scientist, 4+ лет",
            preferredStyle = ResponseStyle.DETAILED,
            preferredTech = listOf("Python", "PyTorch", "TensorFlow", "Pandas", "SQL"),
            commonGoals = listOf("ML модели", "Анализ данных", "AI агенты"),
            customNotes = "Нужны математические обоснования и примеры кода. Интересует производительность моделей."
        )
    )

    val devops = NamedProfile(
        name = "DevOps Engineer",
        icon = "⚙️",
        profile = UserProfile(
            name = "DevOps Engineer",
            experience = "DevOps Engineer, 5+ лет",
            preferredStyle = ResponseStyle.CONCISE,
            preferredTech = listOf("Kubernetes", "Docker", "Terraform", "AWS", "CI/CD"),
            commonGoals = listOf("Инфраструктура как код", "Автоматизация", "Мониторинг"),
            customNotes = "Нужны конкретные команды и конфигурации. Важна безопасность и масштабируемость."
        )
    )
}

data class NamedProfile(
    val name: String,
    val icon: String,
    val profile: UserProfile
)
