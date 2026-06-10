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

package android.packageinstaller.criticaluserjourney.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.content.ComponentName;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.test.uiautomator.By;

import com.android.bedstead.accounts.annotations.EnsureHasNoAccounts;
import com.android.bedstead.enterprise.annotations.EnsureHasNoDeviceOwner;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.UserType;
import com.android.bedstead.harrier.annotations.NotificationsTest;
import com.android.bedstead.multiuser.annotations.RequireRunOnSystemUser;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.devicepolicy.DeviceOwner;
import com.android.bedstead.nene.notifications.Notification;
import com.android.bedstead.nene.notifications.NotificationListener;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;

/** Tests for PackageInstaller CUJs to uninstall a device admin app. */
@RunWith(BedsteadJUnit4.class)
@AppModeFull
@RequireRunOnSystemUser
@EnsureHasNoDeviceOwner
@EnsureHasNoAccounts(onUser = UserType.ANY)
public class UninstallDeviceAdminAppTest extends UninstallationTestBase {

    private static final long CLEAR_NOTIFICATION_POLL_TIMEOUT_MS = 100L;
    private static final long NOTIFICATION_POLL_TIMEOUT_MS = 3000L;
    private static final String UNINSTALL_FAILURE_NOTIFICATION_CHANNEL_ID = "uninstall failure";

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private DeviceOwner mDeviceOwner = null;

    @Override
    protected boolean shouldInstallTestAppInTestBaseSetup() {
        // In the test case, it needs the device admin app, don't need to install the test
        // app first.
        return false;
    }

    @Before
    @Override
    public void setup() throws Exception {
        super.setup();
        try {
            installPackage(DEVICE_ADMIN_APK_NAME);
            assertDeviceAdminAppIsInstalled();

            mDeviceOwner = TestApis.devicePolicy().setDeviceOwner(new ComponentName(
                    DEVICE_ADMIN_APP_PACKAGE_NAME, DEVICE_ADMIN_APP_RECEIVER_NAME));
            assertThat(TestApis.devicePolicy().getDeviceOwner()).isEqualTo(mDeviceOwner);
        } catch (Exception ex) {
            if (mDeviceOwner != null) {
                mDeviceOwner.remove();
            }
            fail("Can NOT set the device admin app :" + ex);
        }
    }

    @After
    @Override
    public void tearDown() throws Exception {
        if (mDeviceOwner != null) {
            mDeviceOwner.remove();
        }
        uninstallPackage(DEVICE_ADMIN_APP_PACKAGE_NAME);
        super.tearDown();
    }

    @Test
    @NotificationsTest
    public void actionDeleteIntent_okButton_fail() throws Exception {
        startUninstallationViaIntentActionDelete(DEVICE_ADMIN_APP_PACKAGE_NAME);

        waitForUiIdle();

        clearAllPackageInstallerUninstallFailureNotification();

        clickUninstallDeviceAdminAppOkButton();

        assertCanNotUninstallNotificationAndClearIt();
        assertDeviceAdminAppIsInstalled();
    }

    @Test
    @NotificationsTest
    public void uninstallPackageIntent_okButton_fail() throws Exception {
        startUninstallationViaIntentActionUninstallPackage(DEVICE_ADMIN_APP_PACKAGE_NAME);

        waitForUiIdle();

        clearAllPackageInstallerUninstallFailureNotification();

        clickUninstallDeviceAdminAppOkButton();

        assertCanNotUninstallNotificationAndClearIt();
        assertDeviceAdminAppIsInstalled();
    }

    @Test
    public void installerApi_noDeletePackages_okButton_fail() throws Exception {
        startUninstallationViaPackageInstallerApi(DEVICE_ADMIN_APP_PACKAGE_NAME);

        waitForUiIdle();

        clickUninstallDeviceAdminAppOkButton();

        assertUninstallFailureBlocked();
        assertDeviceAdminAppIsInstalled();
    }

