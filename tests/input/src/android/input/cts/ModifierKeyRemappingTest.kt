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

import android.Manifest
import android.cts.input.EventVerifier
import android.hardware.input.InputManager
import android.platform.test.annotations.RequiresFlagsEnabled
import android.provider.Settings
import android.view.KeyEvent
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.PollingCheck
import com.android.compatibility.common.util.SystemUtil
import com.android.compatibility.common.util.ThrowingSupplier
import com.android.cts.input.CaptureEventActivity
import com.android.cts.input.EvdevInputEventCodes.Companion.KEY_LEFTALT
import com.android.cts.input.UinputKeyboard
import com.android.cts.input.inputeventmatchers.withKeyAction
import com.android.cts.input.inputeventmatchers.withKeyCode
import com.android.cts.input.inputeventmatchers.withModifierState
import com.android.input.flags.Flags.FLAG_DEVICE_ASSOCIATIONS
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Create virtual keyboard devices and inject a 'hardware' key event after remapping keys. Ensure
 * that the event keys are correctly remapped.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(FLAG_DEVICE_ASSOCIATIONS)
class ModifierKeyRemappingTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val rule = ActivityScenarioRule<CaptureEventActivity>(CaptureEventActivity::class.java)

    private lateinit var activity: CaptureEventActivity
    private lateinit var verifier: EventVerifier
    private lateinit var inputManager: InputManager
    private lateinit var existingRemappings: Map<Int, Int>

    @Before
    fun setUp() {
        rule.getScenario().onActivity {
            inputManager = it.getSystemService(InputManager::class.java)
            activity = it
            verifier = EventVerifier(activity::getInputEvent)
        }
        inputManager.resetLockedModifierState()
        PollingCheck.waitFor { activity.hasWindowFocus() }

        // Save existing remappings
        existingRemappings = getModifierKeyRemapping()
        clearAllModifierKeyRemappings()
    }

    @After
    fun tearDown() {
        if (this::existingRemappings.isInitialized) {
            clearAllModifierKeyRemappings()
            existingRemappings.forEach { entry ->
                remapModifierKey(entry.key, entry.value)
            }
        }
        if (this::inputManager.isInitialized) {
            inputManager.resetLockedModifierState()
        }
    }

    @Test
    fun testHardwareKeyEventsWithRemapping_afterKeyboardAdded() {
        ModifierRemappingFlag(true).use {
            UinputKeyboard(instrumentation).use { keyboardDevice ->
                val inputDevice = inputManager.getInputDevice(keyboardDevice.deviceId)

                // Add remapping after device is already added
                remapModifierKey(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_SHIFT_LEFT)
                PollingCheck.waitFor {
                    KeyEvent.KEYCODE_SHIFT_LEFT ==
                            inputDevice?.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_ALT_LEFT)
                }

                keyboardDevice.injectKeyDown(KEY_LEFTALT)
                verifier.assertReceivedKey(
                    allOf(
                        withKeyCode(KeyEvent.KEYCODE_SHIFT_LEFT),
                        withKeyAction(KeyEvent.ACTION_DOWN),
                        withModifierState(KeyEvent.META_SHIFT_LEFT_ON or KeyEvent.META_SHIFT_ON)
                    )
                )

                keyboardDevice.injectKeyUp(KEY_LEFTALT)
                verifier.assertReceivedKey(withKeyCode(KeyEvent.KEYCODE_SHIFT_LEFT))

                clearAllModifierKeyRemappings()
                PollingCheck.waitFor {
                    KeyEvent.KEYCODE_ALT_LEFT ==
                            inputDevice?.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_ALT_LEFT)
                }

                keyboardDevice.injectKeyDown(KEY_LEFTALT)

                verifier.assertReceivedKey(
                    allOf(
                        withKeyCode(KeyEvent.KEYCODE_ALT_LEFT),
                        withKeyAction(KeyEvent.ACTION_DOWN),
                        withModifierState(KeyEvent.META_ALT_LEFT_ON or KeyEvent.META_ALT_ON)
                    )
                )

                keyboardDevice.injectKeyUp(KEY_LEFTALT)
                verifier.assertReceivedKey(withKeyCode(KeyEvent.KEYCODE_ALT_LEFT))

                activity.assertNoEvents()
            }
        }
    }

    @Test
    fun testHardwareKeyEventsWithRemapping_beforeKeyboardAdded() {
        ModifierRemappingFlag(true).use {
            // Add remapping before device is added
            remapModifierKey(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_SHIFT_LEFT)
            PollingCheck.waitFor { getModifierKeyRemapping().size == 1 }

            UinputKeyboard(instrumentation).use { keyboardDevice ->
                val inputDevice = inputManager.getInputDevice(keyboardDevice.deviceId)
                PollingCheck.waitFor {
                    KeyEvent.KEYCODE_SHIFT_LEFT ==
                            inputDevice?.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_ALT_LEFT)
                }

                keyboardDevice.injectKeyDown(KEY_LEFTALT)
                verifier.assertReceivedKey(
                    allOf(
                        withKeyCode(KeyEvent.KEYCODE_SHIFT_LEFT),
                        withKeyAction(KeyEvent.ACTION_DOWN),
                        withModifierState(KeyEvent.META_SHIFT_LEFT_ON or KeyEvent.META_SHIFT_ON)
                    )
                )

                keyboardDevice.injectKeyUp(KEY_LEFTALT)
                verifier.assertReceivedKey(withKeyCode(KeyEvent.KEYCODE_SHIFT_LEFT))

                clearAllModifierKeyRemappings()
                PollingCheck.waitFor {
                    KeyEvent.KEYCODE_ALT_LEFT ==
                            inputDevice?.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_ALT_LEFT)
                }

                keyboardDevice.injectKeyDown(KEY_LEFTALT)
                verifier.assertReceivedKey(
                    allOf(
                        withKeyCode(KeyEvent.KEYCODE_ALT_LEFT),
                        withKeyAction(KeyEvent.ACTION_DOWN),
                        withModifierState(KeyEvent.META_ALT_LEFT_ON or KeyEvent.META_ALT_ON)
                    )
                )

                keyboardDevice.injectKeyUp(KEY_LEFTALT)
                verifier.assertReceivedKey(withKeyCode(KeyEvent.KEYCODE_ALT_LEFT))

                activity.assertNoEvents()
            }
        }
    }

    @Test
    fun testAltToCapsLockRemapping_forKeyboardWithNoCapsLockKey() {
        ModifierRemappingFlag(true).use {
            UinputKeyboard(instrumentation, listOf("KEY_Q", "KEY_LEFTALT")).use { keyboardDevice ->
                val inputDevice = inputManager.getInputDevice(keyboardDevice.deviceId)
                remapModifierKey(KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_CAPS_LOCK)
                PollingCheck.waitFor {
                    KeyEvent.KEYCODE_CAPS_LOCK ==
                            inputDevice?.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_ALT_LEFT)
                }

                keyboardDevice.injectKeyDown(KEY_LEFTALT)
                verifier.assertReceivedKey(withKeyCode(KeyEvent.KEYCODE_CAPS_LOCK))

                keyboardDevice.injectKeyUp(KEY_LEFTALT)
                verifier.assertReceivedKey(
                    allOf(
                        withKeyCode(KeyEvent.KEYCODE_CAPS_LOCK),
                        withModifierState(KeyEvent.META_CAPS_LOCK_ON),
                    )
                )

                // Send second pair of key presses to reset caps lock state
                keyboardDevice.injectKeyDown(KEY_LEFTALT)
                verifier.assertReceivedKey(
                    allOf(
                        withKeyCode(KeyEvent.KEYCODE_CAPS_LOCK),
                        withModifierState(KeyEvent.META_CAPS_LOCK_ON),
                    )
                )

                keyboardDevice.injectKeyUp(KEY_LEFTALT)
                verifier.assertReceivedKey(withKeyCode(KeyEvent.KEYCODE_CAPS_LOCK))

                clearAllModifierKeyRemappings()
                PollingCheck.waitFor {
                    KeyEvent.KEYCODE_ALT_LEFT ==
                            inputDevice?.getKeyCodeForKeyLocation(KeyEvent.KEYCODE_ALT_LEFT)
                }

                keyboardDevice.injectKeyDown(KEY_LEFTALT)
                verifier.assertReceivedKey(
                    allOf(
                        withKeyCode(KeyEvent.KEYCODE_ALT_LEFT),
                        withKeyAction(KeyEvent.ACTION_DOWN),
                        withModifierState(KeyEvent.META_ALT_LEFT_ON or KeyEvent.META_ALT_ON)
                    )
                )

                keyboardDevice.injectKeyUp(KEY_LEFTALT)
                verifier.assertReceivedKey(withKeyCode(KeyEvent.KEYCODE_ALT_LEFT))

                activity.assertNoEvents()
            }
        }
    }

    /**
     * Remaps a modifier key to another modifier key
     *
     * @param fromKey modifier key getting remapped
     * @param toKey   modifier key that it is getting remapped to
     */
    private fun remapModifierKey(fromKey: Int, toKey: Int) {
        SystemUtil.runWithShellPermissionIdentity(
            { inputManager.remapModifierKey(fromKey, toKey) },
            Manifest.permission.REMAP_MODIFIER_KEYS
        )
    }

    /**
     * Clears remapping for a modifier key
     */
    private fun clearAllModifierKeyRemappings() {
        SystemUtil.runWithShellPermissionIdentity(
            { inputManager.clearAllModifierKeyRemappings() },
            Manifest.permission.REMAP_MODIFIER_KEYS
        )
        PollingCheck.waitFor { getModifierKeyRemapping().isEmpty() }
    }

    private fun getModifierKeyRemapping(): Map<Int, Int> {
        return SystemUtil.runWithShellPermissionIdentity(
            ThrowingSupplier<Map<Int, Int>> { inputManager.modifierKeyRemapping },
            Manifest.permission.REMAP_MODIFIER_KEYS
        )
    }

    private inner class ModifierRemappingFlag constructor(enabled: Boolean) : AutoCloseable {
        init {
            Settings.Global.putString(
                activity.contentResolver,
                "settings_new_keyboard_modifier_key",
                enabled.toString()
            )
        }

        override fun close() {
            Settings.Global.putString(
                activity.contentResolver,
                "settings_new_keyboard_modifier_key",
                ""
            )
        }
    }
}
