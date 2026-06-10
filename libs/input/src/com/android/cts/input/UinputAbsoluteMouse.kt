/*
 * Copyright 2025 The Android Open Source Project
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
import android.view.Display
import android.view.InputDevice
import com.android.cts.input.EvdevInputEventCodes.Companion.MT_TOOL_FINGER

private fun createAbsoluteMouseRegisterCommand(display: Display): UinputRegisterCommand {
    val configurationItems = listOf(
        ConfigurationItem("UI_SET_EVBIT", listOf("EV_KEY", "EV_ABS")),
        ConfigurationItem("UI_SET_KEYBIT", listOf("BTN_TOUCH", "BTN_TOOL_MOUSE", "BTN_MOUSE")),
        ConfigurationItem(
            "UI_SET_ABSBIT",
            listOf(
                "ABS_MT_SLOT",
                "ABS_MT_POSITION_X",
                "ABS_MT_POSITION_Y",
                "ABS_MT_TRACKING_ID",
            )
        ),
    )

    // NOTE: Absolute mice do not work with ABS_MT_TOOL_TYPE when using the slot protocol.
    val absInfoItems = mapOf(
        "ABS_MT_SLOT" to AbsInfo(0, 0, 9, 0, 0, 0),
        "ABS_MT_TRACKING_ID" to AbsInfo(0, 0, 9, 0, 0, 0),
        "ABS_MT_POSITION_X" to AbsInfo(0, 0, display.mode.physicalWidth - 1, 0, 0, 0),
        "ABS_MT_POSITION_Y" to AbsInfo(0, 0, display.mode.physicalHeight - 1, 0, 0, 0),
    )

    return UinputRegisterCommand(
        id = 1,
        name = "Test Absolute Mouse (USB)",
        vid = 0x18d1,
        pid = 0xabce,
        bus = "usb",
        port = "usb:1",
        configuration = configurationItems,
        absInfo = absInfoItems
    )
}

/**
 * An absolute mouse device that has the same resolution as the provided targeted display.
 * This device behaves somewhat similarly to a drawing tablet, but reports itself as a mouse.
 */
class UinputAbsoluteMouse(instrumentation: Instrumentation, display: Display) : UinputTouchDevice(
    instrumentation,
    display,
    createAbsoluteMouseRegisterCommand(display),
    InputDevice.SOURCE_MOUSE,
    MT_TOOL_FINGER, // The default tool type isn't used, since the ABS_MT_TOOL axis isn't supported.
)
