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

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.android.sts.common.SystemUtil.poll;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.app.Notification;
import android.app.Notification.Action;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.IIntentReceiver;
import android.content.IIntentSender;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.IBinder;
import android.platform.test.annotations.AsbSecurityTest;
import android.service.notification.StatusBarNotification;
import android.util.ArraySet;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class CVE_2024_49743 extends StsExtraBusinessLogicTestCase {
    Notification mPostedNotification = null;

    @Test
    @AsbSecurityTest(cveBugId = 305695605)
    public void testPocCVE_2024_49743() {
        try {
            // Create a public notification
            final Context context = getApplicationContext();
            final String channelId = "CVE_2024_49743_channelId";
            final Icon icon =
                    Icon.createWithData(new byte[0] /* data */, 0 /* offset */, 0 /* length */);
            final Notification publicNotification =
                    new Notification.Builder(context, channelId).setSmallIcon(icon).build();

            // Add a public notification to 'mAllowedDataTypes' of 'RemoteInput' object
            final RemoteInput remoteInput =
                    new RemoteInput.Builder("CVE_2024_49743_resultKey" /* resultKey */).build();
            final Field allowedDataTypesField =
                    getDeclaredField(RemoteInput.class, "mAllowedDataTypes" /* fieldName */);
            allowedDataTypesField.set(
                    remoteInput,
                    new ArraySet<>(Collections.singleton(publicNotification)) /* value */);

            // Create a new notification channel
            final NotificationManager notificationManager =
                    context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(
                    new NotificationChannel(
                            channelId,
                            "CVE_2024_49743_channelName" /* channelName */,
                            NotificationManager.IMPORTANCE_HIGH /* importance */));

            // Create a pending intent to add action to the notification
            final PendingIntent pendingIntent =
                    new PendingIntent(
                            (IIntentSender)
                                    new IIntentSender.Stub() {
                                        @Override
                                        public void send(
                                                int code,
                                                Intent intent,
                                                String resolvedType,
                                                IBinder allowlistToken,
                                                IIntentReceiver finishedReceiver,
                                                String requiredPermission,
                                                Bundle options) {}
                                    });

            // Post the notification with remote input object and custom action
            final String actionTitle = "CVE_2024_49743_action_title";
            notificationManager.notify(
                    0 /* notificationId */,
                    new Notification.Builder(context, channelId)
                            .setSmallIcon(icon)
                            .setPublicVersion(publicNotification)
                            .addAction(
                                    new Action.Builder(icon, actionTitle, pendingIntent)
                                            .addRemoteInput(remoteInput)
                                            .build())
                            .build());

            // Check if notification gets posted or not
            final String packageName = context.getPackageName();
            assume().withMessage("Notification was not posted")
                    .that(
                            poll(
                                    () -> {
                                        for (StatusBarNotification statusBarNotification :
                                                notificationManager.getActiveNotifications()) {
                                            if (packageName.equals(
                                                    statusBarNotification.getPackageName())) {
                                                mPostedNotification =
                                                        statusBarNotification.getNotification();
                                                return true;
                                            }
                                        }
                                        return false;
                                    }))
                    .isTrue();

            // Fetch 'mAllowlistToken' field of Notification class
            final Field allowlistTokenField =
                    getDeclaredField(Notification.class, "mAllowlistToken" /* fieldName */);

            // With fix, 'mAllowlistToken' field of public version of the notification is
            // set to null
            final List<String> vulnerableNotification = new ArrayList<String>();
            if (allowlistTokenField.get(mPostedNotification.publicVersion) != null) {
                vulnerableNotification.add("public version of notification");
            }

            // With fix, 'mAllowlistToken' field of the unparceled notification fetched
            // from the allowed data types is set to null
            for (Action action : mPostedNotification.actions) {
                if (actionTitle.equals(action.title.toString())) {
                    for (RemoteInput statusRemoteInput : action.getRemoteInputs()) {
                        for (Object alloweDataType : statusRemoteInput.getAllowedDataTypes()) {
                            if (allowlistTokenField.get((Notification) alloweDataType) != null) {
                                vulnerableNotification.add("unparceled notification");
                                break;
                            }
                        }
                    }
                }
            }

            // Without fix, 'mAllowlistToken' field of the notification remains non-null
            final String errorMessage =
                    "Device is vulnerable to b/305695605 !!"
                            + " Allowlist token remains not-null for ";
            assertWithMessage(errorMessage.concat(String.join(" and ", vulnerableNotification)))
                    .that(vulnerableNotification)
                    .isEmpty();
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }

    private Field getDeclaredField(Class cls, String fieldName) {
        // Iterate through all declared fields and return the required field
        for (Field declaredField : cls.getDeclaredFields()) {
            if (declaredField.getName().equals(fieldName)) {
                declaredField.setAccessible(true);
                return declaredField;
            }
        }
        return null;
    }
}
