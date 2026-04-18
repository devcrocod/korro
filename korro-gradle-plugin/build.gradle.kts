plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-gradle-plugin`
    alias(libs.plugins.pluginPublish)
    alias(libs.plugins.mavenPublish)
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

val signingEnabled = providers.gradleProperty("signingInMemoryKey").isPresent ||
    providers.gradleProperty("signing.keyId").isPresent

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    if (signingEnabled) {
        signAllPublications()
    }

    coordinates(project.group.toString(), "korro-gradle-plugin", project.version.toString())

    pom {
        name.set("Korro Gradle Plugin")
        description.set(
            "Gradle plugin that injects Kotlin sample snippets into documentation"
        )
        inceptionYear.set("2021")
        url.set("https://github.com/devcrocod/korro")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("devcrocod")
                name.set("Pavel Gorgulov")
                url.set("https://github.com/devcrocod")
            }
        }
        scm {
            url.set("https://github.com/devcrocod/korro")
            connection.set("scm:git:git://github.com/devcrocod/korro.git")
            developerConnection.set("scm:git:ssh://git@github.com/devcrocod/korro.git")
        }
    }
}
