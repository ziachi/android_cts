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

package com.android.bedstead.harrier;

import static com.android.bedstead.permissions.annotations.EnsureDoesNotHavePermissionKt.ensureDoesNotHavePermission;
import static com.android.bedstead.permissions.annotations.EnsureHasPermissionKt.ensureHasPermission;

import com.android.bedstead.enterprise.annotations.CanSetPolicyTest;
import com.android.bedstead.enterprise.annotations.CannotSetPolicyTest;
import com.android.bedstead.enterprise.annotations.EnsureHasWorkProfile;
import com.android.bedstead.enterprise.annotations.MostImportantCoexistenceTest;
import com.android.bedstead.enterprise.annotations.MostRestrictiveCoexistenceTest;
import com.android.bedstead.enterprise.annotations.PolicyAppliesTest;
import com.android.bedstead.enterprise.annotations.PolicyDoesNotApplyTest;
import com.android.bedstead.enterprise.annotations.RequireRunOnWorkProfile;
import com.android.bedstead.harrier.annotations.AnnotationCostRunPrecedence;
import com.android.bedstead.harrier.annotations.AnnotationPriorityRunPrecedence;
import com.android.bedstead.harrier.annotations.CrossUserTest;
import com.android.bedstead.harrier.annotations.EnumTestParameter;
import com.android.bedstead.harrier.annotations.HiddenApiTest;
import com.android.bedstead.harrier.annotations.IntTestParameter;
import com.android.bedstead.harrier.annotations.PermissionTest;
import com.android.bedstead.harrier.annotations.PolicyArgument;
import com.android.bedstead.harrier.annotations.RequireRunOnInitialUser;
import com.android.bedstead.harrier.annotations.StringTestParameter;
import com.android.bedstead.harrier.annotations.UserPair;
import com.android.bedstead.harrier.annotations.UserTest;
import com.android.bedstead.harrier.annotations.meta.ParameterizedAnnotation;
import com.android.bedstead.harrier.annotations.meta.RepeatingAnnotation;
import com.android.bedstead.harrier.annotations.parameterized.IncludeNone;
import com.android.bedstead.harrier.exceptions.RestartTestException;
import com.android.bedstead.multiuser.annotations.EnsureHasAdditionalUser;
import com.android.bedstead.multiuser.annotations.EnsureHasCloneProfile;
import com.android.bedstead.multiuser.annotations.EnsureHasPrivateProfile;
import com.android.bedstead.multiuser.annotations.EnsureHasSecondaryUser;
import com.android.bedstead.multiuser.annotations.EnsureHasTvProfile;
import com.android.bedstead.multiuser.annotations.OtherUser;
import com.android.bedstead.multiuser.annotations.RequireNotHeadlessSystemUserMode;
import com.android.bedstead.multiuser.annotations.RequireRunOnAdditionalUser;
import com.android.bedstead.multiuser.annotations.RequireRunOnCloneProfile;
import com.android.bedstead.multiuser.annotations.RequireRunOnPrimaryUser;
import com.android.bedstead.multiuser.annotations.RequireRunOnPrivateProfile;
import com.android.bedstead.multiuser.annotations.RequireRunOnSecondaryUser;
import com.android.bedstead.multiuser.annotations.RequireRunOnSystemUser;
import com.android.bedstead.multiuser.annotations.RequireRunOnTvProfile;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.types.OptionalBoolean;
import com.android.bedstead.performanceanalyzer.annotations.PerformanceTest;
import com.android.queryable.annotations.Query;

import com.google.auto.value.AutoAnnotation;
import com.google.common.collect.ImmutableMap;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestClass;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A JUnit test runner for use with Bedstead.
 */
// Annotating this class with @Query as a workaround to add this as a data type to a field
// in annotations that are called upon by @AutoAnnotation (for e.g. EnsureHasWorkProfile).
// @AutoAnnotation is not able to set default value for a field with an annotated data type,
// so we try to pass the default value explicitly that is accessed via reflection through this
// class.
@SuppressWarnings("AndroidJdkLibsChecker")
@Query
public final class BedsteadJUnit4 extends BlockJUnit4ClassRunner {

    private static final Set<TestLifecycleListener> sLifecycleListeners = new HashSet<>();

    private static final Map<Annotation, Integer> ANNOTATION_COST_CACHE = new HashMap<>();
    private static final Map<Annotation, Integer> ANNOTATION_PRIORITY_CACHE = new HashMap<>();

    private static final String LOG_TAG = "BedsteadJUnit4";
    private boolean mHasManualHarrierRule = false;

    @AutoAnnotation
    private static RequireRunOnSystemUser requireRunOnSystemUser() {
        return new AutoAnnotation_BedsteadJUnit4_requireRunOnSystemUser();
    }

    private static RequireRunOnPrimaryUser requireRunOnPrimaryUser() {
        return requireRunOnPrimaryUser(OptionalBoolean.ANY);
    }

    @AutoAnnotation
    private static RequireRunOnPrimaryUser requireRunOnPrimaryUser(OptionalBoolean switchedToUser) {
        return new AutoAnnotation_BedsteadJUnit4_requireRunOnPrimaryUser(switchedToUser);
    }

