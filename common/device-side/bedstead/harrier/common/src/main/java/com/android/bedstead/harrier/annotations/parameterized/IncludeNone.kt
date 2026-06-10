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
package com.android.bedstead.harrier.annotations.parameterized

import com.android.bedstead.harrier.annotations.AnnotationPriorityRunPrecedence
import com.android.bedstead.harrier.annotations.ParameterizedAnnotationScope
import com.android.bedstead.harrier.annotations.meta.ParameterizedAnnotation
import com.google.auto.value.AutoAnnotation

/**
 * Parameterize a test but do not generate a run.
 */
@Target(AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.CLASS)
@Retention(
    AnnotationRetention.RUNTIME)
@ParameterizedAnnotation(scope = ParameterizedAnnotationScope.ENTERPRISE)
annotation class IncludeNone(
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
    val priority: Int = AnnotationPriorityRunPrecedence.EARLY)

@AutoAnnotation
fun includeNone(): IncludeNone {
    return AutoAnnotation_IncludeNoneKt_includeNone()
}
