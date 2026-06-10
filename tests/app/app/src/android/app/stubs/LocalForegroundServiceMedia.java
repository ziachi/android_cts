/*
 * Copyright (C) 2019 The Android Open Source Project
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

import android.app.ForegroundServiceStartNotAllowedException;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.util.Log;

/**
 * Foreground Service with media type.
 */
public class LocalForegroundServiceMedia extends LocalForegroundService {

    private static final String TAG = "LocalForegroundServiceMedia";
    private static final String NOTIFICATION_CHANNEL_ID = "cts/" + TAG;
    public static final String EXTRA_FOREGROUND_SERVICE_TYPE = "ForegroundService.type";
    public static final int COMMAND_START_FOREGROUND_WITH_TYPE = 1;
    public static final int COMMAND_PLAY_MEDIA = 100;
    public static final int COMMAND_POST_MEDIA_NOTIFICATION = 101;
    public static final int COMMAND_DEACTIVATE_MEDIA_SESSION = 102;
    public static final int COMMAND_RELEASE_MEDIA_SESSION = 103;
    public static final int COMMAND_STOP_MEDIA = 104;
    public static String ACTION_START_FGSM_RESULT =
            "android.app.stubs.LocalForegroundServiceMedia.RESULT";
    public static String FGSM_NOTIFICATION_ID =
            "android.app.stubs.LocalForegroundServiceMedia.NOTIFICATION_ID";
    private int mNotificationId = 1000;

    private MediaSession mMediaSession = null;

    /** Returns the channel id for this service */
    public static String getNotificationChannelId() {
        return NOTIFICATION_CHANNEL_ID;
    }

    private static long sAllPlayStateActions =
            PlaybackState.ACTION_PLAY
                    | PlaybackState.ACTION_PAUSE
                    | PlaybackState.ACTION_PLAY_PAUSE
                    | PlaybackState.ACTION_STOP
                    | PlaybackState.ACTION_SKIP_TO_NEXT
                    | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                    | PlaybackState.ACTION_FAST_FORWARD
                    | PlaybackState.ACTION_REWIND;

    private void setPlaybackState(int state, MediaSession mediaSession) {
        PlaybackState playbackState =
                new PlaybackState.Builder()
                        .setActions(sAllPlayStateActions)
                        .setState(state, 0L, 0.0f)
                        .build();
        mediaSession.setPlaybackState(playbackState);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mMediaSession = new MediaSession(this, TAG);
        mMediaSession.setCallback(
                new MediaSession.Callback() {
                    @Override
                    public void onPlay() {
                        Log.d(TAG, "received onPlay");
                        super.onPlay();
                        setPlaybackState(PlaybackState.STATE_PLAYING, mMediaSession);
                    }

                    @Override
                    public void onPause() {
                        Log.d(TAG, "received onPause");
                        super.onPause();
                        setPlaybackState(PlaybackState.STATE_PAUSED, mMediaSession);
                    }

                    @Override
                    public void onStop() {
                        Log.d(TAG, "received onStop");
                        super.onStop();
                        setPlaybackState(PlaybackState.STATE_PAUSED, mMediaSession);
                    }
                });
        mMediaSession.setActive(true);
        Log.d(
                getTag(),
                "service created: "
                        + this
                        + " in "
                        + android.os.Process.myPid()
                        + "with media session: "
                        + mMediaSession);
    }

    @Override
    public void onDestroy() {
        mMediaSession.release();
        mMediaSession = null;
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String notificationChannelId = getNotificationChannelId();
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(new NotificationChannel(
                notificationChannelId, notificationChannelId,
                NotificationManager.IMPORTANCE_DEFAULT));

        Context context = getApplicationContext();
        int command = intent.getIntExtra(EXTRA_COMMAND, -1);
        Intent reply = new Intent(ACTION_START_FGSM_RESULT).setFlags(
                Intent.FLAG_RECEIVER_FOREGROUND);
        switch (command) {
            case COMMAND_START_FOREGROUND_WITH_TYPE:
                final int type = intent.getIntExtra(EXTRA_FOREGROUND_SERVICE_TYPE,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST);
                mNotificationId++;
                final Notification notification =
                        new Notification.Builder(context, NOTIFICATION_CHANNEL_ID)
                                .setContentTitle(getNotificationTitle(mNotificationId))
                                .setSmallIcon(R.drawable.black)
                                .setStyle(
                                        new Notification.MediaStyle()
                                                .setMediaSession(mMediaSession.getSessionToken()))
                                .build();
                try {
                    startForeground(mNotificationId, notification, type);
                    reply.putExtra(FGSM_NOTIFICATION_ID, mNotificationId);
                } catch (ForegroundServiceStartNotAllowedException e) {
                    Log.d(TAG, "startForeground gets an "
                            + " ForegroundServiceStartNotAllowedException", e);
                }
                break;
            case COMMAND_STOP_FOREGROUND_REMOVE_NOTIFICATION:
                Log.d(TAG, "Stopping foreground removing notification");
                stopForeground(true);
                break;
            case COMMAND_START_NO_FOREGROUND:
                Log.d(TAG, "Starting without calling startForeground()");
                break;
            case COMMAND_POST_MEDIA_NOTIFICATION:
                Log.d(TAG, "Posting media style notification");
                final Notification notification1 =
                        new Notification.Builder(context, NOTIFICATION_CHANNEL_ID)
                                .setContentTitle(getNotificationTitle(mNotificationId))
                                .setSmallIcon(R.drawable.black)
                                .setStyle(
                                        new Notification.MediaStyle()
                                                .setMediaSession(mMediaSession.getSessionToken()))
                                .build();
                notificationManager.notify(mNotificationId++, notification1);
                break;

            case COMMAND_PLAY_MEDIA:
                Log.d(TAG, "Setting media session state to playing");
                setPlaybackState(PlaybackState.STATE_PLAYING, mMediaSession);
                break;
            case COMMAND_STOP_MEDIA:
                Log.d(TAG, "Setting media session state to stopped");
                setPlaybackState(PlaybackState.STATE_STOPPED, mMediaSession);
                break;
            case COMMAND_DEACTIVATE_MEDIA_SESSION:
                Log.d(TAG, "Deactivating media session");
                mMediaSession.setActive(false);
                break;
            case COMMAND_RELEASE_MEDIA_SESSION:
                Log.d(TAG, "Release media session");
                mMediaSession.release();
                break;
            default:
                Log.e(TAG, "Unknown command: " + command);
        }
        sendBroadcast(reply);
        return START_NOT_STICKY;
    }
}
