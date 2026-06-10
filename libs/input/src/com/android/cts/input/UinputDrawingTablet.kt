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
import com.android.cts.input.EvdevInputEventCodes.Companion.MT_TOOL_PEN

/**
 * Warning! The configuration of the drawing tablet used here is not the best way of emulating an
 * actual drawing tablet:
 * - size shouldn't always match the display
 * - ABS_MT_ axes typically aren't used by these devices.
 * TODO(b/341183598): make the configuration match a real device more closely.
 */
private fun createDrawingTabletRegisterCommand(display: Display): UinputRegisterCommand {
    val configurationItems = listOf(
        ConfigurationItem("UI_SET_EVBIT", listOf("EV_KEY", "EV_ABS")),
        ConfigurationItem("UI_SET_KEYBIT", listOf("BTN_TOUCH", "BTN_TOOL_PEN")),
        ConfigurationItem(
            "UI_SET_ABSBIT",
            listOf(
                "ABS_MT_SLOT",
                "ABS_MT_TOUCH_MAJOR",
                "ABS_MT_POSITION_X",
                "ABS_MT_POSITION_Y",
                "ABS_MT_TRACKING_ID",
                "ABS_MT_TOOL_TYPE"
            )
        ),
        ConfigurationItem("UI_SET_PROPBIT", listOf("INPUT_PROP_POINTER"))
    )

    val absInfoItems = mapOf(
        "ABS_MT_SLOT" to AbsInfo(0, 0, 9, 0, 0, 0),
        "ABS_MT_TRACKING_ID" to AbsInfo(0, 0, 9, 0, 0, 0),
        "ABS_MT_TOUCH_MAJOR" to AbsInfo(0, 0, 31, 0, 0, 0),
        "ABS_MT_POSITION_X" to AbsInfo(0, 0, display.mode.physicalWidth - 1, 0, 0, 0),
        "ABS_MT_POSITION_Y" to AbsInfo(0, 0, display.mode.physicalHeight - 1, 0, 0, 0),
        "ABS_MT_TOOL_TYPE" to AbsInfo(0, MT_TOOL_FINGER, MT_TOOL_PALM, 0, 0, 0),
    )

    return UinputRegisterCommand(
        id = 1,
        name = "Test Drawing Tablet (USB)",
        vid = 0x18d1,
        pid = 0xabcd,
        bus = "usb",
        port = "usb:1",
        configuration = configurationItems,
        absInfo = absInfoItems
    )
}

/**
 * A drawing tablet that has the same resolution as the provided targeted display.
 */
class UinputDrawingTablet(instrumentation: Instrumentation, display: Display) : UinputTouchDevice(
    instrumentation,
    display,
    createDrawingTabletRegisterCommand(display),
    InputDevice.SOURCE_MOUSE or InputDevice.SOURCE_STYLUS,
    MT_TOOL_PEN,
)
