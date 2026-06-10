/*
 * Copyright 2023 The Android Open Source Project
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
package android.hardware.input.cts.virtualcreators

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.MotionEvent.PointerCoords
import android.view.MotionEvent.PointerProperties

/**
 * Static utilities for creating input events to verify the functionality of virtual input devices.
 */
@Suppress("ktlint:standard:comment-wrapping")
object VirtualInputEventCreator {

    fun createMouseEvent(
        action: Int,
        x: Float,
        y: Float,
        buttonState: Int,
        pressure: Float,
        relativeX: Float = 0f,
        relativeY: Float = 0f,
        hScroll: Float = 0f,
        vScroll: Float = 0f
    ): MotionEvent {
        val pointerProperties = PointerProperties()
        pointerProperties.toolType = MotionEvent.TOOL_TYPE_MOUSE
        val pointerCoords = PointerCoords()
        pointerCoords.setAxisValue(MotionEvent.AXIS_X, x)
        pointerCoords.setAxisValue(MotionEvent.AXIS_Y, y)
        pointerCoords.setAxisValue(MotionEvent.AXIS_RELATIVE_X, relativeX)
        pointerCoords.setAxisValue(MotionEvent.AXIS_RELATIVE_Y, relativeY)
        pointerCoords.setAxisValue(MotionEvent.AXIS_PRESSURE, pressure)
        pointerCoords.setAxisValue(MotionEvent.AXIS_HSCROLL, hScroll)
        pointerCoords.setAxisValue(MotionEvent.AXIS_VSCROLL, vScroll)
        return MotionEvent.obtain(
            /* downTime= */ 0,
            /* eventTime= */ 0,
            action,
            /* pointerCount= */ 1,
            arrayOf(pointerProperties),
            arrayOf(pointerCoords),
            /* metaState= */ 0,
            buttonState,
            /* xPrecision= */ 1f,
            /* yPrecision= */ 1f,
            /* deviceId= */ 0,
            /* edgeFlags= */ 0,
            InputDevice.SOURCE_MOUSE,
            /* flags= */ 0
        )
    }

    fun createTouchscreenEvent(
        action: Int,
        x: Float,
        y: Float,
        pressure: Float,
        size: Float,
        axisSize: Float
    ): MotionEvent {
        val pointerProperties = PointerProperties()
        pointerProperties.toolType = MotionEvent.TOOL_TYPE_FINGER
        val pointerCoords = PointerCoords()
        pointerCoords.setAxisValue(MotionEvent.AXIS_X, x)
        pointerCoords.setAxisValue(MotionEvent.AXIS_Y, y)
        pointerCoords.setAxisValue(MotionEvent.AXIS_PRESSURE, pressure)
        pointerCoords.setAxisValue(MotionEvent.AXIS_SIZE, size)
        pointerCoords.setAxisValue(MotionEvent.AXIS_TOUCH_MAJOR, axisSize)
        pointerCoords.setAxisValue(MotionEvent.AXIS_TOUCH_MINOR, axisSize)
        pointerCoords.setAxisValue(MotionEvent.AXIS_TOOL_MAJOR, axisSize)
        pointerCoords.setAxisValue(MotionEvent.AXIS_TOOL_MINOR, axisSize)
        return MotionEvent.obtain(
            /* downTime= */ 0,
            /* eventTime= */ 0,
            action,
            /* pointerCount= */ 1,
            arrayOf(pointerProperties),
            arrayOf(pointerCoords),
            /* metaState= */ 0,
            /* buttonState= */ 0,
            /* xPrecision= */ 1f,
            /* yPrecision= */ 1f,
            /* deviceId= */ 0,
            /* edgeFlags= */ 0,
            InputDevice.SOURCE_TOUCHSCREEN,
            /* flags= */ 0
        )
    }

    fun createNavigationTouchpadMotionEvent(
        action: Int,
        x: Float,
        y: Float,
        size: Float,
        axisSize: Float
    ): MotionEvent {
        val pointerProperties = PointerProperties()
        pointerProperties.toolType = MotionEvent.TOOL_TYPE_FINGER
        val pointerCoords = PointerCoords()
        pointerCoords.setAxisValue(MotionEvent.AXIS_X, x)
        pointerCoords.setAxisValue(MotionEvent.AXIS_Y, y)
        pointerCoords.setAxisValue(MotionEvent.AXIS_PRESSURE, 1f /* value */)
        pointerCoords.setAxisValue(MotionEvent.AXIS_SIZE, size)
        pointerCoords.setAxisValue(MotionEvent.AXIS_TOUCH_MAJOR, axisSize)
        pointerCoords.setAxisValue(MotionEvent.AXIS_TOUCH_MINOR, axisSize)
        pointerCoords.setAxisValue(MotionEvent.AXIS_TOOL_MAJOR, axisSize)
        pointerCoords.setAxisValue(MotionEvent.AXIS_TOOL_MINOR, axisSize)
        return MotionEvent.obtain(
            /* downTime= */ 0,
            /* eventTime= */ 0,
            action,
            /* pointerCount= */ 1,
            arrayOf(pointerProperties),
            arrayOf(pointerCoords),
            /* metaState= */ 0,
            /* buttonState= */ 0,
            /* xPrecision= */ 1f,
            /* yPrecision= */ 1f,
            /* deviceId= */ 0,
            /* edgeFlags= */ 0,
            InputDevice.SOURCE_TOUCH_NAVIGATION,
            /* flags= */ 0
        )
    }

