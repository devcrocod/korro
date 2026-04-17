package io.github.devcrocod.korro

import org.gradle.api.Action
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import java.io.File

class FunSuffixApi {
    internal val substitutions = mutableMapOf<String, String>()

    fun replaceText(placeholder: String, text: String) {
        substitutions.put(placeholder, text)
    }
}

abstract class GroupSamplesApi {

    val patterns = mutableListOf<FunctionPattern>()

    fun funSuffix(suffix: String, action: Action<in FunSuffixApi>) {
        val api = FunSuffixApi()
        action.execute(api)
        patterns.add(FunctionPattern(suffix, api.substitutions))
    }

    @get:Input
    abstract val beforeGroup: Property<String>

    @get:Input
    abstract val afterGroup: Property<String>

    @get:Input
    abstract val beforeSample: Property<String>

    @get:Input
    abstract val afterSample: Property<String>

    init  {
        afterGroup.set("")
        beforeGroup.set("")
        afterSample.set("")
        beforeSample.set("")
    }
}

abstract class KorroExtension {
    var docs: FileCollection? = null
    var samples: FileCollection? = null
    var outputs: FileCollection? = null

    internal val groups = mutableListOf<SamplesGroup>()

    @Nested
    abstract fun getGroupSamples(): GroupSamplesApi

    fun groupSamples(action: Action<in GroupSamplesApi>) {
        val api = getGroupSamples()
        action.execute(api)
        val group = SamplesGroup(api.beforeGroup.get(), api.afterGroup.get(), api.beforeSample.get(), api.afterSample.get(), api.patterns)
        groups.add(group)
    }

    fun createContext(docs: Collection<File>, samples: Collection<File>, outputs: Collection<File>) = KorroContext(
        logger = LoggerLog(),
        docs = docs,
        samples = samples,
        outputs = outputs,
        groups = groups
    )
}