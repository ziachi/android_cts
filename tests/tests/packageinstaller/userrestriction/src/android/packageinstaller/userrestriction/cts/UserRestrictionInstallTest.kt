/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.packageinstaller.userrestriction.cts

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller.EXTRA_STATUS
import android.content.pm.PackageInstaller.STATUS_FAILURE_INVALID
import android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION
import android.content.pm.PackageInstaller.Session
import android.content.pm.PackageInstaller.SessionParams
import android.platform.test.annotations.AppModeFull
import android.util.Log
import androidx.core.content.FileProvider
import androidx.test.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import com.android.bedstead.enterprise.annotations.EnsureDoesNotHaveUserRestriction
import com.android.bedstead.enterprise.annotations.EnsureHasUserRestriction
import com.android.bedstead.enterprise.annotations.EnsureHasWorkProfile
import com.android.bedstead.enterprise.annotations.RequireRunOnWorkProfile
import com.android.bedstead.enterprise.workProfile
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.UserType
import com.android.bedstead.harrier.annotations.enterprise.DevicePolicyRelevant
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.exceptions.AdbException
import com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_DEBUGGING_FEATURES
import com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_INSTALL_APPS
import com.android.bedstead.nene.users.UserReference
import com.android.bedstead.nene.utils.BlockingBroadcastReceiver
import com.android.bedstead.nene.utils.ShellCommand
import com.android.bedstead.permissions.CommonPermissions.INTERACT_ACROSS_USERS_FULL
import com.android.bedstead.permissions.annotations.EnsureHasPermission
import com.android.compatibility.common.util.ApiTest
import com.android.compatibility.common.util.FutureResultActivity
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.test.assertFailsWith
import org.junit.Assert
import org.junit.Assert.fail
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
@AppModeFull(reason = "DEVICE_POLICY_SERVICE is null in instant mode")
class UserRestrictionInstallTest {

    companion object {
        const val INSTALL_BUTTON_ID = "button1"
        const val CANCEL_BUTTON_ID = "button2"

        const val PACKAGE_INSTALLER_PACKAGE_NAME = "com.android.packageinstaller"
        const val SYSTEM_PACKAGE_NAME = "android"

        const val TEST_APK_NAME = "CtsEmptyTestApp.apk"
        const val TEST_APK_PACKAGE_NAME = "android.packageinstaller.emptytestapp.cts"
        const val TEST_APK_LOCATION = "/data/local/tmp/cts/packageinstaller"

        const val CONTENT_AUTHORITY = "android.packageinstaller.userrestriction.cts.fileprovider"
        const val APP_INSTALL_ACTION = "android.packageinstaller.userrestriction.cts.action"

        const val TIMEOUT = 60000L

        @JvmField
        @ClassRule
        @Rule
        val sDeviceState = DeviceState()
    }

    val TAG = UserRestrictionInstallTest::class.java.simpleName

    @get:Rule
    val installDialogStarter = ActivityTestRule(FutureResultActivity::class.java)

    val context: Context = InstrumentationRegistry.getTargetContext()
    val apkFile = File(context.filesDir, TEST_APK_NAME)
    val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Before
    fun uninstallTestApp() {
        val cmd = ShellCommand.builder("pm uninstall")
        cmd.addOperand(TEST_APK_PACKAGE_NAME)
        try {
            cmd.execute()
        } catch (_: AdbException) {
            fail("Could not uninstall $TEST_APK_PACKAGE_NAME")
        }
    }

    @Before
    fun copyTestApk() {
        File(TEST_APK_LOCATION, TEST_APK_NAME).copyTo(target = apkFile, overwrite = true)
    }

    @Test
    @ApiTest(apis = ["android.os.UserManager#DISALLOW_DEBUGGING_FEATURES"])
    @DevicePolicyRelevant
    @EnsureHasWorkProfile
    @EnsureHasUserRestriction(value = DISALLOW_DEBUGGING_FEATURES, onUser = UserType.WORK_PROFILE)
    @EnsureDoesNotHaveUserRestriction(value = DISALLOW_INSTALL_APPS, onUser = UserType.WORK_PROFILE)
    fun disallowDebuggingFeatures_adbInstallOnAllUsers_installedOnUnrestrictedUser() {
        val initialUser = sDeviceState.initialUser()
        val workProfile = sDeviceState.workProfile()

        installPackageViaAdb(apkPath = "$TEST_APK_LOCATION/$TEST_APK_NAME")

        assertWithMessage("Test app should be installed in initial user")
            .that(TestApis.packages().find(TEST_APK_PACKAGE_NAME).installedOnUser(initialUser))
            .isTrue()

        assertWithMessage(
            "Test app shouldn't be installed in a work profile with " +
                "$DISALLOW_DEBUGGING_FEATURES set"
        )
            .that(TestApis.packages().find(TEST_APK_PACKAGE_NAME).installedOnUser(workProfile))
            .isFalse()
    }

