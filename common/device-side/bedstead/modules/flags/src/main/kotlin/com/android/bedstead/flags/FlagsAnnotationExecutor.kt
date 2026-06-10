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
package com.android.bedstead.flags

import android.platform.test.flag.junit.DeviceFlagsValueProvider
import com.android.bedstead.flags.annotations.RequireFlagsDisabled
import com.android.bedstead.flags.annotations.RequireFlagsEnabled
import com.android.bedstead.harrier.AnnotationExecutor
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue

/**
 * [AnnotationExecutor] for flags annotations
 */
@Suppress("unused")
class FlagsAnnotationExecutor : AnnotationExecutor {

    private val flagValueProvider = DeviceFlagsValueProvider()

    override fun applyAnnotation(annotation: Annotation) {
        when (annotation) {
            is RequireFlagsEnabled -> requireFlagsEnabled(annotation.value)
            is RequireFlagsDisabled -> requireFlagsDisabled(annotation.value)
        }
    }

    private fun requireFlagsEnabled(flags: Array<out String>) {
        for (flag in flags) {
            assumeTrue(
                String.format("Flag %s required to be enabled, but is disabled", flag),
                flagValueProvider.getBoolean(flag)
            )
        }
    }

    private fun requireFlagsDisabled(flags: Array<out String>) {
        for (flag in flags) {
            assumeFalse(
                String.format("Flag %s required to be disabled, but is enabled", flag),
                flagValueProvider.getBoolean(flag)
            )
        }
    }
}
