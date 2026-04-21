package io.github.devcrocod.korro.analysis

import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty

class FqnResolver(session: KorroAnalysisSession) {
    private val byFqn: Map<String, KtNamedDeclaration>
    private val byShortName: Map<String, List<KtNamedDeclaration>>
    private val ordered: List<Pair<String, KtNamedDeclaration>>

    init {
        val fqn = linkedMapOf<String, KtNamedDeclaration>()
        val shortName = linkedMapOf<String, MutableList<KtNamedDeclaration>>()
        val orderedList = mutableListOf<Pair<String, KtNamedDeclaration>>()
        val files = session.files.sortedBy { it.virtualFilePath }
        files.forEach { file -> collectDeclarations(file, fqn, shortName, orderedList) }
        byFqn = fqn
        byShortName = shortName
        ordered = orderedList
    }

    fun resolve(candidateFqn: String): KtNamedDeclaration? {
        byFqn[candidateFqn]?.let { return it }
        if ('.' !in candidateFqn) {
            byShortName[candidateFqn]?.singleOrNull()?.let { return it }
        }
        return null
    }

    /**
     * FQNs of every declaration sharing [bareName] when the short name is ambiguous,
     * or `null` when the name is unambiguous, qualified (contains a dot), or unknown.
     * Used by callers to distinguish "not found" from "multiple matches" in diagnostics.
     */
    fun ambiguous(bareName: String): List<String>? {
        if ('.' in bareName) return null
        val candidates = byShortName[bareName] ?: return null
        if (candidates.size < 2) return null
        return candidates.mapNotNull { it.fqName?.asString() }
    }

    /**
     * Return every declaration whose FQN matches `prefix + pattern` for some prefix in [prefixes].
     * Deduplicates across prefixes (a declaration reached via several prefixes appears once),
     * preserving the first-encountered order: prefixes in the given order, and within each
     * prefix the declaration order from the source set.
     */
    fun matchGlob(pattern: String, prefixes: List<String>): List<KtNamedDeclaration> {
        val regexes = prefixes.map { compileGlob(it + pattern) }
        val seen = mutableSetOf<KtNamedDeclaration>()
        val result = mutableListOf<KtNamedDeclaration>()
        for (regex in regexes) {
            for ((fqn, decl) in ordered) {
                if (regex.matches(fqn) && seen.add(decl)) {
                    result += decl
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
            .asSequence()
            .map { it to levenshtein(it, target) }
            .filter { it.second <= (target.length / 2).coerceAtLeast(2) }
            .sortedWith(compareBy({ it.second }, { it.first }))
            .take(limit)
            .map { it.first }
            .toList()
    }

    private fun collectDeclarations(
        file: KtFile,
        fqn: MutableMap<String, KtNamedDeclaration>,
        shortName: MutableMap<String, MutableList<KtNamedDeclaration>>,
        ordered: MutableList<Pair<String, KtNamedDeclaration>>,
    ) {
        fun index(decl: KtNamedDeclaration) {
            val fqnString = decl.fqName?.asString()
            if (fqnString != null) {
                fqn[fqnString] = decl
                ordered += fqnString to decl
            }
            decl.name?.let { shortName.getOrPut(it) { mutableListOf() }.add(decl) }
        }

        fun visit(declarations: List<KtDeclaration>) {
            declarations.forEach { decl ->
                when (decl) {
                    is KtEnumEntry -> {}
                    is KtNamedFunction -> index(decl)
                    is KtProperty -> index(decl)
                    is KtClassOrObject -> {
                        index(decl)
                        visit(decl.declarations)
                    }

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
