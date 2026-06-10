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

package com.android.xts.apimapper.helper;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A class to record and dump Android API method calls.
 */
public final class DeviceMethodCallStats {

    public DeviceMethodCallStats() {}

    // A map to record the data of called class -> called method -> called method description
    // -> called times.
    private final Map<String, Map<String, Map<String, Integer>>> mStats = new TreeMap<>();

    /** Clear all records. */
    public void clear() {
        mStats.clear();
    }

    /** Record the given method call. */
    public void onMethodCalled(String className, String methodName, String methodDesc) {
        var classStats = mStats.computeIfAbsent(className, k -> new TreeMap<>());
        var methodStats = classStats.computeIfAbsent(methodName, k -> new TreeMap<>());
        int count = methodStats.getOrDefault(methodDesc, 0);
        methodStats.put(methodDesc, count + 1);
    }

    /**
     * Dump all API calls to the output stream. Each API call is formatted as a csv record
     * containing a prefix, a test class name, a test method name, a called class, a called method,
     * a called method description, and a called times.
     */
    public void dump(String tag, String prefix, String testClassName, String testMethodName) {
        String logPrefix = String.format(
                "%s %s:%s",
                prefix,
                testClassName,
                removeMethodParameters(testMethodName));
        List<String> classes = new ArrayList<>(mStats.keySet());
        Collections.sort(classes);

        for (String className : classes) {
            Map<String, Map<String, Integer>> methodStats = mStats.get(className);
            List<String> methodNames = new ArrayList<>(methodStats.keySet());

            for (String methodName : methodNames) {
                Map<String, Integer> methodDescStats = methodStats.get(methodName);
                List<String> methodDescs = new ArrayList<>(methodDescStats.keySet());

                for (String methodDesc : methodDescs) {
                    String logMessage = String.format(
                            "%s:%s:%s:%s:%s",
                            logPrefix,
                            getPackageClass(className),
                            methodName,
                            methodDesc,
                            methodDescStats.get(methodDesc)

                    );
                    Log.i(tag, logMessage);
                }
            }
        }
    }

    /**
     * Split package name and class name from the given string.
     *
     * @return a csv style string with the format "{packageName}:{className}"
     */
    public static String getPackageClass(String packageClass) {
        int lastDot = packageClass.lastIndexOf('.');
        if (lastDot < 0) {
            return ":" + packageClass;
        } else {
            return packageClass.substring(0, lastDot) + ":"
                    + packageClass.substring(lastDot + 1);
        }
    }

    private static String removeMethodParameters(String methodName) {
        int pos = methodName.indexOf('[');
        if (pos < 0) {
            return methodName;
        }
        return methodName.substring(0, pos);
    }
}
