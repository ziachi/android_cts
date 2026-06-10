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

package android.security.cts.CVE_2024_34734;

import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;

import static com.android.sts.common.SystemUtil.poll;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.IBinder;
import android.security.cts.R;

public class PocService extends Service {
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            // Create the foreground service notification
            final String channelName = "CVE_2024_34734_channel";
            getSystemService(NotificationManager.class)
                    .createNotificationChannel(
                            new NotificationChannel(
                                    channelName,
                                    channelName,
                                    NotificationManager.IMPORTANCE_DEFAULT));
            Notification notification =
                    new Notification.Builder(this, channelName)
                            .setSmallIcon(
                                    Icon.createWithData(
                                            new byte[0] /* data */, 0 /* offset */, 0 /* length */))
                            .build();

            // Start the service in foreground and simulate a long running task
            startForeground(1 /* id */, notification, FOREGROUND_SERVICE_TYPE_MICROPHONE);
            new Thread(
                            () -> {
                                try {
                                    poll(
                                            () -> {
                                                return false;
                                            }); // 30 seconds
                                } catch (InterruptedException e) {
                                    // Ignore exception here
                                }
                                stopSelf();
                            })
                    .start();

            sendException(null);
            return START_STICKY;
        } catch (Exception e) {
            try {
                sendException(e);
            } catch (Exception ignore) {
                // Ignore exceptions here
            }
            return START_STICKY;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void sendException(Exception e) {
        sendBroadcast(
                new Intent(getString(R.string.CVE_2024_34734_action))
                        .putExtra(getString(R.string.CVE_2024_34734_key), e));
    }
}
