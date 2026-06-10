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

package android.security.cts.CVE_2024_23713;

import static android.Manifest.permission.MANAGE_NOTIFICATION_LISTENERS;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;
import static com.android.sts.common.SystemUtil.poll;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Environment;
import android.platform.test.annotations.AsbSecurityTest;
import android.security.cts.R;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class CVE_2024_23713 extends StsExtraBusinessLogicTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 305926929)
    public void testPocCVE_2024_23713() {
        try {
            File systemDir = new File(Environment.getDataDirectory(), "system");
            File notificationPolicyFile = new File(systemDir, "notification_policy.xml");
            assume().withMessage("File not found!").that(notificationPolicyFile.exists()).isTrue();
            long lengthBeforeWrite = notificationPolicyFile.length();

            // Fetch and add the flag 'RECEIVER_NOT_EXPORTED' for 'TIRAMISU' and above versions to
            // keep the code consistent
            final Context context = getApplicationContext();
            final int requiredFlag =
                    Build.VERSION.SDK_INT >= 33 /* TIRAMISU */
                            ? (int) Context.class.getField("RECEIVER_NOT_EXPORTED").get(context)
                            : 0;

            // Check if there is an exception while calling vulnerable function in
            // PocNotificationListenerService
            // Register a broadcast receiver to get broadcast from PocNotificationListenerService
            CompletableFuture<String> broadcastReceived = new CompletableFuture<>();
            context.registerReceiver(
                    new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            try {
                                broadcastReceived.complete(
                                        intent.getStringExtra(
                                                context.getString(
                                                        R.string.CVE_2024_23713_exception)));
                            } catch (Exception e) {
                                // Ignore exceptions here
                            }
                        }
                    },
                    new IntentFilter(context.getString(R.string.CVE_2024_23713_action)),
                    requiredFlag);

            // Enable the notification listener for the PocNotificationListenerService, the method
            // onListenerConnected of the PocNotificationListenerService will be invoked.
            NotificationManager notificationManager =
                    context.getSystemService(NotificationManager.class);
            runWithShellPermissionIdentity(
                    () -> {
                        String pkgName = context.getPackageName();
                        ComponentName componentName =
                                new ComponentName(
                                        pkgName,
                                        pkgName + ".CVE_2024_23713.PocNotificationListenerService");
                        notificationManager.setNotificationListenerAccessGranted(
                                componentName, true /* granted */, false /* userSet */);
                    },
                    MANAGE_NOTIFICATION_LISTENERS);
            assume().withMessage("Listener not enabled")
                    .that(notificationManager.isNotificationPolicyAccessGranted())
                    .isTrue();

            // Wait for broadcast from PocNotificationListenerService.
            String exception = broadcastReceived.get(10_000L /* timeout */, TimeUnit.MILLISECONDS);
            assume().withMessage(
                            "Exception occurred in PocNotificationListenerService " + exception)
                    .that(exception)
                    .isNull();

            // Fail the test if change in size of notification_policy.xml is greater than or equal
            // to UTF8LengthLimit
            assertWithMessage(
                            "Device is vulnerable to b/305926929!!"
                                    + " a malicious app can cause disallowed notification"
                                    + " listeners to be enabled after a reboot.")
                    .that(
                            poll(
                                    () -> {
                                        return (notificationPolicyFile.length() - lengthBeforeWrite)
                                                >= context.getResources()
                                                                .getInteger(
                                                                        R.integer.CVE_2024_23713_Limit);
                                    }))
                    .isFalse();
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }
}
