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

import com.squareup.kotlinpoet.ContextReceivable
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName

/** A union type over [PropertySpec] | [FunSpec]. */
sealed interface FunOrPropertySpec {
    /** Add this spec to a [file]. */
    fun addTo(file: FileSpec.Builder)

    /** The name of this spec */
    val name: String

    /** Receiver type for this spec if any. */
    val receiver: TypeName?

    /** Boxed [FunSpec] | [PropertySpec] */
    val spec: ContextReceivable

    /** [FunSpec] container for [FunOrPropertySpec] */
    @JvmInline
    value class Fun(override val spec: FunSpec) : FunOrPropertySpec {
        override fun toString(): String = spec.toString()

        override fun addTo(file: FileSpec.Builder) {
            file.addFunction(spec)
        }

        override val name: String
            get() = spec.name

        override val receiver: TypeName?
            get() = spec.receiverType
    }

    /** [PropertySpec] container for [FunOrPropertySpec] */
    @JvmInline
    value class Property(override val spec: PropertySpec) : FunOrPropertySpec {
        override fun toString(): String = spec.toString()

        override fun addTo(file: FileSpec.Builder) {
            file.addProperty(spec)
        }

        override val name: String
            get() = spec.name

        override val receiver: TypeName?
            get() = spec.receiverType
    }
}
