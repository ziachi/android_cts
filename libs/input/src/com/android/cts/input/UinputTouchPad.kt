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
import android.view.Display
import android.view.InputDevice
import com.android.cts.input.EvdevInputEventCodes.Companion.MT_TOOL_FINGER
import com.android.cts.input.EvdevInputEventCodes.Companion.MT_TOOL_PALM

private fun createTouchPadRegisterCommand(): UinputRegisterCommand {
    val configurationItems = listOf(
        ConfigurationItem("UI_SET_EVBIT", listOf("EV_KEY", "EV_ABS")),
        ConfigurationItem(
            "UI_SET_KEYBIT",
            listOf(
                "BTN_LEFT",
                "BTN_TOOL_FINGER",
                "BTN_TOOL_QUINTTAP",
                "BTN_TOUCH",
                "BTN_TOOL_DOUBLETAP",
                "BTN_TOOL_TRIPLETAP",
                "BTN_TOOL_QUADTAP",
            )
        ),
        ConfigurationItem(
            "UI_SET_ABSBIT",
            listOf(
                "ABS_MT_SLOT",
                "ABS_MT_TOUCH_MAJOR",
                "ABS_MT_TOUCH_MINOR",
                "ABS_MT_ORIENTATION",
                "ABS_MT_POSITION_X",
                "ABS_MT_POSITION_Y",
                "ABS_MT_TOOL_TYPE",
                "ABS_MT_TRACKING_ID",
                "ABS_MT_PRESSURE",
            )
        ),
        ConfigurationItem("UI_SET_PROPBIT", listOf("INPUT_PROP_POINTER", "INPUT_PROP_BUTTONPAD"))
    )

    val absInfoItems = mapOf(
        "ABS_MT_SLOT" to AbsInfo(0, 0, 9, 0, 0, 0),
        "ABS_MT_TOUCH_MAJOR" to AbsInfo(0, 0, 15, 0, 0, 0),
        "ABS_MT_TOUCH_MINOR" to AbsInfo(0, 0, 15, 0, 0, 0),
        "ABS_MT_ORIENTATION" to AbsInfo(0, 0, 1, 0, 0, 0),
        "ABS_MT_POSITION_X" to AbsInfo(0, 0, 1936, 0, 0, 20),
        "ABS_MT_POSITION_Y" to AbsInfo(0, 0, 1057, 0, 0, 20),
        "ABS_MT_TOOL_TYPE" to AbsInfo(0, MT_TOOL_FINGER, MT_TOOL_PALM, 0, 0, 0),
        "ABS_MT_TRACKING_ID" to AbsInfo(0, 0, 65535, 0, 0, 0),
        "ABS_MT_PRESSURE" to AbsInfo(0, 0, 255, 0, 0, 0),
    )

    return UinputRegisterCommand(
        id = 1,
        name = "Test Touchpad (USB)",
        vid = 0x18d1,
        pid = 0xabcd,
        bus = "usb",
        port = "usb:1",
        configuration = configurationItems,
        absInfo = absInfoItems
    )
}

/**
 * A touch pad that has the a fixed resolution (see AbsInfo for ABS_MT_POSITION_X / Y). Gets
 * associated with the specific display.
 */
class UinputTouchPad(
    instrumentation: Instrumentation,
    display: Display,
) : UinputTouchDevice(
    instrumentation,
    display,
    createTouchPadRegisterCommand(),
    InputDevice.SOURCE_TOUCHPAD or InputDevice.SOURCE_MOUSE,
    MT_TOOL_FINGER,
)
