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

import android.content.Intent
import android.os.Build
import com.android.bedstead.enterprise.annotations.EnsureHasWorkProfile
import com.android.bedstead.enterprise.workProfile
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.annotations.RequireRunOnInitialUser
import com.android.bedstead.harrier.annotations.RequireSdkVersion
import com.android.bedstead.multiuser.additionalUser
import com.android.bedstead.multiuser.annotations.EnsureHasAdditionalUser
import com.android.bedstead.nene.TestApis.context
import com.android.bedstead.nene.TestApis.packages
import com.android.bedstead.nene.TestApis.permissions
import com.android.bedstead.nene.TestApis.users
import com.android.bedstead.nene.exceptions.NeneException
import com.android.bedstead.nene.packages.Package
import com.android.bedstead.nene.packages.Packages
import com.android.bedstead.nene.users.UserReference
import com.android.bedstead.nene.utils.Poll
import com.android.bedstead.nene.utils.Versions
import com.android.bedstead.permissions.CommonPermissions
import com.android.bedstead.testapps.testApps
import com.android.compatibility.common.util.FileUtils
import com.android.queryable.queries.ActivityQuery
import com.google.common.truth.Truth
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import org.junit.Assume
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.testng.Assert

// TODO(388746331): Extract bedstead-packages
@RunWith(BedsteadJUnit4::class)
class PackagesTest {
    private val mUser: UserReference = users().instrumented()
    private val mExistingPackage: Package = packages().find("com.android.providers.telephony")
    private val mTestAppReference: Package = packages().find(
        TEST_APP_PACKAGE_NAME
    )
    private val mDifferentTestAppReference: Package = packages().find(
        NON_EXISTING_PACKAGE
    )
    private val mNonExistingUser: UserReference = users().find(99999)
    private val mApkFile = File("")

    @Test
    fun construct_constructs() {
        Packages() // Doesn't throw any exceptions
    }

    @Test
    fun features_noUserSpecified_containsKnownFeature() {
        Truth.assertThat(packages().features()).contains(INPUT_METHODS_FEATURE)
    }

    @Test
    fun find_nullPackageName_throwsException() {
        Assert.assertThrows(NullPointerException::class.java) {
            packages().find(null)
        }
    }

    @Test
    fun find_existingPackage_returnsPackageReference() {
        Truth.assertThat(packages().find(mExistingPackage.packageName())).isNotNull()
    }

    @Test
    fun find_nonExistingPackage_returnsPackageReference() {
        Truth.assertThat(packages().find(NON_EXISTING_PACKAGE)).isNotNull()
    }

    @Test
    fun installedForUser_nullUserReference_throwsException() {
        Assert.assertThrows(NullPointerException::class.java) {
            packages().installedForUser( /* user= */null)
        }
    }

    @Test
    fun installedForUser_containsPackageInstalledForUser() {
        val pkg = packages().install(mUser, TEST_APP_APK_FILE)

        try {
            Truth.assertThat(packages().installedForUser(mUser)).contains(pkg)
        } finally {
            pkg!!.uninstall(mUser)
        }
    }

    @Test
    @EnsureHasAdditionalUser
    fun installedForUser_doesNotContainPackageNotInstalledForUser() {
        var pkg: Package? = null
        try {
            pkg = packages().install(mUser, TEST_APP_APK_FILE)
            pkg!!.uninstall(sDeviceState.additionalUser())

            Truth.assertThat(packages().installedForUser(sDeviceState.additionalUser()))
                .doesNotContain(pkg)
        } finally {
            pkg?.uninstall(mUser)
        }
    }

    @Test
    fun install_nonExistingPackage_throwsException() {
        Assert.assertThrows(NeneException::class.java) {
            packages().install(mUser, NON_EXISTING_APK_FILE)
        }
    }

    @Test
    fun install_nullUser_throwsException() {
        Assert.assertThrows(NullPointerException::class.java) {
            packages().install( /* user= */null, mApkFile)
        }
    }

