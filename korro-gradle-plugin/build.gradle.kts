plugins {
    kotlin("jvm")
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "2.1.1"
    `maven-publish`
    id("com.gradleup.shadow") version "9.4.1"
}

configurations.named(JavaPlugin.API_CONFIGURATION_NAME) {
    dependencies.remove(project.dependencies.gradleApi())
}

val kotlin_version: String by project
dependencies {
    shadow(kotlin("stdlib-jdk8", version = kotlin_version))

    compileOnly(project(":korro-analysis"))

    shadow(gradleApi())
    shadow(gradleKotlinDsl())
}

val generateKorroVersionResource by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/korroVersion")
    val korroVersion = project.version.toString()
    inputs.property("korroVersion", korroVersion)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().file("META-INF/korro-gradle-plugin.properties").asFile
        file.parentFile.mkdirs()
        file.writeText("version=$korroVersion\n")
    }
}

tasks.processResources {
    from(generateKorroVersionResource)
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
