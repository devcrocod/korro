package io.github.devcrocod.korro

import java.io.Serializable

data class FunctionPattern(val nameSuffix: String, val substitutions: Map<String, String>): Serializable {
    fun processSubstitutions(text: String) = substitutions.entries.fold(text) { acc, entry ->
        acc.replace(entry.key, entry.value)
    }
}

data class SamplesGroup(
    val beforeGroup: String?,
    val afterGroup: String?,
    val beforeSample: String?,
    val afterSample: String?,
    val patterns: List<FunctionPattern>
) : Serializable