    @Test
    fun install_byteArray_nullUser_throwsException() {
        Assert.assertThrows(NullPointerException::class.java) {
            packages().install( /* user= */null, TEST_APP_BYTES)
        }
    }

    @Test
    fun install_nullApkFile_throwsException() {
        Assert.assertThrows(NullPointerException::class.java) {
            packages().install(mUser,  /* apkFile= */null as File?)
        }
    }

    @Test
    fun install_nullByteArray_throwsException() {
        Assert.assertThrows(NullPointerException::class.java) {
            packages().install(mUser,  /* apkFile= */null as ByteArray?)
        }
    }

    @Test
    fun install_instrumentedUser_isInstalled() {
        val pkg =
            packages().install(users().instrumented(), TEST_APP_APK_FILE)

        try {
            Truth.assertThat(pkg!!.installedOnUser()).isTrue()
        } finally {
            pkg!!.uninstall(users().instrumented())
        }
    }

    @Test
    fun install_byteArray_instrumentedUser_isInstalled() {
        val pkg =
            packages().install(users().instrumented(), TEST_APP_BYTES)

        try {
            Truth.assertThat(pkg!!.installedOnUser()).isTrue()
        } finally {
            pkg!!.uninstall(users().instrumented())
        }
    }

    @Test
    @RequireRunOnInitialUser
    @EnsureHasWorkProfile
    fun install_inWorkProfile_isInstalled() {
        packages().install(sDeviceState.workProfile(), TEST_APP_APK_FILE)
        val pkg = packages().find(TEST_APP_PACKAGE_NAME)

        try {
            Truth.assertThat(pkg.installedOnUser(sDeviceState.workProfile())).isTrue()
        } finally {
            pkg.uninstall(sDeviceState.workProfile())
        }
    }

    @Test
    @EnsureHasAdditionalUser
    fun install_differentUser_isInstalled() {
        packages().install(sDeviceState.additionalUser(), TEST_APP_APK_FILE)
        val pkg = packages().find(TEST_APP_PACKAGE_NAME)

        try {
            Truth.assertThat(pkg.installedOnUser(sDeviceState.additionalUser())).isTrue()
        } finally {
            pkg.uninstall(sDeviceState.additionalUser())
        }
    }

    @Test
    @EnsureHasAdditionalUser
    fun install_byteArray_differentUser_isInstalled() {
        var pkg: Package? = null
        try {
            pkg = packages().install(sDeviceState.additionalUser(), TEST_APP_BYTES)

            Truth.assertThat(pkg!!.installedOnUser(sDeviceState.additionalUser())).isTrue()
        } finally {
            pkg?.uninstallFromAllUsers()
        }
    }

    @Test
    @EnsureHasAdditionalUser
    fun install_userNotStarted_throwsException() {
        try {
            sDeviceState.additionalUser().stop()

            Assert.assertThrows(NeneException::class.java) {
                packages().install(
                    sDeviceState.additionalUser(),
                    TEST_APP_APK_FILE
                )
            }
        } finally {
            sDeviceState.additionalUser().start()
        }
    }

    @Test
    @EnsureHasAdditionalUser
    fun install_byteArray_userNotStarted_throwsException() {
        try {
            sDeviceState.additionalUser().stop()

            Assert.assertThrows(NeneException::class.java) {
                packages()
                    .install(
                        sDeviceState.additionalUser(),
                        TEST_APP_BYTES
                    )
            }
        } finally {
            sDeviceState.additionalUser().start()
        }
    }

    @Test
    fun install_userDoesNotExist_throwsException() {
        Assert.assertThrows(NeneException::class.java) {
            packages().install(mNonExistingUser, TEST_APP_APK_FILE)
        }
    }

    @Test
    fun install_byteArray_userDoesNotExist_throwsException() {
        Assert.assertThrows(NeneException::class.java) {
            packages().install(mNonExistingUser, TEST_APP_BYTES)
        }
    }

