/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.cts.statsdatom.telecom;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.RunUtil;

import org.junit.After;
import org.junit.Before;

public class BaseTelecomMetricsTest extends BaseHostJUnit4Test implements IBuildReceiver {

    public static final String APK_CTS_TELECOM_TEST = "CtsTelecomTestCases.apk";
    public static final String PKG_CTS_TELECOM_TEST = "android.telecom.cts";
    static final String CMD_ENABLE_TEST = "cmd telecom set-metrics-test-enabled";
    static final String CMD_DISABLE_TEST = "cmd telecom set-metrics-test-enabled";
    private static final String FEATURE_TELECOM = "android.software.telecom";
    protected int mTestAppUid = -1;

    protected IBuildInfo mCtsBuild;

    @Before
    public void setUp() throws Exception {
        assumeTrue(DeviceUtils.hasFeature(getDevice(), FEATURE_TELECOM));
        assertThat(mCtsBuild).isNotNull();
        setTestModeEnabled(true);
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.installStatsdTestApp(getDevice(), mCtsBuild);
        for (String[] app : getInstalledTestApps()) {
            DeviceUtils.installTestApp(getDevice(), app[0], app[1], mCtsBuild);
            if (mTestAppUid < 0) {
                mTestAppUid = DeviceUtils.getAppUid(getDevice(), app[1]);
            }
        }
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
    }

    @After
    public void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        for (String[] app : getInstalledTestApps()) {
            DeviceUtils.uninstallTestApp(getDevice(), app[0]);
        }
        DeviceUtils.uninstallStatsdTestApp(getDevice());
        setTestModeEnabled(false);
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    protected String[][] getInstalledTestApps() {
        return new String[][] {{APK_CTS_TELECOM_TEST, PKG_CTS_TELECOM_TEST}};
    }

    private void setTestModeEnabled(boolean enabled) throws Exception {
        getDevice().executeShellCommand(enabled ? CMD_ENABLE_TEST : CMD_DISABLE_TEST);
    }
}
