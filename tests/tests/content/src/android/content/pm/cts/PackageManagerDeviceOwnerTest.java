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

package android.content.pm.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.os.UserManager;
import android.platform.test.annotations.AppModeFull;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.bedstead.enterprise.annotations.EnsureHasNoDeviceOwner;
import com.android.bedstead.multiuser.annotations.RequireRunOnSystemUser;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.devicepolicy.DeviceOwner;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@AppModeFull
@RunWith(AndroidJUnit4.class)
@RequireRunOnSystemUser
@EnsureHasNoDeviceOwner
public class PackageManagerDeviceOwnerTest {
    private static final String DEVICE_ADMIN_TEST_APP_PACKAGE_NAME =
            "android.content.cts.deviceadmintestapp";
    private static final String DEVICE_ADMIN_TEST_APP_CLASS_NAME =
            "android.content.cts.deviceadmintestapp.TestDeviceAdminReceiver";
    private static final String SAMPLE_APK_BASE = "/data/local/tmp/cts/content/";
    private static final String DEVICE_ADMIN_TEST_APP_APK_PATH =
            SAMPLE_APK_BASE + "CtsDeviceAdminTestApp.apk";

    @Before
    public void setup() throws Exception {
        assumeTrue("Device is not supported", isDeviceSupported());
    }

    @After
    public void uninstall() {
        uninstallPackage(DEVICE_ADMIN_TEST_APP_PACKAGE_NAME);
        assertThat(isAppInstalledForUser(DEVICE_ADMIN_TEST_APP_PACKAGE_NAME,
                ActivityManager.getCurrentUser())).isFalse();
    }

    @Test
    public void testInstallAdminAppAsInstantShouldFail() {
        final int currentUser = ActivityManager.getCurrentUser();
        installPackageAsUser(DEVICE_ADMIN_TEST_APP_APK_PATH, currentUser);
        assertTrue(isAppInstalledForUser(DEVICE_ADMIN_TEST_APP_PACKAGE_NAME, currentUser));

        DeviceOwner deviceOwner = null;
        try {
            deviceOwner = TestApis.devicePolicy().setDeviceOwner(
                    new ComponentName(DEVICE_ADMIN_TEST_APP_PACKAGE_NAME,
                            DEVICE_ADMIN_TEST_APP_CLASS_NAME));
            assertThat(TestApis.devicePolicy().getDeviceOwner()).isEqualTo(deviceOwner);

            String result = SystemUtil.runShellCommand(
                    "pm install-existing --instant --user " + currentUser + " "
                            + DEVICE_ADMIN_TEST_APP_PACKAGE_NAME);
            assertThat(result).contains("NameNotFoundException");
        } catch (Exception e) {
            fail("testInstallAdminAppAsInstantShouldFail unexpected Exception: " + e);
        } finally {
            if (deviceOwner != null) {
                deviceOwner.remove();
            }
        }
    }

    private void uninstallPackage(String packageName) {
        SystemUtil.runShellCommand(
                String.format("pm uninstall %s", packageName));
    }

    private static void installPackageAsUser(String apkPath, int userId) {
        assertThat(SystemUtil.runShellCommand(
                String.format("pm install -t -g --user %s %s", userId, apkPath)))
                .isEqualTo(String.format("Success\n"));
    }

    private static boolean isAppInstalledForUser(String packageName, int userId) {
        return Arrays.stream(SystemUtil.runShellCommand(
                                String.format("pm list packages --user %s %s", userId, packageName))
                        .split("\\r?\\n"))
                .anyMatch(pkg -> pkg.equals(String.format("package:%s", packageName)));
    }

    private static boolean isDeviceSupported() {
        return !UserManager.isHeadlessSystemUserMode();
    }
}
