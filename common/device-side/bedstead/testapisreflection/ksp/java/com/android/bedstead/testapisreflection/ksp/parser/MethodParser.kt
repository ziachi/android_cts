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

import com.android.bedstead.testapisreflection.ksp.data.Context
import com.android.bedstead.testapisreflection.ksp.data.Method
import com.android.bedstead.testapisreflection.ksp.parser.Parser.Companion.getOrThrow
import com.squareup.kotlinpoet.ClassName

class MethodParser(
    private val typeNameParser: TypeNameParser = TypeNameParser(),
    private val allowedTestClasses: Collection<ClassName>,
    private val proxyPackage: String,
) : Parser<Method> {
    override fun parse(input: String): Result<Method> = runCatching {
        val frameworkClass = typeNameParser.parseClassName(input.substringBefore(","))
        val signature =
            input.substringAfter(",").replace(" +".toRegex(), " ").replace(", ", ",").trim().split(" ")
        val returnType = signature.dropLast(1).last().let(typeNameParser::parse).getOrThrow()
        val modifiers = signature.dropLast(2)
        val name = signature.last().substringBefore("(")
        val parameters =
            signature
                .last()
                .substringAfter("(")
                .substringBeforeLast(")")
                .split(",")
                .filterNot(String::isEmpty)
                .map(typeNameParser::parse)
                .getOrThrow()

        Method(
            frameworkClass = frameworkClass.getOrThrow(),
            name = name,
            returnType =
            Method.ReturnType(
                type = returnType,
                proxyType = Context.getProxy(proxyPackage, allowedTestClasses, returnType).getOrNull(),
            ),
            argumentTypes = parameters,
            isStatic = "static" in modifiers,
            isGetter = parameters.isEmpty() && (name.hasPrefix("get") || name.hasPrefix("is")),
        )
    }

    private fun String.hasPrefix(prefix: String): Boolean =
        startsWith(prefix) && get(prefix.length).isUpperCase()
