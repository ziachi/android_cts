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
package com.android.bedstead.enterprise;

import com.android.bedstead.enterprise.annotations.CanSetPolicyTest;
import com.android.bedstead.enterprise.annotations.CannotSetPolicyTest;
import com.android.bedstead.enterprise.annotations.EnterprisePolicy;
import com.android.bedstead.enterprise.annotations.PolicyAppliesTest;
import com.android.bedstead.enterprise.annotations.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.BedsteadJUnit4;

import com.google.common.collect.ImmutableSet;

import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Parse enterprise-specific annotations.
 */
public final class EnterpriseAnnotationsParser {

    /**
     * Parse enterprise-specific annotations in {@link BedsteadJUnit4},
     * i.e. {@link PolicyAppliesTest}, {@link PolicyDoesNotApplyTest},
     * {@link CanSetPolicyTest} and {@link CannotSetPolicyTest}
     * <p>To be used before general annotation processing.
     */
    public static void parse(List<Annotation> annotations) {
        int index = 0;
        while (index < annotations.size()) {
            Annotation annotation = annotations.get(index);
            if (annotation instanceof PolicyAppliesTest) {
                annotations.remove(index);

                List<Annotation> replacementAnnotations =
                        Policy.policyAppliesStates(
                                unionPolicies(((PolicyAppliesTest) annotation).policy()));
                replacementAnnotations.sort(BedsteadJUnit4::annotationSorter);

                annotations.addAll(index, replacementAnnotations);
                index += replacementAnnotations.size();
            } else if (annotation instanceof PolicyDoesNotApplyTest) {
                annotations.remove(index);

                List<Annotation> replacementAnnotations =
                        Policy.policyDoesNotApplyStates(
                                unionPolicies(((PolicyDoesNotApplyTest) annotation).policy()));
                replacementAnnotations.sort(BedsteadJUnit4::annotationSorter);

                annotations.addAll(index, replacementAnnotations);
                index += replacementAnnotations.size();
            } else if (annotation instanceof CannotSetPolicyTest) {
                annotations.remove(index);

                List<Annotation> replacementAnnotations =
                        Policy.cannotSetPolicyStates(
                                unionPolicies(((CannotSetPolicyTest) annotation).policy()),
                                ((CannotSetPolicyTest) annotation).includeDeviceAdminStates(),
                                ((CannotSetPolicyTest) annotation).includeNonDeviceAdminStates());
                replacementAnnotations.sort(BedsteadJUnit4::annotationSorter);

                annotations.addAll(index, replacementAnnotations);
                index += replacementAnnotations.size();
            } else if (annotation instanceof CanSetPolicyTest) {
                annotations.remove(index);
                boolean singleTestOnly = ((CanSetPolicyTest) annotation).singleTestOnly();

                Class<?>[] policyUnionAnnotations = ((CanSetPolicyTest) annotation).policyUnion();
                Class<?>[] policyIntersectionAnnotations =
                        ((CanSetPolicyTest) annotation).policyIntersection();
                Class<?>[] policyAnnotations = ((CanSetPolicyTest) annotation).policy();

                int numberOfUniquePolicySet = (policyAnnotations.length > 0 ? 1 : 0)
                        + (policyUnionAnnotations.length > 0 ? 1 : 0) + (
                        policyIntersectionAnnotations.length > 0 ? 1 : 0);

                if (numberOfUniquePolicySet != 1) {
                    throw new IllegalStateException("Exactly 1 of policy/policyUnion/policyIntersection must be set");
                }

                EnterprisePolicy enterprisePolicy;

                if (policyAnnotations.length > 0) {
                    enterprisePolicy = unionPolicies(policyAnnotations);
                } else if (policyUnionAnnotations.length > 0) {
                    enterprisePolicy = unionPolicies(policyUnionAnnotations);
                } else {
                    enterprisePolicy = intersectPolicies(policyIntersectionAnnotations);
                }

                List<Annotation> replacementAnnotations =
                        Policy.canSetPolicyStates(enterprisePolicy, singleTestOnly);

                replacementAnnotations.sort(BedsteadJUnit4::annotationSorter);

                annotations.addAll(index, replacementAnnotations);
                index += replacementAnnotations.size();
            } else {
                index++;
            }
        }
    }

