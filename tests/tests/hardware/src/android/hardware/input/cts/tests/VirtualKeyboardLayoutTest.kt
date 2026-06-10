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
import android.hardware.display.VirtualDisplay
import android.hardware.input.InputManager
import android.hardware.input.VirtualKeyboard
import android.hardware.input.cts.virtualcreators.VirtualInputDeviceCreator
import android.view.InputDevice
import android.view.KeyEvent
import android.virtualdevice.cts.common.VirtualDeviceRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RunWith(AndroidJUnit4::class)
class VirtualKeyboardLayoutTest {
    @get:Rule
    var mRule: VirtualDeviceRule = VirtualDeviceRule.createDefault()

    private lateinit var mInputManager: InputManager
    private lateinit var mVirtualDevice: VirtualDeviceManager.VirtualDevice
    private lateinit var mVirtualDisplay: VirtualDisplay

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        mInputManager = context.getSystemService(InputManager::class.java)
        mVirtualDevice = mRule.createManagedVirtualDevice()
        mVirtualDisplay = mRule.createManagedVirtualDisplay(
            mVirtualDevice, VirtualDeviceRule.createTrustedVirtualDisplayConfigBuilder()
        )!!
    }

    @Test
    fun createVirtualKeyboard_layoutSelected() {
        val frenchKeyboard = createVirtualKeyboard("fr-Latn-FR", "azerty")
        assertKeyMappings(
            frenchKeyboard,
            intArrayOf(
                KeyEvent.KEYCODE_Q,
                KeyEvent.KEYCODE_W,
                KeyEvent.KEYCODE_E,
                KeyEvent.KEYCODE_R,
                KeyEvent.KEYCODE_T,
                KeyEvent.KEYCODE_Y,
            ),
            intArrayOf(
                KeyEvent.KEYCODE_A,
                KeyEvent.KEYCODE_Z,
                KeyEvent.KEYCODE_E,
                KeyEvent.KEYCODE_R,
                KeyEvent.KEYCODE_T,
                KeyEvent.KEYCODE_Y,
            )
        )

        val swissGermanKeyboard = createVirtualKeyboard("de-CH", "qwertz")
        assertKeyMappings(
            swissGermanKeyboard,
            intArrayOf(
                KeyEvent.KEYCODE_Q,
                KeyEvent.KEYCODE_W,
                KeyEvent.KEYCODE_E,
                KeyEvent.KEYCODE_R,
                KeyEvent.KEYCODE_T,
                KeyEvent.KEYCODE_Y,
            ),
            intArrayOf(
                KeyEvent.KEYCODE_Q,
                KeyEvent.KEYCODE_W,
                KeyEvent.KEYCODE_E,
                KeyEvent.KEYCODE_R,
                KeyEvent.KEYCODE_T,
                KeyEvent.KEYCODE_Z,
            )
        )
    }

    @Test
    fun createVirtualKeyboard_layoutSelected_differentLayoutType() {
        val qwertyKeyboard = createVirtualKeyboard("en-Latn-US", "qwerty")
        assertKeyMappings(
            qwertyKeyboard,
            intArrayOf(
                KeyEvent.KEYCODE_Q,
                KeyEvent.KEYCODE_W,
                KeyEvent.KEYCODE_E,
                KeyEvent.KEYCODE_R,
                KeyEvent.KEYCODE_T,
                KeyEvent.KEYCODE_Y,
            ),
            intArrayOf(
                KeyEvent.KEYCODE_Q,
                KeyEvent.KEYCODE_W,
                KeyEvent.KEYCODE_E,
                KeyEvent.KEYCODE_R,
                KeyEvent.KEYCODE_T,
                KeyEvent.KEYCODE_Y,
            )
        )

        val dvorakKeyboard = createVirtualKeyboard("en-Latn-US", "dvorak")
        assertKeyMappings(
            dvorakKeyboard,
            intArrayOf(
                KeyEvent.KEYCODE_Q,
                KeyEvent.KEYCODE_W,
                KeyEvent.KEYCODE_E,
                KeyEvent.KEYCODE_R,
                KeyEvent.KEYCODE_T,
                KeyEvent.KEYCODE_Y,
            ),
            intArrayOf(
                KeyEvent.KEYCODE_APOSTROPHE,
                KeyEvent.KEYCODE_COMMA,
                KeyEvent.KEYCODE_PERIOD,
                KeyEvent.KEYCODE_P,
                KeyEvent.KEYCODE_Y,
                KeyEvent.KEYCODE_F,
            )
        )
    }

    private fun createVirtualKeyboard(languageTag: String, layoutType: String): InputDevice {
        val virtualKeyboard: VirtualKeyboard = VirtualInputDeviceCreator.createAndPrepareKeyboard(
            mVirtualDevice,
            "$DEVICE_NAME/$languageTag/$layoutType",
            mVirtualDisplay.display,
            languageTag,
            layoutType
        ).device
        val inputDevice = mInputManager.getInputDevice(virtualKeyboard.inputDeviceId)
        assertThat(inputDevice).isNotNull()
        return inputDevice!!
    }

    private fun assertKeyMappings(device: InputDevice, fromKeys: IntArray, toKeys: IntArray) {
        for (i in fromKeys.indices) {
            assertEquals(
                "Key location " +
                        KeyEvent.keyCodeToString(fromKeys[i]) +
                        " should map to " +
                        KeyEvent.keyCodeToString(toKeys[i]) +
                        " on a " +
                        device.keyboardLanguageTag +
                        "/" +
                        device.keyboardLayoutType +
                        " layout.",
                device.getKeyCodeForKeyLocation(fromKeys[i]).toLong(),
                toKeys[i].toLong()
            )
        }
    }

    companion object {
        private const val DEVICE_NAME = "CtsVirtualKeyboardTestDevice"
    }
}
