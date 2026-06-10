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
package com.android.bedstead.bluetooth

import com.android.bedstead.bluetooth.annotations.EnsureBluetoothDisabled
import com.android.bedstead.bluetooth.annotations.EnsureBluetoothEnabled
import com.android.bedstead.enterprise.UserRestrictionsComponent
import com.android.bedstead.harrier.BedsteadServiceLocator
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.DeviceStateComponent
import com.android.bedstead.nene.TestApis.bluetooth
import com.android.bedstead.nene.TestApis.users
import com.android.bedstead.nene.userrestrictions.CommonUserRestrictions
import org.junit.Assume

/**
 * Contains logic specific to Bluetooth for Bedstead tests using [DeviceState] rule
 *
 * @param locator provides access to other dependencies.
 */
class BluetoothComponent(locator: BedsteadServiceLocator) : DeviceStateComponent {

    private val userRestrictionsComponent: UserRestrictionsComponent by locator
    private var originalBluetoothEnabled: Boolean? = null

    /**
     * See [EnsureBluetoothEnabled]
     */
    fun ensureBluetoothEnabled() {
        // TODO(b/220306133): bluetooth from background
        Assume.assumeTrue(
            "Can only configure bluetooth from foreground",
            users().instrumented().isForeground()
        )
        userRestrictionsComponent.ensureDoesNotHaveUserRestriction(
            CommonUserRestrictions.DISALLOW_BLUETOOTH
        )
        if (originalBluetoothEnabled == null) {
            originalBluetoothEnabled = bluetooth().isEnabled
        }
        bluetooth().setEnabled(true)
    }

    /**
     * See [EnsureBluetoothDisabled]
     */
    fun ensureBluetoothDisabled() {
        Assume.assumeTrue(
            "Can only configure bluetooth from foreground",
            users().instrumented().isForeground()
        )
        if (originalBluetoothEnabled == null) {
            originalBluetoothEnabled = bluetooth().isEnabled
        }
        bluetooth().setEnabled(false)
    }

    override fun teardownShareableState() {
        originalBluetoothEnabled?.let {
            bluetooth().setEnabled(it)
            originalBluetoothEnabled = null
        }
    }
}
