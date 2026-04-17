package io.github.devcrocod.korro

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject

@DisableCachingByDefault(because = "Abstract base; concrete subclasses opt in with @CacheableTask.")
abstract class AbstractKorroTask : DefaultTask() {

    @get:Inject
    abstract val workerExecutor: WorkerExecutor

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val docs: ConfigurableFileCollection

    @get:Internal
    abstract val docsBaseDir: DirectoryProperty

    @get:Input
    val docsRelativePaths: Provider<List<String>>
        get() = docs.elements.map { files ->
            val base = docsBaseDir.get().asFile
            files.map { it.asFile.toRelativeString(base) }.sorted()
        }

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val samples: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val samplesOutputs: ConfigurableFileCollection

    @get:Input
    abstract val rewriteAsserts: Property<Boolean>

    @get:Input
    abstract val ignoreMissing: Property<Boolean>

    @get:Input
    abstract val korroPluginVersion: Property<String>

    @get:Nested
    abstract val groupSamples: GroupSamplesApi

    @get:Classpath
    abstract val korroRuntimeClasspath: ConfigurableFileCollection

    protected fun buildSamplesGroups(): List<SamplesGroup> = listOf(
        SamplesGroup(
            beforeGroup = groupSamples.beforeGroup.get(),
            afterGroup = groupSamples.afterGroup.get(),
            beforeSample = groupSamples.beforeSample.get(),
            afterSample = groupSamples.afterSample.get(),
            patterns = groupSamples.patterns.get(),
        )
    )

    protected fun buildDocsToOutputs(outDir: File): Map<File, File> {
        val base = docsBaseDir.get().asFile
        return docs.files.associateWith { input ->
            val rel = input.toRelativeString(base)
            check(!rel.startsWith("..")) {
                "$input is outside docs.baseDir=$base. Set docs.baseDir to a directory that contains all docs."
            }
            File(outDir, rel)
        }
    }
}

@CacheableTask
abstract class KorroTask : AbstractKorroTask() {

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun korro() {
        val outDir = outputDirectory.get().asFile
        val docsToOutputs = buildDocsToOutputs(outDir)
        val queue = workerExecutor.classLoaderIsolation {
            it.classpath.from(korroRuntimeClasspath)
        }
        queue.submit(KorroWorkAction::class.java) { p ->
            p.docsToOutputs = docsToOutputs
            p.samples = samples.files
            p.sampleOutputs = samplesOutputs.files
            p.groups = buildSamplesGroups()
            p.rewriteAsserts = rewriteAsserts.get()
            p.ignoreMissing = ignoreMissing.get()
            p.korroPluginVersion = korroPluginVersion.get()
            p.taskName = name
        }
    }
}
