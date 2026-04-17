import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") apply false
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

val language_version: String by project

subprojects {
    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
        gradlePluginPortal()
    }

    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions {
            freeCompilerArgs = freeCompilerArgs + listOf(
                "-opt-in=kotlin.RequiresOptIn",
                "-Xskip-metadata-version-check",
                "-Xjsr305=strict",
            )
            languageVersion = language_version
            apiVersion = language_version
            jvmTarget = "1.8"
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        sourceCompatibility = JavaVersion.VERSION_1_8.toString()
        targetCompatibility = JavaVersion.VERSION_1_8.toString()
    }
}
