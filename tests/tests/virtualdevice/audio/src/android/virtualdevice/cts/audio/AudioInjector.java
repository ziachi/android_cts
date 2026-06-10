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
package android.virtualdevice.cts.audio;

import static android.media.AudioFormat.CHANNEL_IN_MONO;
import static android.media.AudioTrack.WRITE_BLOCKING;

import android.companion.virtual.audio.AudioInjection;
import android.companion.virtual.audio.VirtualAudioDevice;
import android.media.AudioFormat;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Utility class to perform virtual audio injection
 */
public class AudioInjector implements AutoCloseable {
    public static final int FREQUENCY = 264;
    public static final int SAMPLE_RATE = 44100;
    public static final int CHANNEL_COUNT = 1;
    public static final int AMPLITUDE = 32767;
    public static final int BUFFER_SIZE_IN_BYTES = 65536;
    public static final int NUMBER_OF_SAMPLES = computeNumSamples(SAMPLE_RATE, CHANNEL_COUNT);
    private static final Duration TIMEOUT = Duration.ofMillis(5000);

    public static final AudioFormat INJECTION_FORMAT = new AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(CHANNEL_IN_MONO)
            .build();

    private final ByteBuffer mAudioDataByteBuffer;
    private final VirtualAudioDevice mVirtualAudioDevice;

    private final AtomicBoolean mRunning = new AtomicBoolean(true);
    private final CountDownLatch mInjectionInitializedLatch = new CountDownLatch(1);

    private final Thread mAudioInjectorThread = new Thread() {
        @Override
        public void run() {
            super.run();

            AudioInjection audioInjection = mVirtualAudioDevice.startAudioInjection(
                    INJECTION_FORMAT);
            audioInjection.play();
            mInjectionInitializedLatch.countDown();
            while (mRunning.get() && !isInterrupted()) {
                int remaining = mAudioDataByteBuffer.remaining();
                while (remaining > 0 && mRunning.get() && !isInterrupted()) {
                    remaining -= audioInjection.write(mAudioDataByteBuffer,
                            mAudioDataByteBuffer.remaining(),
                            WRITE_BLOCKING);
                }
                mAudioDataByteBuffer.rewind();
            }
            audioInjection.stop();
        }
    };

    AudioInjector(ByteBuffer audioDataByteBuffer, VirtualAudioDevice virtualAudioDevice) {
        mAudioDataByteBuffer = audioDataByteBuffer;
        mVirtualAudioDevice = virtualAudioDevice;
    }

    /**
     * Start injecting audio to the VirtualAudioDevice
     */
    public void startInjection()
            throws TimeoutException, InterruptedException {
        mAudioInjectorThread.start();
        boolean success =
                mInjectionInitializedLatch.await(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        if (!success) {
            throw new TimeoutException(
                    "Timeout while waiting for audio injection initialization");
        }
    }


    @Override
    public void close() throws Exception {
        mRunning.set(false);
        mAudioInjectorThread.interrupt();
        mAudioInjectorThread.join();
    }

    static ByteBuffer createAudioData() {
        return createAudioData(SAMPLE_RATE, NUMBER_OF_SAMPLES, CHANNEL_COUNT, FREQUENCY, AMPLITUDE);
    }

    static ByteBuffer createAudioData(int samplingRate, int numSamples, int channelCount,
            double signalFrequencyHz, float amplitude) {
        ByteBuffer playBuffer =
                ByteBuffer.allocateDirect(numSamples * 2).order(ByteOrder.nativeOrder());
        final double multiplier = 2f * Math.PI * signalFrequencyHz / samplingRate;
        for (int i = 0; i < numSamples; ) {
            double vDouble = amplitude * Math.sin(multiplier * (i / channelCount));
            short v = (short) vDouble;
            for (int c = 0; c < channelCount; c++) {
                playBuffer.putShort(i * 2, v);
                i++;
            }
        }
        return playBuffer;
    }

    static int computeNumSamples(int samplingRate, int channelCount) {
        return (int) ((long) 1000 * samplingRate * channelCount / 1000);
    }
}