    private static RequireRunOnSecondaryUser requireRunOnSecondaryUser() {
        return requireRunOnSecondaryUser(OptionalBoolean.ANY);
    }

    @AutoAnnotation
    private static RequireRunOnSecondaryUser requireRunOnSecondaryUser(
            OptionalBoolean switchedToUser) {
        return new AutoAnnotation_BedsteadJUnit4_requireRunOnSecondaryUser(switchedToUser);
    }

    @AutoAnnotation
    private static RequireRunOnAdditionalUser requireRunOnAdditionalUser() {
        return new AutoAnnotation_BedsteadJUnit4_requireRunOnAdditionalUser();
    }

    @AutoAnnotation
    private static RequireRunOnWorkProfile requireRunOnWorkProfile(Query dpc) {
        return new AutoAnnotation_BedsteadJUnit4_requireRunOnWorkProfile(dpc);
    }

    @AutoAnnotation
    private static RequireRunOnTvProfile requireRunOnTvProfile() {
        return new AutoAnnotation_BedsteadJUnit4_requireRunOnTvProfile();
    }

    @AutoAnnotation
    private static RequireRunOnCloneProfile requireRunOnCloneProfile() {
        return new AutoAnnotation_BedsteadJUnit4_requireRunOnCloneProfile();
    }

    @AutoAnnotation
    private static RequireRunOnPrivateProfile requireRunOnPrivateProfile() {
        return new AutoAnnotation_BedsteadJUnit4_requireRunOnPrivateProfile();
    }

    @AutoAnnotation
    static RequireRunOnInitialUser requireRunOnInitialUser(OptionalBoolean switchedToUser) {
        return new AutoAnnotation_BedsteadJUnit4_requireRunOnInitialUser(switchedToUser);
    }

    static RequireRunOnInitialUser requireRunOnInitialUser() {
        return requireRunOnInitialUser(OptionalBoolean.TRUE);
    }

    @AutoAnnotation
    private static EnsureHasSecondaryUser ensureHasSecondaryUser() {
        return new AutoAnnotation_BedsteadJUnit4_ensureHasSecondaryUser();
    }

    @AutoAnnotation
    private static EnsureHasAdditionalUser ensureHasAdditionalUser() {
        return new AutoAnnotation_BedsteadJUnit4_ensureHasAdditionalUser();
    }

    @AutoAnnotation
    private static EnsureHasWorkProfile ensureHasWorkProfile(Query dpc) {
        return new AutoAnnotation_BedsteadJUnit4_ensureHasWorkProfile(dpc);
    }

    @AutoAnnotation
    private static EnsureHasTvProfile ensureHasTvProfile() {
        return new AutoAnnotation_BedsteadJUnit4_ensureHasTvProfile();
    }

    @AutoAnnotation
    private static EnsureHasCloneProfile ensureHasCloneProfile() {
        return new AutoAnnotation_BedsteadJUnit4_ensureHasCloneProfile();
    }

    @AutoAnnotation
    private static EnsureHasPrivateProfile ensureHasPrivateProfile() {
        return new AutoAnnotation_BedsteadJUnit4_ensureHasPrivateProfile();
    }

    @AutoAnnotation
    private static OtherUser otherUser(UserType value) {
        return new AutoAnnotation_BedsteadJUnit4_otherUser(value);
    }

    @AutoAnnotation
    private static RequireNotHeadlessSystemUserMode requireNotHeadlessSystemUserMode(String reason) {
        return new AutoAnnotation_BedsteadJUnit4_requireNotHeadlessSystemUserMode(reason);
    }

    // Get @Query annotation via BedsteadJunit4 class as a workaround to enable adding Query
    // fields to annotations that rely on @AutoAnnotation (for e.g. @EnsureHasWorkProfile)
    private static Query query() {
        try {
            return Class.forName("com.android.bedstead.harrier.BedsteadJUnit4")
                    .getAnnotation(Query.class);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(
                    "Unable to get BedsteadJunit4 class when trying to get "
                            + "@Query annotation", e);
        }
    }


    // These are annotations which are not included indirectly
    private static final Set<String> sIgnoredAnnotationPackages = new HashSet<>();

    static {
        sIgnoredAnnotationPackages.add("java.lang.annotation");
        sIgnoredAnnotationPackages.add("com.android.bedstead.harrier.annotations.meta");
        sIgnoredAnnotationPackages.add("kotlin.*");
        sIgnoredAnnotationPackages.add("org.junit");
    }

    /**
     * Annotation sorter using the priority method added to an annotation,
     * higher priority numbers are earlier in the list, if a priority is not provided
     * {@link AnnotationPriorityRunPrecedence#PRECEDENCE_NOT_IMPORTANT} will be used
     */
    public static int annotationSorter(Annotation a, Annotation b) {
        return getAnnotationPriority(a) - getAnnotationPriority(b);
    }

    private static int getAnnotationCost(Annotation annotation) {
        return ANNOTATION_COST_CACHE.computeIfAbsent(
                annotation, BedsteadJUnit4::computeAnnotationCost);
    }

    private static int getAnnotationPriority(Annotation annotation) {
        return ANNOTATION_PRIORITY_CACHE.computeIfAbsent(
                annotation, BedsteadJUnit4::computeAnnotationPriority);
    }

