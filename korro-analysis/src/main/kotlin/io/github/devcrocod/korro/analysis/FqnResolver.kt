package io.github.devcrocod.korro.analysis

import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

class FqnResolver(session: KorroAnalysisSession) {
    private val byFqn: Map<String, KtNamedFunction>
    private val byShortName: Map<String, List<KtNamedFunction>>

    init {
        val fqn = mutableMapOf<String, KtNamedFunction>()
        val shortName = mutableMapOf<String, MutableList<KtNamedFunction>>()
        session.files.forEach { file -> collectFunctions(file, fqn, shortName) }
        byFqn = fqn
        byShortName = shortName
    }

    fun resolve(candidateFqn: String): KtNamedFunction? {
        byFqn[candidateFqn]?.let { return it }
        if ('.' !in candidateFqn) {
            byShortName[candidateFqn]?.singleOrNull()?.let { return it }
        }
        return null
    }

    private fun collectFunctions(
        file: KtFile,
        fqn: MutableMap<String, KtNamedFunction>,
        shortName: MutableMap<String, MutableList<KtNamedFunction>>,
    ) {
        fun visit(declarations: List<org.jetbrains.kotlin.psi.KtDeclaration>) {
            declarations.forEach { decl ->
                when (decl) {
                    is KtNamedFunction -> {
                        decl.fqName?.asString()?.let { fqn[it] = decl }
                        decl.name?.let { shortName.getOrPut(it) { mutableListOf() }.add(decl) }
                    }
                    is KtClassOrObject -> visit(decl.declarations)
                    else -> {}
                }
            }
        }
        visit(file.declarations)
    }
}
