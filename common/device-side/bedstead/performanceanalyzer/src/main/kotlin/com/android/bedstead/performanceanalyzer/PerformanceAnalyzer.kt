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

import android.util.Log
import com.android.bedstead.performanceanalyzer.exceptions.PerformanceTestFailedException
import kotlin.math.round
import kotlin.time.measureTime

/**
 * A helper class to analyze performance of a runnable.
 *
 * Example usage:
 * ```
 * assertThat(
 *             analyzeThat(runnable)
 *                 .cleanUpUsing(runnable)
 *                 .runsNumberOfTimes(100)
 *                 .finishesIn(2000))
 *             .isTrue()
 * ```
 */
class PerformanceAnalyzer
private constructor(
    private var subjectRunnable: Runnable
) {
    private var cleanUpRunnable: Runnable? = null
    private var iterations: Int = 1

    companion object {
        /**
         * Entry point to the [PerformanceAnalyzer]. Takes the [subjectRunnable] under review as an
         * argument.
         */
        @JvmStatic
        fun analyzeThat(subjectRunnable: Runnable): PerformanceAnalyzer {
            return PerformanceAnalyzer(subjectRunnable)
        }

        private const val LOG_TAG = "PerformanceAnalyzer"
    }

    /**
     * Specify the number of times the [subjectRunnable] is supposed to run before we analyze its
     * performance.
     *
     * Default value: 1.
     */
    fun runsNumberOfTimes(times: Int): PerformanceAnalyzer {
        iterations = times
        return this
    }

    /**
     * Checks that [subjectRunnable] under review finishes in [expectedTimeInMs],
     * throws [PerformanceTestFailedException] with the summary report otherwise.
     *
     * In case the performance test is passed, the summary report can be found in the logcat
     * file.
     *
     * The [subjectRunnable] is executed the specified number of times using [runsNumberOfTimes()],
     * default being 1.
     *
     * The [cleanUpRunnable] specified using [cleanUpUsing()] is also invoked after every execution
     * but its execution time is not analyzed.
     */
    fun finishesIn(expectedTimeInMs: Long): Boolean {
        val stats = analyze(expectedTimeInMs)
        val summary = PerformanceSummary.of(stats)

        if (stats.executionTimesUnderExpectedTimePc < RUNTIME_SLO) {
            throw PerformanceTestFailedException(summary.toString())
        }

        // Log summary into logcat.
        Log.i(LOG_TAG, summary.toString())

        return true
    }

    /**
     * Use this to specify a runnable action that is required to clean up the resources initialized
     * by [subjectRunnable].
     * The runnable would be executed once after every execution of [subjectRunnable].
     */
    fun cleanUpUsing(cleanUpRunnable: Runnable): PerformanceAnalyzer {
        this.cleanUpRunnable = cleanUpRunnable
        return this
    }

    private fun analyze(expectedTimeInMs: Long): PerformanceStats {
        val executionTimes = mutableListOf<Long>()
        var failuresCount = 0

        for (i in 1..iterations) {
            try {
                val executionTime = measureTime {
                    subjectRunnable.run()
                }
                executionTimes.add(executionTime.inWholeMilliseconds)
            } catch (e: Throwable) {
                failuresCount++
            } finally {
                runCleanup()
            }
        }

        return stats(expectedTimeInMs, executionTimes, failuresCount)
    }

    private fun stats(expectedTimeInMs: Long,
                      executionTimes: List<Long>,
                      failuresCount: Int): PerformanceStats {
        val executionTimesUnderExpectedTime = executionTimes.count { it <= expectedTimeInMs }
        val executionTimesUnderExpectedTimePc =
            executionTimesUnderExpectedTime.toDouble() / iterations.toDouble() * 100

        val executionTimesSorted = executionTimes.sorted()
        val successfulExecutions = executionTimes.count()
        var percentile90 = 0L
        var percentile99 = 0L
        if (successfulExecutions > 0) {
            percentile90 =
                executionTimesSorted[(successfulExecutions * 0.9).toInt()].roundToNearestHundred()
            percentile99 =
                executionTimesSorted[(successfulExecutions * 0.99).toInt()].roundToNearestHundred()
        }

        return PerformanceStats(expectedTimeInMs, iterations, executionTimesUnderExpectedTimePc,
            failuresCount, percentile90, percentile99)
    }

    private fun runCleanup() {
        try {
            cleanUpRunnable?.run()
        } catch (e: Throwable) {
            throw RuntimeException("The cleanup function failed", e)
        }
    }

    private fun Long.roundToNearestHundred(): Long {
        return (round(this / 100.0) * 100).toLong()
    }
}
