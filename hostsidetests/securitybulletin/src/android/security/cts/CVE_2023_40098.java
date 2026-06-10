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

import static org.junit.Assume.assumeNoException;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.UserUtils.SecondaryUser;
import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2023_40098 extends NonRootSecurityTestCase {

    @AsbSecurityTest(cveBugId = 288896269)
    @Test
    public void testPocCVE_2023_40098() {
        try {
            // Create a secondary user 'cve_2023_40098_user'
            final SecondaryUser secondaryUser = new SecondaryUser(getDevice());
            try (AutoCloseable withSecondaryUser =
                    secondaryUser
                            .name("cve_2023_40098_user")
                            .doSwitch()
                            .doSkipSetupWizard()
                            .withUser()) {

                // Install app for 'cve_2023_40098_user'
                installPackageAsUser(
                        "CVE-2023-40098.apk",
                        true /* grantPermission */,
                        secondaryUser.getTestUserId());

                // Run DeviceTest
                runDeviceTests(
                        new DeviceTestRunOptions(
                                "android.security.cts.CVE_2023_40098" /* testPkg */));
            }
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
