plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
    alias(libs.plugins.mavenPublish)
}

repositories {
    mavenCentral()
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
}

dependencies {
    compileOnly(libs.kotlin.stdlib)

    implementation(libs.kotlin.analysisApi) { isTransitive = false }
    implementation(libs.kotlin.analysisApi.implBase) { isTransitive = false }
    implementation(libs.kotlin.analysisApi.platformInterface) { isTransitive = false }
    implementation(libs.kotlin.analysisApi.standalone) { isTransitive = false }
    implementation(libs.kotlin.analysisApi.k2) { isTransitive = false }
    implementation(libs.kotlin.lowLevelApiFir) { isTransitive = false }
    implementation(libs.kotlin.symbolLightClasses) { isTransitive = false }

    implementation(libs.kotlin.compiler)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.caffeine)
}

tasks.shadowJar {
    archiveClassifier.set("")
    isZip64 = true
    mergeServiceFiles()

    exclude("com/sun/jna/**")
    exclude("org/jline/**")
    exclude("io/vavr/**")
    exclude("org/fusesource/**")
    exclude("org/jetbrains/kotlin/js/**")
    exclude("org/jetbrains/kotlin/ir/backend/js/**")
    exclude("org/jetbrains/kotlin/incremental/**")
    exclude("org/jetbrains/kotlin/backend/wasm/**")
    exclude("org/jetbrains/kotlin/backend/konan/**")
    exclude("org/jetbrains/kotlin/psi2ir/**")
    exclude("org/jetbrains/kotlin/cli/js/**")
    exclude("org/jetbrains/kotlin/cli/metadata/**")
    exclude("org/jetbrains/kotlin/library/**")
}

tasks.jar {
    enabled = false
    dependsOn("shadowJar")
}

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

val emptyJavadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifact(tasks.shadowJar)
            artifact(sourcesJar)
            artifact(emptyJavadocJar)
            groupId = project.group.toString()
            artifactId = "korro-analysis"
            version = project.version.toString()
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

    coordinates(project.group.toString(), "korro-analysis", project.version.toString())

    pom {
        name.set("Korro Analysis")
        description.set(
            "Kotlin Analysis API (K2 standalone) backend for Korro"
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
