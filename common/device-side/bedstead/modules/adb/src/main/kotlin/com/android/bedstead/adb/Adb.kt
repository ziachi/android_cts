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

package com.android.bedstead.adb

import android.util.Log
import com.android.bedstead.nene.utils.ShellCommandUtils
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.annotations.Experimental
import com.android.bedstead.nene.exceptions.NeneException

/** Helper methods related to Adb. */
object Adb {

    private const val ADB_WIFI_ENABLED = "adb_wifi_enabled"
    private const val LOG_TAG = "Adb"

    /**
     * True if the device can run commands as root.
     */
    fun isRootAvailable(): Boolean = ShellCommandUtils.isRootAvailable()

    /**
     * True if Adb is enabled over wifi.
     */
    // TODO(scottjonathan): This API would be more useful if it checked if we actually had an
    // active adb connection over wifi rather than just if the setting is set.
    @Experimental
    fun isEnabledOverWifi(): Boolean {
        return try {
            TestApis.settings().global().getInt(ADB_WIFI_ENABLED) == 1
        } catch (exception: NeneException) {
            Log.w(LOG_TAG,"ADB_WIFI_ENABLED setting not found on the device", exception)
            false
        }
    }
}

fun TestApis.adb() = Adb
