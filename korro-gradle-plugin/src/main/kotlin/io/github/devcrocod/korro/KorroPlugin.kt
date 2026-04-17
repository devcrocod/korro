package io.github.devcrocod.korro

import org.gradle.api.Plugin
import org.gradle.api.Project
import java.util.Properties

class KorroPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = with(project) {
        val ext = extensions.create("korro", KorroExtension::class.java)

        val korroPluginVersion = readKorroPluginVersion()

        val runtime = configurations.create("korroAnalysisRuntime") {
            it.isCanBeConsumed = false
            it.isCanBeResolved = true
        }
        dependencies.add(runtime.name, "io.github.devcrocod:korro-analysis:$korroPluginVersion")

        afterEvaluate {
            val korroTask = tasks.register("korro", KorroTask::class.java) { t ->
                t.description = "Generates markdown docs with sample snippets into build/korro/docs."
                t.group = "documentation"
                t.docs.from(ext.docs.from)
                t.docsBaseDir.set(ext.docs.baseDir)
                t.samples.from(ext.samples.from)
                t.samplesOutputs.from(ext.samples.outputs)
                t.rewriteAsserts.set(ext.behavior.rewriteAsserts)
                t.ignoreMissing.set(ext.behavior.ignoreMissing)
                t.groupSamples.beforeGroup.set(ext.groupSamples.beforeGroup)
                t.groupSamples.afterGroup.set(ext.groupSamples.afterGroup)
                t.groupSamples.beforeSample.set(ext.groupSamples.beforeSample)
                t.groupSamples.afterSample.set(ext.groupSamples.afterSample)
                t.groupSamples.patterns.set(ext.groupSamples.patterns)
                t.korroRuntimeClasspath.from(runtime)
                t.outputDirectory.set(layout.buildDirectory.dir("korro/docs"))
                t.korroPluginVersion.set(korroPluginVersion)
            }

            tasks.register("korroApply", KorroApplyTask::class.java) { t ->
                t.description = "Copies generated docs onto the source tree (mutates source)."
                t.group = "documentation"
                t.dependsOn(korroTask)
                t.from(korroTask.flatMap { it.outputDirectory })
                t.into(ext.docs.baseDir)
            }

            tasks.register("korroCheck", KorroCheckTask::class.java) { t ->
                t.description = "Verifies generated docs match source tree (stub; full implementation pending)."
                t.group = "verification"
                t.docs.from(ext.docs.from)
                t.docsBaseDir.set(ext.docs.baseDir)
                t.samples.from(ext.samples.from)
                t.samplesOutputs.from(ext.samples.outputs)
                t.rewriteAsserts.set(ext.behavior.rewriteAsserts)
                t.ignoreMissing.set(ext.behavior.ignoreMissing)
                t.groupSamples.beforeGroup.set(ext.groupSamples.beforeGroup)
                t.groupSamples.afterGroup.set(ext.groupSamples.afterGroup)
                t.groupSamples.beforeSample.set(ext.groupSamples.beforeSample)
                t.groupSamples.afterSample.set(ext.groupSamples.afterSample)
                t.groupSamples.patterns.set(ext.groupSamples.patterns)
                t.korroRuntimeClasspath.from(runtime)
                t.korroPluginVersion.set(korroPluginVersion)
                t.reportFile.set(layout.buildDirectory.file("korro/check.report"))
            }
        }
    }

    private fun readKorroPluginVersion(): String {
        val resource = KorroPlugin::class.java.classLoader
            .getResourceAsStream("META-INF/korro-gradle-plugin.properties")
            ?: error("Cannot locate META-INF/korro-gradle-plugin.properties on the plugin classpath.")
        val props = resource.use { Properties().apply { load(it) } }
        return props.getProperty("version")
            ?: error("Property 'version' missing from META-INF/korro-gradle-plugin.properties.")
    }
}
