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

import android.companion.virtual.VirtualDeviceManager.VirtualDevice
import android.graphics.Point
import android.hardware.input.VirtualMouse
import android.hardware.input.VirtualMouseConfig
import android.hardware.input.VirtualMouseRelativeEvent
import android.view.Display
import androidx.test.platform.app.InstrumentationRegistry
import com.android.cts.input.EvdevInputEventCodes.Companion.BTN_TOOL_FINGER
import com.android.cts.input.EvdevInputEventCodes.Companion.BTN_TOOL_PEN

enum class TestPointerDevice {

    MOUSE {
        private lateinit var virtualMouse: VirtualMouse

        override fun setUp(
            virtualDevice: VirtualDevice,
            display: Display
        ) {
            virtualMouse =
                virtualDevice.createVirtualMouse(
                    VirtualMouseConfig.Builder()
                        .setVendorId(TEST_VENDOR_ID)
                        .setProductId(TEST_PRODUCT_ID)
                        .setInputDeviceName("Pointer Icon Test Mouse")
                        .setAssociatedDisplayId(display.displayId).build())
        }

        override fun hoverMove(dx: Int, dy: Int) {
            virtualMouse.sendRelativeEvent(
                VirtualMouseRelativeEvent.Builder()
                    .setRelativeX(dx.toFloat())
                    .setRelativeY(dy.toFloat())
                    .build()
            )
        }

        override fun tearDown() {
            if (this::virtualMouse.isInitialized) {
                virtualMouse.close()
            }
        }

        override fun toString(): String = "MOUSE"
    },

    DRAWING_TABLET {
        private lateinit var drawingTablet: UinputTouchDevice
        private lateinit var pointer: Point

        @Suppress("DEPRECATION")
        override fun setUp(
            virtualDevice: VirtualDevice,
            display: Display
        ) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            drawingTablet = UinputDrawingTablet(instrumentation, display)
            // Start with the pointer in the middle of the display.
            pointer = Point((display.width - 1) / 2, (display.height - 1) / 2)
        }

        override fun hoverMove(dx: Int, dy: Int) {
            pointer.offset(dx, dy)
            drawingTablet.sendBtn(BTN_TOOL_PEN, isDown = true)
            drawingTablet.sendDown(
                id = 0,
                physicalLocation = pointer,
            )
            drawingTablet.sync()
        }

        override fun tearDown() {
            if (this::drawingTablet.isInitialized) {
                drawingTablet.close()
            }
        }

        override fun toString(): String = "DRAWING_TABLET"
    },

    TOUCHPAD {
        private lateinit var touchpad: UinputTouchPad

        override fun setUp(
            virtualDevice: VirtualDevice,
            display: Display
        ) {
            touchpad =
                UinputTouchPad(
                    InstrumentationRegistry.getInstrumentation(),
                    display,
                )
        }

        override fun hoverMove(dx: Int, dy: Int) {
            val point = Point(20, 50)
            touchpad.sendBtn(BTN_TOOL_FINGER, isDown = true)
            touchpad.sendBtnTouch(isDown = true)
            touchpad.sendDown(id = 0, point)
            touchpad.sync()

            // TODO(b/310997010): Determine how we can consistently move the mouse pointer by a
            //  fixed number of integer pixels using a touchpad.
            point.offset(dx, dx)
            touchpad.sendMove(id = 0, point)
            touchpad.sync()

            touchpad.sendUp(id = 0)
            touchpad.sendBtnTouch(isDown = false)
            touchpad.sendBtn(BTN_TOOL_FINGER, isDown = false)
            touchpad.sync()
        }

        override fun tearDown() {
            if (this::touchpad.isInitialized) {
                touchpad.close()
            }
        }

        override fun toString(): String = "TOUCHPAD"
    };

    abstract fun setUp(
        virtualDevice: VirtualDevice,
        display: Display,
    )

    abstract fun hoverMove(dx: Int, dy: Int)
    abstract fun tearDown()

    companion object {
        const val TEST_VENDOR_ID = 0x18d1
        const val TEST_PRODUCT_ID = 0xabcd
    }
}
