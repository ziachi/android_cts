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

package com.android.bedstead.testapis.parser;

import com.android.bedstead.testapis.parser.signatures.ClassSignature;
import com.android.bedstead.testapis.parser.signatures.ConstructorSignature;
import com.android.bedstead.testapis.parser.signatures.MethodSignature;
import com.android.bedstead.testapis.parser.signatures.PackageSignature;

import com.google.common.io.Resources;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

/**
 * Helper class to parse {@code test-current.txt} and fetch TestApis.
 */
public final class TestApisParser {
    private static final String API_FILE = "test-current.txt";
    private static final String API_TXT = initialiseApiTxts();

    private TestApisParser() {}

    private static String initialiseApiTxts() {
        try {
            return Resources.toString(TestApisParser.class.getResource("/apis/" + API_FILE),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read file " + API_FILE);
        }
    }

    /**
     * Parse all TestApis into a {@code List} of {@link PackageSignature}.
     */
    public static List<PackageSignature> parse() {
        List<PackageSignature> packageSignatures = new ArrayList<>();

        String[] lines = API_TXT.split("\n");

        Deque<Character> stack = new LinkedList<>();

        String packageName = null;
        String className = null;
        List<ClassSignature> classSignatures = null;
        ConstructorSignature constructorSignature = null;
        List<MethodSignature> methodSignatures = null;

        // Marks a class to be ignored as it has an unhandled case
        // TODO(b/337769574): add support for generic types and enums
        boolean ignoringClass = false;
        for (String line : lines) {
            try {
                if (line.contains("enum ")) {
                    stack.addFirst('{');
                    continue;
                }
                if (line.contains("enum_constant ")) {
                    continue;
                }

                if (line.startsWith("package ")) {
                    stack.addFirst('{');

                    // package declaration
                    packageName = line.replaceAll("package", "")
                            .replaceAll("\\{", "").trim();

                    classSignatures = new ArrayList<>();
                } else if (line.contains("class ")) {
                    stack.addFirst('{');

                    // class declaration
                    className = packageName + "." +
                            line.substring(line.indexOf("class ") + 6).split(" ")[0];

                    if (className.contains("<")) {
                        ignoringClass = true;
                        continue;
                    }

                    methodSignatures = new ArrayList<>();
                } else if (line.contains("interface ")) {
                    stack.addFirst('{');

                    // interface declaration
                    className = packageName + "." +
                            line.substring(line.indexOf("interface ") + 10).split(" ")[0];

                    if (className.contains("<")) {
                        ignoringClass = true;
                        continue;
                    }

                    methodSignatures = new ArrayList<>();
                } else if (line.contains("ctor ")) {
                    if (ignoringClass) {
                        continue;
                    }

                    // We only care about public constructors for our case
                    if (line.contains("public ")) {
                        constructorSignature = ConstructorSignature.forString(className, line);
                    }
                } else if (line.contains("method ")) {
                    if (ignoringClass) {
                        continue;
                    }

                    // We only care about non-abstract methods and methods that do not return
                    // generic types for our case
                    if (line.contains("public ") && !line.contains("abstract ") &&
                            !line.contains("?")) {
                        MethodSignature methodSignature = MethodSignature.forApiString(className,
                                line);
                        if (!methodSignatures.contains(methodSignature)) {
                            methodSignatures.add(methodSignature);
                        }
                    }
                } else if (line.endsWith("}")) {
                    stack.removeFirst();

                    if (stack.isEmpty()) {
                        PackageSignature packageSignature = new PackageSignature(
                                packageName, classSignatures);
                        if (!packageSignatures.contains(packageSignature)) {
                            packageSignatures.add(packageSignature);
                        }
                    } else {
                        if (ignoringClass) {
                            ignoringClass = false;
                            continue;
                        }

                        ClassSignature classSignature = new ClassSignature(packageName, className,
                                constructorSignature, methodSignatures);

                        if (!classSignatures.contains(classSignature)) {
                            classSignatures.add(classSignature);
                        }

                        constructorSignature = null;
                    }
                }
            } catch (NullPointerException e) {
                System.out.println(
                        "Invalid test-current.txt detected. Parsing test-current.txt to load "
                                + "TestApis failed for the line: '" + line + "'. Run update-apis.sh"
                                + " to reset the file.");
            }
        }

        return packageSignatures;
    }
}
