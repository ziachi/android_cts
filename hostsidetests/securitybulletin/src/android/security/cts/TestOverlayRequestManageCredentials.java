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
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class TestOverlayRequestManageCredentials extends NonRootSecurityTestCase {
    private static final String TEST_PKG =
            "android.security.cts.overlay_request_manage_credentials";

    @AsbSecurityTest(cveBugId = 205150380)
    @Test
    public void testOverlayDisallowed() {
        try {
            // Install the test app
            installPackage("OverlayRequestManageCredentials.apk");

            // Run the device-side test
            runDeviceTests(TEST_PKG, TEST_PKG + ".DeviceTest", "testPocBug_205150380");
        } catch (Exception e) {
            assumeNoException(e);
        }
    }

    @AsbSecurityTest(cveBugId = 302431573)
    @Test
    public void testOverlayBypassInLandscapeMode() {
        try {
            // Install the test app
            installPackage("OverlayRequestManageCredentials.apk");

            // Run the device-side test
            runDeviceTests(TEST_PKG, TEST_PKG + ".DeviceTest", "testPocBug_302431573");
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
