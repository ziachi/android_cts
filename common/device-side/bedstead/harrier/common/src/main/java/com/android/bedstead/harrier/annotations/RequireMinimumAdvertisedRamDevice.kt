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

package com.android.bedstead.harrier.annotations;

/**
 * Mark that a test should only run when a device has at least the specified
 * amount of advertised RAM.
 *
 * Advertised RAM is the amount of RAM for the device as an end user would
 * encounter in a retail display environment.
 * This can differ from available or total RAM.
 * see: https://developer.android.com/reference/android/app/ActivityManager.MemoryInfo#advertisedMem
 *
 * Only effective on devices with API level 34+.
 *
 * This should be used with `DeviceState`.
 */
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.CLASS,
)
@Retention(AnnotationRetention.RUNTIME)
@UsesAnnotationExecutor(UsesAnnotationExecutor.MAIN)
annotation class RequireMinimumAdvertisedRamDevice (
    val ramDeviceSize: Long,
    val reason: String = "",
    val failureMode: FailureMode = FailureMode.SKIP,

     /**
     * Priority sets the order that annotations will be resolved.
     *
     * <p>Annotations with a lower priority will be resolved before annotations with a higher
     * priority.
     *
     * <p>If there is an order requirement between annotations, ensure that the priority of the
     * annotation which must be resolved first is lower than the one which must be resolved later.
     *
     * <p>Priority can be set to a {@link AnnotationPriorityRunPrecedence} constant, or to any {@link int}.
     */
    val priority: Int = AnnotationPriorityRunPrecedence.FIRST,
)
