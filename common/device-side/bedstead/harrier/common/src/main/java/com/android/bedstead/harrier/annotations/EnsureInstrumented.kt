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
package com.android.bedstead.harrier.annotations

/**
 * Marks that the test requires a list of instrumented processes to be running. It assumes that
 * instrumented apks are installed. If installed, it will start those instrumented processes
 * irrespective of whether they are running or not.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@UsesAnnotationExecutor(UsesAnnotationExecutor.MAIN)
annotation class EnsureInstrumented(
    /**
     * An array of [InstrumentationComponent], each containing a package name, runner class, and an
     * optional parameter [broadcastReceiverIntentAction]. The test will start instrumented processes
     * for these package names using the corresponding runner classes. The value of
     * broadcastReceiverIntentAction should be unique. For components where this parameter is set, the
     * executing thread will block until all the corresponding instrumented processes have sent a
     * broadcast with the specified intent action.
     */
    val value: Array<InstrumentationComponent>,

    /**
     * Priority sets the order that annotations will be resolved.
     *
     * Annotations with a lower priority will be resolved before annotations with a higher priority.
     *
     * If there is an order requirement between annotations, ensure that the priority of the
     * annotation which must be resolved first is lower than the one which must be resolved later.
     *
     * Priority can be set to a [AnnotationPriorityRunPrecedence] constant, or to any [int].
     */
    val priority: Int = AnnotationPriorityRunPrecedence.LATE,
)

/**
 * Annotation class representing a pair of package name and runner class.
 *
 * @property packageName The package name of the instrumented APK.
 * @property runnerClass The runner class which is a subclass of [AndroidJUnitRunner] to be used for
 *   instrumentation, specified as a string.
 * @property broadcastReceiverIntentAction The optional [Intent] action for a broadcast receiver.
 *   After starting the instrumentation, if this param is set, then the executing thread will block
 *   until a broadcast with the specified intent action is received.
 */
annotation class InstrumentationComponent(
    val packageName: String,
    val runnerClass: String,
    val broadcastReceiverIntentAction: String = "",
)