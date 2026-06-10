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

package com.android.bedstead.testapisreflection.processor;

import static com.android.bedstead.testapisreflection.processor.utils.ResourceLoader.load;

import com.android.bedstead.testapis.parser.TestApisParser;
import com.android.bedstead.testapis.parser.signatures.ClassSignature;
import com.android.bedstead.testapis.parser.signatures.PackageSignature;
import com.android.bedstead.testapisreflection.processor.annotations.TestApisReflectionTrigger;
import com.android.bedstead.testapisreflection.processor.generators.ProxyClassesGenerator;
import com.android.bedstead.testapisreflection.processor.generators.ProxyMethodExtensionsGenerator;

import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;

/**
 * Processor for generating the {@code TestApisReflection} file.
 *
 * <p>This is started by including the {@link TestApisReflectionTrigger} annotation.
 *
 * <p><b>Usage:</b>
 *
 * <li> Any method marked as @TestApi at method level can be accessed using the
 * {@code TestApisReflection} kotlin extensions file.
 *
 * <li> Any method that is a member of a class that is marked as @TestApi at class level can be
 * accessed through its respective Proxy class. For e.g. all methods inside
 * {@code android.content.pm.UserInfo} can be accessed using
 * {@code android.cts.testapisreflection.UserInfoProxy}.
 *
 * <li> If the TestApisReflection file is accessed from a java class,
 * <ul>
 *  <li> Add {@code import android.cts.testapisreflection.TestApisReflectionKt}
 *  <li> Access the method using TestApisReflectionKt.method_name(receiver_object, args…)
 *  <li> Example: {@code android.service.quicksettings.TileService#isQuickSettingsSupported} is a
 *  TestApi and can be accessed using
 *  {@code TestApisReflectionKt.isQuickSettingsSupported(tileServiceInstance)} using the import line
 *  below.
 * </ul>
 *
 * <li> If the TestApisReflection file is accessed from a kotlin class,
 * <ul>
 *  <li> Add {@code import android.cts.testspisreflection.*}
 *  <li> Example: {@code android.service.quicksettings.TileService#isQuickSettingsSupported} is
 *   a TestApi and can be accessed using {@code tileService.isQuickSettingsSupported()} using the
 *   mentioned import line.
 * </ul>
 *
 * <b> Note: Generated proxy classes should not be exposed outside of bedstead and must always be
 * hidden and wrapped by Bedstead-specific classes.
 */
@SupportedAnnotationTypes({
        "com.android.bedstead.testapisreflection.processor.annotations.TestApisReflectionTrigger",
})
@AutoService(javax.annotation.processing.Processor.class)
public class Processor extends AbstractProcessor {

    public static final ImmutableList<String> BLOCKLISTED_TEST_CLASSES =
            load("/apis/blocklisted-test-classes.txt");

    public static final ImmutableList<String> ALLOWLISTED_TEST_FIELDS =
            load("/apis/allowlisted-test-fields.txt");

    public static final String PACKAGE_NAME = "android.cts.testapisreflection";
    public static final String FILE_NAME = "TestApisReflection";

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.getElementsAnnotatedWith(TestApisReflectionTrigger.class).isEmpty()) {
            return true;
        }

        List<PackageSignature> testApis = TestApisParser.parse();
        List<ClassSignature> testClasses = testApis.stream()
                .flatMap(p -> p.getClassSignatures().stream()
                        .filter(c -> c.isTestClass(processingEnv.getElementUtils())))
                .collect(Collectors.toUnmodifiableList());

        // Generate proxy classes for classes annotated as @TestApi as a whole.
        new ProxyClassesGenerator(processingEnv).generatedMethods(testClasses);

        // Generate methods to access @TestApi methods through reflection.
        new ProxyMethodExtensionsGenerator(processingEnv).generateMethods(testApis, testClasses);

        return true;
    }
}
