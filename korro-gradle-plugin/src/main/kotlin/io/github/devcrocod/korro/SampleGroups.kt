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

enum class Severity { ERROR, WARN }

data class Diagnostic(
    val severity: Severity,
    val file: String,
    val line: Int,
    val message: String,
    val hint: String? = null,
) : Serializable
