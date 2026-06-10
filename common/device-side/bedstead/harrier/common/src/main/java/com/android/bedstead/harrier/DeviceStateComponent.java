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

/**
 * Interface used by classes which respond to key events in DeviceState
 * These classes must be loaded in {@link BedsteadServiceLocator} to receive the callbacks
 */
// This is written in Java because Kotlin interfaces can't expose default methods to Java
public interface DeviceStateComponent {
    /**
     * Restore the previous state of any shareable changes.
     */
    default void teardownShareableState() {}

    /**
     * Restore the previous state of any non-shareable changes.
     */
    default void teardownNonShareableState() {}

    /**
     * Prepare component for test
     */
    default void prepareTestState() {}
}
