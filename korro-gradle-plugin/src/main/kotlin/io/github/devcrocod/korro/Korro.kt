package io.github.devcrocod.korro

import java.io.File

const val IMPORT_DIRECTIVE = "IMPORT"
const val FUN_DIRECTIVE = "FUN"
const val FUNS_DIRECTIVE = "FUNS"
const val END_DIRECTIVE = "END"

private val KORRO_DIRECTIVE_NAMES = setOf(
    IMPORT_DIRECTIVE,
    FUN_DIRECTIVE,
    FUNS_DIRECTIVE,
    END_DIRECTIVE,
)

private val DIRECTIVE_INNER_REGEX = Regex("([_a-zA-Z.]+)(?:\\s+(.*))?")

/**
 * Marker syntax used to wrap a Korro directive on a single line.
 *
 * [HTML] matches Markdown's HTML-comment form `<!---NAME VALUE-->`.
 * [MDX] matches an MDX JSX-expression comment form `{/*---NAME VALUE--*/}`;
 * plain `<!-- -->` is rejected by MDX v2 parsers (e.g. Mintlify), so MDX docs
 * must use this variant. Both forms share the same 3-dashes-to-open, 2-dashes-to-close
 * asymmetry so the directive signature is visually consistent across file types.
 */
enum class DirectiveSyntax(val start: String, val end: String) {
    HTML("<!---", "-->"),
    MDX("{/*---", "--*/}"),
    ;

    val endSample: String get() = "$start$END_DIRECTIVE$end"

    companion object {
        fun forFile(file: File): DirectiveSyntax = when (file.extension.lowercase()) {
            "mdx" -> MDX
            else -> HTML
        }
    }
}

fun KorroContext.korro(inputFile: File, outputFile: File): Boolean {
    logger.info("*** Reading $inputFile")
    val syntax = DirectiveSyntax.forFile(inputFile)
    val endSample = syntax.endSample
    val samplesTransformer = this.samplesTransformer
    val lines = ArrayList<String>()
    // No empty-prefix seed — see renderFunBody for why.
    val imports = mutableListOf<String>()

    fun reportMissing(line: Int, message: String, hint: String? = null) {
        val sev = if (ignoreMissing) Severity.WARN else Severity.ERROR
        diagnostics += Diagnostic(sev, inputFile.path, line, message, hint)
        val suffix = hint?.let { " ($it)" } ?: ""
        if (sev == Severity.WARN) logger.warn("$inputFile:$line: $message$suffix")
        else logger.info("$inputFile:$line: $message$suffix")
    }

    fun renderFunBody(funName: String): List<String>? {
        // A bare FUN under IMPORT(s) must be tried *only* against IMPORT-qualified candidates.
        // Adding an implicit empty prefix would let the resolver's byShortName uniqueness fallback
        // pick a same-named function from an unrelated package, silently ignoring IMPORT.
        val functionNames = when {
            '.' in funName || imports.isEmpty() -> listOf(funName)
            else -> imports.map { it + funName }
        }
        return functionNames.firstNotNullOfOrNull { name ->
            val direct = samplesTransformer(name)
            var text: String? = direct?.snippet
            var resolvedFqn: String? = direct?.fqn
            if (text == null) {
                val grouped = groups.firstNotNullOfOrNull { group ->
                    var baseFqn: String? = null
                    val pieces = group.patterns.mapNotNull { pattern ->
                        samplesTransformer(name + pattern.nameSuffix)?.let { rs ->
                            if (baseFqn == null) baseFqn = rs.fqn.removeSuffix(pattern.nameSuffix)
                            group.beforeSample?.let { pattern.processSubstitutions(it) } + rs.snippet +
                                group.afterSample?.let { pattern.processSubstitutions(it) }
                        }
                    }
                    pieces.takeIf { it.isNotEmpty() }?.joinToString(
                        separator = "\n",
                        prefix = group.beforeGroup ?: "",
                        postfix = group.afterGroup ?: ""
                    )?.let { it to baseFqn }
                }
                text = grouped?.first
                resolvedFqn = grouped?.second
            }
            if (text != null && resolvedFqn != null) {
                outputsMap[resolvedFqn]?.let { text += it.readText() }
            }
            text?.split("\n")?.plus(endSample)
        }
    }

    fun processFun(funName: String, oldSampleLines: List<String>, directiveLine: Int) {
        val newSamplesLines = renderFunBody(funName)
        if (newSamplesLines == null) {
            val shortNameMatches = samplesTransformer.matchesByShortName(funName)
            val (message, hint) = when {
                imports.isEmpty() && shortNameMatches.size >= 2 ->
                    "Ambiguous FUN '$funName'" to
                        "candidates: ${shortNameMatches.joinToString(", ")}; qualify with IMPORT"
                imports.isNotEmpty() && shortNameMatches.isNotEmpty() ->
                    "Cannot resolve FUN '$funName' under current IMPORT(s)" to
                        "found at: ${shortNameMatches.joinToString(", ")} — add an IMPORT or qualify the FUN"
                else -> {
                    val suggestions = samplesTransformer.suggestions(funName).takeIf { it.isNotEmpty() }
                        ?.joinToString(prefix = "did you mean: ", separator = ", ")
                    "Cannot resolve FUN '$funName'" to suggestions
                }
            }
            reportMissing(directiveLine, message, hint)
            lines.addAll(oldSampleLines)
            return
        }
        if (oldSampleLines != newSamplesLines) {
            logger.info("*** Add $funName sample")
            lines.addAll(newSamplesLines)
        } else {
            lines.addAll(oldSampleLines)
        }
    }

    fun renderFunsBody(glob: String): List<String>? {
        // Same scoping rule as renderFunBody — IMPORT, when present, is authoritative.
        val prefixes = when {
            '.' in glob || imports.isEmpty() -> listOf("")
            else -> imports
        }
        val matches = samplesTransformer.matchGlob(glob, prefixes)
        if (matches.isEmpty()) return null

        val trimmed = matches.map { it.copy(snippet = it.snippet.trim { ch -> ch == '\n' }) }

        val group = groups.firstOrNull()
        val hasWrapping = group != null && (
            !group.beforeGroup.isNullOrEmpty() || !group.afterGroup.isNullOrEmpty() ||
                !group.beforeSample.isNullOrEmpty() || !group.afterSample.isNullOrEmpty()
            )

        val body = when {
            hasWrapping && trimmed.size >= 2 -> trimmed.joinToString(
                separator = "\n",
                prefix = group.beforeGroup.orEmpty(),
                postfix = group.afterGroup.orEmpty(),
            ) { rs -> group.beforeSample.orEmpty() + rs.snippet + group.afterSample.orEmpty() }

            hasWrapping -> {
                val rs = trimmed.single()
                group.beforeSample.orEmpty() + rs.snippet + group.afterSample.orEmpty()
            }

            else -> trimmed.joinToString(separator = "\n\n") { it.snippet }
        }
        return ("\n" + body + "\n").split("\n") + endSample
    }

    fun processFuns(glob: String, oldSampleLines: List<String>, directiveLine: Int) {
        val newSamplesLines = renderFunsBody(glob)
        if (newSamplesLines == null) {
            reportMissing(directiveLine, "FUNS '$glob' matched no declarations")
            lines.addAll(oldSampleLines)
            return
        }
        if (oldSampleLines != newSamplesLines) {
            logger.info("*** Expand FUNS $glob (${newSamplesLines.size} lines)")
            lines.addAll(newSamplesLines)
        } else {
            lines.addAll(oldSampleLines)
        }
    }

    data class BlockCollect(
        val old: List<String>,
        val terminator: Directive?,
        val terminatorLine: String?,
        val unclosed: Boolean,
    )

    fun collectBlock(reader: java.io.BufferedReader, startLineNo: Int): Pair<BlockCollect, Int> {
        val old = ArrayList<String>()
        var n = startLineNo
        while (true) {
            val sampleLine = reader.readLine() ?: return BlockCollect(old, null, null, unclosed = true) to n
            n++
            val nextDirective = parseDirective(sampleLine, syntax)
            when (nextDirective?.name) {
                END_DIRECTIVE -> {
                    old.add(sampleLine)
                    return BlockCollect(old, nextDirective, sampleLine, unclosed = false) to n
                }

                FUN_DIRECTIVE, FUNS_DIRECTIVE -> {
                    return BlockCollect(old, nextDirective, sampleLine, unclosed = true) to n
                }

                else -> old.add(sampleLine)
            }
        }
    }

    inputFile.bufferedReader().use { reader ->
        var lineNo = 0
        var pendingDirective: Directive? = null
        var pendingDirectiveLine = 0
        var pendingLineText: String? = null

        while (true) {
            val line: String
            val directive: Directive?
            val directiveLineNo: Int

            if (pendingDirective != null) {
                directive = pendingDirective
                directiveLineNo = pendingDirectiveLine
                line = pendingLineText!!
                pendingDirective = null
                pendingLineText = null
            } else {
                val raw = reader.readLine() ?: break
                lineNo++
                line = raw
                directive = parseDirective(raw, syntax)
                directiveLineNo = lineNo
            }
            lines.add(line)

            when (directive?.name) {
                null, END_DIRECTIVE -> { /* no-op */
                }

                IMPORT_DIRECTIVE -> imports.add(directive.value + ".")

                FUN_DIRECTIVE, FUNS_DIRECTIVE -> {
                    val (collected, newLineNo) = collectBlock(reader, lineNo)
                    lineNo = newLineNo

                    if (collected.unclosed) {
                        val kind = directive.name
                        reportMissing(
                            directiveLineNo,
                            "Unclosed $kind '${directive.value}' (reached ${if (collected.terminator == null) "EOF" else "next " + collected.terminator.name})",
                        )
                        lines.addAll(collected.old)
                        if (collected.terminator != null) {
                            pendingDirective = collected.terminator
                            pendingLineText = collected.terminatorLine
                            pendingDirectiveLine = lineNo
                        }
                    } else {
                        when (directive.name) {
                            FUN_DIRECTIVE -> processFun(directive.value, collected.old, directiveLineNo)
                            FUNS_DIRECTIVE -> processFuns(directive.value, collected.old, directiveLineNo)
                        }
                    }
                }
            }
        }
    }

    outputFile.parentFile?.mkdirs()
    outputFile.printWriter().use { out ->
        lines.forEach { out.println(it) }
    }
    return diagnostics.none { it.severity == Severity.ERROR }
}

data class Directive(
    val name: String,
    val value: String,
)

// Returns null for anything but a same-line, known-name directive — `<!---TODO-->`,
// `<!---TOC -->`, three-dash HTML comments, or unclosed openers all pass through as text.
fun parseDirective(line: String, syntax: DirectiveSyntax = DirectiveSyntax.HTML): Directive? {
    val trimLine = line.trim()
    if (!trimLine.startsWith(syntax.start) || !trimLine.endsWith(syntax.end)) return null
    val inner = trimLine
        .substring(syntax.start.length, trimLine.length - syntax.end.length)
        .trim()
    val match = DIRECTIVE_INNER_REGEX.matchEntire(inner) ?: return null
    val name = match.groupValues[1]
    if (name !in KORRO_DIRECTIVE_NAMES) return null
    return Directive(name, match.groupValues[2].trim())
}
