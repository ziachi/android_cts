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

package android.companion.cts.core

import android.companion.AssociationInfo
import android.companion.AssociationRequest.DEVICE_PROFILE_WATCH
import android.companion.Flags
import android.companion.cts.common.MAC_ADDRESS_A
import android.companion.cts.common.MAC_ADDRESS_B
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test [android.companion.AssociationInfo.Builder].
 *
 * Run: atest CtsCompanionDeviceManagerCoreTestCases:AssociationInfoBuilderTest
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(Flags.FLAG_NEW_ASSOCIATION_BUILDER)
class AssociationInfoBuilderTest : CoreTestBase() {
    @get:Rule
    val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Test
    fun test_setters() = with(targetApp) {
        associate(MAC_ADDRESS_A)

        var associationInfo = cdm.myAssociations[0]
        var currentTime = System.currentTimeMillis()

        var newAssociationInfo = AssociationInfo.Builder(associationInfo)
                .setDeviceProfile(DEVICE_PROFILE_WATCH)
                .setSelfManaged(true)
                .setDisplayName(DISPLAY_NAME)
                .setLastTimeConnected(currentTime)
                .setTimeApproved(currentTime)
                .setDeviceMacAddress(MAC_ADDRESS_B)
                .build()

        assertEquals(actual = newAssociationInfo.deviceProfile, expected = DEVICE_PROFILE_WATCH)
        assertEquals(actual = newAssociationInfo.displayName, expected = DISPLAY_NAME)
        assertEquals(actual = newAssociationInfo.lastTimeConnectedMs, expected = currentTime)
        assertEquals(actual = newAssociationInfo.timeApprovedMs, expected = currentTime)
        assertEquals(actual = newAssociationInfo.deviceMacAddress, expected = MAC_ADDRESS_B)
        assertTrue(newAssociationInfo.isSelfManaged)
    }

    companion object {
        private const val DISPLAY_NAME = "My Device"
    }
}
