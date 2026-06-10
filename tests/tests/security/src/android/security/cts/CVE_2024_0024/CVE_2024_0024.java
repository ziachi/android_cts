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

package android.security.cts.CVE_2024_0024;

import static android.Manifest.permission.CREATE_USERS;
import static android.os.Environment.buildPath;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;
import static com.android.sts.common.SystemUtil.poll;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.UserInfo;
import android.os.Build;
import android.os.Environment;
import android.os.UserManager;
import android.platform.test.annotations.AsbSecurityTest;
import android.security.cts.R;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.Until;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
public class CVE_2024_0024 extends StsExtraBusinessLogicTestCase {
    private static final int MAX_UNSIGNED_SHORT = (1 << 16) - 1;
    UserManager mUserManager = null;
    UserInfo mUserInfo = null;

    @AsbSecurityTest(cveBugId = 293602317)
    @Test
    public void grantSmsPermission() {
        try {
            final Instrumentation instrumentation = getInstrumentation();
            final Context context = instrumentation.getContext();
            mUserManager = context.getSystemService(UserManager.class);

            // Check if the device supports multiple users
            assume().withMessage("This device does not support multiple users")
                    .that(mUserManager.supportsMultipleUsers())
                    .isTrue();

            // Launch pocActivity
            context.startActivity(
                    new Intent(context, PocActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));

            // Fetch and add the flag 'RECEIVER_NOT_EXPORTED' for 'TIRAMISU' and above versions to
            // keep the code consistent
            final int requiredFlag =
                    Build.VERSION.SDK_INT >= 33 /* TIRAMISU */
                            ? (int) Context.class.getField("RECEIVER_NOT_EXPORTED").get(context)
                            : 0;

            // Check if there is an exception while calling vulnerable function in PocActivity
            // Register a broadcast receiver to get broadcast from PocActivity
            CompletableFuture<String> broadcastReceived = new CompletableFuture<String>();
            context.registerReceiver(
                    new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            try {
                                broadcastReceived.complete(
                                        intent.getStringExtra(
                                                context.getString(
                                                        R.string.CVE_2024_0024_exception)));
                            } catch (Exception e) {
                                // Ignore exceptions here
                            }
                        }
                    },
                    new IntentFilter(context.getString(R.string.CVE_2024_0024_action)),
                    requiredFlag);

            // Wait for broadcast from PocActivity
            String exception = broadcastReceived.get(10_000L /* timeout */, TimeUnit.MILLISECONDS);
            assume().withMessage("Exception occurred in PocActivity " + exception)
                    .that(exception)
                    .isNull();

            // Click ok
            UiDevice uiDevice = UiDevice.getInstance(instrumentation);
            BySelector okButtonSelector = By.text(Pattern.compile("ok", Pattern.CASE_INSENSITIVE));
            uiDevice.wait(Until.hasObject(okButtonSelector), 5_000L /* timeout */);
            uiDevice.findObject(okButtonSelector).click();

            // Poll till user CVE_2024_0024_user is created and get it's user info
            assume().withMessage("CVE_2024_0024_user was not found")
                    .that(
                            poll(
                                    () -> {
                                        return runWithShellPermissionIdentity(
                                                () -> {
                                                    final List<UserInfo> list =
                                                            mUserManager.getUsers();
                                                    for (UserInfo info : list) {
                                                        if (info.name.contains(
                                                                "CVE_2024_0024_user")) {
                                                            mUserInfo = info;
                                                            return true;
                                                        }
                                                    }
                                                    return false;
                                                },
                                                CREATE_USERS);
                                    }))
                    .isTrue();

            assume().withMessage("Unable to get created user's info").that(mUserInfo).isNotNull();
            assertWithMessage(
                            "Device is vulnerable to b/293602317 !!"
                                    + " Create and persist a new secondary user without any"
                                    + " restrictions via a super large accountType")
                    .that(
                            poll(
                                    () -> {
                                        return (buildPath(
                                                                Environment
                                                                        .getDataSystemDirectory(),
                                                                "users",
                                                                mUserInfo.id + ".xml")
                                                        .length()
                                                > MAX_UNSIGNED_SHORT - 1);
                                    }))
                    .isFalse();
        } catch (Exception e) {
            assume().that(e).isNull();
        } finally {
            try {
                mUserManager.removeUser(mUserInfo.id);
            } catch (Exception ignore) {
                // Ignore unintended exceptions
            }
        }
    }
}
