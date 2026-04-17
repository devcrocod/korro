package io.github.devcrocod.korro

import io.github.devcrocod.korro.analysis.SamplesTransformer
import java.io.File

class KorroContext(
    val logger: KorroLog,
    docsToOutputs: Map<File, File>,
    sampleOutputs: Collection<File>,
    val groups: List<SamplesGroup>,
    val ignoreMissing: Boolean,
    val samplesTransformer: SamplesTransformer,
) {
    val fileQueue: ArrayDeque<Pair<File, File>> = ArrayDeque(
        docsToOutputs.entries.map { (input, output) -> input to output }
    )
    val outputsMap: Map<String, File> = sampleOutputs.associateBy { it.name }
    val diagnostics: MutableList<Diagnostic> = mutableListOf()
}

fun KorroContext.process() {
    while (!fileQueue.isEmpty()) {
        val (input, output) = fileQueue.removeFirst()
        korro(input, output)
    }
}
