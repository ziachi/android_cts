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

package android.security.cts

import android.content.ComponentName
import android.content.Context.BIND_AUTO_CREATE
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager.NameNotFoundException
import android.content.pm.PermissionInfo
import android.os.IBinder
import android.platform.test.annotations.AsbSecurityTest
import android.security.cts.dynamicpermissiontestattackerapp.IDynamicPermissionService
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.modules.utils.build.SdkLevel
import com.google.common.truth.Truth.assertWithMessage
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit.MILLISECONDS
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies permission definition changes for dynamic permissions.
 */
@RunWith(AndroidJUnit4::class)
class DynamicPermissionsTest : BasePermissionUiTest() {
    private lateinit var serviceConnection: ServiceConnection
    private lateinit var dynamicPermissionService: IDynamicPermissionService

    @Before
    override fun setUp() {
        super.setUp()
        installPackage(TEST_ATTACKER_APP_APK_PATH)
        bindService()
    }

    @After
    override fun tearDown() {
        super.tearDown()
        if (this::serviceConnection.isInitialized) {
            mContext.unbindService(serviceConnection)
        }
        uninstallPackage(TEST_ATTACKER_APP_PKG)
    }

    @Test
    @AsbSecurityTest(cveBugId = [225880325])
    fun testRemovePermission_dynamicPermission_permissionRemoved() {
        val permissionInfo = PermissionInfo().apply {
            name = DYNAMIC_PERMISSION
            nonLocalizedLabel = DYNAMIC_PERMISSION
            protectionLevel = PermissionInfo.PROTECTION_NORMAL
        }
        packageManager.addPermission(permissionInfo)
        assertWithMessage("$DYNAMIC_PERMISSION should exist before running the test")
            .that(packageManager.getPermissionInfo(DYNAMIC_PERMISSION, 0).name)
            .isEqualTo(DYNAMIC_PERMISSION)

        packageManager.removePermission(DYNAMIC_PERMISSION)

        assertThrows(
            "The dynamic permission $DYNAMIC_PERMISSION should be correctly removed",
            NameNotFoundException::class.java
        ) {
            packageManager.getPermissionInfo(DYNAMIC_PERMISSION, 0)
        }
    }

    @Test
    @AsbSecurityTest(cveBugId = [225880325])
    fun testRemovePermission_nonDynamicPermission_permissionUnchanged() {
        assertWithMessage("$NON_DYNAMIC_PERMISSION should exist before running the test")
            .that(packageManager.getPermissionInfo(NON_DYNAMIC_PERMISSION, 0).name)
            .isEqualTo(NON_DYNAMIC_PERMISSION)

        try {
            dynamicPermissionService.removePermission(NON_DYNAMIC_PERMISSION)
        } catch (e: SecurityException) {
            // using a try-catch block instead of @Test(expected = SecurityException::class) or
            // assertThrows() because the SecurityException is only thrown on V and after.
            // In pre-V, we just log.wtf() the error. The below assertion of the existence of
            // NON_DYNAMIC_PERMISSION is sufficient to validate the functionality this method tests.
        }

        assertWithMessage("The non-dynamic perm $NON_DYNAMIC_PERMISSION shouldn't be removed")
            .that(packageManager.getPermissionInfo(NON_DYNAMIC_PERMISSION, 0).name)
            .isEqualTo(NON_DYNAMIC_PERMISSION)
    }

    @Test
    @AsbSecurityTest(cveBugId = [340480881])
    fun testGrantPhonePermission_sharesGroupWithGrantedCustomDynamicPermission_notAutoGranted() {
        if (SdkLevel.isAtLeastV()) {
            assertThrows(
                "Not allowed to change custom permission type from TYPE_DYNAMIC to" +
                    " TYPE_MANIFEST for V+",
                SecurityException::class.java
            ) {
                setupAttackerAppUpdateAndAddDynamicPermission()
            }
            return
        } else {
            setupAttackerAppUpdateAndAddDynamicPermission()
        }

        requestAppPermissionsAndAssertResult(
            ATTACKER_APP_DYNAMIC_PERMISSION to true,
        ) {
            clickPermissionRequestAllowButton()
        }
        requestAppPermissionsAndAssertResult(
            PHONE_PERMISSION to true,
        ) {
            clickPermissionRequestAllowButton()
        }
    }

