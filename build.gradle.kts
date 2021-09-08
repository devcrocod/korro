import com.gradle.publish.PluginBundleExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("java")
    id("com.gradle.plugin-publish") version ("0.15.0")
    `java-gradle-plugin`
    `maven-publish`
}

group = "me.devcrocod"
version = detectVersion()

fun detectVersion(): String {
    val buildNumber = rootProject.findProperty("build.number") as String?
    return if (buildNumber != null) {
        if (hasProperty("build.number.detection")) {
            "$version-dev-$buildNumber"
        } else {
            buildNumber
        }
    } else if (hasProperty("release")) {
        version as String
    } else {
        "$version-dev"
    }
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

val dokkaVersion: String by project
dependencies {
    compileOnly(gradleApi())
    compileOnly("org.jetbrains.dokka:dokka-core:$dokkaVersion")
    compileOnly("org.jetbrains.dokka:dokka-analysis:$dokkaVersion")
}

val language_version: String by project
tasks.withType(KotlinCompile::class).all {
    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-Xopt-in=kotlin.RequiresOptIn",
            "-Xskip-metadata-version-check",
            "-Xjsr305=strict"
        )
        languageVersion = language_version
        apiVersion = language_version
        jvmTarget = "1.8"
    }
}


apply {
        plugin("org.jetbrains.kotlin.jvm")
        plugin("java")
        plugin("signing")
}

// Gradle metadata
java {
    withSourcesJar()
    withJavadocJar()
    targetCompatibility = JavaVersion.VERSION_1_8
}

extensions.getByType(PluginBundleExtension::class).apply {
    website = "https://github.com/devcrocod/korro"
    vcsUrl = website
    tags = listOf("kotlin", "documentation", "markdown")
}

gradlePlugin {
    plugins {
        create("korro") {
            id = "me.devcrocod.korro"
            implementationClass = "me.devcrocod.korro.KorroPlugin"
            displayName = "Korro documentation plugin"
            description = "Inserts snippets code of Kotlin into markdown documents from source example files and tests."
        }
    }
}
