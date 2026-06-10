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
import static com.android.sts.common.SystemUtil.poll;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import static java.util.regex.Pattern.CASE_INSENSITIVE;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2024_43081 extends NonRootSecurityTestCase {

    @AsbSecurityTest(cveBugId = 341256043)
    @Test
    public void testPocCVE_2024_43081() {
        try {
            final ITestDevice device = getDevice();

            // Install test-app
            installPackage("CVE-2024-43081.apk", "-t");

            // Set the 'PocAdminReceiver' as device-owner using device policy manager
            final String testPkg = "android.security.cts.CVE_2024_43081";
            try (AutoCloseable removeDeviceOwner = asDeviceOwnerAndInstantApp(testPkg, device)) {

                // Fail if the test-app gets reinstalled as an instant app
                assertWithMessage(
                                "Device is vulnerable to b/341256043 !!! Device admin apps can be"
                                        + " reinstalled as instant apps.")
                        .that(
                                poll(
                                        () -> {
                                            try {
                                                return getParsedDumpsys(
                                                                device,
                                                                "package " + testPkg,
                                                                "instant=true",
                                                                CASE_INSENSITIVE)
                                                        .find();
                                            } catch (Exception e) {
                                                return false;
                                            }
                                        }))
                        .isFalse();
            }
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }

    private AutoCloseable asDeviceOwnerAndInstantApp(String testPackage, ITestDevice device)
            throws Exception {
        // Set the 'PocAdminReceiver' as device-owner using device policy manager
        final int userId = device.getCurrentUser();
        final String componentName = testPackage + "/.PocAdminReceiver";
        assume().withMessage("Unable to set device owner")
                .that(device.setDeviceOwner(componentName, userId))
                .isTrue();

        // Reinstall as an instant app
        try {
            runAndCheck(device, "pm install-existing --instant " + testPackage);
        } catch (Exception e) {
            // Ignore exception thrown with fix.
        }

        // Return 'AutoCloseable' to remove the 'PocAdminReceiver' as device-owner
        return () -> device.removeAdmin(componentName, userId);
    }
}
