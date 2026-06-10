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
package android.hardware.input.cts.tests

import android.graphics.Point
import android.hardware.input.VirtualStylus
import android.hardware.input.VirtualStylusButtonEvent
import android.hardware.input.VirtualStylusMotionEvent
import android.hardware.input.cts.virtualcreators.VirtualInputDeviceCreator
import android.hardware.input.cts.virtualcreators.VirtualInputEventCreator
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent
import androidx.test.filters.SmallTest
import junitparams.JUnitParamsRunner
import junitparams.Parameters
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(JUnitParamsRunner::class)
class VirtualStylusTest : VirtualDeviceTestCase() {
    private lateinit var mVirtualStylus: VirtualStylus

    override fun onSetUpVirtualInputDevice() {
        mVirtualStylus = VirtualInputDeviceCreator.createAndPrepareStylus(
            mVirtualDevice,
            DEVICE_NAME, mVirtualDisplay.display
        ).device
        // We expect to get the exact coordinates in the view that were injected using the
        // stylus. Touch resampling could result in the generation of additional "fake" touch
        // events. To disable resampling, request unbuffered dispatch.
        mTestActivity.window.decorView.requestUnbufferedDispatch(
            InputDevice.SOURCE_STYLUS
        )
    }

    @Parameters(method = "allToolTypes")
    @Test
    fun sendTouchEvents(toolType: Int) {
        val point = getActivityCenter()
        // The number of move events that are sent between the down and up event.
        val moveEventCount = 5
        val expectedEvents: MutableList<InputEvent> = ArrayList(moveEventCount + 2)
        // The builder is used for all events in this test. So properties all events have in common
        // are set here.
        val builder: VirtualStylusMotionEvent.Builder = VirtualStylusMotionEvent.Builder()
            .setToolType(toolType)

        // Down event
        mVirtualStylus.sendMotionEvent(
            builder
                .setAction(VirtualStylusMotionEvent.ACTION_DOWN)
                .setX(point.x)
                .setY(point.y)
                .setPressure(255)
                .build()
        )
        expectedEvents.add(
            VirtualInputEventCreator.createStylusTouchMotionEvent(
                MotionEvent.ACTION_DOWN,
                point.x.toFloat(),
                point.y.toFloat(),
                toolType
            )
        )

        // Next we send a bunch of ACTION_MOVE events. Each one with a different x and y coordinate.
        builder.setAction(VirtualStylusMotionEvent.ACTION_MOVE)
        for (i in 1..moveEventCount) {
            builder.setX(point.x + i)
                .setY(point.y + i)
                .setPressure(255)
            mVirtualStylus.sendMotionEvent(builder.build())
            expectedEvents.add(
                VirtualInputEventCreator.createStylusTouchMotionEvent(
                    MotionEvent.ACTION_MOVE,
                    (point.x + i).toFloat(),
                    (point.y + i).toFloat(),
                    toolType
                )
            )
        }

        // Up event
        mVirtualStylus.sendMotionEvent(
            builder
                .setAction(VirtualStylusMotionEvent.ACTION_UP)
                .setX(point.x + moveEventCount)
                .setY(point.y + moveEventCount)
                .build()
        )
        expectedEvents.add(
            VirtualInputEventCreator.createStylusTouchMotionEvent(
                MotionEvent.ACTION_UP,
                (point.x + moveEventCount).toFloat(),
                (point.y + moveEventCount).toFloat(),
                toolType
            )
        )

        verifyEvents(expectedEvents)
    }

