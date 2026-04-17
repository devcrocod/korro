import org.gradle.plugin.devel.tasks.PluginUnderTestMetadata

plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-gradle-plugin`
}

evaluationDependsOn(":korro-gradle-plugin")

val pluginShadowJar = project(":korro-gradle-plugin").tasks.named("shadowJar")

dependencies {
    testImplementation(gradleTestKit())
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

gradlePlugin {
    testSourceSets(sourceSets["test"])
}

tasks.named<PluginUnderTestMetadata>("pluginUnderTestMetadata") {
    pluginClasspath.from(pluginShadowJar)
}

val regenerateExpected = providers.gradleProperty("korro.regenerate.expected").orElse("false")

tasks.test {
    useJUnitPlatform()
    dependsOn(":korro-gradle-plugin:shadowJar")
    dependsOn(":korro-analysis:publishToMavenLocal")
    systemProperty("korro.testkit.gradleVersion", "8.5")
    systemProperty("korro.regenerate.expected", regenerateExpected.get())
    systemProperty("korro.fixtures.dir", layout.projectDirectory.dir("fixtures").asFile.absolutePath)
}