    @Test
    @AsbSecurityTest(cveBugId = [340480881])
    fun testGrantCustomDynamicPermission_sharesGroupWithGrantedPhonePermission_autoGranted() {
        if (SdkLevel.isAtLeastV()) {
            assertThrows(
                "Not allowed to change custom permission type from TYPE_DYNAMIC to" +
                    " TYPE_MANIFEST for V+",
                SecurityException::class.java
            ) {
                setupAttackerAppUpdateAndAddDynamicPermission()
            }
            return
        } else {
            setupAttackerAppUpdateAndAddDynamicPermission()
        }

        requestAppPermissionsAndAssertResult(
            PHONE_PERMISSION to true,
        ) {
            clickPermissionRequestAllowButton()
        }
        requestAppPermissionsAndAssertResult(
            ATTACKER_APP_DYNAMIC_PERMISSION to true,
        ) {
            // The custom permission should be granted automatically
        }
    }

    private fun setupAttackerAppUpdateAndAddDynamicPermission() {
        dynamicPermissionService.addPermission(
            ATTACKER_APP_DYNAMIC_PERMISSION,
            TEST_ATTACKER_APP_PKG,
            PermissionInfo.PROTECTION_NORMAL,
            null
        )
        if (this::serviceConnection.isInitialized) {
            mContext.unbindService(serviceConnection)
        }
        installPackage(UPDATED_TEST_ATTACKER_APP_APK_PATH)
        bindService()
        dynamicPermissionService.addPermission(
            ATTACKER_APP_DYNAMIC_PERMISSION,
            TEST_ATTACKER_APP_PKG,
            PermissionInfo.PROTECTION_DANGEROUS,
            PHONE_PERMISSION_GROUP
        )
    }

    private fun bindService() {
        val dynamicPermissionServiceFuture = CompletableFuture<IDynamicPermissionService>()
        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                dynamicPermissionServiceFuture.complete(
                    IDynamicPermissionService.Stub.asInterface(service)
                )
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                dynamicPermissionServiceFuture.completeExceptionally(
                    InterruptedException("DynamicPermissionService disconnected")
                )
            }
        }

        val intent = Intent().apply {
            component = ComponentName(
                TEST_ATTACKER_APP_PKG, DYNAMIC_PERMISSION_SERVICE_COMPONENT_NAME
            )
        }
        mContext.bindService(intent, serviceConnection, BIND_AUTO_CREATE)
        dynamicPermissionService = dynamicPermissionServiceFuture.get(
            SERVICE_CONNECTION_TIMEOUT, MILLISECONDS
        )
    }

    companion object {
        private const val DYNAMIC_PERMISSION_TREE_ROOT = "com.android.cts"
        private const val DYNAMIC_PERMISSION = "$DYNAMIC_PERMISSION_TREE_ROOT.DYNAMIC_PERMISSION"
        private const val NON_DYNAMIC_PERMISSION = android.Manifest.permission.READ_VOICEMAIL
        private const val PHONE_PERMISSION = "android.permission.CALL_PHONE"
        private const val TEST_ATTACKER_APP_APK_PATH =
            "/data/local/tmp/cts/security/DynamicPermissionTestAttackerApp.apk"
        private const val UPDATED_TEST_ATTACKER_APP_APK_PATH =
            "/data/local/tmp/cts/security/UpdatedDynamicPermissionTestAttackerApp.apk"
        private const val TEST_ATTACKER_APP_PKG = "android.security.cts.usepermission"
        private const val ATTACKER_APP_DYNAMIC_PERMISSION =
            "$TEST_ATTACKER_APP_PKG.DYNAMIC_PERMISSION"
        private const val DYNAMIC_PERMISSION_SERVICE_COMPONENT_NAME =
            "$TEST_ATTACKER_APP_PKG.DynamicPermissionService"
        private const val PHONE_PERMISSION_GROUP = "android.permission-group.PHONE"
        private const val SERVICE_CONNECTION_TIMEOUT = 5000L
    }
}
