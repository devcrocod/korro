package me.devcrocod.korro

import java.io.File

const val DIRECTIVE_START = "<!---"
const val DIRECTIVE_END = "-->"
const val END_DIRECTIVE = "<!---END-->"

fun KorroContext.korro(inputFile: File): Boolean {
    logger.info("*** Reading $inputFile")
    val inputFileType = inputFile.type()
    if (inputFileType != InputFileType.MARKDOWN) {
        logger.warn("WARNING: $inputFile: Unknown input file type. Treating it as markdown.")
    }
    val samplesTransformer = SamplesTransformer(this)
    val lines = ArrayList<String>()
    var rewrite = false
    inputFile.bufferedReader().use { bufferedReader ->
        while (true) {
            val line = bufferedReader.readLine() ?: break
            lines.add(line)
            if (line.startsWith(DIRECTIVE_START)) {
                val functionName = line.substringAfter(DIRECTIVE_START).substringBefore(DIRECTIVE_END).trim()
                if (functionName == "END") {
                    logger.warn(
                        "WARNING: Incorrect name $functionName. " +
                                "Sample name should not be called END, it is the directive of the end of sample."
                    )
                }

                val oldSampleLines = ArrayList<String>()
                while (true) {
                    val sampleLine = bufferedReader.readLine() ?: break
                    if (sampleLine.filter { it != ' ' } == END_DIRECTIVE) {
                        oldSampleLines.add(END_DIRECTIVE)
                        break
                    }
                    oldSampleLines.add(sampleLine)
                }
                val newSamplesLines = samplesTransformer(functionName)?.split("\n")?.plus(END_DIRECTIVE) ?: oldSampleLines
                if (oldSampleLines != newSamplesLines) {
                    rewrite = true
                    logger.info("*** Add $functionName sample")
                    lines.addAll(newSamplesLines)
                } else {
                    lines.addAll(oldSampleLines)
                }
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

enum class InputFileType(
    val extension: String
) {
    MARKDOWN(".md"),
    UNKNOWN("") // works just like MARKDOWN
}

fun File.type(): InputFileType = InputFileType.values().first { name.endsWith(it.extension) }