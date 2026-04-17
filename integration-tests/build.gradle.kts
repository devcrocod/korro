import org.gradle.plugin.devel.tasks.PluginUnderTestMetadata

plugins {
    kotlin("jvm")
    `java-gradle-plugin`
}

evaluationDependsOn(":korro-gradle-plugin")

val pluginShadowJar = project(":korro-gradle-plugin").tasks.named("shadowJar")

dependencies {
    testImplementation(gradleTestKit())
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
    systemProperty("korro.testkit.gradleVersion", "8.5")
    systemProperty("korro.regenerate.expected", regenerateExpected.get())
    systemProperty("korro.fixtures.dir", layout.projectDirectory.dir("fixtures").asFile.absolutePath)
}
