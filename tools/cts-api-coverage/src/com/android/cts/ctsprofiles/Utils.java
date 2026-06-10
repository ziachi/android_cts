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

package com.android.cts.ctsprofiles;

import org.apache.commons.math3.util.Pair;

import java.util.List;

/** Utility for generating package, class or method information. */
public final class Utils {

    /** Splits a class signature and returns the (package, class) pair. */
    public static Pair<String, String> getPackageClass(String classSignature) {
        int splitPos = classSignature.lastIndexOf('.');
        String className = classSignature.substring(splitPos + 1);
        String packageName = "";
        if (splitPos > 0) {
            packageName = classSignature.substring(0, splitPos);
        }
        return Pair.create(packageName, className.replaceAll("\\$", "."));
    }

    /** Splits a class name represented in ASM and returns the (package, class) pair. */
    public static Pair<String, String> getPackageClassFromASM(String packageClass) {
        // The class name obtained from ASM includes package information and is separated by "/".
        // For example, the class com.my.package.MyClass.NestClass is represented as
        // com/my/package/MyClass$NestClass in ASM.
        int splitPos = packageClass.lastIndexOf("/");
        String packageName = "";
        if (splitPos != -1) {
            packageName = packageClass.substring(0, splitPos);
        }
        String className = packageClass.substring(splitPos + 1);
        return Pair.create(
                packageName.replaceAll("/", "."),
                className.replaceAll("\\$", ".")
        );
    }

    /** Returns a class signature in the format {package}.{class}. */
    public static String getClassSignature(String packageName, String className) {
        if (packageName.isEmpty()) {
            return className;
        }
        return String.format("%s.%s", packageName, className);
    }

    /** Returns a method signature in the format {method}({param1}, {param2}, ...). */
    public static String getMethodSignature(String methodName, List<String> paramTypes) {
        return String.format("%s(%s)", methodName, String.join(", ", paramTypes));
    }

    /**
     * Returns a full method signature in the format
     * {package}.{class}#{method}({param1}, {param2}, ...).
     */
    public static String getMethodSignatureWithClass(
            String packageName, String className, String methodName, List<String> paramTypes) {
        String classSignature = getClassSignature(packageName, className);
        String methodSignature = getMethodSignature(methodName, paramTypes);
        return String.format("%s#%s", classSignature, methodSignature);
    }
}
