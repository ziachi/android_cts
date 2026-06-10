/*
 * Copyright 2022 The Android Open Source Project
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
package android.packageinstaller.install.cts

import android.app.UiAutomation
import android.content.pm.Flags
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.PackageManager.APP_METADATA_SOURCE_APK
import android.content.pm.PackageManager.APP_METADATA_SOURCE_INSTALLER
import android.content.pm.PackageManager.APP_METADATA_SOURCE_UNKNOWN
import android.content.pm.PackageManager.NameNotFoundException
import android.os.PersistableBundle
import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.platform.test.rule.ScreenRecordRule.ScreenRecord
import androidx.test.runner.AndroidJUnit4
import androidx.test.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.FileNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@AppModeFull(reason = "Instant apps cannot install packages")
@ScreenRecord
class InstallAppMetadataTest : PackageInstallerTestBase() {

    companion object {
        private val TEST_FIELD = "testField"
        private val TEST_APK2_NAME = "CtsEmptyTestApp_AppMetadataInApk.apk"
        private val TEST_APK3_NAME = "CtsEmptyTestApp_AppMetadataInApk_ExceedSizeLimit.apk"
    }

    private val uiAutomation: UiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation()

    @JvmField
    @Rule
    val mCheckFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    @RequiresFlagsEnabled(Flags.FLAG_ASL_IN_APK_APP_METADATA_SOURCE)
    @Test(expected = SecurityException::class)
    fun getAppMetadataSourceWithNoPermission() {
        installApkViaSession(createAppMetadata())

        pm.getAppMetadataSource(TEST_APK_PACKAGE_NAME)
    }

    @RequiresFlagsEnabled(Flags.FLAG_ASL_IN_APK_APP_METADATA_SOURCE)
    @Test(expected = NameNotFoundException::class)
    fun getAppMetadataSourceApNotInstall() {
        uiAutomation.adoptShellPermissionIdentity()
        try {
            pm.getAppMetadataSource(TEST_APK_PACKAGE_NAME)
        } catch (e: Exception) {
            throw e
        } finally {
            uiAutomation.dropShellPermissionIdentity()
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_ASL_IN_APK_APP_METADATA_SOURCE)
    @Test
    fun appMetadataInApk() {
        installApk(TEST_APK2_NAME, true)
        installApk(TEST_APK_NAME, false)
        installApk(TEST_APK2_NAME, true)
    }

    @RequiresFlagsEnabled(Flags.FLAG_ASL_IN_APK_APP_METADATA_SOURCE)
    @Test
    fun appMetadataInApk_exceedSizeLimit() {
        installApk(TEST_APK3_NAME, false)
    }

    @RequiresFlagsEnabled(Flags.FLAG_ASL_IN_APK_APP_METADATA_SOURCE)
    @Test
    fun installViaSessionWithAppMetadataInApk() {
        val data = createAppMetadata()
        installApkViaSession(data, TEST_APK2_NAME)
        assertAppMetadata(data.getString(TEST_FIELD))
    }

    @RequiresFlagsEnabled(Flags.FLAG_ASL_IN_APK_APP_METADATA_SOURCE)
    @Test
    fun updateWithMultipleSource() {
        val data = createAppMetadata()
        installApkViaSession(data)
        assertAppMetadata(data.getString(TEST_FIELD))
        installApk(TEST_APK2_NAME, true)
        installApkViaSession(data)
        assertAppMetadata(data.getString(TEST_FIELD))
    }

    @Test
    fun installViaSessionWithAppMetadata() {
        val data = createAppMetadata()
        installApkViaSession(data)
        assertAppMetadata(data.getString(TEST_FIELD))
        installApkViaSession(null)
        assertAppMetadata(null)
        installApkViaSession(data)
        assertAppMetadata(data.getString(TEST_FIELD))
    }

    @Test(expected = SecurityException::class)
    fun getAppMetadataWithNoPermission() {
        installApkViaSession(createAppMetadata())
        pm.getAppMetadata(TEST_APK_PACKAGE_NAME)
    }

    @Test(expected = IllegalArgumentException::class)
    fun installViaSessionWithBadAppMetadata() {
        installApkViaSession(createAppMetadataExceedSizeLimit())
    }

    @Test(expected = NameNotFoundException::class)
    fun noInstallGetAppMetadata() {
        assertAppMetadata(null)
    }

    @Test
    fun installViaSessionWithOnlyAppMetadata() {
        val data = createAppMetadata()
        val (sessionId, session) = createSession(0, false, null)
        setAppMetadata(session, data)
        assertThat(session.getNames()).isEmpty()
        commitSession(session, false)
        val result = getInstallSessionResult()
        assertThat(result.status).isEqualTo(PackageInstaller.STATUS_FAILURE_INVALID)
    }

    @Test
    fun resetAppMetadataInSession() {
        val data = createAppMetadata()
        val (sessionId, session) = createSession(0, false, null)
        writeSession(session, TEST_APK_NAME)
        setAppMetadata(session, data)
        assertBundleAreEqual(data, session.appMetadata)
        setAppMetadata(session, null)
        assertThat(session.getAppMetadata().isEmpty()).isTrue()
        commitSession(session)
        clickInstallerUIButton(INSTALL_BUTTON_ID)
        val result = getInstallSessionResult()
        assertThat(result.status).isEqualTo(PackageInstaller.STATUS_SUCCESS)
        assertAppMetadata(null)
    }

    @Test(expected = FileNotFoundException::class)
    fun readAppMetadataFileShouldFail() {
        val data = createAppMetadata()
        installApkViaSession(data)

        val appInfo = pm.getApplicationInfo(
            TEST_APK_PACKAGE_NAME,
            PackageManager.ApplicationInfoFlags.of(0)
        )
        val file = File(File(appInfo.publicSourceDir).getParentFile(), "app.metadata")
        PersistableBundle.readFromStream(file.inputStream())
    }

    private fun installApk(apkName: String, shouldHaveAppMetadata: Boolean) {
        installPackage(apkName)

        uiAutomation.adoptShellPermissionIdentity()
        try {
            val data = pm.getAppMetadata(TEST_APK_PACKAGE_NAME)
            if (shouldHaveAppMetadata) {
                assertThat(data.size()).isEqualTo(2)
                assertThat(data.getString("source")).isEqualTo("apk")
                assertThat(data.getLong("version")).isEqualTo(2)
                assertThat(pm.getAppMetadataSource(TEST_APK_PACKAGE_NAME))
                    .isEqualTo(APP_METADATA_SOURCE_APK)
            } else {
                assertThat(data.isEmpty).isTrue()
                assertThat(pm.getAppMetadataSource(TEST_APK_PACKAGE_NAME))
                    .isEqualTo(APP_METADATA_SOURCE_UNKNOWN)
            }
        } finally {
            uiAutomation.dropShellPermissionIdentity()
        }
    }

    private fun installApkViaSession(data: PersistableBundle?, apkName: String = TEST_APK_NAME) {
        val (sessionId, session) = createSession(0, false, null)
        writeSession(session, apkName)
        if (data != null) {
            setAppMetadata(session, data)
            assertBundleAreEqual(data, session.appMetadata)
            assertThat(session.getNames()).hasLength(1)
        }
        commitSession(session)

        clickInstallerUIButton(INSTALL_BUTTON_ID)

        // Install should have succeeded
        val result = getInstallSessionResult()
        assertThat(result.status).isEqualTo(PackageInstaller.STATUS_SUCCESS)
    }

    private fun setAppMetadata(session: PackageInstaller.Session, data: PersistableBundle?) {
        try {
            session.setAppMetadata(data)
        } catch (e: Exception) {
            session.abandon()
            throw e
        }
    }

    private fun assertBundleAreEqual(bundle1: PersistableBundle, bundle2: PersistableBundle) {
        assertThat(bundle1).isNotNull()
        assertThat(bundle2).isNotNull()
        assertThat(bundle1.size()).isEqualTo(1)
        assertThat(bundle2.size()).isEqualTo(1)
        assertThat(bundle2.getString(TEST_FIELD)).isEqualTo(bundle1.getString(TEST_FIELD))
    }

    private fun assertAppMetadata(testValue: String?) {
        uiAutomation.adoptShellPermissionIdentity()
        try {
            val data = pm.getAppMetadata(TEST_APK_PACKAGE_NAME)
            assertThat(data).isNotNull()
            if (testValue != null) {
                assertThat(data.size()).isEqualTo(1)
                assertThat(data.containsKey(TEST_FIELD)).isTrue()
                assertEquals(data.getString(TEST_FIELD), testValue)
                if (Flags.aslInApkAppMetadataSource()) {
                    assertThat(pm.getAppMetadataSource(TEST_APK_PACKAGE_NAME))
                        .isEqualTo(APP_METADATA_SOURCE_INSTALLER)
                }
            } else {
                assertThat(data.isEmpty()).isTrue()
                if (Flags.aslInApkAppMetadataSource()) {
                    assertThat(pm.getAppMetadataSource(TEST_APK_PACKAGE_NAME))
                        .isEqualTo(APP_METADATA_SOURCE_UNKNOWN)
                }
            }
        } finally {
            uiAutomation.dropShellPermissionIdentity()
        }
    }

    private fun createAppMetadata(): PersistableBundle {
        val bundle = PersistableBundle()
        bundle.putString(TEST_FIELD, "testValue")
        return bundle
    }

    private fun createAppMetadataExceedSizeLimit(): PersistableBundle {
        val bundle = PersistableBundle()
        // create a bundle that is greater than default size limit of 32KB.
        bundle.putString(TEST_FIELD, "a".repeat(32000))
        return bundle
    }
}
