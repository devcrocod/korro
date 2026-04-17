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
            if (!ctx.process()) {
                throw GradleException(
                    "${p.taskName} failed, see log for details (use --info for detailed log)."
                )
            }
        }
    }
}
