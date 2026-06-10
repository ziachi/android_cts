/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static android.content.pm.PackageManager.FEATURE_MICROPHONE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import static org.testng.Assert.assertThrows;

import android.app.Instrumentation;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaSyncEvent;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.filters.SdkSuppress;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.compatibility.common.util.FrameworkSpecificTest;
import com.android.compatibility.common.util.RequiredFeatureRule;
import com.android.compatibility.common.util.SystemUtil;
import com.android.media.mediatestutils.PermissionUpdateBarrierRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.Rule;
import org.junit.runner.RunWith;

import java.io.IOException;



@FrameworkSpecificTest
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 31, codeName = "S")
public class AudioRecordSharedAudioTest {
    private static final String TAG = "AudioRecordSharedAudioTest";
    private int mSamplingRateHz;

    @Rule(order = 0)
    public final RequiredFeatureRule mMicRule = new RequiredFeatureRule(FEATURE_MICROPHONE);

    @Rule(order = 1)
    public final AdoptShellPermissionsRule mPermissionRule = new AdoptShellPermissionsRule();

    @Rule(order = 2)
    public final PermissionUpdateBarrierRule mBarrierRule = new PermissionUpdateBarrierRule();

    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
    private String mPackageName = mInstrumentation.getContext().getPackageName();

    @Before
    public void setUp() throws Exception {
        clearAudioserverPermissionCache();
        setDefaultSamplingRate();
    }

    @After
    public void tearDown() throws Exception {
        clearAudioserverPermissionCache();
    }

    @Test
    public void testPermissionFailure() throws Exception {
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        clearAudioserverPermissionCache();
        mInstrumentation.getContext().getSystemService(AudioManager.class).permissionUpdateBarrier();

        assertThrows(UnsupportedOperationException.class, () -> {
                    AudioRecord record = new AudioRecord.Builder().setMaxSharedAudioHistoryMillis(
                            AudioRecord.getMaxSharedAudioHistoryMillis() - 1).build();
                });

        final AudioRecord record =
                new AudioRecord.Builder()
                        .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(mSamplingRateHz)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                        .setBufferSizeInBytes(mSamplingRateHz
                                * AudioFormat.getBytesPerSample(AudioFormat.ENCODING_PCM_16BIT))
                        .build();
        assertEquals(AudioRecord.STATE_INITIALIZED, record.getState());
        record.startRecording();
        Thread.sleep(500);

        assertThrows(SecurityException.class, () -> {
                    record.shareAudioHistory(mPackageName, 100);
                });

        record.stop();
        record.release();
    }

    @Test
    public void testPermissionSuccess() throws Exception {
        AudioRecord record = new AudioRecord.Builder().setAudioFormat(new AudioFormat.Builder()
                    .setSampleRate(mSamplingRateHz)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                .setBufferSizeInBytes(mSamplingRateHz
                        * AudioFormat.getBytesPerSample(AudioFormat.ENCODING_PCM_16BIT))
                .setMaxSharedAudioHistoryMillis(
                    AudioRecord.getMaxSharedAudioHistoryMillis()-1)
                .build();

        assertEquals(AudioRecord.STATE_INITIALIZED, record.getState());

        record.startRecording();
        Thread.sleep(500);
        try {
            record.shareAudioHistory(mPackageName, 100);
        } catch (SecurityException e) {
            fail("testPermissionSuccess shareAudioHistory be allowed");
        } finally {
            record.stop();
            record.release();
        }
    }

    @Test
    public void testBadValues() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> {
                    AudioRecord.Builder builder = new AudioRecord.Builder()
                            .setMaxSharedAudioHistoryMillis(
                                    AudioRecord.getMaxSharedAudioHistoryMillis() + 1);
                });

        assertThrows(IllegalArgumentException.class, () -> {
                    AudioRecord.Builder builder = new AudioRecord.Builder()
                            .setMaxSharedAudioHistoryMillis(-1);
                });

        final AudioRecord record =
                new AudioRecord.Builder().setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(mSamplingRateHz)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                    .setBufferSizeInBytes(mSamplingRateHz
                            * AudioFormat.getBytesPerSample(AudioFormat.ENCODING_PCM_16BIT))
                    .setMaxSharedAudioHistoryMillis(
                            AudioRecord.getMaxSharedAudioHistoryMillis()-1)
                    .build();

        assertEquals(AudioRecord.STATE_INITIALIZED, record.getState());

        record.startRecording();
        Thread.sleep(500);

        assertThrows(NullPointerException.class, () -> {
                    record.shareAudioHistory(null /* sharedPackage */, 100 /* startFromMillis */);
                });

        assertThrows(IllegalArgumentException.class, () -> {
                    record.shareAudioHistory(mPackageName, -1 /* startFromMillis */);
                });

