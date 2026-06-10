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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A class to manage annotations marked on the CTS class/method. */
public final class AnnotationManagement {

    private static final String TEST_METADATA_ERROR_MSG = "Not allowed to add new test metadata";

    // A list of annotations marked on the CTS class/method.
    private final List<ClassProfile> mAnnotations = new ArrayList<>();

    // A map to store useful annotation values. For example, values collected from @ApiTest,
    // @CddTest annotations. This values are indicating what the class/method is testing.
    private final Map<String, Set<String>> mTestMetadata = new HashMap<>();

    private boolean mTestMetadataMerged = false;

    /** Gets all xTS annotation values mapped to the class/method. */
    public List<ClassProfile> getAnnotations() {
        return mAnnotations;
    }

    /** Gets all test metadata mapped to the class/method. */
    public Map<String, Set<String>> getTestMetadata() {
        if (!mTestMetadataMerged) {
            resolveNestedTestMetadata();
        }
        return mTestMetadata;
    }

    /** Adds a test metadata. */
    public void addTestMetadata(String type, String value) {
        if (mTestMetadataMerged) {
            throw new RuntimeException(TEST_METADATA_ERROR_MSG);
        }
        mTestMetadata.putIfAbsent(type, new HashSet<>());
        mTestMetadata.get(type).add(value);
    }

    /** Adds an annotation marked on the class/method. */
    public void addAnnotation(ClassProfile classProfile) {
        mAnnotations.add(classProfile);
    }

    /**
     * Merges nested test metadata. For example, a customized annotation @MyAnnotation could be
     * annotated by @ApiTest.
     */
    private void resolveNestedTestMetadata() {
        mTestMetadataMerged = true;
        for (ClassProfile annotation : mAnnotations) {
            annotation.annotationManagement.getTestMetadata().forEach((key, value) -> {
                mTestMetadata.putIfAbsent(key, new HashSet<>());
                mTestMetadata.get(key).addAll(value);
            });
        }
    }
}
