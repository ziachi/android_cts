
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

package com.android.bedstead.testapisreflection.ksp.parser

import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.ARRAY
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.BOOLEAN_ARRAY
import com.squareup.kotlinpoet.BYTE
import com.squareup.kotlinpoet.BYTE_ARRAY
import com.squareup.kotlinpoet.CHAR
import com.squareup.kotlinpoet.CHAR_ARRAY
import com.squareup.kotlinpoet.COLLECTION
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.DOUBLE_ARRAY
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.FLOAT_ARRAY
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.INT_ARRAY
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.LONG_ARRAY
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.SET
import com.squareup.kotlinpoet.SHORT
import com.squareup.kotlinpoet.SHORT_ARRAY
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.UNIT
import java.io.StringReader

class TypeNameParser : Parser<TypeName> {
    /**
     * Parse a string representation of a type to [TypeName]. Understands Java types and has full
     * support for parameterized types.
     */
    override fun parse(input: String): Result<TypeName> = runCatching {
        val str = input.replace(" ", "").trim()
        knownJavaClassNames[str] ?: str.reader().readType() ?: error("Unable to parse type from $str")
    }

    /** Parse a string representation of a type to [ClassName]. Assumes non-parameterised type. */
    fun parseClassName(input: String): Result<ClassName> = runCatching {
        knownJavaClassNames[input] ?: ClassName.bestGuess(input)
    }

    private fun parseArray(input: String): TypeName =
        knownJavaArrays[input]
            ?: ARRAY.parameterizedBy(parseClassName(input.removeSuffix("[]")).getOrThrow())

    private fun StringReader.readType(): TypeName? {
        var boxName = ""
        loop@ while (true) {
            val next = read()
            if (next == -1) break@loop
            when (val c = next.toChar()) {
                '<' -> {
                    val args = readParameterizedTypeArguments()
                    return parseClassName(boxName).getOrThrow().parameterizedBy(args)
                }
                ',',
                '>' -> break@loop
                '[' -> {
                    require(read() == ']'.code)
                    return parseArray("${boxName}[]")
                }
                else -> boxName += c
            }
        }
        return boxName.takeIf(String::isNotEmpty)?.let(::parseClassName)?.getOrThrow()
    }

    private fun StringReader.readParameterizedTypeArguments(): List<TypeName> {
        val args = mutableListOf<TypeName>()
        loop@ while (true) {
            val arg = readType()
            if (arg != null) {
                args += arg
                mark(0)
                if (read() != ','.code) reset()
            } else {
                break@loop
            }
        }
        return args
    }

    companion object {
        private val knownJavaArrays: Map<String, ClassName> = buildMap {
            this["byte[]"] = BYTE_ARRAY
            this["short[]"] = SHORT_ARRAY
            this["long[]"] = LONG_ARRAY
            this["int[]"] = INT_ARRAY
            this["char[]"] = CHAR_ARRAY
            this["float[]"] = FLOAT_ARRAY
            this["double[]"] = DOUBLE_ARRAY
            this["boolean[]"] = BOOLEAN_ARRAY
        }
        private val knownJavaClassNames: Map<String, ClassName> =
            buildMap {
                this["byte"] = BYTE
                this["short"] = SHORT
                this["long"] = LONG
                this["int"] = INT
                this["char"] = CHAR
                this["float"] = FLOAT
                this["double"] = DOUBLE
                this["boolean"] = BOOLEAN
                this["String"] = STRING
                this["java.lang.String"] = STRING
                this["java.lang.Object"] = ANY
                this["java.util.List"] = LIST
                this["java.util.Set"] = SET
                this["java.util.Collection"] = COLLECTION
                this["java.util.Map"] = MAP
                this["void"] = UNIT
            } + knownJavaArrays
    }
}