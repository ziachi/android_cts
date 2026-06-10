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

package android.media.audio.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTimestamp;
import android.media.AudioTrack;
import android.media.audiopolicy.AudioMix;
import android.media.audiopolicy.AudioMixingRule;
import android.media.audiopolicy.AudioPolicy;
import android.os.Build;
import android.os.Looper;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.RequireNotAutomotive;
import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.PropertyUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(AndroidJUnit4.class)
public class LoopbackPassthroughTest {

    private static final String TAG = "LoopbackPassthroughTest";
    private Context mContext;
    private AudioManager mAudioManager;
    private AudioPolicy mAudioPolicy;
    private AudioFormat mMixFormat;
    private AudioSource mPlaybackSource;
    private AudioSource mRecordReferenceSource;
    private int mBytesToRead;
    private float mBitrateInBytesPerSecond;

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MODIFY_AUDIO_ROUTING);
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
    }

    @After
    public void tearDown() throws Exception {
        if (mPlaybackSource != null) {
            mPlaybackSource.release();
        }
        if (mRecordReferenceSource != null) {
            mRecordReferenceSource.release();
        }
        if (mAudioPolicy != null) {
            mAudioManager.unregisterAudioPolicy(mAudioPolicy);
            mAudioPolicy = null;
        }
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().dropShellPermissionIdentity();
    }

    private static class Assert {
        public static void fail(String message) {
            Log.e(TAG, message);
            org.junit.Assert.fail(message);
        }

        public static void assertNotNull(String message, Object object) {
            if (object == null) {
                Log.e(TAG, message);
            }
            org.junit.Assert.assertNotNull(message, object);
        }

        public static void assertTrue(String message, boolean condition) {
            if (!condition) {
                Log.e(TAG, message);
            }
            org.junit.Assert.assertTrue(message, condition);
        }

        public static <T> void assertEquals(String message, T expected, T actual) {
            if (!Objects.equals(expected, actual)) {
                Log.e(TAG, message + ". Expected " + String.valueOf(expected) + " but got "
                        + String.valueOf(actual) + ".");
            }
            org.junit.Assert.assertEquals(message, expected, actual);
        }
    }

    @CddTest(requirement="5.4.3/C-1-1")
    @Test
    @RequireNotAutomotive(reason = "Auto uses its own policy for routing")
    public void testPcmLoopback() {
        if (!supportsLoopback()
                || PropertyUtil.getVsrApiLevel() <= Build.VENDOR_API_2024_Q2) {
            return;
        }
        final int NUM_BUFFERS_TO_WRITE = 32;
        final int NUM_BUFFERS_NOT_DRAINED_TOLERANCE = 1;  // Read this number of buffers less
        // compared to the number of buffers written.
        final int sampleRate = 48000;
        mMixFormat = new AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build();
        mBitrateInBytesPerSecond = sampleRate * mMixFormat.getFrameSizeInBytes();
        mBytesToRead = (NUM_BUFFERS_TO_WRITE - NUM_BUFFERS_NOT_DRAINED_TOLERANCE)
                * AudioTrack.getMinBufferSize(mMixFormat.getSampleRate(),
                mMixFormat.getChannelMask(), mMixFormat.getEncoding());
        mPlaybackSource = new PcmAudioSource(mBytesToRead);
        mRecordReferenceSource = new PcmAudioSource(mBytesToRead);
        loopback(false);
    }

    @CddTest(requirement="5.4.3/C-1-1")
    @Test
    @RequireNotAutomotive(reason = "Auto uses its own policy for routing")
    public void testEac3JocLoopback() {
        if (!supportsLoopback()
                || PropertyUtil.getVsrApiLevel() <= Build.VENDOR_API_2024_Q2) {
            return;
        }
        final int EAC3_JOC_RESOURCE = R.raw.Living_Room_Atmos_6ch_640kbps_eac3_joc_10s;
        mBitrateInBytesPerSecond = (float) 640000 / 8;
        final int NUM_EAC3_JOC_FRAMES_TO_WRITE = 312;
        final int EAC3_JOC_FRAMES_NOT_DRAINED_TOLERANCE = 2;  // Read this number of frames less
        // compared to the number of frames written.
        // TODO: improve implementation to reduce EAC3_JOC_FRAMES_NOT_DRAINED_TOLERANCE
        final int NUM_EAC3_JOC_FRAMES_TO_READ =
                NUM_EAC3_JOC_FRAMES_TO_WRITE - EAC3_JOC_FRAMES_NOT_DRAINED_TOLERANCE;
        final int EAC3_JOC_BYTES_PER_FRAME = 2560;
        mBytesToRead = NUM_EAC3_JOC_FRAMES_TO_READ * EAC3_JOC_BYTES_PER_FRAME;
        mMixFormat = new AudioFormat.Builder()
                .setSampleRate(48000)
                .setEncoding(AudioFormat.ENCODING_E_AC3_JOC)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build();
        mPlaybackSource = new Eac3JocAudioSource(EAC3_JOC_RESOURCE);
        mRecordReferenceSource = new Eac3JocAudioSource(EAC3_JOC_RESOURCE);
        loopback(true);
    }

    private boolean supportsLoopback() {
        return mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
                && mContext.getPackageManager().hasSystemFeature(
                        PackageManager.FEATURE_AUDIO_OUTPUT);
    }

    private void loopback(boolean checkAudioData) {
        AudioAttributes mediaAttr = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build();
        AudioMixingRule mediaRule = new AudioMixingRule.Builder()
                .addRule(mediaAttr, AudioMixingRule.RULE_MATCH_ATTRIBUTE_USAGE)
                .build();
        AudioMix audioMix = new AudioMix.Builder(mediaRule)
                .setFormat(mMixFormat)
                .setRouteFlags(AudioMix.ROUTE_FLAG_LOOP_BACK)
                .build();

        mAudioPolicy = new AudioPolicy.Builder(mContext)
                .addMix(audioMix)
                .setLooper(Looper.getMainLooper())
                .build();

        if (mAudioManager.registerAudioPolicy(mAudioPolicy) != AudioManager.SUCCESS) {
            Assert.fail("failed to register audio policy");
        }
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Log.e(TAG, "main thread interrupted");
        }

        AudioRecord recorder = mAudioPolicy.createAudioRecordSink(audioMix);
        Assert.assertNotNull("didn't create AudioRecord sink", recorder);
        Assert.assertEquals("AudioRecord not initialized", AudioRecord.STATE_INITIALIZED,
                recorder.getState());
        AudioRecordThread audioRecordThread = new AudioRecordThread(recorder, checkAudioData);
        audioRecordThread.startRecording();

        // when audio policy is installed, 3P apps should be able to discover direct capabilities
        if (mPlaybackSource.getFormat() == AudioFormat.ENCODING_E_AC3_JOC) {
            Assert.assertEquals("direct playback not supported",
                    AudioManager.DIRECT_PLAYBACK_BITSTREAM_SUPPORTED,
                    AudioManager.getDirectPlaybackSupport(mMixFormat, mediaAttr)
                            | AudioManager.DIRECT_PLAYBACK_BITSTREAM_SUPPORTED);
        }

        AudioTrack player = null;
        try {
            final int kBufferSizeInBytes = AudioTrack.getMinBufferSize(mMixFormat.getSampleRate(),
                    mMixFormat.getChannelMask(), mMixFormat.getEncoding());
            player = new AudioTrack.Builder()
                    .setAudioAttributes(mediaAttr)
                    .setAudioFormat(mMixFormat)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(kBufferSizeInBytes)
                    .build();
            byte[] chunk = new byte[kBufferSizeInBytes];
            int totalBytesWritten = 0;
            AudioTimestamp timestamp = new AudioTimestamp();
            while (true) {
                int bytesRead = mPlaybackSource.read(chunk, kBufferSizeInBytes);
                if (bytesRead <= 0) {
                    // TODO: Test getUnderrunCount().
                    break;
                }
                int bytesToWrite = bytesRead;
                while (bytesToWrite > 0) {
                    int ret = player.write(chunk, bytesRead - bytesToWrite, bytesToWrite,
                            AudioTrack.WRITE_BLOCKING);
                    if (ret < 0) {
                        Assert.fail("Unable to write to AudioTrack, returns:" + ret);
                    } else {
                        bytesToWrite -= ret;
                        totalBytesWritten += ret;
                        Log.v(TAG, "wrote " + ret
                                + " bytes to AudioTrack. Bytes left:" + bytesToWrite
                                + " Offset relative to start:" + totalBytesWritten);
                        if (player.getPlayState() != AudioTrack.PLAYSTATE_PLAYING
                                && ret < kBufferSizeInBytes) {
                            player.play();
                            Log.v(TAG, "start play");
                            Assert.assertEquals("track not routed to remote submix",
                                    AudioDeviceInfo.TYPE_REMOTE_SUBMIX,
                                    player.getRoutedDevice().getType());
                        }
                        // check positions are correct within a latency tolerance of 1 second
                        player.getTimestamp(timestamp);
                        int headPosition = player.getPlaybackHeadPosition();
                        float writtenInMilliseconds =
                                (totalBytesWritten * 1000) / mBitrateInBytesPerSecond;
                        float expectedFramePositionPcmReferred =
                                (writtenInMilliseconds * mMixFormat.getSampleRate()) / 1000;
                        float minAllowedFramePosition =
                                expectedFramePositionPcmReferred - mMixFormat.getSampleRate();
                        float maxAllowedFramePosition =
                                expectedFramePositionPcmReferred + mMixFormat.getSampleRate();
                        Assert.assertTrue("timestamp position:" + timestamp.framePosition
                                        + " time:" + timestamp.nanoTime + " out of range",
                                timestamp.framePosition >= minAllowedFramePosition
                                        && timestamp.framePosition <= maxAllowedFramePosition);
                        Assert.assertTrue("head position:" + headPosition + " out of range",
                                headPosition >= minAllowedFramePosition
                                        && headPosition <= maxAllowedFramePosition);
                    }
                }
            }
        } catch (UnsupportedOperationException e) {
            Assert.fail("can't create audio track");
        } finally {
            if (player != null) {
                player.stop();
                player.release();
            }
        }

        try {
            Thread.sleep(1000);
            Assert.assertTrue("AudioRecord output differs from AudioTrack input",
                    audioRecordThread.isRecordingOutputCorrect());

        } catch (InterruptedException e) {
            Assert.fail("main thread interrupted");
        } finally {
            audioRecordThread.stopRecording();
        }
    }

    private interface AudioSource {
        // Read "numBytes" bytes of audio into "buffer".
        // @return Number of bytes actually read.
        int read(byte[] buffer, int numBytes);

        // Returns the offset in bytes read relative to the start of the source.
        int getOffsetInBytes();

        // Returns the audio format of the source.
        int getFormat();

        // Releases resources acquired by this instance.
        void release();
    }

    private static class PcmAudioSource implements AudioSource {
        private final int mTotalBytes;
        private int mBytesRead;

        public PcmAudioSource(int totalBytes) {
            mTotalBytes = totalBytes;
            mBytesRead = 0;
        }

        @Override
        public int read(byte[] buffer, int numBytes) {
            int bytesToRead = Math.min(numBytes, mTotalBytes - mBytesRead);
            for (int j = 0; j < bytesToRead; j++) {
                buffer[j] = (byte) ((mBytesRead + j) % 256);
            }
            mBytesRead += bytesToRead;
            return bytesToRead;
        }

        @Override
        public int getOffsetInBytes() {
            return mBytesRead;
        }

        @Override
        public int getFormat() {
            return AudioFormat.ENCODING_PCM_16BIT;
        }

        @Override
        public void release() {
        }
    }

    private class Eac3JocAudioSource implements AudioSource {
        private final InputStream mStream;
        private int mBytesRead;

        public Eac3JocAudioSource(int resource) {
            mStream = mContext.getResources().openRawResource(resource);
            Assert.assertNotNull("Stream is null when opening resource:" + resource, mStream);
            mBytesRead = 0;
        }

        @Override
        public int read(byte[] buffer, int numBytes) {
            try {
                int bytesRead = mStream.read(buffer, 0, numBytes);
                if (bytesRead < 0) {
                    return 0;
                }
                mBytesRead += bytesRead;
                return bytesRead;
            } catch (IOException e) {
                Assert.fail("Unable to read from stream: " + e.getMessage());
                return 0;
            }
        }

        @Override
        public int getOffsetInBytes() {
            return mBytesRead;
        }

        @Override
        public int getFormat() {
            return AudioFormat.ENCODING_E_AC3_JOC;
        }

        @Override
        public void release() {
            try {
                if (mStream != null) {
                    mStream.close();
                }
            } catch (IOException e) {
                Assert.fail("Unable to close asset file stream: " + e.getMessage());
            }
        }
    }

    private class AudioRecordThread extends Thread {
        private static final String TAG = "AudioRecordThread";
        private final AudioRecord mRecord;
        private final boolean mCheckAudioData;
        private final AtomicBoolean mStopped = new AtomicBoolean(false);
        private boolean mIsRecordingOutputCorrect = true;

        public AudioRecordThread(AudioRecord record, boolean checkAudioData) {
            mRecord = record;
            mCheckAudioData = checkAudioData;
        }

        public void startRecording() {
            mRecord.startRecording();
            Assert.assertEquals("recording didn't start", AudioRecord.RECORDSTATE_RECORDING,
                    mRecord.getRecordingState());
            Assert.assertEquals("recorder not routed from remote submix",
                    AudioDeviceInfo.TYPE_REMOTE_SUBMIX, mRecord.getRoutedDevice().getType());
            start();
        }

        public boolean isRecordingOutputCorrect() {
            return mIsRecordingOutputCorrect;
        }

        public void stopRecording() {
            mStopped.set(true);
            try {
                join();
            } catch (InterruptedException e) {
                Assert.fail("Unable to complete test successfully");
            }
        }

        public void run() {
            final int kBufferSizeInBytes = AudioRecord.getMinBufferSize(
                    mRecord.getFormat().getSampleRate(), mRecord.getFormat().getChannelMask(),
                    mRecord.getFormat().getEncoding());
            byte[] audioData = new byte[kBufferSizeInBytes];
            byte[] referenceData = new byte[kBufferSizeInBytes];
            while (!mStopped.get() && mBytesToRead > 0) {
                int ret = mRecord.read(audioData, 0, Math.min(mBytesToRead, kBufferSizeInBytes),
                        AudioRecord.READ_BLOCKING);
                if (ret > 0) {
                    Log.v(TAG, "read " + ret + " bytes");
                    if (mCheckAudioData) {
                        int srcOffset = mRecordReferenceSource.getOffsetInBytes();
                        mRecordReferenceSource.read(referenceData, ret);
                        if (!Arrays.equals(audioData, 0, ret, referenceData, 0, ret)
                                && mIsRecordingOutputCorrect) {
                            mIsRecordingOutputCorrect = false;
                            int bufOffset = 0;
                            while (bufOffset < Integer.min(ret, kBufferSizeInBytes)) {
                                if (audioData[bufOffset] != referenceData[bufOffset]) {
                                    break;
                                }
                                ++bufOffset;
                            }
                            srcOffset += bufOffset;
                            Log.e(TAG, "Detected difference in AudioRecord output at reference "
                                    + "source offset " + srcOffset + " bytes:");
                            final int NUM_DEBUG_PRINT_BYTES = 256;
                            String expectedBytesStr = "";
                            String actualBytesStr = "";
                            for (int i = 0; i < Integer.min(NUM_DEBUG_PRINT_BYTES, ret - bufOffset);
                                    ++i) {
                                expectedBytesStr += String.format("%02x,",
                                        referenceData[bufOffset + i]);
                                actualBytesStr += String.format("%02x,", audioData[bufOffset + i]);
                            }
                            Log.e(TAG, "Expected:" + expectedBytesStr);
                            Log.e(TAG, "Actual:  " + actualBytesStr);
                        }
                    }
                    mBytesToRead -= ret;
                } else if (ret < 0) {
                    Log.e(TAG, "read error:" + ret);
                    break;
                } else {
                    // No more data to read
                    break;
                }
            }
            mRecord.stop();
            mRecord.release();
        }
    }
}
