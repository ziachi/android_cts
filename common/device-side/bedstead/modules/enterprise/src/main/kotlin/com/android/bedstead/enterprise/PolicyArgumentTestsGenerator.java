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
import com.android.bedstead.enterprise.annotations.PolicyAppliesTest;
import com.android.bedstead.enterprise.annotations.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.BedsteadFrameworkMethod;
import com.android.bedstead.harrier.FrameworkMethodWithParameter;
import com.android.bedstead.harrier.annotations.PolicyArgument;
import com.android.bedstead.nene.exceptions.NeneException;

import org.junit.runners.model.FrameworkMethod;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generate policy argument tests
 */
public final class PolicyArgumentTestsGenerator {

    /**
     * Generates extra tests for test functions using
     * {@link PolicyArgument} annotation in the function parameter
     */
    public static Stream<FrameworkMethod> generate(
            FrameworkMethod frameworkMethod, Stream<FrameworkMethod> expandedMethods) {
        try {
            Class<?>[] policyClasses;
            PolicyAppliesTest policyAppliesTestAnnotation =
                    frameworkMethod.getMethod().getAnnotation(PolicyAppliesTest.class);
            PolicyDoesNotApplyTest policyDoesNotApplyTestAnnotation =
                    frameworkMethod.getMethod().getAnnotation(PolicyDoesNotApplyTest.class);
            CanSetPolicyTest canSetPolicyTestAnnotation =
                    frameworkMethod.getMethod().getAnnotation(CanSetPolicyTest.class);
            CannotSetPolicyTest cannotSetPolicyTestAnnotation =
                    frameworkMethod.getMethod().getAnnotation(CannotSetPolicyTest.class);

            if (policyAppliesTestAnnotation != null) {
                policyClasses = policyAppliesTestAnnotation.policy();
            } else if (policyDoesNotApplyTestAnnotation != null) {
                policyClasses = policyDoesNotApplyTestAnnotation.policy();
            } else if (canSetPolicyTestAnnotation != null) {
                policyClasses = canSetPolicyTestAnnotation.policy();
            } else if (cannotSetPolicyTestAnnotation != null) {
                policyClasses = cannotSetPolicyTestAnnotation.policy();
            } else {
                throw new NeneException("PolicyArgument annotation can only by used with a test "
                        + "that is marked with either @PolicyAppliesTest, "
                        + "@PolicyDoesNotApplyTest, @CanSetPolicyTest or @CannotSetPolicyTest.");
            }

            Map<String, Set<Annotation>> policyClassToAnnotationsMap =
                    Policy.getAnnotationsForPolicies(
                            Policy.getEnterprisePolicyWithCallingClass(policyClasses));

            List<FrameworkMethod> expandedMethodList = expandedMethods.collect(Collectors.toList());
            Set<FrameworkMethod> tempExpandedFrameworkMethodSet = new HashSet<>();
            for (Class<?> policyClass : policyClasses) {
                Method validArgumentsMethod = policyClass.getDeclaredMethod("validArguments");
                Set<?> validArguments =
                        (Set<?>) validArgumentsMethod.invoke(policyClass.newInstance());
                if (validArguments.isEmpty()) {
                    throw new NeneException(
                            "Empty valid arguments passed for "
                                    + policyClass.getSimpleName()
                                    + " policy");
                }

                Set<Annotation> policyAnnotations =
                        policyClassToAnnotationsMap.get(policyClass.getName());

                for (FrameworkMethod expandedMethod : expandedMethodList) {
                    List<Annotation> parameterizedAnnotations =
                            ((BedsteadFrameworkMethod) expandedMethod)
                                    .getParameterizedAnnotations();
                    for (Annotation parameterizedAnnotation : parameterizedAnnotations) {
                        if ((policyAppliesTestAnnotation != null
                                && policyAnnotations.contains((parameterizedAnnotation)))
                                || (policyDoesNotApplyTestAnnotation != null)
                                || (canSetPolicyTestAnnotation != null
                                && policyAnnotations.contains(parameterizedAnnotation))
                                || (cannotSetPolicyTestAnnotation != null)) {
                            tempExpandedFrameworkMethodSet.addAll(
                                    applyPolicyArgumentParameter(expandedMethod, validArguments));
                        }
                    }
                }
            }

            return tempExpandedFrameworkMethodSet.stream();
        } catch (NoSuchMethodException
                 | InvocationTargetException
                 | IllegalAccessException
                 | InstantiationException e) {
            // Should never happen as validArguments method will always have a default
            // implementation for every EnterprisePolicy
            throw new NeneException(
                    "PolicyArgument parameter annotation cannot be added to a test "
                            + "without the validArguments method specified for the "
                            + "EnterprisePolicy",
                    e);
        }
    }

    private static List<FrameworkMethodWithParameter> applyPolicyArgumentParameter(
            FrameworkMethod frameworkMethod, Set<?> validArguments) {
        List<FrameworkMethodWithParameter> expandedMethods = new ArrayList<>(validArguments.size());
        for (Object arg : validArguments) {
            expandedMethods.add(new FrameworkMethodWithParameter(frameworkMethod, arg));
        }
        return expandedMethods;
    }
}
