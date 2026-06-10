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

import android.Manifest
import android.companion.DevicePresenceEvent
import android.companion.Flags
import android.companion.ObservingDevicePresenceRequest
import android.companion.cts.common.MAC_ADDRESS_A
import android.companion.cts.common.PrimaryCompanionService
import android.companion.cts.common.UUID_A
import android.companion.cts.common.assertDevicePresenceEvent
import android.companion.cts.common.assertValidCompanionDeviceServicesBind
import android.companion.cts.common.assertValidCompanionDeviceServicesUnbind
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test send device event when the device is locked.
 *
 * Run: atest CtsCompanionDeviceManagerCoreTestCases:DeviceEventDeviceLockedTest
 *
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(Flags.FLAG_DEVICE_PRESENCE)
class DeviceEventDeviceLockedTest : CoreTestBase() {
    @get:Rule
    val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    override fun tearDown() {
        PrimaryCompanionService.clearConnectedDevices()
        PrimaryCompanionService.clearDeviceUuidPresence()
        super.tearDown()
    }

    @Test
    fun test_ble_event_on_device_locked() {
        // Create a regular (not self-managed) association.
        targetApp.associate(MAC_ADDRESS_A)
        val associationId = cdm.myAssociations[0].id

        val request = ObservingDevicePresenceRequest.Builder()
                .setAssociationId(associationId).build()

        // Start observing presence.
        withShellPermissionIdentity(Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE) {
            cdm.startObservingDevicePresence(request)
        }

        // simulate send call back when the device is locked.
        simulateDeviceEventDeviceLocked(
            associationId,
            userId,
            DevicePresenceEvent.EVENT_BLE_APPEARED,
            "null"
        )
        // App should not bind at this moment.
        assertValidCompanionDeviceServicesUnbind()
        // simulate device is unlocked.
        simulateDeviceEventDeviceUnlocked(userId)

        PrimaryCompanionService.waitAssociationToAppear(associationId)
        // App should receive ble appeared event after device is unlocked.
        PrimaryCompanionService.getCurrentEvent()
                ?.let { assertDevicePresenceEvent(DevicePresenceEvent.EVENT_BLE_APPEARED, it) }

        assertValidCompanionDeviceServicesBind()

        simulateDeviceEvent(associationId, DevicePresenceEvent.EVENT_BLE_DISAPPEARED)

        withShellPermissionIdentity(Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE) {
            cdm.stopObservingDevicePresence(request)
        }
    }

    @Test
    fun test_bt_event_on_device_locked() {
        // Create a regular (not self-managed) association.
        targetApp.associate(MAC_ADDRESS_A)
        val associationId = cdm.myAssociations[0].id

        val request = ObservingDevicePresenceRequest.Builder()
                .setAssociationId(associationId).build()

        // Start observing presence.
        withShellPermissionIdentity(Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE) {
            cdm.startObservingDevicePresence(request)
        }

        // simulate send call back when the device is locked.
        simulateDeviceEventDeviceLocked(
            associationId,
            userId,
            DevicePresenceEvent.EVENT_BT_CONNECTED,
            "null"
        )
        // App should not bind at this moment.
        assertValidCompanionDeviceServicesUnbind()
        // simulate device is unlocked.
        simulateDeviceEventDeviceUnlocked(userId)

        PrimaryCompanionService.waitAssociationToBtConnect(associationId)
        // App should receive BT connected event after device is unlocked.
        PrimaryCompanionService.getCurrentEvent()
                ?.let { assertDevicePresenceEvent(DevicePresenceEvent.EVENT_BT_CONNECTED, it) }

        assertValidCompanionDeviceServicesBind()

        simulateDeviceEvent(associationId, DevicePresenceEvent.EVENT_BT_DISCONNECTED)

        withShellPermissionIdentity(Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE) {
            cdm.stopObservingDevicePresence(request)
        }
    }

    @Test
    fun test_bt_ble_event_on_device_locked() {
        // Create a regular (not self-managed) association.
        targetApp.associate(MAC_ADDRESS_A)
        val associationId = cdm.myAssociations[0].id

        val request = ObservingDevicePresenceRequest.Builder()
                .setAssociationId(associationId).build()

        // Start observing presence.
        withShellPermissionIdentity(Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE) {
            cdm.startObservingDevicePresence(request)
        }

        // simulate send call back when the device is locked.
        simulateDeviceEventDeviceLocked(
            associationId,
            userId,
            DevicePresenceEvent.EVENT_BLE_APPEARED,
            "null"
        )
        simulateDeviceEventDeviceLocked(
            associationId,
            userId,
            DevicePresenceEvent.EVENT_BT_CONNECTED,
            "null"
        )

        // App should not bind at this moment.
        assertValidCompanionDeviceServicesUnbind()
        // simulate device is unlocked.
        simulateDeviceEventDeviceUnlocked(userId)

        PrimaryCompanionService.waitAssociationToAppear(associationId)
        PrimaryCompanionService.waitAssociationToBtConnect(associationId)

        assertValidCompanionDeviceServicesBind()

        simulateDeviceEvent(associationId, DevicePresenceEvent.EVENT_BLE_DISAPPEARED)
        simulateDeviceEvent(associationId, DevicePresenceEvent.EVENT_BT_DISCONNECTED)

        withShellPermissionIdentity(Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE) {
            cdm.stopObservingDevicePresence(request)
        }
    }

