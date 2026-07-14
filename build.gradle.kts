import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.shadow)
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(libs.jetbrains.compose.material3)
    implementation(libs.jetbrains.compose.material.icons.extended)
    implementation(libs.jetbrains.compose.foundation)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.bundles.ktor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.mcp.kotlin.sdk.client)
    implementation(libs.slf4j.nop)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
}

kotlin { jvmToolchain(17) }

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

val excludePatterns = listOf(
    "compose-", "material-", "foundation-",
    "lifecycle-", "animation-", "ui-", "runtime-saveable-",
).map { it.lowercase() }

tasks.register<Jar>("reviewRunnerJar") {
    from(sourceSets.main.get().output)

    val runtime = configurations.runtimeClasspath.get()
    from(runtime.filter { jar ->
        !excludePatterns.any { jar.name.lowercase().contains(it) }
    }.map { jar ->
        if (jar.isDirectory) jar else zipTree(jar)
    })

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveBaseName.set("review-runner")
    archiveVersion.set("")
    archiveClassifier.set("")

    manifest {
        attributes("Main-Class" to "com.llmapp.pr_review.PRReviewRunnerKt")
    }
}

tasks.register<JavaExec>("runPrReview") {
    dependsOn("reviewRunnerJar")
    classpath = files(tasks.named<Jar>("reviewRunnerJar").get().archiveFile)
    mainClass = "com.llmapp.pr_review.PRReviewRunnerKt"
}

tasks.withType<JavaExec> {
    systemProperty("file.encoding", "UTF-8")
}