    private static int computeAnnotationCost(Annotation annotation) {
        try {
            return (int) annotation.annotationType().getMethod("cost").invoke(annotation);
        } catch (NoSuchMethodException e) {
            // Default to MIDDLE if no cost is found on the annotation.
            return AnnotationCostRunPrecedence.MIDDLE;
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new NeneException("Failed to invoke cost on this annotation: " + annotation, e);
        }
    }

    private static int computeAnnotationPriority(Annotation annotation) {
        if (annotation instanceof DynamicParameterizedAnnotation) {
            // Special case, not important
            return AnnotationPriorityRunPrecedence.PRECEDENCE_NOT_IMPORTANT;
        }

        try {
            return (int) annotation.annotationType().getMethod("priority").invoke(annotation);
        } catch (NoSuchMethodException e) {
            // Default to PRECEDENCE_NOT_IMPORTANT if no priority is found on the annotation.
            return AnnotationPriorityRunPrecedence.PRECEDENCE_NOT_IMPORTANT;
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new NeneException(
                    "Failed to invoke priority on this annotation: " + annotation, e);
        }
    }

    static String getParameterName(Annotation annotation) {
        if (annotation instanceof DynamicParameterizedAnnotation) {
            return ((DynamicParameterizedAnnotation) annotation).name();
        }
        return annotation.annotationType().getSimpleName();
    }

    /**
     * Resolves annotations recursively.
     *
     * @param parameterizedAnnotations The class of the parameterized annotations to expand, if any
     */
    public void resolveRecursiveAnnotations(
            List<Annotation> annotations, List<Annotation> parameterizedAnnotations) {
        resolveRecursiveAnnotations(getHarrierRule(), annotations, parameterizedAnnotations);
    }

    /**
     * Resolves annotations recursively.
     *
     * @param parameterizedAnnotations The class of the parameterized annotation to expand, if any
     */
    public static void resolveRecursiveAnnotations(
            HarrierRule harrierRule,
            List<Annotation> annotations,
            List<Annotation> parameterizedAnnotations) {
        int index = 0;
        while (index < annotations.size()) {
            Annotation annotation = annotations.get(index);
            annotations.remove(index);
            List<Annotation> replacementAnnotations =
                    getReplacementAnnotations(harrierRule, annotation, parameterizedAnnotations);
            replacementAnnotations.sort(BedsteadJUnit4::annotationSorter);
            annotations.addAll(index, replacementAnnotations);
            index += replacementAnnotations.size();
        }
    }

    private static boolean isParameterizedAnnotation(Annotation annotation) {
        if (annotation instanceof DynamicParameterizedAnnotation) {
            return true;
        }

        return annotation.annotationType().getAnnotation(ParameterizedAnnotation.class) != null;
    }

    private static boolean isAnnotationClassParameterizedAnnotation(Annotation annotation) {
        return annotation.annotationType() != null
                && annotation.annotationType().getAnnotation(ParameterizedAnnotation.class) != null;
    }

    private static Annotation[] getIndirectAnnotations(Annotation annotation) {
        if (annotation instanceof DynamicParameterizedAnnotation) {
            return ((DynamicParameterizedAnnotation) annotation).annotations();
        }
        return annotation.annotationType().getAnnotations();
    }

    private static boolean isRepeatingAnnotation(Annotation annotation) {
        if (annotation instanceof DynamicParameterizedAnnotation) {
            return false;
        }

        return annotation.annotationType().getAnnotation(RepeatingAnnotation.class) != null;
    }

    private HarrierRule mHarrierRule;

    private static final ImmutableMap<
                    Class<? extends Annotation>,
                    BiFunction<HarrierRule, Annotation, Stream<Annotation>>>
            ANNOTATION_REPLACEMENTS =
                    ImmutableMap.of(
                            RequireRunOnInitialUser.class,
                            (harrierRule, a) -> {
                                RequireRunOnInitialUser requireRunOnInitialUserAnnotation =
                                        (RequireRunOnInitialUser) a;

                                if (harrierRule.isHeadlessSystemUserMode()) {
                                    return Stream.of(
                                            a,
                                            ensureHasSecondaryUser(),
                                            requireRunOnSecondaryUser(
                                                    requireRunOnInitialUserAnnotation
                                                            .switchedToUser()));
                                } else {
                                    return Stream.of(
                                            a,
                                            requireRunOnPrimaryUser(
                                                    requireRunOnInitialUserAnnotation
                                                            .switchedToUser()));
                                }
                            },
                            RequireRunOnAdditionalUser.class,
                            (harrierRule, a) -> {
                                RequireRunOnAdditionalUser requireRunOnAdditionalUserAnnotation =
                                        (RequireRunOnAdditionalUser) a;
                                if (harrierRule.isHeadlessSystemUserMode()) {
                                    return Stream.of(ensureHasSecondaryUser(), a);
                                } else {
                                    return Stream.of(
                                            a,
                                            requireRunOnSecondaryUser(
                                                    requireRunOnAdditionalUserAnnotation
                                                            .switchedToUser()));
                                }
                            });

