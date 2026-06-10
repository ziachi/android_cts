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

import static com.google.common.truth.TruthJUnit.assume;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.UserUtils;
import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2025_0087 extends NonRootSecurityTestCase {

    @AsbSecurityTest(cveBugId = 333681693)
    @Test
    public void testPocCVE_2025_0087() {
        try {
            // Install helper-app in the primary user.
            final ITestDevice device = getDevice();
            installPackage("CVE-2025-0087-helperApp.apk");

            // Create a secondary user using 'UserUtils'.
            final UserUtils.SecondaryUser secondaryUser = new UserUtils.SecondaryUser(device);
            final int primaryUserId = device.getCurrentUser();
            try (AutoCloseable asSecondaryUser =
                    secondaryUser.managed(primaryUserId).name("cve_2025_0087_user").withUser()) {
                // Install test-app in the secondary user.
                final int secondaryUserId = secondaryUser.getTestUserId();
                installPackageAsUser(
                        "CVE-2025-0087-testApp.apk", true /* grantPermission */, secondaryUserId);

                // Run the test
                runDeviceTests(
                        new DeviceTestRunOptions("android.security.cts.CVE_2025_0087_testApp")
                                .setUserId(secondaryUserId)
                                .addInstrumentationArg(
                                        "primaryUserId", String.valueOf(primaryUserId))
                                .addInstrumentationArg(
                                        "secondaryUserId", String.valueOf(secondaryUserId)));
            }
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }
}
