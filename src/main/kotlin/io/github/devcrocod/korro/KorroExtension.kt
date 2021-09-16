package io.github.devcrocod.korro

import org.gradle.api.file.FileCollection
import java.io.File

open class KorroExtension {
    var docs: FileCollection? = null
    var samples: FileCollection? = null

    fun createContext(docs: Collection<File>, samples: Collection<File>) = KorroContext(
        logger = LoggerLog(),
        docs = docs,
        samples = samples
    )
}