    static List<Annotation> getReplacementAnnotations(
            HarrierRule harrierRule,
            Annotation annotation,
            List<Annotation> parameterizedAnnotations) {
        BiFunction<HarrierRule, Annotation, Stream<Annotation>> specialReplaceFunction =
                ANNOTATION_REPLACEMENTS.get(annotation.annotationType());

        if (specialReplaceFunction != null) {
            List<Annotation> replacement =
                    specialReplaceFunction.apply(harrierRule, annotation)
                            .collect(Collectors.toList());
            return replacement;
        }

        List<Annotation> replacementAnnotations = new ArrayList<>();

        if (isRepeatingAnnotation(annotation)) {
            try {
                Annotation[] annotations =
                        (Annotation[]) annotation.annotationType()
                                .getMethod("value").invoke(annotation);
                Collections.addAll(replacementAnnotations, annotations);
                return replacementAnnotations;
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                throw new NeneException("Error expanding repeated annotations", e);
            }
        }

        if (isParameterizedAnnotation(annotation)
                && !parameterizedAnnotations.contains(annotation)) {
            return replacementAnnotations;
        }

        for (Annotation indirectAnnotation : getIndirectAnnotations(annotation)) {
            if (shouldSkipAnnotation(annotation)) {
                continue;
            }

            replacementAnnotations.addAll(
                    getReplacementAnnotations(
                            harrierRule, indirectAnnotation, parameterizedAnnotations));
        }

        if (!(annotation instanceof DynamicParameterizedAnnotation)) {
            // We drop the fake annotation once it's replaced
            replacementAnnotations.add(annotation);
        }

        return replacementAnnotations;
    }

    private static boolean shouldSkipAnnotation(Annotation annotation) {
        if (annotation instanceof DynamicParameterizedAnnotation) {
            return false;
        }

        if(annotation.annotationType().equals(IncludeNone.class)) {
            return true;
        }

        String annotationPackage = annotation.annotationType().getPackage().getName();

        for (String ignoredPackage : sIgnoredAnnotationPackages) {
            if (ignoredPackage.endsWith(".*")) {
                if (annotationPackage.startsWith(
                        ignoredPackage.substring(0, ignoredPackage.length() - 2))) {
                    return true;
                }
            } else if (annotationPackage.equals(ignoredPackage)) {
                return true;
            }
        }

        return false;
    }

    public BedsteadJUnit4(Class<?> testClass) throws InitializationError {
        super(testClass);
    }

    private static List<FrameworkMethod> getBasicTests(TestClass testClass) {
        Set<FrameworkMethod> methods = new HashSet<>();

        methods.addAll(testClass.getAnnotatedMethods(Test.class));
        methods.addAll(testClass.getAnnotatedMethods(PolicyAppliesTest.class));
        methods.addAll(testClass.getAnnotatedMethods(PolicyDoesNotApplyTest.class));
        methods.addAll(testClass.getAnnotatedMethods(CanSetPolicyTest.class));
        methods.addAll(testClass.getAnnotatedMethods(CannotSetPolicyTest.class));
        methods.addAll(testClass.getAnnotatedMethods(UserTest.class));
        methods.addAll(testClass.getAnnotatedMethods(CrossUserTest.class));
        methods.addAll(testClass.getAnnotatedMethods(PermissionTest.class));
        methods.addAll(testClass.getAnnotatedMethods(MostRestrictiveCoexistenceTest.class));
        methods.addAll(testClass.getAnnotatedMethods(MostImportantCoexistenceTest.class));
        methods.addAll(testClass.getAnnotatedMethods(HiddenApiTest.class));
        methods.addAll(testClass.getAnnotatedMethods(PerformanceTest.class));

        return new ArrayList<>(methods);
    }

    /**
     * Groups list of annotations of type [ParameterizedAnnotation] by its [scope].
     *
     * @param parameterizedAnnotations the list of annotations of type [ParameterizedAnnotation]
     * @return list of list of [ParameterizedAnnotation] where each sub list corresponds to
     *     annotations of one scope.
     */
    private List<List<Annotation>> getParameterizedAnnotationsGroupedByScope(
            Set<Annotation> parameterizedAnnotations) {
        Map<String, List<Annotation>> annotationsPerScope = new HashMap<>();
        for (Annotation annotation : parameterizedAnnotations) {
            if (isAnnotationClassParameterizedAnnotation(annotation)
                    && !shouldSkipAnnotation(annotation)) {
                ParameterizedAnnotation parameterizedAnnotation =
                        annotation.annotationType().getAnnotation(ParameterizedAnnotation.class);
                annotationsPerScope.putIfAbsent(
                        parameterizedAnnotation.scope().name(), new ArrayList<>());
                annotationsPerScope.get(parameterizedAnnotation.scope().name()).add(annotation);
            }
        }

        return new ArrayList<>(annotationsPerScope.values());
    }

    /**
     * Generates a cartesian product of multiple sets of annotations. For example: If the
     * [annotations] param has value [[A1, A2], [A3, A4]] then it will return [[A1, A3], [A1, A4],
     * [A2, A3], [A2, A4]].
     *
     * @param annotations list of list of annotations whose cartesian product we want to generate.
     * @return cartesian product of the annotation sets.
     */
    private static List<List<Annotation>> calculateCartesianProductOfAnnotationSets(
            List<List<Annotation>> annotations) {
        List<List<Annotation>> result = new ArrayList<>();
        if (!annotations.isEmpty()) {
            generateCartesianProductOfAnnotationSets(annotations, 0, result, new ArrayList<>());
        }
        return result;
    }

