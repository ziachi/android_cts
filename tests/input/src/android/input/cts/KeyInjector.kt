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

import com.android.cts.input.EvdevInputEventCodes.Companion.EV_KEY
import com.android.cts.input.EvdevInputEventCodes.Companion.EV_KEY_PRESS
import com.android.cts.input.EvdevInputEventCodes.Companion.EV_KEY_RELEASE
import com.android.cts.input.EvdevInputEventCodes.Companion.EV_SYN
import com.android.cts.input.EvdevInputEventCodes.Companion.SYN_REPORT
import com.android.cts.input.UinputDevice

fun injectEvents(device: UinputDevice, events: IntArray) {
    device.injectEvents(events.joinToString(prefix = "[", postfix = "]", separator = ","))
}

fun injectKeyDown(device: UinputDevice, scanCode: Int) {
    injectEvents(device, intArrayOf(EV_KEY, scanCode, EV_KEY_PRESS, EV_SYN, SYN_REPORT, 0))
}

fun injectKeyUp(device: UinputDevice, scanCode: Int) {
    injectEvents(device, intArrayOf(EV_KEY, scanCode, EV_KEY_RELEASE, EV_SYN, SYN_REPORT, 0))
}