    @Test
    @ApiTest(apis = ["android.os.UserManager#DISALLOW_DEBUGGING_FEATURES"])
    @DevicePolicyRelevant
    @EnsureHasWorkProfile
    @EnsureHasUserRestriction(value = DISALLOW_DEBUGGING_FEATURES, onUser = UserType.WORK_PROFILE)
    @EnsureDoesNotHaveUserRestriction(value = DISALLOW_INSTALL_APPS, onUser = UserType.WORK_PROFILE)
    fun disallowDebuggingFeatures_adbInstallOnWorkProfile_fails() {
        val workProfile = sDeviceState.workProfile()

        installPackageViaAdb(apkPath = "$TEST_APK_LOCATION/$TEST_APK_NAME", user = workProfile)

        assertWithMessage(
            "Test app shouldn't be installed in a work profile with " +
                "$DISALLOW_DEBUGGING_FEATURES set"
        )
            .that(TestApis.packages().find(TEST_APK_PACKAGE_NAME).installedOnUser(workProfile))
            .isFalse()
    }

    @Test
    @ApiTest(apis = ["android.os.UserManager#DISALLOW_DEBUGGING_FEATURES"])
    @DevicePolicyRelevant
    @EnsureHasWorkProfile
    @EnsureHasUserRestriction(value = DISALLOW_DEBUGGING_FEATURES, onUser = UserType.WORK_PROFILE)
    @EnsureDoesNotHaveUserRestriction(value = DISALLOW_INSTALL_APPS, onUser = UserType.WORK_PROFILE)
    @EnsureHasPermission(INTERACT_ACROSS_USERS_FULL)
    fun disallowDebuggingFeatures_sessionInstallOnWorkProfile_getInstallRequest() {
        val workProfile = sDeviceState.workProfile()
        val (_, session) = createSessionForUser(workProfile)
        try {
            writeSessionAsUser(workProfile, session)
            val result: Intent? = commitSessionAsUser(workProfile, session)
            assertThat(result).isNotNull()
            assertThat(result!!.getIntExtra(EXTRA_STATUS, STATUS_FAILURE_INVALID))
                .isEqualTo(STATUS_PENDING_USER_ACTION)
        } finally {
            session.abandon()
        }
    }

    @Test
    @ApiTest(apis = ["android.os.UserManager#DISALLOW_DEBUGGING_FEATURES"])
    @DevicePolicyRelevant
    @EnsureHasUserRestriction(value = DISALLOW_DEBUGGING_FEATURES, onUser = UserType.WORK_PROFILE)
    @EnsureDoesNotHaveUserRestriction(value = DISALLOW_INSTALL_APPS, onUser = UserType.WORK_PROFILE)
    @RequireRunOnWorkProfile
    fun disallowDebuggingFeatures_intentInstallOnWorkProfile_installationSucceeds() {
        val appInstallIntent = getAppInstallationIntent(apkFile)

        val installation = startInstallationViaIntent(appInstallIntent)
        clickInstallerUIButton(INSTALL_BUTTON_ID)

        // Install should have succeeded
        assertThat(installation.get(TIMEOUT, TimeUnit.MILLISECONDS)).isEqualTo(Activity.RESULT_OK)
    }

    @Test
    @ApiTest(apis = ["android.os.UserManager#DISALLOW_INSTALL_APPS"])
    @DevicePolicyRelevant
    @EnsureHasWorkProfile
    @EnsureHasUserRestriction(value = DISALLOW_INSTALL_APPS, onUser = UserType.WORK_PROFILE)
    @EnsureDoesNotHaveUserRestriction(
        value = DISALLOW_DEBUGGING_FEATURES,
        onUser = UserType.WORK_PROFILE
    )
    fun disallowInstallApps_adbInstallOnAllUsers_installedOnUnrestrictedUser() {
        val initialUser = sDeviceState.initialUser()
        val workProfile = sDeviceState.workProfile()

        installPackageViaAdb(apkPath = "$TEST_APK_LOCATION/$TEST_APK_NAME")

        var targetPackage = TestApis.packages().installedForUser(initialUser).filter {
            it.packageName().equals(TEST_APK_PACKAGE_NAME)
        }
        assertWithMessage("Test app should be installed in initial user")
            .that(targetPackage.size)
            .isNotEqualTo(0)

        targetPackage = TestApis.packages().installedForUser(workProfile).filter {
            it.packageName().equals(TEST_APK_PACKAGE_NAME)
        }
        assertWithMessage(
            "Test app shouldn't be installed in a work profile with $DISALLOW_INSTALL_APPS set"
        )
            .that(targetPackage.size)
            .isEqualTo(0)
    }