    /**
     * Create a new {@link EnterprisePolicy} by merging a group of policies.
     *
     * <p> Example usage
     * Policy1: APPLIED_BY_DEVICE_OWNER | APPLIES_GLOBALLY, APPLIED_BY_PROFILE_OWNER | APPLIES_TO_OWN_USER
     * Policy 2: APPLIED_BY_DEVICE_OWNER | APPLIES_TO_OWN_USER
     * EnterprisePolicy.dpc(): APPLIED_BY_DEVICE_OWNER | APPLIES_GLOBALLY, APPLIED_BY_PROFILE_OWNER | APPLIES_TO_OWN_USER
     *
     * <p>Each policy will have flags validated.
     *
     * <p>If policies support different delegation scopes, then they cannot be merged and an
     * exception will be thrown. These policies require separate tests.
     */
    private static EnterprisePolicy unionPolicies(Class<?>[] policies) {
        if (policies.length == 0) {
            throw new IllegalStateException("Cannot union 0 policies");
        } else if (policies.length == 1) {
            // Nothing to merge, just return the only one
            return policies[0].getAnnotation(EnterprisePolicy.class);
        }

        Set<Integer> dpc = new HashSet<>();
        Set<EnterprisePolicy.Permission> permissions = new HashSet<>();
        Set<EnterprisePolicy.AppOp> appOps = new HashSet<>();
        Set<String> delegatedScopes = new HashSet<>();

        for (Class<?> policy : policies) {
            EnterprisePolicy enterprisePolicy = policy.getAnnotation(EnterprisePolicy.class);
            Policy.validateFlags(policy.getName(), enterprisePolicy.dpc());

            for (int dpcPolicy : enterprisePolicy.dpc()) {
                dpc.add(dpcPolicy);
            }

            for (EnterprisePolicy.Permission permission : enterprisePolicy.permissions()) {
                permissions.add(permission);
            }

            for (EnterprisePolicy.AppOp appOp : enterprisePolicy.appOps()) {
                appOps.add(appOp);
            }

            if (enterprisePolicy.delegatedScopes().length > 0) {
                ImmutableSet<String> newDelegatedScopes = ImmutableSet.copyOf(enterprisePolicy.delegatedScopes());
                if (!delegatedScopes.isEmpty()
                        && !delegatedScopes.containsAll(newDelegatedScopes)) {
                    throw new IllegalStateException("Cannot merge multiple policies which define "
                            + "different delegated scopes. You should separate this into multiple "
                            + "tests.");
                }

                delegatedScopes = newDelegatedScopes;
            }
        }

        return Policy.enterprisePolicy(dpc.stream().mapToInt(Integer::intValue).toArray(),
                permissions.toArray(new EnterprisePolicy.Permission[0]),
                appOps.toArray(new EnterprisePolicy.AppOp[0]),
                delegatedScopes.toArray(new String[0]));

    }

    /**
     * Create a new {@link EnterprisePolicy} with DPC that fulfills all the requirements of all the
     * provided policies.
     *
     * <p> Example usage
     * Policy1: APPLIED_BY_DEVICE_OWNER | APPLIES_GLOBALLY, APPLIED_BY_PROFILE_OWNER |
     * APPLIES_TO_OWN_USER
     * Policy 2: APPLIED_BY_DEVICE_OWNER | APPLIES_TO_OWN_USER
     * EnterprisePolicy.dpc(): APPLIED_BY_DEVICE_OWNER | APPLIES_TO_OWN_USER
     *
     * <p>Each policy will have flags validated.
     */
    private static EnterprisePolicy intersectPolicies(Class<?>[] policies) {
        if (policies.length == 0) {
            throw new IllegalStateException("Cannot intersect 0 policies");
        } else if (policies.length == 1) {
            // Nothing to intersect, just return the only one
            return policies[0].getAnnotation(EnterprisePolicy.class);
        }

        Set<EnterprisePolicy.Permission> permissions = new HashSet<>();
        Set<EnterprisePolicy.AppOp> appOps = new HashSet<>();
        Set<String> delegatedScopes = new HashSet<>();

        int intersectDpc = ~0;

        for (Class<?> policy : policies) {
            EnterprisePolicy enterprisePolicy = policy.getAnnotation(EnterprisePolicy.class);
            Policy.validateFlags(policy.getName(), enterprisePolicy.dpc());

            for (int dpcPolicy : enterprisePolicy.dpc()) {
                intersectDpc &= dpcPolicy;
            }

            // TODO: (b/331606832) support permissions intersection
            for (EnterprisePolicy.Permission permission : enterprisePolicy.permissions()) {
                permissions.add(permission);
            }

            // TODO: (b/341401594) support appOps intersection
            for (EnterprisePolicy.AppOp appOp : enterprisePolicy.appOps()) {
                appOps.add(appOp);
            }

            if (enterprisePolicy.delegatedScopes().length > 0) {
                ImmutableSet<String> newDelegatedScopes = ImmutableSet.copyOf(
                        enterprisePolicy.delegatedScopes());
                if (!delegatedScopes.isEmpty()
                        && !delegatedScopes.containsAll(newDelegatedScopes)) {
                    throw new IllegalStateException(
                            "Cannot intersect multiple policies which define "
                                    + "different delegated scopes. You should separate this into "
                                    + "multiple tests.");
                }

                delegatedScopes = newDelegatedScopes;
            }
        }

        return Policy.enterprisePolicy(new int[]{intersectDpc},
                permissions.toArray(new EnterprisePolicy.Permission[0]),
                appOps.toArray(new EnterprisePolicy.AppOp[0]),
                delegatedScopes.toArray(new String[0]));

    }
}
