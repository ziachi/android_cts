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

import static com.android.bedstead.testapisreflection.processor.Processor.FILE_NAME;
import static com.android.bedstead.testapisreflection.processor.Processor.PACKAGE_NAME;
import static com.android.bedstead.testapisreflection.processor.Processor.BLOCKLISTED_TEST_CLASSES;

import com.android.bedstead.testapis.parser.signatures.ClassSignature;
import com.android.bedstead.testapis.parser.signatures.MethodSignature;
import com.android.bedstead.testapis.parser.signatures.PackageSignature;
import com.android.bedstead.testapisreflection.processor.generators.common.CodeGenerator;

import com.squareup.kotlinpoet.FileSpec;

import java.io.IOException;
import java.util.List;

import javax.annotation.processing.ProcessingEnvironment;

/**
 * Helper class to generate the TestApisReflection kotlin extensions file.
 *
 * <p>
 * The file is comprised of kotlin extension functions that allow access to all TestApis
 * through reflection except for the ones that are part of classes that are marked as
 * {@code @TestApi} at the class level. For such classes, separate proxy classes are generated
 * (one for each class) to access their APIs, see {@link ProxyClassesGenerator}.
 * </p>
 */
public final class ProxyMethodExtensionsGenerator {

    private static final String LOG_TAG = "TestApisReflection";

    private final ProcessingEnvironment mProcessingEnvironment;
    private final CodeGenerator mCodeGenerator;

    public ProxyMethodExtensionsGenerator(ProcessingEnvironment processingEnvironment) {
        mProcessingEnvironment = processingEnvironment;
        mCodeGenerator = new CodeGenerator(processingEnvironment);
    }

    /**
     * Generate methods and add to the file.
     */
    public void generateMethods(List<PackageSignature> testApis, List<ClassSignature> testClasses) {
        FileSpec.Builder fileBuilder = FileSpec.builder(PACKAGE_NAME, FILE_NAME);

        for (PackageSignature packageSignature : testApis) {
            for (ClassSignature classSignature : packageSignature.getClassSignatures()) {
                // TODO(b/337769574): add support for blocklisted classes
                if (testClasses.contains(classSignature) ||
                        BLOCKLISTED_TEST_CLASSES.contains(classSignature.getName())) {
                    continue;
                }

                for (MethodSignature methodSignature : classSignature.getMethodSignatures()) {
                    try {
                        mCodeGenerator.generateReflectionMethod(fileBuilder, methodSignature,
                                testClasses);
                    } catch (IllegalStateException e) {
                        System.out.println(LOG_TAG + ": " +
                                String.format("Unable to generate proxy for %s#%s",
                                        methodSignature.getFrameworkClass(), methodSignature.getName()));
                    }
                }
            }
        }

        FileSpec reflectionFile = fileBuilder.build();
        try {
            reflectionFile.writeTo(mProcessingEnvironment.getFiler());
        } catch (IOException e) {
            throw new IllegalStateException("Error writing TestApisReflection to file", e);
        }

    }

}
