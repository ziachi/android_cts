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

package android.security.cts.BUG_352542820_test;

import static android.Manifest.permission.CREATE_USERS;
import static android.provider.Settings.ACTION_SETTINGS;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;
import static com.android.sts.common.SystemUtil.DEFAULT_MAX_POLL_TIME_MS;
import static com.android.sts.common.SystemUtil.DEFAULT_POLL_TIME_MS;
import static com.android.sts.common.SystemUtil.poll;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.os.UserManager;
import android.widget.Button;

import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.compatibility.common.util.ThrowingSupplier;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
public class DeviceTest {

    @Test
    public void testPocBUG_352542820() {
        try {
            // Check if the device supports multiple users or not
            final Instrumentation instrumentation = getInstrumentation();
            final Context context = instrumentation.getContext();
            final UserManager userManager = context.getSystemService(UserManager.class);
            assume().withMessage("This device does not support multiple users")
                    .that(userManager.supportsMultipleUsers())
                    .isTrue();

            // Fetch overflow menu description
            final PackageManager packageManager = context.getPackageManager();
            final Resources androidResources = packageManager.getResourcesForApplication("android");
            final String overflowMenuDescription =
                    androidResources.getString(
                            androidResources.getIdentifier(
                                    "action_menu_overflow_description", "string", "android"));
            assume().withMessage("Overflow menu description is empty")
                    .that(overflowMenuDescription.trim())
                    .isNotEmpty();

            // Click on Overflow menu
            final String settingPkgName = querySettingPkgName(packageManager);
            final UiDevice uiDevice = UiDevice.getInstance(instrumentation);
            boolean isOverflowMenuVisible =
                    findUiObject(
                            uiDevice,
                            By.pkg(Pattern.compile(settingPkgName, Pattern.CASE_INSENSITIVE))
                                    .desc(
                                            Pattern.compile(
                                                    overflowMenuDescription,
                                                    Pattern.CASE_INSENSITIVE)),
                            true /* clickRequired */);

            // Overflow menu is not visible with fix
            if (isOverflowMenuVisible) {
                // Fetch remove user menu text
                final Resources settingsResources =
                        packageManager.getResourcesForApplication(settingPkgName);
                final String removeUserMenu =
                        settingsResources.getString(
                                settingsResources.getIdentifier(
                                        "user_remove_user_menu", "string", settingPkgName));
                assume().withMessage("removeUserMenuText is empty")
                        .that(removeUserMenu.trim())
                        .isNotEmpty();

                // Click on remove user menu
                final String removeUserMenuWithManagedUser =
                        String.format(removeUserMenu, "bug_352542820_managedUser");
                assume().withMessage(removeUserMenuWithManagedUser + " not found")
                        .that(
                                findUiObject(
                                        UiDevice.getInstance(instrumentation),
                                        By.text(
                                                Pattern.compile(
                                                        removeUserMenuWithManagedUser,
                                                        Pattern.CASE_INSENSITIVE)),
                                        true /* clickRequired */))
                        .isTrue();

                // Fetch delete button text
                final String deleteButton =
                        settingsResources.getString(
                                settingsResources.getIdentifier(
                                        "user_delete_button", "string", settingPkgName));
                assume().withMessage("deleteButton text is empty")
                        .that(deleteButton.trim())
                        .isNotEmpty();

                // Click on Delete button
                assume().withMessage("Delete button not found")
                        .that(
                                findUiObject(
                                        UiDevice.getInstance(instrumentation),
                                        By.clazz(Button.class)
                                                .text(
                                                        Pattern.compile(
                                                                deleteButton,
                                                                Pattern.CASE_INSENSITIVE)),
                                        true /* clickRequired */))
                        .isTrue();

                // Fail the test if the managed user profile is deleted.
                // Wait for the managed user profile to get deleted.
                // Managed user profile is deleted in 20 seconds.
                // Keeping 90 seconds for slow devices.
                ThrowingSupplier<Boolean> checkManagedUserDeleted =
                        () -> {
                            List<UserInfo> list = userManager.getUsers();
                            for (UserInfo info : list) {
                                if (info.toString().contains("bug_352542820_managedUser")) {
                                    return false;
                                }
                            }
                            return true;
                        };
                final boolean managedUserDeleted =
                        poll(
                                () ->
                                        runWithShellPermissionIdentity(
                                                checkManagedUserDeleted, CREATE_USERS),
                                DEFAULT_POLL_TIME_MS /*  pollingTime */,
                                DEFAULT_MAX_POLL_TIME_MS * 3 /* maxPollingTime */);
                assertWithMessage(
                                "Device is vulnerable to b/352542820, Organization-owned work"
                                    + " profiles can be removed even though policy should prevent"
                                    + " it")
                        .that(managedUserDeleted)
                        .isFalse();
            }

        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }

    private boolean findUiObject(UiDevice uiDevice, BySelector selector, boolean clickRequired)
            throws Exception {
        final UiObject2 uiobject = uiDevice.wait(Until.findObject(selector), 5000 /* timeout */);
        if (clickRequired && (uiobject != null)) {
            poll(() -> (uiobject.isEnabled()));
            uiobject.click();
        }
        return (uiobject != null);
    }

    private String querySettingPkgName(PackageManager packageManager) {
        ComponentName componentName = new Intent(ACTION_SETTINGS).resolveActivity(packageManager);
        return componentName != null ? componentName.getPackageName() : "com.android.settings";
    }
}
