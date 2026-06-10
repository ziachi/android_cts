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

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

/**
 * A thread that runs a native audio loopback analyzer.
 */
public class NativeAnalyzerThread {
    private static final String TAG = "NativeAnalyzerThread";

    private Context mContext;

    // Stream IDs
    // These are the same os the constants in NativeAnalyzer.h and need to be kept in sync.
    public static final int NUM_STREAM_TYPES = 2;
    public static final int STREAM_INPUT = 0;
    public static final int STREAM_OUTPUT = 1;

    private final int mSecondsToRun = 5;
    private Handler mMessageHandler;
    private Thread mThread;
    private volatile boolean mEnabled = false;
    private volatile double mLatencyMillis = 0.0;
    private volatile double mConfidence = 0.0;
    private volatile int mSampleRate = 0;
    private volatile double mTimestampLatencyMillis = 0.0;
    private volatile boolean mHas24BitHardwareSupport = false;
    private volatile int mHardwareFormat = 0; // AAUDIO_FORMAT_UNSPECIFIED

    private volatile boolean[] mIsLowLatencyStream = new boolean[NUM_STREAM_TYPES];
    private volatile int[] mBurstFrames = new int[NUM_STREAM_TYPES];
    private volatile int[] mCapacityFrames = new int[NUM_STREAM_TYPES];
    private volatile boolean[] mIsMMapStream = new boolean[NUM_STREAM_TYPES];

    private int mInputPreset = 0;

    private int mInputDeviceId;
    private int mOutputDeviceId;

    static final int NATIVE_AUDIO_THREAD_MESSAGE_REC_STARTED = 892;
    static final int NATIVE_AUDIO_THREAD_MESSAGE_OPEN_ERROR = 893;
    static final int NATIVE_AUDIO_THREAD_MESSAGE_REC_ERROR = 894;
    static final int NATIVE_AUDIO_THREAD_MESSAGE_REC_COMPLETE = 895;
    static final int NATIVE_AUDIO_THREAD_MESSAGE_REC_COMPLETE_ERRORS = 896;
    static final int NATIVE_AUDIO_THREAD_MESSAGE_ANALYZING = 897;

    public NativeAnalyzerThread(Context context) {
        mContext = context;
    }

    public void setInputPreset(int inputPreset) {
        mInputPreset = inputPreset;
    }

    //JNI load
    static {
        try {
            System.loadLibrary("audioloopback_jni");
        } catch (UnsatisfiedLinkError e) {
            log("Error loading loopback JNI library");
            log("e: " + e);
            e.printStackTrace();
        }

        /* TODO: gracefully fail/notify if the library can't be loaded */
    }

    /**
     * @return native audio context
     */
    private native long openAudio(int inputDeviceID, int outputDeviceId);
    private native int startAudio(long audioContext);
    private native int stopAudio(long audioContext);
    private native int closeAudio(long audioContext);
    private native int getError(long audioContext);
    private native boolean isRecordingComplete(long audioContext);
    private native int analyze(long audioContext);
    private native double getLatencyMillis(long audioContext);
    private native double getConfidence(long audioContext);
    private native boolean has24BitHardwareSupport(long audioContext);
    private native int getHardwareFormat(long audioContext);

    // Stream Attributes
    private native boolean isLowlatency(long audioContext, int streamId);
    private native int getBurstFrames(long audioContext, int streamId);
    private native int getCapacityFrames(long audioContext, int streamId);
    private native boolean isMMapStream(long audioContext, int streamId);

    private native int getSampleRate(long audio_context);

    private native double measureTimestampLatencyMillis(long audioContext);

    public double getLatencyMillis() {
        return mLatencyMillis;
    }

    public double getConfidence() {
        return mConfidence;
    }

    public int getSampleRate() { return mSampleRate; }

    /**
     * @return whether 24 bit data formats are supported for the hardware
     */
    public boolean has24BitHardwareSupport() {
        return mHas24BitHardwareSupport;
    }

    public int getHardwareFormat() {
        return mHardwareFormat;
    }

    /**
     * @param streamId one of STREAM_INPUT or STREAM_OUTPUT
     * @return true if the specified stream is a low-latency stream.
     */
    public boolean isLowLatencyStream(int streamId) {
        if (streamId != STREAM_INPUT && streamId != STREAM_OUTPUT) {
            return false;
        }
        return mIsLowLatencyStream[streamId];
    }

    /**
     * @param streamId one of STREAM_INPUT or STREAM_OUTPUT
     * @return the number of burst frames for the specified stream
     */
    public int getBurstFrames(int streamId) {
        if (streamId != STREAM_INPUT && streamId != STREAM_OUTPUT) {
            return -1;
        }
        return mBurstFrames[streamId];
    }

    /**
     * @param streamId one of STREAM_INPUT or STREAM_OUTPUT
     * @return the capacity in frames for the specified stream
     */
    public int getCapacityFrames(int streamId) {
        if (streamId != STREAM_INPUT && streamId != STREAM_OUTPUT) {
            return -1;
        }
        return mCapacityFrames[streamId];
    }

