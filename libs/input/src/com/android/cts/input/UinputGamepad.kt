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

private fun createGamepadRegisterCommand(): UinputRegisterCommand {
    val configurationItems = listOf(
        ConfigurationItem("UI_SET_EVBIT", listOf("EV_KEY")),
        ConfigurationItem("UI_SET_KEYBIT", listOf("BTN_GAMEPAD"))
    )

    return UinputRegisterCommand(
        id = 1,
        name = "Test Gamepad (USB)",
        vid = 0x18d1,
        pid = 0xabcd,
        bus = "usb",
        port = "usb:1",
        configuration = configurationItems,
        absInfo = emptyMap(),
    )
}

/**
 * A gamepad that currently only sends a single button.
 */
class UinputGamepad(instrumentation: Instrumentation) : UinputDevice(
    instrumentation,
    SOURCE_KEYBOARD,
    createGamepadRegisterCommand(),
    null // display
)
