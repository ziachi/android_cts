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

/**
 * Represents a minimal representation of a package for comparison purposes.
 */
public final class PackageSignature {

    private final String mName;

    private final List<ClassSignature> mClassSignatures;

    public PackageSignature(String name, List<ClassSignature> classSignature) {
        mName = name;
        mClassSignatures = classSignature;
    }

    public String getName() {
        return mName;
    }

    public List<ClassSignature> getClassSignatures() {
        return mClassSignatures;
    }

    @Override
    public String toString() {
        return "PackageSignature{" +
                "mName='" + mName + '\'' +
                ", mClassSignature=" + mClassSignatures +
                '}';
    }
}
