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
import static com.android.sts.common.DumpsysUtils.getParsedDumpsys;
import static com.android.sts.common.SystemUtil.Namespace;
import static com.android.sts.common.SystemUtil.poll;
import static com.android.sts.common.SystemUtil.withSetting;

import static com.google.common.truth.TruthJUnit.assume;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.LockSettingsUtil;
import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.function.BiPredicate;
import java.util.regex.Pattern;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2021_0682 extends NonRootSecurityTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 159624555)
    public void testPocCVE_2021_0682() {
        try {
            installPackage("CVE-2021-0682.apk", "-g");

            final ITestDevice device = getDevice();
            final String testPackage = "android.security.cts.CVE_2021_0682";
            try (AutoCloseable withLockScreen = new LockSettingsUtil(device).withLockScreen();
                    AutoCloseable withDeviceAccessibilityService =
                            withSetting(
                                    device,
                                    Namespace.SECURE,
                                    "enabled_accessibility_services",
                                    String.format("%s/.PocAccessibilityService", testPackage))) {

                // Lock the device and wake it up
                // Ensure device is in a locked state and the screen is on
                runAndCheck(device, "input keyevent KEYCODE_POWER");
                runAndCheck(device, "input keyevent KEYCODE_WAKEUP");

                // Checking the service status
                final BiPredicate<String, String> dumpsysService =
                        (serviceName, pattern) -> {
                            return poll(
                                    () -> {
                                        try {
                                            return getParsedDumpsys(
                                                            device,
                                                            serviceName,
                                                            pattern,
                                                            Pattern.CASE_INSENSITIVE)
                                                    .find();
                                        } catch (Exception exception) {
                                            throw new IllegalStateException(exception);
                                        }
                                    });
                        };

                // Check if the device screen is locked
                assume().withMessage("The device screen is not locked")
                        .that(
                                dumpsysService.test(
                                        "window" /* service to check window state */,
                                        "mDreamingLockscreen=true" /* pattern */))
                        .isTrue();

                // Check if the device is awake
                assume().withMessage("The device screen is not on")
                        .that(
                                dumpsysService.test(
                                        "power" /* service to check screen state */,
                                        "mWakefulness=Awake" /* pattern */))
                        .isTrue();

                // Launch 'PocActivity' to post a private notification
                final String activityName = String.format("%s/.PocActivity", testPackage);
                runAndCheck(device, String.format("am start-activity -n %s", activityName));

                // Without fix, the device displays private notification
                runDeviceTests(new DeviceTestRunOptions(testPackage));
            }
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }
}
