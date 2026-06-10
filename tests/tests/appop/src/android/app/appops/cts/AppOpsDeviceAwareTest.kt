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

package android.app.appops.cts

import android.Manifest
import android.app.AppOpsManager
import android.companion.virtual.VirtualDeviceManager
import android.companion.virtual.VirtualDeviceParams
import android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_CUSTOM
import android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_DEFAULT
import android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_CAMERA
import android.content.AttributionSource
import android.os.Process
import android.permission.PermissionManager
import android.permission.flags.Flags
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.virtualdevice.cts.common.VirtualDeviceRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@AppModeFull(reason = "Instant apps cannot hold GET_APP_OPS_STATS")
class AppOpsDeviceAwareTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val appOpsManager = context.getSystemService(AppOpsManager::class.java)!!
    private val permissionManager = context.getSystemService(PermissionManager::class.java)!!
    private lateinit var virtualDevice: VirtualDeviceManager.VirtualDevice

    @get:Rule val mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()
    @get:Rule
    var virtualDeviceRule =
        VirtualDeviceRule.withAdditionalPermissions(
            Manifest.permission.GRANT_RUNTIME_PERMISSIONS,
            Manifest.permission.REVOKE_RUNTIME_PERMISSIONS,
            Manifest.permission.GET_APP_OPS_STATS,
            Manifest.permission.MANAGE_APPOPS,
        )

    @Before
    fun setUp() {
        virtualDevice =
            virtualDeviceRule.createManagedVirtualDevice(
                VirtualDeviceParams.Builder()
                    .setDevicePolicy(POLICY_TYPE_CAMERA, DEVICE_POLICY_CUSTOM)
                    .build()
            )

        // Reset app ops state for this test package to the system default.
        reset(context.opPackageName)
        appOpsManager.resetPackageOpsNoHistory(context.opPackageName)
    }

    @RequiresFlagsEnabled(
        Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED,
        Flags.FLAG_RUNTIME_PERMISSION_APPOPS_MAPPING_ENABLED
    )
    @Test
    fun virtualDeviceDefaultPolicy_opModeShouldBeFromDefaultDevicePermissionState() {
        virtualDevice =
            virtualDeviceRule.createManagedVirtualDevice(
                VirtualDeviceParams.Builder()
                    .setDevicePolicy(POLICY_TYPE_CAMERA, DEVICE_POLICY_DEFAULT)
                    .build()
            )

        val attributionSource =
            AttributionSource.Builder(Process.myUid())
                .setPackageName(context.opPackageName)
                .setAttributionTag(context.attributionTag)
                .setDeviceId(virtualDevice.deviceId)
                .build()

        // Verify permission is already granted on the default device. App op mode for a virtual
        // device should fall back to the default device permission state.
        assertThat(
            appOpsManager.unsafeCheckOpRawNoThrow(
                AppOpsManager.OPSTR_CAMERA,
                context.applicationInfo.uid,
                context.opPackageName
            )
        ).isEqualTo(AppOpsManager.MODE_FOREGROUND)

        assertThat(
            appOpsManager.unsafeCheckOpRawNoThrow(AppOpsManager.OPSTR_CAMERA, attributionSource)
        ).isEqualTo(AppOpsManager.MODE_FOREGROUND)
    }

    @RequiresFlagsEnabled(
        Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED,
        Flags.FLAG_RUNTIME_PERMISSION_APPOPS_MAPPING_ENABLED
    )
    @Test
    fun getUidMode_shouldBeInferredFromPermissionState() {
        val attributionSource =
            AttributionSource.Builder(Process.myUid())
                .setPackageName(context.opPackageName)
                .setAttributionTag(context.attributionTag)
                .setDeviceId(virtualDevice.deviceId)
                .build()

        // When permission is not granted, the app op mode should be IGNORED
        assertThat(
                appOpsManager.unsafeCheckOpRawNoThrow(AppOpsManager.OPSTR_CAMERA, attributionSource)
            )
            .isEqualTo(AppOpsManager.MODE_IGNORED)

        // Grant permission to the default device, expect it will not affect app op mode for the
        // external device.
        permissionManager.grantRuntimePermission(
            context.opPackageName,
            Manifest.permission.CAMERA,
            VirtualDeviceManager.PERSISTENT_DEVICE_ID_DEFAULT
        )

        assertThat(
                appOpsManager.unsafeCheckOpRawNoThrow(AppOpsManager.OPSTR_CAMERA, attributionSource)
            )
            .isEqualTo(AppOpsManager.MODE_IGNORED)

        // Grant permission to the external device, expect the app op mode is affected
        permissionManager.grantRuntimePermission(
            context.opPackageName,
            Manifest.permission.CAMERA,
            virtualDevice.persistentDeviceId!!
        )

        assertThat(
                appOpsManager.unsafeCheckOpRawNoThrow(AppOpsManager.OPSTR_CAMERA, attributionSource)
            )
            .isEqualTo(AppOpsManager.MODE_FOREGROUND)
    }

    @RequiresFlagsEnabled(
        Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED,
        Flags.FLAG_RUNTIME_PERMISSION_APPOPS_MAPPING_ENABLED
    )
    @Test
    fun virtualDeviceDefaultPolicy_opAccessShouldBeAttributedToDefaultDevice() {
        virtualDevice =
            virtualDeviceRule.createManagedVirtualDevice(
                VirtualDeviceParams.Builder()
                    .setDevicePolicy(POLICY_TYPE_CAMERA, DEVICE_POLICY_DEFAULT)
                    .build()
            )

        val attributionSource =
            AttributionSource.Builder(Process.myUid())
                .setPackageName(context.opPackageName)
                .setAttributionTag(context.attributionTag)
                .setDeviceId(virtualDevice.deviceId)
                .build()

        val startTimeMillis = System.currentTimeMillis()
        val mode =
            appOpsManager.noteOpNoThrow(AppOpsManager.OP_CAMERA, attributionSource, "message")
        val endTimeMillis = System.currentTimeMillis()

        assertThat(mode).isEqualTo(AppOpsManager.MODE_ALLOWED)

        // Expect noteOp doesn't create attributedOpEntry for virtual device
        val packagesOpsForExternalDevice =
            appOpsManager.getPackagesForOps(
                arrayOf(AppOpsManager.OPSTR_CAMERA),
                virtualDevice.persistentDeviceId!!
            )
        val packageOpsForExternalDevice =
            packagesOpsForExternalDevice.find { it.packageName == context.opPackageName }
        val opEntryForExternalDevice = packageOpsForExternalDevice!!.ops[0]
        assertThat(opEntryForExternalDevice.opStr).isEqualTo(AppOpsManager.OPSTR_CAMERA)
        assertThat(opEntryForExternalDevice.attributedOpEntries).isEmpty()

        // Expect op is noted for the default device
        val packagesOpsForDefaultDevice =
            appOpsManager.getPackagesForOps(arrayOf(AppOpsManager.OPSTR_CAMERA))

        val packageOps =
            packagesOpsForDefaultDevice.find { it.packageName == context.opPackageName }
        assertThat(packageOps).isNotNull()

        val opEntries = packageOps!!.ops
        assertThat(opEntries.size).isEqualTo(1)

        val opEntry = opEntries[0]
        assertThat(opEntry.opStr).isEqualTo(AppOpsManager.OPSTR_CAMERA)
        assertThat(opEntry.mode).isEqualTo(AppOpsManager.MODE_ALLOWED)

        val attributedOpEntry = opEntry.attributedOpEntries[null]!!
        val lastAccessTime = attributedOpEntry.getLastAccessTime(AppOpsManager.OP_FLAG_SELF)
        assertThat(lastAccessTime).isAtLeast(startTimeMillis)
        assertThat(lastAccessTime).isAtMost(endTimeMillis)
    }

    @RequiresFlagsEnabled(
        Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED,
        Flags.FLAG_RUNTIME_PERMISSION_APPOPS_MAPPING_ENABLED
    )
    @Test
    fun getPackagesForOps_isDeviceAware() {
        permissionManager.grantRuntimePermission(
            context.opPackageName,
            Manifest.permission.CAMERA,
            virtualDevice.persistentDeviceId!!
        )

        // noteOp for an external device
        val attributionSource =
            AttributionSource.Builder(Process.myUid())
                .setPackageName(context.opPackageName)
                .setAttributionTag(context.attributionTag)
                .setDeviceId(virtualDevice.deviceId)
                .build()

        val startTimeMillis = System.currentTimeMillis()
        val mode =
            appOpsManager.noteOpNoThrow(AppOpsManager.OP_CAMERA, attributionSource, "message")
        val endTimeMillis = System.currentTimeMillis()
        assertThat(mode).isEqualTo(AppOpsManager.MODE_ALLOWED)

        // Expect noteOp doesn't create attributedOpEntry for default device
        val packagesOpsForDefaultDevice =
            appOpsManager.getPackagesForOps(arrayOf(AppOpsManager.OPSTR_CAMERA))
        val packageOpsForDefaultDevice =
            packagesOpsForDefaultDevice.find { it.packageName == context.opPackageName }
        val opEntryForDefaultDevice = packageOpsForDefaultDevice!!.ops[0]
        assertThat(opEntryForDefaultDevice.opStr).isEqualTo(AppOpsManager.OPSTR_CAMERA)
        assertThat(opEntryForDefaultDevice.attributedOpEntries).isEmpty()

        // Expect op is noted for the external device
        val packagesOpsForExternalDevice =
            appOpsManager.getPackagesForOps(
                arrayOf(AppOpsManager.OPSTR_CAMERA),
                virtualDevice.persistentDeviceId!!
            )

        val packageOps =
            packagesOpsForExternalDevice.find { it.packageName == context.opPackageName }
        assertThat(packageOps).isNotNull()

        val opEntries = packageOps!!.ops
        assertThat(opEntries.size).isEqualTo(1)

        val opEntry = opEntries[0]
        assertThat(opEntry.opStr).isEqualTo(AppOpsManager.OPSTR_CAMERA)
        assertThat(opEntry.mode).isEqualTo(AppOpsManager.MODE_ALLOWED)

        val attributedOpEntry = opEntry.attributedOpEntries[null]!!
        val lastAccessTime = attributedOpEntry.getLastAccessTime(AppOpsManager.OP_FLAG_SELF)
        assertThat(lastAccessTime).isAtLeast(startTimeMillis)
        assertThat(lastAccessTime).isAtMost(endTimeMillis)
    }

    @RequiresFlagsEnabled(
        Flags.FLAG_DEVICE_AWARE_PERMISSION_APIS_ENABLED,
        Flags.FLAG_RUNTIME_PERMISSION_APPOPS_MAPPING_ENABLED
    )
    @Test
    fun getPermissionGroupUsageForPrivacyIndicator_isDeviceAware() {
        permissionManager.grantRuntimePermission(
            context.opPackageName,
            Manifest.permission.CAMERA,
            virtualDevice.persistentDeviceId!!
        )

        // noteOp for an external device
        val attributionSource =
            AttributionSource.Builder(Process.myUid())
                .setPackageName(context.opPackageName)
                .setAttributionTag(context.attributionTag)
                .setDeviceId(virtualDevice.deviceId)
                .build()

        val startTimeMillis = System.currentTimeMillis()
        val mode =
            appOpsManager.noteOpNoThrow(AppOpsManager.OP_CAMERA, attributionSource, "message")
        val endTimeMillis = System.currentTimeMillis()
        assertThat(AppOpsManager.MODE_ALLOWED).isEqualTo(mode)

        val groupUsage = appOpsManager.getPermissionGroupUsageForPrivacyIndicator(false)
        assertThat(groupUsage.size).isEqualTo(1)

        val permGroupUsage = groupUsage[0]
        assertThat(permGroupUsage.persistentDeviceId).isEqualTo(virtualDevice.persistentDeviceId)
        assertThat(permGroupUsage.packageName).isEqualTo(context.opPackageName)
        assertThat(permGroupUsage.permissionGroupName).isEqualTo(Manifest.permission_group.CAMERA)
        assertThat(permGroupUsage.lastAccessTimeMillis).isAtLeast(startTimeMillis)
        assertThat(permGroupUsage.lastAccessTimeMillis).isAtMost(endTimeMillis)
    }
}
