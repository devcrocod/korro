plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "1.1.0"
    `maven-publish`
    id("com.gradleup.shadow") version "8.3.5"
}

configurations.named(JavaPlugin.API_CONFIGURATION_NAME) {
    dependencies.remove(project.dependencies.gradleApi())
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
