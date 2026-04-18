package io.github.devcrocod.korro.analysis

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.analysis.api.standalone.StandaloneAnalysisAPISession
import org.jetbrains.kotlin.analysis.api.standalone.buildStandaloneAnalysisAPISession
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtSdkModule
import org.jetbrains.kotlin.analysis.project.structure.builder.buildKtSourceModule
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import java.nio.file.Paths

class KorroAnalysisSession(samples: Set<File>) : AutoCloseable {
    private val disposable = Disposer.newDisposable("korro.analysis")
    val session: StandaloneAnalysisAPISession
    val contextModule: KaSourceModule
    val project: Project
    val files: List<KtFile>

    init {
        lateinit var sourceModule: KaSourceModule
        session = buildStandaloneAnalysisAPISession(projectDisposable = disposable) {
            buildKtModuleProvider {
                platform = JvmPlatforms.defaultJvmPlatform
                val jdk = addModule(
                    buildKtSdkModule {
                        platform = JvmPlatforms.defaultJvmPlatform
                        addBinaryRootsFromJdkHome(Paths.get(System.getProperty("java.home")), isJre = true)
                        libraryName = "jdk"
                    }
                )
                sourceModule = addModule(
                    buildKtSourceModule {
                        platform = JvmPlatforms.defaultJvmPlatform
                        languageVersionSettings = LanguageVersionSettingsImpl(
                            LanguageVersion.LATEST_STABLE, ApiVersion.LATEST_STABLE
                        )
                        addSourceRoots(samples.map { it.toPath() })
                        moduleName = "korro.samples"
                        addRegularDependency(jdk)
                    }
                )
            }
        }
        contextModule = sourceModule
        project = session.project
        files = session.modulesWithFiles[contextModule]
            .orEmpty()
            .filterIsInstance<KtFile>()
    }

    override fun close() {
        Disposer.dispose(disposable)
    }
}
