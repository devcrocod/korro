package com.github.devcrocod.korro

import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.dokka.Platform
import org.jetbrains.dokka.analysis.AnalysisEnvironment
import org.jetbrains.dokka.analysis.DokkaResolutionFacade
import org.jetbrains.dokka.analysis.EnvironmentAndFacade
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.idea.kdoc.resolveKDocSampleLink
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.prevLeaf
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.utils.PathUtil
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.math.max
import kotlin.math.min

class SamplesTransformer(private val context: KorroContext) {

    private class SampleBuilder : KtTreeVisitorVoid() {
        val builder = StringBuilder()
        val text: String
            get() = builder.toString()

        val errors = mutableListOf<ConvertError>()

        var start: Boolean = false

        data class ConvertError(val e: Exception, val text: String, val loc: String)

        fun convertAssertPrints(expression: KtCallExpression) {
            val (argument, commentArgument) = expression.valueArguments
            builder.apply {
                append("println(")
                append(argument.text)
                append(") // ")
                append(commentArgument.extractStringArgumentValue())
            }
        }

        fun convertAssertTrueFalse(expression: KtCallExpression, expectedResult: Boolean) {
            val (argument) = expression.valueArguments
            builder.apply {
                expression.valueArguments.getOrNull(1)?.let {
                    append("// ${it.extractStringArgumentValue()}")
                    val ws = expression.prevLeaf { it is PsiWhiteSpace }
                    append(ws?.text ?: "\n")
                }
                append("println(\"")
                append(argument.text)
                append(" is \${")
                append(argument.text)
                append("}\") // $expectedResult")
            }
        }

        fun convertAssertFails(expression: KtCallExpression) {
            val valueArguments = expression.valueArguments

            val funcArgument: KtValueArgument
            val message: KtValueArgument?

            if (valueArguments.size == 1) {
                message = null
                funcArgument = valueArguments.first()
            } else {
                message = valueArguments.first()
                funcArgument = valueArguments.last()
            }

            builder.apply {
                val argument = funcArgument.extractFunctionalArgumentText()
                append(argument.lines().joinToString(separator = "\n") { "// $it" })
                append(" // ")
                if (message != null) {
                    append(message.extractStringArgumentValue())
                }
                append(" will fail")
            }
        }

        private fun KtValueArgument.extractFunctionalArgumentText(): String {
            return if (getArgumentExpression() is KtLambdaExpression)
                PsiTreeUtil.findChildOfType(this, KtBlockExpression::class.java)?.text ?: ""
            else
                text
        }

        private fun KtValueArgument.extractStringArgumentValue() =
            (getArgumentExpression() as KtStringTemplateExpression)
                .entries.joinToString("") { it.text }

        fun convertAssertFailsWith(expression: KtCallExpression) {
            val (funcArgument) = expression.valueArguments
            val (exceptionType) = expression.typeArguments
            builder.apply {
                val argument = funcArgument.extractFunctionalArgumentText()
                append(argument.lines().joinToString(separator = "\n") { "// $it" })
                append(" // will fail with ")
                append(exceptionType.text)
            }
        }

        override fun visitCallExpression(expression: KtCallExpression) {
            when (expression.calleeExpression?.text) {
                "assertPrints" -> convertAssertPrints(expression)
                "assertTrue" -> convertAssertTrueFalse(expression, expectedResult = true)
                "assertFalse" -> convertAssertTrueFalse(expression, expectedResult = false)
                "assertFails" -> convertAssertFails(expression)
                "assertFailsWith" -> convertAssertFailsWith(expression)
                else -> super.visitCallExpression(expression)
            }
        }

        private fun reportProblemConvertingElement(element: PsiElement, e: Exception) {
            val text = element.text
            val document = PsiDocumentManager.getInstance(element.project).getDocument(element.containingFile)

            val lineInfo = if (document != null) {
                val lineNumber = document.getLineNumber(element.startOffset)
                "$lineNumber, ${element.startOffset - document.getLineStartOffset(lineNumber)}"
            } else {
                "offset: ${element.startOffset}"
            }
            errors += ConvertError(e, text, lineInfo)
        }

