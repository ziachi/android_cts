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

package com.android.cts.input

import android.app.Instrumentation
import android.view.InputDevice.SOURCE_KEYBOARD

private fun createKeyboardRegisterCommand(
    keys: List<String>,
    productId: Int
): UinputRegisterCommand {
    val configurationItems = listOf(
        ConfigurationItem("UI_SET_EVBIT", listOf("EV_KEY")),
        ConfigurationItem("UI_SET_KEYBIT", keys)
    )

    return UinputRegisterCommand(
        id = 1,
        name = "Test Keyboard (USB)",
        vid = 0x18d1,
        pid = productId,
        bus = "usb",
        port = "usb:1",
        configuration = configurationItems,
        absInfo = emptyMap(),
    )
}

/**
 * A Keyboard that only has a few common keys (lots of keys are missing, for simplicity).
 */
class UinputKeyboard(
    instrumentation: Instrumentation,
    keys: List<String> = listOf(
        "KEY_Q", "KEY_W", "KEY_E", "KEY_A", "KEY_B", "KEY_C", "KEY_BACKSPACE", "KEY_ESC",
        "KEY_LEFTALT", "KEY_LEFTMETA", "KEY_LEFT", "KEY_LEFTSHIFT", "KEY_CAPSLOCK",
    ),
    productId: Int = 0xabcd,
) : UinputDevice(
    instrumentation,
    SOURCE_KEYBOARD,
    createKeyboardRegisterCommand(keys, productId),
    null // display
) {

  companion object {
    const val EV_KEY = 1
    const val KEY_DOWN = 1
    const val KEY_UP = 0
    const val EV_SYN = 0
    const val SYN_REPORT = 0
  }

  // store the keys that are currently down
  private val keysDown = mutableSetOf<Int>()

  private fun injectEvents(events: IntArray) {
      injectEvents(events.joinToString(prefix = "[", postfix = "]", separator = ","))
  }

  fun injectKeyDown(scanCode: Int) {
      if (!keysDown.add(scanCode)) {
        throw IllegalArgumentException("Key $scanCode is already down")
      }
      injectEvents(intArrayOf(EV_KEY, scanCode, KEY_DOWN, EV_SYN, SYN_REPORT, 0))
  }

  fun injectKeyUp(scanCode: Int) {
      if (!keysDown.remove(scanCode)) {
        throw IllegalArgumentException("Key $scanCode is not down")
      }
      injectEvents(intArrayOf(EV_KEY, scanCode, KEY_UP, EV_SYN, SYN_REPORT, 0))
  }
}
