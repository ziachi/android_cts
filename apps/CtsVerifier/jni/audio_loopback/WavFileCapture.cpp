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
#include <android/log.h>

#include <stdio.h>
#include <stdlib.h>
#include <fcntl.h>

#include "WavFileCapture.h"
#include "WavCaptureOutputStream.h"

const char * TAG = "WavFileCapture";

WavFileCapture sWavFileCapture;

WavFileCapture::WavFileCapture() : mCaptureActive(false) {
    mOutputStream = new WavCaptureOutputStream();
    mWavFileWriter = new WaveFileWriter(mOutputStream);
}

WavFileCapture::~WavFileCapture() {
    delete mOutputStream;
    delete mWavFileWriter;
}

void WavFileCapture::setCaptureFile(const char* wavFilePath) {
    mWavCapturePath = wavFilePath;
}

void WavFileCapture::setWavSpec(int numChannels, int sampleRate) {
    mNumChannels = numChannels;
    mSampleRate = sampleRate;
}

void WavFileCapture::startCapture() {
    mOutputStream->init();
    mWavFileWriter->reset();
    mCaptureActive = true;
}

int WavFileCapture::completeCapture() {
    if (!mCaptureActive) {
        return CAPTURE_NOTDONE;
    }

    int returnVal;
    FILE* file = fopen(mWavCapturePath.c_str(), "w");
    if (file == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, TAG,
                            "error opening capture file: %s", mWavCapturePath.c_str());
        returnVal = CAPTURE_BADOPEN;
    } else {
        int numWriteBytes = mOutputStream->length();
        int numWrittenBytes =
                fwrite(mOutputStream->getData(), sizeof(uint8_t), numWriteBytes, file);
        if (numWrittenBytes != numWriteBytes) {
            __android_log_print(ANDROID_LOG_ERROR, TAG,
                                "error writing capture file: %s", mWavCapturePath.c_str());
            returnVal = CAPTURE_BADWRITE;
        } else {
            returnVal = CAPTURE_SUCCESS;
        }
        fclose(file);
    }
    mCaptureActive = false;

    return returnVal;
}

void WavFileCapture::abandonCaptureData() {
    // TODO: implement when we have a caller for this.
    mCaptureActive = false;
}

void WavFileCapture::captureData(void *audioData, int32_t numFrames) {
    if (mCaptureActive) {
        mWavFileWriter->write((float*)audioData, 0, numFrames /* * numChannels? */);
    }
}

/*
 * JNI Interface
 */
#include <jni.h>

extern "C" {

JNIEXPORT jlong JNICALL
    Java_com_android_cts_verifier_audio_audiolib_WavFileCapture_getCapture_1n() {
    return (jlong)&sWavFileCapture;
}

JNIEXPORT void JNICALL
    Java_com_android_cts_verifier_audio_audiolib_WavFileCapture_setCaptureFile_1n(
            JNIEnv *env __unused, jobject obj __unused, jlong wavCaptureObj, jstring wavFilePath) {
    WavFileCapture* capture = (WavFileCapture*)wavCaptureObj;

    const char *captureFile = env->GetStringUTFChars(wavFilePath, 0);
    capture->setCaptureFile(captureFile);

    env->ReleaseStringUTFChars(wavFilePath, captureFile);
}

JNIEXPORT void JNICALL
    Java_com_android_cts_verifier_audio_audiolib_WavFileCapture_setWavSpec_1n(
            JNIEnv *env __unused, jobject obj __unused, jlong wavCaptureObj, jint numChannels, jint sampleRate) {
    WavFileCapture* capture = (WavFileCapture*)wavCaptureObj;
    capture->setWavSpec(numChannels, sampleRate);
}

JNIEXPORT void JNICALL
    Java_com_android_cts_verifier_audio_audiolib_WavFileCapture_startCapture_1n(
            JNIEnv *env __unused, jobject obj __unused, jlong wavCaptureObj) {
    WavFileCapture* capture = (WavFileCapture*)wavCaptureObj;
    capture->startCapture();
}

JNIEXPORT jint JNICALL
    Java_com_android_cts_verifier_audio_audiolib_WavFileCapture_completeCapture_1n(
            JNIEnv *env __unused, jobject obj __unused, jlong wavCaptureObj) {
    WavFileCapture* capture = (WavFileCapture*)wavCaptureObj;
    return capture->completeCapture();
}

JNIEXPORT void JNICALL
    Java_com_android_cts_verifier_audio_audiolib_WavFileCapture_abandonCapture_n(
            JNIEnv *env __unused, jobject obj __unused, jlong wavCaptureObj) {
    WavFileCapture* capture = (WavFileCapture*)wavCaptureObj;
    capture->abandonCaptureData();
}

} // extern "C"
