/*
 * Copyright 2024 The Android Open Source Project
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
import android.view.InputDevice.SOURCE_KEYBOARD
import android.view.InputDevice.SOURCE_STYLUS

private fun createBluetoothStylusRegisterCommand(): UinputRegisterCommand {
    val configurationItems = listOf(
        ConfigurationItem("UI_SET_EVBIT", listOf("EV_KEY")),
        ConfigurationItem("UI_SET_KEYBIT", listOf("BTN_STYLUS", "BTN_STYLUS2", "BTN_STYLUS3"))
    )

    return UinputRegisterCommand(
        id = 1,
        name = "Test Stylus With Buttons (BT)",
        vid = 0x18d1,
        pid = 0xabcd,
        bus = "bluetooth",
        port = "bluetooth:1",
        configuration = configurationItems,
        absInfo = emptyMap(),
    )
}

/**
 * A Bluetooth stylus that only sends buttons. Does not send the position.
 */
class UinputBluetoothStylus(instrumentation: Instrumentation) : UinputDevice(
    instrumentation,
    SOURCE_KEYBOARD or SOURCE_STYLUS,
    createBluetoothStylusRegisterCommand(),
    null // display
)
