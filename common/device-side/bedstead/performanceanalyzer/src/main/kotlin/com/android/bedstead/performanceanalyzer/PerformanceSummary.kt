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

/**
 * A summary of the analysis performed by [PerformanceAnalyzer].
 */
internal class PerformanceSummary private constructor(
    private val expectedTimeInMs: Long,
    private val iterations: Int,
    private val executionTimesUnderExpectedTimePc: Double,
    private val failuresCount: Int,
    private val percentile90: Long,
    private val percentile99: Long
) {
    companion object {
        /**
         * Returns a summary of [PerformanceStats].
         */
        fun of(stats: PerformanceStats): PerformanceSummary {
            return PerformanceSummary(stats.expectedTimeInMs, stats.iterations,
                stats.executionTimesUnderExpectedTimePc, stats.failuresCount, stats.percentile90,
                stats.percentile99)
        }
    }

    override fun toString(): String {
        return "Performance Test Summary: \n" +
                "Number of executions: ${iterations} \n" +
                "Expected time: ${expectedTimeInMs} ms \n" +
                "Expected runtime SLO: ${RUNTIME_SLO}% \n" +
                "% of executions that followed SLO: ${executionTimesUnderExpectedTimePc}% \n" +
                "90th percentile: ${percentile90} ms \n" +
                "99th percentile: ${percentile99} ms \n" +
                "Number of failures: ${failuresCount}"
    }
}