    fun createStylusHoverMotionEvent(
        action: Int,
        x: Float,
        y: Float,
        toolType: Int,
        buttonState: Int = 0
    ): MotionEvent {
        return createStylusEvent(action, toolType, x, y, pressure = 0f, buttonState)
    }

    fun createStylusTouchMotionEvent(
        action: Int,
        x: Float,
        y: Float,
        toolType: Int,
        tilt: Float,
        orientation: Float
    ): MotionEvent {
        return createStylusEvent(
            action,
            toolType,
            x,
            y,
            pressure = 1f,
            buttonState = 0,
            tilt,
            orientation
        )
    }

    fun createStylusTouchMotionEvent(
        action: Int,
        x: Float,
        y: Float,
        toolType: Int,
        buttonState: Int = 0
    ): MotionEvent {
        return createStylusEvent(action, toolType, x, y, pressure = 1f, buttonState)
    }

    fun createStylusEvent(
        action: Int,
        toolType: Int,
        x: Float,
        y: Float,
        pressure: Float,
        buttonState: Int
    ): MotionEvent {
        return createStylusEvent(
            action,
            toolType,
            x,
            y,
            pressure,
            buttonState,
            tilt = 0f,
            orientation = 0f
        )
    }

    private fun createStylusEvent(
        action: Int,
        toolType: Int,
        x: Float,
        y: Float,
        pressure: Float,
        buttonState: Int,
        tilt: Float,
        orientation: Float
    ): MotionEvent {
        val pointerProperties = PointerProperties()
        pointerProperties.toolType = toolType
        val pointerCoords = PointerCoords()
        pointerCoords.setAxisValue(MotionEvent.AXIS_X, x)
        pointerCoords.setAxisValue(MotionEvent.AXIS_Y, y)
        pointerCoords.setAxisValue(MotionEvent.AXIS_PRESSURE, pressure)
        pointerCoords.setAxisValue(MotionEvent.AXIS_TILT, tilt)
        pointerCoords.setAxisValue(MotionEvent.AXIS_ORIENTATION, orientation)
        return MotionEvent.obtain(
            /* downTime= */ 0,
            /* eventTime= */ 0,
            action,
            /* pointerCount= */ 1,
            arrayOf(pointerProperties),
            arrayOf(pointerCoords),
            /* metaState= */ 0,
            buttonState,
            /* xPrecision= */ 1f,
            /* yPrecision= */ 1f,
            /* deviceId= */ 0,
            /* edgeFlags= */ 0,
            InputDevice.SOURCE_STYLUS or InputDevice.SOURCE_TOUCHSCREEN,
            /* flags= */ 0
        )
    }

    fun createRotaryEvent(scrollAmount: Float): MotionEvent {
        val pointerProperties = PointerProperties()
        pointerProperties.toolType = MotionEvent.TOOL_TYPE_UNKNOWN
        val pointerCoords = PointerCoords()
        pointerCoords.setAxisValue(MotionEvent.AXIS_SCROLL, scrollAmount)
        return MotionEvent.obtain(
            /* downTime= */ 0,
            /* eventTime= */ 0,
            MotionEvent.ACTION_SCROLL,
            /* pointerCount= */ 1,
            arrayOf(pointerProperties),
            arrayOf(pointerCoords),
            /* metaState= */ 0,
            /* buttonState= */ 0,
            /* xPrecision= */ 1f,
            /* yPrecision= */ 1f,
            /* deviceId= */ 0,
            /* edgeFlags= */ 0,
            InputDevice.SOURCE_ROTARY_ENCODER,
            /* flags= */ 0
        )
    }

    fun createKeyboardEvent(action: Int, code: Int): KeyEvent {
        return createKeyEvent(action, code, InputDevice.SOURCE_KEYBOARD)
    }

    fun createDpadEvent(action: Int, code: Int): KeyEvent {
        return createKeyEvent(action, code, InputDevice.SOURCE_KEYBOARD or InputDevice.SOURCE_DPAD)
    }

    private fun createKeyEvent(action: Int, code: Int, source: Int): KeyEvent {
        return KeyEvent(
            /* downTime= */ 0,
            /* eventTime= */ 0,
            action,
            code,
            /* repeat= */ 0,
            /* metaState= */ 0,
            /* deviceId= */ 0,
            /* scancode= */ 0,
            /* flags= */ 0,
            source
        )
    }
}
