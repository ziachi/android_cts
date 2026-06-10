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

package com.android.cts.apimap;

import com.android.cts.ctsprofiles.AnnotationManagement;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Opcodes;

/** A class for collecting annotation values. */
class AnnotationAnalyzer extends AnnotationVisitor {

    private final AnnotationManagement mAnnotationManagement;

    private final String mFieldSignature;

    AnnotationAnalyzer(
            AnnotationManagement annotationManagement,
            String fieldSignature) {
        super(Opcodes.ASM9);
        mAnnotationManagement = annotationManagement;
        mFieldSignature = fieldSignature;
    }

    @Override
    public AnnotationVisitor visitArray(String name) {
        return new AnnotationAnalyzer(mAnnotationManagement, getFieldSignature(name));
    }

    @Override
    public void visit(String name, Object value) {
        mAnnotationManagement.addTestMetadata(
                getFieldSignature(name), String.valueOf(value));
    }

    /** Appends an annotation field name to the field signature. */
    private String getFieldSignature(String fieldName) {
        if (fieldName == null) {
            return mFieldSignature;
        }
        return String.format("%s:%s", mFieldSignature, fieldName);
    }
}
