import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "1.1.0"
    `maven-publish`
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "io.github.devcrocod"
version = detectVersion()

fun detectVersion(): String {
    val buildNumber = rootProject.findProperty("build.number") as String?
    return if (hasProperty("release")) {
        version as String
    } else if (buildNumber != null) {
        "$version-dev-$buildNumber"
    } else {
        "$version-dev"
    }
}

configurations.named(JavaPlugin.API_CONFIGURATION_NAME) {
    dependencies.remove(project.dependencies.gradleApi())
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

val dokka_version: String by project
val kotlin_version: String by project
dependencies {
    shadow(kotlin("stdlib-jdk8", version = kotlin_version))
    shadow("org.jetbrains.dokka:dokka-core:$dokka_version")
    shadow("org.jetbrains.dokka:dokka-analysis:$dokka_version")

    shadow(gradleApi())
    shadow(gradleKotlinDsl())
}

tasks.shadowJar {
    isZip64 = true
    archiveClassifier.set("")
}


tasks.jar {
    enabled = false
    dependsOn("shadowJar")
    manifest {
        attributes(
            "Implementation-Title" to "$archiveBaseName",
            "Implementation-Version" to "$archiveVersion"
        )
    }
}

val language_version: String by project
tasks.withType(KotlinCompile::class).all {
    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-Xskip-metadata-version-check",
            "-Xjsr305=strict"
        )
        languageVersion = language_version
        apiVersion = language_version
    }
}

gradlePlugin {
    website.set("https://github.com/devcrocod/korro")
    vcsUrl.set("https://github.com/devcrocod/korro")
    plugins {
        create("korro") {
            id = "io.github.devcrocod.korro"
            implementationClass = "io.github.devcrocod.korro.KorroPlugin"
            displayName = "Korro documentation plugin"
            description = "Inserts snippets code of Kotlin into markdown documents from source example files and tests."
            tags.set(listOf("kotlin", "documentation", "markdown"))
        }
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

tasks.withType<JavaCompile> {
    sourceCompatibility = JavaVersion.VERSION_1_8.toString()
    targetCompatibility = JavaVersion.VERSION_1_8.toString()
}