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

import java.util.List;
import java.util.Objects;

import javax.lang.model.util.Elements;

/**
 * Represents a minimal representation of a class for comparison purposes.
 */
public final class ClassSignature {

    private final String mName;
    private final String mPackageName;
    private final ConstructorSignature
            mConstructorSignature;
    private final List<MethodSignature> mMethodSignatures;

    public ClassSignature(String packageName, String name,
            ConstructorSignature constructorSignature, List<MethodSignature> methodSignatures) {
        mPackageName = packageName;
        mName = name;
        mConstructorSignature = constructorSignature;
        mMethodSignatures = methodSignatures;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public String getName() {
        return mName;
    }

    public List<MethodSignature> getMethodSignatures() {
        return mMethodSignatures;
    }

    /**
     * Checks if this is a "Test class" (a class marked as @TestApi).
     * <p>
     * Note: We are parsing {@code test-current.txt} and there is not enough information in the
     * text file to know if a class defined here is a "Test class". We assume it is a "Test class"
     * if it is present in test-current.txt and is inaccessible when test sdk is disabled.
     */
    public boolean isTestClass(Elements elements) {
        return elements.getTypeElement(getName()) == null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClassSignature)) return false;
        ClassSignature that = (ClassSignature) o;
        return Objects.equals(mName, that.mName) && Objects.equals(mPackageName,
                that.mPackageName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mName, mPackageName);
    }

    @Override
    public String toString() {
        return "ClassSignature{" +
                "mName='" + mName + '\'' +
                ", mConstructorSignature=" + mConstructorSignature +
                ", mMethodSignatures=" + mMethodSignatures +
                '}';
    }
}
