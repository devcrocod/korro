import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}

group = "io.github.devcrocod"
version = detectVersion()

fun detectVersion(): String {
    val buildNumber = findProperty("build.number") as String?
    val baseVersion = version as String
    return when {
        hasProperty("release") -> baseVersion
        buildNumber != null    -> "$baseVersion-dev-$buildNumber"
        else                   -> "$baseVersion-dev"
    }
}

val kotlinLanguageVersion = libs.versions.kotlinLanguage.get()

subprojects {
    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
        gradlePluginPortal()
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            freeCompilerArgs.addAll(
                "-opt-in=kotlin.RequiresOptIn",
                "-Xskip-metadata-version-check",
                "-Xjsr305=strict",
            )
            languageVersion.set(KotlinVersion.fromVersion(kotlinLanguageVersion))
            apiVersion.set(KotlinVersion.fromVersion(kotlinLanguageVersion))
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        sourceCompatibility = JavaVersion.VERSION_17.toString()
        targetCompatibility = JavaVersion.VERSION_17.toString()
    }
}
