import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    // Compose Multiplatform
    implementation(compose.desktop.currentOs)
    implementation(libs.jetbrains.compose.material3)
    implementation(libs.jetbrains.compose.material.icons.extended)
    implementation(libs.jetbrains.compose.foundation)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)

    // Ktor
    implementation(libs.bundles.ktor)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // MCP SDK
    implementation(libs.mcp.kotlin.sdk.client)

    // Logging
    implementation(libs.slf4j.nop)

    // ViewModel
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
}

kotlin {
    jvmToolchain(17)
}

compose.desktop {
    application {
        mainClass = "com.llmapp.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "LLM Chat App"
            packageVersion = "1.0.0"
        }
    }
}

// === AI Code Review Runner (fat JAR) ===
// Exclude Compose/UI deps from runner; they aren't needed for CLI

val runnerLibs = configurations.create("runnerLibs") {
    isCanBeResolved = true
    isCanBeConsumed = false
    extendsFrom(configurations.implementation.get())
}

val excludeJars = listOf(
    "compose-", "material-", "material3-", "material-icons-",
    "foundation-", "lifecycle-viewmodel-", "lifecycle-runtime-",
    "animation-", "ui-", "runtime-saveable-",
)

tasks.register<Jar>("reviewRunnerJar") {
    dependsOn(runnerLibs)
    archiveBaseName.set("review-runner")
    archiveVersion.set("")
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(sourceSets.main.get().output)

    val filtered = runnerLibs.filter { file ->
        excludeJars.none { file.name.contains(it, ignoreCase = true) }
    }
    from(filtered.map { file ->
        if (file.isDirectory) file else zipTree(file)
    })

    manifest {
        attributes(mapOf("Main-Class" to "com.llmapp.pr_review.PRReviewRunnerKt"))
    }
}

tasks.register<JavaExec>("runPrReview") {
    dependsOn("reviewRunnerJar")
    classpath = files(tasks.named<Jar>("reviewRunnerJar").get().archiveFile)
    mainClass = "com.llmapp.pr_review.PRReviewRunnerKt"
}

// === FileAssistant Test Runner (console, no GUI) ===
tasks.register<JavaExec>("runFileTest") {
    dependsOn("classes")
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "com.llmapp.assistant.FileAssistantTestRunnerKt"
    args = project.findProperty("args")?.toString()?.split(" ") ?: emptyList()
}

tasks.withType<JavaExec> {
    systemProperty("file.encoding", "UTF-8")
}
