/*
 * Copyright 2020 The Android Open Source Project
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
package org.hyphonate.megaaudio.recorder;

import android.util.Log;

import org.hyphonate.megaaudio.common.BuilderBase;

public class OboeRecorder extends Recorder {
    @SuppressWarnings("unused")
    private static final String TAG = OboeRecorder.class.getSimpleName();
    @SuppressWarnings("unused")
    private static final boolean LOG = true;

    private int mRecorderSubtype;
    private long mNativeRecorder;

    public OboeRecorder(AudioSinkProvider sinkProvider, int subType) {
        super(sinkProvider);
        if (LOG) {
            Log.d(TAG, "OboeRecorder()");
        }

        mRecorderSubtype = subType;
        mNativeRecorder = allocNativeRecorder(
                sinkProvider.allocNativeSink().getNativeObject(), mRecorderSubtype);
    }

    //
    // Lifecycle
    //
    @Override
    public int build(BuilderBase builder) {
        mChannelCount = builder.getChannelCount();
        mSampleRate = builder.getSampleRate();
        mNumExchangeFrames = builder.getNumExchangeFrames();
        mSharingMode = builder.getSharingMode();
        mPerformanceMode = builder.getPerformanceMode();
        mInputPreset = ((RecorderBuilder) builder).getInputPreset();

        if (LOG) {
            Log.i(TAG, "build()");
            Log.i(TAG, "  chans:" + mChannelCount);
            Log.i(TAG, "  rate: " + mSampleRate);
            Log.i(TAG, "  frames: " + mNumExchangeFrames);
            Log.i(TAG, "  perf mode: " + mPerformanceMode);
            Log.i(TAG, "  route device: " + builder.getRouteDeviceId());
            Log.i(TAG, "  preset: " + mInputPreset);
        }
        return trackBuild(buildStreamN(mNativeRecorder, mChannelCount, mSampleRate,
                mPerformanceMode, mSharingMode, builder.getRouteDeviceId(), mInputPreset));
    }

    @Override
    public int open() {
        if (LOG) {
            Log.d(TAG, "open()");
        }
        return trackOpen(openStreamN(mNativeRecorder));
    }

    @Override
    public int start() {
        if (LOG) {
            Log.d(TAG, "start()");
        }
        int retVal = startStreamN(mNativeRecorder, mRecorderSubtype);
        mRecording = retVal == 0;
        return trackStart(retVal);
    }

    @Override
    public int stop() {
        if (LOG) {
            Log.d(TAG, "stop()");
        }
        mRecording = false;
        return trackStop(stopStreamN(mNativeRecorder));
    }

    @Override
    public int close() {
        if (LOG) {
            Log.d(TAG, "close()");
        }
        return trackClose(closeStreamN(mNativeRecorder));
    }

    @Override
    public int teardown() {
        if (LOG) {
            Log.d(TAG, "teardown()");
        }
        int errCode = teardownStreamN(mNativeRecorder);
        mChannelCount = 0;
        mSampleRate = 0;

        return trackTeardown(errCode);
    }

    //
    // Attributes
    //
    public int getNumBufferFrames() {
        return getNumBufferFramesN(mNativeRecorder);
    }

    @Override
    public int getRoutedDeviceId() {
        return getRoutedDeviceIdN(mNativeRecorder);
    }

    @Override
    public int getSharingMode() {
        return getSharingModeN(mNativeRecorder);
    }

    @Override
    public int getChannelCount() {
        return getChannelCountN(mNativeRecorder);
    }

    @Override
    public boolean isMMap() {
        return isMMapN(mNativeRecorder);
    }

    /**
     * @return See StreamState constants
     */
    public int getStreamState() {
        return getStreamStateN(mNativeRecorder);
    }

    /**
     * @return The last error callback result (these must match Oboe). See Oboe constants
     */
    public int getLastErrorCallbackResult() {
        return getLastErrorCallbackResultN(mNativeRecorder);
    }

    private native long allocNativeRecorder(long nativeSink, int recorderSubtype);

    private native int getBufferFrameCountN(long nativeRecorder);
    private native void setInputPresetN(long nativeRecorder, int inputPreset);

    private native int getRoutedDeviceIdN(long nativeRecorder);

    private native int getSharingModeN(long nativeRecorder);

    private native int getChannelCountN(long nativeRecorder);

    private native boolean isMMapN(long nativeRecorder);

    private native int buildStreamN(long nativeRecorder, int channelCount, int sampleRate,
                                    int performanceMode, int sharingMode, int routeDeviceId,
                                    int inputPreset);

    private native int openStreamN(long nativeRecorder);

    private native int startStreamN(long nativeRecorder, int recorderSubtype);

    private native int stopStreamN(long nativeRecorder);

    private native int closeStreamN(long nativeRecorder);

    private native int teardownStreamN(long nativeRecorder);

    private native int getStreamStateN(long nativeRecorder);
    private native int getLastErrorCallbackResultN(long nativeRecorder);

    private native int getNumBufferFramesN(long nativeRecorder);
    private native int calcMinBufferFramesN(long nativeRecorder);
}
