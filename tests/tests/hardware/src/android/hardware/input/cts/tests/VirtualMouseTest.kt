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
package android.hardware.input.cts.tests

import android.companion.virtualdevice.flags.Flags
import android.graphics.PointF
import android.hardware.input.VirtualMouse
import android.hardware.input.VirtualMouseButtonEvent
import android.hardware.input.VirtualMouseRelativeEvent
import android.hardware.input.VirtualMouseScrollEvent
import android.hardware.input.cts.virtualcreators.VirtualInputDeviceCreator
import android.hardware.input.cts.virtualcreators.VirtualInputEventCreator
import android.platform.test.annotations.RequiresFlagsDisabled
import android.platform.test.annotations.RequiresFlagsEnabled
import android.view.InputEvent
import android.view.MotionEvent
import com.android.compatibility.common.util.SystemUtil
import com.android.cts.input.DefaultPointerSpeedRule
import junitparams.JUnitParamsRunner
import junitparams.Parameters
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(JUnitParamsRunner::class)
class VirtualMouseTest : VirtualDeviceTestCase() {
    @get:Rule
    val mDefaultPointerSpeedRule: DefaultPointerSpeedRule = DefaultPointerSpeedRule()

    private lateinit var mVirtualMouse: VirtualMouse

    override fun onSetUpVirtualInputDevice() {
        mVirtualMouse = VirtualInputDeviceCreator.createAndPrepareMouse(
            mVirtualDevice, DEVICE_NAME,
            mVirtualDisplay.display
        ).device
    }

    @Test
    fun sendButtonEvent() {
        val startPosition: PointF = mVirtualMouse.cursorPosition
        mVirtualMouse.sendButtonEvent(
            VirtualMouseButtonEvent.Builder()
                .setAction(VirtualMouseButtonEvent.ACTION_BUTTON_PRESS)
                .setButtonCode(VirtualMouseButtonEvent.BUTTON_PRIMARY)
                .build()
        )
        mVirtualMouse.sendButtonEvent(
            VirtualMouseButtonEvent.Builder()
                .setAction(VirtualMouseButtonEvent.ACTION_BUTTON_RELEASE)
                .setButtonCode(VirtualMouseButtonEvent.BUTTON_PRIMARY)
                .build()
        )
        val buttonPressEvent: MotionEvent = VirtualInputEventCreator.createMouseEvent(
            MotionEvent.ACTION_BUTTON_PRESS,
            startPosition.x,
            startPosition.y,
            MotionEvent.BUTTON_PRIMARY,
            pressure = 1f
        )
        buttonPressEvent.actionButton = MotionEvent.BUTTON_PRIMARY
        val buttonReleaseEvent: MotionEvent = VirtualInputEventCreator.createMouseEvent(
            MotionEvent.ACTION_BUTTON_RELEASE,
            startPosition.x,
            startPosition.y,
            buttonState = 0,
            pressure = 0f
        )
        buttonReleaseEvent.actionButton = MotionEvent.BUTTON_PRIMARY
        verifyEvents(
            listOf<InputEvent>(
                VirtualInputEventCreator.createMouseEvent(
                    MotionEvent.ACTION_DOWN,
                    startPosition.x,
                    startPosition.y,
                    MotionEvent.BUTTON_PRIMARY,
                    pressure = 1f
                ),
                buttonPressEvent,
                buttonReleaseEvent,
                VirtualInputEventCreator.createMouseEvent(
                    MotionEvent.ACTION_UP,
                    startPosition.x,
                    startPosition.y,
                    buttonState = 0,
                    pressure = 0f
                ),
                VirtualInputEventCreator.createMouseEvent(
                    MotionEvent.ACTION_HOVER_ENTER,
                    startPosition.x,
                    startPosition.y,
                    buttonState = 0,
                    pressure = 0f
                )
            )
        )
    }

    @Test
    fun sendRelativeEvent() {
        val startPosition: PointF = mVirtualMouse.cursorPosition
        val relativeChangeX = 25f
        val relativeChangeY = 35f
        mVirtualMouse.sendRelativeEvent(
            VirtualMouseRelativeEvent.Builder()
                .setRelativeY(relativeChangeY)
                .setRelativeX(relativeChangeX)
                .build()
        )
        val firstStopPositionX: Float = startPosition.x + relativeChangeX
        val firstStopPositionY: Float = startPosition.y + relativeChangeY
        verifyEvents(
            listOf<InputEvent>(
                VirtualInputEventCreator.createMouseEvent(
                    MotionEvent.ACTION_HOVER_ENTER,
                    firstStopPositionX, firstStopPositionY, buttonState = 0,
                    pressure = 0f, relativeChangeX, relativeChangeY, hScroll = 0f, vScroll = 0f
                )
            )
        )
        val cursorPosition1: PointF = mVirtualMouse.cursorPosition
        assertEquals(
            "getCursorPosition() should return the updated x position",
            firstStopPositionX,
            cursorPosition1.x,
            EPSILON
        )
        assertEquals(
            "getCursorPosition() should return the updated y position",
            firstStopPositionY,
            cursorPosition1.y,
            EPSILON
        )

        val secondStopPositionX = firstStopPositionX - relativeChangeX
        val secondStopPositionY = firstStopPositionY - relativeChangeY
        mVirtualMouse.sendRelativeEvent(
            VirtualMouseRelativeEvent.Builder()
                .setRelativeY(-relativeChangeY)
                .setRelativeX(-relativeChangeX)
                .build()
        )
        verifyEvents(
            listOf<InputEvent>(
                VirtualInputEventCreator.createMouseEvent(
                    MotionEvent.ACTION_HOVER_MOVE,
                    secondStopPositionX, secondStopPositionY, buttonState = 0,
                    pressure = 0f, -relativeChangeX, -relativeChangeY, hScroll = 0f, vScroll = 0f
                )
            )
        )
        val cursorPosition2: PointF = mVirtualMouse.cursorPosition
        assertEquals(
            "getCursorPosition() should return the updated x position",
            secondStopPositionX,
            cursorPosition2.x,
            EPSILON
        )
        assertEquals(
            "getCursorPosition() should return the updated y position",
            secondStopPositionY,
            cursorPosition2.y,
            EPSILON
        )
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_HIGH_RESOLUTION_SCROLL)
    @Parameters(method = "allHighResScrollValues")
    fun sendHighResScrollEventX(scroll: Float) {
        verifyScrollX(scroll)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_HIGH_RESOLUTION_SCROLL)
    @Parameters(method = "allHighResScrollValues")
    fun sendHighResScrollEventY(scroll: Float) {
        verifyScrollY(scroll)
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_HIGH_RESOLUTION_SCROLL)
    @Parameters(method = "allScrollValues")
    fun sendScrollEventX(scroll: Float) {
        verifyScrollX(scroll)
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_HIGH_RESOLUTION_SCROLL)
    @Parameters(method = "allScrollValues")
    fun sendScrollEventY(scroll: Float) {
        verifyScrollY(scroll)
    }

