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

package com.android.bedstead.testapisreflection.processor.generators;

import static com.android.bedstead.testapisreflection.processor.Processor.BLOCKLISTED_TEST_CLASSES;

import com.android.bedstead.testapis.parser.signatures.ClassSignature;
import com.android.bedstead.testapisreflection.processor.generators.common.CodeGenerator;

import com.google.common.collect.ImmutableMap;

import java.util.List;
import java.util.Map;

import javax.annotation.processing.ProcessingEnvironment;

/**
 * Helper class to generate proxy classes that enable access to classes that are annotated
 * with @TestApi at the class level hereby referred to as a "TestClass".
 */
public final class ProxyClassesGenerator {

    private final CodeGenerator mCodeGenerator;

    public static final Map<String, String> SERVICES_ALIAS = ImmutableMap.of(
            "android.app.DreamManager", "dream",
            "android.app.ActivityTaskManager", "activity_task"
    );

    public ProxyClassesGenerator(ProcessingEnvironment processingEnvironment) {
        mCodeGenerator = new CodeGenerator(processingEnvironment);
    }

    /**
     * Generate a proxy class per each "TestClass".
     */
    public void generatedMethods(List<ClassSignature> testClasses) {
        for (ClassSignature classSignature : testClasses) {
            if (BLOCKLISTED_TEST_CLASSES.contains(classSignature.getName())) {
                continue;
            }

            mCodeGenerator.generateProxyClass(classSignature, testClasses);
        }
    }
}