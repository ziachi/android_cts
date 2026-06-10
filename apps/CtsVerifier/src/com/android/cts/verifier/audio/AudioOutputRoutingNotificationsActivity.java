/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.cts.verifier.audio;

import static com.android.cts.verifier.TestListActivity.sCurrentDisplayMode;
import static com.android.cts.verifier.TestListAdapter.setTestNameSuffix;

import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;

import com.android.cts.verifier.R;
import com.android.cts.verifier.audio.audiolib.AudioDeviceUtils;

import org.hyphonate.megaaudio.common.BuilderBase;
import org.hyphonate.megaaudio.common.StreamBase;
import org.hyphonate.megaaudio.player.JavaPlayer;
import org.hyphonate.megaaudio.player.PlayerBuilder;
import org.hyphonate.megaaudio.player.sources.SilenceAudioSourceProvider;

/**
 * Tests AudioTrack and AudioRecord (re)Routing messages.
 */
public class AudioOutputRoutingNotificationsActivity extends AudioNotificationsBaseActivity {
    private static final String TAG = "AudioOutputRoutingNotificationsActivity";

    static final int NUM_CHANNELS = 2;
    static final int SAMPLE_RATE = 48000;

    int mNumRoutingNotifications;

    // ignore messages sent as a consequence of starting the player
    private static final int NUM_IGNORE_MESSAGES = 1;

    // Mega Player
    private JavaPlayer mAudioPlayer;
    private AudioTrackRoutingChangeListener mRoutingChangeListener;
    private boolean mIsPlaying;

    private boolean mInitialRoutingMessageHandled;

    // ReportLog schema
    private static final String SECTION_OUTPUT_ROUTING = "audio_out_routing_notifications";

    public AudioOutputRoutingNotificationsActivity() {
        super(AudioManager.GET_DEVICES_OUTPUTS);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.audio_routingnotifications_test);
        super.onCreate(savedInstanceState);

        ((TextView) findViewById(R.id.audio_routingnotification_instructions))
                .setText(getText(R.string.audio_output_routingnotification_instructions));

        setInfoResources(R.string.audio_output_routingnotifications_test,
                R.string.audio_output_routingnotification_instructions, -1);

        mRoutingChangeListener = new AudioTrackRoutingChangeListener();
    }

    @Override
    void startAudio() {
        if (mIsPlaying) {
            return;
        }

        int buildResult = StreamBase.ERROR_UNKNOWN;
        int openResult = StreamBase.ERROR_UNKNOWN;
        int startResult = StreamBase.ERROR_UNKNOWN;

        try {
            int numExchangeFrames = StreamBase.getNumBurstFrames(BuilderBase.TYPE_NONE);

            PlayerBuilder builder = new PlayerBuilder();
            builder.setSourceProvider(new SilenceAudioSourceProvider())
                    .setPlayerType(PlayerBuilder.TYPE_JAVA)
                    .setChannelCount(NUM_CHANNELS)
                    .setSampleRate(SAMPLE_RATE)
                    .setNumExchangeFrames(numExchangeFrames);
            mAudioPlayer = (JavaPlayer) builder.allocStream();

            if (mAudioPlayer != null
                    && (buildResult = mAudioPlayer.build(builder)) == StreamBase.OK
                    && (openResult = mAudioPlayer.open()) == StreamBase.OK
                    && (startResult = mAudioPlayer.start()) == StreamBase.OK) {
                mNumRoutingNotifications = 0;

                AudioTrack audioTrack = mAudioPlayer.getAudioTrack();
                audioTrack.addOnRoutingChangedListener(mRoutingChangeListener,
                        new Handler());

                mIsPlaying = true;
            }
        } catch (BuilderBase.BadStateException ex) {
            Log.e(TAG, "Failed MegaPlayer build. ex:", ex);
        }

        if (!mIsPlaying) {
            // Report Error
            showStartupError("Player", buildResult, openResult, startResult);

            // Unwind
            if (mAudioPlayer != null) {
                mAudioPlayer.unwind();
                mAudioPlayer = null;
            }
        }
    }

    @Override
    void stopAudio() {
        if (mIsPlaying) {
            AudioTrack audioTrack = mAudioPlayer.getAudioTrack();
            audioTrack.removeOnRoutingChangedListener(mRoutingChangeListener);

            // unwind will call stop()
            mAudioPlayer.unwind();
            mAudioPlayer = null;

            mIsPlaying = false;
        }
    }

    private class AudioTrackRoutingChangeListener implements AudioTrack.OnRoutingChangedListener {
        public void onRoutingChanged(AudioTrack audioTrack) {
            // Starting playback triggers a message, so ignore the first one.
            mNumRoutingNotifications++;
            if (mNumRoutingNotifications <= NUM_IGNORE_MESSAGES) {
                return;
            }

            TextView textView =
                    (TextView) findViewById(R.id.audio_routingnotification_change);
            String msg = mContext.getString(
                    R.string.audio_routingnotification_trackRoutingMsg);
            AudioDeviceInfo routedDevice = audioTrack.getRoutedDevice();
            mConnectedPeripheralName = AudioDeviceUtils.formatDeviceName(routedDevice);
            textView.setText(msg + ": " + mConnectedPeripheralName);
            mRoutingNotificationReceived = true;
            stopAudio();
            calculatePass();
        }
    }

    @Override
    public final String getReportSectionName() {
        return setTestNameSuffix(sCurrentDisplayMode, SECTION_OUTPUT_ROUTING);
    }
}
