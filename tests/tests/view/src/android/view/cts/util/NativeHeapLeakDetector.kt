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
package android.view.cts.util

import android.os.Debug
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert

/**
 * Helper class to detect native heap memory leaks in tests.
 * Example usage:
 *     ... Allocate any memory required for test
 *     try (NativeHeapLeakDetector d = new NativeHeapLeakDetector()) {
 *         ... code to be tested for memory leak ...
 *     }
 * Note: This only reports leaks larger then MEMORY_LEAK_THRESHOLD_KB (35 KB). Its recommended to
 * run the code to be tested for memory leak sufficient times for the leak to large enough to be
 * detected.
 */
class NativeHeapLeakDetector : AutoCloseable {
    private val mInitialNativeHeapUsage: Long

    init {
        runGcAndFinalizersSync()
        mInitialNativeHeapUsage = Debug.getNativeHeapAllocatedSize()
    }

    /**
     * MemoryInfo values are not always precise and reliable, to prevent the test from flake we are
     * using a arbitrary limit of MEMORY_LEAK_THRESHOLD_KB (35 KB) over multiple iterations. It
     * should still allow us to catch major memory leaks avoiding flakiness in the test.
     */
    override fun close() {
        runGcAndFinalizersSync()
        val leakedNativeHeapKB =
                (Debug.getNativeHeapAllocatedSize() - mInitialNativeHeapUsage) / 1024
        Assert.assertFalse(
                "Possible Memory leak. Leaked native memory usage: $leakedNativeHeapKB KB",
                leakedNativeHeapKB > MEMORY_LEAK_THRESHOLD_KB // KB
        )
    }

    companion object {
        const val MEMORY_LEAK_THRESHOLD_KB = 35
        private fun runGcAndFinalizersSync() {
            // This is a simple way to wait for all finalizers to run.
            // It is not guaranteed to work, but it is sufficient for our purposes.
            val fence = CountDownLatch(1)
            object : Any() {
                protected fun finalize() {
                    fence.countDown()
                }
            }
            try {
                do {
                    Runtime.getRuntime().gc()
                    Runtime.getRuntime().runFinalization()
                } while (!fence.await(100, TimeUnit.MILLISECONDS))
            } catch (ex: InterruptedException) {
                throw RuntimeException(ex)
            }
        }
    }
}
