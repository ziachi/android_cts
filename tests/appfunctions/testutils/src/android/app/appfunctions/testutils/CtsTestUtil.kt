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

package android.app.appfunctions.testutils

import com.android.bedstead.nene.TestApis.permissions
import kotlinx.coroutines.delay
import org.junit.AssumptionViolatedException

/** Contains testing utilities related to AppFunction's Sidecar library. */
object CtsTestUtil {
    /** Runs a block with shell permissions. */
    suspend fun runWithShellPermission(vararg permissions: String, block: suspend () -> Unit) {
        permissions().withPermission(*permissions).use { block() }
    }

    fun interface ThrowRunnable {
        @Throws(Throwable::class) suspend fun run()
    }

    /** Retries an assertion with a delay between attempts. */
    @Throws(Throwable::class)
    suspend fun retryAssert(runnable: ThrowRunnable) {
        var lastError: Throwable? = null

        for (attempt in 0 until RETRY_MAX_INTERVALS) {
            try {
                runnable.run()
                return
            } catch (e: Throwable) {
                lastError = e
                delay(RETRY_CHECK_INTERVAL_MILLIS)
            }
        }
        throw lastError!!
    }

    private const val RETRY_CHECK_INTERVAL_MILLIS: Long = 500
    private const val RETRY_MAX_INTERVALS: Long = 10
}