        override fun visitElement(element: PsiElement) {
            if (element is LeafPsiElement) {
                val t = element.text
                if (t.filterNot { it.isWhitespace() } == "//SampleEnd") start = false
                if (start) builder.append(t)
                if (t.filterNot { it.isWhitespace() } == "//SampleStart") start = true
            }

            element.acceptChildren(object : PsiElementVisitor() {
                override fun visitElement(element: PsiElement) {
                    try {
                        element.accept(this@SampleBuilder)
                    } catch (e: Exception) {
                        try {
                            reportProblemConvertingElement(element, e)
                        } finally {
                            builder.append(element.text) //recover
                        }
                    }
                }
            })
        }

    }

    private fun processBody(psiElement: PsiElement): String {
        val text = processSampleBody(psiElement).trim { it == '\n' || it == '\r' }.trimEnd()
        val lines = text.split("\n")
        val indent = lines.filter(String::isNotBlank).map { it.takeWhile(Char::isWhitespace).count() }.minOrNull() ?: 0
        return lines.joinToString("\n") { it.drop(indent) }
    }

    operator fun invoke(functionName: String): String? {
        val facade = setUpAnalysis().facade
        val psiElement = fqNameToPsiElement(facade, functionName) ?: return null.also { context.logger.warn("Cannot find PsiElement corresponding to $functionName") }
        val body = processBody(psiElement)
        return createSampleBody(body)
    }

    private fun setUpAnalysis(): EnvironmentAndFacade =
        AnalysisEnvironment(KorroMessageCollector(context.logger), Platform.jvm).run {
            addClasspath(PathUtil.getJdkClassesRootsFromCurrentJre())
            addSources(context.sampleSet.toList())
            loadLanguageVersionSettings(null, null)

            val environment = createCoreEnvironment()
            val (facade, _) = createResolutionFacade(environment)
            EnvironmentAndFacade(environment, facade)
        }

    private fun createSampleBody(body: String) =
        """ |```kotlin
            |$body
            |```""".trimMargin()

    private fun fqNameToPsiElement(resolutionFacade: DokkaResolutionFacade?, functionName: String): PsiElement? {
        val packageName = functionName.takeWhile { it != '.' }
        val descriptor = resolutionFacade?.resolveSession?.getPackageFragment(FqName(packageName))
            ?: return null.also { context.logger.warn("Cannot find descriptor for package $functionName") }
        val symbol = resolveKDocSampleLink(
            BindingContext.EMPTY,
            resolutionFacade,
            descriptor,
            functionName.split(".")
        ).firstOrNull() ?: return null.also { context.logger.warn("Unresolved function $functionName") }
        return DescriptorToSourceUtils.descriptorToDeclaration(symbol)
    }

    private fun processSampleBody(psiElement: PsiElement) = when (psiElement) {
        is KtDeclarationWithBody -> {
            val bodyExpression = psiElement.bodyExpression
            val bodyExpressionText = bodyExpression!!.buildSampleText()
            when (bodyExpression) {
                is KtBlockExpression -> bodyExpressionText.removeSurrounding("{", "}")
                else -> bodyExpressionText
            }
        }
        else -> psiElement.buildSampleText()
    }

    private fun PsiElement.buildSampleText(): String {
        val sampleBuilder = SampleBuilder()
        this.accept(sampleBuilder)

        sampleBuilder.errors.forEach {
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            it.e.printStackTrace(pw)

            this@SamplesTransformer.context.logger.error(
                "${containingFile.name}: (${it.loc}): Exception thrown while converting \n```\n${it.text}\n```\n$sw",
                it.e
            )
        }
        return sampleBuilder.text
    }
}

class KorroMessageCollector(private val logger: KorroLog) : MessageCollector {
    override fun clear() {
        seenErrors = false
    }

    private var seenErrors = false

    override fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageSourceLocation?) {
        if (severity == CompilerMessageSeverity.ERROR) {
            seenErrors = true
        }
        logger.info(MessageRenderer.PLAIN_FULL_PATHS.render(severity, message, location))
    }

    override fun hasErrors() = seenErrors
}
