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

package android.media.audio.cts;

import static android.media.AudioAttributes.ALLOW_CAPTURE_BY_ALL;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeThat;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioAttributes.AttributeUsage;
import android.media.AudioAttributes.CapturePolicy;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.cts.MediaProjectionRule;
import android.media.cts.Utils;
import android.media.projection.MediaProjection;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;
import android.util.Log;
import android.view.KeyEvent;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Validate that there is no discontinuity in the AudioRecord data from Remote Submix.
 *
 * The tests do the following:
 *   - Start AudioRecord and MediaPlayer.
 *   - Play sine wav audio and read the recorded audio in rawBuffer.
 *   - Add screen lock during playback.
 *   - Stop MediaPlayer and AudioRecord, and then unlock the device.
 *   - Verify that the recorded audio doesn't have any discontinuity.
 *
 * Testing at sample level that audio playback and record do not make any alterations to input
 * signal.
 */

@Presubmit
@AppModeFull(reason = "instant apps can't set up test conditions")
public class RemoteSubmixTest {
    private static final String TAG = "RemoteSubmixTest";
    private static final int SAMPLE_RATE = 44100;
    private static final int DURATION_IN_SEC = 1;
    private static final int ENCODING_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int CHANNEL_MASK = AudioFormat.CHANNEL_IN_MONO;
    private static final int BUFFER_SIZE_IN_BYTES = SAMPLE_RATE * DURATION_IN_SEC
            * Integer.bitCount(CHANNEL_MASK)
            * Short.BYTES; // Size in bytes for 16bit mono at 44.1k/s
    private static final int RETRY_DISCONTINUITY = 10;
    private static final int RETRY_RECORD_READ = 3;

    private Context mContext;
    private AudioManager mAudioManager;
    private Activity mActivity;
    private MediaProjection mMediaProjection;
    private Map<Integer, Integer> mStreamVolume = new HashMap<Integer, Integer>();
    private Map<Integer, String> mStreamNames = new HashMap<Integer, String>();

    @Rule public MediaProjectionRule mMediaProjectionRule = new MediaProjectionRule();

    @Before
    public void setup() throws Exception {
        mMediaProjection = mMediaProjectionRule.startMediaProjection();
        mActivity = mMediaProjectionRule.getActivity();
        mContext = getInstrumentation().getContext();
        mAudioManager = mActivity.getSystemService(AudioManager.class);
        mStreamNames.put(AudioManager.STREAM_RING, "RING");
        mStreamNames.put(AudioManager.STREAM_NOTIFICATION, "NOTIFICATION");
        mStreamNames.put(AudioManager.STREAM_SYSTEM, "SYSTEM");
        muteStreams();
    }

    @After
    public void tearDown() throws Exception {
        unmuteStreams();
    }

    private static Instrumentation getInstrumentation() {
        return androidx.test.platform.app.InstrumentationRegistry.getInstrumentation();
    }

    private AudioRecord createPlaybackCaptureRecord() throws Exception {
        AudioPlaybackCaptureConfiguration apcConfig =
                new AudioPlaybackCaptureConfiguration.Builder(mMediaProjection)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .build();

        AudioFormat audioFormat = new AudioFormat.Builder()
                                          .setEncoding(ENCODING_FORMAT)
                                          .setSampleRate(SAMPLE_RATE)
                                          .setChannelMask(CHANNEL_MASK)
                                          .build();

        assertEquals(
                "matchingUsages", AudioAttributes.USAGE_MEDIA, apcConfig.getMatchingUsages()[0]);

        AudioRecord audioRecord = new AudioRecord.Builder()
                                          .setAudioPlaybackCaptureConfig(apcConfig)
                                          .setAudioFormat(audioFormat)
                                          .build();

        assertEquals("AudioRecord failed to initialized", AudioRecord.STATE_INITIALIZED,
                audioRecord.getState());

        return audioRecord;
    }

    private MediaPlayer createMediaPlayer(
            @CapturePolicy int capturePolicy, int resid, @AttributeUsage int usage) {
        MediaPlayer mediaPlayer = MediaPlayer.create(mActivity, resid,
                new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(usage)
                        .setAllowedCapturePolicy(capturePolicy)
                        .build(),
                mAudioManager.generateAudioSessionId());
        return mediaPlayer;
    }

