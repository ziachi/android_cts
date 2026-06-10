/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.bedstead.harrier

import com.android.bedstead.harrier.annotations.FailureMode
import com.google.common.truth.Truth
import org.junit.Assume
import org.junit.AssumptionViolatedException

/**
 * Utilities for use by [AnnotationExecutor] subclasses.
 */
object AnnotationExecutorUtil {
    /**
     * [failOrSkip] if [value] is true.
     */
    @JvmStatic
    fun checkFailOrSkip(message: String?, value: Boolean, failureMode: FailureMode) {
        when (failureMode) {
            FailureMode.FAIL -> Truth.assertWithMessage(message).that(value).isTrue()
            FailureMode.SKIP -> Assume.assumeTrue(message, value)
        }
    }

    /**
     * Either fail or skip the current test depending on the value of `failureMode`.
     */
    @JvmStatic
    fun failOrSkip(message: String?, failureMode: FailureMode) {
        when (failureMode) {
            FailureMode.FAIL -> throw AssertionError(message)
            FailureMode.SKIP -> throw AssumptionViolatedException(message)
        }
    }
}