    @Test
    fun testStartingCursorPosition() {
        // The virtual display is 100x100px, running from [0,99]. Half of this is 49.5, and
        // we assume the pointer for a new display begins at the center.
        val displayWidth = mVirtualDisplay.display.mode.physicalWidth
        val displayHeight = mVirtualDisplay.display.mode.physicalHeight
        val startPosition = PointF((displayWidth) / 2f, (displayHeight) / 2f)
        // Trigger a position update without moving the cursor off the starting position.
        mVirtualMouse.sendButtonEvent(
            VirtualMouseButtonEvent.Builder()
                .setAction(VirtualMouseButtonEvent.ACTION_BUTTON_PRESS)
                .setButtonCode(VirtualMouseButtonEvent.BUTTON_PRIMARY)
                .build()
        )
        val buttonPressEvent: MotionEvent = VirtualInputEventCreator.createMouseEvent(
            MotionEvent.ACTION_BUTTON_PRESS,
            startPosition.x,
            startPosition.y,
            MotionEvent.BUTTON_PRIMARY,
            pressure = 1f
        )
        buttonPressEvent.actionButton = MotionEvent.BUTTON_PRIMARY
        verifyEvents(
            listOf<InputEvent>(
                VirtualInputEventCreator.createMouseEvent(
                    MotionEvent.ACTION_DOWN,
                    startPosition.x,
                    startPosition.y,
                    MotionEvent.BUTTON_PRIMARY,
                    pressure = 1f
                ),
                buttonPressEvent
            )
        )

        val position: PointF = mVirtualMouse.cursorPosition

        assertEquals("Cursor position x differs", startPosition.x, position.x, EPSILON)
        assertEquals("Cursor position y differs", startPosition.y, position.y, EPSILON)
    }

    private fun verifyScrollX(hScroll: Float) {
        val startPosition: PointF = mVirtualMouse.cursorPosition
        mVirtualMouse.sendScrollEvent(
            VirtualMouseScrollEvent.Builder()
                .setYAxisMovement(0f)
                .setXAxisMovement(hScroll)
                .build()
        )
        verifyEvents(
            listOf<InputEvent>(
                VirtualInputEventCreator.createMouseEvent(
                    MotionEvent.ACTION_HOVER_ENTER,
                    startPosition.x,
                    startPosition.y,
                    buttonState = 0,
                    pressure = 0f
                ),
                VirtualInputEventCreator.createMouseEvent(
                    MotionEvent.ACTION_SCROLL,
                    startPosition.x, startPosition.y, buttonState = 0, pressure = 0f,
                    relativeX = 0f, relativeY = 0f, hScroll, vScroll = 0f
                )
            )
        )
    }

    private fun verifyScrollY(vScroll: Float) {
        val startPosition: PointF = mVirtualMouse.getCursorPosition()
        mVirtualMouse.sendScrollEvent(
            VirtualMouseScrollEvent.Builder()
                .setYAxisMovement(vScroll)
                .setXAxisMovement(0f)
                .build()
        )
        verifyEvents(
            listOf<InputEvent>(
                VirtualInputEventCreator.createMouseEvent(
                    MotionEvent.ACTION_HOVER_ENTER,
                    startPosition.x,
                    startPosition.y,
                    buttonState = 0,
                    pressure = 0f
                ),
                VirtualInputEventCreator.createMouseEvent(
                    MotionEvent.ACTION_SCROLL,
                    startPosition.x, startPosition.y, buttonState = 0, pressure = 0f,
                    relativeX = 0f, relativeY = 0f, hScroll = 0f, vScroll
                )
            )
        )
    }

    private fun allScrollValues(): Array<Float> = arrayOf(
        -1f,
        1f,
    )

    private fun allHighResScrollValues(): Array<Float> = arrayOf(
        0.1f,
        0.5f,
        1f,
        -0.1f,
        -0.5f,
        -1f,
    )

    companion object {
        private const val DEVICE_NAME = "CtsVirtualMouseTestDevice"

        private const val EPSILON = 0.001f
    }
}
