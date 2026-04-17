plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
    `maven-publish`
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

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifact(tasks.shadowJar)
            groupId = project.group.toString()
            artifactId = "korro-analysis"
            version = project.version.toString()
        }
    }
}
