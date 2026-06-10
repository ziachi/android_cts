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

package android.security.cts;

import static com.android.sts.common.CommandUtil.runAndCheck;
import static com.android.sts.common.DumpsysUtils.hasActivityResumed;
import static com.android.sts.common.ProcessUtil.INTENT_QUERY_CMDS;
import static com.android.sts.common.ProcessUtil.getAllProcessIdsFromComponents;
import static com.android.sts.common.SystemUtil.poll;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.LockSettingsUtil;
import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2025_22434 extends NonRootSecurityTestCase {
    ITestDevice mDevice;

    @AsbSecurityTest(cveBugId = 378900798)
    @Test
    public void testPocCVE_2025_22434() {
        try {
            // Get ITestDevice
            mDevice = getDevice();

            // Start settings process
            final String intentAction = "android.settings.SETTINGS";
            runAndCheck(mDevice, String.format("am start -a %s", intentAction));

            // Fetch pid's for all processes corresponding to action android.settings.SETTINGS.
            final Map<String, String> intentOptions = new HashMap<>();
            intentOptions.put("-a", intentAction);
            final Optional<Map<Integer, String>> pidNameMap =
                    getAllProcessIdsFromComponents(
                            INTENT_QUERY_CMDS[0] /* resolve-activity */, intentOptions, mDevice);
            assume().withMessage("No process found for android.settings.SETTINGS")
                    .that(pidNameMap.isPresent())
                    .isTrue();

            // Get the settings app's package name
            final String settingsPkgName =
                    pidNameMap.isPresent()
                            ? pidNameMap.get().values().iterator().next()
                            : "com.android.settings";

            // Ensure that settings app is not resumed
            runAndCheck(mDevice, "input keyevent KEYCODE_HOME");
            assume().that(hasSettingsResumed(settingsPkgName)).isFalse();

            // Enable screen lock and attempt to exploit the bug
            try (AutoCloseable withLockScreen = new LockSettingsUtil(mDevice).withLockScreen()) {
                // Lock the mDevice
                runAndCheck(mDevice, "input keyevent KEYCODE_POWER");
                runAndCheck(mDevice, "input keyevent KEYCODE_WAKEUP");

                // Attempt to launch settings app using Windows key + I
                runAndCheck(mDevice, "input keycombination KEYCODE_META_LEFT KEYCODE_I");
            }

            // Lift the lock screen
            runAndCheck(mDevice, "input keyevent KEYCODE_MENU");

            // Fail the test if settings app is in resumed state after unlocking device
            assertWithMessage(
                            "Device is vulnerable to b/378900798. Settings app can be accessed"
                                    + " from the lockscreen.")
                    .that(hasSettingsResumed(settingsPkgName))
                    .isFalse();
        } catch (Exception e) {
            assume().that(e).isNull();
        } finally {
            try {
                runAndCheck(mDevice, "input keyevent KEYCODE_BACK");
                runAndCheck(mDevice, "input keyevent KEYCODE_HOME");
            } catch (Exception e) {
                // Ignore exception while cleanup
            }
        }
    }

    private boolean hasSettingsResumed(String settingsPkgName) throws Exception {
        return poll(
                () -> {
                    try {
                        return hasActivityResumed(mDevice, settingsPkgName);
                    } catch (Exception e) {
                        return false;
                    }
                });
    }
}
