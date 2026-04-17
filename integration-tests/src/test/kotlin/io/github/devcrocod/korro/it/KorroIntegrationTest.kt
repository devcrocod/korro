package io.github.devcrocod.korro.it

import org.gradle.testkit.runner.GradleRunner
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
    fun korroReplacesFunDirectiveWithSnippet(@TempDir tempDir: Path) {
        val fixture = loadFixture("basic", tempDir)

        val runner = GradleRunner.create()
            .withProjectDir(fixture.toFile())
            .withArguments("korro", "--stacktrace")
            .withPluginClasspath()
            .forwardOutput()

        System.getProperty("korro.testkit.gradleVersion")
            ?.takeIf { it.isNotBlank() }
            ?.let(runner::withGradleVersion)

        val result = runner.build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":korro")?.outcome, "korro task outcome")

        val actualFile = fixture.resolve("build/korro/docs/foo.md")
        assertTrue(Files.exists(actualFile)) { "korro did not produce $actualFile" }

        val expectedFile = fixturesRoot().resolve("basic/docs/expected/foo.md")
        val actual = normalize(actualFile.readText())

        if (System.getProperty("korro.regenerate.expected") == "true") {
            expectedFile.writeText(actual)
            return
        }

        val expected = normalize(expectedFile.readText())
        assertEquals(expected, actual, "Generated markdown does not match golden file")
    }

    private fun loadFixture(name: String, tempDir: Path): Path {
        val source = fixturesRoot().resolve(name).toFile()
        val target = tempDir.resolve(name).toFile()
        source.copyRecursively(target, overwrite = true)
        return target.toPath()
    }

    private fun fixturesRoot(): Path {
        val dir = System.getProperty("korro.fixtures.dir")
            ?: error("System property 'korro.fixtures.dir' is not set; check integration-tests/build.gradle.kts")
        return File(dir).toPath()
    }

    private fun normalize(s: String): String = s.replace("\r\n", "\n")
}
