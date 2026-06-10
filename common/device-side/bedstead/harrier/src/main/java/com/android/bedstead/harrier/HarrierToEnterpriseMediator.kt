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
package com.android.bedstead.harrier

import com.android.bedstead.enterprise.annotations.CanSetPolicyTest
import com.android.bedstead.enterprise.annotations.CannotSetPolicyTest
import com.android.bedstead.enterprise.annotations.PolicyAppliesTest
import com.android.bedstead.enterprise.annotations.PolicyDoesNotApplyTest
import com.android.bedstead.harrier.annotations.PolicyArgument
import java.util.stream.Stream
import org.junit.runners.model.FrameworkMethod

/**
 * Allows to execute bedstead-enterprise methods from Harrier when the module is loaded
 */
interface HarrierToEnterpriseMediator {

    /**
     * Generates extra tests for test functions using
     * [PolicyArgument] annotation in the function parameter
     */
    fun generatePolicyArgumentTests(
        frameworkMethod: FrameworkMethod,
        expandedMethods: Stream<FrameworkMethod>
    ): Stream<FrameworkMethod>

    /**
     * Parse enterprise-specific annotations in [BedsteadJUnit4],
     * i.e. [PolicyAppliesTest], [PolicyDoesNotApplyTest],
     * [CanSetPolicyTest] and [CannotSetPolicyTest]
     * To be used before general annotation processing.
     */
    fun parseEnterpriseAnnotations(annotations: List<Annotation>)

    companion object {
        private const val IMPLEMENTATION =
            "com.android.bedstead.enterprise.HarrierToEnterpriseMediatorImpl"

        private val mediatorInternal: HarrierToEnterpriseMediator? by lazy {
            try {
                Class.forName(
                    IMPLEMENTATION
                ).getDeclaredConstructor().newInstance() as HarrierToEnterpriseMediator
            } catch (ignored: ReflectiveOperationException) {
                null
            }
        }

        /**
         * @return HarrierToEnterpriseMediator or null
         * if the bedstead-enterprise module isn't loaded
         */
        fun getMediatorOrNull(): HarrierToEnterpriseMediator? {
            return mediatorInternal
        }

        /**
         * @return HarrierToEnterpriseMediator or throws [IllegalStateException] when the
         * bedstead-enterprise module isn't loaded
         */
        fun getMediatorOrThrowException(attemptString: String = ""): HarrierToEnterpriseMediator {
            return mediatorInternal ?: throw IllegalStateException(
                "bedstead-enterprise module is not loaded - " +
                        "add it to static_libs to fix it, $attemptString"
            )
        }
    }
}
