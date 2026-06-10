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
package com.android.bedstead.testapp

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Environment
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.annotations.AfterClass
import com.android.bedstead.harrier.annotations.BeforeClass
import com.android.bedstead.multiuser.annotations.EnsureHasSecondaryUser
import com.android.bedstead.multiuser.annotations.RequireRunNotOnSecondaryUser
import com.android.bedstead.multiuser.secondaryUser
import com.android.bedstead.nene.TestApis.context
import com.android.bedstead.nene.TestApis.packages
import com.android.bedstead.nene.TestApis.permissions
import com.android.bedstead.nene.TestApis.users
import com.android.bedstead.nene.exceptions.NeneException
import com.android.bedstead.nene.packages.Package
import com.android.bedstead.nene.users.UserReference
import com.android.bedstead.testapps.testApps
import com.google.common.truth.Truth
import java.io.File
import org.junit.Assume
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.testng.Assert

// TODO(388746331): Extract bedstead-packages
@RunWith(BedsteadJUnit4::class)
class PackageTest {

    @Test
    fun packageName_returnsPackageName() {
        Truth.assertThat(packages().find(PACKAGE_NAME).packageName()).isEqualTo(PACKAGE_NAME)
    }

    @Test
    fun exists_nonExistingPackage_returnsFalse() {
        Truth.assertThat(packages().find(NON_EXISTING_PACKAGE_NAME).exists()).isFalse()
    }

    @Test
    fun exists_existingPackage_returnsTrue() {
        Truth.assertThat(packages().find(EXISTING_PACKAGE_NAME).exists()).isTrue()
    }

