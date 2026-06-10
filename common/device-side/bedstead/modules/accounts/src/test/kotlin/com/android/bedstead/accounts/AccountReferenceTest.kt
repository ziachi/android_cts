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
package com.android.bedstead.accounts

import com.android.bedstead.accounts.annotations.EnsureHasAccount
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.google.common.truth.Truth
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class AccountReferenceTest {
    @Test
    @EnsureHasAccount(features = ["feature"])
    fun hasFeature_accountHasFeature_returnsTrue() {
        Truth.assertThat(sDeviceState.account().hasFeature("feature")).isTrue()
    }

    @Test
    @EnsureHasAccount(features = [])
    fun hasFeature_accountDoesNotHaveFeature_returnsFalse() {
        Truth.assertThat(sDeviceState.account().hasFeature("feature")).isFalse()
    }

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val sDeviceState: DeviceState = DeviceState()
    }
}
