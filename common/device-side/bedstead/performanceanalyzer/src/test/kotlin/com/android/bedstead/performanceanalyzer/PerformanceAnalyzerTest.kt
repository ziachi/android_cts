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

package com.android.bedstead.performanceanalyzer

import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.performanceanalyzer.PerformanceAnalyzer.Companion.analyzeThat
import com.android.bedstead.performanceanalyzer.annotations.PerformanceTest
import com.android.bedstead.performanceanalyzer.exceptions.PerformanceTestFailedException
import com.google.common.truth.Truth.assertThat
import kotlin.random.Random
import org.junit.Assert.assertThrows
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class PerformanceAnalyzerTest {

    private val runnable = {
        Thread.sleep(1000)
    }

    private val runnableThatFails = {
        Thread.sleep(500)
        throw RuntimeException()
    }

    @PerformanceTest
    fun analyzeThat_runnable_finishesIn_true_success() {
        assertThat(
            analyzeThat(runnable)
                .finishesIn(2000))
            .isTrue()
    }

    @PerformanceTest
    fun analyzeThat_runnable_finishesIn_false_throws() {
        val thrown = assertThrows(PerformanceTestFailedException::class.java) {
            analyzeThat(runnable)
                .finishesIn(500)
            }

        assertThat(thrown).hasMessageThat().contains("The function did not comply with the SLOs")
    }

    @PerformanceTest
    fun analyzeThat_runnable_withCleanUpSpecified_finishesIn_true_success() {
        assertThat(
            analyzeThat(runnable)
                .cleanUpUsing(runnable)
                .finishesIn(2000))
            .isTrue()
    }

    @PerformanceTest
    fun analyzeThat_runnable_withCleanUpSpecified_finishesIn_false_throws() {
        val thrown = assertThrows(PerformanceTestFailedException::class.java) {
            analyzeThat(runnable)
                .cleanUpUsing(runnable)
                .finishesIn(500)
        }

        assertThat(thrown).hasMessageThat().contains("The function did not comply with the SLOs")
    }

    @PerformanceTest
    fun analyzeThat_runnable_withCleanUpAndIterationsSpecified_finishesIn_true_success() {
        assertThat(
            analyzeThat(runnable)
                .cleanUpUsing(runnable)
                .runsNumberOfTimes(3)
                .finishesIn(2000))
            .isTrue()
    }

    @PerformanceTest
    fun analyzeThat_runnable_withCleanUpAndIterationsSpecified_finishesIn_false_throws() {
        val thrown = assertThrows(PerformanceTestFailedException::class.java) {
            analyzeThat(runnable)
                .cleanUpUsing(runnable)
                .runsNumberOfTimes(3)
                .finishesIn(500)
        }

        assertThat(thrown).hasMessageThat().contains("The function did not comply with the SLOs")
    }

    @PerformanceTest
    fun analyzeThat_runnableThatFails_summaryContainsFailureCounts() {
       val thrown = assertThrows(PerformanceTestFailedException::class.java) {
            analyzeThat(runnableThatFails)
                .runsNumberOfTimes(3)
                .finishesIn(500)
        }

        assertThat(thrown).hasMessageThat().contains("Number of failures: 3")
    }
}
