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
package com.android.bedstead.permissions.annotations

import com.android.bedstead.harrier.annotations.AnnotationPriorityRunPrecedence
import com.android.bedstead.harrier.annotations.FailureMode
import com.android.bedstead.harrier.annotations.UsesAnnotationExecutor
import com.google.auto.value.AutoAnnotation

/**
 * Ensure that the given permission is denied before running the test.
 *
 *
 * Note that use of this annotation implies [RequireNotInstantApp] as instant apps are not
 * able to drop permissions.
 */
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.CLASS
)
@Retention(AnnotationRetention.RUNTIME)
@UsesAnnotationExecutor(UsesAnnotationExecutor.PERMISSIONS)
annotation class EnsureDoesNotHavePermission(
    vararg val value: String,

    val failureMode: FailureMode = FailureMode.FAIL,
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
)

@AutoAnnotation
fun ensureDoesNotHavePermission(value: Array<String>): EnsureDoesNotHavePermission {
    return AutoAnnotation_EnsureDoesNotHavePermissionKt_ensureDoesNotHavePermission(value)
}
