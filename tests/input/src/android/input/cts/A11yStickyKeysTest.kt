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

package android.input.cts

import android.hardware.input.InputManager
import android.hardware.input.InputSettings
import android.view.KeyEvent
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.PollingCheck
import com.android.compatibility.common.util.SystemUtil
import com.android.cts.input.CaptureEventActivity
import com.android.cts.input.EvdevInputEventCodes.Companion.KEY_A
import com.android.cts.input.EvdevInputEventCodes.Companion.KEY_LEFTSHIFT
import com.android.cts.input.UinputKeyboard
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Create virtual keyboard devices and test A11Y sticky keys feature.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class A11yStickyKeysTest {

    companion object {
        const val A11Y_SETTINGS_PROPAGATE_TIME_MILLIS: Long = 100
        const val EPHEMERAL_MODIFIER_MASK: Int =
            KeyEvent.META_META_MASK or
                    KeyEvent.META_CTRL_MASK or
                    KeyEvent.META_ALT_MASK or
                    KeyEvent.META_SHIFT_MASK
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val activityRule = ActivityScenarioRule<CaptureEventActivity>(CaptureEventActivity::class.java)

    private var wasEnabled = false
    private lateinit var activity: CaptureEventActivity
    private lateinit var inputManager: InputManager

    @Before
    fun setUp() {
        activityRule.getScenario().onActivity {
            inputManager = it.getSystemService(InputManager::class.java)
            activity = it
        }
        PollingCheck.waitFor { activity.hasWindowFocus() }

        SystemUtil.runWithShellPermissionIdentity(
            {
                wasEnabled = InputSettings.isAccessibilityStickyKeysEnabled(activity)
                InputSettings.setAccessibilityStickyKeysEnabled(activity, true)
            },
            "android.permission.QUERY_USERS",
            "android.permission.INTERACT_ACROSS_USERS_FULL",
        )
        Thread.sleep(A11yBounceKeysTest.A11Y_SETTINGS_PROPAGATE_TIME_MILLIS)
    }

    @After
    fun tearDown() {
        SystemUtil.runWithShellPermissionIdentity(
            {
                InputSettings.setAccessibilityStickyKeysEnabled(activity, wasEnabled)
            },
            "android.permission.QUERY_USERS",
            "android.permission.INTERACT_ACROSS_USERS_FULL",
        )
    }

    private fun assertReceivedEventsCorrectlyMapped(
        numEvents: Int,
        expectedKeyCode: Int,
        expectedModifierState: Int
    ) {
        for (i in 1..numEvents) {
            val lastInputEvent = activity.getInputEvent() as? KeyEvent
            Assert.assertNotNull("Failed to receive key event number $i", lastInputEvent)
            Assert.assertEquals(
                "Key code should be " + KeyEvent.keyCodeToString(expectedKeyCode),
                expectedKeyCode,
                lastInputEvent!!.keyCode
            )
            Assert.assertEquals(
                "Modifier state should be $expectedModifierState",
                expectedModifierState,
                lastInputEvent.metaState and EPHEMERAL_MODIFIER_MASK
            )
        }
        activity.assertNoEvents()
    }

    @Test
    fun testStickyShiftModifierKey() {
        UinputKeyboard(instrumentation).use { keyboardDevice ->
            activity.assertNoEvents()

            // Shift key pressed: Shouldn't be sent to apps
            keyboardDevice.injectKeyDown(KEY_LEFTSHIFT)
            keyboardDevice.injectKeyUp(KEY_LEFTSHIFT)
            activity.assertNoEvents()

            // Subsequent key press should have sticky modifier state
            keyboardDevice.injectKeyDown(KEY_A)
            keyboardDevice.injectKeyUp(KEY_A)
            assertReceivedEventsCorrectlyMapped(
                2,
                KeyEvent.KEYCODE_A,
                KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
            )

            // Any non-modifier key press (the previous press on key A), should have cleared
            // non-locked modifier state
            keyboardDevice.injectKeyDown(KEY_A)
            keyboardDevice.injectKeyUp(KEY_A)
            assertReceivedEventsCorrectlyMapped(2, KeyEvent.KEYCODE_A, 0)
        }
    }

    @Test
    fun testLockedShiftModifierKey() {
        UinputKeyboard(instrumentation).use { keyboardDevice ->
            activity.assertNoEvents()

            // Shift key pressed twice: Should lock modifier state
            keyboardDevice.injectKeyDown(KEY_LEFTSHIFT)
            keyboardDevice.injectKeyUp(KEY_LEFTSHIFT)
            keyboardDevice.injectKeyDown(KEY_LEFTSHIFT)
            keyboardDevice.injectKeyUp(KEY_LEFTSHIFT)
            activity.assertNoEvents()

            // Subsequent key press should have locked modifier state
            keyboardDevice.injectKeyDown(KEY_A)
            keyboardDevice.injectKeyUp(KEY_A)
            keyboardDevice.injectKeyDown(KEY_A)
            keyboardDevice.injectKeyUp(KEY_A)
            assertReceivedEventsCorrectlyMapped(
                4,
                KeyEvent.KEYCODE_A,
                KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
            )

            // Re-pressing modifier key should clear locked modifier state
            keyboardDevice.injectKeyDown(KEY_LEFTSHIFT)
            keyboardDevice.injectKeyUp(KEY_LEFTSHIFT)
            keyboardDevice.injectKeyDown(KEY_A)
            keyboardDevice.injectKeyUp(KEY_A)
            assertReceivedEventsCorrectlyMapped(2, KeyEvent.KEYCODE_A, 0)
        }
    }
}
