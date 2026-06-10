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
import com.android.cts.input.UinputKeyboard
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Create virtual keyboard devices and test A11Y slow keys feature.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class A11ySlowKeysTest {

    companion object {
        const val THRESHOLD_MILLIS = 500
        const val A11Y_SETTINGS_PROPAGATE_TIME_MILLIS: Long = 100
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val activityRule = ActivityScenarioRule<CaptureEventActivity>(CaptureEventActivity::class.java)

    private var savedThreshold = 0
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
                savedThreshold = InputSettings.getAccessibilitySlowKeysThreshold(activity)
                InputSettings.setAccessibilitySlowKeysThreshold(activity, THRESHOLD_MILLIS)
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
                InputSettings.setAccessibilitySlowKeysThreshold(activity, savedThreshold)
            },
            "android.permission.QUERY_USERS",
            "android.permission.INTERACT_ACROSS_USERS_FULL",
        )
    }

    private fun assertReceivedEventsCorrectlyMapped(numEvents: Int, expectedKeyCode: Int) {
        for (i in 1..numEvents) {
            val lastInputEvent = activity.getInputEvent() as? KeyEvent
            Assert.assertNotNull("Failed to receive key event number $i", lastInputEvent)
            Assert.assertEquals(
                "Key code should be " + KeyEvent.keyCodeToString(expectedKeyCode),
                expectedKeyCode,
                lastInputEvent!!.keyCode
            )
        }
        activity.assertNoEvents()
    }

    @Test
    fun testKeyIgnored_ifPressedForLessThanSlowThreshold() {
        UinputKeyboard(instrumentation).use { keyboardDevice ->
            activity.assertNoEvents()

            // Key not pressed for threshold duration
            keyboardDevice.injectKeyDown(KEY_A)
            keyboardDevice.injectKeyUp(KEY_A)

            activity.assertNoEvents()
        }
    }

    @Test
    fun testKeyAccepted_ifPressedForMoreThanSlowThreshold() {
        UinputKeyboard(instrumentation).use { keyboardDevice ->
            activity.assertNoEvents()

            // Key pressed for threshold duration
            keyboardDevice.injectKeyDown(KEY_A)
            Thread.sleep(2 * THRESHOLD_MILLIS.toLong())
            keyboardDevice.injectKeyUp(KEY_A)

            assertReceivedEventsCorrectlyMapped(2, KeyEvent.KEYCODE_A)
        }
    }
}
