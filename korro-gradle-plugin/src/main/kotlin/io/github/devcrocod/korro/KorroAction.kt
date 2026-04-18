package io.github.devcrocod.korro

import io.github.devcrocod.korro.analysis.SamplesTransformer
import org.gradle.api.GradleException
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import java.io.File

interface KorroParameters : WorkParameters {
    var docsToOutputs: Map<File, File>
    var samples: Set<File>
    var sampleOutputs: Set<File>
    var groups: List<SamplesGroup>
    var rewriteAsserts: Boolean
    var ignoreMissing: Boolean
    var korroPluginVersion: String
    var taskName: String
}

abstract class KorroWorkAction : WorkAction<KorroParameters> {
    override fun execute() {
        val p = parameters
        SamplesTransformer(p.samples, p.rewriteAsserts).use { transformer ->
            val ctx = KorroContext(
                logger = LoggerLog(),
                docsToOutputs = p.docsToOutputs,
                sampleOutputs = p.sampleOutputs,
                groups = p.groups,
                ignoreMissing = p.ignoreMissing,
                samplesTransformer = transformer,
            )
            ctx.process()

            val errors = ctx.diagnostics.filter { it.severity == Severity.ERROR }
            if (errors.isNotEmpty()) {
                throw GradleException(formatDiagnosticTable(p.taskName, errors))
            }
        }
    }
}

internal fun formatDiagnosticTable(taskName: String, errors: List<Diagnostic>): String {
    val header = "$taskName: ${errors.size} error(s) found"
    val sevWidth = errors.maxOf { it.severity.name.length }
    val locWidth = errors.maxOf { "${it.file}:${it.line}".length }
    val rows = errors.joinToString("\n") { d ->
        val loc = "${d.file}:${d.line}".padEnd(locWidth)
        val sev = d.severity.name.padEnd(sevWidth)
        val hint = d.hint?.let { " ($it)" } ?: ""
        "  $sev  $loc  ${d.message}$hint"
    }
    return "$header\n$rows"
}
