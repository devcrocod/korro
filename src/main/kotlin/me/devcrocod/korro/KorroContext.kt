package me.devcrocod.korro

import java.io.File
import java.util.*

class KorroContext(
    val logger: KorroLog,
    //
    val outputDirectory: File,
    docs: Collection<File>,
    samples: Collection<File>
) {
    // state
    val docSet = HashSet(docs)
    val fileQueue = ArrayDeque(docs)
    val sampleSet = HashSet(samples)
}

fun KorroContext.process(): Boolean {
    while (!fileQueue.isEmpty()) {
        if (!korro(fileQueue.removeFirst())) return false
    }
    return true
}