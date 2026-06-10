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

import android.companion.virtual.VirtualDeviceManager
import android.companion.virtualdevice.flags.Flags
import android.graphics.Point
import android.graphics.PointF
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.hardware.input.VirtualDpad
import android.hardware.input.VirtualKeyEvent
import android.hardware.input.VirtualKeyboard
import android.hardware.input.VirtualMouse
import android.hardware.input.VirtualMouseButtonEvent
import android.hardware.input.VirtualMouseRelativeEvent
import android.hardware.input.VirtualMouseScrollEvent
import android.hardware.input.VirtualNavigationTouchpad
import android.hardware.input.VirtualRotaryEncoder
import android.hardware.input.VirtualRotaryEncoderScrollEvent
import android.hardware.input.VirtualStylus
import android.hardware.input.VirtualStylusMotionEvent
import android.hardware.input.VirtualTouchEvent
import android.hardware.input.VirtualTouchscreen
import android.hardware.input.cts.virtualcreators.VirtualInputDeviceCreator
import android.hardware.input.cts.virtualcreators.VirtualInputEventCreator
import android.platform.test.annotations.RequiresFlagsEnabled
import android.server.wm.WindowManagerStateHelper
import android.util.DisplayMetrics
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import android.virtualdevice.cts.common.VirtualDeviceRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compatibility.common.util.UserHelper
import com.android.cts.input.DefaultPointerSpeedRule
import org.junit.Assert.assertEquals
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VirtualDeviceMirrorDisplayTest : InputTestCase() {
    @get:Rule
    val mRule: VirtualDeviceRule = VirtualDeviceRule.createDefault()

    @get:Rule
    val mDefaultPointerSpeedRule: DefaultPointerSpeedRule = DefaultPointerSpeedRule()

    private lateinit var mVirtualDevice: VirtualDeviceManager.VirtualDevice
    private lateinit var mVirtualDisplay: VirtualDisplay
    private var mUserHelper: UserHelper = UserHelper(mInstrumentation.context)
    private var mDisplayWidth = 0
    private var mDisplayHeight = 0

    // Used to get the absolute location of the test activity based on the decor window location.
    // This location is used to ensure that axis x and axis y are valid on any surface,
    // regardless of the position of the window on the display.
    private lateinit var mWindowLocationOnScreen: Point

    override fun onSetUp() {
        // TODO(b/376071769): Remove the annotation after the source display for mirroring
        // on the virtual display supports the secondary display for the visible background user.
        Assume.assumeFalse(
            "Since the source display for mirroring on the virtual display" +
                " only supports the default display, it cannot be tested for the" +
                " visible background user",
            mUserHelper.isVisibleBackgroundUser
        )
        // We expect the VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR flag to mirror the entirety of the current
        // display. Use the same size for the virtual display to avoid scaling the mirrored content.
        val displayMetrics = DisplayMetrics()
        // Using Display#getRealMetrics to fetch the logical display size
        @Suppress("DEPRECATION")
        mTestActivity.display.getRealMetrics(displayMetrics)
        mDisplayWidth = displayMetrics.widthPixels
        mDisplayHeight = displayMetrics.heightPixels
        mVirtualDevice = mRule.createManagedVirtualDevice()
        mVirtualDisplay = mRule.createManagedVirtualDisplay(
            mVirtualDevice,
            VirtualDeviceRule.createDefaultVirtualDisplayConfigBuilder(
                mDisplayWidth, mDisplayHeight
            )
                .setFlags(
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                            or DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR
                )
        )!!
        mRule.assumeActivityLaunchSupported(mVirtualDisplay.display.displayId)
        // Wait for any pending transitions
        val windowManagerStateHelper = WindowManagerStateHelper()
        windowManagerStateHelper.waitForAppTransitionIdleOnDisplay(mTestActivity.displayId)
        mInstrumentation.uiAutomation.syncInputTransactions()
        // Get the decor view screen location
        val location = IntArray(2)
        mTestActivity.window.decorView.getLocationOnScreen(location)
        mWindowLocationOnScreen = Point(location[0], location[1])
    }

    public override fun onTearDown() {}

    @Test
    fun virtualMouse_scrollEvent() {
        val mouse: VirtualMouse = VirtualInputDeviceCreator.createAndPrepareMouse(
            mVirtualDevice,
            DEVICE_NAME,
            mVirtualDisplay.display
        ).device
        val startPosition: PointF = mouse.cursorPosition
        val moveX = 0f
        val moveY = 1f
        mouse.sendScrollEvent(
            VirtualMouseScrollEvent.Builder()
                .setYAxisMovement(moveY)
                .setXAxisMovement(moveX)
                .build()
        )
        // Verify that events have been received on the activity running on default display.
        verifyEvents(
            listOf<InputEvent>(
                VirtualInputEventCreator.createMouseEvent(
                    MotionEvent.ACTION_HOVER_ENTER,
                    toWindowX(startPosition.x),
                    toWindowY(startPosition.y),
                    buttonState = 0,
                    pressure = 0f
                ),
                VirtualInputEventCreator.createMouseEvent(
                    MotionEvent.ACTION_SCROLL,
                    toWindowX(startPosition.x), toWindowY(startPosition.y), buttonState = 0,
                    pressure = 0f, relativeX = 0f, relativeY = 0f, hScroll = 0f, vScroll = 1f
                )
            )
        )
    }

    @Test
    fun virtualMouse_relativeEvent() {
        val mouse: VirtualMouse = VirtualInputDeviceCreator.createAndPrepareMouse(
            mVirtualDevice,
            DEVICE_NAME,
            mVirtualDisplay.display
        ).device
        val startPosition: PointF = mouse.cursorPosition
        val relativeChangeX = 25f
        val relativeChangeY = 35f
        mouse.sendRelativeEvent(
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
                    toWindowX(firstStopPositionX), toWindowY(firstStopPositionY),
                    buttonState = 0, pressure = 0f, relativeChangeX, relativeChangeY,
                    hScroll = 0f, vScroll = 0f
                )
            )
        )
        val cursorPosition1: PointF = mouse.cursorPosition
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
        mouse.sendRelativeEvent(
            VirtualMouseRelativeEvent.Builder()
                .setRelativeY(-relativeChangeY)
                .setRelativeX(-relativeChangeX)
                .build()
        )
        verifyEvents(
            listOf<InputEvent>(
                VirtualInputEventCreator.createMouseEvent(
                    MotionEvent.ACTION_HOVER_MOVE,
                    toWindowX(secondStopPositionX), toWindowY(secondStopPositionY),
                    buttonState = 0, pressure = 0f, -relativeChangeX, -relativeChangeY,
                    hScroll = 0f, vScroll = 0f
                )
            )
        )
        val cursorPosition2: PointF = mouse.cursorPosition
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
    fun virtualMouse_buttonEvent() {
        val mouse: VirtualMouse = VirtualInputDeviceCreator.createAndPrepareMouse(
            mVirtualDevice,
            DEVICE_NAME,
            mVirtualDisplay.display
        ).device
        val startPosition: PointF = mouse.cursorPosition
        mouse.sendButtonEvent(
            VirtualMouseButtonEvent.Builder()
                .setAction(VirtualMouseButtonEvent.ACTION_BUTTON_PRESS)
                .setButtonCode(VirtualMouseButtonEvent.BUTTON_PRIMARY)
                .build()
        )
        mouse.sendButtonEvent(
            VirtualMouseButtonEvent.Builder()
                .setAction(VirtualMouseButtonEvent.ACTION_BUTTON_RELEASE)
                .setButtonCode(VirtualMouseButtonEvent.BUTTON_PRIMARY)
                .build()
        )
        val buttonPressEvent: MotionEvent = VirtualInputEventCreator.createMouseEvent(
            MotionEvent.ACTION_BUTTON_PRESS,
            toWindowX(startPosition.x),
            toWindowY(startPosition.y),
            MotionEvent.BUTTON_PRIMARY,
            pressure = 1f
        )
        buttonPressEvent.actionButton = MotionEvent.BUTTON_PRIMARY
        val buttonReleaseEvent: MotionEvent = VirtualInputEventCreator.createMouseEvent(
            MotionEvent.ACTION_BUTTON_RELEASE,
            toWindowX(startPosition.x),
            toWindowY(startPosition.y),
            buttonState = 0,
            pressure = 0f
        )
        buttonReleaseEvent.actionButton = MotionEvent.BUTTON_PRIMARY
        verifyEvents(
            listOf<InputEvent>(
                VirtualInputEventCreator.createMouseEvent(
                    MotionEvent.ACTION_DOWN,
                    toWindowX(startPosition.x),
                    toWindowY(startPosition.y),
                    MotionEvent.BUTTON_PRIMARY,
                    pressure = 1f
                ),
                buttonPressEvent,
                buttonReleaseEvent,
                VirtualInputEventCreator.createMouseEvent(
                    MotionEvent.ACTION_UP,
                    toWindowX(startPosition.x),
                    toWindowY(startPosition.y),
                    buttonState = 0,
                    pressure = 0f
                ),
                VirtualInputEventCreator.createMouseEvent(
                    MotionEvent.ACTION_HOVER_ENTER,
                    toWindowX(startPosition.x),
                    toWindowY(startPosition.y),
                    buttonState = 0,
                    pressure = 0f
                )
            )
        )
    }

    @Test
    fun virtualTouchscreen_touchEvent() {
        val touchscreen: VirtualTouchscreen = VirtualInputDeviceCreator.createAndPrepareTouchscreen(
            mVirtualDevice,
            DEVICE_NAME,
            mVirtualDisplay.display
        ).device
        val inputSize = 1f
        // Convert the input axis size to its equivalent fraction of the total screen.
        val computedSize = inputSize / (mDisplayWidth - 1f)
        // TODO(b/343960635): Use test activity to calculate the center position.
        val x = mDisplayWidth / 2
        val y = mDisplayHeight / 2
        // The number of move events that are sent between the down and up event.
        val moveEventCount = 5
        val expectedEvents: MutableList<InputEvent> = ArrayList(moveEventCount + 2)
        // The builder is used for all events in this test. So properties all events have in
        // common are set here.
        val builder: VirtualTouchEvent.Builder = VirtualTouchEvent.Builder()
            .setPointerId(1)
            .setMajorAxisSize(inputSize)
            .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)

        // Down event
        touchscreen.sendTouchEvent(
            builder
                .setAction(VirtualTouchEvent.ACTION_DOWN)
                .setX(x.toFloat())
                .setY(y.toFloat())
                .setPressure(255f)
                .build()
        )
        expectedEvents.add(
            VirtualInputEventCreator.createTouchscreenEvent(
                MotionEvent.ACTION_DOWN,
                toWindowX(x.toFloat()),
                toWindowY(y.toFloat()),
                pressure = 1f,
                computedSize,
                inputSize
            )
        )

        // We expect to get the exact coordinates in the view that were injected into the
        // touchscreen. Touch resampling could result in the generation of additional "fake"
        // touch events. To disable resampling, request unbuffered dispatch.
        mTestActivity.window.decorView.requestUnbufferedDispatch(
            InputDevice.SOURCE_TOUCHSCREEN
        )

        // Next we send a bunch of ACTION_MOVE events. Each one with a different x and y
        // coordinate. If no property changes (i.e. the same VirtualTouchEvent is sent
        // multiple times) then the kernel drops the event as there is no point in delivering
        // a new event if nothing changed.
        builder.setAction(VirtualTouchEvent.ACTION_MOVE)
        for (i in 1..moveEventCount) {
            builder.setX((x + i).toFloat())
                .setY((y + i).toFloat())
                .setPressure(255f)
            touchscreen.sendTouchEvent(builder.build())
            expectedEvents.add(
                VirtualInputEventCreator.createTouchscreenEvent(
                    MotionEvent.ACTION_MOVE,
                    toWindowX((x + i).toFloat()),
                    toWindowY((y + i).toFloat()),
                    pressure = 1f,
                    computedSize,
                    inputSize
                )
            )
        }

        touchscreen.sendTouchEvent(
            builder
                .setAction(VirtualTouchEvent.ACTION_UP)
                .setX((x + moveEventCount).toFloat())
                .setY((y + moveEventCount).toFloat())
                .build()
        )
        expectedEvents.add(
            VirtualInputEventCreator.createTouchscreenEvent(
                MotionEvent.ACTION_UP,
                toWindowX((x + moveEventCount).toFloat()),
                toWindowY((y + moveEventCount).toFloat()),
                pressure = 1f,
                computedSize,
                inputSize
            )
        )

        verifyEvents(expectedEvents)
    }

    @Test
    fun virtualKeyboard_keyEvent() {
        val keyboard: VirtualKeyboard = VirtualInputDeviceCreator.createAndPrepareKeyboard(
            mVirtualDevice,
            DEVICE_NAME,
            mVirtualDisplay.display
        ).device
        keyboard.sendKeyEvent(
            VirtualKeyEvent.Builder()
                .setKeyCode(KeyEvent.KEYCODE_A)
                .setAction(VirtualKeyEvent.ACTION_DOWN)
                .build()
        )
        keyboard.sendKeyEvent(
            VirtualKeyEvent.Builder()
                .setKeyCode(KeyEvent.KEYCODE_A)
                .setAction(VirtualKeyEvent.ACTION_UP)
                .build()
        )

        verifyEvents(
            listOf<InputEvent>(
                VirtualInputEventCreator.createKeyboardEvent(
                    KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_A
                ),
                VirtualInputEventCreator.createKeyboardEvent(
                    KeyEvent.ACTION_UP,
                    KeyEvent.KEYCODE_A
                )
            )
        )
    }

    @Test
    fun virtualDpad_keyEvent() {
        val dpad: VirtualDpad = VirtualInputDeviceCreator.createAndPrepareDpad(
            mVirtualDevice,
            DEVICE_NAME,
            mVirtualDisplay.display
        ).device
        dpad.sendKeyEvent(
            VirtualKeyEvent.Builder()
                .setKeyCode(KeyEvent.KEYCODE_DPAD_UP)
                .setAction(VirtualKeyEvent.ACTION_DOWN)
                .build()
        )
        dpad.sendKeyEvent(
            VirtualKeyEvent.Builder()
                .setKeyCode(KeyEvent.KEYCODE_DPAD_UP)
                .setAction(VirtualKeyEvent.ACTION_UP)
                .build()
        )
        dpad.sendKeyEvent(
            VirtualKeyEvent.Builder()
                .setKeyCode(KeyEvent.KEYCODE_DPAD_CENTER)
                .setAction(VirtualKeyEvent.ACTION_DOWN)
                .build()
        )
        dpad.sendKeyEvent(
            VirtualKeyEvent.Builder()
                .setKeyCode(KeyEvent.KEYCODE_DPAD_CENTER)
                .setAction(VirtualKeyEvent.ACTION_UP)
                .build()
        )

        verifyEvents(
            listOf<InputEvent>(
                VirtualInputEventCreator.createDpadEvent(
                    KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_DPAD_UP
                ),
                VirtualInputEventCreator.createDpadEvent(
                    KeyEvent.ACTION_UP,
                    KeyEvent.KEYCODE_DPAD_UP
                ),
                VirtualInputEventCreator.createDpadEvent(
                    KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_DPAD_CENTER
                ),
                VirtualInputEventCreator.createDpadEvent(
                    KeyEvent.ACTION_UP,
                    KeyEvent.KEYCODE_DPAD_CENTER
                )
            )
        )
    }

    @Test
    fun virtualNavigationTouchpad_touchEvent() {
        val touchPadWidth = 50
        val touchPadHeight = 50
        val navigationTouchpad: VirtualNavigationTouchpad =
            VirtualInputDeviceCreator.createAndPrepareNavigationTouchpad(
                mVirtualDevice,
                DEVICE_NAME,
                mVirtualDisplay.display,
                touchPadWidth,
                touchPadHeight
            ).device
        val axisSize = 1f
        // Virtual navigation touchpad coordinates will not be transformed/scaled.
        val x = 30f
        val y = 40f
        navigationTouchpad.sendTouchEvent(
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
        navigationTouchpad.sendTouchEvent(
            VirtualTouchEvent.Builder()
                .setAction(VirtualTouchEvent.ACTION_UP)
                .setPointerId(1)
                .setX(x)
                .setY(y)
                .setToolType(VirtualTouchEvent.TOOL_TYPE_FINGER)
                .build()
        )
        // Convert the input axis size to its equivalent fraction of the total touchpad size.
        val size = axisSize / (touchPadWidth - 1f)

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
    fun virtualStylus_touchEvent() {
        val stylus: VirtualStylus = VirtualInputDeviceCreator.createAndPrepareStylus(
            mVirtualDevice,
            DEVICE_NAME,
            mVirtualDisplay.display
        ).device
        // We expect to get the exact coordinates in the view that were injected using the
        // stylus. Touch resampling could result in the generation of additional "fake" touch
        // events. To disable resampling, request unbuffered dispatch.
        mTestActivity.window.decorView.requestUnbufferedDispatch(
            InputDevice.SOURCE_STYLUS
        )

        val x = mDisplayWidth / 2
        val y = mDisplayHeight / 2
        val toolType: Int = VirtualStylusMotionEvent.TOOL_TYPE_STYLUS
        // The number of move events that are sent between the down and up event.
        val moveEventCount = 5
        val expectedEvents: MutableList<InputEvent> = ArrayList(moveEventCount + 2)
        // The builder is used for all events in this test. So properties all events have in
        // common are set here.
        val builder: VirtualStylusMotionEvent.Builder = VirtualStylusMotionEvent.Builder()
            .setToolType(toolType)

        // Down event
        stylus.sendMotionEvent(
            builder
                .setAction(VirtualStylusMotionEvent.ACTION_DOWN)
                .setX(x)
                .setY(y)
                .setPressure(255)
                .build()
        )
        expectedEvents.add(
            VirtualInputEventCreator.createStylusEvent(
                MotionEvent.ACTION_DOWN,
                toolType,
                toWindowX(x.toFloat()),
                toWindowY(y.toFloat()),
                pressure = 1f,
                buttonState = 0
            )
        )

        // Next we send a bunch of ACTION_MOVE events. Each one with a different x and y
        // coordinate.
        builder.setAction(VirtualStylusMotionEvent.ACTION_MOVE)
        for (i in 1..moveEventCount) {
            builder.setX(x + i)
                .setY(y + i)
                .setPressure(255)
            stylus.sendMotionEvent(builder.build())
            expectedEvents.add(
                VirtualInputEventCreator.createStylusEvent(
                    MotionEvent.ACTION_MOVE,
                    toolType,
                    toWindowX((x + i).toFloat()),
                    toWindowY((y + i).toFloat()),
                    pressure = 1f,
                    buttonState = 0
                )
            )
        }

        // Up event
        stylus.sendMotionEvent(
            builder
                .setAction(VirtualStylusMotionEvent.ACTION_UP)
                .setX(x + moveEventCount)
                .setY(y + moveEventCount)
                .build()
        )
        expectedEvents.add(
            VirtualInputEventCreator.createStylusEvent(
                MotionEvent.ACTION_UP,
                toolType,
                toWindowX((x + moveEventCount).toFloat()),
                toWindowY((y + moveEventCount).toFloat()),
                pressure = 1f,
                buttonState = 0
            )
        )

        verifyEvents(expectedEvents)
    }

    @RequiresFlagsEnabled(Flags.FLAG_VIRTUAL_ROTARY)
    @Test
    fun virtualRotary_scrollEvent() {
        val rotary: VirtualRotaryEncoder = VirtualInputDeviceCreator.createAndPrepareRotary(
            mVirtualDevice,
            DEVICE_NAME,
            mVirtualDisplay.display
        ).device
        val scrollAmount = 1.0f
        rotary.sendScrollEvent(
            VirtualRotaryEncoderScrollEvent.Builder()
                .setScrollAmount(scrollAmount)
                .build()
        )
        // Verify that events have been received on the activity running on default display.
        verifyEvents(
            listOf<InputEvent>(
                VirtualInputEventCreator.createRotaryEvent(scrollAmount)
            )
        )
    }

    private fun toWindowX(posX: Float): Float {
        return posX - mWindowLocationOnScreen.x
    }

    private fun toWindowY(posY: Float): Float {
        return posY - mWindowLocationOnScreen.y
    }

    companion object {
        private const val DEVICE_NAME = "automirror-inputdevice"
        private const val EPSILON = 0.001f
    }
}
