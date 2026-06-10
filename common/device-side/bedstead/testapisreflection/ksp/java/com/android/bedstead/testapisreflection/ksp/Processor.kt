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
package com.android.bedstead.testapisreflection.ksp

import com.android.bedstead.testapisreflection.ksp.data.Context
import com.android.bedstead.testapisreflection.ksp.gen.ProxyClassGenerator
import com.android.bedstead.testapisreflection.ksp.gen.ProxyExtensionsGenerator
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import kotlinx.coroutines.runBlocking

/**
 * Processor for generating Proxy classes and extension methods that allow accessing hidden
 * framework APIs via reflection.
 *
 * Any Test API that needs to be accessed through reflection should be added to
 * [Context.allowedTestMethods].
 *
 * Any class that has `@TestApi` annotation marked at class level for e.g.
 * [android.content.pm.UserInfo] and that needs to be accessed through reflection should be added to
 * [Context.allowedTestClasses] with the api name added to [Context.allowedTestMethods] as well.
 *
 * Any field that is part of an [Context.allowedTestClasses] and that needs to be accessed through
 * reflection should be added to [Context.allowedTestFields] for e.g.
 * [android.content.pm.UserInfo.id]
 *
 * For each entry in [Context.allowedTestMethods] the processor will generate a method in
 * "[Context.packageName].[Context.reflectionFileName]" which allows the user to access the Test API
 * through reflection.
 *
 * <p>For each entry in [Context.allowedTestClasses] the processor will generate a proxy class which
 * enables access to all methods listed in [Context.allowedTestMethods].
 *
 * ### Usage
 * 1. Any method marked as `@TestApi` at method level can be accessed using the
 *    "[Context.packageName].[Context.reflectionFileName]" kotlin class.
 * 1. Any method that is a member of a class that is marked as `@TestApi` at class level can be
 *    accessed through its respective Proxy class. For e.g. all methods inside
 *    [android.content.pm.UserInfo] can be accessed using
 *    [android.cts.testapisreflection.UserInfoProxy].
 * 1. If the TestApisReflection file is accessed from a java class:
 *     - `import android.cts.testapisreflection.TestApisReflectionKt;`
 *     - Access the method using `TestApisReflectionKt.method_name(receiver_object, args…)`
 *     - Example: [android.service.quicksettings.TileService.isQuickSettingsSupported] is a TestApi
 *       and can be accessed using
 *       [android.cts.testapisreflection.TestApisReflectionKt.isQuickSettingsSupported(tileServiceInstance)].
 * 1. If the TestApisReflection file is accessed from a kotlin class, add the below import line to
 *    access any TestApi method normally.
 *         - `import android.cts.testspisreflection.*`
 *         - Example: [android.service.quicksettings.TileService.isQuickSettingsSupported] is a
 *           TestApi and can be accessed using `tileService.isQuickSettingsSupported()` using the
 *           mentioned import line.
 *
 * **Note:** Generated proxy classes should not be exposed outside of bedstead and must always be
 * hidden and wrapped by Bedstead-specific classes.
 */
class Processor(private val context: Context, private val codeGenerator: CodeGenerator) :
    SymbolProcessor {
    private val log = context.log.tagged(this::class)

    override fun process(resolver: Resolver): List<KSAnnotated> = runBlocking {
        log.debug("Generating test APIs")
        log.debug("$context")

        val newFiles = resolver.getNewFiles()
        val newClasses =
            newFiles
                .map(KSFile::declarations)
                .flatMap { it.filterIsInstance<KSClassDeclaration>() }
                .map(KSClassDeclaration::toClassName)
                .toSet()
        generateProxyClasses(context, newClasses)
        generateProxyMethodExtensions(context, newFiles.toSet())

        return@runBlocking listOf()
    }

    private fun generateProxyClasses(context: Context, newClasses: Collection<ClassName>) {
        log.debug("Generating proxy classes")
        val gen = ProxyClassGenerator(context)
        context.allowedTestClasses
            .asSequence()
            .map(gen::generate)
            .filterNot { (name, _) -> name in newClasses }
            .map { (name, type) -> FileSpec.builder(name).addType(type).build() }
            .onEach { log.debug("--- ${it.relativePath} ---" + "\n$it") }
            .forEach { file ->
                log.debug("Writing ${file.relativePath}")
                file.writeTo(codeGenerator, aggregating = false)
                log.info("Generated ${file.relativePath}")
            }
        log.info("Proxy class generation complete")
    }

    private fun generateProxyMethodExtensions(context: Context, newFiles: Collection<KSFile>) {
        val file =
            FileSpec.builder(packageName = context.packageName, fileName = context.reflectionFileName)
        val filePath = file.build().relativePath
        if (newFiles.any { it.filePath.endsWith(filePath) }) return

        log.debug("Generating proxy method extensions")
        val gen = ProxyExtensionsGenerator(context)
        context.allowedTestMethods
            .asSequence()
            .filter { it.frameworkClass !in context.allowedTestClasses }
            .map(gen::generate)
            .onEach { log.debug("--- ${it.name} ---\n$it") }
            .forEach { it.addTo(file) }

        val output = file.build()
        log.debug("Writing ${output.relativePath}")
        output.writeTo(codeGenerator, aggregating = false)
        log.info("Generated ${output.relativePath}")

        log.info("Proxy method generation complete")
    }
}