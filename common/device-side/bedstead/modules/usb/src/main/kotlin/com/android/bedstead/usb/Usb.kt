/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.bedstead.usb

import android.hardware.usb.UsbManager
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.utils.ShellCommand

/** Entrance point to USB TestAPIs. */
object Usb {

    private val usbManager =
        TestApis.context().instrumentedContext().getSystemService(UsbManager::class.java)!!

    /**
     * True if this device has an active USB connection.
     */
    fun isConnected(): Boolean {
        return ShellCommand.builder("dumpsys usb")
                .executeAndParseOutput { it.contains("connected=true") }
    }
}

/** Entrance point to USB TestAPIs. */
fun TestApis.usb() = Usb