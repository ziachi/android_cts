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
package com.android.bedstead.harrier;

import androidx.annotation.NonNull;

import com.android.bedstead.nene.utils.FailureDumper;

import java.lang.annotation.Annotation;

/**
 * Interface used to register a new class which can execute Harrier annotations.
 * <p>
 * This can be used to add additional harrier-compatible annotations without modifying harrier
 * <p>
 * Ideally instances of this interface shouldn't contain state but should reference to
 * extension functions or use objects of {@link DeviceStateComponent}
 * with {@link BedsteadServiceLocator} as the provider
 */
// This is written in Java because Kotlin interfaces can't expose default methods to Java
public interface AnnotationExecutor extends FailureDumper {
    /**
     * Called when an annotation should be applied.
     *
     * <p>This should take care of recording any state necessary to correctly restore state after
     * the test.
     * Annotations that don't need the context of device state components
     * could be handled by extension functions like: {@link AnnotationLogicExtensionsKt}
     */
    void applyAnnotation(@NonNull Annotation annotation);

    /**
     * Called when a test has failed which used this annotation executor.
     */
    default void onTestFailed(@NonNull Throwable exception) {}
}
