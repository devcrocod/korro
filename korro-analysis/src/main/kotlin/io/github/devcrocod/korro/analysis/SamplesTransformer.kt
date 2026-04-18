package io.github.devcrocod.korro.analysis

import java.io.File

data class RenderedSample(val fqn: String, val snippet: String)

class SamplesTransformer(
    samples: Set<File>,
    rewriteAsserts: Boolean,
) : AutoCloseable {
    private val session = KorroAnalysisSession(samples)
    private val resolver = FqnResolver(session)
    private val extractor = SampleExtractor(rewriteAsserts)

    operator fun invoke(functionName: String): String? {
        val fn = resolver.resolve(functionName) ?: return null
        return extractor.extract(fn)
    }

    fun matchGlob(globPattern: String, imports: List<String>): List<RenderedSample> {
        val matches = resolver.matchGlob(globPattern, imports)
        return matches.map { fn ->
            val fqn = fn.fqName?.asString() ?: fn.name ?: "<anonymous>"
            RenderedSample(fqn, extractor.extract(fn))
        }
    }

    fun suggestions(bareName: String): List<String> = resolver.suggestShortNames(bareName)

    override fun close() {
        session.close()
    }
}
