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

package com.android.bedstead.enterprise

import com.android.bedstead.enterprise.annotations.canSetPolicyTest
import com.android.bedstead.enterprise.annotations.cannotSetPolicyTest
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnFinancedDeviceOwnerUser
import com.android.bedstead.enterprise.annotations.parameterized.IncludeRunOnParentOfOrganizationOwnedProfileOwnerUsingParentInstance
import com.android.bedstead.enterprise.annotations.policyAppliesTest
import com.android.bedstead.enterprise.annotations.policyDoesNotApplyTest
import com.android.bedstead.enterprise.Policy.includeRunOnAffiliatedDeviceOwnerSecondaryUser
import com.android.bedstead.enterprise.Policy.includeRunOnAffiliatedProfileOwnerAdditionalUser
import com.android.bedstead.enterprise.Policy.includeRunOnCloneProfileAlongsideOrganizationOwnedProfile
import com.android.bedstead.enterprise.Policy.includeRunOnParentOfOrganizationOwnedProfileOwner
import com.android.bedstead.enterprise.Policy.includeRunOnPrivateProfileAlongsideOrganizationOwnedProfile
import com.android.bedstead.enterprise.Policy.includeRunOnProfileOwnerPrimaryUser
import com.android.bedstead.enterprise.Policy.includeRunOnProfileOwnerProfileWithNoDeviceOwner
import com.android.bedstead.enterprise.Policy.includeRunOnSingleDeviceOwnerUser
import com.android.bedstead.enterprise.Policy.includeRunOnSystemDeviceOwnerUser
import com.android.bedstead.enterprise.Policy.includeRunOnUnaffiliatedProfileOwnerAdditionalUser
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DynamicParameterizedAnnotation
import com.android.bedstead.harrier.annotations.parameterized.includeNone
import com.android.bedstead.nene.utils.Assert.assertThrows
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class BedsteadJUnit4AnnotationTest {

    @Test
    fun canSetPolicyTest_policy_returnsUnionParameterizedAnnotations() {
        val parameterizedAnnotations =
            BedsteadJUnit4.getParameterizedAnnotations(
                arrayOf(
                    canSetPolicyTest(
                        policy = arrayOf(
                            AppliedByDeviceOwnerOrProfileOwnerAppliesToOwnUserPolicy::class.java,
                            AppliedByDeviceOwnerAppliesToOwnUserPolicy::class.java
                        )
                    )
                )
            )

        assertThat(parameterizedAnnotations).containsExactly(
            includeRunOnSystemDeviceOwnerUser(),
            includeRunOnSingleDeviceOwnerUser(),
            includeRunOnAffiliatedProfileOwnerAdditionalUser(),
            includeRunOnProfileOwnerPrimaryUser(),
            includeRunOnProfileOwnerProfileWithNoDeviceOwner()
        )
    }

    @Test
    fun canSetPolicyTest_policyUnion_returnsUnionParameterizedAnnotations() {
        val parameterizedAnnotations =
            BedsteadJUnit4.getParameterizedAnnotations(
                arrayOf(
                    canSetPolicyTest(
                        policyUnion = arrayOf(
                            AppliedByDeviceOwnerOrProfileOwnerAppliesToOwnUserPolicy::class.java,
                            AppliedByDeviceOwnerAppliesToOwnUserPolicy::class.java
                        )
                    )
                )
            )

        assertThat(parameterizedAnnotations).containsExactly(
            includeRunOnSystemDeviceOwnerUser(),
            includeRunOnSingleDeviceOwnerUser(),
            includeRunOnAffiliatedProfileOwnerAdditionalUser(),
            includeRunOnProfileOwnerPrimaryUser(),
            includeRunOnProfileOwnerProfileWithNoDeviceOwner()
        )
    }

    @Test
    fun canSetPolicyTest_policyIntersection_returnsIntersectParameterizedAnnotations() {
        val parameterizedAnnotations =
            BedsteadJUnit4.getParameterizedAnnotations(
                arrayOf(
                    canSetPolicyTest(
                        policyIntersection = arrayOf(
                            AppliedByDeviceOwnerOrProfileOwnerAppliesToOwnUserPolicy::class.java,
                            AppliedByAffiliatedProfileOwnerProfileOrSystemDeviceOwnerOrAffiliatedProfileOwnerUserAppliesToParentPolicy::class.java,
                            AppliedByDeviceOwnerAppliesToOwnUserPolicy::class.java
                        )
                    )
                )
            )

        assertThat(parameterizedAnnotations).containsExactly(
            includeRunOnSystemDeviceOwnerUser())

    }

    @Test
    fun canSetPolicyTest_policyIntersection_singlePolicy_returnsIntersectParameterizedAnnotations() {
        val parameterizedAnnotations =
            BedsteadJUnit4.getParameterizedAnnotations(
                arrayOf(
                    canSetPolicyTest(
                        policyIntersection = arrayOf(
                            AppliedByDeviceOwnerOrProfileOwnerAppliesToOwnUserPolicy::class.java
                        )
                    )
                )
            )

        assertThat(parameterizedAnnotations).containsExactly(
            includeRunOnSystemDeviceOwnerUser(),
            includeRunOnSingleDeviceOwnerUser(),
            includeRunOnAffiliatedProfileOwnerAdditionalUser(),
            includeRunOnProfileOwnerPrimaryUser(),
            includeRunOnProfileOwnerProfileWithNoDeviceOwner()
        )

    }

    @Test
    fun canSetPolicyTest_policyIntersection_noIntersectPolicy_returnsIncludeNone() {
        val parameterizedAnnotations =
            BedsteadJUnit4.getParameterizedAnnotations(
                arrayOf(
                    canSetPolicyTest(
                        policyIntersection = arrayOf(
                            AppliedByDeviceOwnerAppliesToOwnUserPolicy::class.java,
                            AppliedByAffiliatedProfileOwnerAppliesToParentPolicy::class.java
                        )
                    )
                )
            )

        assertThat(parameterizedAnnotations).containsExactly(includeNone())
    }

    @Test
    fun canSetPolicyTest_noPolicy_throws() {
        assertThrows(IllegalStateException::class.java) {
            BedsteadJUnit4.getParameterizedAnnotations(arrayOf(canSetPolicyTest()))
        }
    }

    @Test
    fun canSetPolicyTest_multiplePolicy_throws() {
        val policyIntersectPolicies = arrayOf(
            AppliedByDeviceOwnerOrProfileOwnerAppliesToOwnUserPolicy::class.java,
            AppliedByDeviceOwnerAppliesToOwnUserPolicy::class.java
        )

        val policyUnionPolicies =
            arrayOf(
                AppliedByDeviceOwnerOrProfileOwnerAppliesToOwnUserPolicy::class.java,
                AppliedByDeviceOwnerAppliesToOwnUserPolicy::class.java
            )

        assertThrows(IllegalStateException::class.java) {
            BedsteadJUnit4.getParameterizedAnnotations(
                arrayOf(
                    canSetPolicyTest(
                        policyIntersection = policyIntersectPolicies,
                        policyUnion = policyUnionPolicies
                    )
                )
            )
        }
    }

    @Test
    fun policyAppliesTest_hasPolicy_returnsParameterizedAnnotations() {
        val parameterizedAnnotations =
            BedsteadJUnit4.getParameterizedAnnotations(
                arrayOf(
                    policyAppliesTest(
                        policy = arrayOf(
                            AppliedByDeviceOwnerOrProfileOwnerAppliesToOwnUserPolicy::class.java,
                            AppliedByDeviceOwnerAppliesToOwnUserPolicy::class.java
                        )
                    )
                )
            )

        assertThat(parameterizedAnnotations).containsExactly(
            includeRunOnSystemDeviceOwnerUser(),
            includeRunOnSingleDeviceOwnerUser(),
            includeRunOnUnaffiliatedProfileOwnerAdditionalUser(),
            includeRunOnProfileOwnerPrimaryUser(),
            includeRunOnProfileOwnerProfileWithNoDeviceOwner()
        )
    }

    @Test
    fun policyAppliesTest_hasNoPolicy_returnsNoAnnotations() {
        assertThrows(IllegalStateException::class.java) {
            BedsteadJUnit4.getParameterizedAnnotations(arrayOf(policyAppliesTest(policy = arrayOf())))
        }
    }

    @Test
    fun policyDoesNotApplyTest_hasPolicy_returnsParameterizedAnnotations() {
        val parameterizedAnnotations =
            BedsteadJUnit4.getParameterizedAnnotations(
                arrayOf(
                    policyDoesNotApplyTest(
                        policy = arrayOf(
                            AppliedByDeviceOwnerOrProfileOwnerAppliesToOwnUserPolicy::class.java,
                            AppliedByDeviceOwnerAppliesToOwnUserPolicy::class.java
                        )
                    )
                )
            )

        assertThat(parameterizedAnnotations).containsExactly(
            includeRunOnAffiliatedDeviceOwnerSecondaryUser(),
            includeRunOnCloneProfileAlongsideOrganizationOwnedProfile(),
            includeRunOnPrivateProfileAlongsideOrganizationOwnedProfile(),
            includeRunOnParentOfOrganizationOwnedProfileOwner()
        )
    }

    @Test
    fun policyDoesNotApplyTest_hasNoPolicy_throws() {
        assertThrows(IllegalStateException::class.java) {
            BedsteadJUnit4.getParameterizedAnnotations(arrayOf(policyDoesNotApplyTest(policy = arrayOf())))
        }
    }

    @Test
    fun cannotSetPolicyTest_hasPolicy_returnsParameterizedAnnotations() {
        val parameterizedAnnotations =
            BedsteadJUnit4.getParameterizedAnnotations(
                arrayOf(
                    cannotSetPolicyTest(
                        policy = arrayOf(
                            AppliedByDeviceOwnerOrProfileOwnerAppliesToOwnUserPolicy::class.java,
                            AppliedByDeviceOwnerAppliesToOwnUserPolicy::class.java
                        )
                    )
                )
            )

        assertThat(parameterizedAnnotations.size).isEqualTo(3)

        val expectedAnnotationTypes = arrayOf(
            IncludeRunOnParentOfOrganizationOwnedProfileOwnerUsingParentInstance::class.java,
            IncludeRunOnFinancedDeviceOwnerUser::class.java,
            DynamicParameterizedAnnotation::class.java
        )

        for (type in expectedAnnotationTypes) {
            val containsType = parameterizedAnnotations.stream().anyMatch { type.isInstance(it) }
            assertThat(containsType).isTrue()
        }
    }

    @Test
    fun cannotSetPolicyTest_hasNoPolicy_throws() {
        assertThrows(IllegalStateException::class.java) {
            BedsteadJUnit4.getParameterizedAnnotations(arrayOf(cannotSetPolicyTest(policy = arrayOf())))
        }
    }
}
