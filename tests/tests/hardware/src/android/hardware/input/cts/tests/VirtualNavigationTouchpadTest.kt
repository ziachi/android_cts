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

import android.hardware.input.VirtualNavigationTouchpad
import android.hardware.input.VirtualTouchEvent
import android.hardware.input.cts.virtualcreators.VirtualInputDeviceCreator
import android.hardware.input.cts.virtualcreators.VirtualInputEventCreator
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.FlakyTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VirtualNavigationTouchpadTest : VirtualDeviceTestCase() {
    private lateinit var mVirtualNavigationTouchpad: VirtualNavigationTouchpad

    override fun onSetUpVirtualInputDevice() {
        mVirtualNavigationTouchpad = VirtualInputDeviceCreator.createAndPrepareNavigationTouchpad(
            mVirtualDevice, DEVICE_NAME, mVirtualDisplay.display, TOUCHPAD_WIDTH,
            TOUCHPAD_HEIGHT
        ).device
    }

    @Test
    fun sendTouchEvent() {
        val axisSize = 1f
        val x = 30f
        val y = 30f
        mVirtualNavigationTouchpad.sendTouchEvent(
            VirtualTouchEvent.Builder()
                .setAction(VirtualTouchEvent.ACTION_DOWN)
                .setPointerId(1)
                .setX(x)
                .setY(y)
                .setPressure(255f)
                .setMajorAxisSize(axisSize)
                .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)
                .build()
        )
        sendVirtualNavigationTouchEvent(x, y, VirtualTouchEvent.ACTION_UP)
        // Convert the input axis size to its equivalent fraction of the total touchpad size.
        val size = axisSize / (TOUCHPAD_WIDTH - 1f)

        verifyEvents(
            listOf<InputEvent>(
                VirtualInputEventCreator.createNavigationTouchpadMotionEvent(
                    MotionEvent.ACTION_DOWN,
                    x,
                    y,
                    size,
                    axisSize
                ),
                VirtualInputEventCreator.createNavigationTouchpadMotionEvent(
                    MotionEvent.ACTION_UP,
                    x,
                    y,
                    size,
                    axisSize
                )
            )
        )
    }

    @Test
    fun sendTap_motionEventNotConsumed_getsConvertedToDpadCenter() {
        setConsumeGenericMotionEvents(false)

        val x = 30f
        val y = 30f
        sendVirtualNavigationTouchEvent(x, y, VirtualTouchEvent.ACTION_DOWN)
        sendVirtualNavigationTouchEvent(x, y, VirtualTouchEvent.ACTION_UP)

        verifyEvents(
            listOf<InputEvent>(
                createKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER),
                createKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)
            )
        )
    }

    @FlakyTest(
        detail = "The test does not reliably simulate a fling action, only way to reliably" +
                "do it is when uinput supports custom timestamps for virtual input events.",
        bugId = 277040837
    )
    @Test
    fun sendFlingUp_motionEventNotConsumed_getsConvertedToDpadUp() {
        setConsumeGenericMotionEvents(false)

        sendFlingEvents(startX = 30f, startY = 30f, diffX = -10f, diffY = -30f)

        verifyEvents(
            listOf<InputEvent>(
                createKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP),
                createKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_UP)
            )
        )
    }

    @FlakyTest(
        detail = "The test does not reliably simulate a fling action, only way to reliably" +
                "do it is when uinput supports custom timestamps for virtual input events.",
        bugId = 277040837
    )
    @Test
    fun sendFlingDown_motionEventNotConsumed_getsConvertedToDpadDown() {
        setConsumeGenericMotionEvents(false)

        sendFlingEvents(startX = 30f, startY = 10f, diffX = 10f, diffY = 30f)

        verifyEvents(
            listOf<InputEvent>(
                createKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN),
                createKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_DOWN)
            )
        )
    }

    @FlakyTest(
        detail = "The test does not reliably simulate a fling action, only way to reliably" +
                "do it is when uinput supports custom timestamps for virtual input events.",
        bugId = 277040837
    )
    @Test
    fun sendFlingRight_motionEventNotConsumed_getsConvertedToDpadRight() {
        setConsumeGenericMotionEvents(false)

        sendFlingEvents(startX = 10f, startY = 30f, diffX = 30f, diffY = 10f)

        verifyEvents(
            listOf<InputEvent>(
                createKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT),
                createKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT)
            )
        )
    }

    @FlakyTest(
        detail = "The test does not reliably simulate a fling action, only way to reliably" +
                "do it is when uinput supports custom timestamps for virtual input events.",
        bugId = 277040837
    )
    @Test
    fun sendFlingLeft_motionEventNotConsumed_getsConvertedToDpadLeft() {
        setConsumeGenericMotionEvents(false)

        sendFlingEvents(startX = 30f, startY = 30f, diffX = -30f, diffY = 10f)

        verifyEvents(
            listOf<InputEvent>(
                createKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT),
                createKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT)
            )
        )
    }

    @Test
    fun sendLongPress_motionEventNotConsumed_getsIgnored() {
        setConsumeGenericMotionEvents(false)

        val x = 30f
        val y = 30f
        sendVirtualNavigationTouchEvent(x, y, VirtualTouchEvent.ACTION_DOWN)
        // TODO(b/277040837): Use custom timestamps for virtual input events instead of sleep.
        SystemClock.sleep(600)
        sendVirtualNavigationTouchEvent(x, y, VirtualTouchEvent.ACTION_UP)

        verifyNoKeyEvents()
    }

    @Test
    fun sendSlowScroll_motionEventNotConsumed_getsIgnored() {
        setConsumeGenericMotionEvents(false)

        sendContinuousEvents(
            startX = 30f,
            startY = 30f,
            diffX = 2f,
            diffY = 1f,
            eventTimeGapMs = 300
        )

        verifyNoKeyEvents()
    }

    // Test case for fling with a set of coordinates that results into negative velocity calculation
    // when using LSQ2 velocity strategy. Verify that we get the correct behaviour for touch
    // navigation (as it internally uses impulse velocity strategy).
    @Test
    fun sendFlingDown_withSpecialCoordinates_motionEventNotConsumed_getsConvertedToDpadDown() {
        setConsumeGenericMotionEvents(false)

        sendVirtualNavigationTouchEvent(1f, 98f, VirtualTouchEvent.ACTION_DOWN)
        SystemClock.sleep(5)
        sendVirtualNavigationTouchEvent(1f, 247f, VirtualTouchEvent.ACTION_MOVE)
        SystemClock.sleep(5)
        sendVirtualNavigationTouchEvent(1f, 310f, VirtualTouchEvent.ACTION_MOVE)
        SystemClock.sleep(5)
        sendVirtualNavigationTouchEvent(1f, 324f, VirtualTouchEvent.ACTION_MOVE)
        SystemClock.sleep(5)
        sendVirtualNavigationTouchEvent(1f, 324f, VirtualTouchEvent.ACTION_UP)

        verifyEvents(
            listOf<InputEvent>(
                createKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN),
                createKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_DOWN)
            )
        )
    }

    private fun sendFlingEvents(startX: Float, startY: Float, diffX: Float, diffY: Float) {
        sendContinuousEvents(startX, startY, diffX, diffY, eventTimeGapMs = 7)
    }

    private fun sendContinuousEvents(
        startX: Float,
        startY: Float,
        diffX: Float,
        diffY: Float,
        eventTimeGapMs: Long
    ) {
        val eventCount = 4
        // Starts with ACTION_DOWN.
        sendVirtualNavigationTouchEvent(startX, startY, VirtualTouchEvent.ACTION_DOWN)
        SystemClock.sleep(eventTimeGapMs)

        for (i in 1..eventCount) {
            sendVirtualNavigationTouchEvent(
                startX + i * diffX / eventCount,
                startY + i * diffY / eventCount,
                VirtualTouchEvent.ACTION_MOVE
            )
            SystemClock.sleep(eventTimeGapMs)
        }

        // Ends with ACTION_UP.
        sendVirtualNavigationTouchEvent(
            startX + diffX,
            startY + diffY,
            VirtualTouchEvent.ACTION_UP
        )
    }

    private fun sendVirtualNavigationTouchEvent(x: Float, y: Float, action: Int) {
        mVirtualNavigationTouchpad.sendTouchEvent(
            VirtualTouchEvent.Builder()
                .setAction(action)
                .setPointerId(1)
                .setX(x)
                .setY(y)
                .setPressure(if (action == VirtualTouchEvent.ACTION_UP) 0f else 255f)
                .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)
                .build()
        )
    }

    private fun createKeyEvent(action: Int, code: Int): KeyEvent {
        val event = KeyEvent(action, code)
        event.source = InputDevice.SOURCE_TOUCH_NAVIGATION
        event.displayId = mVirtualDisplay.display.displayId
        return event
    }

    companion object {
        private const val DEVICE_NAME = "CtsVirtualNavigationTouchpadTestDevice"
        private const val TOUCHPAD_HEIGHT = 500
        private const val TOUCHPAD_WIDTH = 500
    }
}
