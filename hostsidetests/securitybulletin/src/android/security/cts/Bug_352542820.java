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

import static com.google.common.truth.TruthJUnit.assume;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.UserUtils;
import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.UserInfo;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;

@RunWith(DeviceJUnit4ClassRunner.class)
public class Bug_352542820 extends NonRootSecurityTestCase {

    @AsbSecurityTest(cveBugId = 352542820)
    @Test
    public void testPocBug_352542820() {
        try {
            final ITestDevice device = getDevice();
            assume().withMessage("Device does not support multiple users")
                    .that(device.isMultiUserSupported())
                    .isTrue();

            // Create a managed user 'bug_352542820_managedUser'.
            final int primaryUserId = device.getCurrentUser();
            final String managedUserName = "bug_352542820_managedUser";
            try (AutoCloseable withManagedUser =
                    new UserUtils.SecondaryUser(device)
                            .managed(primaryUserId)
                            .name(managedUserName)
                            .withUser()) {
                // Fetch the userId of 'bug_352542820_managedUser'
                int managedUserId = -1;
                Map<Integer, UserInfo> mapOfUserInfos = device.getUserInfos();
                for (UserInfo userInfo : mapOfUserInfos.values()) {
                    if (userInfo.userName().equals(managedUserName)) {
                        managedUserId = userInfo.userId();
                        break;
                    }
                }
                assume().withMessage("UserId not found for the managed user")
                        .that(managedUserId)
                        .isNotEqualTo(-1);

                // Install helper-app for managed user
                installPackage("BUG-352542820-helper.apk", "-t", "--user " + managedUserId);

                // Set the 'PocDeviceAdminReceiver' of helper app as profile-owner of organisation
                // owned work profile using device policy manager
                final String helperPkg = "android.security.cts.BUG_352542820_helper";
                try (AutoCloseable withPocDeviceAdminReceiverAsProfileOwner =
                        withPocDeviceAdminReceiverAsProfileOwner(
                                helperPkg, device, managedUserId)) {
                    // launch UserSettings Activity in managed user
                    runAndCheck(
                            device,
                            String.format(
                                    "am start-activity --user %d"
                                        + " 'com.android.settings/.Settings$UserSettingsActivity'",
                                    managedUserId));

                    // Install test-app for primary user
                    installPackage("BUG-352542820-test.apk", "--user " + primaryUserId);
                    final String testPkg = "android.security.cts.BUG_352542820_test";
                    runDeviceTests(new DeviceTestRunOptions(testPkg));
                }
            }
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }

    private AutoCloseable withPocDeviceAdminReceiverAsProfileOwner(
            String pkgName, ITestDevice device, int userId) throws Exception {
        // Set the 'PocDeviceAdminReceiver' as profile-owner on organisation owned device using
        // device policy manager
        final String componentName = String.format("%s/.PocDeviceAdminReceiver", pkgName);
        runAndCheck(
                device, String.format("dpm set-profile-owner --user %d %s", userId, componentName));
        runAndCheck(
                device,
                String.format(
                        "dpm mark-profile-owner-on-organization-owned-device --user %d %s",
                        userId, componentName));

        // Return 'AutoCloseable' to remove the 'PocDeviceAdminReceiver' as profile-owner
        return () -> {
            try {
                device.removeAdmin(componentName, userId);
            } catch (Exception e) {
                // Ignore unintended exceptions
            }
        };
    }
}
