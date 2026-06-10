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

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeName

/**
 * Parsed descriptor of the framework class method.
 *
 * @property frameworkClass the name of the framework class where this method originates from.
 * @property name the name of the method.
 * @property returnType the return type of the method.
 * @property argumentTypes the ordered types of the method arguments.
 * @property isStatic is the method declared as static.
 * @property isGetter is the method a java getter (starts with `get` or `is` and has no arguments).
 */
data class Method(
    val frameworkClass: ClassName,
    val name: String,
    val returnType: ReturnType,
    val argumentTypes: List<TypeName>,
    val isStatic: Boolean,
    val isGetter: Boolean,
) {
    /**
     * Parsed and resolved return type of a [Method].
     *
     * @property type the raw type as parsed from the framework specification
     * @property proxyType matching generated proxy type if one exists for the [type]
     */
    data class ReturnType(val type: TypeName, var proxyType: TypeName?)

    /**
     * [TypeName] to be used in generated entities. This resolves to proxy type if one exists or
     * framework type otherwise.
     */
    val resolvedReturn = returnType.proxyType ?: returnType.type

    /** String representation of the method in kotlin context. */
    val signature: String = run {
        val sig =
            if (isGetter) {
                "val $frameworkClass.$name: $resolvedReturn"
            } else {
                "fun $frameworkClass.$name(${argumentTypes.joinToString()}): $resolvedReturn"
            }
        val annotation = if (isStatic) "@JvmStatic " else ""
        "$annotation$sig"
    }
}
