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
import android.view.InputDevice.SOURCE_STYLUS
import android.view.InputDevice.SOURCE_TOUCHSCREEN
import com.android.cts.input.EvdevInputEventCodes.Companion.MT_TOOL_FINGER
import com.android.cts.input.EvdevInputEventCodes.Companion.MT_TOOL_PALM
import com.android.cts.input.EvdevInputEventCodes.Companion.MT_TOOL_PEN

private fun createStylusRegisterCommand(display: Display): UinputRegisterCommand {
    val configurationItems = listOf(
        ConfigurationItem("UI_SET_EVBIT", listOf("EV_KEY", "EV_ABS")),
        ConfigurationItem(
            "UI_SET_KEYBIT",
            listOf("BTN_TOUCH", "BTN_TOOL_PEN", "BTN_STYLUS", "BTN_STYLUS2")
        ),
        ConfigurationItem(
            "UI_SET_ABSBIT",
            listOf(
                "ABS_MT_SLOT",
                "ABS_MT_TOUCH_MAJOR",
                "ABS_MT_POSITION_X",
                "ABS_MT_POSITION_Y",
                "ABS_MT_PRESSURE",
                "ABS_MT_TRACKING_ID",
                "ABS_MT_TOOL_TYPE"
            )
        ),
        ConfigurationItem("UI_SET_PROPBIT", listOf("INPUT_PROP_DIRECT"))
    )

    val absInfoItems = mapOf(
        "ABS_MT_SLOT" to AbsInfo(0, 0, 9, 0, 0, 0),
        "ABS_MT_TRACKING_ID" to AbsInfo(0, 0, 9, 0, 0, 0),
        "ABS_MT_TOUCH_MAJOR" to AbsInfo(0, 0, 31, 0, 0, 0),
        "ABS_MT_POSITION_X" to AbsInfo(0, 0, display.mode.physicalWidth - 1, 0, 0, 0),
        "ABS_MT_POSITION_Y" to AbsInfo(0, 0, display.mode.physicalHeight - 1, 0, 0, 0),
        "ABS_MT_PRESSURE" to AbsInfo(0, 0, 255, 0, 0, 0),
        "ABS_MT_TOOL_TYPE" to AbsInfo(0, MT_TOOL_FINGER, MT_TOOL_PALM, 0, 0, 0),
    )

    return UinputRegisterCommand(
        id = 1,
        name = "Test Stylus (USB)",
        vid = 0x18d1,
        pid = 0xabce,
        bus = "usb",
        port = "usb:1",
        configuration = configurationItems,
        absInfo = absInfoItems
    )
}

/**
 * A UinputTouchDevice that has the properties of a typical touchscreen. The touchscreen has the
 * same resolution as the provided display.
 */
class UinputStylus (instrumentation: Instrumentation, display: Display) : UinputTouchDevice(
    instrumentation,
    display,
    createStylusRegisterCommand(display),
    SOURCE_STYLUS or SOURCE_TOUCHSCREEN,
    MT_TOOL_PEN,
)
