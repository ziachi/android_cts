/*
 * Copyright (C) 2022 The Android Open Source Project
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
import android.graphics.Matrix
import android.graphics.Point
import android.server.wm.CtsWindowInfoUtils
import android.view.Display
import android.view.Surface
import android.view.View
import com.android.cts.input.EvdevInputEventCodes.Companion.ABS_MT_POSITION_X
import com.android.cts.input.EvdevInputEventCodes.Companion.ABS_MT_POSITION_Y
import com.android.cts.input.EvdevInputEventCodes.Companion.ABS_MT_PRESSURE
import com.android.cts.input.EvdevInputEventCodes.Companion.ABS_MT_SLOT
import com.android.cts.input.EvdevInputEventCodes.Companion.ABS_MT_TOOL_TYPE
import com.android.cts.input.EvdevInputEventCodes.Companion.ABS_MT_TRACKING_ID
import com.android.cts.input.EvdevInputEventCodes.Companion.BTN_TOOL_DOUBLETAP
import com.android.cts.input.EvdevInputEventCodes.Companion.BTN_TOOL_FINGER
import com.android.cts.input.EvdevInputEventCodes.Companion.BTN_TOOL_QUADTAP
import com.android.cts.input.EvdevInputEventCodes.Companion.BTN_TOOL_QUINTTAP
import com.android.cts.input.EvdevInputEventCodes.Companion.BTN_TOOL_TRIPLETAP
import com.android.cts.input.EvdevInputEventCodes.Companion.BTN_TOUCH
import com.android.cts.input.EvdevInputEventCodes.Companion.EV_ABS
import com.android.cts.input.EvdevInputEventCodes.Companion.EV_KEY
import com.android.cts.input.EvdevInputEventCodes.Companion.EV_SYN
import com.android.cts.input.EvdevInputEventCodes.Companion.INVALID_MT_TRACKING_ID
import com.android.cts.input.EvdevInputEventCodes.Companion.MT_TOOL_PALM
import com.android.cts.input.EvdevInputEventCodes.Companion.SYN_REPORT
import kotlin.math.round

private fun transformFromScreenToTouchDeviceSpace(x: Int, y: Int, display: Display): Point {
    val displayInfos = CtsWindowInfoUtils.getWindowAndDisplayState().second

    var displayTransform: Matrix? = null
    for (displayInfo in displayInfos) {
        if (displayInfo.displayId == display.displayId) {
            displayTransform = displayInfo.transform
        }
    }

    if (displayTransform == null) {
        throw IllegalStateException(
            "failed to find display transform for display ${display.displayId}"
            )
    }

    // The display transform is the transform from physical display space to
    // logical display space. We need to go from logical display space to
    // physical display space so we take the inverse transform.
    val inverseTransform = Matrix()
    displayTransform.invert(inverseTransform)

    val p = floatArrayOf(x.toFloat(), y.toFloat())
    inverseTransform.mapPoints(p)

    val point = Point(round(p[0]).toInt(), round(p[1]).toInt())

    // We need to apply offset to correctly map the point to discrete coordinate space, this is done
    // to account for the disparity between continuous and discrete coordinates during rotation
    // Refer to frameworks/native/services/inputflinger/docs/input_coordinates.md
    return when (display.rotation) {
        Surface.ROTATION_0 -> point
        Surface.ROTATION_90 -> point.apply { offset(-1, 0) }
        Surface.ROTATION_180 -> point.apply { offset(-1, -1) }
        Surface.ROTATION_270 -> point.apply { offset(0, -1) }
        else -> throw IllegalStateException("unexpected display rotation ${display.rotation}")
    }
}

/**
 * Helper class for configuring and interacting with a [UinputDevice] that uses the evdev
 * multitouch protocol.
 */