    @Test
    @ApiTest(apis = ["android.os.UserManager#DISALLOW_INSTALL_APPS"])
    @DevicePolicyRelevant
    @EnsureHasWorkProfile
    @EnsureHasUserRestriction(value = DISALLOW_INSTALL_APPS, onUser = UserType.WORK_PROFILE)
    @EnsureDoesNotHaveUserRestriction(
        value = DISALLOW_DEBUGGING_FEATURES,
        onUser = UserType.WORK_PROFILE
    )
    fun disallowInstallApps_adbInstallOnWorkProfile_fails() {
        val workProfile = sDeviceState.workProfile()
        assertThat(
            TestApis.devicePolicy().userRestrictions(workProfile).isSet(DISALLOW_INSTALL_APPS)
        ).isTrue()

        installPackageViaAdb(apkPath = "$TEST_APK_LOCATION/$TEST_APK_NAME", user = workProfile)

        val targetPackage = TestApis.packages().installedForUser(workProfile).filter {
            it.packageName().equals(TEST_APK_PACKAGE_NAME)
        }
        assertWithMessage(
            "Test app shouldn't be installed in a work profile with " +
                "$DISALLOW_DEBUGGING_FEATURES set"
        )
            .that(targetPackage.size)
            .isEqualTo(0)
    }

    @Test
    @ApiTest(apis = ["android.os.UserManager#DISALLOW_INSTALL_APPS"])
    @DevicePolicyRelevant
    @EnsureHasWorkProfile
    @EnsureHasUserRestriction(value = DISALLOW_INSTALL_APPS, onUser = UserType.WORK_PROFILE)
    @EnsureDoesNotHaveUserRestriction(
        value = DISALLOW_DEBUGGING_FEATURES,
        onUser = UserType.WORK_PROFILE
    )
    @EnsureHasPermission(INTERACT_ACROSS_USERS_FULL)
    fun disallowInstallApps_sessionInstallOnWorkProfile_throwsException() {
        val workProfile = sDeviceState.workProfile()
        assertFailsWith(SecurityException::class) {
            createSessionForUser(workProfile)
        }
    }

    @Test
    @ApiTest(apis = ["android.os.UserManager#DISALLOW_INSTALL_APPS"])
    @DevicePolicyRelevant
    @EnsureHasUserRestriction(value = DISALLOW_INSTALL_APPS, onUser = UserType.WORK_PROFILE)
    @EnsureDoesNotHaveUserRestriction(
        value = DISALLOW_DEBUGGING_FEATURES,
        onUser = UserType.WORK_PROFILE
    )
    @RequireRunOnWorkProfile
    fun disallowInstallApps_intentInstallOnWorkProfile_installationFails() {
        val appInstallIntent = getAppInstallationIntent(apkFile)

        val installation = startInstallationViaIntent(appInstallIntent)
        // Dismiss the device policy dialog
        val closeBtn: UiObject = TestApis.ui().device().findObject(
                UiSelector().resourceId("android:id/button1")
        )
        closeBtn.click()

        // Install should have failed
        assertThat(installation.get(TIMEOUT, TimeUnit.MILLISECONDS))
            .isEqualTo(Activity.RESULT_CANCELED)
    }

    @Test
    @ApiTest(
        apis = ["android.os.UserManager#DISALLOW_DEBUGGING_FEATURES",
        "android.os.UserManager#DISALLOW_INSTALL_APPS"]
    )
    @DevicePolicyRelevant
    @EnsureHasWorkProfile
    @EnsureDoesNotHaveUserRestriction(
        value = DISALLOW_DEBUGGING_FEATURES,
        onUser = UserType.WORK_PROFILE
    )
    @EnsureDoesNotHaveUserRestriction(value = DISALLOW_INSTALL_APPS, onUser = UserType.WORK_PROFILE)
    fun unrestrictedWorkProfile_adbInstallOnAllUsers_installedOnAllUsers() {
        val initialUser = sDeviceState.initialUser()
        val workProfile = sDeviceState.workProfile()
        assertThat(
            TestApis.devicePolicy().userRestrictions(workProfile).isSet(DISALLOW_DEBUGGING_FEATURES)
        ).isFalse()
        assertThat(
            TestApis.devicePolicy().userRestrictions(workProfile).isSet(DISALLOW_INSTALL_APPS)
        ).isFalse()

        installPackageViaAdb(apkPath = "$TEST_APK_LOCATION/$TEST_APK_NAME")

        var targetPackage = TestApis.packages().installedForUser(initialUser).filter {
            it.packageName().equals(TEST_APK_PACKAGE_NAME)
        }
        assertWithMessage("Test app should be installed in initial user")
            .that(targetPackage.size)
            .isNotEqualTo(0)

        targetPackage = TestApis.packages().installedForUser(workProfile).filter {
            it.packageName().equals(TEST_APK_PACKAGE_NAME)
        }
        assertWithMessage("Test app should be installed in work profile")
            .that(targetPackage.size)
            .isNotEqualTo(0)
    }

