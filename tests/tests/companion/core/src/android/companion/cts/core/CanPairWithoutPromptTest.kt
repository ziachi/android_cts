/*
 * Copyright (C) 2025 The Android Open Source Project
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

import android.Manifest.permission.MANAGE_COMPANION_DEVICES
import android.companion.cts.common.MAC_ADDRESS_A
import android.os.UserHandle
import android.platform.test.annotations.AppModeFull
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test CDM APIs for CanPairWithoutPromptTest.
 *
 * Run: atest CtsCompanionDeviceManagerCoreTestCases:CanPairWithoutPromptTest
 *
 * @see android.companion.CompanionDeviceManager.canPairWithoutPrompt
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
class CanPairWithoutPromptTest : CoreTestBase() {

    @Test
    fun test_canPairWithoutPromptTest() {
        targetApp.associate(MAC_ADDRESS_A)
        withShellPermissionIdentity(MANAGE_COMPANION_DEVICES) {
            assertTrue(cdm.canPairWithoutPrompt(
                targetPackageName,
                MAC_ADDRESS_A.toString(),
                UserHandle.of(targetUserId)
            ))
        }
    }
}
