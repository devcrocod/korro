plugins {
    kotlin("jvm")
    id("com.gradleup.shadow") version "9.4.1"
    `maven-publish`
}

val kotlin_version: String by project

repositories {
    mavenCentral()
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
}

dependencies {
    compileOnly(kotlin("stdlib"))

    implementation("org.jetbrains.kotlin:analysis-api-for-ide:$kotlin_version") { isTransitive = false }
    implementation("org.jetbrains.kotlin:analysis-api-impl-base-for-ide:$kotlin_version") { isTransitive = false }
    implementation("org.jetbrains.kotlin:analysis-api-platform-interface-for-ide:$kotlin_version") { isTransitive = false }
    implementation("org.jetbrains.kotlin:analysis-api-standalone-for-ide:$kotlin_version") { isTransitive = false }
    implementation("org.jetbrains.kotlin:analysis-api-k2-for-ide:$kotlin_version") { isTransitive = false }
    implementation("org.jetbrains.kotlin:low-level-api-fir-for-ide:$kotlin_version") { isTransitive = false }
    implementation("org.jetbrains.kotlin:symbol-light-classes-for-ide:$kotlin_version") { isTransitive = false }

    implementation("org.jetbrains.kotlin:kotlin-compiler:$kotlin_version")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.11.0")
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.3")
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
