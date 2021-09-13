package com.github.devcrocod.korro

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction

abstract class KorroTask : DefaultTask() {
    private val ext: KorroExtension = project.extensions.getByType(KorroExtension::class.java)

    @InputFiles
    var docs: FileCollection = ext.docs ?: project.fileTree(project.rootDir) {
        it.include("**/*.md")
    }

    @InputFiles
    var samples: FileCollection = ext.samples ?: project.fileTree(project.rootDir) {
        it.include("**/*.kt")
    }

    @TaskAction
    fun korro() {
        val ctx = ext.createContext(docs.files, samples.files)

        //TODO - check missing files!

        //TODO - process!!! error
        if (!ctx.process()) {
            val extra = if (ctx.logger.nOutdated > 0)
                "\nRun 'korro' task to write ${ctx.logger.nOutdated} missing/outdated files."
            else
                ""
            throw GradleException("$name task failed, see log for details (use '--info' for detailed log).$extra")
        }
    }
}