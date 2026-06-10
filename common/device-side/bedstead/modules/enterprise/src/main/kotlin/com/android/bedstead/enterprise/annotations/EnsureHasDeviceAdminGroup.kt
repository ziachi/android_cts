/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.bedstead.harrier.annotations.meta.RepeatingAnnotation

/**
 * Group together multiple [EnsureHasDeviceAdmin] annotations.
 */
@Target(
        AnnotationTarget.FUNCTION,
        AnnotationTarget.ANNOTATION_CLASS,
        AnnotationTarget.CLASS
)
@Retention(AnnotationRetention.RUNTIME)
@RepeatingAnnotation
annotation class EnsureHasDeviceAdminGroup(
        vararg val value: EnsureHasDeviceAdmin,
        /**
         * Weight sets the order that annotations will be resolved.
         *
         *
         * Annotations with a lower weight will be resolved before annotations with a higher weight.
         *
         *
         * If there is an order requirement between annotations, ensure that the weight of the
         * annotation which must be resolved first is lower than the one which must be resolved
         * later.
         *
         *
         * Weight can be set to a [AnnotationRunPrecedence] constant, or to any [int].
         */
        val weight: Int = EnsureHasDeviceAdmin.ENSURE_HAS_DEVICE_ADMIN_WEIGHT)
