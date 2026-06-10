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
import com.android.bedstead.harrier.annotations.EnsureTestAppInstalled
import com.android.bedstead.harrier.annotations.UsesAnnotationExecutor
import com.android.queryable.annotations.Query

/**
 * Mark that a test requires the given test app to be installed on the given user and marked as primary.
 * This testApp will be returned by calls to `DeviceState#dpc()`.
 *
 *
 * You should use `DeviceState` to ensure that the device enters
 * the correct state for the method.
 *
 *
 * Only one policy manager per test should be marked as primary.
 */
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.CLASS
)
@Retention(
    AnnotationRetention.RUNTIME
)
@JvmRepeatable(
    EnsureTestAppInstalledAsPrimaryDPCGroup::class
)
@UsesAnnotationExecutor(UsesAnnotationExecutor.ENTERPRISE)
annotation class EnsureTestAppInstalledAsPrimaryDPC(
    /**
     * A key which uniquely identifies the test app for the test.
     *
     *
     * This can be used with e.g. `DeviceState#testApp` and
     * [AdditionalQueryParameters].
     */
    val key: String = EnsureTestAppInstalled.DEFAULT_KEY,
    /**
     * Query specifying the testapp. Defaults to any test app.
     */
    val query: Query = Query(),
    /**
     * The user the testApp should be installed on.
     */
    val onUser: UserType = UserType.INSTRUMENTED_USER,
    /**
     * Priority sets the order that annotations will be resolved.
     *
     *
     * Annotations with a lower priority will be resolved before annotations with a higher
     * priority.
     *
     *
     * If there is an order requirement between annotations, ensure that the priority of the
     * annotation which must be resolved first is lower than the one which must be resolved later.
     *
     *
     * Priority can be set to a [AnnotationPriorityRunPrecedence] constant, or to any [int].
     */
    val priority: Int = AnnotationPriorityRunPrecedence.MIDDLE
) {
    companion object {
        const val ENSURE_TEST_APP_INSTALLED_AS_PRIMARY_DPC_PRIORITY: Int =
            AnnotationPriorityRunPrecedence.MIDDLE
    }
}

