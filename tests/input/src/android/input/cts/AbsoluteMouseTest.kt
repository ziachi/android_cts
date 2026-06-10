/*
 * Copyright 2025 The Android Open Source Project
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

package android.input.cts

import android.cts.input.EventVerifier
import android.graphics.Point
import android.graphics.PointF
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.cts.input.CaptureEventActivity
import com.android.cts.input.DebugInputRule
import com.android.cts.input.EvdevInputEventCodes.Companion.BTN_LEFT
import com.android.cts.input.EvdevInputEventCodes.Companion.BTN_TOOL_MOUSE
import com.android.cts.input.UinputAbsoluteMouse
import com.android.cts.input.UinputTouchDevice
import com.android.cts.input.VirtualDisplayActivityScenario
import com.android.cts.input.inputeventmatchers.withButtonState
import com.android.cts.input.inputeventmatchers.withCoords
import com.android.cts.input.inputeventmatchers.withMotionAction
import com.android.cts.input.inputeventmatchers.withPressure
import com.android.cts.input.inputeventmatchers.withSource
import com.android.cts.input.inputeventmatchers.withToolType
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class AbsoluteMouseTest {
    private lateinit var absoluteMouse: UinputTouchDevice
    private lateinit var verifier: EventVerifier

    @get:Rule
    val testName = TestName()
    @get:Rule
    val virtualDisplayRule = VirtualDisplayActivityScenario.Rule<CaptureEventActivity>(testName)
    @get:Rule
    val debugInputRule = DebugInputRule()

    @Before
    fun setUp() {
        absoluteMouse = UinputAbsoluteMouse(
            InstrumentationRegistry.getInstrumentation(), virtualDisplayRule.virtualDisplay.display)
        verifier = EventVerifier(virtualDisplayRule.activity::getInputEvent)
    }

    @After
    fun tearDown() {
        if (this::absoluteMouse.isInitialized) {
            absoluteMouse.close()
        }
    }

    @DebugInputRule.DebugInput(bug = 388364364)
    @Test
    fun testHoverAndClick() {
        val pointerId = 0
        val commonMatcher =
            allOf(
                withSource(InputDevice.SOURCE_MOUSE),
                withToolType(MotionEvent.TOOL_TYPE_MOUSE),
            )

        // Inject and verify HOVER_ENTER
        absoluteMouse.sendBtn(BTN_TOOL_MOUSE, true)
        absoluteMouse.sendBtnTouch(true)
        absoluteMouse.sendDown(pointerId, Point(0, 0))
        absoluteMouse.sync()

        verifier.assertReceivedMotion(
            allOf(
                withMotionAction(MotionEvent.ACTION_HOVER_ENTER),
                withCoords(PointF(0f, 0f)),
                withPressure(0f),
                commonMatcher
            )
        )
        verifier.assertReceivedMotion(
            allOf(
                withMotionAction(MotionEvent.ACTION_HOVER_MOVE),
                withCoords(PointF(0f, 0f)),
                withPressure(0f),
                commonMatcher
            )
        )

        // Inject and verify HOVER_MOVE
        absoluteMouse.sendMove(pointerId, Point(10, 10))
        absoluteMouse.sync()

        verifier.assertReceivedMotion(
            allOf(
                withMotionAction(MotionEvent.ACTION_HOVER_MOVE),
                withCoords(PointF(10f, 10f)),
                withPressure(0f),
                commonMatcher
            )
        )

        // Inject and verify mouse button click
        absoluteMouse.sendBtn(BTN_LEFT, true)
        absoluteMouse.sync()

        verifier.assertReceivedMotion(
            allOf(
                withMotionAction(MotionEvent.ACTION_HOVER_EXIT),
                withCoords(PointF(10f, 10f)),
                commonMatcher
            )
        )
        verifier.assertReceivedMotion(
            allOf(
                withMotionAction(MotionEvent.ACTION_DOWN),
                withCoords(PointF(10f, 10f)),
                withButtonState(MotionEvent.BUTTON_PRIMARY),
                commonMatcher
            )
        )
        if (com.android.input.flags.Flags.disableTouchInputMapperPointerUsage()) {
            verifier.assertReceivedMotion(
                allOf(
                    withMotionAction(MotionEvent.ACTION_BUTTON_PRESS),
                    withCoords(PointF(10f, 10f)),
                    withButtonState(MotionEvent.BUTTON_PRIMARY),
                    commonMatcher
                )
            )
        } else {
            verifier.assertReceivedMotion(
                allOf(
                    withMotionAction(MotionEvent.ACTION_MOVE),
                    withCoords(PointF(10f, 10f)),
                    withButtonState(MotionEvent.BUTTON_PRIMARY),
                    commonMatcher
                )
            )
        }

        // Inject and verify mouse button release
        absoluteMouse.sendUp(pointerId)
        absoluteMouse.sendBtnTouch(false)
        absoluteMouse.sendBtn(BTN_LEFT, false)
        absoluteMouse.sync()

        if (com.android.input.flags.Flags.disableTouchInputMapperPointerUsage()) {
            verifier.assertReceivedMotion(
                allOf(
                    withMotionAction(MotionEvent.ACTION_BUTTON_RELEASE),
                    withCoords(PointF(10f, 10f)),
                    withButtonState(0),
                    commonMatcher
                )
            )
            verifier.assertReceivedMotion(
                allOf(
                    withMotionAction(MotionEvent.ACTION_UP),
                    withCoords(PointF(10f, 10f)),
                    withButtonState(0),
                    commonMatcher
                )
            )
        } else {
            verifier.assertReceivedMotion(
                allOf(
                    withMotionAction(MotionEvent.ACTION_UP),
                    withCoords(PointF(10f, 10f)),
                    withButtonState(MotionEvent.BUTTON_PRIMARY),
                    commonMatcher
                )
            )
        }
    }
}
