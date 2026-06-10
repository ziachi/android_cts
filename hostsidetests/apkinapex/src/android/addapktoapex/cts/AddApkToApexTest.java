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

package android.addapktoapex.cts;

import static com.android.cts.shim.lib.ShimPackage.SHIM_APEX_PACKAGE_NAME;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.cts.install.lib.host.InstallUtilsHost;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.ITestDevice.ApexInfo;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

/**
 * Test AddApkToApex.
 *
 * The test install an apex which doesn't contain any app then update the apex.
 * with a version contains an app and run the app main activity.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class AddApkToApexTest extends BaseHostJUnit4Test {

    private final InstallUtilsHost mHostUtils = new InstallUtilsHost(this);

    @Before
    public void beforeTest() throws Exception {
        mHostUtils.uninstallShimApexIfNecessary();
    }

    @After
    public void afterTest() throws Exception {
        mHostUtils.uninstallShimApexIfNecessary();
    }

    @Test
    public void updateShimApexAndStartTheApp() throws Exception {
        assumeTrue("Device does not support updating APEX", mHostUtils.isApexUpdateSupported());
        assumeTrue("Shim apex is already updated", getShimApexVersionCode(getDevice()) == 1);
        String apexWithApk = "com.android.apex.cts.shim.v2_add_apk_to_apex.apex";
        String appPackageName = "android.addapktoapex.app";
        String appActivityPath = appPackageName + "/.AddApkToApexDeviceActivity";

        // Update the shim apex and make sure that the app package has been installed.
        updateApexAndReboot(apexWithApk);
        assumeTrue("Shim apex did not get updated", getShimApexVersionCode(getDevice()) == 2);

        // Start the app activity and make sure that it has been run successfully.
        getDevice().executeShellCommand(
                "am start -W -n "
                + appActivityPath
        );
        String result = getDevice().executeShellCommand(
                "dumpsys activity activities | grep -E ' ResumedActivity.*"
                + appActivityPath
                + "'"
            );
        assertThat(result).contains(appActivityPath);
    }

    private void updateApexAndReboot(String apk) throws Exception  {
        getDevice().installPackage(mHostUtils.getTestFile(apk), true);
        getDevice().reboot();
    }

    private static long getShimApexVersionCode(ITestDevice device) throws Exception  {
        Set<ApexInfo> activeApexes = device.getActiveApexes();
        for (ApexInfo apex : activeApexes) {
            if (SHIM_APEX_PACKAGE_NAME.equals(apex.name)) {
                return apex.versionCode;
            }
        }
        return -1;
    }
}
