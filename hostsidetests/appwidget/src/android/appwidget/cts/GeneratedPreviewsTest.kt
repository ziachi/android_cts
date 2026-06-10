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
package android.appwidget.cts

import android.appwidget.flags.Flags
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.host.HostFlagsValueProvider
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(DeviceJUnit4ClassRunner::class)
@RequiresFlagsEnabled(Flags.FLAG_GENERATED_PREVIEWS, Flags.FLAG_REMOTE_VIEWS_PROTO)
class GeneratedPreviewsTest : BaseHostJUnit4Test() {
    private companion object {
        private const val PACKAGE = "android.appwidget.cts.app"
        private const val TEST_CLASS = "android.appwidget.cts.app.PreviewDeviceTest"
        private const val SET_PREVIEW_TEST = "setPreview"
        private const val CHECK_PREVIEW_TEST = "checkPreview"
    }

    @get:Rule
    val checkFlagsRule = HostFlagsValueProvider.createCheckFlagsRule { device }

    var user = 0

    @Before
    fun setUp() {
        // Clear package (which clears previews)
        val pmResult = device.executeShellV2Command("pm clear $PACKAGE")
        assertThat(pmResult.exitCode).isEqualTo(0)
        user = device.mainUserId ?: device.currentUser
        device.startUser(user, true)
    }

    @Test
    fun testGeneratedPreviewPersistence() {
        // Set preview
        val setResult = runDeviceTests(PACKAGE, TEST_CLASS, SET_PREVIEW_TEST)
        assertThat(setResult).isTrue()

        // Reboot
        device.rebootUntilOnline()
        device.waitForBootComplete(60_000L)
        device.startUser(user, true)

        // Check preview
        val checkResult = runDeviceTests(PACKAGE, TEST_CLASS, CHECK_PREVIEW_TEST)
        assertThat(checkResult).isTrue()
    }
}
