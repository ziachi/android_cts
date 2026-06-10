/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.companion.AssociationRequest
import android.companion.CompanionDeviceManager.FLAG_CALL_METADATA
import android.companion.DeviceId
import android.companion.Flags
import android.companion.cts.common.CUSTOM_ID_A
import android.companion.cts.common.CUSTOM_ID_B
import android.companion.cts.common.CUSTOM_ID_INVALID
import android.companion.cts.common.MAC_ADDRESS_A
import android.companion.cts.common.MAC_ADDRESS_B
import android.companion.cts.common.RecordingCallback
import android.companion.cts.common.RecordingCallback.OnAssociationPending
import android.companion.cts.common.SIMPLE_EXECUTOR
import android.companion.cts.common.getAssociationForPackage
import android.net.MacAddress
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compatibility.common.util.FeatureUtil
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test CDM APIs for requesting establishing new associations.
 *
 * Run: atest CtsCompanionDeviceManagerCoreTestCases:AssociateTest
 *
 * @see android.companion.CompanionDeviceManager.associate
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
class AssociateTest : CoreTestBase() {
    @get:Rule
    val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Test
    fun test_associate() {
        assumeFalse(FeatureUtil.isWatch())

        val request: AssociationRequest = AssociationRequest.Builder()
            .build()
        val callback = RecordingCallback()

        callback.assertInvokedByActions {
            cdm.associate(request, SIMPLE_EXECUTOR, callback)
        }
        // Check callback invocations: there should have been exactly 1 invocation of the
        // onAssociationPending() method.
        assertEquals(1, callback.invocations.size)
        assertIs<OnAssociationPending>(callback.invocations.first())
    }

    @Test
    fun test_systemDataSyncForTypes() = with(targetApp) {
        associate(MAC_ADDRESS_A)

        var associations = cdm.myAssociations
        assertEquals(
            0,
            associations[0].systemDataSyncFlags and FLAG_CALL_METADATA
        )

        cdm.enableSystemDataSyncForTypes(associations[0].id, FLAG_CALL_METADATA)
        associations = cdm.myAssociations
        assertEquals(
            FLAG_CALL_METADATA,
            associations[0].systemDataSyncFlags and FLAG_CALL_METADATA
        )

        cdm.disableSystemDataSyncForTypes(associations[0].id, FLAG_CALL_METADATA)
        associations = cdm.myAssociations
        assertEquals(0, associations[0].systemDataSyncFlags and FLAG_CALL_METADATA)
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ASSOCIATION_TAG)
    fun test_association_invalid_customized_deviceId() {
        targetApp.associate(MAC_ADDRESS_A)
        // Make sure that the length of given device id must less than 120.
        assertFailsWith(IllegalArgumentException::class) {
            createDeviceId(CUSTOM_ID_INVALID, null)
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ASSOCIATION_TAG)
    fun test_association_deviceId_different_packages() = with(testApp) {
        associate(MAC_ADDRESS_A)
        val deviceId = createDeviceId(CUSTOM_ID_A, MAC_ADDRESS_A)
        val association = withShellPermissionIdentity {
            getAssociationForPackage(userId, packageName, MAC_ADDRESS_A, cdm)
        }

        // Only the package that own the association is able to set the device id.
        assertFailsWith(SecurityException::class) {
            cdm.setDeviceId(association.id, deviceId)
        }

        withShellPermissionIdentity {
            cdm.setDeviceId(association.id, deviceId)
        }

        val associationWithDeviceId = withShellPermissionIdentity {
            getAssociationForPackage(userId, packageName, MAC_ADDRESS_A, cdm)
        }

        assertEquals(
            expected = CUSTOM_ID_A,
            actual = associationWithDeviceId.getDeviceId()?.customId
        )
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ASSOCIATION_TAG)
    fun test_association_deviceId() = with(targetApp) {
        associate(MAC_ADDRESS_A)
        val deviceId = createDeviceId(CUSTOM_ID_A, MAC_ADDRESS_A)

        val association = withShellPermissionIdentity {
            getAssociationForPackage(userId, packageName, MAC_ADDRESS_A, cdm)
        }

        cdm.setDeviceId(association.id, deviceId)

        val associationWithDeviceId = cdm.myAssociations[0]

        assertEquals(
            expected = CUSTOM_ID_A,
            actual = associationWithDeviceId.getDeviceId()?.customId
        )

        assertEquals(
            expected = MAC_ADDRESS_A,
            actual = associationWithDeviceId.getDeviceId()?.macAddress
        )

        cdm.setDeviceId(association.id, null)

        val associationNullDeviceId = cdm.myAssociations[0]

        assertEquals(expected = null, actual = associationNullDeviceId.getDeviceId())
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ASSOCIATION_TAG)
    fun test_association_deviceId_after_load_from_disk() = with(targetApp) {
        associate(MAC_ADDRESS_A)
        val deviceIdA = createDeviceId(CUSTOM_ID_A, MAC_ADDRESS_A)
        val deviceIdB = createDeviceId(CUSTOM_ID_B, MAC_ADDRESS_B)

        val association = withShellPermissionIdentity {
            getAssociationForPackage(userId, packageName, MAC_ADDRESS_A, cdm)
        }

        cdm.setDeviceId(association.id, deviceIdA)
        // Re-set the device id.
        cdm.setDeviceId(association.id, deviceIdB)

        val associationWithDeviceId = cdm.myAssociations[0]
        assertEquals(
            expected = CUSTOM_ID_B,
            actual = associationWithDeviceId.getDeviceId()?.customId
        )

        assertEquals(
            expected = MAC_ADDRESS_B,
            actual = associationWithDeviceId.getDeviceId()?.macAddress
        )

        // Load the association from disk the, customized_device_id should be still remaining.
        runShellCommand("cmd companiondevice refresh-cache")

        assertEquals(
            expected = CUSTOM_ID_B,
            actual = associationWithDeviceId.getDeviceId()?.customId
        )

        assertEquals(
            expected = MAC_ADDRESS_B,
            actual = associationWithDeviceId.getDeviceId()?.macAddress
        )
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ASSOCIATION_TAG)
    fun test_at_least_one_device_id() {
        assertFailsWith(IllegalArgumentException::class) {
            createDeviceId(null, null)
        }

        val deviceIdA = createDeviceId(CUSTOM_ID_A, null)
        assertEquals(
            expected = CUSTOM_ID_A,
            actual = deviceIdA.customId
        )

        val deviceIdB = createDeviceId(null, MAC_ADDRESS_A)
        assertEquals(
            expected = MAC_ADDRESS_A,
            actual = deviceIdB.macAddress
        )

        val deviceIdC = createDeviceId(CUSTOM_ID_A, MAC_ADDRESS_A)
        assertEquals(
            expected = CUSTOM_ID_A,
            actual = deviceIdC.customId
        )
        assertEquals(
            expected = MAC_ADDRESS_A,
            actual = deviceIdC.macAddress
        )
    }

    private fun createDeviceId(id: String?, macAddress: MacAddress?): DeviceId {
        return DeviceId.Builder().setCustomId(id).setMacAddress(macAddress).build()
    }
}
