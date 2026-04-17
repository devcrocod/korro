package io.github.devcrocod.korro.analysis

import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

class FqnResolver(session: KorroAnalysisSession) {
    private val byFqn: Map<String, KtNamedFunction>
    private val byShortName: Map<String, List<KtNamedFunction>>
    private val ordered: List<Pair<String, KtNamedFunction>>

    init {
        val fqn = linkedMapOf<String, KtNamedFunction>()
        val shortName = linkedMapOf<String, MutableList<KtNamedFunction>>()
        val orderedList = mutableListOf<Pair<String, KtNamedFunction>>()
        val files = session.files.sortedBy { it.virtualFilePath }
        files.forEach { file -> collectFunctions(file, fqn, shortName, orderedList) }
        byFqn = fqn
        byShortName = shortName
        ordered = orderedList
    }

    fun resolve(candidateFqn: String): KtNamedFunction? {
        byFqn[candidateFqn]?.let { return it }
        if ('.' !in candidateFqn) {
            byShortName[candidateFqn]?.singleOrNull()?.let { return it }
        }
        return null
    }

    /**
     * Return every function whose FQN matches `prefix + pattern` for some prefix in [prefixes].
     * Deduplicates across prefixes (a function reached via several prefixes appears once),
     * preserving the first-encountered order: prefixes in the given order, and within each
     * prefix the declaration order from the source set.
     */
    fun matchGlob(pattern: String, prefixes: List<String>): List<KtNamedFunction> {
        val regexes = prefixes.map { compileGlob(it + pattern) }
        val seen = mutableSetOf<KtNamedFunction>()
        val result = mutableListOf<KtNamedFunction>()
        for (regex in regexes) {
            for ((fqn, fn) in ordered) {
                if (regex.matches(fqn) && seen.add(fn)) {
                    result += fn
                }
            }
        }
        return result
    }

    /** Top [limit] short names closest to [bareName] by Levenshtein distance, used for hints. */
    fun suggestShortNames(bareName: String, limit: Int = 3): List<String> {
        val target = bareName.substringAfterLast('.')
        if (target.isEmpty()) return emptyList()
        return byShortName.keys
            .map { it to levenshtein(it, target) }
            .filter { it.second <= (target.length / 2).coerceAtLeast(2) }
            .sortedWith(compareBy({ it.second }, { it.first }))
            .take(limit)
            .map { it.first }
    }

    private fun collectFunctions(
        file: KtFile,
        fqn: MutableMap<String, KtNamedFunction>,
        shortName: MutableMap<String, MutableList<KtNamedFunction>>,
        ordered: MutableList<Pair<String, KtNamedFunction>>,
    ) {
        fun visit(declarations: List<org.jetbrains.kotlin.psi.KtDeclaration>) {
            declarations.forEach { decl ->
                when (decl) {
                    is KtNamedFunction -> {
                        val fqnString = decl.fqName?.asString()
                        if (fqnString != null) {
                            fqn[fqnString] = decl
                            ordered += fqnString to decl
                        }
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

private fun compileGlob(pattern: String): Regex {
    val sb = StringBuilder("^")
    for (c in pattern) {
        when (c) {
            '*' -> sb.append(".*")
            '?' -> sb.append('.')
            '.', '\\', '+', '(', ')', '[', ']', '{', '}', '|', '^', '$' -> sb.append('\\').append(c)
            else -> sb.append(c)
        }
    }
    sb.append('$')
    return Regex(sb.toString())
}

private fun levenshtein(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    var prev = IntArray(b.length + 1) { it }
    var curr = IntArray(b.length + 1)
    for (i in 1..a.length) {
        curr[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            curr[j] = minOf(
                curr[j - 1] + 1,
                prev[j] + 1,
                prev[j - 1] + cost,
            )
        }
        val tmp = prev
        prev = curr
        curr = tmp
    }
    return prev[b.length]
}
