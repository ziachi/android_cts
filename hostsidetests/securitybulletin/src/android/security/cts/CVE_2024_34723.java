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

import static com.android.sts.common.SystemUtil.withSetting;

import static org.junit.Assume.assumeNoException;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2024_34723 extends NonRootSecurityTestCase {

    @AsbSecurityTest(cveBugId = 317048338)
    @Test
    public void testPocCVE_2024_34723() {
        // Enable 'hidden_api_policy' to access hidden APIs in 'PocService'
        try (AutoCloseable withHiddenApiEnabled =
                withSetting(getDevice(), "global", "hidden_api_policy", "1")) {
            // Install PoC
            installPackage("CVE-2024-34723-maliciousApp.apk", "-g");

            // Install test-app and run the DeviceTest
            installPackage("CVE-2024-34723-testApp.apk");
            runDeviceTests(new DeviceTestRunOptions("android.security.cts.CVE_2024_34723"));
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
