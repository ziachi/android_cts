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
import android.media.AudioRecord;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;

import com.android.cts.verifier.R;
import com.android.cts.verifier.audio.audiolib.AudioDeviceUtils;

import org.hyphonate.megaaudio.common.BuilderBase;
import org.hyphonate.megaaudio.common.StreamBase;
import org.hyphonate.megaaudio.recorder.JavaRecorder;
import org.hyphonate.megaaudio.recorder.RecorderBuilder;
import org.hyphonate.megaaudio.recorder.sinks.NopAudioSinkProvider;

/*
 * Tests AudioRecord (re)Routing messages.
 */
public class AudioInputRoutingNotificationsActivity extends AudioNotificationsBaseActivity {
    private static final String TAG = "AudioInputRoutingNotificationsActivity";

    int mNumRoutingNotifications;

    static final int NUM_CHANNELS = 2;
    static final int SAMPLE_RATE = 48000;

    private JavaRecorder mAudioRecorder;
    private AudioRecordRoutingChangeListener mRouteChangeListener;
    private boolean mIsRecording;

    // ignore messages sent as a consequence of starting the player
    private static final int NUM_IGNORE_MESSAGES = 0; // 2;

    // ReportLog schema
    protected static final String SECTION_INPUT_ROUTING = "audio_in_routing_notifications";

    public AudioInputRoutingNotificationsActivity() {
        super(AudioManager.GET_DEVICES_INPUTS);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.audio_routingnotifications_test);
        super.onCreate(savedInstanceState);

        ((TextView) findViewById(R.id.audio_routingnotification_instructions))
                .setText(getText(R.string.audio_input_routingnotification_instructions));

        setInfoResources(R.string.audio_input_routingnotifications_test,
                R.string.audio_input_routingnotification_instructions, -1);

        mRouteChangeListener = new AudioRecordRoutingChangeListener();
    }

    //
    // Audio Handlers
    //
    @Override
    void startAudio() {
        if (mIsRecording) {
            return;
        }

        int buildResult = StreamBase.ERROR_UNKNOWN;
        int openResult = StreamBase.ERROR_UNKNOWN;
        int startResult = StreamBase.ERROR_UNKNOWN;

        try {
            int numExchangeFrames = StreamBase.getNumBurstFrames(BuilderBase.TYPE_NONE);

            RecorderBuilder builder = new RecorderBuilder();
            builder.setRecorderType(RecorderBuilder.TYPE_JAVA)
                    .setAudioSinkProvider(new NopAudioSinkProvider())
                    .setChannelCount(NUM_CHANNELS)
                    .setSampleRate(SAMPLE_RATE)
                    .setNumExchangeFrames(numExchangeFrames);
            mAudioRecorder = (JavaRecorder) builder.allocStream();

            if (mAudioRecorder != null
                    && (buildResult = mAudioRecorder.build(builder)) == StreamBase.OK
                    && (openResult = mAudioRecorder.open()) == StreamBase.OK
                    && (startResult = mAudioRecorder.start()) == StreamBase.OK) {
                mNumRoutingNotifications = 0;

                AudioRecord audioRecord = mAudioRecorder.getAudioRecord();
                audioRecord.addOnRoutingChangedListener(mRouteChangeListener,
                        new Handler());

                mIsRecording = true;
            }
        } catch (BuilderBase.BadStateException ex) {
            Log.e(TAG, "Failed MegaRecorder build. ex:" + ex);
        }

        if (!mIsRecording) {
            // Report Errors...
            showStartupError("Recorder", buildResult, openResult, startResult);

            // Unwind
            if (mAudioRecorder != null) {
                mAudioRecorder.unwind();
                mAudioRecorder = null;
            }
        }
    }

    @Override
    void stopAudio() {
        if (mIsRecording) {
            AudioRecord audioRecord = mAudioRecorder.getAudioRecord();
            audioRecord.removeOnRoutingChangedListener(mRouteChangeListener);

            // unwind() will call stop()
            mAudioRecorder.unwind();
            mAudioRecorder = null;
            mIsRecording = false;
        }
    }

    private class AudioRecordRoutingChangeListener implements AudioRecord.OnRoutingChangedListener {
        public void onRoutingChanged(AudioRecord audioRecord) {
            // Starting recording triggers routing message, so ignore the first one.
            mNumRoutingNotifications++;
            if (mNumRoutingNotifications <= NUM_IGNORE_MESSAGES) {
                return;
            }

            TextView textView =
                    (TextView) findViewById(R.id.audio_routingnotification_change);
            String msg = mContext.getString(
                    R.string.audio_routingnotification_recordRoutingMsg);
            AudioDeviceInfo routedDevice = audioRecord.getRoutedDevice();
            mConnectedPeripheralName = AudioDeviceUtils.formatDeviceName(routedDevice);
            textView.setText(msg + ": " + mConnectedPeripheralName);
            mRoutingNotificationReceived = true;
            stopAudio();
            calculatePass();
        }
    }

    @Override
    public final String getReportSectionName() {
        return setTestNameSuffix(sCurrentDisplayMode, SECTION_INPUT_ROUTING);
    }
}
