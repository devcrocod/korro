package io.github.devcrocod.korro.it

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.InvalidPluginMetadataException
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

class KorroIntegrationTest {

    @Test
    fun basicFixture(@TempDir tempDir: Path) {
        runFixture(
            name = "basic",
            tempDir = tempDir,
            generatedRelativePath = "build/korro/docs/foo.md",
            expectedRelativePath = "basic/docs/expected/foo.md",
        )
    }

    @Test
    fun commonTestFixture(@TempDir tempDir: Path) {
        runFixture(
            name = "commonTest",
            tempDir = tempDir,
            generatedRelativePath = "build/korro/docs/readme.md",
            expectedRelativePath = "commonTest/docs/expected/readme.md",
        )
    }

    private fun runFixture(
        name: String,
        tempDir: Path,
        generatedRelativePath: String,
        expectedRelativePath: String,
    ) {
        val fixture = loadFixture(name, tempDir)

        val runner = GradleRunner.create()
            .withProjectDir(fixture.toFile())
            .withArguments("korro", "--stacktrace")
            .forwardOutput()

        configurePluginClasspath(runner)

        System.getProperty("korro.testkit.gradleVersion")
            ?.takeIf { it.isNotBlank() }
            ?.let(runner::withGradleVersion)

        val result = runner.build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":korro")?.outcome, "korro task outcome for $name")

        val actualFile = fixture.resolve(generatedRelativePath)
        assertTrue(Files.exists(actualFile)) { "korro did not produce $actualFile" }

        val expectedFile = fixturesRoot().resolve(expectedRelativePath)
        val actual = normalize(actualFile.readText())

        if (System.getProperty("korro.regenerate.expected") == "true") {
            expectedFile.writeText(actual)
            return
        }

        val expected = normalize(expectedFile.readText())
        assertEquals(expected, actual, "Generated markdown does not match golden file for $name")
    }

    private fun loadFixture(name: String, tempDir: Path): Path {
        val source = fixturesRoot().resolve(name).toFile()
        val target = tempDir.resolve(name).toFile()
        source.copyRecursively(target, overwrite = true)
        return target.toPath()
    }

    private fun fixturesRoot(): Path {
        System.getProperty("korro.fixtures.dir")?.let { return File(it).toPath() }

        val cwd = File("").absoluteFile.toPath()
        val candidates = listOf(
            cwd.resolve("fixtures"),
            cwd.resolve("integration-tests/fixtures"),
            cwd.parent?.resolve("fixtures"),
        ).filterNotNull()
        return candidates.firstOrNull { Files.isDirectory(it) }
            ?: error(
                "Cannot locate integration-tests/fixtures. " +
                        "Set system property 'korro.fixtures.dir' or run via `./gradlew integration-tests:test`. " +
                        "CWD=$cwd"
            )
    }

    private fun normalize(s: String): String = s.replace("\r\n", "\n")

    private fun configurePluginClasspath(runner: GradleRunner) {
        try {
            runner.withPluginClasspath()
        } catch (_: InvalidPluginMetadataException) {
            runner.withPluginClasspath(fallbackPluginClasspath())
        }
    }

    private fun fallbackPluginClasspath(): List<File> {
        val jar = findPluginShadowJar()
            ?: error(
                "Cannot locate korro-gradle-plugin shadow jar. " +
                        "Run `./gradlew korro-gradle-plugin:shadowJar` first, " +
                        "or run tests via `./gradlew integration-tests:test`."
            )
        return listOf(jar)
    }

    private fun findPluginShadowJar(): File? {
        val cwd = File("").absoluteFile
        val candidates = listOf(
            cwd.resolve("../korro-gradle-plugin/build/libs"),
            cwd.resolve("korro-gradle-plugin/build/libs"),
            cwd.parentFile?.resolve("korro-gradle-plugin/build/libs"),
        ).filterNotNull().filter { it.isDirectory }
        return candidates.asSequence()
            .flatMap { (it.listFiles { _, name -> name.endsWith(".jar") } ?: emptyArray<File>()).asSequence() }
            .filterNot { it.name.contains("-sources") || it.name.contains("-javadoc") }
            .firstOrNull()
    }
}
