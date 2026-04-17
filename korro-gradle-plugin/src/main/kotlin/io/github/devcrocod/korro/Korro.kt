package io.github.devcrocod.korro

import java.io.File

const val DIRECTIVE_START = "<!---"
const val DIRECTIVE_END = "-->"
const val IMPORT_DIRECTIVE = "IMPORT"
const val FUN_DIRECTIVE = "FUN"
const val FUNS_DIRECTIVE = "FUNS"
const val END_DIRECTIVE = "END"
const val EOF = "\u001a"

const val END_SAMPLE = DIRECTIVE_START + END_DIRECTIVE + DIRECTIVE_END

val DIRECTIVE_REGEX =
    Regex("$DIRECTIVE_START\\s*([_a-zA-Z.]+)(?:\\s+(.+?(?=$DIRECTIVE_END|)))?(?:\\s*($DIRECTIVE_END))?\\s*")

fun KorroContext.korro(inputFile: File, outputFile: File): Boolean {
    logger.info("*** Reading $inputFile")
    val samplesTransformer = SamplesTransformer(this, rewriteAsserts)
    val lines = ArrayList<String>()
    val imports = mutableListOf("")

    fun processFun(funName: String, oldSampleLines: List<String>) {
        val functionNames = imports.map {
            it + funName
        }
        val newSamplesLines = functionNames.firstNotNullOfOrNull { name -> // TODO: can be improved
            var text = samplesTransformer(name) ?: groups.firstNotNullOfOrNull { group ->
                group.patterns.mapNotNull { pattern ->
                    samplesTransformer(name + pattern.nameSuffix)?.let {
                        group.beforeSample?.let { pattern.processSubstitutions(it) } + it +
                                group.afterSample?.let { pattern.processSubstitutions(it) }
                    }
                }.takeIf { it.isNotEmpty() }?.joinToString(
                    separator = "\n",
                    prefix = group.beforeGroup ?: "",
                    postfix = group.afterGroup ?: ""
                )
            }

            val output = outputsMap[name]
            if (text != null && output != null) {
                text += output.readText()
            }

            text?.split("\n")?.plus(END_SAMPLE)
        }
        if (newSamplesLines == null) {
            logger.warn("Cannot find PsiElement corresponding to '$funName'")
        }
        if (newSamplesLines != null && oldSampleLines != newSamplesLines) {
            logger.info("*** Add $funName sample")
            lines.addAll(newSamplesLines)
        } else {
            lines.addAll(oldSampleLines)
        }
    }

    inputFile.bufferedReader().use { bufferedReader ->
        while (true) {
            val line = bufferedReader.readLine() ?: break
            lines.add(line)
            var directive = parseDirective(line)
            when (directive?.name) {
                null, END_DIRECTIVE -> {
                }
                IMPORT_DIRECTIVE -> {
                    imports.add(directive.value + ".")
                }
                FUN_DIRECTIVE -> {
                    val oldSampleLines = ArrayList<String>()
                    while (true) {
                        val sampleLine = bufferedReader.readLine()
                        val nextDirective = if (sampleLine != null) parseDirective(sampleLine) else Directive(EOF, "")
                        when (nextDirective?.name) {
                            END_DIRECTIVE -> {
                                oldSampleLines.add(sampleLine)
                                break
                            }
                            EOF, FUN_DIRECTIVE -> {
                                processFun(directive!!.value, emptyList())
                                lines.addAll(oldSampleLines)
                                oldSampleLines.clear()
                                if (sampleLine == null) {
                                    directive = null
                                    break
                                }
                                directive = nextDirective
                                lines.add(sampleLine)
                            }
                            else -> {
                                oldSampleLines.add(sampleLine)
                            }
                        }
                    }
                    if (directive == null) break
                    processFun(directive.value, oldSampleLines)
                }
                FUNS_DIRECTIVE -> {
                }
                else -> logger.warn("Unrecognized directive '${directive.name}' on a line starting with '$DIRECTIVE_START' in '$inputFile'")
            }
        }
    }

    outputFile.parentFile?.mkdirs()
    outputFile.printWriter().use { out ->
        lines.forEach { out.println(it) }
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
