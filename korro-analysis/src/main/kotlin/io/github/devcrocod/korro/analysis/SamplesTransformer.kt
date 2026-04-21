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

    operator fun invoke(name: String): String? {
        val decl = resolver.resolve(name) ?: return null
        return extractor.extract(decl)
    }

    fun matchGlob(globPattern: String, imports: List<String>): List<RenderedSample> {
        val matches = resolver.matchGlob(globPattern, imports)
        return matches.map { decl ->
            val fqn = decl.fqName?.asString() ?: decl.name ?: "<anonymous>"
            RenderedSample(fqn, extractor.extract(decl))
        }
    }

    fun suggestions(bareName: String): List<String> = resolver.suggestShortNames(bareName)

    fun ambiguous(bareName: String): List<String>? = resolver.ambiguous(bareName)

    override fun close() {
        session.close()
    }
}
