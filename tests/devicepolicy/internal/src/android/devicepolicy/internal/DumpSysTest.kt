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

package android.devicepolicy.internal

import com.android.bedstead.enterprise.annotations.EnsureHasWorkProfile
import com.android.bedstead.enterprise.dpc
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.nene.utils.ShellCommand
import com.google.common.truth.Truth.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class DumpSysTest {

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val deviceState = DeviceState()
    }

    @Test
    @EnsureHasWorkProfile(dpcIsPrimary = true)
    fun dumpSys_containsPolicy() {
        try {
            deviceState.dpc().devicePolicyManager()
                    .setScreenCaptureDisabled(deviceState.dpc().componentName(), true)

            assertThat(
                ShellCommand.builder("dumpsys device_policy").validate(String::isNotEmpty).execute()
            ).contains("BooleanPolicyValue { mValue= true } }")
        } finally {
            deviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    deviceState.dpc().componentName(),
                    false
            )
        }
    }
}