    /**
     * Generates a cartesian product of multiple sets of annotations. This method is an internal
     * helper method for {@code calculateCartesianProductOfAnnotationSets()}. Refer {@code
     * calculateCartesianProductOfAnnotationSets()} for an example.
     */
    private static void generateCartesianProductOfAnnotationSets(
            List<List<Annotation>> annotations,
            int position,
            List<List<Annotation>> result,
            List<Annotation> subResult) {
        if (position == annotations.size()) {
            if (!subResult.isEmpty()) {
                result.add(new ArrayList<>(subResult));
            }
            return;
        }
        for (int i = 0; i < annotations.get(position).size(); i++) {
            subResult.add(annotations.get(position).get(i));
            generateCartesianProductOfAnnotationSets(annotations, position + 1, result, subResult);
            subResult.remove(subResult.size() - 1);
        }
    }

    @Override
    protected List<FrameworkMethod> computeTestMethods() {
        // TODO: It appears that the annotations are computed up to 8 times per run. Figure out how
        // to cut this out (this method only seems to be called once)
        List<FrameworkMethod> basicTests = getBasicTests(getTestClass());
        List<FrameworkMethod> modifiedTests = new ArrayList<>();

        for (FrameworkMethod m : basicTests) {
            Set<Annotation> parameterizedAnnotations = getParameterizedAnnotations(m.getAnnotations());

            if (parameterizedAnnotations.isEmpty()) {
                // Unparameterized, just add the original
                modifiedTests.add(new BedsteadFrameworkMethod(this, m.getMethod()));
                continue;
            }

            // Create [BedsteadFrameworkMethod] for parameterized annotation of instance {@Code
            // DynamicParameterizedAnnotation}.
            for (Annotation annotation : parameterizedAnnotations) {
                if (shouldSkipAnnotation(annotation)
                        || isAnnotationClassParameterizedAnnotation(annotation)) {
                    // Special case - does not generate a run
                    continue;
                }
                modifiedTests.add(
                        new BedsteadFrameworkMethod(this, m.getMethod(), List.of(annotation)));
            }

            List<List<Annotation>> parametrizedAnnotationsGroupedByScope =
                    getParameterizedAnnotationsGroupedByScope(parameterizedAnnotations);

            List<List<Annotation>> cartesianProductOfAnnotationSets =
                    calculateCartesianProductOfAnnotationSets(
                            parametrizedAnnotationsGroupedByScope);

            // Create [BedsteadFrameworkMethod] for each parameterized annotation of type
            // [ParameterizedAnnotation].
            for (List<Annotation> annotationsToApplyTogether : cartesianProductOfAnnotationSets) {
                modifiedTests.add(
                        new BedsteadFrameworkMethod(
                                this, m.getMethod(), annotationsToApplyTogether));
            }
        }

        modifiedTests = generateGeneralParameterisationMethods(modifiedTests);

        sortMethodsByBedsteadAnnotations(modifiedTests);

        return modifiedTests;
    }

    private List<FrameworkMethod> generateGeneralParameterisationMethods(
            List<FrameworkMethod> modifiedTests) {
        return modifiedTests.stream()
                .flatMap(this::generateGeneralParameterisationMethods)
                .collect(Collectors.toList());
    }

    private Stream<FrameworkMethod> generateGeneralParameterisationMethods(FrameworkMethod method) {
        Stream<FrameworkMethod> expandedMethods = Stream.of(method);
        if (method.getMethod().getParameterCount() == 0) {
            return expandedMethods;
        }

        for (Parameter parameter : method.getMethod().getParameters()) {
            List<Annotation> annotations =
                    new ArrayList<>(Arrays.asList(parameter.getAnnotations()));
            resolveRecursiveAnnotations(annotations, /* parameterizedAnnotations= */ List.of());

            boolean hasParameterised = false;

            for (Annotation annotation : annotations) {

                if (annotation instanceof PolicyArgument) {
                    if (hasParameterised) {
                        throw new IllegalStateException(
                                "Each parameter can only have a single parameterised annotation");
                    }
                    hasParameterised = true;

                    HarrierToEnterpriseMediator mediator =
                            HarrierToEnterpriseMediator.Companion.getMediatorOrThrowException(
                                    "you can't use @PolicyArgument without the enterprise module"
                            );
                    expandedMethods = mediator.generatePolicyArgumentTests(method, expandedMethods);
                } else if (annotation instanceof StringTestParameter) {
                    if (hasParameterised) {
                        throw new IllegalStateException(
                                "Each parameter can only have a single parameterised annotation");
                    }
                    hasParameterised = true;

                    StringTestParameter stringTestParameter = (StringTestParameter) annotation;

                    expandedMethods = expandedMethods.flatMap(
                            i -> applyStringTestParameter(i, stringTestParameter));
                } else if (annotation instanceof IntTestParameter) {
                    if (hasParameterised) {
                        throw new IllegalStateException(
                                "Each parameter can only have a single parameterised annotation");
                    }
                    hasParameterised = true;

                    IntTestParameter intTestParameter = (IntTestParameter) annotation;

                    expandedMethods = expandedMethods.flatMap(
                            i -> applyIntTestParameter(i, intTestParameter));
                } else if (annotation instanceof EnumTestParameter) {
                    if (hasParameterised) {
                        throw new IllegalStateException(
                                "Each parameter can only have a single parameterised annotation");
                    }
                    hasParameterised = true;

                    EnumTestParameter enumTestParameter = (EnumTestParameter) annotation;

                    expandedMethods = expandedMethods.flatMap(
                            i -> applyEnumTestParameter(i, enumTestParameter));
                }
            }

            if (!hasParameterised) {
                throw new IllegalStateException(
                        "Parameter " + parameter + " must be annotated as parameterised");
            }
        }

        return expandedMethods;
    }

