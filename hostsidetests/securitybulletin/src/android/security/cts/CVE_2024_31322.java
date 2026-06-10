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

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;
import com.android.tradefed.util.RunUtil;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2024_31322 extends NonRootSecurityTestCase {

    @AsbSecurityTest(cveBugId = 326485767)
    @Test
    public void testPocCVE_2024_31322() {
        try {
            // Enable 'PocAccessibilityService' before installing app.
            final String testPkg = "android.security.cts.CVE_2024_31322";
            try (AutoCloseable withAccessibilityEnabled =
                    withAccessibilityEnabled(testPkg + "/.PocAccessibilityService")) {

                // Without fix, the permission 'BIND_ACCESSIBILITY_SERVICE' gets granted.
                installPackage("CVE-2024-31322.apk");

                // Run DeviceTest
                runDeviceTests(new DeviceTestRunOptions(testPkg));
            }
        } catch (Exception e) {
            assumeNoException(e);
        }
    }

    private AutoCloseable withAccessibilityEnabled(String className)
            throws DeviceNotAvailableException, IllegalStateException {
        final String namespace = "secure";
        final String key = "enabled_accessibility_services";
        final ITestDevice device = getDevice();
        String oldValue = device.getSetting(namespace, key);
        device.setSetting(namespace, key, className);

        // Wait till the value is set.
        // Setting is necessary for checking vulnerability but it is possibly affected by the
        // fix hence not adding an assumption failure check here.
        long startTime = System.currentTimeMillis();
        do {
            if (device.getSetting(namespace, key).contains(className)) {
                break;
            }
            RunUtil.getDefault().sleep(1_000L);
        } while (System.currentTimeMillis() - startTime <= 5_000L);

        // Restore oldValue
        return () -> {
            device.setSetting(namespace, key, oldValue);
        };
    }
}
