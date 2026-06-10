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

import static com.android.bedstead.testapis.parser.utils.TypeUtils.typeForString;

import java.util.Objects;

import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Represents a minimal representation of a field for comparison purposes
 */
public final class FieldSignature {

    private final String mFrameworkClass;
    private final String mName;
    private final TypeMirror mType;

    private FieldSignature(String frameworkClass, String name, TypeMirror type) {
        this.mFrameworkClass = frameworkClass;
        this.mName = name;
        this.mType = type;
    }

    /** Create a {@link MethodSignature} for the given {@code fieldString}. */
    public static FieldSignature forFieldString(String fieldString, Types types, Elements elements) {
        try {
            String[] parts = fieldString.split(",");

            return new FieldSignature(/* frameworkClass= */ parts[0],
                    /* name= */ parts[1],
                    /* type= */ typeForString(parts[2], types, elements));
        } catch (Exception e) {
            throw new RuntimeException(
                    "TestApisReflection: unable to parse Test Api field: " + fieldString, e);
        }
    }

    public String getFrameworkClass() {
        return mFrameworkClass;
    }

    public String getName() {
        return mName;
    }

    public TypeMirror getType() {
        return mType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FieldSignature)) return false;
        FieldSignature that = (FieldSignature) o;
        return Objects.equals(mFrameworkClass, that.mFrameworkClass)
                && Objects.equals(mName, that.mName) && Objects.equals(mType,
                that.mType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mFrameworkClass, mName, mType);
    }

    @Override
    public String toString() {
        return "FieldSignature{" +
                "mFrameworkClass='" + mFrameworkClass + '\'' +
                ", mName='" + mName + '\'' +
                ", mType=" + mType +
                '}';
    }
}
