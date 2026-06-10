/*
 * Copyright 2021 The Android Open Source Project
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
import android.platform.test.annotations.RequiresFlagsEnabled
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.PollingCheck
import com.android.cts.input.CaptureEventActivity
import com.android.cts.input.EvdevInputEventCodes.Companion.KEY_A
import com.android.cts.input.UinputKeyboard
import com.android.input.flags.Flags.FLAG_DEVICE_ASSOCIATIONS
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Create a virtual keyboard and inject a 'hardware' key event. Ensure that the event can be
 * verified using the InputManager::verifyInputEvent api.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(FLAG_DEVICE_ASSOCIATIONS)
class VerifyHardwareKeyEventTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val rule = ActivityScenarioRule<CaptureEventActivity>(CaptureEventActivity::class.java)

    private lateinit var activity: CaptureEventActivity
    private lateinit var inputManager: InputManager

    @Before
    fun setUp() {
        rule.getScenario().onActivity {
            inputManager = it.getSystemService(InputManager::class.java)
            activity = it
        }
        PollingCheck.waitFor { activity.hasWindowFocus() }
    }

    private fun assertReceivedEventsCanBeVerified(numEvents: Int) {
        for (i in 1..numEvents) {
            val lastInputEvent = activity.getInputEvent()
            assertNotNull("Event number $i is null!", lastInputEvent)
            assertNotNull(inputManager.verifyInputEvent(lastInputEvent!!))
        }
    }

    /**
     * Send a hardware key event and check that InputManager::verifyInputEvent returns non-null
     * result.
     */
    @Test
    fun testVerifyHardwareKeyEvent() {
        UinputKeyboard(instrumentation).use {
            keyboardDevice ->
            keyboardDevice.injectKeyDown(KEY_A)
            // Send the UP event right away to avoid key repeat
            keyboardDevice.injectKeyUp(KEY_A)

            assertReceivedEventsCanBeVerified(numEvents = 2)
         }
    }
}
