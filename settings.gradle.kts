pluginManagement {
    val kotlin_version: String by settings
    plugins {
        id("org.jetbrains.kotlin.jvm") version kotlin_version
    }
}

rootProject.name = "korro"

include("korro-gradle-plugin", "korro-analysis", "integration-tests")