    @Test
    fun install_alreadyInstalledForUser_installs() {
        var pkg = packages().install(mUser, TEST_APP_APK_FILE)

        try {
            pkg = packages().install(mUser, TEST_APP_APK_FILE)
            Truth.assertThat(pkg!!.installedOnUser(mUser)).isTrue()
        } finally {
            pkg!!.uninstall(mUser)
        }
    }

    @Test
    fun install_byteArray_alreadyInstalledForUser_installs() {
        var pkg = packages().install(mUser, TEST_APP_BYTES)

        try {
            pkg = packages().install(mUser, TEST_APP_BYTES)
            Truth.assertThat(pkg!!.installedOnUser(mUser)).isTrue()
        } finally {
            pkg!!.uninstall(mUser)
        }
    }

    @Test
    @EnsureHasAdditionalUser
    fun install_alreadyInstalledOnOtherUser_installs() {
        var pkg: Package? = null

        try {
            pkg = packages().install(sDeviceState.additionalUser(), TEST_APP_APK_FILE)

            packages().install(mUser, TEST_APP_APK_FILE)

            Truth.assertThat(pkg!!.installedOnUser(mUser)).isTrue()
        } finally {
            pkg?.uninstallFromAllUsers()
        }
    }

    @Test
    @EnsureHasAdditionalUser
    fun install_byteArray_alreadyInstalledOnOtherUser_installs() {
        var pkg: Package? = null

        try {
            pkg = packages().install(sDeviceState.additionalUser(), TEST_APP_BYTES)

            packages().install(mUser, TEST_APP_BYTES)

            Truth.assertThat(pkg!!.installedOnUser(mUser)).isTrue()
        } finally {
            pkg?.uninstallFromAllUsers()
        }
    }

    @Test
    @RequireSdkVersion(
        min = Build.VERSION_CODES.S,
        reason = "keepUninstalledPackages is only supported on S+"
    )
    @Ignore("TODO: .exists() doesn't return true when the package is kept - restore this functionality")
    fun keepUninstalledPackages_packageIsUninstalled_packageStillExists() {
        try {
            sTestApp.install().use { testAppInstance ->
                packages().keepUninstalledPackages()
                    .add(sTestApp.pkg())
                    .commit()
                testAppInstance.uninstall()
                Truth.assertThat(sTestApp.pkg().exists()).isTrue()
            }
        } finally {
            packages().keepUninstalledPackages().clear()
        }
    }

    @Test
    @Ignore(
        ("While using adb calls this is not reliable, enable once we use framework calls for "
                + "uninstall")
    )
    fun keepUninstalledPackages_packageRemovedFromList_packageIsUninstalled_packageDoesNotExist() {
        Assume.assumeTrue(
            "keepUninstalledPackages is only supported on S+",
            Versions.meetsMinimumSdkVersionRequirement(Build.VERSION_CODES.S)
        )

        packages().install(mUser, TEST_APP_APK_FILE)
        packages().keepUninstalledPackages()
            .add(mTestAppReference)
            .commit()
        packages().keepUninstalledPackages()
            .add(mDifferentTestAppReference)
            .commit()

        try {
            mTestAppReference.uninstall(mUser)

            Truth.assertThat(mTestAppReference.exists()).isFalse()
        } finally {
            packages().keepUninstalledPackages().clear()
        }
    }

    @Test
    @Ignore(
        ("While using adb calls this is not reliable, enable once we use framework calls for "
                + "uninstall")
    )
    fun keepUninstalledPackages_cleared_packageIsUninstalled_packageDoesNotExist() {
        Assume.assumeTrue(
            "keepUninstalledPackages is only supported on S+",
            Versions.meetsMinimumSdkVersionRequirement(Build.VERSION_CODES.S)
        )

        packages().install(mUser, TEST_APP_APK_FILE)

        packages().keepUninstalledPackages()
            .add(mTestAppReference)
            .commit()
        packages().keepUninstalledPackages().clear()

        try {
            mTestAppReference.uninstall(mUser)

            Truth.assertThat(mTestAppReference.exists()).isFalse()
        } finally {
            packages().keepUninstalledPackages().clear()
        }
    }