    @Parameters(method = "allButtonCodes")
    @Test
    fun sendTouchEvents_withButtonPressed(buttonCode: Int) {
        val point0 = getActivityCenter()
        val point1 = Point(point0.x + 10, point0.y + 10)
        val toolType: Int = VirtualStylusMotionEvent.TOOL_TYPE_STYLUS
        moveStylusWithButtonPressed(
            point0.x,
            point0.y,
            point1.x,
            point1.y,
            pressure = 255,
            buttonCode,
            toolType
        )

        verifyEvents(
            listOf<InputEvent>(
                VirtualInputEventCreator.createStylusTouchMotionEvent(
                    MotionEvent.ACTION_DOWN,
                    point0.x.toFloat(),
                    point0.y.toFloat(),
                    toolType,
                    buttonCode
                ),
                VirtualInputEventCreator.createStylusTouchMotionEvent(
                    MotionEvent.ACTION_BUTTON_PRESS,
                    point0.x.toFloat(),
                    point0.y.toFloat(),
                    toolType,
                    buttonCode
                ),
                VirtualInputEventCreator.createStylusTouchMotionEvent(
                    MotionEvent.ACTION_MOVE,
                    point0.x.toFloat(),
                    point1.y.toFloat(),
                    toolType,
                    buttonCode
                ),
                VirtualInputEventCreator.createStylusTouchMotionEvent(
                    MotionEvent.ACTION_MOVE,
                    point1.x.toFloat(),
                    point1.y.toFloat(),
                    toolType,
                    buttonCode
                ),
                VirtualInputEventCreator.createStylusTouchMotionEvent(
                    MotionEvent.ACTION_BUTTON_RELEASE,
                    point1.x.toFloat(),
                    point1.y.toFloat(),
                    toolType
                ),
                VirtualInputEventCreator.createStylusTouchMotionEvent(
                    MotionEvent.ACTION_UP,
                    point1.x.toFloat(),
                    point1.y.toFloat(),
                    toolType
                )
            )
        )
    }

    @Test
    fun sendTouchEvents_withTilt() {
        verifyStylusTouchWithTilt(
            tiltXDegrees = 0,
            tiltYDegrees = 0,
            expectedTiltDegrees = 0,
            expectedOrientationDegrees = 0
        )
        verifyStylusTouchWithTilt(
            tiltXDegrees = 90,
            tiltYDegrees = 0,
            expectedTiltDegrees = 90,
            expectedOrientationDegrees = -90
        )
        verifyStylusTouchWithTilt(
            tiltXDegrees = -90,
            tiltYDegrees = 0,
            expectedTiltDegrees = 90,
            expectedOrientationDegrees = 90
        )
        verifyStylusTouchWithTilt(
            tiltXDegrees = 0,
            tiltYDegrees = 90,
            expectedTiltDegrees = 90,
            expectedOrientationDegrees = 0
        )
        verifyStylusTouchWithTilt(
            tiltXDegrees = 0,
            tiltYDegrees = -90,
            expectedTiltDegrees = 90,
            expectedOrientationDegrees = -180
        )
        verifyStylusTouchWithTilt(
            tiltXDegrees = 90,
            tiltYDegrees = -90,
            expectedTiltDegrees = 90,
            expectedOrientationDegrees = -135
        )
        verifyStylusTouchWithTilt(
            tiltXDegrees = 90,
            tiltYDegrees = 90,
            expectedTiltDegrees = 90,
            expectedOrientationDegrees = -45
        )
        verifyStylusTouchWithTilt(
            tiltXDegrees = -90,
            tiltYDegrees = 90,
            expectedTiltDegrees = 90,
            expectedOrientationDegrees = 45
        )
        verifyStylusTouchWithTilt(
            tiltXDegrees = -90,
            tiltYDegrees = -90,
            expectedTiltDegrees = 90,
            expectedOrientationDegrees = 135
        )
    }

    @Parameters(method = "allToolTypes")
    @Test
    fun sendHoverEvents(toolType: Int) {
        val point0 = getActivityCenter()
        val point1 = Point(point0.x + 10, point0.y + 10)
        val pressure = 0

        sendMotionEvent(
            VirtualStylusMotionEvent.ACTION_DOWN,
            point0.x,
            point0.y,
            pressure,
            toolType
        )
        sendMotionEvent(
            VirtualStylusMotionEvent.ACTION_MOVE,
            point0.x,
            point1.y,
            pressure,
            toolType
        )
        sendMotionEvent(
            VirtualStylusMotionEvent.ACTION_MOVE,
            point1.x,
            point1.y,
            pressure,
            toolType
        )
        sendMotionEvent(VirtualStylusMotionEvent.ACTION_UP, point1.x, point1.y, pressure, toolType)

        verifyEvents(
            listOf<InputEvent>(
                VirtualInputEventCreator.createStylusHoverMotionEvent(
                    MotionEvent.ACTION_HOVER_ENTER,
                    point0.x.toFloat(),
                    point0.y.toFloat(),
                    toolType
                ),
                VirtualInputEventCreator.createStylusHoverMotionEvent(
                    MotionEvent.ACTION_HOVER_MOVE,
                    point0.x.toFloat(),
                    point0.y.toFloat(),
                    toolType
                ),
                VirtualInputEventCreator.createStylusHoverMotionEvent(
                    MotionEvent.ACTION_HOVER_MOVE,
                    point0.x.toFloat(),
                    point1.y.toFloat(),
                    toolType
                ),
                VirtualInputEventCreator.createStylusHoverMotionEvent(
                    MotionEvent.ACTION_HOVER_MOVE,
                    point1.x.toFloat(),
                    point1.y.toFloat(),
                    toolType
                ),
                VirtualInputEventCreator.createStylusHoverMotionEvent(
                    MotionEvent.ACTION_HOVER_EXIT,
                    point1.x.toFloat(),
                    point1.y.toFloat(),
                    toolType
                )
            )
        )
    }

