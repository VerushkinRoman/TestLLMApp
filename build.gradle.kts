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
    // Compose Multiplatform (only for desktop app)
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

val runnerLibs = configurations.create("runnerLibs") {
    isCanBeResolved = true
    isCanBeConsumed = false
    extendsFrom(configurations.implementation.get()!!)
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

tasks.withType<JavaExec> {
    systemProperty("file.encoding", "UTF-8")
}

// ProGuard minification — запускается отдельной задачей
// Требует: brew install proguard
val proguardRules = """
# === review-runner ProGuard rules ===

-injars build/libs/review-runner.jar
-outjars build/libs/review-runner-min.jar

-libraryjars <java.home>/jmods

-dontwarn
-dontnote

-keep class com.llmapp.pr_review.** { *; }
-keep class com.llmapp.rag.data.GitHubApi** { *; }
-keep class com.llmapp.mcp.GitHubMcpTools** { *; }
-keep class com.llmapp.api.** { *; }
-keep class com.llmapp.model.** { *; }

# Kotlin
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }

# Ktor
-keep class io.ktor.** { *; }

# MCP
-keep class io.modelcontextprotocol.** { *; }

# Serialization
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}

# Keep all public constructors
-keepclassmembers class * {
    public <init>(...);
}
""".trimIndent()

tasks.register("proguardReviewRunner") {
    dependsOn("reviewRunnerJar")
    doFirst {
        file("build/review-runner.pro").writeText(proguardRules)
    }
    doLast {
        val proguardJar = "/opt/homebrew/Cellar/proguard/7.6.1/libexec/lib/proguard.jar"
        val proguardFile = file(proguardJar)
        if (!proguardFile.exists()) {
            logger.warn("ProGuard not found at $proguardJar. Run: brew install proguard")
            logger.warn("Skipping minification, using fat JAR as-is")
            copy {
                from("build/libs/review-runner.jar")
                into("build/libs/")
                rename { "review-runner-min.jar" }
            }
        } else {
            javaexec {
                classpath = files(proguardFile)
                mainClass.set("proguard.ProGuard")
                args("-include", "build/review-runner.pro")
            }
        }
    }
}
