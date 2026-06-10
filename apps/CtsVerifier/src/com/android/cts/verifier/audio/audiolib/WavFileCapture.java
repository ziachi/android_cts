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

package com.android.cts.verifier.audio.audiolib;

/**
 * Implements a facility for capturing (buffering) audio data from a native stream and writing
 * it out to a WAV format file.
 */
public class WavFileCapture {
    private static final String TAG = "WavFileCapture";
    private static final boolean LOG = false;

    private long mNativeWavFileCapture;

    private String mWavFilePath;

    public WavFileCapture() {
        getCapture();
    }

    /**
     * @return a pointer to the native WavFileCapture cast to a long
     */
    public long getCapture() {
        return mNativeWavFileCapture = getCapture_n();
    }

    /**
     * Specifies the location and filename for any capture files.
     * @param wavFileName - The full path name of the captured file.
     */
    public void setCaptureFile(String wavFileName) {
        mWavFilePath = wavFileName;

        setCaptureFile_n(mNativeWavFileCapture, mWavFilePath);
    }

    /**
     * @return The name of the captured file (if any)
     */
    public String getWavFilePath() {
        return mWavFilePath;
    }

    /**
     * Specifies the format of the captured WAV file name.
     * @param numChannels The number of channels
     * @param sampleRate The sample rate
     */
    public void setWavSpec(int numChannels, int sampleRate) {
        setWavSpec_n(mNativeWavFileCapture, numChannels, sampleRate);
    }

    /**
     * Starts the capture process
     */
    public void startCapture() {
        startCapture_n(mNativeWavFileCapture);
    }

    /**
     * completeCapture() status codes.
     * Note: These need to be kept in sync with the equivalent constants
     * in WavFileCapture.h.
     */
    public static final int CAPTURE_NOTDONE = 1;
    public static final int CAPTURE_SUCCESS = 0;
    public static final int CAPTURE_BADOPEN = -1;
    public static final int CAPTURE_BADWRITE = -2;
    /**
     * Finishes the capture process and saves the captured WAV data
     * @return a return code (from above) indicating the result of the file save.
     */
    public int completeCapture() {
        return completeCapture_n(mNativeWavFileCapture);
    }

    /**
     * Abandons the capture process. Discards any captured WAV data.
     */
    public void abandonCaptureData() {
        abandonCaptureData_n(mNativeWavFileCapture);
    }

    /*
     * JNI Interface
     */
    private native long getCapture_n();
    private native void setCaptureFile_n(long wavCaptureObj, String wavFilePath);
    private native void setWavSpec_n(long wavCaptureObj, int numChannels, int sampleRate);

    private native void startCapture_n(long wavCaptureObj);
    private native int completeCapture_n(long wavCaptureObj);
    private native void abandonCaptureData_n(long wavCaptureObj);
}
