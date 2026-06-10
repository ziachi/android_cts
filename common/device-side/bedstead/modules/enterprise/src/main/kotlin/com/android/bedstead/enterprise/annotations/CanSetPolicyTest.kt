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
 * Mark a test as testing the states where a policy is allowed to be applied.
 *
 *
 * This will generate parameterized runs for all matching states. Tests will only be run on
 * the same user as the DPC. If you wish to test that a policy applies across all relevant states,
 * use [PolicyAppliesTest].
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(
    AnnotationRetention.RUNTIME)
@RequiresBedsteadJUnit4
annotation class CanSetPolicyTest(
    /**
     * The policy being tested.
     *
     * Setting policy will run tests in all states where the caller has access to either of the policies specified
     *
     * If multiple policies are specified, then they will be merged so that all valid states for
     * all specified policies are considered as valid.
     *
     * Same as [policyUnion].
     *
     * <p> Example usage
     * Policy1: APPLIED_BY_DEVICE_OWNER | APPLIES_GLOBALLY, APPLIED_BY_PROFILE_OWNER | APPLIES_TO_OWN_USER
     * Policy 2: APPLIED_BY_DEVICE_OWNER | APPLIES_TO_OWN_USER
     * policy: APPLIED_BY_DEVICE_OWNER | APPLIES_GLOBALLY, APPLIED_BY_PROFILE_OWNER | APPLIES_TO_OWN_USER
     *
     * Only 1 of policy/policyUnion/policyIntersection must be set at a time
     *
     * This is used to calculate which states are required to be tested.
     */
    val policy: Array<KClass<*>> = [],
    /**
     * The policy being tested.
     *
     * Setting policyUnion will run tests in all states where the caller has access to either of the policies specified
     *
     * If multiple policies are specified, then they will be merged so that all valid states for
     * all specified policies are considered as valid.
     *
     * Same as [policy].
     *
     * <p> Example usage
     * Policy1: APPLIED_BY_DEVICE_OWNER | APPLIES_GLOBALLY, APPLIED_BY_PROFILE_OWNER | APPLIES_TO_OWN_USER
     * Policy 2: APPLIED_BY_DEVICE_OWNER | APPLIES_TO_OWN_USER
     * policyUnion: APPLIED_BY_DEVICE_OWNER | APPLIES_GLOBALLY, APPLIED_BY_PROFILE_OWNER | APPLIES_TO_OWN_USER
     *
     * Only 1 of policy/policyUnion/policyIntersection must be set at a time
     *
     * This is used to calculate which states are required to be tested.
     */
    val policyUnion: Array<KClass<*>> = [],
    /**
     * The policy being tested.
     *
     * Setting policyIntersection will run tests in all states where the caller has access to all of the policies specified
     *
     * If multiple policies are specified, then they will be filtered so that only states that
     * apply to all specified policies are considered as valid.
     *
     * <p> Example usage
     * Policy1: APPLIED_BY_DEVICE_OWNER | APPLIES_GLOBALLY, APPLIED_BY_PROFILE_OWNER | APPLIES_TO_OWN_USER
     * Policy 2: APPLIED_BY_DEVICE_OWNER | APPLIES_TO_OWN_USER
     * policyIntersection: APPLIED_BY_DEVICE_OWNER | APPLIES_TO_OWN_USER
     *
     * Only 1 of policy/policyUnion/policyIntersection must be set at a time
     *
     * This is used to calculate which states are required to be tested.
     */
    val policyIntersection: Array<KClass<*>> = [],
    /**
     * If true, this test will only be run in a single state.
     *
     *
     * This is useful for tests of invalid inputs, where running in multiple states is unlikely
     * to add meaningful coverage.
     *
     * By default, all states where the policy can be set will be included.
     */
    val singleTestOnly: Boolean = false,
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
     * Priority can be set to a [AnnotationPriorityRunPrecedence] constant, or to any [Int].
     */
    val priority: Int = AnnotationPriorityRunPrecedence.PRECEDENCE_NOT_IMPORTANT)

@AutoAnnotation
fun canSetPolicyTest(
    policy: Array<Class<*>>? = emptyArray(),
    policyUnion: Array<Class<*>>? = emptyArray(),
    policyIntersection: Array<Class<*>>? = emptyArray()
): CanSetPolicyTest {
    return AutoAnnotation_CanSetPolicyTestKt_canSetPolicyTest(policy,
        policyUnion,
        policyIntersection)
}