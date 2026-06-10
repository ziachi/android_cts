/*
 * Copyright (C) 2023 The Android Open Source Project
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
import android.view.KeyEvent
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.PollingCheck
import com.android.cts.input.CaptureEventActivity
import com.android.cts.input.EvdevInputEventCodes.Companion.KEY_ESC
import com.android.cts.input.EvdevInputEventCodes.Companion.KEY_LEFT
import com.android.cts.input.EvdevInputEventCodes.Companion.KEY_LEFTMETA
import com.android.cts.input.UinputKeyboard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Create virtual keyboard devices and inject 'hardware' key combinations for Back shortcuts
 * and check if KEYCODE_BACK is dispatched to the applications.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class BackKeyShortcutsTest {

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

    private fun assertReceivedEventsCorrectlyMapped(numEvents: Int, expectedKeyCode: Int) {
        for (i in 1..numEvents) {
            val lastInputEvent = activity.getInputEvent() as? KeyEvent
            assertNotNull("Failed to receive key event number $i", lastInputEvent)
            assertEquals(
                    "Key code should be " + KeyEvent.keyCodeToString(expectedKeyCode),
                    expectedKeyCode,
                    lastInputEvent!!.keyCode
            )
        }
        activity.assertNoEvents()
    }

    @Test
    fun testBackKeyMetaShortcuts() {
        UinputKeyboard(instrumentation).use { keyboardDevice ->
            activity.assertNoEvents()

            for (scanCode in intArrayOf(KEY_ESC, KEY_LEFT)) {
                keyboardDevice.injectKeyDown(KEY_LEFTMETA)
                keyboardDevice.injectKeyDown(scanCode)
                keyboardDevice.injectKeyUp(scanCode)
                keyboardDevice.injectKeyUp(KEY_LEFTMETA)

                assertReceivedEventsCorrectlyMapped(2, KeyEvent.KEYCODE_BACK)
            }
        }
    }
}
