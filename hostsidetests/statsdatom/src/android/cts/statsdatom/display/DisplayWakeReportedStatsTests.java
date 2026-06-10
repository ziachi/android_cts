/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.cts.statsdatom.display;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeFalse;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.compatibility.common.util.NonApiTest;
import com.android.compatibility.common.util.PollingCheck;
import com.android.os.AtomsProto;
import com.android.os.StatsLog;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.RunUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@RunWith(DeviceJUnit4ClassRunner.class)
@NonApiTest(exemptionReasons = {}, justification = "METRIC")
public class DisplayWakeReportedStatsTests extends BaseHostJUnit4Test implements IBuildReceiver {
    private static final String FEATURE_AUTOMOTIVE = "android.hardware.type.automotive";
    private static final int WAKE_REASON_UNKNOWN = 0;
    private static final int WAKE_REASON_APPLICATION = 2;
    private static final int WAKE_REASON_WAKE_KEY = 6;
    private static final int SYSTEM_UID = 1000;

    private IBuildInfo mCtsBuild;

    @Before
    public void setUp() throws Exception {
        // com.android.server.cts.device.statsdatom.DisplayWakeReportedTests uses 'input keyevent
        // SLEEP' which is not correctly supported on Automotive MUMD devices. So tests are skipped
        // temporarily b/369415968 b/366037029
        assumeFalse(isAutomotiveWithVisibleBackgroundUser());
        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.installStatsdTestApp(getDevice(), mCtsBuild);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        // Upload config to collect DisplayWakeReported event.
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.DISPLAY_WAKE_REPORTED_FIELD_NUMBER);
    }

    @After
    public void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.uninstallStatsdTestApp(getDevice());
        DeviceUtils.turnScreenOn(getDevice());
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    @Test
    public void testDisplayWakeReportedFromWakeKey() throws Exception {
        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".DisplayWakeReportedTests",
                "testWakeWithWakeKey");
        assertWakeup(WAKE_REASON_WAKE_KEY, SYSTEM_UID);
    }

    @Test
    public void testDisplayWakeReportedFromWakeLock() throws Exception {
        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".DisplayWakeReportedTests",
                "testWakeWithWakeLock");

        assertWakeup(WAKE_REASON_APPLICATION, DeviceUtils.getStatsdTestAppUid(getDevice()));
    }

    @Test
    public void testDisplayWakeReportedFromWakeUpApi() throws Exception {
        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".DisplayWakeReportedTests",
                "testWakeWithWakeUpApi");

        assertWakeup(WAKE_REASON_UNKNOWN, DeviceUtils.getStatsdTestAppUid(getDevice()));
    }

    @Test
    public void testDisplayWakeReportedFromTurnScreenOnActivity() throws Exception {
        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".DisplayWakeReportedTests",
                "testWakeWithTurnScreenOnActivity");

        assertWakeup(WAKE_REASON_APPLICATION, SYSTEM_UID);
    }

    private void assertWakeup(int reason, int uid) throws Exception {
        // Assert one DisplayWakeReported event has been collected.
        final List<StatsLog.EventMetricData> data = new ArrayList<>();
        PollingCheck.check(
                "EventMetricData should have 1 entry",
                /* timeout= */ 5500,
                () -> {
                    data.addAll(ReportUtils.getEventMetricDataList(getDevice()));
                    if (data.isEmpty()) {
                        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
                    }
                    return data.size() == 1;
                });

        AtomsProto.DisplayWakeReported displayWakeReported =
                data.get(0).getAtom().getDisplayWakeReported();
        assertThat(displayWakeReported.getWakeUpReason()).isEqualTo(reason);
        assertThat(displayWakeReported.getUid()).isEqualTo(uid);
    }

    private boolean isAutomotiveWithVisibleBackgroundUser() throws Exception {
        return getDevice().hasFeature("feature:" + FEATURE_AUTOMOTIVE)
                && getDevice().isVisibleBackgroundUsersSupported();
    }
}
