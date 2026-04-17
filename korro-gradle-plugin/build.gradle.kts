plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-gradle-plugin`
    alias(libs.plugins.pluginPublish)
    `maven-publish`
    alias(libs.plugins.shadow)
}

configurations.named(JavaPlugin.API_CONFIGURATION_NAME) {
    dependencies.remove(project.dependencies.gradleApi())
}

dependencies {
    shadow(libs.kotlin.stdlib)

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
