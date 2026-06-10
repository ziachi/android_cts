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

// Test AAudio offload.

#define LOG_NDEBUG 0
#define LOG_TAG "AAudioTest"

#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>

#include <cstdio>
#include <cstdlib>
#include <memory>

#include "utils.h"

static constexpr int64_t WAIT_END_OF_PRESENTATION_MICROSECONDS = 1200000;

class AAudioOffloadTest : public AAudioCtsBase,
                          public ::testing::WithParamInterface<aaudio_format_t> {
protected:
    bool mEndOfPresentation = false;
    struct DataCallbackUserData {
        DataCallbackUserData(int32_t framesOfStream) : mFramesOfStream(framesOfStream) {}
        const int32_t mFramesOfStream;
        int32_t mFramesWritten = 0;
    };
    static void MyPresentationEndCallbackProc(AAudioStream *stream, void *userData);
    static aaudio_data_callback_result_t MyDataCallbackProc(AAudioStream *stream, void *userData,
                                                            void *audioData, int32_t numFrames);

    std::unique_ptr<DataCallbackUserData> mCbData;
};

// static
void AAudioOffloadTest::MyPresentationEndCallbackProc(AAudioStream * /*stream*/, void *userData) {
    bool *myData = static_cast<bool *>(userData);
    *myData = true;
}

// static
aaudio_data_callback_result_t AAudioOffloadTest::MyDataCallbackProc(AAudioStream *stream,
                                                                    void *userData,
                                                                    void * /*audioData*/,
                                                                    int32_t numFrames) {
    DataCallbackUserData *myData = static_cast<DataCallbackUserData *>(userData);
    myData->mFramesWritten += numFrames;
    if (myData->mFramesWritten >= myData->mFramesOfStream) {
        AAudioStream_setOffloadEndOfStream(stream);
        return AAUDIO_CALLBACK_RESULT_STOP;
    }
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

TEST_P(AAudioOffloadTest, testOffload) {
    AAudioStreamBuilder *builder = nullptr;
    AAudioStream *stream = nullptr;

    const aaudio_format_t format = GetParam();
    const int32_t sampleRate = 48000;
    const int32_t delay = 8;
    const int32_t padding = 16;

    EXPECT_EQ(AAUDIO_OK, AAudio_createStreamBuilder(&builder));
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_POWER_SAVING_OFFLOADED);
    AAudioStreamBuilder_setFormat(builder, format);
    AAudioStreamBuilder_setChannelMask(builder, AAUDIO_CHANNEL_STEREO);
    AAudioStreamBuilder_setSampleRate(builder, sampleRate);
    mEndOfPresentation = false;
    AAudioStreamBuilder_setPresentationEndCallback(builder, &MyPresentationEndCallbackProc,
                                                   &mEndOfPresentation);
    mCbData.reset(new DataCallbackUserData(sampleRate));
    AAudioStreamBuilder_setDataCallback(builder, &MyDataCallbackProc, mCbData.get());

    aaudio_result_t result = AAudioStreamBuilder_openStream(builder, &stream);
    AAudioStreamBuilder_delete(builder);
    if (result != AAUDIO_OK) {
        // Offload is not supported with the request.
        AAudioStream_close(stream);
        return;
    }
    EXPECT_EQ(AAUDIO_OK, result);
    EXPECT_EQ(AAUDIO_PERFORMANCE_MODE_POWER_SAVING_OFFLOADED,
              AAudioStream_getPerformanceMode(stream));
    if (isCompressedFormat(format)) {
        // The HAL will return error if there is not real compressed data written. Skip the data
        // transfer for compressed format for now.
        // TODO(b/392178400): Use real compress data for testing
        goto exit;
    }

    EXPECT_EQ(isCompressedFormat(format) ? AAUDIO_OK : AAUDIO_ERROR_UNIMPLEMENTED,
              AAudioStream_setOffloadDelayPadding(stream, delay, padding));
    EXPECT_EQ(isCompressedFormat(format) ? delay : AAUDIO_ERROR_UNIMPLEMENTED,
              AAudioStream_getOffloadDelay(stream));
    EXPECT_EQ(isCompressedFormat(format) ? padding : AAUDIO_ERROR_UNIMPLEMENTED,
              AAudioStream_getOffloadPadding(stream));

    EXPECT_EQ(AAUDIO_OK, AAudioStream_requestStart(stream));
    usleep(WAIT_END_OF_PRESENTATION_MICROSECONDS);
    EXPECT_TRUE(mEndOfPresentation);

exit:
    // These should not crash if NULL.
    AAudioStream_close(stream);
}

INSTANTIATE_TEST_CASE_P(Offload, AAudioOffloadTest,
                        ::testing::Values(AAUDIO_FORMAT_PCM_I16, AAUDIO_FORMAT_MP3,
                                          AAUDIO_FORMAT_AAC_LC, AAUDIO_FORMAT_AAC_HE_V1,
                                          AAUDIO_FORMAT_AAC_HE_V2, AAUDIO_FORMAT_AAC_ELD,
                                          AAUDIO_FORMAT_AAC_XHE, AAUDIO_FORMAT_OPUS));
