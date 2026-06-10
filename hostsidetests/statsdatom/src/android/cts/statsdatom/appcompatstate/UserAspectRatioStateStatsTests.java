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

package android.cts.statsdatom.appcompatstate;

import static android.app.settings.Action.ACTION_USER_ASPECT_RATIO_16_9_SELECTED;
import static android.app.settings.Action.ACTION_USER_ASPECT_RATIO_16_9_UNSELECTED;
import static android.app.settings.Action.ACTION_USER_ASPECT_RATIO_3_2_SELECTED;
import static android.app.settings.Action.ACTION_USER_ASPECT_RATIO_3_2_UNSELECTED;
import static android.app.settings.Action.ACTION_USER_ASPECT_RATIO_4_3_SELECTED;
import static android.app.settings.Action.ACTION_USER_ASPECT_RATIO_4_3_UNSELECTED;
import static android.app.settings.Action.ACTION_USER_ASPECT_RATIO_APP_DEFAULT_SELECTED;
import static android.app.settings.Action.ACTION_USER_ASPECT_RATIO_APP_DEFAULT_UNSELECTED;
import static android.app.settings.Action.ACTION_USER_ASPECT_RATIO_DISPLAY_SIZE_SELECTED;
import static android.app.settings.Action.ACTION_USER_ASPECT_RATIO_DISPLAY_SIZE_UNSELECTED;
import static android.app.settings.Action.ACTION_USER_ASPECT_RATIO_FULL_SCREEN_SELECTED;
import static android.app.settings.Action.ACTION_USER_ASPECT_RATIO_FULL_SCREEN_UNSELECTED;
import static android.app.settings.Action.ACTION_USER_ASPECT_RATIO_HALF_SCREEN_SELECTED;
import static android.app.settings.Action.ACTION_USER_ASPECT_RATIO_HALF_SCREEN_UNSELECTED;
import static android.cts.statsdatom.lib.DeviceUtils.FEATURE_AUTOMOTIVE;
import static android.cts.statsdatom.lib.DeviceUtils.FEATURE_WATCH;

import static com.google.common.truth.Truth.assertThat;

import android.app.settings.Action;
import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.compatibility.common.util.NonApiTest;
import com.android.os.AtomsProto;
import com.android.os.StatsLog;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.RunUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This test is for making sure that user aspect ratio changes in Settings log the desired atoms.
 *
 * <p>Build/Install/Run:
 * atest CtsStatsdAtomHostTestCases:UserAspectRatioStateStatsTests
 */
@NonApiTest(exemptionReasons = {}, justification = "METRIC")
public class UserAspectRatioStateStatsTests  extends DeviceTestCase implements IBuildReceiver {

    private static final String TEST_APK =
            "CtsPropertyCompatAllowUserAspectRatioOverrideOptInApp.apk";
    private static final String TEST_PKG = "android.server.wm.allowuseraspectratiooverrideoptin";
    private static final Pattern USER_ASPECT_RATIO_SETTINGS_PATTERN =
            Pattern.compile("Is the user aspect ratio settings enabled: (true|false)");
    private static final String WM_GET_LETTERBOX_STYLE = "wm get-letterbox-style";
    private IBuildInfo mCtsBuild;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.installStatsdTestApp(getDevice(), mCtsBuild);
        DeviceUtils.installTestApp(getDevice(), TEST_APK, TEST_PKG, mCtsBuild);
        DeviceUtils.turnScreenOn(getDevice());
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.SETTINGS_UI_CHANGED_FIELD_NUMBER);
    }

    @Override
    protected void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.uninstallTestApp(getDevice(), TEST_PKG);
        DeviceUtils.uninstallStatsdTestApp(getDevice());
        super.tearDown();
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    public void testUserAspectRatioOption() throws Exception {
        // TODO(b/400982232): Add atom statsd logging for automotive
        if (DeviceUtils.hasFeature(getDevice(), FEATURE_WATCH)
                || DeviceUtils.hasFeature(getDevice(), FEATURE_AUTOMOTIVE)) return;
        // Cannot enforce existence of user aspect ratio settings.
        if (isUserAspectRatioSettingsDisabled()) return;
        // Run an local test (AppCompatTests#testUserAspectRatioOptions) to
        // generate device interactions that cause aspect ratio option atoms to be logged.
        final String testClass = ".appcompat.AppCompatTests";
        final String testMethod = "testUserAspectRatioOptions";

        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), testClass, testMethod);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        List<Action> realActions = new ArrayList<>();
        for (StatsLog.EventMetricData d : data) {
            realActions.add(d.getAtom().getSettingsUiChanged().getAction());
        }

        List<Action> expectedAnyActions = Arrays.asList(
                ACTION_USER_ASPECT_RATIO_APP_DEFAULT_SELECTED,
                ACTION_USER_ASPECT_RATIO_APP_DEFAULT_UNSELECTED,
                ACTION_USER_ASPECT_RATIO_FULL_SCREEN_SELECTED,
                ACTION_USER_ASPECT_RATIO_FULL_SCREEN_UNSELECTED,
                ACTION_USER_ASPECT_RATIO_3_2_SELECTED,
                ACTION_USER_ASPECT_RATIO_3_2_UNSELECTED,
                ACTION_USER_ASPECT_RATIO_4_3_SELECTED,
                ACTION_USER_ASPECT_RATIO_4_3_UNSELECTED,
                ACTION_USER_ASPECT_RATIO_16_9_SELECTED,
                ACTION_USER_ASPECT_RATIO_16_9_UNSELECTED,
                ACTION_USER_ASPECT_RATIO_DISPLAY_SIZE_SELECTED,
                ACTION_USER_ASPECT_RATIO_DISPLAY_SIZE_UNSELECTED,
                ACTION_USER_ASPECT_RATIO_HALF_SCREEN_SELECTED,
                ACTION_USER_ASPECT_RATIO_HALF_SCREEN_UNSELECTED);
        assertThat(realActions).containsAnyIn(expectedAnyActions);
    }

    private boolean isUserAspectRatioSettingsDisabled() throws DeviceNotAvailableException {
        String output = getDevice().executeShellCommand(WM_GET_LETTERBOX_STYLE);
        final Matcher matcher = USER_ASPECT_RATIO_SETTINGS_PATTERN.matcher(output);
        assertTrue(matcher.find());
        return !Boolean.parseBoolean(matcher.group(1));
    }
}
