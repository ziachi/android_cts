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

package com.android.bedstead.harrier.components

import com.android.bedstead.harrier.DeviceStateComponent
import com.android.bedstead.harrier.annotations.EnsureInstrumented
import com.android.bedstead.harrier.annotations.InstrumentationComponent
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.instrumentation.InstrumentationComponents

/**
 * A [DeviceStateComponent] that contains logic specific to instrumentation.
 */
class InstrumentationComponent : DeviceStateComponent {

    /**
     * Marks that the test requires a list of instrumented processes to be running.
     *
     * See [EnsureInstrumented].
     */
    fun ensureInstrumented(ensureInstrumentedAnnotation: EnsureInstrumented) {
        validateEnsureInstrumentedAnnotation(ensureInstrumentedAnnotation)
        val instrumentationComponents = ensureInstrumentedAnnotation.value
        val instrumentationTuples = mutableListOf<InstrumentationComponents>()
        for (component: InstrumentationComponent in instrumentationComponents) {
            instrumentationTuples.add(
                InstrumentationComponents(
                    component.packageName,
                    component.runnerClass,
                    component.broadcastReceiverIntentAction
                )
            )
        }
        TestApis.instrumentation().startInstrumentation(instrumentationTuples)
    }

    /**
     * Validates that each [InstrumentationComponent] in the given [EnsureInstrumented]
     * annotation has a unique value for broadcastReceiverIntentAction if it is set.
     *
     * @param ensureInstrumentedAnnotation The [EnsureInstrumented] annotation element to be
     * validated.
     * @throws IllegalArgumentException if duplicate [broadcastReceiverIntentAction] values are
     * found.
     */
    private fun validateEnsureInstrumentedAnnotation(
        ensureInstrumentedAnnotation: EnsureInstrumented) {
        val actions = mutableSetOf<String>()
        for (component: InstrumentationComponent in ensureInstrumentedAnnotation.value) {
            val action = component.broadcastReceiverIntentAction
            if (!action.isEmpty()) {
                if (!actions.add(action)) {
                    throw IllegalArgumentException(
                        "Duplicate broadcastReceiverIntentAction found: "
                                + action
                                + ". Each instrumented component should use a unique broadcast "
                                + "intent action"
                    )
                }
            }
        }
    }
}