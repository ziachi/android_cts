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

package android.virtualdevice.streamedtestapp;

import static android.Manifest.permission.RECORD_AUDIO;
import static android.content.Intent.EXTRA_RESULT_RECEIVER;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
import static android.media.AudioFormat.ENCODING_PCM_16BIT;
import static android.virtualdevice.cts.common.StreamedAppConstants.EXTRA_RECORD_AUDIO_SUCCESS;
import static android.virtualdevice.cts.common.StreamedAppConstants.EXTRA_USE_SERVICE;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteCallback;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;

/**
 * Activity used for testing audio recording permissions on different devices. It needs to be in a
 * separate apk because CTS is automatically granted runtime permissions.
 */
public class RecordAudioTestActivity extends Activity {

    private static final String TAG = RecordAudioTestActivity.class.getSimpleName();
    private static final int SAMPLE_RATE = 48000;
    private static final int BUFFER_SIZE = 65536;
    private static final int AUDIO_PERMISSIONS_PROPAGATION_TIME_MS = 300;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean useService = getIntent().getBooleanExtra(EXTRA_USE_SERVICE, false);
        final RemoteCallback resultReceiver = getIntent().getParcelableExtra(EXTRA_RESULT_RECEIVER,
                RemoteCallback.class);

        // TODO: b/383048413
        // Account for the delay until the VDM audio policies and permissions are propagated
        SystemClock.sleep(AUDIO_PERMISSIONS_PROPAGATION_TIME_MS);

        if (useService) {
            startForegroundService(new Intent(this, RecordAudioService.class)
                    .putExtra(EXTRA_RESULT_RECEIVER, resultReceiver));
        } else {
            recordAudio(this, resultReceiver);
        }

        finish();
    }

    @SuppressLint("MissingPermission")
    // RECORD_AUDIO permission is set externally
    static void recordAudio(Context context, RemoteCallback resultReceiver) {
        Bundle result = new Bundle();
        AudioRecord audioRecord = null;

        try {
            Log.d(TAG, "Before recording on context device id " + context.getDeviceId()
                    + " with RECORD_AUDIO permission state "
                    + context.checkSelfPermission(RECORD_AUDIO));

            audioRecord = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, ENCODING_PCM_16BIT, BUFFER_SIZE);

            audioRecord.startRecording();

            Log.d(TAG, "Recording audio in RecordAudioTestActivity... source: "
                    + audioRecord.getAudioSource() + " address: "
                    + audioRecord.getRoutedDevice().getAddress());

            result.putBoolean(EXTRA_RECORD_AUDIO_SUCCESS, /* record succeeded */ true);
        } catch (Exception e) {
            Log.d(TAG, "Could not start audio recording in RecordAudioTestActivity.");
            result.putBoolean(EXTRA_RECORD_AUDIO_SUCCESS, /* record failed */ false);
        } finally {
            if (audioRecord != null) {
                try {
                    if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                        audioRecord.stop();
                    }
                    audioRecord.release();
                } catch (Exception ex) {
                    Log.w(TAG, "Exception stopping and releasing the AudioRecord: " + ex);
                }
            }
        }

        if (resultReceiver != null) {
            resultReceiver.sendResult(result);
        }
    }

    /**
     * Service used for testing starting of an audio recording foreground service.
     */
    public static class RecordAudioService extends Service {

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            super.onStartCommand(intent, flags, startId);
            final RemoteCallback resultReceiver = intent.getParcelableExtra(EXTRA_RESULT_RECEIVER,
                    RemoteCallback.class);

            try {
                Log.d(TAG, "Start foreground for RecordAudioService on deviceId "
                        + getDeviceId());
                startForeground(1, buildNotification(), FOREGROUND_SERVICE_TYPE_MICROPHONE);
                new Thread(() -> {
                    recordAudio(this, resultReceiver);
                    stopSelf();
                }).start();
            } catch (Exception e) {
                Log.d(TAG, "Exception starting foreground service " + e);
                if (resultReceiver != null) {
                    Bundle result = new Bundle();
                    result.putBoolean(EXTRA_RECORD_AUDIO_SUCCESS, false);
                    resultReceiver.sendResult(result);
                }
            }

            return START_NOT_STICKY;
        }

        @Nullable
        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        /** Create a notification which is required to start a foreground service */
        private Notification buildNotification() {
            NotificationManager notificationManager = getSystemService(NotificationManager.class);

            notificationManager.createNotificationChannel(new NotificationChannel(
                    "all", "All Notifications", NotificationManager.IMPORTANCE_HIGH));

            return new Notification.Builder(this, "all")
                    .setContentTitle("Recording audio")
                    .setContentText("recording...")
                    .setSmallIcon(android.R.drawable.sym_def_app_icon)
                    .build();
        }
    }
}
