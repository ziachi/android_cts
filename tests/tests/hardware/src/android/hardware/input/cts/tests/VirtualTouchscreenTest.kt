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

import android.graphics.Point
import android.hardware.input.VirtualTouchEvent
import android.hardware.input.VirtualTouchscreen
import android.hardware.input.cts.virtualcreators.VirtualInputDeviceCreator
import android.hardware.input.cts.virtualcreators.VirtualInputEventCreator
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class VirtualTouchscreenTest : VirtualDeviceTestCase() {
    private lateinit var mVirtualTouchscreen: VirtualTouchscreen

    override fun onSetUpVirtualInputDevice() {
        mVirtualTouchscreen = VirtualInputDeviceCreator.createAndPrepareTouchscreen(
            mVirtualDevice, DEVICE_NAME, mVirtualDisplay.display
        ).device
    }

    @Test
    fun sendTouchEvent() {
        val inputSize = 1f
        // Convert the input axis size to its equivalent fraction of the total screen.
        val computedSize = (inputSize /
                (mVirtualDisplay.display.mode.physicalWidth - 1f))
        val point = getActivityCenter()

        // The number of move events that are sent between the down and up event.
        val moveEventCount = 5
        val expectedEvents: MutableList<InputEvent> = ArrayList(moveEventCount + 2)
        // The builder is used for all events in this test. So properties all events have in common
        // are set here.
        val builder: VirtualTouchEvent.Builder = VirtualTouchEvent.Builder()
            .setPointerId(1)
            .setMajorAxisSize(inputSize)
            .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)

        // Down event
        mVirtualTouchscreen.sendTouchEvent(
            builder
                .setAction(VirtualTouchEvent.ACTION_DOWN)
                .setX(point.x.toFloat())
                .setY(point.y.toFloat())
                .setPressure(255f)
                .build()
        )
        expectedEvents.add(
            VirtualInputEventCreator.createTouchscreenEvent(
                MotionEvent.ACTION_DOWN,
                point.x.toFloat(),
                point.y.toFloat(),
                pressure = 1f,
                computedSize,
                inputSize
            )
        )

        // We expect to get the exact coordinates in the view that were injected into the
        // touchscreen. Touch resampling could result in the generation of additional "fake" touch
        // events. To disable resampling, request unbuffered dispatch.
        mTestActivity.window.decorView.requestUnbufferedDispatch(InputDevice.SOURCE_TOUCHSCREEN)

        // Next we send a bunch of ACTION_MOVE events. Each one with a different x and y coordinate.
        // If no property changes (i.e. the same VirtualTouchEvent is sent multiple times) then the
        // kernel drops the event as there is no point in delivering a new event if nothing changed.
        builder.setAction(VirtualTouchEvent.ACTION_MOVE)
        for (i in 1..moveEventCount) {
            builder.setX((point.x + i).toFloat())
                .setY((point.y + i).toFloat())
                .setPressure(255f)
            mVirtualTouchscreen.sendTouchEvent(builder.build())
            expectedEvents.add(
                VirtualInputEventCreator.createTouchscreenEvent(
                    MotionEvent.ACTION_MOVE,
                    (point.x + i).toFloat(),
                    (point.y + i).toFloat(),
                    pressure = 1f,
                    computedSize,
                    inputSize
                )
            )
        }

        mVirtualTouchscreen.sendTouchEvent(
            builder
                .setAction(VirtualTouchEvent.ACTION_UP)
                .setX((point.x + moveEventCount).toFloat())
                .setY((point.y + moveEventCount).toFloat())
                .build()
        )
        expectedEvents.add(
            VirtualInputEventCreator.createTouchscreenEvent(
                MotionEvent.ACTION_UP,
                (point.x + moveEventCount).toFloat(),
                (point.y + moveEventCount).toFloat(),
                pressure = 1f,
                computedSize,
                inputSize
            )
        )

        verifyEvents(expectedEvents)
    }

    @Test
    fun sendHoverEvents() {
        val point0 = getActivityCenter()
        val point1 = Point(point0.x + 10, point0.y + 10)

        sendHoverEvent(VirtualTouchEvent.ACTION_DOWN, point0.x.toFloat(), point0.y.toFloat())
        sendHoverEvent(VirtualTouchEvent.ACTION_MOVE, point0.x.toFloat(), point1.y.toFloat())
        sendHoverEvent(VirtualTouchEvent.ACTION_MOVE, point1.x.toFloat(), point1.y.toFloat())
        sendHoverEvent(VirtualTouchEvent.ACTION_UP, point1.x.toFloat(), point1.y.toFloat())

        verifyEvents(
            listOf<InputEvent>(
                createMotionEvent(
                    MotionEvent.ACTION_HOVER_ENTER,
                    point0.x.toFloat(),
                    point0.y.toFloat()
                ),
                createMotionEvent(
                    MotionEvent.ACTION_HOVER_MOVE,
                    point0.x.toFloat(),
                    point0.y.toFloat()
                ),
                createMotionEvent(
                    MotionEvent.ACTION_HOVER_MOVE,
                    point0.x.toFloat(),
                    point1.y.toFloat()
                ),
                createMotionEvent(
                    MotionEvent.ACTION_HOVER_MOVE,
                    point1.x.toFloat(),
                    point1.y.toFloat()
                ),
                createMotionEvent(
                    MotionEvent.ACTION_HOVER_EXIT,
                    point1.x.toFloat(),
                    point1.y.toFloat()
                )
            )
        )
    }

    private fun sendHoverEvent(action: Int, x: Float, y: Float) {
        mVirtualTouchscreen.sendTouchEvent(
            VirtualTouchEvent.Builder()
                .setAction(action)
                .setPointerId(1)
                .setX(x)
                .setY(y)
                .setPressure(0f)
                .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)
                .build()
        )
    }

    private fun createMotionEvent(action: Int, x: Float, y: Float): MotionEvent {
        return VirtualInputEventCreator.createTouchscreenEvent(
            action,
            x,
            y,
            pressure = 0f,
            size = 0f,
            axisSize = 0f
        )
    }

    companion object {
        private const val DEVICE_NAME = "CtsVirtualTouchscreenTestDevice"
    }
}
