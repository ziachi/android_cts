/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.bedstead.testapisreflection.ksp.util

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSNode
import kotlin.reflect.KClass

/**
 * An implementation of [KSPLogger] to log a more appropriate and readable messages.
 *
 * @property tag the tag to insert into all logs written to this logger.
 * @property delegate the actual [KSPLogger] to pipe the formatted logs to.
 */
data class Logger(private val tag: String?, private val delegate: KSPLogger) : KSPLogger {
    constructor(
        kClass: KClass<*>,
        delegate: KSPLogger,
    ) : this(tag = kClass.simpleName, delegate = delegate)

    fun tagged(tag: String?): Logger = Logger(tag, delegate)

    fun tagged(kClass: KClass<*>): Logger = Logger(kClass, delegate)

    private fun format(message: String, level: Level): String {
        val prefix = tag?.let { "${level.name.first()} [${it.padEnd(10)}] " } ?: ""
        return level.paint("$prefix$message")
    }

    fun debug(message: String, symbol: KSNode? = null) = logging(message, symbol)

    override fun logging(message: String, symbol: KSNode?) {
        delegate.logging(format(message, Level.DEBUG), symbol)
    }

    override fun info(message: String, symbol: KSNode?) {
        delegate.info(format(message, Level.INFO), symbol)
    }

    override fun warn(message: String, symbol: KSNode?) {
        delegate.warn(format(message, Level.WARN), symbol)
    }

    override fun error(message: String, symbol: KSNode?) {
        delegate.error(format(message, Level.ERROR), symbol)
    }

    override fun exception(e: Throwable) {
        delegate.exception(e)
    }

    @Suppress("unused")
    fun dev(message: Any?) = warn(">>> $message", null)

    enum class Level(val paint: (String) -> String) {
        DEBUG({ "\u001B[37m${it}\u001B[0m" }),
        INFO({ "\u001B[36m${it}\u001B[0m" }),
        WARN({ "\u001B[33m${it}\u001B[0m" }),
        ERROR({ "\u001B[31m${it}\u001B[0m" })
    }
}