    @Test
    public void installerApi_hasDeletePackages_differentInstaller_okButton_fail() throws Exception {
        startUninstallationViaPackageInstallerApiWithDeletePackages(/* isSameInstaller= */ false,
                DEVICE_ADMIN_APP_PACKAGE_NAME);

        waitForUiIdle();

        clickUninstallDeviceAdminAppOkButton();

        assertUninstallFailureBlocked();
        assertDeviceAdminAppIsInstalled();
    }

    @Test
    public void installerApi_hasDeletePackages_sameInstaller_noConfirmedDialog_fail()
            throws Exception {
        // if the installer is not the test case, even if the test is granted the DELETE_PACKAGES
        // permission, it also needs the user confirmation to approve the uninstallation.
        // Set the test case to be the installer of the test app
        installPackage(DEVICE_ADMIN_APK_NAME, getContext().getPackageName());
        assertDeviceAdminAppIsInstalled();

        startUninstallationViaPackageInstallerApiWithDeletePackages(/* isSameInstaller= */ true,
                DEVICE_ADMIN_APP_PACKAGE_NAME);

        assertUninstallFailureBlocked();
        assertDeviceAdminAppIsInstalled();
    }

    private static void clearAllPackageInstallerUninstallFailureNotification() throws Exception {
        boolean result = clearPackageInstallerUninstallFailureNotification();
        while (result) {
            result = clearPackageInstallerUninstallFailureNotification();
        }
    }

    /**
     * Clear the notifications that the package name is {@link #getPackageInstallerPackageName()}
     * and the channel id is uninstallation failure. Return {@code true} if there is matched
     * notification. Return {@code false} if there is no matched notification.
     */
    private static boolean clearPackageInstallerUninstallFailureNotification() throws Exception {
        try (NotificationListener notificationListener =
                     TestApis.notifications().createListener()) {
            final Notification notification = getPackageInstallerUninstallFailureNotification(
                    notificationListener, CLEAR_NOTIFICATION_POLL_TIMEOUT_MS);
            Log.d(TAG, "notification = " + notification);

            if (notification != null) {
                Log.d(TAG, "notification = " + notification + ", when= "
                        + notification.getNotification().when);
                notification.cancel();
                return true;
            }
        }
        return false;
    }

    private static void assertCanNotUninstallNotificationAndClearIt() throws Exception {
        try (NotificationListener notificationListener =
                     TestApis.notifications().createListener()) {
            final Notification notification = getPackageInstallerUninstallFailureNotification(
                    notificationListener, NOTIFICATION_POLL_TIMEOUT_MS);
            assertThat(notification).isNotNull();
            Log.d(TAG, "notification = " + notification + ", when= "
                    + notification.getNotification().when);

            // Cancel the notification
            notification.cancel();
        }
    }

    @Nullable
    private static Notification getPackageInstallerUninstallFailureNotification(
            NotificationListener notificationListener, long timeoutMs) {
        return notificationListener.query()
                .wherePackageName().isEqualTo(getPackageInstallerPackageName())
                .whereNotification().channelId().isEqualTo(
                        UNINSTALL_FAILURE_NOTIFICATION_CHANNEL_ID)
                .poll(Duration.ofMillis(timeoutMs));
    }

    private static void assertDeviceAdminAppIsInstalled() {
        assertThat(isInstalled(DEVICE_ADMIN_APP_PACKAGE_NAME)).isTrue();
    }

    /**
     * Click the OK button and wait for the uninstallation dialog for device admin app
     * to disappear.
     */
    private static void clickUninstallDeviceAdminAppOkButton() throws Exception {
        findPackageInstallerObject(DEVICE_ADMIN_APP_PACKAGE_LABEL);
        findPackageInstallerObject(By.textContains(UNINSTALL_LABEL), /* checkNull= */ true);
        clickAndWaitForNewWindow(findPackageInstallerObject(BUTTON_OK_LABEL));
    }
}