    /**
     * Start an installation via an Intent
     */
    private fun startInstallationViaIntent(intent: Intent): CompletableFuture<Int> {
        return installDialogStarter.activity.startActivityForResult(intent)
    }

    private fun installPackageViaAdb(apkPath: String, user: UserReference? = null): String? {
        val cmd = ShellCommand.builderForUser(user, "pm install")
        cmd.addOperand(apkPath)
        return try {
            cmd.execute()
        } catch (e: AdbException) {
            null
        }
    }

    @Throws(SecurityException::class)
    private fun createSessionForUser(user: UserReference = sDeviceState.initialUser()):
            Pair<Int, Session> {
        val context = TestApis.context().androidContextAsUser(user)
        val pm = context.packageManager
        val pi = pm.packageInstaller

        val params = SessionParams(SessionParams.MODE_FULL_INSTALL)
        params.setRequireUserAction(SessionParams.USER_ACTION_REQUIRED)

        val sessionId = pi.createSession(params)
        val session = pi.openSession(sessionId)

        return Pair(sessionId, session)
    }

    private fun writeSessionAsUser(
        user: UserReference = sDeviceState.initialUser(),
        session: Session
    ) {
        val context = TestApis.context().androidContextAsUser(user)
        // val apkFile = File(context.filesDir, TEST_APK_NAME)
        // Write data to session
        apkFile.inputStream().use { fileOnDisk ->
            session.openWrite(TEST_APK_NAME, 0, -1).use { sessionFile ->
                fileOnDisk.copyTo(sessionFile)
            }
        }
    }

    private fun commitSessionAsUser(
        user: UserReference = sDeviceState.initialUser(),
        session: Session
    ): Intent? {
        val context = TestApis.context().androidContextAsUser(user)
        val receiver: BlockingBroadcastReceiver =
                sDeviceState.registerBroadcastReceiverForUser(user, APP_INSTALL_ACTION)
        receiver.register()

        val intent = Intent(APP_INSTALL_ACTION).setPackage(context.packageName)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0 /* requestCode */,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        session.commit(pendingIntent.intentSender)

        // The system should have asked us to launch the installer
        return receiver.awaitForBroadcast()
    }

    private fun getAppInstallationIntent(apkFile: File): Intent {
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
        intent.data = FileProvider.getUriForFile(context, CONTENT_AUTHORITY, apkFile)
        intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        intent.putExtra(Intent.EXTRA_RETURN_RESULT, true)
        return intent
    }

    /**
     * Click a button in the UI of the installer app
     *
     * @param resId The resource ID of the button to click
     */
    fun clickInstallerUIButton(resId: String) {
        clickInstallerUIButton(getBySelector(resId))
    }

    fun getBySelector(id: String): BySelector {
        // Normally, we wouldn't need to look for buttons from 2 different packages.
        // However, to fix b/297132020, AlertController was replaced with AlertDialog and shared
        // to selective partners, leading to fragmentation in which button surfaces in an OEM's
        // installer app.
        return By.res(
            Pattern.compile(
                String.format(
                    "(?:^%s|^%s):id/%s", PACKAGE_INSTALLER_PACKAGE_NAME, SYSTEM_PACKAGE_NAME, id
                )
            )
        )
    }

    /**
     * Click a button in the UI of the installer app
     *
     * @param bySelector The bySelector of the button to click
     */
    fun clickInstallerUIButton(bySelector: BySelector) {
        var button: UiObject2? = null
        val startTime = System.currentTimeMillis()
        while (startTime + TIMEOUT > System.currentTimeMillis()) {
            try {
                button = uiDevice.wait(Until.findObject(bySelector), 1000)
                if (button != null) {
                    Log.d(
                        TAG,
                        "Found bounds: ${button.getVisibleBounds()} of button $bySelector," +
                        " text: ${button.getText()}," +
                        " package: ${button.getApplicationPackage()}"
                    )
                    button.click()
                    return
                } else {
                    // Maybe the screen is small. Swipe down and attempt to click
                    swipeDown()
                }
            } catch (ignore: Throwable) {
            }
        }
        Assert.fail("Failed to click the button: $bySelector")
    }

    private fun swipeDown() {
        // Perform a swipe from the center of the screen to the top of the screen.
        // Higher the "steps" value, slower is the swipe
        val centerX = uiDevice.displayWidth / 2
        val centerY = uiDevice.displayHeight / 2
        uiDevice.swipe(centerX, centerY, centerX, 0, 10)
    }
}
