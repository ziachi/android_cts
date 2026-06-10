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

import com.android.bedstead.harrier.UserType.INSTRUMENTED_USER

import com.android.bedstead.harrier.annotations.AnnotationPriorityRunPrecedence
import com.android.bedstead.harrier.annotations.RequireFeature
import com.android.bedstead.harrier.annotations.RequireNotInstantApp
import com.android.bedstead.harrier.UserType
import com.android.bedstead.harrier.annotations.UsesAnnotationExecutor
import com.android.bedstead.nene.packages.CommonPackages
import com.android.queryable.annotations.Query
import java.lang.annotation.Repeatable

/**
 * Mark that a test requires that a device admin is available on the device.
 *
 * <p>Your test configuration may be configured so that this test is only run on a device which has
 * a device admin. Otherwise, you can use {@code DeviceState} to ensure that the device enters
 * the correct state for the method. If using {@code DeviceState}, you can use
 * {@code DeviceState#deviceAdmin()} to interact with the device admin.
 */
@Target(
        AnnotationTarget.FUNCTION,
        AnnotationTarget.ANNOTATION_CLASS,
        AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@RequireFeature(CommonPackages.FEATURE_DEVICE_ADMIN)
@RequireNotInstantApp(reason = "Instant Apps cannot run Enterprise Tests")
@Repeatable(EnsureHasDeviceAdminGroup::class)
@UsesAnnotationExecutor(UsesAnnotationExecutor.ENTERPRISE)
annotation class EnsureHasDeviceAdmin(
        /** A key which uniquely identifies the device admin for the test. */
        val key: String = DEFAULT_KEY,
        /** Which user type the device admin should be installed on. */
        val onUser: UserType = INSTRUMENTED_USER,
        /**
         * Whether this device admin should be returned by calls to `Devicestate#dpc()`.
         *
         *
         * Only one policy manager per test should be marked as primary.
         */
        val isPrimary: Boolean = false,
        /**
         * Requirements for the Device Admin
         */
        val dpc: Query = Query(),
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
        val weight: Int = ENSURE_HAS_DEVICE_ADMIN_WEIGHT) {
    companion object {
        const val ENSURE_HAS_DEVICE_ADMIN_WEIGHT = AnnotationPriorityRunPrecedence.MIDDLE
        const val DEFAULT_KEY = "remoteDeviceAdmin"
    }
}
