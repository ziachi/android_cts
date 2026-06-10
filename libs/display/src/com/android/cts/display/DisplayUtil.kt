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

package com.android.cts.display

import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Display

fun validateOnlyDefaultDisplayOn(displayManager: DisplayManager, tag: String): Boolean {
    val invalidStateDisplays = displayManager.displays.toList().filter { display ->
            // default display is not ON
            (display.displayId == Display.DEFAULT_DISPLAY && display.state != Display.STATE_ON) ||
                    // other than default display is ON
                    (display.displayId != Display.DEFAULT_DISPLAY &&
                            display.state == Display.STATE_ON)
        }.map { display -> Pair(display.displayId, display.state) }

    if (invalidStateDisplays.isNotEmpty()) {
        Log.d(tag, "Displays are in invalid state: $invalidStateDisplays")
        return false
    }
    return true
}
