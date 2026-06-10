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

import static com.google.common.truth.TruthJUnit.assume;

import static org.junit.Assume.assumeNoException;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2024_31332 extends NonRootSecurityTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 299931076)
    public void testPocCVE_2024_31332() {
        try {
            // Install test-app
            installPackage("CVE-2024-31332.apk", "-t");

            // Set the 'PocDeviceAdminReceiver' as device-owner using device policy manager
            final String testPkg = "android.security.cts.CVE_2024_31332";
            try (AutoCloseable withPocDeviceAdminReceiverAsDeviceOwner =
                    withPocDeviceAdminReceiverAsDeviceOwner(testPkg)) {
                // Run DeviceTest
                runDeviceTests(new DeviceTestRunOptions(testPkg).setDisableHiddenApiCheck(true));
            }
        } catch (Exception e) {
            assumeNoException(e);
        }
    }

    private AutoCloseable withPocDeviceAdminReceiverAsDeviceOwner(String testPackage)
            throws Exception {
        // Set the 'PocDeviceAdminReceiver' as device-owner using device policy manager
        final ITestDevice device = getDevice();
        final int userId = device.getCurrentUser();
        final String componentName = testPackage + "/.PocDeviceAdminReceiver";
        assume().withMessage("Unable to set device owner")
                .that(device.setDeviceOwner(componentName, userId))
                .isTrue();

        // Return 'AutoCloseable' to remove the 'PocDeviceAdminReceiver' as device-owner
        return () -> device.removeAdmin(componentName, userId);
    }
}
