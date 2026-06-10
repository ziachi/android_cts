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

package android.app.stubs;

import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class BootCompletedFgs extends Service {
    private static final String TAG = "BootCompletedFgs";

    public static final String ACTION_FAKE_BOOT_COMPLETED =
            "android.intent.action.PSEUDO_BOOT_COMPLETED";
    public static final String EXTRA_FGS_TYPES = "extra_fgs_types";
    public static final String ACTION_BOOT_COMPLETED_FGS_RESULT =
            "android.intent.action.ACTION_BOOT_COMPLETED_FGS_RESULT";
    public static final int RESULT_CODE_UNKNOWN = 0;
    public static final int RESULT_CODE_SUCCESS = 1;
    public static final int RESULT_CODE_FAILURE = 2;

    private static final int FGS_NOTIFICATION_ID = 1;
    private static final String NOTIFICATION_CHANNEL_ID =
            BootCompletedFgs.class.getSimpleName();

    private static final int DEFAULT_TYPES = FOREGROUND_SERVICE_TYPE_DATA_SYNC;

    @Override
    public void onCreate() {
        createNotificationChannelId(this, NOTIFICATION_CHANNEL_ID);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final int types = intent.getIntExtra(EXTRA_FGS_TYPES, DEFAULT_TYPES);
        // When this service is started, make it a foreground service
        final Notification.Builder builder =
                new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.btn_star)
                        .setContentTitle(NOTIFICATION_CHANNEL_ID)
                        .setContentText(BootCompletedFgs.class.getName());
        try {
            startForeground(FGS_NOTIFICATION_ID, builder.build(), types);
            sendResult(true);
        } catch (Exception e) {
            Log.w(TAG, "Got exception", e);
            sendResult(false);
        }
        return Service.START_NOT_STICKY;
    }

    private void sendResult(boolean success) {
        final Intent intent = new Intent(ACTION_BOOT_COMPLETED_FGS_RESULT);
        intent.putExtra(Intent.EXTRA_RETURN_RESULT,
                success ? RESULT_CODE_SUCCESS : RESULT_CODE_FAILURE);
        sendBroadcast(intent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /** Create a notification channel. */
    private static void createNotificationChannelId(Context context, String id) {
        final NotificationManager nm =
                context.getSystemService(NotificationManager.class);
        final CharSequence name = id;
        final String description = BootCompletedFgs.class.getName();
        final int importance = NotificationManager.IMPORTANCE_DEFAULT;
        final NotificationChannel channel = new NotificationChannel(
                NOTIFICATION_CHANNEL_ID, name, importance);
        channel.setDescription(description);
        nm.createNotificationChannel(channel);
    }
}
