package io.github.devcrocod.korro.analysis

import java.io.File

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

    override fun close() {
        session.close()
    }
}