    @Test
    fun test_uuid_event_on_device_locked() {
        startObservingDevicePresenceByUuid(userId, targetPackageName, UUID_A.toString())

        simulateDeviceEventDeviceLocked(
            -1,
            userId,
            DevicePresenceEvent.EVENT_BT_CONNECTED,
            UUID_A.toString()
        )
        // App should not bind at this moment.
        assertValidCompanionDeviceServicesUnbind()
        // simulate device is unlocked.
        simulateDeviceEventDeviceUnlocked(userId)

        PrimaryCompanionService.waitDeviceUuidConnect(UUID_A)

        assertValidCompanionDeviceServicesBind()
        PrimaryCompanionService.getCurrentEvent()
                ?.let { assertDevicePresenceEvent(DevicePresenceEvent.EVENT_BT_CONNECTED, it) }

        simulateDeviceUuidEvent(UUID_A, DevicePresenceEvent.EVENT_BT_DISCONNECTED)
        PrimaryCompanionService.waitDeviceUuidDisconnect(UUID_A)

        stopObservingDevicePresenceByUuid(userId, targetPackageName, UUID_A.toString())
    }

    @Test
    fun test_ble_event_disappeared_before_device_unlocked() {
        // Create a regular (not self-managed) association.
        targetApp.associate(MAC_ADDRESS_A)
        val associationId = cdm.myAssociations[0].id

        val request = ObservingDevicePresenceRequest.Builder()
                .setAssociationId(associationId).build()

        // Start observing presence.
        withShellPermissionIdentity(Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE) {
            cdm.startObservingDevicePresence(request)
        }

        // simulate send call back when the device is locked.
        simulateDeviceEventDeviceLocked(
            associationId,
            userId,
            DevicePresenceEvent.EVENT_BLE_APPEARED,
            "null"
        )

        // simulate send negative callback before device is unlocked.
        simulateDeviceEventDeviceLocked(
            associationId,
            userId,
            DevicePresenceEvent.EVENT_BLE_DISAPPEARED,
            "null"
        )

        // Unlock the device.
        simulateDeviceEventDeviceUnlocked(userId)

        // App should not bind at this moment.
        assertValidCompanionDeviceServicesUnbind()

        // Start observing presence.
        withShellPermissionIdentity(Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE) {
            cdm.stopObservingDevicePresence(request)
        }
    }

    @Test
    fun test_bt_event_disconnected_before_device_unlocked() {
        // Create a regular (not self-managed) association.
        targetApp.associate(MAC_ADDRESS_A)
        val associationId = cdm.myAssociations[0].id
        val request = ObservingDevicePresenceRequest.Builder()
                .setAssociationId(associationId).build()

        // Start observing presence.
        withShellPermissionIdentity(Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE) {
            cdm.startObservingDevicePresence(request)
        }

        // simulate send call back when the device is locked.
        simulateDeviceEventDeviceLocked(
            associationId,
            userId,
            DevicePresenceEvent.EVENT_BT_CONNECTED,
            "null"
        )
        simulateDeviceEventDeviceLocked(
            associationId,
            userId,
            DevicePresenceEvent.EVENT_BT_DISCONNECTED,
            "null"
        )
        // simulate device is unlocked.
        simulateDeviceEventDeviceUnlocked(userId)
        // App should not bind at this moment.
        assertValidCompanionDeviceServicesUnbind()

        withShellPermissionIdentity(
            Manifest.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE,
        ) {
            cdm.stopObservingDevicePresence(request)
        }
    }

    @Test
    fun test_uuid_event_disconnected_before_device_unlocked() {
        startObservingDevicePresenceByUuid(userId, targetPackageName, UUID_A.toString())

        simulateDeviceEventDeviceLocked(
            -1,
            userId,
            DevicePresenceEvent.EVENT_BT_CONNECTED,
            UUID_A.toString()
        )
        simulateDeviceEventDeviceLocked(
            -1,
            userId,
            DevicePresenceEvent.EVENT_BT_DISCONNECTED,
            UUID_A.toString()
        )
        // simulate device is unlocked.
        simulateDeviceEventDeviceUnlocked(userId)
        // App should not bind at this moment.
        assertValidCompanionDeviceServicesUnbind()

        stopObservingDevicePresenceByUuid(userId, targetPackageName, UUID_A.toString())
    }
}
