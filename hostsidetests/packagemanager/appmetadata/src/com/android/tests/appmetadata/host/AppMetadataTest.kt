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

package com.android.tests.appmetadata.host

import android.content.pm.Flags
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.host.HostFlagsValueProvider
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(DeviceJUnit4ClassRunner::class)
class AppMetadataTest : BaseHostJUnit4Test() {
    companion object {
        const val EMPTY_TEST_APK: String = "CtsEmptyTestApp_AppMetadataInApk.apk"
        const val EMPTY_TEST_APP_NAME: String = "android.packageinstaller.emptytestapp.cts"
        const val TEST_APK: String = "AppMetadataTestApp.apk"
        const val TEST_PACKAGE: String = "com.android.tests.appmetadata.app"
        const val TEST_CLASS_NAME: String = "$TEST_PACKAGE.AppMetadataDeviceTest"
        const val TEST_METHOD_NAME: String = "installPackageWithAppMetadata"
    }

    @Rule
    @JvmField
    val mCheckFlagsRule: CheckFlagsRule =
        HostFlagsValueProvider.createCheckFlagsRule { this.device }

    @Before
    fun uninstallEmptyTestApp() {
        device.uninstallPackage(EMPTY_TEST_APP_NAME)
    }

    @RequiresFlagsEnabled(Flags.FLAG_ASL_IN_APK_APP_METADATA_SOURCE)
    @Test
    fun appMetadataInAPkWithReboot() {
        assertThat(device.executeShellCommand("pm get-app-metadata $EMPTY_TEST_APP_NAME"))
            .contains("NameNotFoundException")
        installPackage(EMPTY_TEST_APK)
        assertThat(device.executeShellCommand("pm get-app-metadata $EMPTY_TEST_APP_NAME"))
            .contains("xml version")
        device.reboot()
        assertThat(device.executeShellCommand("pm get-app-metadata $EMPTY_TEST_APP_NAME"))
            .contains("xml version")
    }

    @Test
    fun appMetadataViaSessionWithReboot() {
        assertThat(device.executeShellCommand("pm get-app-metadata $EMPTY_TEST_APP_NAME"))
            .contains("NameNotFoundException")
        installPackage(TEST_APK)
        runDeviceTests(device, TEST_PACKAGE, TEST_CLASS_NAME, TEST_METHOD_NAME)
        device.reboot()
        assertThat(device.executeShellCommand("pm get-app-metadata $EMPTY_TEST_APP_NAME"))
            .contains("testValue")
    }
}
