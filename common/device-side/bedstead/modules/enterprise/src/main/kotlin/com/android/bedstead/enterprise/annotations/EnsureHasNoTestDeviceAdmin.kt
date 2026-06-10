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
package com.android.bedstead.enterprise.annotations

import com.android.bedstead.harrier.UserType
import com.android.bedstead.harrier.annotations.AnnotationPriorityRunPrecedence
import com.android.bedstead.harrier.annotations.RequireNotInstantApp
import com.android.bedstead.harrier.annotations.UsesAnnotationExecutor

/**
 * Mark that a test requires that there is no bedstead-controlled admin on the device.
 *
 *
 * Your test configuration may be configured so that this test is only run on a device which has
 * no test admin. Otherwise, you can use `Devicestate` to ensure that the device enters
 * the correct state for the method.
 *
 *
 * Note that this currently does not control the existence of non-bedstead-controlled admins
 * but ideally it will in the future.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER,
        AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME) // TODO(b/206441366): Add instant app support
@RequireNotInstantApp(reason = "Instant Apps cannot run Enterprise Tests")
@UsesAnnotationExecutor(UsesAnnotationExecutor.ENTERPRISE)
annotation class EnsureHasNoTestDeviceAdmin(
        /** Which user type the device admin should not be installed on.  */
        val onUser: UserType = UserType.INSTRUMENTED_USER,
        /**
         * Weight sets the order that annotations will be resolved.
         *
         *
         * Annotations with a lower weight will be resolved before annotations with a higher weight.
         *
         *
         * If there is an order requirement between annotations, ensure that the weight of the
         * annotation which must be resolved first is lower than the one which must be resolved later.
         *
         *
         * Weight can be set to a `AnnotationPriorityRunPrecedence` constant, or to any [int].
         */
        val weight: Int = AnnotationPriorityRunPrecedence.LATE)