open class UinputTouchDevice(
    instrumentation: Instrumentation,
    private val display: Display,
    private val registerCommand: UinputRegisterCommand,
    source: Int,
    private val defaultToolType: Int,
) : AutoCloseable {

    val uinputDevice = UinputDevice(instrumentation, source, registerCommand, display)

    private fun injectEvent(events: IntArray) {
        uinputDevice.injectEvents(events.joinToString(
            prefix = "[",
            postfix = "]",
            separator = ",",
        ))
    }

    fun sendBtnTouch(isDown: Boolean) {
        injectEvent(intArrayOf(EV_KEY, BTN_TOUCH, if (isDown) 1 else 0))
    }

    fun sendBtn(btnCode: Int, isDown: Boolean) {
        injectEvent(intArrayOf(EV_KEY, btnCode, if (isDown) 1 else 0))
    }

    /**
     * Send events signifying a new pointer is being tracked.
     *
     * Note: The [physicalLocation] parameter is specified in the touch device's
     * raw coordinate space, and does not factor display rotation or scaling. Use
     * [touchDown] to start tracking a pointer in screen (a.k.a. logical display)
     * coordinate space.
     */
    fun sendDown(id: Int, physicalLocation: Point) {
        injectEvent(intArrayOf(EV_ABS, ABS_MT_SLOT, id))
        injectEvent(intArrayOf(EV_ABS, ABS_MT_TRACKING_ID, id))
        injectEvent(intArrayOf(EV_ABS, ABS_MT_TOOL_TYPE, defaultToolType))
        injectEvent(intArrayOf(EV_ABS, ABS_MT_POSITION_X, physicalLocation.x))
        injectEvent(intArrayOf(EV_ABS, ABS_MT_POSITION_Y, physicalLocation.y))
    }

    /**
     * Send events signifying a tracked pointer is being moved.
     *
     * Note: The [physicalLocation] parameter is specified in the touch device's
     * raw coordinate space, and does not factor display rotation or scaling.
    */
    fun sendMove(id: Int, physicalLocation: Point) {
        // Use same events of down.
        sendDown(id, physicalLocation)
    }

    fun sendUp(id: Int) {
        injectEvent(intArrayOf(EV_ABS, ABS_MT_SLOT, id))
        injectEvent(intArrayOf(EV_ABS, ABS_MT_TRACKING_ID, INVALID_MT_TRACKING_ID))
    }

    fun sendToolType(id: Int, toolType: Int) {
        injectEvent(intArrayOf(EV_ABS, ABS_MT_SLOT, id))
        injectEvent(intArrayOf(EV_ABS, ABS_MT_TOOL_TYPE, toolType))
    }

    fun sendPressure(pressure: Int) {
        injectEvent(intArrayOf(EV_ABS, ABS_MT_PRESSURE, pressure))
    }

    fun sync() {
        injectEvent(intArrayOf(EV_SYN, SYN_REPORT, 0))
    }

    fun delay(delayMs: Int) {
        uinputDevice.injectDelay(delayMs)
    }

    fun getDeviceId(): Int {
        return uinputDevice.deviceId
    }

    override fun close() {
        uinputDevice.close()
    }

    fun tapOnViewCenter(view: View) {
        val xy = IntArray(2)
        view.getLocationOnScreen(xy)
        val x = xy[0] + view.width / 2
        val y = xy[1] + view.height / 2
        val pointer = touchDown(x, y)
        pointer.lift()
    }

    private val pointerIds = mutableSetOf<Int>()

    /**
     * Send a new pointer to the screen, generating an ACTION_DOWN if there aren't any other
     * pointers currently down, or an ACTION_POINTER_DOWN otherwise.
     * @param x The x coordinate in screen (logical display) space.
     * @param y The y coordinate in screen (logical display) space.
     * @param pressure The pressure value to be used, default not sending pressure.
     */
    @JvmOverloads
    fun touchDown(x: Int, y: Int, pressure: Int? = null): Pointer {
        val pointerId = firstUnusedPointerId()
        pointerIds.add(pointerId)
        return Pointer(pointerId, pressure, x, y)
    }

    private fun firstUnusedPointerId(): Int {
        var id = 0
        while (pointerIds.contains(id)) {
            id++
        }
        return id
    }

    private fun removePointer(id: Int) {
        pointerIds.remove(id)
    }

    private val pointerCount get() = pointerIds.size

    /**
     * A single pointer interacting with the screen. This class simplifies the interactions by
     * removing the need to separately manage the pointer id.
     * Works in the screen (logical display) coordinate space.
     */
    inner class Pointer(
        private val id: Int,
        private val pressure: Int?,
        x: Int,
        y: Int,
    ) : AutoCloseable {
        private var active = true

        init {
            // Send ACTION_DOWN or ACTION_POINTER_DOWN
            sendBtnTouch(true)
            sendDown(id, transformFromScreenToTouchDeviceSpace(x, y, display))
            pressure?.let { sendPressure(pressure) }
            sync()
        }

        /**
         * Send ACTION_MOVE
         * The coordinates provided here should be relative to the screen edge, rather than the
         * window corner. That is, the location should be in the same coordinate space as that
         * returned by View::getLocationOnScreen API rather than View::getLocationInWindow.
         */
        fun moveTo(x: Int, y: Int) {
            if (!active) {
                throw IllegalStateException("Pointer $id is not active, can't move to ($x, $y)")
            }
            sendMove(id, transformFromScreenToTouchDeviceSpace(x, y, display))
            sync()
        }

        fun lift() {
            if (!active) {
                throw IllegalStateException("Pointer $id is not active, already lifted?")
            }
            if (pointerCount == 1) {
                sendBtnTouch(false)
            }
            sendUp(id)
            pressure?.let { sendPressure(0) }
            sync()
            active = false
            removePointer(id)
        }

        /**
         * Send a cancel if this pointer hasn't yet been lifted
         */
        override fun close() {
            if (!active) {
                return
            }
            sendToolType(id, MT_TOOL_PALM)
            sync()
            lift()
        }
    }

    companion object {
        /**
         * The allowed error when making assertions on touch coordinates.
         *
         * Coordinates are transformed from logical display space to physical display space and
         * then rounded to the nearest integer, introducing error. The epsilon value effectively
         * sets the maximum allowed scaling factor for a display. This value allows a maximum scale
         * factor of 2.
         */
        const val TOUCH_COORDINATE_EPSILON = 1.001f

        fun toolBtnForFingerCount(numFingers: Int): Int {
            return when (numFingers) {
                1 -> BTN_TOOL_FINGER
                2 -> BTN_TOOL_DOUBLETAP
                3 -> BTN_TOOL_TRIPLETAP
                4 -> BTN_TOOL_QUADTAP
                5 -> BTN_TOOL_QUINTTAP
                else -> throw IllegalArgumentException("Number of fingers must be between 1 and 5")
            }
        }
    }
}