    @Parameters(method = "allButtonCodes")
    @Test
    fun sendHoverEvents_withButtonAlwaysPressed(buttonCode: Int) {
        val point0 = getActivityCenter()
        val point1 = Point(point0.x + 10, point0.y + 10)
        val toolType: Int = VirtualStylusMotionEvent.TOOL_TYPE_STYLUS
        moveStylusWithButtonPressed(
            point0.x,
            point0.y,
            point1.x,
            point1.y,
            pressure = 0,
            buttonCode,
            toolType
        )

        verifyEvents(
            listOf<InputEvent>(
                VirtualInputEventCreator.createStylusHoverMotionEvent(
                    MotionEvent.ACTION_HOVER_ENTER,
                    point0.x.toFloat(),
                    point0.y.toFloat(),
                    toolType,
                    buttonCode
                ),
                VirtualInputEventCreator.createStylusHoverMotionEvent(
                    MotionEvent.ACTION_HOVER_MOVE,
                    point0.x.toFloat(),
                    point0.y.toFloat(),
                    toolType,
                    buttonCode
                ),
                VirtualInputEventCreator.createStylusHoverMotionEvent(
                    MotionEvent.ACTION_HOVER_MOVE,
                    point0.x.toFloat(),
                    point1.y.toFloat(),
                    toolType,
                    buttonCode
                ),
                VirtualInputEventCreator.createStylusHoverMotionEvent(
                    MotionEvent.ACTION_HOVER_MOVE,
                    point1.x.toFloat(),
                    point1.y.toFloat(),
                    toolType,
                    buttonCode
                ),
                VirtualInputEventCreator.createStylusHoverMotionEvent(
                    MotionEvent.ACTION_HOVER_EXIT,
                    point1.x.toFloat(),
                    point1.y.toFloat(),
                    toolType,
                    buttonCode
                )
            )
        )
    }

    @Parameters(method = "allButtonCodes")
    @Test
    fun stylusButtonPressRelease_withoutHoverOrTouch(buttonCode: Int) {
        mVirtualStylus.sendButtonEvent(
            VirtualStylusButtonEvent.Builder()
                .setAction(VirtualStylusButtonEvent.ACTION_BUTTON_PRESS)
                .setButtonCode(buttonCode)
                .build()
        )
        mVirtualStylus.sendButtonEvent(
            VirtualStylusButtonEvent.Builder()
                .setAction(VirtualStylusButtonEvent.ACTION_BUTTON_RELEASE)
                .setButtonCode(buttonCode)
                .build()
        )

        assertNoMoreEvents()
    }