    private static Stream<FrameworkMethod> applyStringTestParameter(FrameworkMethod frameworkMethod,
            StringTestParameter stringTestParameter) {
        return Stream.of(stringTestParameter.value()).map(
                (i) -> new FrameworkMethodWithParameter(frameworkMethod, i)
        );
    }

    private static Stream<FrameworkMethod> applyIntTestParameter(FrameworkMethod frameworkMethod,
            IntTestParameter intTestParameter) {
        return Arrays.stream(intTestParameter.value()).mapToObj(
                (i) -> new FrameworkMethodWithParameter(frameworkMethod, i)
        );
    }

    private static Stream<FrameworkMethod> applyEnumTestParameter(FrameworkMethod frameworkMethod,
            EnumTestParameter enumTestParameter) {
        return Arrays.stream(enumTestParameter.value().getEnumConstants()).map(
                (i) -> new FrameworkMethodWithParameter(frameworkMethod, i)
        );
    }

    /**
     * Sort methods by cost and group the ones with identical bedstead annotations together.
     *
     * <p>This will also ensure that all tests methods which are not annotated for bedstead will
     * run before any tests which are annotated.
     */
    private void sortMethodsByBedsteadAnnotations(List<FrameworkMethod> modifiedTests) {
        List<Annotation> bedsteadAnnotationsSortedByCost =
                bedsteadAnnotationsSortedByCost(modifiedTests);
        Comparator<FrameworkMethod> comparator = ((o1, o2) -> {
            for (Annotation annotation : bedsteadAnnotationsSortedByCost) {
                boolean o1HasAnnotation = o1.getAnnotation(annotation.annotationType()) != null;
                boolean o2HasAnnotation = o2.getAnnotation(annotation.annotationType()) != null;

                if (o1HasAnnotation && !o2HasAnnotation) {
                    // o1 goes to the start
                    return -1;
                } else if (o2HasAnnotation && !o1HasAnnotation) {
                    return 1;
                }
            }
            return 0;
        });

        List<Annotation> bedsteadAnnotationsSortedByMostCommon =
                bedsteadAnnotationsSortedByMostCommon(modifiedTests);
        var unused = comparator.thenComparing((o1, o2) -> {
            for (Annotation annotation : bedsteadAnnotationsSortedByMostCommon) {
                boolean o1HasAnnotation = o1.getAnnotation(annotation.annotationType()) != null;
                boolean o2HasAnnotation = o2.getAnnotation(annotation.annotationType()) != null;

                if (o1HasAnnotation && !o2HasAnnotation) {
                    // o1 goes to the end
                    return 1;
                } else if (o2HasAnnotation && !o1HasAnnotation) {
                    return -1;
                }
            }

            return 0;
        });

        modifiedTests.sort(comparator);
    }

    private List<Annotation> bedsteadAnnotationsSortedByCost(List<FrameworkMethod> methods) {
        Map<Annotation, Integer> annotationCosts = mapAnnotationsCost(methods);

        List<Annotation> annotations = new ArrayList<>(annotationCosts.keySet());
        annotations.sort(Comparator.comparingInt(annotationCosts::get));

        return annotations;
    }

    private List<Annotation> bedsteadAnnotationsSortedByMostCommon(List<FrameworkMethod> methods) {
        Map<Annotation, Integer> annotationCounts = countAnnotations(methods);
        List<Annotation> annotations = new ArrayList<>(annotationCounts.keySet());
        annotations.sort(Comparator.comparingInt(annotationCounts::get));
        Collections.reverse(annotations);

        return annotations;
    }

    private Map<Annotation, Integer> countAnnotations(List<FrameworkMethod> methods) {
        Map<Annotation, Integer> annotationCounts = new HashMap<>();

        for (FrameworkMethod method : methods) {
            for (Annotation annotation : method.getAnnotations()) {
                annotationCounts.put(
                        annotation, annotationCounts.getOrDefault(annotation, 0) + 1);
            }
        }

        return annotationCounts;
    }

    private Map<Annotation, Integer> mapAnnotationsCost(List<FrameworkMethod> methods) {
        Map<Annotation, Integer> annotationCosts = new HashMap<>();

        for (FrameworkMethod method : methods) {
            for (Annotation annotation : method.getAnnotations()) {
                annotationCosts.put(annotation, getAnnotationCost(annotation));
            }
        }

        return annotationCosts;
    }

