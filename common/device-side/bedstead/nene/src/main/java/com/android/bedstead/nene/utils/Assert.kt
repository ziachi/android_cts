/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.google.errorprone.annotations.CanIgnoreReturnValue
import org.junit.AssumptionViolatedException

/**
 * Test utilities related to assertions
 */
object Assert {
    /**
     * Assert that a particular exception type is thrown.
     */
    @JvmStatic
    @CanIgnoreReturnValue
    fun <E : Throwable?> assertThrows(message: String? = null,
        exception: Class<E>? = null,
        executable: () -> Unit): E {
        val exceptionType = exception?.toString() ?: "an exception"
        try {
            executable()
            throw AssertionError(message ?: "Expected to throw $exceptionType but nothing thrown")
        } catch (e: Throwable) {
            if (exception?.isInstance(e) == true) {
                return e as E
            }
            throw e
        }
    }

    @JvmStatic
    @CanIgnoreReturnValue
    fun assertThrows(message: String? = null, executable: () -> Unit): Exception =
        assertThrows(message, Exception::class.java, executable)

    // This method is just needed to maintain compatibility with existing Java callers - it should
    // be removed once possible
    @JvmStatic
    @CanIgnoreReturnValue
    fun <E : Throwable?> assertThrows(exception: Class<E>? = null, executable: Runnable): E =
        assertThrows(null, exception) { executable.run() }

    /**
     * Assert that a exception is not thrown.
     */
    @JvmStatic
    @JvmOverloads
    fun assertDoesNotThrow(message: String? = null, executable: () -> Unit) {
        try {
            executable()
        } catch (e: Exception) {
            if (message != null) {
                throw AssumptionViolatedException(message, e)
            }
            throw e
        }
    }
}
