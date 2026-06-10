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

package com.android.xts.apimapper

import com.android.xts.apimapper.asm.ArgumentException

private const val IN_JAR_OPTION = "--in-jar"
private const val OUT_JAR_OPTION = "--out-jar"

/** A class to parse and validate input options. */
class ApiMapperOption(
    val args: Array<String>
) {

    private val options = getOptions(args)

    /** Validate whether input options are valid. */
    fun validateOptions() {
        if (options[IN_JAR_OPTION] == null) {
            throw ArgumentException("Option --in-jar missing.")
        }
        if (options[OUT_JAR_OPTION] == null) {
            throw ArgumentException("Option --out-jar missing.")
        }
        if (options[IN_JAR_OPTION]?.size != 1) {
            throw ArgumentException("Only one input jar file is allowed.")
        }
        if (options[OUT_JAR_OPTION]?.size != 1) {
            throw ArgumentException("Only one output jar file is allowed.")
        }
    }

    /** Get the input jar file from the option. */
    fun getInJar(): String {
        return options[IN_JAR_OPTION]?.get(0) ?: ""
    }

    /** Get the output jar file from the option. */
    fun getOutJar(): String {
        return options[OUT_JAR_OPTION]?.get(0) ?: ""
    }

    private fun getOptions(args: Array<String>): Map<String, List<String>> {
        var option = ""
        val parseResult: MutableMap<String, MutableList<String>> = mutableMapOf()
        args.forEach {
            if (it.startsWith("-")) {
                option = it
                parseResult[option] = mutableListOf()
            } else {
                parseResult[option]?.add(it)
            }
        }
        return parseResult
    }
}