    @Test
    @Ignore(
        ("While using adb calls this is not reliable, enable once we use framework calls for "
                + "uninstall")
    )
    fun keepUninstalledPackages_packageRemovedFromList_packageAlreadyUninstalled_packageDoesNotExist() {
        Assume.assumeTrue(
            "keepUninstalledPackages is only supported on S+",
            Versions.meetsMinimumSdkVersionRequirement(Build.VERSION_CODES.S)
        )

        packages().install(mUser, TEST_APP_APK_FILE)
        packages().keepUninstalledPackages().add(mTestAppReference).commit()
        mTestAppReference.uninstall(mUser)
        packages().keepUninstalledPackages().add(mDifferentTestAppReference).commit()

        try {
            Truth.assertThat(mTestAppReference.exists()).isFalse()
        } finally {
            packages().keepUninstalledPackages().clear()
        }
    }

    @Test
    @Ignore // TODO(270963894): Restore
    fun kill_killsProcess() {
        sTestApp.install().use { testApp ->
            // Start an activity so the process exists
            testApp.activities().query()
                .whereActivity().exported().isTrue()
                .get().start()
            Poll.forValue("process") {
                sTestApp.pkg().runningProcess()
            }
                .toNotBeNull()
                .await()
            val pidBeforeKill = sTestApp.pkg().runningProcess()!!
                .pid()

            sTestApp.pkg().runningProcess()!!.kill()

            val processReference = sTestApp.pkg().runningProcess()
            val pidAfterKill = processReference?.pid() ?: -1
            Truth.assertThat(pidAfterKill).isNotEqualTo(pidBeforeKill)
        }
    }

    @Test
    @RequireRunOnInitialUser
    @EnsureHasWorkProfile
    @Ignore("TODO(270963894): Restore")
    fun kill_doesNotKillProcessInOtherUser() {
        sTestApp.install().use { personalTestApp ->
            sTestApp.install(sDeviceState.workProfile()).use {
                // Start an activity so the process exists
                val activity =
                    personalTestApp.activities().query().whereActivity().exported().isTrue().get()
                val intent = Intent()
                intent.setComponent(activity.component().componentName())
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context().instrumentedContext().startActivity(intent)
                permissions().withPermission(CommonPermissions.INTERACT_ACROSS_USERS_FULL)
                    .use {
                        context().instrumentedContext().startActivityAsUser(
                            intent, sDeviceState.workProfile().userHandle()
                        )
                    }
                Poll.forValue("process") {
                    sTestApp.pkg()
                        .runningProcess(sDeviceState.workProfile())
                }
                    .toNotBeNull()
                    .await()

                sTestApp.pkg().runningProcess()!!.kill()
                Truth.assertThat(sTestApp.pkg().runningProcess(sDeviceState.workProfile()))
                    .isNotNull()
            }
        }
    }

    @Test
    fun dump_dumpsState() {
        Truth.assertThat(packages().dump()).isNotEmpty()
    }

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val sDeviceState: DeviceState = DeviceState()
        private const val INPUT_METHODS_FEATURE = "android.software.input_methods"
        private const val NON_EXISTING_PACKAGE = "com.package.does.not.exist"

        // Controlled by AndroidTest.xml
        private const val TEST_APP_PACKAGE_NAME = "com.android.bedstead.testapp.testapps.TestApp1"
        private val TEST_APP_APK_FILE = File("/data/local/tmp/bedstead-testapp-test1.apk")
        private val NON_EXISTING_APK_FILE = File("/data/local/tmp/ThisApkDoesNotExist.apk")
        private val TEST_APP_BYTES = loadBytes(TEST_APP_APK_FILE)
        private val sTestApp: TestApp = sDeviceState.testApps().query()
            .whereActivities().contains(
                ActivityQuery.activity().where().exported().isTrue()
            ).get()

        private fun loadBytes(file: File): ByteArray {
            try {
                FileInputStream(file).use { fis ->
                    return FileUtils.readInputStreamFully(fis)
                }
            } catch (e: IOException) {
                throw AssertionError("Could not read file bytes", e)
            }
        }
    }
}
