package io.github.devcrocod.korro

import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File

@CacheableTask
abstract class KorroCheckTask : AbstractKorroTask() {

    @get:Internal
    abstract val generatedDirectory: DirectoryProperty

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun check() {
        val outDir = generatedDirectory.get().asFile
        outDir.deleteRecursively()
        outDir.mkdirs()

        val docsToOutputs = buildDocsToOutputs(outDir)
        val queue = workerExecutor.classLoaderIsolation {
            it.classpath.from(korroRuntimeClasspath)
        }
        queue.submit(KorroWorkAction::class.java) { p ->
            p.docsToOutputs = docsToOutputs
            p.samples = samples.files
            p.sampleOutputs = samplesOutputs.files
            p.groups = buildSamplesGroups()
            p.rewriteAsserts = rewriteAsserts.get()
            p.ignoreMissing = ignoreMissing.get()
            p.korroPluginVersion = korroPluginVersion.get()
            p.taskName = name
        }
        queue.await()

        val base = docsBaseDir.get().asFile
        val mismatches = mutableListOf<CheckMismatch>()
        for ((source, generated) in docsToOutputs) {
            if (!generated.exists()) continue
            val actual = if (source.exists()) source.readText() else ""
            val expected = generated.readText()
            if (actual != expected) {
                val diff = firstDifferingLine(actual, expected)
                mismatches += CheckMismatch(
                    relativePath = source.toRelativeString(base),
                    lineNumber = diff.line,
                    sourceLine = diff.sourceLine,
                    generatedLine = diff.generatedLine,
                )
            }
        }

        val report = formatCheckReport(name, mismatches)
        val reportF = reportFile.get().asFile
        reportF.parentFile?.mkdirs()
        reportF.writeText(report)

        if (mismatches.isNotEmpty()) {
            throw GradleException(report)
        }
    }
}

internal data class CheckMismatch(
    val relativePath: String,
    val lineNumber: Int,
    val sourceLine: String?,
    val generatedLine: String?,
)

internal data class FirstDiff(val line: Int, val sourceLine: String?, val generatedLine: String?)

internal fun firstDifferingLine(actual: String, expected: String): FirstDiff {
    val actualLines = actual.split("\n")
    val expectedLines = expected.split("\n")
    val maxLines = maxOf(actualLines.size, expectedLines.size)
    for (i in 0 until maxLines) {
        val a = actualLines.getOrNull(i)
        val e = expectedLines.getOrNull(i)
        if (a != e) return FirstDiff(i + 1, a, e)
    }
    return FirstDiff(maxLines, null, null)
}

internal fun formatCheckReport(taskName: String, mismatches: List<CheckMismatch>): String {
    if (mismatches.isEmpty()) {
        return "$taskName: OK (all docs up to date)\n"
    }
    return buildString {
        append(taskName).append(": ").append(mismatches.size)
            .append(" file(s) out of date — run `./gradlew korro` to regenerate.\n")
        for (m in mismatches) {
            append("\n  ").append(m.relativePath).append(":").append(m.lineNumber).append('\n')
            append("    - ").append(m.sourceLine ?: "<absent>").append('\n')
            append("    + ").append(m.generatedLine ?: "<absent>").append('\n')
        }
    }
}
