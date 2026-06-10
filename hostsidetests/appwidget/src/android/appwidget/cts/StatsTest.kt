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

import android.cts.statsdatom.lib.AtomTestUtils
import android.cts.statsdatom.lib.ConfigUtils
import android.cts.statsdatom.lib.DeviceUtils
import android.cts.statsdatom.lib.ReportUtils
import com.android.os.framework.FrameworkExtensionAtoms
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test
import com.android.tradefed.util.RunUtil
import com.google.common.truth.Truth.assertThat
import com.google.protobuf.ExtensionRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(DeviceJUnit4ClassRunner::class)
class StatsTest : BaseHostJUnit4Test() {
    private companion object {
        private const val PACKAGE = "android.appwidget.cts.app"
        private const val TEST_CLASS = "android.appwidget.cts.app.StatsDeviceTest"
        private const val BIND_WIDGET = "bindWidget"
    }

    var user = 0

    @Before
    fun before() {
        // Clear package and start user
        val pmResult = device.executeShellV2Command("pm clear $PACKAGE")
        assertThat(pmResult.exitCode).isEqualTo(0)
        user = device.mainUserId ?: device.currentUser
        device.startUser(user, true)

        // Clear statsd configs uploaded by other tests
        ConfigUtils.removeConfig(device)
        ReportUtils.clearReports(device)
    }

    @After
    fun after() {
        val pmResult = device.executeShellV2Command("pm clear $PACKAGE")
        assertThat(pmResult.exitCode).isEqualTo(0)
        ConfigUtils.removeConfig(device)
        ReportUtils.clearReports(device)
    }

    @Test
    fun testWidgetMemoryStats() {
        // Create statsd config and upload it to the device
        ConfigUtils.uploadConfigForPulledAtomWithUid(
            device,
            PACKAGE,
            FrameworkExtensionAtoms.WIDGET_MEMORY_STATS_FIELD_NUMBER,
            /* useUidAttributionChain = */
            false,
        )

        // Bind widget
        val bindResult = runDeviceTests(PACKAGE, TEST_CLASS, BIND_WIDGET)
        assertThat(bindResult).isTrue()

        // Trigger atom pull
        AtomTestUtils.sendAppBreadcrumbReportedAtom(device)
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG.toLong())

        // Get atoms
        val extensionRegistry = ExtensionRegistry.newInstance()
        FrameworkExtensionAtoms.registerAllExtensions(extensionRegistry)
        val events = ReportUtils.getGaugeMetricAtoms(
            device,
            extensionRegistry,
            /* checkTimestampTruncated= */
            false,
        )

        // Verify widget memory stat was logged
        val stats = events.single().getExtension(FrameworkExtensionAtoms.widgetMemoryStats)
        assertThat(stats.uid)
            .isEqualTo(DeviceUtils.getAppUidForUser(device, PACKAGE, user))
        // Bitmap is 640x480 with ARGB_8888 config
        assertThat(stats.bitmapMemoryBytes).isEqualTo(640 * 480 * 4)
    }
}