    /**
     * @param streamId one of STREAM_INPUT or STREAM_OUTPUT
     * @return true if the specified stream is a MMAP stream.
     */
    public boolean isMMapStream(int streamId) {
        if (streamId != STREAM_INPUT && streamId != STREAM_OUTPUT) {
            return false;
        }
        return mIsMMapStream[streamId];
    }

    public double getTimestampLatencyMillis() {
        return mTimestampLatencyMillis;
    }

    public synchronized void startTest(int inputDeviceId, int outputDeviceId) {
        mInputDeviceId = inputDeviceId;
        mOutputDeviceId = outputDeviceId;

        if (mThread == null) {
            mEnabled = true;
            mThread = new Thread(mBackGroundTask);
            mThread.start();
        }
    }

    public synchronized void stopTest(int millis) throws InterruptedException {
        mEnabled = false;
        if (mThread != null) {
            mThread.interrupt();
            mThread.join(millis);
            mThread = null;
        }
    }

    private void sendMessage(int what) {
        if (mMessageHandler != null) {
            Message msg = Message.obtain();
            msg.what = what;
            mMessageHandler.sendMessage(msg);
        }
    }

    private Runnable mBackGroundTask = () -> {
        mLatencyMillis = 0.0;
        mConfidence = 0.0;
        mSampleRate = 0;
        mTimestampLatencyMillis = 0.0;

        boolean analysisComplete = false;

        log(" Started capture test");
        sendMessage(NATIVE_AUDIO_THREAD_MESSAGE_REC_STARTED);

        //TODO - route parameters
        long audioContext = openAudio(mInputDeviceId, mOutputDeviceId);
        log(String.format("audioContext = 0x%X",audioContext));

        if (audioContext == 0 ) {
            log(" ERROR at JNI initialization");
            sendMessage(NATIVE_AUDIO_THREAD_MESSAGE_OPEN_ERROR);
        }  else if (mEnabled) {
            int result = startAudio(audioContext);
            if (result < 0) {
                sendMessage(NATIVE_AUDIO_THREAD_MESSAGE_REC_ERROR);
                mEnabled = false;
            }
            mHas24BitHardwareSupport = has24BitHardwareSupport(audioContext);
            mHardwareFormat = getHardwareFormat(audioContext);

            mIsLowLatencyStream[STREAM_OUTPUT] = isLowlatency(audioContext, STREAM_OUTPUT);
            mIsLowLatencyStream[STREAM_INPUT] = isLowlatency(audioContext, STREAM_INPUT);

            mBurstFrames[STREAM_OUTPUT] = getBurstFrames(audioContext, STREAM_OUTPUT);
            mBurstFrames[STREAM_INPUT] = getBurstFrames(audioContext, STREAM_INPUT);

            mCapacityFrames[STREAM_OUTPUT] = getCapacityFrames(audioContext, STREAM_OUTPUT);
            mCapacityFrames[STREAM_INPUT] = getCapacityFrames(audioContext, STREAM_INPUT);

            mIsMMapStream[STREAM_OUTPUT] = isMMapStream(audioContext, STREAM_OUTPUT);
            mIsMMapStream[STREAM_INPUT] = isMMapStream(audioContext, STREAM_INPUT);

            final long timeoutMillis = mSecondsToRun * 1000;
            final long startedAtMillis = System.currentTimeMillis();
            boolean timedOut = false;
            int loopCounter = 0;
            while (mEnabled && !timedOut) {
                result = getError(audioContext);
                if (result < 0) {
                    sendMessage(NATIVE_AUDIO_THREAD_MESSAGE_REC_ERROR);
                    break;
                } else if (isRecordingComplete(audioContext)) {
                    mTimestampLatencyMillis = measureTimestampLatencyMillis(audioContext);
                    stopAudio(audioContext);

                    // Analyze the recording and measure latency.
                    mThread.setPriority(Thread.MAX_PRIORITY);
                    sendMessage(NATIVE_AUDIO_THREAD_MESSAGE_ANALYZING);
                    result = analyze(audioContext);
                    if (result < 0) {
                        break;
                    } else {
                        analysisComplete = true;
                    }
                    mLatencyMillis = getLatencyMillis(audioContext);
                    mConfidence = getConfidence(audioContext);
                    mSampleRate = getSampleRate(audioContext);
                    break;
                } else {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                long now = System.currentTimeMillis();
                timedOut = (now - startedAtMillis) > timeoutMillis;
            }
            log("latency: analyze returns " + result);
            closeAudio(audioContext);

            int what = (analysisComplete && result == 0)
                    ? NATIVE_AUDIO_THREAD_MESSAGE_REC_COMPLETE
                    : NATIVE_AUDIO_THREAD_MESSAGE_REC_COMPLETE_ERRORS;
            sendMessage(what);
        }
    };

    public void setMessageHandler(Handler messageHandler) {
        mMessageHandler = messageHandler;
    }

    private static void log(String msg) {
        Log.v("Loopback", msg);
    }

}  //end thread.
