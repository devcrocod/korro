package io.github.devcrocod.korro

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

@CacheableTask
abstract class KorroCheckTask : AbstractKorroTask() {

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun check() {
        logger.warn("korroCheck: not implemented (wired in a later phase). Task succeeded.")
        reportFile.get().asFile.apply {
            parentFile?.mkdirs()
            writeText("pending: implementation deferred\n")
        }
    }
}
