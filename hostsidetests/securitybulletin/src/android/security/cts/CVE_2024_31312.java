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

package android.security.cts;

import static com.android.sts.common.CommandUtil.runAndCheck;
import static com.android.sts.common.SystemUtil.withSetting;

import static org.junit.Assume.assumeNoException;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2024_31312 extends NonRootSecurityTestCase {

    @AsbSecurityTest(cveBugId = 314333719)
    @Test
    public void testPocCVE_2024_31312() {
        ITestDevice device = null;
        try {
            // Disable show media on lock screen
            device = getDevice();

            try (AutoCloseable withDisableNotificationOnLockScreen =
                            withSetting(device, "secure", "lock_screen_show_notifications", "0");
                    AutoCloseable withDisableMediaOnLockScreen =
                            withSetting(device, "secure", "media_controls_lock_screen", "0")) {
                // Install application
                installPackage("CVE-2024-31312.apk", "-g");

                // Run DeviceTest
                final String testPkg = "android.security.cts.CVE_2024_31312";
                runDeviceTests(testPkg, testPkg + ".DeviceTest", "testPocCVE_2024_31312");
            }
        } catch (Exception e) {
            assumeNoException(e);
        } finally {
            try {
                // To exit the test gracefully
                runAndCheck(device, "input keyevent KEYCODE_POWER");
            } catch (Exception e) {
                // Ignore all exceptions
            }
        }
    }
}
