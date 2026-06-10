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

package android.view.cts.input

import android.app.Instrumentation
import android.view.InputDevice
import com.android.cts.input.ConfigurationItem
import com.android.cts.input.UinputDevice
import com.android.cts.input.UinputRegisterCommand

private fun createRegisterCommand(): UinputRegisterCommand {
    val configurationItems = listOf(
        ConfigurationItem("UI_SET_EVBIT", listOf("EV_KEY", "EV_FF")),
        ConfigurationItem("UI_SET_KEYBIT", listOf("KEY_0", "KEY_1", "KEY_2", "KEY_3")),
        ConfigurationItem("UI_SET_FFBIT", listOf("FF_RUMBLE"))
    )
    val id = 1
    val ffEffectsMax = 1

    return UinputRegisterCommand(
        id,
        "Gamepad FF (USB Test)",
        0x18d1,
        0xabcd,
        "usb",
        "usb:1",
        configurationItems,
        mapOf(),
        ffEffectsMax,
    )
}

/**
 * A virtual game pad.
 */
class UinputGoogleGamepad(instrumentation: Instrumentation) : UinputDevice(
    instrumentation,
    InputDevice.SOURCE_KEYBOARD,
    createRegisterCommand(),
    null // display
)
