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

package android.input.cts

import android.cts.input.EventVerifier
import android.graphics.Point
import android.hardware.input.InputManager
import android.platform.test.annotations.RequiresFlagsEnabled
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_MOVE
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.cts.input.CaptureEventActivity
import com.android.cts.input.DebugInputRule
import com.android.cts.input.UinputTouchDevice
import com.android.cts.input.UinputTouchScreen
import com.android.cts.input.VirtualDisplayActivityScenario
import com.android.cts.input.VirtualDisplayActivityScenario.Companion.DEFAULT_HEIGHT
import com.android.cts.input.VirtualDisplayActivityScenario.Companion.DEFAULT_WIDTH
import com.android.cts.input.VirtualDisplayActivityScenario.Companion.ORIENTATION_0
import com.android.cts.input.VirtualDisplayActivityScenario.Companion.ORIENTATION_180
import com.android.cts.input.VirtualDisplayActivityScenario.Companion.ORIENTATION_270
import com.android.cts.input.VirtualDisplayActivityScenario.Companion.ORIENTATION_90
import com.android.cts.input.inputeventmatchers.withCoords
import com.android.cts.input.inputeventmatchers.withFlags
import com.android.cts.input.inputeventmatchers.withMotionAction
import com.android.cts.input.inputeventmatchers.withPointerCount
import com.android.input.flags.Flags.FLAG_DEVICE_ASSOCIATIONS
import org.hamcrest.Description
import org.hamcrest.Matchers.allOf
import org.hamcrest.TypeSafeMatcher
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(FLAG_DEVICE_ASSOCIATIONS)
class TouchScreenTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var touchScreen: UinputTouchDevice
    private lateinit var verifier: EventVerifier

    @get:Rule
    val debugInputRule = DebugInputRule()
    @get:Rule
    val testName = TestName()
    @get:Rule
    val virtualDisplayRule = VirtualDisplayActivityScenario.Rule<CaptureEventActivity>(testName)

    @Before
    fun setUp() {
        touchScreen = UinputTouchScreen(instrumentation, virtualDisplayRule.virtualDisplay.display)
        verifier = EventVerifier(virtualDisplayRule.activity::getInputEvent)
    }

    @After
    fun tearDown() {
        if (this::touchScreen.isInitialized) {
            touchScreen.close()
        }
    }

    @Test
    fun testHostUsiVersionIsNull() {
        assertNull(
            instrumentation.targetContext.getSystemService(InputManager::class.java)
                .getHostUsiVersion(virtualDisplayRule.virtualDisplay.display)
        )
    }

    @DebugInputRule.DebugInput(bug = 288321659)
    @Test
    fun testSingleTouch() {
        val x = 100
        val y = 100

        val pointer = touchScreen.touchDown(x, y)
        verifier.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_DOWN))

        pointer.moveTo(x + 1, y + 1)
        verifier.assertReceivedMotion(withMotionAction(ACTION_MOVE))

        pointer.lift()
        verifier.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_UP))
    }

    @DebugInputRule.DebugInput(bug = 288321659)
    @Test
    fun testMultiTouch() {
        val location1 = Point(100, 100)
        val location2 = Point(150, 150)

        // ACTION_DOWN
        val pointer1 = touchScreen.touchDown(location1.x, location1.y)
        verifier.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_DOWN))

        // ACTION_POINTER_DOWN
        val pointer2 = touchScreen.touchDown(location2.x, location2.y)
        verifier.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_POINTER_DOWN, 1))

        // ACTION_MOVE
        location1.offset(1, 1)
        pointer1.moveTo(location1.x, location1.y)
        verifier.assertReceivedMotion(withMotionAction(ACTION_MOVE))

        // ACTION_POINTER_UP
        pointer1.lift()
        verifier.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_POINTER_UP, 0))

        // ACTION_UP
        pointer2.lift()
        verifier.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_UP))
    }

    @DebugInputRule.DebugInput(bug = 288321659)
    @Test
    fun testDeviceCancel() {
        val location = Point(100, 100)

        // ACTION_DOWN
        val pointer = touchScreen.touchDown(location.x, location.y)
        verifier.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_DOWN))

        // ACTION_MOVE
        location.offset(1, 1)
        pointer.moveTo(location.x, location.y)
        verifier.assertReceivedMotion(withMotionAction(ACTION_MOVE))

        // ACTION_CANCEL
        pointer.close()
        verifier.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_CANCEL))

        // No event
        virtualDisplayRule.activity.assertNoEvents()
    }

    /**
     * Check that pointer cancel is received by the activity via uinput device.
     */
    @DebugInputRule.DebugInput(bug = 288321659)
    @Test
    fun testDevicePointerCancel() {
        val location1 = Point(100, 100)
        val location2 = Point(150, 150)

        // ACTION_DOWN
        val pointer1 = touchScreen.touchDown(location1.x, location1.y)
        verifier.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_DOWN))

        // ACTION_MOVE
        location1.offset(1, 1)
        pointer1.moveTo(location1.x, location1.y)
        verifier.assertReceivedMotion(withMotionAction(ACTION_MOVE))

        // ACTION_POINTER_DOWN(1)
        val pointer2 = touchScreen.touchDown(location2.x, location2.y)
        verifier.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_POINTER_DOWN, 1))

        // ACTION_POINTER_UP(1) with cancel flag
        pointer2.close()
        verifier.assertReceivedMotion(
            allOf(
                withMotionAction(MotionEvent.ACTION_POINTER_UP, 1),
                withFlags(MotionEvent.FLAG_CANCELED)
            )
        )

        // ACTION_UP
        pointer1.lift()
        // TODO(b/341773832): Fix the implementation so that this unexpected MOVE is not generated.
        verifier.assertReceivedMotion(allOf(withMotionAction(ACTION_MOVE), withPointerCount(1)))
        verifier.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_UP))
    }

    @Test
    fun testTouchScreenPrecisionOrientation0() {
        virtualDisplayRule.runInDisplayOrientation(ORIENTATION_0) {
            verifyTapsOnFourCorners(
                arrayOf(
                    Point(0, 0),
                    Point(DEFAULT_WIDTH - 1, 0),
                    Point(DEFAULT_WIDTH - 1, DEFAULT_HEIGHT - 1),
                    Point(0, DEFAULT_HEIGHT - 1),
                )
            )
        }
    }

    @Test
    fun testTouchScreenPrecisionOrientation90() {
        virtualDisplayRule.runInDisplayOrientation(ORIENTATION_90) {
            verifyTapsOnFourCorners(
                arrayOf(
                    Point(0, DEFAULT_WIDTH - 1),
                    Point(0, 0),
                    Point(DEFAULT_HEIGHT - 1, 0),
                    Point(DEFAULT_HEIGHT - 1, DEFAULT_WIDTH - 1),
                )
            )
        }
    }

    @Test
    fun testTouchScreenPrecisionOrientation180() {
        virtualDisplayRule.runInDisplayOrientation(ORIENTATION_180) {
            verifyTapsOnFourCorners(
                arrayOf(
                    Point(DEFAULT_WIDTH - 1, DEFAULT_HEIGHT - 1),
                    Point(0, DEFAULT_HEIGHT - 1),
                    Point(0, 0),
                    Point(DEFAULT_WIDTH - 1, 0),
                )
            )
        }
    }

    @Test
    fun testTouchScreenPrecisionOrientation270() {
        virtualDisplayRule.runInDisplayOrientation(ORIENTATION_270) {
            verifyTapsOnFourCorners(
                arrayOf(
                    Point(DEFAULT_HEIGHT - 1, 0),
                    Point(DEFAULT_HEIGHT - 1, DEFAULT_WIDTH - 1),
                    Point(0, DEFAULT_WIDTH - 1),
                    Point(0, 0),
                )
            )
        }
    }

    @Test
    fun testEventTime() {
        val location = Point(100, 100)

        val withConsistentEventTime = object : TypeSafeMatcher<MotionEvent>() {
            override fun describeTo(description: Description) {
                description.appendText("getEventTimeNanos() is consistent with getEventTime()")
            }

            override fun matchesSafely(event: MotionEvent): Boolean {
                return event.eventTimeNanos / 1_000_000 == event.eventTime
            }
        }

       // ACTION_DOWN
        val pointer = touchScreen.touchDown(location.x, location.y)
        verifier.assertReceivedMotion(
            allOf(withMotionAction(MotionEvent.ACTION_DOWN), withConsistentEventTime)
        )

        // ACTION_MOVE
        location.offset(1, 1)
        pointer.moveTo(location.x, location.y)
        verifier.assertReceivedMotion(
            allOf(withMotionAction(ACTION_MOVE), withConsistentEventTime)
        )

        // ACTION_UP
        pointer.lift()
        verifier.assertReceivedMotion(
            allOf(withMotionAction(MotionEvent.ACTION_UP), withConsistentEventTime)
        )
    }

    // Verifies that each of the four corners of the touch screen (lt, rt, rb, lb) map to the
    // given four points by tapping on the corners in order and asserting the location of the
    // received events match the provided values.
    private fun verifyTapsOnFourCorners(expectedPoints: Array<Point>) {
        val pointerId = 0
        for (i in 0 until 4) {
            touchScreen.sendBtnTouch(true)
            touchScreen.sendDown(pointerId, CORNERS[i])
            touchScreen.sync()
            verifier.assertReceivedMotion(
                allOf(withMotionAction(MotionEvent.ACTION_DOWN), withCoords(expectedPoints[i]))
            )

            touchScreen.sendBtnTouch(false)
            touchScreen.sendUp(pointerId)
            touchScreen.sync()
            verifier.assertReceivedMotion(withMotionAction(MotionEvent.ACTION_UP))
        }
    }

    companion object {
        // The four corners of the touchscreen: lt, rt, rb, lb
        val CORNERS = arrayOf(
            Point(0, 0),
            Point(DEFAULT_WIDTH - 1, 0),
            Point(DEFAULT_WIDTH - 1, DEFAULT_HEIGHT - 1),
            Point(0, DEFAULT_HEIGHT - 1),
        )
    }
}
