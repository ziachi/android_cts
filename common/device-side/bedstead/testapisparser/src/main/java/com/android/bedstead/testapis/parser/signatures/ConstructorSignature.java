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

package com.android.bedstead.testapis.parser.signatures;

import static com.android.bedstead.testapis.parser.signatures.MethodSignature.parameterTypes;

import com.google.common.collect.ImmutableList;

/**
 * Represents a minimal representation of a constructor for comparison purposes.
 */
public final class ConstructorSignature {

    private final String mClassName;
    private final ImmutableList<String> mParameterTypes;

    public ConstructorSignature(String className, ImmutableList<String> parameterTypes) {
        mClassName = className;
        mParameterTypes = parameterTypes;
    }

    public static ConstructorSignature forString(String frameworkClass, String apiString) {
        try {
            String constructor = apiString.substring(apiString.indexOf("public"),
                    apiString.indexOf(";"));

            ImmutableList<String> parameterTypes = parameterTypes(constructor);

            return new ConstructorSignature(frameworkClass, parameterTypes);
        } catch (Exception e) {
            throw new RuntimeException("TestApisReflection: unable to parse Test Api: " + apiString,
                    e);
        }
    }

    @Override
    public String toString() {
        return "ConstructorSignature{" +
                "mClassName='" + mClassName + '\'' +
                ", mParameterTypes=" + mParameterTypes +
                '}';
    }
}