    @Test
    fun of_returnsPackageWithCorrectPackageName() {
        Truth.assertThat(Package.of(PACKAGE_NAME).packageName()).isEqualTo(PACKAGE_NAME)
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireRunNotOnSecondaryUser
    fun installExisting_alreadyInstalled_installsInUser() {
        sInstrumentedPackage.installExisting(sDeviceState.secondaryUser())

        try {
            Truth.assertThat(sInstrumentedPackage.installedOnUser(sDeviceState.secondaryUser()))
                .isTrue()
        } finally {
            sInstrumentedPackage.uninstall(sDeviceState.secondaryUser())
        }
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireRunNotOnSecondaryUser
    @Throws(Exception::class)
    fun uninstallForAllUsers_isUninstalledForAllUsers() {
        val pkg = packages().install(sTestAppApkFile)
        pkg.installExisting(sDeviceState.secondaryUser())

        pkg.uninstallFromAllUsers()

        Truth.assertThat(pkg.installedOnUsers()).isEmpty()
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireRunNotOnSecondaryUser
    @Throws(Exception::class)
    fun uninstall_packageIsInstalledForDifferentUser_isUninstalledForUser() {
        val pkg = packages().install(sTestAppApkFile)

        try {
            pkg.installExisting(sDeviceState.secondaryUser())

            pkg.uninstall(users().instrumented())

            Truth.assertThat(sTestApp.pkg().installedOnUsers())
                .containsExactly(sDeviceState.secondaryUser())
        } finally {
            pkg.uninstall(users().instrumented())
            pkg.uninstall(sDeviceState.secondaryUser())
        }
    }

    @Test
    @Throws(Exception::class)
    fun uninstall_packageIsUninstalled() {
        val pkg = packages().install(sTestAppApkFile)

        pkg.uninstall(users().instrumented())

        Truth.assertThat(sTestApp.pkg()
            .installedOnUser(users().instrumented())).isFalse()
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireRunNotOnSecondaryUser
    fun uninstall_packageNotInstalledForUser_doesNotThrowException() {
        packages().install(sTestAppApkFile)

        try {
            sTestApp.pkg().uninstall(sDeviceState.secondaryUser())
        } finally {
            sTestApp.pkg().uninstallFromAllUsers()
        }
    }

    @Test
    fun uninstall_packageDoesNotExist_doesNotThrowException() {
        val pkg = packages().find(NON_EXISTING_PACKAGE_NAME)

        pkg.uninstall(sUser)
    }

    @Test
    fun grantPermission_installPermission_throwsException() {
        Assert.assertThrows(NeneException::class.java) {
            packages().find(sContext.packageName)
                .grantPermission(sUser, INSTALL_PERMISSION)
        }
    }

    @Test
    fun grantPermission_nonDeclaredPermission_throwsException() {
        Assert.assertThrows(NeneException::class.java) {
            packages().find(sContext.packageName)
                .grantPermission(sUser, UNDECLARED_RUNTIME_PERMISSION)
        }
    }

    @Test
    @EnsureHasSecondaryUser
    fun grantPermission_permissionIsGranted() {
        sTestApp.install().use {
            sTestApp.pkg().grantPermission(USER_SPECIFIC_PERMISSION)
            Truth.assertThat(sTestApp.pkg().hasPermission(USER_SPECIFIC_PERMISSION)).isTrue()
        }
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireRunNotOnSecondaryUser
    fun grantPermission_permissionIsUserSpecific_permissionIsGrantedOnlyForThatUser() {
        sTestApp.install().use {
            sTestApp.install(sDeviceState.secondaryUser()).use {
                sTestApp.pkg()
                    .grantPermission(sDeviceState.secondaryUser(), USER_SPECIFIC_PERMISSION)
                Truth.assertThat(sTestApp.pkg().hasPermission(USER_SPECIFIC_PERMISSION)).isFalse()
                Truth.assertThat(
                    sTestApp.pkg().hasPermission(sDeviceState.secondaryUser(),
                        USER_SPECIFIC_PERMISSION)
                ).isTrue()
            }
        }
    }

    @Test
    fun grantPermission_packageDoesNotExist_throwsException() {
        Assert.assertThrows(NeneException::class.java) {
            packages().find(NON_EXISTING_PACKAGE_NAME)
                .grantPermission(sUser, DECLARED_RUNTIME_PERMISSION)
        }
    }

    @Test
    fun grantPermission_permissionDoesNotExist_throwsException() {
        Assert.assertThrows(NeneException::class.java) {
            packages().find(sContext.packageName)
                .grantPermission(sUser, NON_EXISTING_PERMISSION)
        }
    }

    @Test
    fun grantPermission_packageIsNotInstalledForUser_throwsException() {
        sTestApp.pkg().uninstall(users().instrumented())

        Assert.assertThrows(NeneException::class.java) {
            sTestApp.pkg().grantPermission(DECLARED_RUNTIME_PERMISSION)
        }
    }

    @Test
    @Ignore("Cannot be tested because all runtime permissions are granted by default")
    fun denyPermission_ownPackage_permissionIsNotGranted_doesNotThrowException() {
        val pkg = packages().find(sContext.packageName)

        pkg.denyPermission(sUser, USER_SPECIFIC_PERMISSION)
    }

    @Test
    fun denyPermission_ownPackage_permissionIsGranted_throwsException() {
        val pkg = packages().find(sContext.packageName)
        pkg.grantPermission(sUser, USER_SPECIFIC_PERMISSION)

        Assert.assertThrows(NeneException::class.java) {
            pkg.denyPermission(sUser, USER_SPECIFIC_PERMISSION)
        }
    }

    @Test
    fun denyPermission_permissionIsNotGranted() {
        sTestApp.install().use {
            sTestApp.pkg().grantPermission(USER_SPECIFIC_PERMISSION)
            sTestApp.pkg().denyPermission(USER_SPECIFIC_PERMISSION)
            Truth.assertThat(sTestApp.pkg().hasPermission(USER_SPECIFIC_PERMISSION)).isFalse()
        }
    }

    @Test
    fun denyPermission_installPermission_throwsException() {
        sTestApp.install().use {
            Assert.assertThrows(NeneException::class.java) {
                sTestApp.pkg().denyPermission(INSTALL_PERMISSION)
            }
        }
    }

    @Test
    fun denyPermission_alreadyDenied_doesNothing() {
        sTestApp.install().use {
            sTestApp.pkg().denyPermission(USER_SPECIFIC_PERMISSION)
            sTestApp.pkg().denyPermission(USER_SPECIFIC_PERMISSION)
            Truth.assertThat(sTestApp.pkg().hasPermission(USER_SPECIFIC_PERMISSION)).isFalse()
        }
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireRunNotOnSecondaryUser
    fun denyPermission_permissionIsUserSpecific_permissionIsDeniedOnlyForThatUser() {
        sTestApp.install().use {
            sTestApp.install(sDeviceState.secondaryUser()).use {
                sTestApp.pkg().grantPermission(USER_SPECIFIC_PERMISSION)
                sTestApp.pkg()
                    .grantPermission(sDeviceState.secondaryUser(), USER_SPECIFIC_PERMISSION)

                sTestApp.pkg()
                    .denyPermission(sDeviceState.secondaryUser(), USER_SPECIFIC_PERMISSION)

                Truth.assertThat(sTestApp.pkg()
                        .hasPermission(sDeviceState.secondaryUser(),
                            USER_SPECIFIC_PERMISSION)).isFalse()
                Truth.assertThat(sTestApp.pkg().hasPermission(USER_SPECIFIC_PERMISSION)).isTrue()
            }
        }
    }

    @Test
    fun installedOnUsers_includesUserWithPackageInstalled() {
        packages().install(sUser, sTestAppApkFile)
        val pkg = packages().find(sTestApp.packageName())

        try {
            Truth.assertThat(pkg.installedOnUsers()).contains(sUser)
        } finally {
            pkg.uninstall(sUser)
        }
    }

    @Test
    fun forceStop_whenRestartableApp_doesNotLoopEndlessly() {
        val previousId = packages().launcher().runningProcess()?.pid()
        packages().launcher().forceStop()
        Truth.assertThat(packages().launcher().runningProcess()?.pid()).isNotEqualTo(previousId)
    }

    @Test
    fun forceStop_whenNoRunningProcess_doesNotThrowException() {
        val notRunningPackage = packages().installedForUser().stream()
            .filter { aPackage: Package -> aPackage.runningProcess() == null }
            .findFirst()
            .get()
        Assume.assumeNotNull(notRunningPackage)

        notRunningPackage.forceStop()
    }

    @Test
    @EnsureHasSecondaryUser
    @RequireRunNotOnSecondaryUser
    @Throws(Exception::class)
    fun installedOnUsers_doesNotIncludeUserWithoutPackageInstalled() {
        val pkg = packages().install(sTestAppApkFile)
        pkg.uninstall(sDeviceState.secondaryUser())

        try {
            Truth.assertThat(pkg.installedOnUsers()).doesNotContain(sDeviceState.secondaryUser())
        } finally {
            pkg.uninstall(users().instrumented())
        }
    }

    @Test
    fun grantedPermission_includesKnownInstallPermission() {
        // TODO(scottjonathan): This relies on the fact that the instrumented app declares
        //  ACCESS_NETWORK_STATE - this should be replaced with TestApp with a useful query
        val pkg = packages().find(sContext.packageName)

        Truth.assertThat(pkg.hasPermission(sUser, ACCESS_NETWORK_STATE_PERMISSION)).isTrue()
    }

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val sDeviceState: DeviceState = DeviceState()

        private val sUser: UserReference = users().instrumented()
        private const val NON_EXISTING_PACKAGE_NAME = "com.package.does.not.exist"
        private const val PACKAGE_NAME = NON_EXISTING_PACKAGE_NAME
        private const val EXISTING_PACKAGE_NAME = "com.android.providers.telephony"
        private const val ACCESS_NETWORK_STATE_PERMISSION =
            "android.permission.ACCESS_NETWORK_STATE"

        private val sContext: Context = context().instrumentedContext()

        private val sInstrumentedPackage: Package = packages().find(sContext.packageName)
        private const val INSTALL_PERMISSION = "android.permission.CHANGE_WIFI_STATE"
        private const val UNDECLARED_RUNTIME_PERMISSION = "android.permission.RECEIVE_SMS"
        private const val DECLARED_RUNTIME_PERMISSION = "android.permission.INTERACT_ACROSS_USERS"
        private const val NON_EXISTING_PERMISSION = "aPermissionThatDoesntExist"
        private const val USER_SPECIFIC_PERMISSION = "android.permission.READ_CONTACTS"
        private val sTestApp: TestApp = sDeviceState.testApps().query()
            .wherePermissions().contains(
                USER_SPECIFIC_PERMISSION,
                DECLARED_RUNTIME_PERMISSION,
                INSTALL_PERMISSION
            ).get()
        private val sTestAppApkFile = File(
            Environment.getExternalStorageDirectory(),
            "bedstead-testapp-test1.apk")

        @JvmStatic
        @BeforeClass
        @Throws(Exception::class)
        fun setupClass() {
            permissions()
                .withPermissionOnVersionAtLeast(
                    Build.VERSION_CODES.R,
                    Manifest.permission.MANAGE_EXTERNAL_STORAGE
                ).use {
                    sTestApp.writeApkFile(sTestAppApkFile)
                }
        }

        @JvmStatic
        @AfterClass
        @Throws(Exception::class)
        fun teardownClass() {
            permissions()
                .withPermissionOnVersionAtLeast(
                    Build.VERSION_CODES.R,
                    Manifest.permission.MANAGE_EXTERNAL_STORAGE
                ).use {
                    sTestAppApkFile.delete()
                }
        }
    }
}