    private static ByteBuffer readToBuffer(AudioRecord audioRecord, int bufferSize)
            throws Exception {
        assertEquals("AudioRecord is not recording", AudioRecord.RECORDSTATE_RECORDING,
                audioRecord.getRecordingState());
        ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);
        int retry = RETRY_RECORD_READ;
        boolean silence = true;
        while (silence && buffer.hasRemaining()) {
            assertNotSame(buffer.remaining() + "/" + bufferSize + " remaining", 0, retry--);
            int written = audioRecord.read(buffer, buffer.remaining());
            assertThat("audioRecord did not read frames", written, greaterThan(0));
            for (int i = 0; i < written; i++) {
                if (buffer.get() != 0) {
                    silence = false;
                    break;
                }
            }
        }
        buffer.rewind();
        return buffer;
    }

    /**
     * Mute device audio streams
     */
    private void muteStreams() throws Exception {
        try {
            Utils.toggleNotificationPolicyAccess(
                    mContext.getPackageName(), getInstrumentation(), true);
            // Since some streams are aliased, need to do a first pass of capturing
            // the current values before doing a pass which applies muting.
            for (Map.Entry<Integer, String> map : mStreamNames.entrySet()) {
                // Get current device stream volume level
                mStreamVolume.put(map.getKey(), mAudioManager.getStreamVolume(map.getKey()));
            }
            for (Map.Entry<Integer, String> map : mStreamNames.entrySet()) {
                // Mute device streams
                mAudioManager.adjustStreamVolume(
                        map.getKey(), AudioManager.ADJUST_MUTE, 0 /*no flag used*/);
                assumeThat("Stream " + map.getValue() + " can not be muted",
                        mAudioManager.getStreamVolume(map.getKey()), is(0));
            }
        } finally {
            Utils.toggleNotificationPolicyAccess(
                    mContext.getPackageName(), getInstrumentation(), false);
        }
    }

    /**
     * Unmute device audio streams
     */
    private void unmuteStreams() throws Exception {
        try {
            Utils.toggleNotificationPolicyAccess(
                    mContext.getPackageName(), getInstrumentation(), true);
            for (Map.Entry<Integer, Integer> map : mStreamVolume.entrySet()) {
                // Restore device stream volume
                mAudioManager.setStreamVolume(map.getKey(), map.getValue(), 0 /*no flag used*/);
            }
        } finally {
            Utils.toggleNotificationPolicyAccess(
                    mContext.getPackageName(), getInstrumentation(), false);
        }
        mStreamVolume.clear();
    }

    private boolean isRecordingBufferContinuous(ByteBuffer buffer) {
        short[] recordArray = new short[BUFFER_SIZE_IN_BYTES / Short.BYTES];

        for (int i = 0; i < recordArray.length; i++) {
            recordArray[i] = buffer.getShort();
        }

        int recordingStartIndex = -1;

        // Skip leading silence of the Recorded Audio
        for (int i = 0; i < recordArray.length; i++) {
            if (recordArray[i] != 0) {
                recordingStartIndex = i;
                break;
            }
        }

        assertFalse("No audio recorded", recordingStartIndex == -1);

        // Validate that there is no continuous silence in recorded sine audio
        for (int i = recordingStartIndex; i < recordArray.length - 1; i++) {
            if (recordArray[i] == 0 && recordArray[i + 1] == 0) {
                Log.i(TAG, "Discontunuity found in the Recorded Audio");
                return false;
            }
        }
        return true;
    }

    private boolean isRecordingContinuous(boolean testWithScreenLock) throws Exception {
        MediaPlayer mediaPlayer = createMediaPlayer(
                ALLOW_CAPTURE_BY_ALL, R.raw.sine1320hz5sec, AudioAttributes.USAGE_MEDIA);
        AudioRecord audioRecord = createPlaybackCaptureRecord();
        ByteBuffer rawBuffer = null;

        try {
            audioRecord.startRecording();
            mediaPlayer.start();

            assertEquals(AudioRecord.RECORDSTATE_RECORDING, audioRecord.getRecordingState());
            assertTrue(mediaPlayer.isPlaying());

            if (testWithScreenLock) {
                UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                        .pressKeyCode(KeyEvent.KEYCODE_POWER);
            }

            rawBuffer = readToBuffer(audioRecord, BUFFER_SIZE_IN_BYTES);

            audioRecord.stop();
            mediaPlayer.stop();

            assertEquals(AudioRecord.RECORDSTATE_STOPPED, audioRecord.getRecordingState());
            assertFalse(mediaPlayer.isPlaying());

        } catch (Exception e) {
            throw e;
        } finally {
            if (testWithScreenLock) {
                UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                        .pressKeyCode(KeyEvent.KEYCODE_WAKEUP);
                UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                        .executeShellCommand("wm dismiss-keyguard");
            }

            audioRecord.release();
            mediaPlayer.release();
        }

        assertNotNull("Recorded data is null ", rawBuffer);
        return isRecordingBufferContinuous(rawBuffer);
    }

    private void testRecordingContinuity(boolean testWithScreenLock) {
        int retry = RETRY_DISCONTINUITY;
        try {
            // Need to ensure continuous recording, but will retry to avoid flaky failure
            while (!isRecordingContinuous(testWithScreenLock)) {
                assertNotSame("Consistent discontinuity detected", 0, retry--);
            }
        } catch (Exception e) {
            fail("isRecordingContinuous throws exception: " + e);
        }
    }

    @Test
    public void testRemoteSubmixRecordingContinuity() {
        testRecordingContinuity(/* testWithScreenLock */ false);
    }

    @Test
    public void testRemoteSubmixRecordingContinuityWithScreenLock() {
        testRecordingContinuity(/* testWithScreenLock */ true);
    }
}
