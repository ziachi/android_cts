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

import static org.junit.Assume.assumeNoException;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class BUG_321707289 extends NonRootSecurityTestCase {

    @AsbSecurityTest(cveBugId = 321707289)
    @Test
    public void testPocBUG_321707289() {
        try {
            // Install helper-app with 'BIND_NOTIFICATION_LISTENER_SERVICE' permission
            installPackage("BUG-321707289-helper.apk");

            // Enable 'PocNotificationListenerService' for helper-app
            final String testPkg = "android.security.cts.BUG_321707289";
            runAndCheck(
                    getDevice(),
                    "cmd notification allow_listener "
                            + testPkg
                            + "/.PocNotificationListenerService");

            // Installing test-app without 'BIND_NOTIFICATION_LISTENER_SERVICE' permission.
            // Since test-app and helper-app have the same package and component name, hence
            // installing test-app will update the helper-app
            // Without fix, the permission 'BIND_NOTIFICATION_LISTENER_SERVICE' gets granted
            // automatically for the test-app
            installPackage("BUG-321707289-test.apk");

            // Run DeviceTest
            runDeviceTests(new DeviceTestRunOptions(testPkg));
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
