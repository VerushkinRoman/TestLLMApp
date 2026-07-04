package com.llmapp.rag.data

import com.llmapp.rag.domain.Document
import com.llmapp.rag.domain.Section

object TechArticlesDocuments {

    fun getAll(): List<Document> = listOf(
        kmpMobileDev,
        cicdPipeline,
        chatDatabase,
    )

    private val kmpMobileDev = Document(
        id = "tech_001",
        title = "Разработка кроссплатформенных мобильных приложений на Kotlin Multiplatform",
        source = "Техническая документация KMP",
        content = """
Kotlin Multiplatform (KMP) — это технология от JetBrains, позволяющая переиспользовать код между Android, iOS, JVM и веб-платформами. В отличие от Flutter и React Native, KMP не заменяет нативный UI, а предоставляет общую бизнес-логику, сохраняя нативный интерфейс на каждой платформе. С появлением Compose Multiplatform стало возможным переиспользовать и UI-код.

Архитектура KMP проекта
Стандартная архитектура KMP-проекта строится по Clean Architecture с разделением на три слоя: data, domain и presentation. В слое domain находятся бизнес-сущности (model), интерфейсы репозиториев (repository) и use case'ы. Слой data содержит реализации репозиториев, DataSource'ы (online/offline), DTO и мапперы. Слой presentation содержит ViewModel, State, Event, Action и Compose UI.

MVI (Model-View-Intent) — это реактивный паттерн, рекомендованный для KMP-проектов. State описывает всё состояние экрана неизменяемым data class. Event (или Intent) — это sealed interface для событий от UI. Action — sealed interface для одноразовых действий (snackbar, навигация). ViewModel получает Event, обновляет State через copy(), и отправляет Action через SharedFlow.

Compose Multiplatform — это декларативный UI-фреймворк от JetBrains, основанный на Jetpack Compose. Он работает на Android, iOS, desktop (JVM) и вебе. Основные компоненты: @Composable функции, Modifier, State, LaunchedEffect, remember. Compose Multiplatform использует тот же API, что и Jetpack Compose для Android, но с некоторыми отличиями: для ресурсов используется org.jetbrains.compose.resources вместо Android R.

Ktor Client — это асинхронный HTTP-клиент от JetBrains для Kotlin, работающий на всех KMP-платформах. Поддерживает плагины: ContentNegotiation (JSON), WebSockets, Auth, Logging. Ktor Client использует kotlinx.coroutines и предоставляет suspend-функции для запросов. Настройка: HttpClient { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }.

SQLDelight — это кроссплатформенная библиотека для работы с SQLite, генерирующая типизированный Kotlin-код из SQL-запросов. Поддерживает миграции через .sqm файлы, потоки через .asFlow(). На Android использует Android SQLite Driver, на iOS — NativeSqliteDriver, на JVM — SqliteJdbcDriver. Пример: `val queries: MyQueries = database.myQueries`, `queries.selectAll().executeAsList()`.

Kodein-DI — это легковесный DI-фреймворк для Kotlin, поддерживающий KMP. Зависимости регистрируются через bindSingleton (для одного экземпляра) и bindProvider (для новых экземпляров). Получение через instance(). Пример: `val module = DI.Module("name") { bindSingleton { MyService() } }`. Kodein-DI не требует генерации кода, в отличие от Dagger.

Kotlin Flow и Coroutines — основа асинхронности в KMP. StateFlow используется для состояния, SharedFlow — для одноразовых событий. viewModelScope.launch { } запускает корутину. withContext(Dispatchers.IO) { } переключает контекст для IO-операций. Flow поддерживает операторы: map, filter, flatMapLatest, combine, catch. Для тестирования Flow используется библиотека Turbine.

Навигация в KMP: Voyager и Decompose — две основные библиотеки. Voyager предоставляет Screen, Navigator, TabNavigator. Decompose — более низкоуровневая, основанная на ComponentTree. Обе поддерживают Compose Multiplatform.

Тёмная тема в Compose Multiplatform реализуется через MaterialTheme с darkColorScheme. Определяются два набора цветов: lightColorScheme и darkColorScheme. Текущая тема определяется через isSystemInDarkTheme(). Переключение темы сохраняется в Settings (russhwolf/multiplatform-settings).

Тестирование в KMP: kotlin.test для модульных тестов, kotlinx-coroutines-test для тестирования корутин, Turbine для тестирования Flow. Для ViewModel тестов используется TestCoroutineDispatcher и Turbine: viewModel.viewStates().test { assertEquals(expected, awaitItem()) }.

Целевые версии: Android 8+ (API 26) и iOS 15+ — рекомендуемый минимум для современных KMP-проектов. Android 8+ обеспечивает поддержку Java 8, а iOS 15+ — Swift Concurrency и современные API.
""".trimIndent(),
        sections = listOf(
            Section("Архитектура KMP", "Clean Architecture: data/domain/presentation. MVI паттерн с State, Event, Action. viewModelScope для корутин. Compose Multiplatform для UI на всех платформах."),
            Section("Ktor Client", "Асинхронный HTTP-клиент, работающий на всех KMP платформах. Плагины: ContentNegotiation, WebSockets, Auth, Logging. Настройка через HttpClient DSL."),
            Section("SQLDelight", "Кроссплатформенная БД с генерацией типизированного Kotlin-кода из SQL. Миграции через .sqm файлы. Flow-подписки через .asFlow(). Разные драйверы для Android/iOS/JVM."),
            Section("Kodein-DI", "Легковесный DI без генерации кода. bindSingleton/bindProvider. Поддержка KMP. Получение через instance() с опциональным тэгом."),
            Section("Навигация и UI", "Voyager и Decompose для навигации. MaterialTheme для тёмной темы. isSystemInDarkTheme(). Settings для сохранения предпочтений."),
            Section("Тестирование", "kotlin.test, kotlinx-coroutines-test, Turbine для Flow. TestCoroutineDispatcher. Тестирование StateFlow через test { }."),
        ),
    )

