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

import android.cts.input.EventVerifier
import android.hardware.input.InputManager
import android.view.KeyEvent
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.PollingCheck
import com.android.cts.input.CaptureEventActivity
import com.android.cts.input.EvdevInputEventCodes.Companion.KEY_LEFTALT
import com.android.cts.input.EvdevInputEventCodes.Companion.KEY_LEFTMETA
import com.android.cts.input.EvdevInputEventCodes.Companion.KEY_Q
import com.android.cts.input.UinputKeyboard
import com.android.cts.input.inputeventmatchers.withKeyAction
import com.android.cts.input.inputeventmatchers.withKeyCode
import com.android.cts.input.inputeventmatchers.withModifierState
import org.hamcrest.Matchers
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Create virtual keyboard devices and inject a 'hardware' key event to test certain key gestures
 * are properly handled.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class ModifierKeyGestureTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val rule = ActivityScenarioRule<CaptureEventActivity>(CaptureEventActivity::class.java)

    private lateinit var activity: CaptureEventActivity
    private lateinit var verifier: EventVerifier
    private lateinit var inputManager: InputManager

    @Before
    fun setUp() {
        rule.getScenario().onActivity {
            inputManager = it.getSystemService(InputManager::class.java)
            activity = it
            verifier = EventVerifier(activity::getInputEvent)
        }
        inputManager.resetLockedModifierState()
        PollingCheck.waitFor { activity.hasWindowFocus() }
    }

    @After
    fun tearDown() {
        if (this::inputManager.isInitialized) {
            inputManager.resetLockedModifierState()
        }
    }

    @Test
    fun testMetaAlt_toggleCapsLock_forKeyboardWithNoCapsLockKey() {
        UinputKeyboard(
            instrumentation,
            listOf("KEY_Q", "KEY_LEFTALT", "KEY_LEFTMETA")
        ).use { keyboardDevice ->
            keyboardDevice.injectKeyDown(KEY_LEFTMETA)
            keyboardDevice.injectKeyDown(KEY_LEFTALT)
            keyboardDevice.injectKeyUp(KEY_LEFTALT)
            keyboardDevice.injectKeyUp(KEY_LEFTMETA)

            activity.assertNoEvents()

            keyboardDevice.injectKeyDown(KEY_Q)
            keyboardDevice.injectKeyUp(KEY_Q)
            verifier.assertReceivedKey(
                Matchers.allOf(
                    withKeyCode(KeyEvent.KEYCODE_Q),
                    withKeyAction(KeyEvent.ACTION_DOWN),
                    withModifierState(KeyEvent.META_CAPS_LOCK_ON)
                )
            )
            verifier.assertReceivedKey(
                Matchers.allOf(
                    withKeyCode(KeyEvent.KEYCODE_Q),
                    withKeyAction(KeyEvent.ACTION_UP),
                    withModifierState(KeyEvent.META_CAPS_LOCK_ON)
                )
            )

            // Re-toggle the Caps lock state
            keyboardDevice.injectKeyDown(KEY_LEFTMETA)
            keyboardDevice.injectKeyDown(KEY_LEFTALT)
            keyboardDevice.injectKeyUp(KEY_LEFTALT)
            keyboardDevice.injectKeyUp(KEY_LEFTMETA)

            activity.assertNoEvents()

            keyboardDevice.injectKeyDown(KEY_Q)
            keyboardDevice.injectKeyUp(KEY_Q)
            verifier.assertReceivedKey(
                Matchers.allOf(
                    withKeyCode(KeyEvent.KEYCODE_Q),
                    withKeyAction(KeyEvent.ACTION_DOWN),
                    withModifierState(0)
                )
            )
            verifier.assertReceivedKey(
                Matchers.allOf(
                    withKeyCode(KeyEvent.KEYCODE_Q),
                    withKeyAction(KeyEvent.ACTION_UP),
                    withModifierState(0)
                )
            )

            activity.assertNoEvents()
        }
    }
}
