package org.devcrocod.korro

import org.slf4j.Logger
import org.slf4j.LoggerFactory

sealed class KorroLog {
    var hasWarningOrError = false
    var nOutdated = 0

    abstract fun debug(message: String)
    abstract fun info(message: String)
    abstract fun warn(message: String)
    abstract fun error(message: String, e: Exception)

    fun outdated(s: String) {
        warn(s)
        nOutdated++
    }
}

class ConsoleLog : KorroLog() {
    override fun debug(message: String): Unit = println(message)

    override fun info(message: String): Unit = println(message)

    override fun warn(message: String): Unit = println(message).also { hasWarningOrError = true }

    override fun error(message: String, e: Exception): Unit = println(message).also {
        e.printStackTrace(System.out)
        hasWarningOrError = true
    }
}

class LoggerLog : KorroLog() {
    private val logger: Logger by lazy { LoggerFactory.getLogger("korro") }

    override fun debug(message: String): Unit = logger.debug(message)

    override fun info(message: String): Unit = logger.info(message)

    override fun warn(message: String): Unit = logger.warn(message).also { hasWarningOrError = true }

    override fun error(message: String, e: Exception): Unit = logger.error(message).also { hasWarningOrError = true }
}