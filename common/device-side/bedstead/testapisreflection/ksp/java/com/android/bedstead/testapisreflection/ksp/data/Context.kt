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

package com.android.bedstead.testapisreflection.ksp.data

import com.android.bedstead.testapisreflection.ksp.gen.KTypes.isProxy
import com.android.bedstead.testapisreflection.ksp.parser.FieldParser
import com.android.bedstead.testapisreflection.ksp.parser.MethodParser
import com.android.bedstead.testapisreflection.ksp.parser.TypeNameParser
import com.android.bedstead.testapisreflection.ksp.util.Logger
import com.android.bedstead.testapisreflection.ksp.util.ResourceLoader
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toSet
import kotlinx.coroutines.runBlocking

/**
 * A god-object for generator configuration and input data.
 *
 * @property allowedTestClasses a set of framework classes which will get proxy classes generated.
 * @property allowedTestMethods a set of framework methods which will get either an implementation
 *   in the proxy class or an extension function.
 * @property allowedTestFields a set of framework fields which will be exposed in and managed by
 *   proxy classes.
 * @property log a base logger to use for spawning tagged loggers when reporting progress.
 * @property reflectionFileName the name of the file where all extension methods are to be written.
 * @property packageName the root package of all generated entities.
 * @property serviceAliases a map of android service classes to their framework name aliases. All
 *   mapped entities here will make the generator use framework services apis to retrieve the
 *   instance rather than creating one via reflection.
 */
data class Context(
    val allowedTestClasses: Set<ClassName>,
    val allowedTestMethods: Set<Method>,
    val allowedTestFields: Set<Field>,
    val log: Logger,
    val reflectionFileName: String,
    val packageName: String,
    val serviceAliases: Map<ClassName, String>,
) {
    /**
     * Resolve proxy type for a given [target]. Returns [target] if it's already a proxy type.
     * Resolves boxed types as well as simple types.
     *
     * @param target a (framework) class to resolve the proxy for.
     * @return [target] if it's already a proxy or resolved proxy type if one exists.
     */
    fun getProxy(target: ClassName): Result<TypeName> =
        Companion.getProxy(packageName, allowedTestClasses, target)

    companion object {
        /**
         * Resolve proxy type for a given [target]. Returns [target] if it's already a proxy type.
         * Resolves boxed types as well as simple types.
         *
         * @param packageName the root package of all generated entities.
         * @param allowedTestClasses a set of framework classes which will get proxy classes generated.
         * @param target a (framework) type to resolve the proxy for.
         * @return [target] if it's already a proxy or resolved proxy type if one exists.
         */
        fun getProxy(
            packageName: String,
            allowedTestClasses: Collection<ClassName>,
            target: TypeName,
        ): Result<TypeName> = runCatching {
            when {
                target.isProxy -> target
                target is ClassName && target in allowedTestClasses -> {
                    val name = "${target.simpleNames.last()}Proxy"
                    ClassName(packageName, name)
                }
                target is ParameterizedTypeName -> {
                    val elementProxy =
                        target.typeArguments
                            .single()
                            .let { getProxy(packageName, allowedTestClasses, it) }
                            .getOrThrow()
                    target.rawType.parameterizedBy(elementProxy)
                }
                else -> throw IllegalArgumentException("$target is not whitelisted for proxying")
            }
        }

        /** Constructs a [Context] instance with default values. */
        operator fun invoke(
            log: Logger,
            reflectionFileName: String = "TestApisReflection",
            packageName: String = "android.cts.testapisreflection",
            serviceAliases: Map<ClassName, String> =
                mapOf(
                    ClassName.bestGuess("android.app.DreamManager") to "dream",
                    ClassName.bestGuess("android.app.ActivityTaskManager") to "activity_task",
                ),
        ): Context = runBlocking {
            val loader = ResourceLoader()
            val typeNameParser = TypeNameParser()
            val fieldParser = FieldParser(typeNameParser)
            val allowedTestClasses =
                loader
                    .loadListFromResources("/apis/allowlisted-test-classes.txt")
                    .map(typeNameParser::parseClassName)
                    .map(Result<ClassName>::getOrThrow)
                    .toSet()

            val methodParser =
                MethodParser(
                    typeNameParser = typeNameParser,
                    allowedTestClasses = allowedTestClasses,
                    proxyPackage = packageName,
                )

            Context(
                allowedTestClasses = allowedTestClasses,
                allowedTestMethods =
                loader
                    .loadListFromResources("/apis/allowlisted-test-methods.txt")
                    .map(methodParser::parse)
                    .map(Result<Method>::getOrThrow)
                    .toSet(),
                allowedTestFields =
                loader
                    .loadListFromResources("/apis/allowlisted-test-fields.txt")
                    .map(fieldParser::parse)
                    .map(Result<Field>::getOrThrow)
                    .toSet(),
                log = log,
                reflectionFileName = reflectionFileName,
                packageName = packageName,
                serviceAliases = serviceAliases,
            )
        }
    }
}
