package io.github.devcrocod.korro

import org.gradle.api.GradleException
import org.gradle.api.tasks.Nested
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import java.io.File

interface KorroParameters : WorkParameters {
    var docs: Set<File>
    var samples: Set<File>
    var name: String
}

abstract class KorroAction : WorkAction<KorroParameters> {

    @get:Nested
    abstract val ext: KorroExtension

    override fun execute() {
        val ctx = ext.createContext(parameters.docs, parameters.samples)

        //TODO - check missing files!

        //TODO - process!!! error
        if (!ctx.process()) {
            val extra = if (ctx.logger.nOutdated > 0)
                "\nRun 'korro' task to write ${ctx.logger.nOutdated} missing/outdated files."
            else
                ""
            throw GradleException("${parameters.name} task failed, see log for details (use '--info' for detailed log).$extra")
        }
    }
}

abstract class KorroCleanAction : WorkAction<KorroParameters> {

    @get:Nested
    abstract val ext: KorroExtension

    override fun execute() {
        val ctx = ext.createContext(parameters.docs, parameters.samples)

        if (!ctx.processClean()) {
            val extra = if (ctx.logger.nOutdated > 0)
                "\nRun 'korro' task to write ${ctx.logger.nOutdated} missing/outdated files."
            else
                ""
            throw GradleException("${parameters.name} task failed, see log for details (use '--info' for detailed log).$extra")
        }
    }
}