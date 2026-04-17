package io.github.devcrocod.korro

import java.io.File

class KorroContext(
    val logger: KorroLog,
    docsToOutputs: Map<File, File>,
    samples: Collection<File>,
    sampleOutputs: Collection<File>,
    val groups: List<SamplesGroup>,
    val rewriteAsserts: Boolean,
    val ignoreMissing: Boolean,
) {
    val fileQueue: ArrayDeque<Pair<File, File>> = ArrayDeque(
        docsToOutputs.entries.map { (input, output) -> input to output }
    )
    val sampleSet: Set<File> = samples.toHashSet()
    val outputsMap: Map<String, File> = sampleOutputs.associateBy { it.name }
}

fun KorroContext.process(): Boolean {
    while (!fileQueue.isEmpty()) {
        val (input, output) = fileQueue.removeFirst()
        if (!korro(input, output)) return false
    }
    return true
}
