import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version ("0.15.0")
    `maven-publish`
    id("com.github.johnrengelman.shadow") version "7.0.0"
}

group = "io.github.devcrocod"
version = detectVersion()

fun detectVersion(): String {
    val buildNumber = rootProject.findProperty("build.number") as String?
    return if (buildNumber != null) {
        "$version-dev-$buildNumber"
    } else if (hasProperty("release")) {
        version as String
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

    compileOnly(gradleApi())
    implementation("org.jetbrains.dokka:dokka-analysis:$dokka_version")
}

tasks {
    shadowJar {
        relocate("com.intellij", "io.github.devcrocod.com.intellij")
        relocate("org.jetbrains.kotlin", "io.github.devcrocod.org.jetbrains.kotlin")
        mergeServiceFiles()

        archiveClassifier.set("")
        dependencies {
            exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib"))
            exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib-jdk8"))
            exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib-jdk7"))
            exclude(dependency("org.jetbrains.kotlin:kotlin-stdlib-common"))
            exclude(dependency("org.jetbrains:annotations"))
            exclude(dependency("org.jetbrains.dokka:dokka-core:$dokka_version"))
        }
    }
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
            "-Xopt-in=kotlin.RequiresOptIn",
            "-Xskip-metadata-version-check",
            "-Xjsr305=strict"
        )
        languageVersion = language_version
        apiVersion = language_version
        jvmTarget = "1.8"
    }
}

gradlePlugin {
    plugins {
        create("korro") {
            id = "io.github.devcrocod.korro"
            implementationClass = "io.github.devcrocod.korro.KorroPlugin"
            displayName = "Korro documentation plugin"
//            description = "Inserts snippets code of Kotlin into markdown documents from source example files and tests."
        }
    }
}

pluginBundle {
    website = "https://github.com/devcrocod/korro"
    vcsUrl = "https://github.com/devcrocod/korro"
    description = "Inserts snippets code of Kotlin into markdown documents from source example files and tests."
    tags = listOf("kotlin", "documentation", "markdown")
}
