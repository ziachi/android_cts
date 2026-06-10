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

#ifndef __WAVFILECAPTURE_H__
#define __WAVFILECAPTURE_H__

#include <string>

class WaveFileWriter;
class WavCaptureOutputStream;

class WavFileCapture {
public:
    WavFileCapture();
    ~WavFileCapture();

    void setCaptureFile(const char* wavFilePath);
    void setWavSpec(int numChannels, int sampleRate);

    void startCapture();

     /*
      * completeCapture() status codes.
      * Note: These need to be kept in sync with the equivalent constants
      * in WavFileCapture.java.
      */
    static const int CAPTURE_NOTDONE = 1;
    static const int CAPTURE_SUCCESS = 0;
    static const int CAPTURE_BADOPEN = -1;
    static const int CAPTURE_BADWRITE = -2;
    int completeCapture();
    void abandonCaptureData();

    void captureData(void *audioData, int32_t numFrames);

private:
    std::string mWavCapturePath;

    int mNumChannels;
    int mSampleRate;

    bool mCaptureActive;

    WaveFileWriter* mWavFileWriter;
    WavCaptureOutputStream*  mOutputStream;
};

#endif // __WAVFILECAPTURE_H__

