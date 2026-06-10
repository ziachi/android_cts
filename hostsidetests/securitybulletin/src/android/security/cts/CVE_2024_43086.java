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
import static com.android.sts.common.DumpsysUtils.isActivityVisible;
import static com.android.sts.common.SystemUtil.poll;

import static com.google.common.truth.TruthJUnit.assume;

import static org.junit.Assume.assumeNoException;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2024_43086 extends NonRootSecurityTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 343440463)
    public void testPocCVE_2024_43086() {
        try {
            // Install the helper-app
            installPackage("CVE-2024-43086-helper-1.apk");

            // Launch 'PocActivity' to add a new account to 'AccountManager'
            final String activityName = "android.security.cts.CVE_2024_43086_helper/.PocActivity";
            runAndCheck(getDevice(), String.format("am start-activity -n %s", activityName));
            assume().withMessage("Unable to launch 'PocActivity' !!")
                    .that(
                            poll(
                                    () -> {
                                        try {
                                            return isActivityVisible(getDevice(), activityName);
                                        } catch (Exception exception) {
                                            throw new IllegalStateException(exception);
                                        }
                                    }))
                    .isTrue();

            // Update the helper-app
            installPackage("CVE-2024-43086-helper-2.apk");

            // Install the test-app and run the DeviceTest
            installPackage("CVE-2024-43086-test.apk");
            runDeviceTests(new DeviceTestRunOptions("android.security.cts.CVE_2024_43086_test"));
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }
}
