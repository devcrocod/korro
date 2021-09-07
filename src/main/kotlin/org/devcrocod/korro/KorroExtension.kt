package org.devcrocod.korro

import org.gradle.api.file.FileCollection
import java.io.File

open class KorroExtension {
    var outputDirectory: File? = null
    var docs: FileCollection? = null
    var samples: FileCollection? = null
    var sampleDir: File? = null

    fun createContext(outputDirectory: File, docs: Collection<File>, samples: Collection<File>) = KorroContext(
        logger = LoggerLog(),
        outputDirectory = outputDirectory,
        docs = docs,
        samples = samples
    )
}