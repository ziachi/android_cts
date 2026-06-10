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

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

interface FailureDumper {

    /**
     * Called when a test has failed.
     */
    fun onTestFailed(exception: Throwable) {}

    companion object {
        /**
         * A set of classes which each implement the [FailureDumper] interface - which will be
         * instantiated (via a no-args constructor) and called when a Bedstead test fails.
         */
        val failureDumpers: MutableSet<String> =
            ConcurrentHashMap<String, Boolean>().keySet(true)

        private val currentTestName = AtomicReference("")

        /**
         * Register currently started test name
         */
        fun registerCurrentTest(testName: String) {
            currentTestName.set(testName)
        }

        /**
         * Get the name of the last started test method
         */
        fun getCurrentTestName(): String = currentTestName.get()
    }
}
