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

import com.android.sts.common.UserUtils.SecondaryUser;
import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2025_26430 extends NonRootSecurityTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 372895305)
    public void testPocCVE_2025_26430() {
        try {
            // Check if the device supports multiple users or not
            final ITestDevice device = getDevice();
            assume().withMessage("This device does not support multiple users")
                    .that(device.isMultiUserSupported())
                    .isTrue();

            // Install test app in primary user
            final int primaryUserId = device.getCurrentUser();
            installPackageAsUser("CVE-2025-26430.apk", false /* grantPermission */, primaryUserId);

            // Create new user
            final SecondaryUser secondaryUser = new SecondaryUser(device);
            try (AutoCloseable asSecondaryUser =
                    secondaryUser
                            .name("CVE_2025_26430_user")
                            .doSwitch()
                            .doSkipSetupWizard()
                            .withUser()) {
                // Install test-app in the secondary user.
                final int secondaryUserId = secondaryUser.getTestUserId();
                installPackageAsUser(
                        "CVE-2025-26430.apk", true /* grantPermission */, secondaryUserId);
                runDeviceTests(
                        new DeviceTestRunOptions("android.security.cts.CVE_2025_26430")
                                .addInstrumentationArg(
                                        "primaryUserId", String.valueOf(primaryUserId)));
            }
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }
}
