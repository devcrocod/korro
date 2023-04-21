package io.github.devcrocod.korro

import org.gradle.api.DefaultTask
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.*
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject

// TODO get from central version
const val dokkaVersion = "1.8.10"
private interface KorroTasksCommon {

    @get:Internal
    val projectReference: Project

    @get:Internal
    val nameReference: String

    @get:Classpath
    val classpath: Configuration
        get() = projectReference.configurations.maybeCreate("korroRuntime") {
            isCanBeConsumed = true
            listOf(
                "org.jetbrains.dokka:dokka-analysis",
                "org.jetbrains.dokka:dokka-base",
                "org.jetbrains.dokka:dokka-core",
            ).forEach {
                dependencies += projectReference.dependencies.create("$it:$dokkaVersion")
            }
        }

    @get:Inject
    val workerExecutor: WorkerExecutor

    @get:Internal
    val ext: KorroExtension

    var docs: FileCollection

    var samples: FileCollection

    @get:Internal
    val groups: List<SamplesGroup>

    fun execute(clazz: Class<out WorkAction<KorroParameters>>) {
        val workQueue = workerExecutor.classLoaderIsolation {
            it.classpath.setFrom(classpath.resolve())
        }
        workQueue.submit(clazz) {
            it.docs = docs.files
            it.samples = samples.files
            it.groups = groups
            it.name = nameReference
        }
    }
}

abstract class KorroTask : DefaultTask(), KorroTasksCommon {

    final override val ext: KorroExtension = project.extensions.getByType(KorroExtension::class.java)

    @InputFiles
    override var docs: FileCollection = ext.docs ?: project.fileTree(project.rootDir) {
        it.include("**/*.md")
    }

    @InputFiles
    override var samples: FileCollection = ext.samples ?: project.fileTree(project.rootDir) {
        it.include("**/*.kt")
    }

    @get:Internal
    override val groups: List<SamplesGroup> = ext.groups

    @get:Internal
    override val projectReference: Project
        get() = project

    @get:Internal
    override val nameReference: String
        get() = name

    @TaskAction
    fun korro() {
        execute(KorroAction::class.java)
    }
}

abstract class KorroCleanTask : Delete(), KorroTasksCommon {
    final override val ext: KorroExtension = project.extensions.getByType(KorroExtension::class.java)

    @InputFiles
    override var docs: FileCollection = ext.docs ?: project.fileTree(project.rootDir) {
        it.include("**/*.md")
    }

    @InputFiles
    override var samples: FileCollection = ext.samples ?: project.fileTree(project.rootDir) {
        it.include("**/*.kt")
    }

    @get:Internal
    override val groups: List<SamplesGroup> = ext.groups

    @get:Internal
    override val projectReference: Project
        get() = project

    @get:Internal
    override val nameReference: String
        get() = name

    @TaskAction
    fun korroClean() {
        execute(KorroCleanAction::class.java)
    }
}

private fun <T : Any> NamedDomainObjectContainer<T>.maybeCreate(name: String, configuration: T.() -> Unit): T =
    findByName(name) ?: create(name, configuration)