    /**
     * Filters array of annotations and returns only annotations of type
     * {@link ParameterizedAnnotation} and {@link DynamicParameterizedAnnotation}.
     *
     * @param methodAnnotations the array of annotations of test method
     */
    @CanIgnoreReturnValue
    public static Set<Annotation> getParameterizedAnnotations(Annotation[] methodAnnotations) {
        Set<Annotation> parameterizedAnnotations = new HashSet<>();
        List<Annotation> annotations = new ArrayList<>(Arrays.asList(methodAnnotations));

        parseEnterpriseAnnotations(annotations);
        parsePermissionAnnotations(annotations);
        parseUserAnnotations(annotations);

        for (Annotation annotation : annotations) {
            if (isParameterizedAnnotation(annotation)) {
                parameterizedAnnotations.add(annotation);
            }
        }

        return parameterizedAnnotations;
    }

    /**
     * Parse enterprise-specific annotations.
     *
     * <p>To be used before general annotation processing.
     */
    static void parseEnterpriseAnnotations(List<Annotation> annotations) {
        HarrierToEnterpriseMediator mediator =
                HarrierToEnterpriseMediator.Companion.getMediatorOrNull();
        if (mediator == null) {
            System.out.println(LOG_TAG + " bedstead-enterprise module is not loaded, "
                    + "parseEnterpriseAnnotations will not be executed");
        } else {
            mediator.parseEnterpriseAnnotations(annotations);
        }
    }

    /**
     * Parse @PermissionTest annotations.
     *
     * <p>To be used before general annotation processing.
     */
    static void parsePermissionAnnotations(List<Annotation> annotations) {
        int index = 0;
        while (index < annotations.size()) {
            Annotation annotation = annotations.get(index);
            if (annotation instanceof PermissionTest) {
                annotations.remove(index);

                List<Annotation> replacementAnnotations = generatePermissionAnnotations(
                        ((PermissionTest) annotation).value());
                replacementAnnotations.sort(BedsteadJUnit4::annotationSorter);

                annotations.addAll(index, replacementAnnotations);
                index += replacementAnnotations.size();
            } else {
                index++;
            }
        }
    }

    private static List<Annotation> generatePermissionAnnotations(String[] permissions) {
        Set<String> allPermissions = new HashSet<>(Arrays.asList(permissions));
        List<Annotation> replacementAnnotations = new ArrayList<>();

        for (String permission : permissions) {
            allPermissions.remove(permission);
            replacementAnnotations.add(
                    new DynamicParameterizedAnnotation(
                            permission,
                            new Annotation[]{
                                    ensureHasPermission(permission),
                                    ensureDoesNotHavePermission(allPermissions.toArray(new String[]{}))
                            }));
            allPermissions.add(permission);
        }

        return replacementAnnotations;
    }

    /**
     * Parse @UserTest and @CrossUserTest annotations.
     *
     * <p>To be used before general annotation processing.
     */
    static void parseUserAnnotations(List<Annotation> annotations) {
        int index = 0;
        while (index < annotations.size()) {
            Annotation annotation = annotations.get(index);
            if (annotation instanceof UserTest) {
                annotations.remove(index);

                List<Annotation> replacementAnnotations = generateUserAnnotations(
                        ((UserTest) annotation).value());
                replacementAnnotations.sort(BedsteadJUnit4::annotationSorter);

                annotations.addAll(index, replacementAnnotations);
                index += replacementAnnotations.size();
            } else if (annotation instanceof CrossUserTest) {
                annotations.remove(index);

                CrossUserTest crossUserTestAnnotation = (CrossUserTest) annotation;
                List<Annotation> replacementAnnotations = generateCrossUserAnnotations(
                        crossUserTestAnnotation.value());
                replacementAnnotations.sort(BedsteadJUnit4::annotationSorter);

                annotations.addAll(index, replacementAnnotations);
                index += replacementAnnotations.size();
            } else {
                index++;
            }
        }
    }

    private static List<Annotation> generateUserAnnotations(UserType[] userTypes) {
        List<Annotation> replacementAnnotations = new ArrayList<>();

        for (UserType userType : userTypes) {
            Annotation runOnUserAnnotation = getRunOnAnnotation(userType, "@UserTest");
            replacementAnnotations.add(
                    new DynamicParameterizedAnnotation(
                            userType.name(),
                            new Annotation[]{runOnUserAnnotation}));
        }

        return replacementAnnotations;
    }

    private static List<Annotation> generateCrossUserAnnotations(UserPair[] userPairs) {
        List<Annotation> replacementAnnotations = new ArrayList<>();

        for (UserPair userPair : userPairs) {
            Annotation[] annotations = new Annotation[]{
                    getRunOnAnnotation(userPair.from(), "@CrossUserTest"),
                    otherUser(userPair.to())
            };
            if (userPair.from() != userPair.to()) {
                Annotation hasUserAnnotation =
                        getHasUserAnnotation(userPair.to(), "@CrossUserTest");
                if (hasUserAnnotation != null) {
                    annotations = new Annotation[]{
                            annotations[0],
                            annotations[1],
                            hasUserAnnotation};
                }
            }

            replacementAnnotations.add(
                    new DynamicParameterizedAnnotation(
                            userPair.from().name() + "_to_" + userPair.to().name(),
                            annotations));
        }

        return replacementAnnotations;
    }

