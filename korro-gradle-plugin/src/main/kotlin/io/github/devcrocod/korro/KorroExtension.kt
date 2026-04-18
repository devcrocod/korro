package io.github.devcrocod.korro

import org.gradle.api.Action
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested

abstract class KorroExtension {
    @get:Nested
    abstract val docs: DocsSpec

    @get:Nested
    abstract val samples: SamplesSpec

    @get:Nested
    abstract val behavior: BehaviorSpec

    @get:Nested
    abstract val groupSamples: GroupSamplesApi

    fun docs(action: Action<in DocsSpec>) {
        action.execute(docs)
    }

    fun samples(action: Action<in SamplesSpec>) {
        action.execute(samples)
    }

    fun behavior(action: Action<in BehaviorSpec>) {
        action.execute(behavior)
    }

    fun groupSamples(action: Action<in GroupSamplesApi>) {
        action.execute(groupSamples)
    }
}

abstract class DocsSpec {
    abstract val from: ConfigurableFileCollection
    abstract val baseDir: DirectoryProperty

    fun from(vararg paths: Any) {
        from.from(*paths)
    }
}

abstract class SamplesSpec {
    abstract val from: ConfigurableFileCollection
    abstract val outputs: ConfigurableFileCollection

    fun from(vararg paths: Any) {
        from.from(*paths)
    }
}

abstract class BehaviorSpec {
    @get:Input
    abstract val rewriteAsserts: Property<Boolean>

    @get:Input
    abstract val ignoreMissing: Property<Boolean>

    init {
        rewriteAsserts.convention(false)
        ignoreMissing.convention(false)
    }
}

abstract class GroupSamplesApi {
    @get:Input
    abstract val beforeGroup: Property<String>

    @get:Input
    abstract val afterGroup: Property<String>

    @get:Input
    abstract val beforeSample: Property<String>

    @get:Input
    abstract val afterSample: Property<String>

    @get:Input
    abstract val patterns: ListProperty<FunctionPattern>

    init {
        beforeGroup.convention("")
        afterGroup.convention("")
        beforeSample.convention("")
        afterSample.convention("")
        patterns.convention(emptyList())
    }

    fun funSuffix(suffix: String, action: Action<in FunSuffixApi>) {
        val api = FunSuffixApi()
        action.execute(api)
        patterns.add(FunctionPattern(suffix, api.substitutions.toMap()))
    }
}

class FunSuffixApi {
    internal val substitutions = mutableMapOf<String, String>()

    fun replaceText(placeholder: String, text: String) {
        substitutions[placeholder] = text
    }
}
