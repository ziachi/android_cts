/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.bedstead.enterprise.annotations

import com.android.bedstead.harrier.annotations.AnnotationPriorityRunPrecedence
import com.android.bedstead.harrier.annotations.meta.RequiresBedsteadJUnit4
import com.google.auto.value.AutoAnnotation
import kotlin.reflect.KClass

/**
 * Mark a test as testing the states where a policy is not allowed to be applied.
 *
 *
 * An example is running with a device owner for a policy only applicable to profile owners.
 *
 *
 * This will generate parameterized runs for all matching states.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(
    AnnotationRetention.RUNTIME)
@RequiresBedsteadJUnit4
annotation class CannotSetPolicyTest(
    /**
     * The policy being tested.
     *
     *
     * If multiple policies are specified, then they will be merged so that all valid states for
     * all specified policies are considered as valid.
     *
     *
     * This is used to calculate which states are required to be tested.
     */
    val policy: Array<KClass<*>>,
    /**
     * If true, then this will run in states where the app is a device admin but is not one which is
     * allowed to make the call.
     */
    val includeDeviceAdminStates: Boolean = true,
    /**
     * If true, then this will run in states where the app is not a device admin.
     */
    val includeNonDeviceAdminStates: Boolean = true,
    /**
     * Priority sets the order that annotations will be resolved.
     *
     *
     * Annotations with a lower priority will be resolved before annotations with a higher
     * priority.
     *
     *
     * If there is an order requirement between annotations, ensure that the priority of the
     * annotation which must be resolved first is lower than the one which must be resolved later.
     *
     *
     * Priority can be set to a [AnnotationPriorityRunPrecedence] constant, or to any [int].
     */
    val priority: Int = AnnotationPriorityRunPrecedence.PRECEDENCE_NOT_IMPORTANT)

@AutoAnnotation
fun cannotSetPolicyTest(
    policy: Array<Class<*>>,
    includeDeviceAdminStates: Boolean = true,
    includeNonDeviceAdminStates: Boolean = true
): CannotSetPolicyTest {
    return AutoAnnotation_CannotSetPolicyTestKt_cannotSetPolicyTest(policy,
        includeDeviceAdminStates,
        includeNonDeviceAdminStates)
}