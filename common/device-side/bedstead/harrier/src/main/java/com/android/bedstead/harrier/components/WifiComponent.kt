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
package com.android.bedstead.harrier.components

import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.DeviceStateComponent
import com.android.bedstead.harrier.annotations.EnsureWifiDisabled
import com.android.bedstead.harrier.annotations.EnsureWifiEnabled
import com.android.bedstead.nene.TestApis.wifi

/**
 * Contains logic specific to Wi-Fi for Bedstead tests using [DeviceState] rule
 */
class WifiComponent : DeviceStateComponent {

    private var mOriginalWifiEnabled: Boolean? = null

    /**
     * See [EnsureWifiEnabled]
     */
    fun ensureWifiEnabled() {
        if (mOriginalWifiEnabled == null) {
            mOriginalWifiEnabled = wifi().isEnabled
        }
        wifi().isEnabled = true
    }

    /**
     * See [EnsureWifiDisabled]
     */
    fun ensureWifiDisabled() {
        if (mOriginalWifiEnabled == null) {
            mOriginalWifiEnabled = wifi().isEnabled
        }
        wifi().isEnabled = false
    }

    override fun teardownShareableState() {
        mOriginalWifiEnabled?.let {
            wifi().isEnabled = it
            mOriginalWifiEnabled = null
        }
    }
}
