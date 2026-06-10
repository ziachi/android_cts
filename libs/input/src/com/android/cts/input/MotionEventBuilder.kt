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

import android.view.Display
import android.view.InputDevice
import android.view.MotionEvent

const val DEFAULT_DEVICE_ID = -1

private fun isFromSource(source: Int, test: Int): Boolean {
    return (source and test) == test
}

class MotionEventBuilder(val action: Int, val source: Int) {
    private var deviceId = DEFAULT_DEVICE_ID
    private var downTime = System.currentTimeMillis()
    private var eventTime = downTime
    private var displayId = Display.DEFAULT_DISPLAY
    private var actionButton = 0
    private var metaState = 0
    private var buttonState = 0
    private var xPrecision = 0f
    private var yPrecision = 0f
    private var flags = 0
    private var rawXCursorPosition: Float? = null
    private var rawYCursorPosition: Float? = null
    private var classification = MotionEvent.CLASSIFICATION_NONE
    private val edgeFlags = 0
    private val pointers = mutableListOf<PointerBuilder>()

    // Builder methods
    fun deviceId(deviceId: Int) = apply { this.deviceId = deviceId }
    fun downTime(downTime: Long) = apply { this.downTime = downTime }
    fun eventTime(eventTime: Long) = apply { this.eventTime = eventTime }
    fun displayId(displayId: Int) = apply { this.displayId = displayId }
    fun actionButton(actionButton: Int) = apply { this.actionButton = actionButton }
    fun metaState(metaState: Int) = apply { this.metaState = metaState }
    fun buttonState(buttonState: Int) = apply { this.buttonState = buttonState }
    fun xPrecision(xPrecision: Float) = apply { this.xPrecision = xPrecision}
    fun yPrecision(yPrecision: Float) = apply { this.yPrecision = yPrecision}
    fun addFlag(flags: Int) = apply { this.flags = this.flags or flags }
    fun rawXCursorPosition(rawXCursorPosition: Float?) = apply {
        this.rawXCursorPosition = rawXCursorPosition
    }
    fun rawYCursorPosition(rawYCursorPosition: Float?) = apply {
        this.rawYCursorPosition = rawYCursorPosition
    }
    fun pointer(pointer: PointerBuilder) = apply { pointers.add(pointer) }
    fun classification(classification: Int) = apply { this.classification = classification }

    fun build(): MotionEvent {
        val pointerProperties = pointers.map { it.buildProperties() }
        val pointerCoords = pointers.map { it.buildCoords() }

        // If the mouse cursor position is not explicitly specified, use the X and Y coordinates of
        // the first pointer, if this is a mouse event.
        if (isFromSource(source, InputDevice.SOURCE_MOUSE) &&
            (rawXCursorPosition == null || rawYCursorPosition == null)) {
            rawXCursorPosition = pointerCoords[0].x
            rawYCursorPosition = pointerCoords[0].y
        }

        return MotionEvent.obtain(
            downTime, eventTime, action, pointerProperties.size, pointerProperties.toTypedArray(),
            pointerCoords.toTypedArray(), metaState, buttonState, xPrecision, yPrecision, deviceId,
            edgeFlags, source, displayId, flags, classification
        )!!
    }
}