        record.stop();
        record.release();
    }

    @Test
    public void testCapturesMatch() throws Exception {
        AudioRecord record1 = null;
        AudioRecord record2 = null;
        try {
            record1 = new AudioRecord.Builder().setAudioFormat(new AudioFormat.Builder()
                                .setSampleRate(mSamplingRateHz)
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                            .setBufferSizeInBytes(mSamplingRateHz
                                * AudioFormat.getBytesPerSample(AudioFormat.ENCODING_PCM_16BIT))
                            .setMaxSharedAudioHistoryMillis(
                                    AudioRecord.getMaxSharedAudioHistoryMillis() - 1)
                            .build();
            assertEquals(AudioRecord.STATE_INITIALIZED, record1.getState());

            record1.startRecording();

            final int RECORD1_NUM_SAMPLES = mSamplingRateHz / 2;
            short[] buffer1 = new short[RECORD1_NUM_SAMPLES];

            // blocking read should allow for at least 500ms of audio in buffer
            int samplesRead = record1.read(buffer1, 0, RECORD1_NUM_SAMPLES);
            assertTrue(samplesRead >= RECORD1_NUM_SAMPLES);


            final int RECORD2_START_TIME_MS = 100;
            MediaSyncEvent event = record1.shareAudioHistory(mPackageName,
                    (long) RECORD2_START_TIME_MS /* startFromMillis */);
            assertEquals(event.getAudioSessionId(), record1.getAudioSessionId());

            record2 = new AudioRecord.Builder().setAudioFormat(new AudioFormat.Builder()
                                .setSampleRate(mSamplingRateHz)
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                            .setBufferSizeInBytes(mSamplingRateHz
                                * AudioFormat.getBytesPerSample(AudioFormat.ENCODING_PCM_16BIT))
                            .setSharedAudioEvent(event)
                            .build();
            assertEquals(AudioRecord.STATE_INITIALIZED, record2.getState());

            record2.startRecording();

            final int RECORD2_NUM_SAMPLES = mSamplingRateHz / 5;
            short[] buffer2 = new short[RECORD2_NUM_SAMPLES];

            samplesRead = record2.read(buffer2, 0, RECORD2_NUM_SAMPLES);
            assertTrue(samplesRead >= RECORD2_NUM_SAMPLES);

            record2.stop();
            record1.stop();


            // verify that the audio read by 2nd AudioRecord exactly matches the audio read
            // by 1st AudioRecord starting from the expected start time with a certain tolerance.
            final int FIRST_EXPECTED_SAMPLE = RECORD2_START_TIME_MS * mSamplingRateHz / 1000;
            // NOTE: START_TIME_TOLERANCE_MS must always be smaller than RECORD2_START_TIME_MS
            final int START_TIME_TOLERANCE_MS = 1;
            final int START_SAMPLE_TOLERANCE = START_TIME_TOLERANCE_MS * mSamplingRateHz / 1000;
            // let time for a resampler to converge by skipping samples at the beginning of the
            // record2 buffer before comparing to record1 buffer
            final int RESAMPLER_CONVERGENCE_MS = 5;
            final int RESAMPLER_CONVERGENCE_SAMPLE =
                    RESAMPLER_CONVERGENCE_MS * mSamplingRateHz / 1000;


            boolean buffersMatch = false;
            for (int i = -START_SAMPLE_TOLERANCE;
                    i < START_SAMPLE_TOLERANCE && !buffersMatch; i++) {
                int offset1 = i + FIRST_EXPECTED_SAMPLE;
                if (offset1 < 0) {
                    continue;
                }
                // unlikely: programming error
                if (RECORD1_NUM_SAMPLES - offset1 < RECORD2_NUM_SAMPLES) {
                    Log.w(TAG, "testCapturesMatch: " +
                            "invalid buffer1 size/buffer2 size/start ms combination!");
                    break;
                }

                buffersMatch = true;
                for (int j = RESAMPLER_CONVERGENCE_SAMPLE; j < RECORD2_NUM_SAMPLES; j++) {
                    if (buffer2[j] != buffer1[j + offset1]) {
                         buffersMatch = false;
                         break;
                     }
                }
            }
            assertTrue(buffersMatch);
        } finally {
            if (record1 != null) {
                record1.release();
            }
            if (record2 != null) {
                record2.release();
            }
        }
    }

    private void clearAudioserverPermissionCache() {
        try {
            SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation(),
                    "cmd media.audio_policy purge_permission-cache");
        } catch (IOException e) {
            fail("cannot purge audio server permission cache");
        }
    }

    private void setDefaultSamplingRate() {
        // Query the default sample rate of the audio HAL. This sample rate is
        // then used for the audio recording. Using the default sample rate ensures that
        // the sample rate converter in the AudioFlinger is bypassed and the test is not
        // influenced by its convergence behavior.
        final AudioRecord record = new AudioRecord.Builder()
                .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(AudioFormat.SAMPLE_RATE_UNSPECIFIED)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                .setBufferSizeInBytes(32000)
                .build();

        mSamplingRateHz = record.getSampleRate(); // finally, query the default sample rate
        Log.i(TAG, "Using default AudioRecord sample rate (Hz): " + record.getSampleRate());
        record.release();
    }
}
