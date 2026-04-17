package io.github.devcrocod.korro

import org.gradle.api.Plugin
import org.gradle.api.Project

class KorroPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = with(project) {
        extensions.create("korro", KorroExtension::class.java)
        project.afterEvaluate {
            tasks.register("korro", KorroTask::class.java) {
                it.description = "Runs Korro Tool"
                it.group = "documentation"
            }

            tasks.register("korroClean", KorroCleanTask::class.java) {
                it.description = "Deletes inserted samples"
                it.group = "documentation"
            }
        }
    }
}
