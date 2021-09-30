package io.github.devcrocod.korro

import java.io.File
import java.util.*

class KorroContext(
    val logger: KorroLog,
    docs: Collection<File>,
    samples: Collection<File>,
    val groups: List<SamplesGroup>
) {
    // state
    val fileQueue = ArrayDeque(docs)
    val sampleSet = HashSet(samples)
}

fun KorroContext.process(): Boolean {
    while (!fileQueue.isEmpty()) {
        if (!korro(fileQueue.removeFirst())) return false
    }
    return true
}

fun KorroContext.processClean(): Boolean {
    while (!fileQueue.isEmpty()) {
        if (!korroClean(fileQueue.removeFirst())) return false
    }
    return true
}