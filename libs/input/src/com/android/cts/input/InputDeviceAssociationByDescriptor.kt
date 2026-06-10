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

package com.android.cts.input

import android.app.Instrumentation
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.server.wm.WindowManagerStateHelper
import android.view.Display
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.compatibility.common.util.TestUtils.waitOn
import java.util.concurrent.TimeUnit

/**
 * Represents an association between an input device and a display. The association is done by
 * mapping the device descriptor against the display unique id.
 *
 * Once this association is closed, it can't be reused anymore. A new association will need to
 * be created if required.
 */
class InputDeviceAssociationByDescriptor private constructor(
        private val inputManager: InputManager,
        val inputDeviceDescriptor: String,
        val associatedDisplay: Display
) : AutoCloseable {

    companion object {
        private val DISPLAY_ASSOCIATION_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(5)
        private const val PERMISSION_ASSOCIATE_INPUT_DEVICE_TO_DISPLAY =
                "android.permission.ASSOCIATE_INPUT_DEVICE_TO_DISPLAY"
    }

    class Associator(private val instrumentation: Instrumentation) {

        private val inputManager =
                instrumentation.context.getSystemService(InputManager::class.java)

        /**
         * Associate the device (represented by deviceId) against the display, both passed as
         * parameters.
         *
         * @return an instance of {@link InputDeviceAssociationByDescriptor} representing the
         *     device and display association
         * @throws AssertionError is raised if the association takes more than the time defined in
         *     {@code DISPLAY_ASSOCIATION_TIMEOUT_MILLIS}
         */
        fun associate(deviceId: Int, display: Display): InputDeviceAssociationByDescriptor {
            val descriptor = inputManager.getInputDevice(deviceId)!!.descriptor
            runWithShellPermissionIdentity({
                inputManager.addUniqueIdAssociationByDescriptor(
                        descriptor,
                        display.uniqueId!!
                )
            }, PERMISSION_ASSOCIATE_INPUT_DEVICE_TO_DISPLAY)

            waitForDeviceUpdatesUntil {
                val inputDevice = inputManager.getInputDevice(deviceId)!!
                inputDevice.associatedDisplayId == display.displayId
            }

            // Before continuing, ensure the display has finished any transitions. Associating an
            // input device can sometimes cause a display configuration change, which may trigger a
            // new transition.
            WindowManagerStateHelper().waitForAppTransitionIdleOnDisplay(display.displayId)
            instrumentation.uiAutomation.syncInputTransactions()

            return InputDeviceAssociationByDescriptor(inputManager, descriptor, display)
        }

        private fun waitForDeviceUpdatesUntil(condition: () -> Boolean) {
            val lockForInputDeviceUpdates = Object()
            val inputDeviceListener = object : InputManager.InputDeviceListener {
                override fun onInputDeviceAdded(deviceId: Int) {
                    synchronized(lockForInputDeviceUpdates) { lockForInputDeviceUpdates.notify() }
                }

                override fun onInputDeviceRemoved(deviceId: Int) {
                    synchronized(lockForInputDeviceUpdates) { lockForInputDeviceUpdates.notify() }
                }

                override fun onInputDeviceChanged(deviceId: Int) {
                    synchronized(lockForInputDeviceUpdates) { lockForInputDeviceUpdates.notify() }
                }
            }
            inputManager.registerInputDeviceListener(
                    inputDeviceListener,
                    Handler(Looper.getMainLooper())
            )
            waitOn(
                    lockForInputDeviceUpdates,
                    condition,
                    DISPLAY_ASSOCIATION_TIMEOUT_MILLIS,
                    null // conditionName
            )
            inputManager.unregisterInputDeviceListener(inputDeviceListener)
        }
    }

    private var closed = false

    override fun close() {
        if (!closed) {
            runWithShellPermissionIdentity({
                inputManager.removeUniqueIdAssociationByDescriptor(inputDeviceDescriptor)
            }, PERMISSION_ASSOCIATE_INPUT_DEVICE_TO_DISPLAY)
            closed = true
        }
    }

    override fun toString() = "[inputDeviceDescriptor: $inputDeviceDescriptor" +
            ", associatedDisplay: $associatedDisplay, closed: $closed]"
}
