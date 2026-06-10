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

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class StringLinesDiffTest {

    @Test
    fun emptyString_noDifference() {
        val diff = StringLinesDiff("", "")

        assertThat(diff.countLinesDifference()).isEqualTo(0)
        assertThat(diff.extraLines).isEmpty()
        assertThat(diff.missingLines).isEmpty()
    }

    @Test
    fun compareSimpleLists_showDifference() {
        val base =
            """a
b
c
"""
        val new =
            """a
x
c
"""

        val diff = StringLinesDiff(base, new)

        assertThat(diff.countLinesDifference()).isEqualTo(1)
        assertThat(diff.extraLines).contains("x")
        assertThat(diff.extraLinesString()).isEqualTo("x")
        assertThat(diff.missingLines).contains("b")
    }
}