    private val cicdPipeline = Document(
        id = "tech_002",
        title = "Непрерывная интеграция и доставка для KMP-проектов: CI/CD пайплайн",
        source = "DevOps документация",
        content = """
CI/CD (Continuous Integration / Continuous Delivery) — это практика автоматизации сборки, тестирования и развёртывания кода. Для KMP-проектов CI/CD особенно важен из-за необходимости собирать и тестировать код на нескольких платформах (Android, iOS, JVM).

GitHub Actions — это встроенная система CI/CD в GitHub. Для KMP-проекта требуется раннер на macOS (macos-latest), так как только на macOS можно собрать и подписать iOS-приложение. Windows и Linux раннеры подходят только для Android и JVM сборок. GitHub Actions бесплатен для публичных репозиториев (2000 минут/месяц на бесплатном плане, до 3000 для private).

Структура GitHub Actions пайплайна: файлы YAML в директории .github/workflows/. Каждый файл описывает workflow с триггерами (push, pull_request, schedule), джобами (jobs), шагами (steps). Джобы могут выполняться параллельно или последовательно (через needs).

Типовой пайплайн для KMP:
1. Checkout — actions/checkout@v4 — получение кода репозитория.
2. Setup JDK — actions/setup-java@v4 — установка JDK 17 или 21 для Kotlin.
3. Cache — actions/cache@v4 — кэширование ~/.gradle/caches для ускорения сборок.
4. Lint — ./gradlew detekt — статический анализ кода.
5. Format check — ./gradlew ktlintCheck — проверка форматирования.
6. Unit tests — ./gradlew allTests или ./gradlew :composeApp:allTests — запуск всех тестов.
7. Build Android — ./gradlew :composeApp:assembleDebug — сборка APK.
8. Build iOS — ./gradlew :composeApp:iosMain — компиляция iOS кода.
9. Upload artifacts — actions/upload-artifact@v4 — сохранение APK и IPA.

Detekt — это статический анализатор кода для Kotlin. Настройка через detekt.yml файл в корне проекта. Правила группируются по sets: potential-bugs, code-smell, style, naming, comments. Detekt интегрируется с Gradle через плагин org.gradle.detekt. Пример конфигурации: detekt { config = files("detekt.yml"); buildUponDefaultConfig = true }. Detekt 1.23+ поддерживает Type Resolution для более точного анализа.

ktlint — это линтер для форматирования Kotlin-кода, поддерживающий стандартные правила: 4 пробела для отступов, max_line_length = 120, no wildcard imports, no trailing comma. Интеграция с Gradle через плагин org.jlleitschuh.gradle.ktlint. Команды: ktlintCheck (проверка), ktlintFormat (автоформатирование). Spotless — альтернатива с поддержкой ktlint внутри.

Fastlane — это инструмент для автоматизации сборок и публикации мобильных приложений. Для iOS используйте Fastlane Match для управления сертификатами и профилями подписи. Match хранит сертификаты в зашифрованном Git-репозитории или Google Cloud. Команды: match development, match appstore, match adhoc. Для публикации в TestFlight используется pilot.

GitHub релизы и дистрибуция: actions/softprops/action-gh-release для создания релизов и загрузки артефактов. APK загружается как asset релиза. Для iOS используется Fastlane deliver для загрузки в App Store Connect и TestFlight.

Версионирование: VERSION_NAME в gradle.properties или git tag. GitHub Actions может автоматически определять версию по git tag или инкрементальный номер сборки. Пример: version = System.getenv("GITHUB_REF")?.removePrefix("refs/tags/v") ?: "1.0.0-SNAPSHOT".

Подпись iOS: требуется Apple Developer аккаунт ($99/год). Сертификаты и профили хранятся в GitHub Secrets (base64). Fastlane Match автоматически синхронизирует профили между разработчиками и CI. Ключи: APPLE_KEY_ID, APPLE_ISSUER_ID, APPLE_KEY_CONTENT, MATCH_PASSWORD.

GitHub Secrets: все чувствительные данные хранятся в Settings -> Secrets and variables -> Actions. Доступны через ${'$'}{{ secrets.SECRET_NAME }}. Для Android: KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD. Для iOS: APPLE_KEY, APPLE_KEY_ID, APPLE_ISSUER_ID, MATCH_PASSWORD.

Gradle Build Action (gradle/actions/setup-gradle@v4) — официальное действие Gradle для GitHub Actions, обеспечивающее кэширование зависимостей и дистрибутивов. Используйте его вместо ручной настройки cache для более надёжного кэширования.

Линтинг и форматирование в CI: Detekt для статического анализа (проверка багов, code smell, стиль). ktlint для форматирования (пробелы, отступы, сортировка импортов). Рекомендуется запускать на каждом PR, а не только на push в main, чтобы ловить проблемы до слияния.
""".trimIndent(),
        sections = listOf(
            Section("GitHub Actions для KMP", "macOS раннер (macos-latest) для iOS сборок. Триггеры push/pull_request/schedule. Джобы: lint, test, build, deploy. Кэширование Gradle."),
            Section("Структура пайплайна", "Checkout → Setup JDK → Cache → Lint → Build → Test → Upload. Параллельные джобы для Android и iOS. Последовательная доставка после успешной сборки."),
            Section("Detekt — статический анализ", "detekt.yml конфиг. Правила: potential-bugs, code-smell, style, naming. Type Resolution в 1.23+. Интеграция с Gradle."),
            Section("ktlint — форматирование", "4 пробела, 120 символов, no wildcard imports. ktlintCheck/ktlintFormat. Spotless как альтернатива. Проверка на каждом PR."),
            Section("Fastlane и подпись iOS", "Fastlane Match для сертификатов. pilot для TestFlight. deliver для App Store. Apple Developer аккаунт $99/год."),
            Section("Релизы и версионирование", "GitHub Releases + softprops/action-gh-release. VERSION_NAME или git tag. APK и IPA как артефакты. Secrets для ключей подписи."),
        ),
    )