    private fun verifyStylusTouchWithTilt(
        tiltXDegrees: Int,
        tiltYDegrees: Int,
        expectedTiltDegrees: Int,
        expectedOrientationDegrees: Int
    ) {
        val point0 = getActivityCenter()
        val point1 = Point(point0.x + 10, point0.y + 10)
        val pressure = 255
        val toolType: Int = VirtualStylusMotionEvent.TOOL_TYPE_STYLUS

        sendMotionEvent(
            VirtualStylusMotionEvent.ACTION_DOWN,
            point0.x,
            point0.y,
            pressure,
            toolType,
            tiltXDegrees,
            tiltYDegrees
        )
        sendMotionEvent(
            VirtualStylusMotionEvent.ACTION_MOVE,
            point0.x,
            point1.y,
            pressure,
            toolType,
            tiltXDegrees,
            tiltYDegrees
        )
        sendMotionEvent(
            VirtualStylusMotionEvent.ACTION_MOVE,
            point1.x,
            point1.y,
            pressure,
            toolType,
            tiltXDegrees,
            tiltYDegrees
        )
        sendMotionEvent(
            VirtualStylusMotionEvent.ACTION_UP,
            point1.x,
            point1.y,
            pressure,
            toolType,
            tiltXDegrees,
            tiltYDegrees
        )

        val expectedTiltRadians = Math.toRadians(expectedTiltDegrees.toDouble()).toFloat()
        val expectedOrientationRadians =
            Math.toRadians(expectedOrientationDegrees.toDouble()).toFloat()
        verifyEvents(
            listOf<InputEvent>(
                VirtualInputEventCreator.createStylusTouchMotionEvent(
                    MotionEvent.ACTION_DOWN,
                    point0.x.toFloat(),
                    point0.y.toFloat(),
                    toolType,
                    expectedTiltRadians,
                    expectedOrientationRadians
                ),
                VirtualInputEventCreator.createStylusTouchMotionEvent(
                    MotionEvent.ACTION_MOVE,
                    point0.x.toFloat(),
                    point1.y.toFloat(),
                    toolType,
                    expectedTiltRadians,
                    expectedOrientationRadians
                ),
                VirtualInputEventCreator.createStylusTouchMotionEvent(
                    MotionEvent.ACTION_MOVE,
                    point1.x.toFloat(),
                    point1.y.toFloat(),
                    toolType,
                    expectedTiltRadians,
                    expectedOrientationRadians
                ),
                VirtualInputEventCreator.createStylusTouchMotionEvent(
                    MotionEvent.ACTION_UP,
                    point1.x.toFloat(),
                    point1.y.toFloat(),
                    toolType,
                    expectedTiltRadians,
                    expectedOrientationRadians
                )
            )
        )
    }

    private fun moveStylusWithButtonPressed(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        pressure: Int,
        buttonCode: Int,
        toolType: Int
    ) {
        mVirtualStylus.sendButtonEvent(
            VirtualStylusButtonEvent.Builder()
                .setAction(VirtualStylusButtonEvent.ACTION_BUTTON_PRESS)
                .setButtonCode(buttonCode)
                .build()
        )
        sendMotionEvent(VirtualStylusMotionEvent.ACTION_DOWN, startX, startY, pressure, toolType)
        sendMotionEvent(VirtualStylusMotionEvent.ACTION_MOVE, startX, endY, pressure, toolType)
        sendMotionEvent(VirtualStylusMotionEvent.ACTION_MOVE, endX, endY, pressure, toolType)
        sendMotionEvent(VirtualStylusMotionEvent.ACTION_UP, endX, endY, pressure, toolType)
        mVirtualStylus.sendButtonEvent(
            VirtualStylusButtonEvent.Builder()
                .setAction(VirtualStylusButtonEvent.ACTION_BUTTON_RELEASE)
                .setButtonCode(buttonCode)
                .build()
        )
    }

    private fun sendMotionEvent(
        action: Int,
        x: Int,
        y: Int,
        pressure: Int,
        toolType: Int,
        tiltX: Int = 0,
        tiltY: Int = 0
    ) {
        mVirtualStylus.sendMotionEvent(
            VirtualStylusMotionEvent.Builder()
                .setAction(action)
                .setToolType(toolType)
                .setX(x)
                .setY(y)
                .setTiltX(tiltX)
                .setTiltY(tiltY)
                .setPressure(pressure)
                .build()
        )
    }

    private fun allButtonCodes(): Array<Int> = arrayOf(
        VirtualStylusButtonEvent.BUTTON_PRIMARY,
        VirtualStylusButtonEvent.BUTTON_SECONDARY,
    )

    private fun allToolTypes(): Array<Int> = arrayOf(
        VirtualStylusMotionEvent.TOOL_TYPE_STYLUS,
        VirtualStylusMotionEvent.TOOL_TYPE_ERASER,
    )

    companion object {
        private const val DEVICE_NAME = "CtsVirtualStylusTestDevice"
    }
}
