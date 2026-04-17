package io.github.devcrocod.korro.analysis

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.prevLeaf

class SampleExtractor(private val rewriteAsserts: Boolean) {

    fun extract(function: KtNamedFunction): String {
        val body = processBody(function)
        return createSampleBody(body)
    }

    private fun processBody(psiElement: PsiElement): String {
        val text = processSampleBody(psiElement).trim { it == '\n' || it == '\r' }.trimEnd()
        val lines = text.split("\n")
        val indent = lines.filter(String::isNotBlank).map { it.takeWhile(Char::isWhitespace).count() }.minOrNull() ?: 0
        return lines.joinToString("\n") { it.drop(indent) }
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
        val sampleBuilder = SampleBuilder(rewriteAsserts)
        this.accept(sampleBuilder)
        return sampleBuilder.text
    }

    private fun createSampleBody(body: String) =
        """ |
            |```kotlin
            |$body
            |```
            |""".trimMargin()

    private class SampleBuilder(private val rewriteAsserts: Boolean) : KtTreeVisitorVoid() {
        val builder = StringBuilder()
        val text: String get() = builder.toString()
        var start: Boolean = false

        private fun convertAssertPrints(expression: KtCallExpression) {
            val (argument, commentArgument) = expression.valueArguments
            builder.apply {
                append("println(")
                append(argument.text)
                append(") // ")
                append(commentArgument.extractStringArgumentValue())
            }
        }

        private fun convertAssertTrueFalse(expression: KtCallExpression, expectedResult: Boolean) {
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

        private fun convertAssertFails(expression: KtCallExpression) {
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

        private fun convertAssertFailsWith(expression: KtCallExpression) {
            val (funcArgument) = expression.valueArguments
            val (exceptionType) = expression.typeArguments
            builder.apply {
                val argument = funcArgument.extractFunctionalArgumentText()
                append(argument.lines().joinToString(separator = "\n") { "// $it" })
                append(" // will fail with ")
                append(exceptionType.text)
            }
        }

        private fun KtValueArgument.extractFunctionalArgumentText(): String =
            if (getArgumentExpression() is KtLambdaExpression)
                PsiTreeUtil.findChildOfType(this, KtBlockExpression::class.java)?.text ?: ""
            else
                text

        private fun KtValueArgument.extractStringArgumentValue() =
            (getArgumentExpression() as KtStringTemplateExpression)
                .entries.joinToString("") { it.text }

        override fun visitCallExpression(expression: KtCallExpression) {
            if (rewriteAsserts) {
                when (expression.calleeExpression?.text) {
                    "assertPrints" -> { convertAssertPrints(expression); return }
                    "assertTrue" -> { convertAssertTrueFalse(expression, expectedResult = true); return }
                    "assertFalse" -> { convertAssertTrueFalse(expression, expectedResult = false); return }
                    "assertFails" -> { convertAssertFails(expression); return }
                    "assertFailsWith" -> { convertAssertFailsWith(expression); return }
                }
            }
            super.visitCallExpression(expression)
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
                        builder.append(element.text)
                    }
                }
            })
        }
    }
}
