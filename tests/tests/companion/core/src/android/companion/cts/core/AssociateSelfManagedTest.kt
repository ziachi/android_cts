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

import android.Manifest.permission.REQUEST_COMPANION_PROFILE_WATCH
import android.Manifest.permission.REQUEST_COMPANION_SELF_MANAGED
import android.companion.AssociationRequest
import android.companion.AssociationRequest.DEVICE_PROFILE_WATCH
import android.companion.Flags
import android.companion.cts.common.RecordingCallback
import android.companion.cts.common.RecordingCallback.OnAssociationCreated
import android.companion.cts.common.SIMPLE_EXECUTOR
import android.companion.cts.common.assertEmpty
import android.graphics.drawable.Icon
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test CDM APIs for requesting establishing new associations.
 *
 * Run: atest CtsCompanionDeviceManagerCoreTestCases:AssociateSelfManagedTest
 *
 * @see android.companion.CompanionDeviceManager.associate
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
class AssociateSelfManagedTest : CoreTestBase() {
    @get:Rule
    val checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Test
    fun test_associate_selfManaged_requiresPermission() {
        val request: AssociationRequest = AssociationRequest.Builder()
                .setSelfManaged(true)
                .setDisplayName(DEVICE_DISPLAY_NAME)
                .build()
        val callback = RecordingCallback()

        // Attempts to create a "self-managed" association without the MANAGE_COMPANION_DEVICES
        // permission should lead to a SecurityException being thrown.
        assertFailsWith(SecurityException::class) {
            cdm.associate(request, SIMPLE_EXECUTOR, callback)
        }
        assertEmpty(callback.invocations)

        // Same call with the MANAGE_COMPANION_DEVICES permissions should succeed.
        withShellPermissionIdentity(REQUEST_COMPANION_SELF_MANAGED) {
            cdm.associate(request, SIMPLE_EXECUTOR, callback)
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ASSOCIATION_DEVICE_ICON)
    fun test_associate_setDeviceIcon_requiresSelfManagedAssociation() {
        val icon = Icon.createWithResource(context, R.drawable.ic_cts_device_icon)
        val requestA: AssociationRequest = AssociationRequest.Builder()
            .setDisplayName(DEVICE_DISPLAY_NAME)
            .setDeviceIcon(icon)
            .build()
        val callbackA = RecordingCallback()

        // Attempts to create a normal association with setDeviceIcon
        assertFailsWith(SecurityException::class) {
            cdm.associate(requestA, SIMPLE_EXECUTOR, callbackA)
        }
        assertEmpty(callbackA.invocations)

        val requestB: AssociationRequest = AssociationRequest.Builder()
            .setDisplayName(DEVICE_DISPLAY_NAME)
            .setDeviceIcon(icon)
            .setSelfManaged(true)
            .build()

        callbackA.clearRecordedInvocations()
        val callbackB = RecordingCallback()

        // Same call with the MANAGE_COMPANION_DEVICES permission +
        // selfManaged association request should succeed.
        withShellPermissionIdentity(REQUEST_COMPANION_SELF_MANAGED) {
            cdm.associate(requestB, SIMPLE_EXECUTOR, callbackB)
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ASSOCIATION_DEVICE_ICON)
    fun test_associate_setDeviceIcon_invalid_icon() {
        val request: AssociationRequest = AssociationRequest.Builder()
            .setDisplayName(DEVICE_DISPLAY_NAME)
            .setSelfManaged(true)
            .setDeviceIcon(
                Icon.createWithResource(context, R.drawable.ic_cts_device_icon_invalid)
            ).build()
        val callbackA = RecordingCallback()

        withShellPermissionIdentity(REQUEST_COMPANION_SELF_MANAGED) {
            // Even if the provided icon is invalid, the system will resize the
            // invalid icon to an acceptable size.
            cdm.associate(request, SIMPLE_EXECUTOR, callbackA)
        }
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ASSOCIATION_DEVICE_ICON)
    fun get_deviceIcon_after_refresh_cache() {
        val iconA = Icon.createWithResource(context, R.drawable.ic_cts_device_icon)
        val request: AssociationRequest = AssociationRequest.Builder()
            .setDisplayName(DEVICE_DISPLAY_NAME)
            .setSelfManaged(true)
            .setDeviceIcon(iconA).build()
        val callbackA = RecordingCallback()

        withShellPermissionIdentity(REQUEST_COMPANION_SELF_MANAGED) {
            cdm.associate(request, SIMPLE_EXECUTOR, callbackA)
        }
        val iconB = cdm.myAssociations[0].deviceIcon
        assertNotNull(iconB, "The device icon should not be null.")

        // Reload associationInfo from the disk, the deviceIcon should be remain.
        runShellCommand("cmd companiondevice refresh-cache")
        val iconC = cdm.myAssociations[0].deviceIcon
        iconB?.bitmap?.let { bBitmap ->
            iconC?.bitmap?.let { cBitmap ->
                assertTrue(bBitmap.sameAs(cBitmap))
            }
        }
        assertNotNull(iconC, "The device icon should not be null.")
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ASSOCIATION_DEVICE_ICON)
    fun get_deviceIcon_URI_is_not_allowed() {
        val iconA = Icon.createWithContentUri("abc")
        val request: AssociationRequest = AssociationRequest.Builder()
            .setDisplayName(DEVICE_DISPLAY_NAME)
            .setSelfManaged(true)
            .setDeviceIcon(iconA).build()
        val callbackA = RecordingCallback()

        withShellPermissionIdentity(REQUEST_COMPANION_SELF_MANAGED) {
            // Attempts to create a normal association with setDeviceIcon
            assertFailsWith(IllegalArgumentException::class) {
                cdm.associate(request, SIMPLE_EXECUTOR, callbackA)
            }
        }
    }

    @Test
    fun test_associate_selfManaged_nullProfile_leadsToNoUiFlow() {
        val request: AssociationRequest = AssociationRequest.Builder()
                .setSelfManaged(true)
                .setDisplayName(DEVICE_DISPLAY_NAME)
                .build()
        val callback = RecordingCallback()

        callback.assertInvokedByActions {
            withShellPermissionIdentity(REQUEST_COMPANION_SELF_MANAGED) {
                cdm.associate(request, SIMPLE_EXECUTOR, callback)
            }
        }

        // Check callback invocations: there should have been exactly 1 invocation of the
        // onAssociationCreated() method.
        assertEquals(1, callback.invocations.size)
        val associationInvocation = callback.invocations.first()
        assertIs<OnAssociationCreated>(associationInvocation)
        with(associationInvocation.associationInfo) {
            assertEquals(actual = displayName, expected = DEVICE_DISPLAY_NAME)
            assertNull(deviceProfile)
            assertTrue(isSelfManaged)
        }

        // Check that the newly created association is included in
        // CompanionDeviceManager.getMyAssociations()
        assertContentEquals(
            actual = cdm.myAssociations,
            expected = listOf(associationInvocation.associationInfo)
        )
    }

    @Test
    fun test_associate_selfManaged_alreadyRoleHolder_leadsToNoUiFlow() =
            targetApp.withRole(ROLE_WATCH) {
                val request: AssociationRequest = AssociationRequest.Builder()
                        .setSelfManaged(true)
                        .setDeviceProfile(DEVICE_PROFILE_WATCH)
                        .setDisplayName(DEVICE_DISPLAY_NAME)
                        .build()
                val callback = RecordingCallback()

                callback.assertInvokedByActions {
                    withShellPermissionIdentity(
                        REQUEST_COMPANION_SELF_MANAGED,
                        REQUEST_COMPANION_PROFILE_WATCH
                    ) {
                        cdm.associate(request, SIMPLE_EXECUTOR, callback)
                    }
                }

                // Check callback invocations: there should have been exactly 1 invocation of the
                // onAssociationCreated().
                assertEquals(1, callback.invocations.size)
                val associationInvocation = callback.invocations.first()
                assertIs<OnAssociationCreated>(associationInvocation)
                with(associationInvocation.associationInfo) {
                    assertEquals(actual = displayName, expected = DEVICE_DISPLAY_NAME)
                    assertEquals(actual = deviceProfile, expected = ROLE_WATCH)
                    assertTrue(isSelfManaged)
                }

                // Check that the newly created association is included in
                // CompanionDeviceManager.getMyAssociations()
                assertContentEquals(
                    actual = cdm.myAssociations,
                    expected = listOf(associationInvocation.associationInfo)
                )
            }

    override fun tearDown() {
        targetApp.removeFromHoldersOfRole(ROLE_WATCH)
        super.tearDown()
    }

    companion object {
        private const val DEVICE_DISPLAY_NAME = "My device"
        private const val ROLE_WATCH = DEVICE_PROFILE_WATCH
    }
}
