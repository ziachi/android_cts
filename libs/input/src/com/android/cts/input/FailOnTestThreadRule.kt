/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.cts.input

import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import org.junit.rules.TestWatcher
import org.junit.runner.Description

private fun toString(e: Exception): String {
    val sw = StringWriter()
    val pw = PrintWriter(sw)
    pw.write(e.message ?: "<no message>")
    sw.write("\n")
    e.printStackTrace(pw)
    pw.flush()
    return sw.toString()
}

/**
 * A test rule that allows adding failures from non-testing threads.
 *
 * A typical Android test has a test thread and a main thread (and possibly other threads).
 * A failure on the test thread (for example, a failing assertion like "assertEquals" or just
 * calling "fail") will function as expected - the test would fail without additional side effects.
 *
 * However, when such an assertion is used on another thread, for example, on the UI thread, the
 * test would fail. Unfortunately, this would also cause any subsequent test to also fail, which is
 * not desirable.
 *
 * This rule provides a way to add failures from the non-testing threads. The failures added to this
 * rule are asserted at the end of the test run, on the test thread. This allows subsequent tests to
 * run even if the current test fails. All of the failures will be logged, but only the first one
 * will actually be asserted.
 * This is not ideal because the failure is delayed, but is much better than ignoring the failure
 * and simpler than having to pass failures to the testing thread directly from the code.
 */
class FailOnTestThreadRule : TestWatcher() {
    private val failures: BlockingQueue<Exception> = LinkedBlockingQueue()

    companion object {
        private val TAG = "FailOnTestThread"
    }

    /**
     * Add a failure from a non-testing thread. This failure will be reported at the end of the
     * test run.
     * Don't call this from the test thread. It's more optimal to fail directly from the test
     * thread, to ensure that the failure happens sooner.
     */
    fun addFailure(e: Exception) {
        // Log the failure immediately, so that it's easier to debug by having the log closer
        // to the point where the failure actually started
        Log.e(TAG, "Test failed: ${toString(e)}")
        failures.put(e)
    }

    override fun failed(e: Throwable?, description: Description?) {
        for (failure in failures) {
            Log.e(TAG, "Additional failure: $failure")
        }
    }

    override fun finished(description: Description?) {
        failures.forEachIndexed { index, failure ->
            Log.e(TAG, "Failure $index: ${toString(failure)}")
        }
        val failure = failures.peek()
        if (failure != null) {
            throw failure
        }
    }
}
