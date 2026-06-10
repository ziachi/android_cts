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
package com.android.bedstead.nene.utils

/**
 * Simple tool to find lines in strings that have changed,
 * the main use-case is comparing the command outputs,
 * this class ignores the order of the lines in the text!
 */
class StringLinesDiff(private val base: String, private val newString: String) {

    private fun findExtraLines(baseString: String, newString: String): List<String> {
        val baseStringLines = baseString.lines()
        return newString.lines().filterNot { baseStringLines.contains(it) }
    }

    /**
     * List of lines present in newString but absent in baseString.
     */
    val extraLines by lazy { findExtraLines(base, newString) }

    /**
     * Returns a string representation of the extra lines, joining them with a newline character.
     */
    fun extraLinesString() = extraLines.joinToString(separator = "\n")

    /**
     * List of lines present in baseString but absent in newString.
     */
    val missingLines by lazy { findExtraLines(newString, base) }

    /**
     * Returns number of lines different between baseString and newString.
     */
    fun countLinesDifference(): Int = maxOf(extraLines.size, missingLines.size)

    companion object {
        const val DEVICE_POLICY_STANDARD_LINES_DIFFERENCE = 4
    }
}