    private val chatDatabase = Document(
        id = "tech_003",
        title = "Проектирование базы данных для чат-приложения: SQLDelight, миграции, шифрование и синхронизация",
        source = "Базы данных для KMP",
        content = """
Проектирование базы данных для чат-приложения на KMP требует учёта кроссплатформенности, производительности, офлайн-режима и безопасности. В этой статье рассматриваются все аспекты: от схемы данных до синхронизации с сервером.

SQLDelight — это основная библиотека для работы с SQLite в KMP. Она генерирует типизированный Kotlin-код из .sq файлов с SQL-запросами. Преимущества: проверка SQL на этапе компиляции, поддержка всех KMP-платформ, миграции, Flow-подписки.

Схема базы данных для чата:
— users(id TEXT PRIMARY KEY, name TEXT NOT NULL, avatar_url TEXT, created_at INTEGER NOT NULL)
— chats(id TEXT PRIMARY KEY, type TEXT NOT NULL CHECK(type IN ('private', 'group', 'channel')), title TEXT NOT NULL, avatar_url TEXT, created_at INTEGER NOT NULL, last_message_id TEXT)
— messages(id TEXT PRIMARY KEY, chat_id TEXT NOT NULL REFERENCES chats(id), user_id TEXT NOT NULL REFERENCES users(id), text TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER, status TEXT NOT NULL DEFAULT 'sent' CHECK(status IN ('sending', 'sent', 'delivered', 'read', 'failed')), reply_to_id TEXT REFERENCES messages(id))
— message_attachments(id TEXT PRIMARY KEY, message_id TEXT NOT NULL REFERENCES messages(id), type TEXT NOT NULL CHECK(type IN ('image', 'file', 'voice', 'video')), url TEXT NOT NULL, name TEXT, size INTEGER, mime_type TEXT)
— chat_participants(chat_id TEXT NOT NULL REFERENCES chats(id), user_id TEXT NOT NULL REFERENCES users(id), role TEXT NOT NULL DEFAULT 'member', joined_at INTEGER NOT NULL, PRIMARY KEY(chat_id, user_id))

Индексы для производительности:
— CREATE INDEX idx_messages_chat_created ON messages(chat_id, created_at DESC) — быстрая загрузка истории чата
— CREATE INDEX idx_messages_user ON messages(user_id) — поиск сообщений пользователя
— CREATE INDEX idx_messages_status ON messages(status) WHERE status != 'sent' — фильтр непрочитанных

Миграции в SQLDelight: файлы с расширением .sqm (например, 1.sqm, 2.sqm, 3.sqm). Каждый файл содержит ALTER TABLE или CREATE TABLE запросы. SQLDelight автоматически применяет миграции по порядку. Важно: никогда не удаляйте старые .sqm файлы, так как они нужны для обновления с любой предыдущей версии базы. Пример 1.sqm: ALTER TABLE messages ADD COLUMN reply_to_id TEXT REFERENCES messages(id). Пример 2.sqm: CREATE TABLE message_attachments (...).

Шифрование базы данных: SQLDelight не имеет встроенного шифрования. Для шифрования используйте SQLCipher — обёртку над SQLite с AES-256 шифрованием. SQLCipher доступен для Android (через database-driver) и iOS (через CocoaPods или SPM). Ключ шифрования должен храниться в безопасном месте: Android — EncryptedSharedPreferences (AndroidX Security), iOS — Keychain. На JVM для desktop можно использовать sqlcipher-jdbc или упрощённый подход с шифрованием отдельных полей через kotlinx-serialization.

Offline-first синхронизация — паттерн, при котором все операции сначала выполняются локально, а затем синхронизируются с сервером. Компоненты: локальная БД (SQLDelight) как источник истины, сетевая служба для отправки изменений, вебсокеты (Ktor WebSockets) для получения изменений в реальном времени.

Схема синхронизации: каждая запись имеет sync_status (synced, pending, failed) и updated_at. При создании записи статус = pending. Фоновая служба периодически отправляет pending записи на сервер. При успешном ответе статус = synced. При ошибке статус = failed. Сервер отправляет изменения через вебсокеты с указанием action (created, updated, deleted) и affected record ID.

Решение конфликтов: Last-Write-Wins (LWW) — запись с более поздним updated_at побеждает. Для сложных сценариев используется CRDT (Conflict-free Replicated Data Types), где конфликтов не возникает математически. Для чат-приложения LWW достаточно, так как каждое сообщение — это независимая сущность.

Производительность SQLite при большом количестве записей: SQLite стабильно работает с миллионами записей при правильной индексации. Индекс на (chat_id, created_at DESC) критичен для быстрой загрузки истории. Без индекса загрузка 100K сообщений может занимать секунды, с индексом — <10мс. Рекомендуется архивировать сообщения старше 6 месяцев в отдельную таблицу для поддержания производительности.

Вебсокеты: Ktor Client поддерживает WebSockets плагин. Соединение устанавливается при запуске приложения и поддерживается в фоне. При получении нового сообщения через WebSocket, оно сразу сохраняется в локальную БД и отображается в UI через StateFlow. URL: wss://server.com/ws?token=... или wss://server.com/ws/{userId}.

Кэширование медиа: изображения и файлы кэшируются на диске с использованием TTL (Time To Live). thumbnail генерируется на сервере и передаётся как base64. Для iOS используется NSCache, для Android — Glide или Coil.

Резервное копирование: локальная БД регулярно экспортируется в JSON для резервного копирования. На Android используется BackupAgent, на iOS — iCloud Backup. Пользователь может вручную экспортировать историю чата.
""".trimIndent(),
        sections = listOf(
            Section("Схема БД для чата", "Таблицы: users, chats, messages, message_attachments, chat_participants. Внешние ключи, CHECK-ограничения. Индексы на chat_id + created_at для производительности."),
            Section("SQLDelight миграции", ".sqm файлы: 1.sqm, 2.sqm и т.д. Автоматическое применение. Не удалять старые миграции. ALTER TABLE, CREATE TABLE."),
            Section("Шифрование SQLCipher", "AES-256 шифрование. Android: EncryptedSharedPreferences. iOS: Keychain. Десктоп: sqlcipher-jdbc или шифрование полей."),
            Section("Offline-first синхронизация", "Локальная БД как источник истины. sync_status: synced/pending/failed. Ktor WebSockets для real-time. Фоновая отправка pending записей."),
            Section("Производительность", "Индексы критичны. <10мс при 100K сообщениях с индексом. Архивация старых сообщений (6+ месяцев). SQLite стабилен до миллионов записей."),
            Section("Вебсокеты", "Ktor WebSockets плагин. Постоянное соединение. Сохранение в БД + UI через StateFlow. wss:// URL с токеном аутентификации."),
        ),
    )
}
