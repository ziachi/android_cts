/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.cts.statsdatom.sizecompatrestartbutton;

import static android.cts.statsdatom.lib.DeviceUtils.FEATURE_WATCH;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.compatibility.common.util.NonApiTest;
import com.android.os.AtomsProto;
import com.android.os.AtomsProto.SizeCompatRestartButtonEventReported;
import com.android.os.AtomsProto.SizeCompatRestartButtonEventReported.Event;
import com.android.os.StatsLog;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.RunUtil;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This test is for making sure that Size Compat Restart Button appearances and clicks log the
 * desired atoms.
 *
 *  <p>Build/Install/Run:
 *  atest CtsStatsdAtomHostTestCases:SizeCompatRestartButtonStatsTests
 */
@NonApiTest(exemptionReasons = {}, justification = "METRIC")
public class SizeCompatRestartButtonStatsTests extends DeviceTestCase implements IBuildReceiver {

    private static final String CMD_GET_STAY_ON = "settings get global stay_on_while_plugged_in";
    private static final String CMD_PUT_STAY_ON_TEMPLATE =
            "settings put global stay_on_while_plugged_in ";
    private static final String WM_SET_IGNORE_ORIENTATION_REQUEST =
            "wm set-ignore-orientation-request ";
    private static final String WM_GET_IGNORE_ORIENTATION_REQUEST =
            "wm get-ignore-orientation-request";
    private static final Pattern IGNORE_ORIENTATION_REQUEST_PATTERN =
            Pattern.compile("ignoreOrientationRequest (true|false) for displayId=\\d+");
    private static final int ENABLE_STAY_ON_CODE = 7;

    private IBuildInfo mCtsBuild;
    private long mOriginalStayOnSetting;
    private boolean mInitialIgnoreOrientationRequest;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        assertThat(mCtsBuild).isNotNull();
        mOriginalStayOnSetting = Long.parseLong(
                getDevice().executeShellCommand(CMD_GET_STAY_ON).trim());
        Matcher matcher = IGNORE_ORIENTATION_REQUEST_PATTERN.matcher(
                getDevice().executeShellCommand(WM_GET_IGNORE_ORIENTATION_REQUEST));
        assertTrue("get-ignore-orientation-request should match pattern",
                matcher.find());
        mInitialIgnoreOrientationRequest = Boolean.parseBoolean(matcher.group(1));

        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        getDevice().executeShellCommand(CMD_PUT_STAY_ON_TEMPLATE + ENABLE_STAY_ON_CODE);

        getDevice().executeShellCommand(WM_SET_IGNORE_ORIENTATION_REQUEST + "true");
        DeviceUtils.installStatsdTestApp(getDevice(), mCtsBuild);
        DeviceUtils.turnScreenOn(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.SIZE_COMPAT_RESTART_BUTTON_EVENT_REPORTED_FIELD_NUMBER,
                /*uidInAttributionChain=*/ false);
    }

    @Override
    protected void tearDown() throws Exception {
        getDevice().executeShellCommand(CMD_PUT_STAY_ON_TEMPLATE + mOriginalStayOnSetting);
        getDevice().executeShellCommand(
                WM_SET_IGNORE_ORIENTATION_REQUEST + mInitialIgnoreOrientationRequest);
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.uninstallStatsdTestApp(getDevice());
        super.tearDown();
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    public void testSizeCompatRestartButtonAppearedAndClicked() throws Exception {
        if (DeviceUtils.hasFeature(getDevice(), FEATURE_WATCH)) return;
        DeviceUtils.runDeviceTestsOnStatsdApp(
                getDevice(), ".appcompat.AppCompatTests", "testClickSizeCompatRestartButton");

        // Wait to make sure metric event is reported.
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data.size()).isAtLeast(1);

        SizeCompatRestartButtonEventReported atom =
                data.getFirst().getAtom().getSizeCompatRestartButtonEventReported();
        assertThat(atom.getUid()).isEqualTo(DeviceUtils.getStatsdTestAppUid(getDevice()));
        assertThat(atom.getEvent()).isEqualTo(Event.APPEARED);

        // Can't enforce size compat restart button to exist.
        if (data.size() < 2) {
            return;
        }

        atom = data.get(1).getAtom().getSizeCompatRestartButtonEventReported();
        assertThat(atom.getUid()).isEqualTo(DeviceUtils.getStatsdTestAppUid(getDevice()));
        assertThat(atom.getEvent()).isEqualTo(Event.CLICKED);
    }
}