    private static Annotation getRunOnAnnotation(UserType userType, String annotationName) {
        switch (userType) {
            case SYSTEM_USER:
                return requireRunOnSystemUser();
            case CURRENT_USER:
                return null; // No requirement, run on current user
            case INITIAL_USER:
                return requireRunOnInitialUser();
            case ADDITIONAL_USER:
                return requireRunOnAdditionalUser();
            case PRIMARY_USER:
                return requireRunOnPrimaryUser();
            case SECONDARY_USER:
                return requireRunOnSecondaryUser();
            case WORK_PROFILE:
                return requireRunOnWorkProfile(query());
            case TV_PROFILE:
                return requireRunOnTvProfile();
            case CLONE_PROFILE:
                return requireRunOnCloneProfile();
            case PRIVATE_PROFILE:
                return requireRunOnPrivateProfile();
            default:
                throw new IllegalStateException(
                        "UserType " + userType + " is not compatible with " + annotationName);
        }
    }

    private static Annotation getHasUserAnnotation(UserType userType, String annotationName) {
        switch (userType) {
            case SYSTEM_USER:
                return null; // We always have a system user
            case CURRENT_USER:
                return null; // We always have a current user
            case INITIAL_USER:
                return null; // We always have an initial user
            case ADDITIONAL_USER:
                return ensureHasAdditionalUser();
            case PRIMARY_USER:
                return requireNotHeadlessSystemUserMode(
                        "Headless System User Mode Devices do not have a primary user");
            case SECONDARY_USER:
                return ensureHasSecondaryUser();
            case WORK_PROFILE:
                return ensureHasWorkProfile(query());
            case TV_PROFILE:
                return ensureHasTvProfile();
            case CLONE_PROFILE:
                return ensureHasCloneProfile();
            case PRIVATE_PROFILE:
                return ensureHasPrivateProfile();
            default:
                throw new IllegalStateException(
                        "UserType " + userType + " is not compatible with " + annotationName);
        }
    }

    HarrierRule getHarrierRule() {
        if (mHarrierRule == null) {
            var unused = classRules();
        }
        return mHarrierRule;
    }

    @Override
    protected List<TestRule> getTestRules(Object target) {
        var testRules = super.getTestRules(target);
        if (mHasManualHarrierRule) {
            return testRules;
        }
        var harrier = findHarrier(testRules);
        if (harrier == null) {
            testRules.add(getHarrierRule());
        }
        return testRules;
    }

    @Override
    protected List<TestRule> classRules() {
        List<TestRule> rules = super.classRules();

        mHarrierRule = findHarrier(rules);
        mHasManualHarrierRule = mHarrierRule != null;

        if (mHarrierRule == null) {
            mHarrierRule = new DeviceState();
        }
        if (!rules.contains(mHarrierRule)) {
            rules.add(mHarrierRule);
        }

        mHarrierRule.setSkipTestTeardown(true);
        mHarrierRule.setUsingBedsteadJUnit4(true);

        return rules;
    }

    private HarrierRule findHarrier(List<TestRule> rules) {
        for (TestRule rule : rules) {
            if (rule instanceof HarrierRule) {
                return (HarrierRule) rule;
            }
        }
        return null;
    }

    /**
     * True if the test is running in debug mode.
     *
     * <p>This will result in additional debugging information being added which would otherwise
     * be dropped to improve test performance.
     *
     * <p>To enable this, pass the "bedstead-debug" instrumentation arg as "true"
     */
    public static boolean isDebug() {
        try {
            Class instrumentationRegistryClass = Class.forName(
                        "androidx.test.platform.app.InstrumentationRegistry");

            Object arguments = instrumentationRegistryClass.getMethod("getArguments")
                    .invoke(null);
            return Boolean.parseBoolean((String) arguments.getClass()
                    .getMethod("getString", String.class, String.class)
                    .invoke(arguments, "bedstead-debug", "false"));
        } catch (ClassNotFoundException e) {
            return false; // Must be on the host so can't access debug information
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Error getting isDebug", e);
        }
    }

    @Override
    protected void validateTestMethods(List<Throwable> errors) {
        // We do allow arguments - they will fail validation later on if not properly annotated
    }

    /**
     * Add a listener to be informed of test lifecycle events.
     */
    public static void addLifecycleListener(TestLifecycleListener listener) {
        sLifecycleListeners.add(listener);
    }

    /**
     * Remove a listener being informed of test lifecycle events.
     */
    public static void removeLifecycleListener(TestLifecycleListener listener) {
        sLifecycleListeners.remove(listener);
    }

    @Override
    protected void runChild(final FrameworkMethod method, RunNotifier notifier) {
        Description description = describeChild(method);
        if (isIgnored(method)) {
            notifier.fireTestIgnored(description);
        } else {
            Statement statement = new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    sLifecycleListeners.forEach(l -> l.testStarted(method.getName()));
                    while (true) {
                        try {
                            methodBlock(method).evaluate();
                            sLifecycleListeners.forEach(l -> l.testFinished(method.getName()));
                            return;
                        } catch (RestartTestException e) {
                            sLifecycleListeners.forEach(
                                    l -> l.testRestarted(method.getName(), e.getMessage()));
                            System.out.println(LOG_TAG + ": Restarting test(" + e.toString() + ")");
                        }
                    }
                }
            };
            runLeaf(statement, description, notifier);
        }
    }
}
