package io.github.devcrocod.korro

import org.jetbrains.kotlin.utils.addToStdlib.firstNotNullResult
import java.io.File

const val DIRECTIVE_START = "<!---"
const val DIRECTIVE_END = "-->"
const val IMPORT_DIRECTIVE = "IMPORT"
const val FUN_DIRECTIVE = "FUN"
const val FUNS_DIRECTIVE = "FUNS"
const val END_DIRECTIVE = "END"

val DIRECTIVE_REGEX =
    Regex("$DIRECTIVE_START\\s*([_a-zA-Z.]+)(?:\\s+(.+?(?=$DIRECTIVE_END|)))?(?:\\s*($DIRECTIVE_END))?\\s*")

fun KorroContext.korro(inputFile: File): Boolean {
    logger.info("*** Reading $inputFile")
    val inputFileType = inputFile.type()
    if (inputFileType != InputFileType.MARKDOWN) {
        logger.warn("WARNING: $inputFile: Unknown input file type. Treating it as markdown.")
    }
    val samplesTransformer = SamplesTransformer(this)
    val lines = ArrayList<String>()
    val imports = mutableListOf("")
    var rewrite = false
    inputFile.bufferedReader().use { bufferedReader ->
        while (true) {
            val line = bufferedReader.readLine() ?: break
            lines.add(line)
            val directive = parseDirective(line)
            when (directive?.name) {
                null, END_DIRECTIVE -> {}
                IMPORT_DIRECTIVE -> {
                    imports.add(directive.value + ".")
                }
                FUN_DIRECTIVE -> {
                    val oldSampleLines = ArrayList<String>()
                    while (true) {
                        val sampleLine = bufferedReader.readLine() ?: error("Unexpected end of file after '$line'")
                        if (parseDirective(sampleLine)?.name == END_DIRECTIVE) {
                            oldSampleLines.add(sampleLine)
                            break
                        }
                        oldSampleLines.add(sampleLine)
                    }
                    val functionNames = imports.map {
                        it + directive.value
                    }
                    val newSamplesLines = functionNames.firstNotNullResult { // TODO: can be improved
                        samplesTransformer(it)?.split("\n")?.plus(oldSampleLines.last()) //?: oldSampleLines
                    }
                    if (newSamplesLines == null) {
                        logger.warn("Cannot find PsiElement corresponding to '${directive.value}'")
                    }
                    if (newSamplesLines != null && oldSampleLines != newSamplesLines) {
                        rewrite = true
                        logger.info("*** Add ${directive.value} sample")
                        lines.addAll(newSamplesLines)
                    } else {
                        lines.addAll(oldSampleLines)
                    }
                }
                FUNS_DIRECTIVE -> {}
                else -> logger.warn("Unrecognized directive '${directive.name}' on a line starting with '$DIRECTIVE_START' in '$inputFile'")
            }
        }
    }
    if (rewrite) {
        inputFile.printWriter().use { out ->
            lines.forEach { out.println(it) }
        }
    }
    return true
}

fun KorroContext.korroClean(inputFile: File): Boolean {
    logger.info("*** Cleaning $inputFile")
    val inputFileType = inputFile.type()
    if (inputFileType != InputFileType.MARKDOWN) {
        logger.warn("WARNING: $inputFile: Unknown input file type. Treating it as markdown.")
    }
    val lines = ArrayList<String>()
    var rewrite = false
    inputFile.bufferedReader().use { bufferedReader ->
        while (true) {
            val line = bufferedReader.readLine() ?: break
            lines.add(line)
            val directive = parseDirective(line)
            when (directive?.name) {
                FUN_DIRECTIVE -> {
                    val oldSampleLines = ArrayList<String>()
                    while (true) {
                        val sampleLine = bufferedReader.readLine() ?: error("Unexpected end of file after '$line'")
                        if (parseDirective(sampleLine)?.name == END_DIRECTIVE) {
                            oldSampleLines.add(sampleLine)
                            break
                        }
                        oldSampleLines.add(sampleLine)
                    }
                    if (oldSampleLines.isNotEmpty() && oldSampleLines.size > 1) {
                        rewrite = true
                        logger.info("*** Clean ${directive.value} sample")
                        lines.add(oldSampleLines.last())
                    } else {
                        lines.add(oldSampleLines.first())
                    }
                }
                FUNS_DIRECTIVE -> {}
                else -> {}
            }
        }
    }
    if (rewrite) {
        inputFile.printWriter().use { out ->
            lines.forEach { out.println(it) }
        }
    }
    return true
}

data class Directive(
    val name: String,
    val value: String,
)

fun parseDirective(line: String): Directive? {
    val trimLine = line.trim()
    if (!trimLine.startsWith(DIRECTIVE_START)) return null
    val match = DIRECTIVE_REGEX.matchEntire(trimLine) ?: return null
    val groups = match.groups.filterNotNull().toMutableList()
    require(groups.last().value == DIRECTIVE_END) { "Directive must end on the same line with '$DIRECTIVE_END'" }
    return Directive(groups[1].value.trim(), groups.getOrNull(2)?.value?.trim() ?: "")
}

enum class InputFileType(
    val extension: String
) {
    MARKDOWN(".md"),
    UNKNOWN("") // works just like MARKDOWN
}

fun File.type(): InputFileType = InputFileType.values().first { name.endsWith(it.extension) }