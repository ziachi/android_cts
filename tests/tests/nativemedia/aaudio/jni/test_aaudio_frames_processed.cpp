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

// Tests that a reasonable number of frames were read and written.

#include <aaudio/AAudio.h>
#include <gtest/gtest.h>
#include <stdio.h>
#include <unistd.h>

#include "utils.h"

using TestAAudioFramesProcessedParams = std::tuple<aaudio_direction_t, int>;

enum {
    PARAM_DIRECTION = 0,
    PARAM_SAMPLE_RATE,
};

class FramesInfoSnapshot {
public:
    void update(int64_t framesRead, int64_t framesWritten, int64_t snapshotTime) {
        std::lock_guard<std::mutex> lock(mMutex);
        mFramesRead = framesRead;
        mFramesWritten = framesWritten;
        mSnapshotTime = snapshotTime;
    }

    void get(int64_t* framesRead, int64_t* framesWritten, int64_t* snapshotTime) {
        std::lock_guard<std::mutex> lock(mMutex);
        *framesRead = mFramesRead;
        *framesWritten = mFramesWritten;
        *snapshotTime = mSnapshotTime;
    }

private:
    int64_t mFramesRead = 0;
    int64_t mFramesWritten = 0;
    int64_t mSnapshotTime = 0;
    std::mutex mMutex;
};

class TestAAudioFramesProcessed
      : public AAudioCtsBase,
        public ::testing::WithParamInterface<TestAAudioFramesProcessedParams> {
public:
    static std::string getTestName(
            const ::testing::TestParamInfo<TestAAudioFramesProcessedParams>& info) {
        return std::string() + aaudioDirectionToString(std::get<PARAM_DIRECTION>(info.param)) +
                "__" + std::to_string(std::get<PARAM_SAMPLE_RATE>(info.param));
    }

protected:
    static const char* aaudioDirectionToString(aaudio_direction_t direction) {
        switch (direction) {
            case AAUDIO_DIRECTION_OUTPUT:
                return "OUTPUT";
            case AAUDIO_DIRECTION_INPUT:
                return "INPUT";
        }
        return "UNKNOWN";
    }

    void testConfiguration(aaudio_direction_t direction, int sampleRate) {
        if (direction == AAUDIO_DIRECTION_INPUT) {
            if (!deviceSupportsFeature(FEATURE_RECORDING)) return;
        } else {
            if (!deviceSupportsFeature(FEATURE_PLAYBACK)) return;
        }

        AAudioStreamBuilder* aaudioBuilder = nullptr;
        AAudioStream* aaudioStream = nullptr;

        // Use an AAudioStreamBuilder to contain requested parameters.
        ASSERT_EQ(AAUDIO_OK, AAudio_createStreamBuilder(&aaudioBuilder));

        // Request stream properties.
        AAudioStreamBuilder_setPerformanceMode(aaudioBuilder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
        AAudioStreamBuilder_setDirection(aaudioBuilder, direction);
        AAudioStreamBuilder_setFormat(aaudioBuilder, AAUDIO_FORMAT_PCM_FLOAT);
        AAudioStreamBuilder_setSampleRate(aaudioBuilder, sampleRate);
        AAudioStreamBuilder_setDataCallback(aaudioBuilder, SampleRateDataCallbackProc, this);

        // Create an AAudioStream using the Builder.
        EXPECT_EQ(AAUDIO_OK, AAudioStreamBuilder_openStream(aaudioBuilder, &aaudioStream));
        AAudioStreamBuilder_delete(aaudioBuilder);
        double sampleRateToleranceRatio = AAudioExtensions::getInstance().isMMapUsed(aaudioStream)
                ? kSampleRateToleranceRatioForLowLatency
                : kSampleRateToleranceRatio;

        mIsFirstCallback = true;
        EXPECT_EQ(AAUDIO_OK, AAudioStream_requestStart(aaudioStream));
        mStartTime = getNanoseconds(CLOCK_MONOTONIC);

        sleep(kProcessTimeSeconds);

        int64_t firstCallbackFramesRead;
        int64_t firstCallbackFramesWritten;
        int64_t firstCallbackTime;
        int64_t currentCallbackFramesRead;
        int64_t currentCallbackFramesWritten;
        int64_t currentCallbackTime;
        mFirstSnapshot.get(&firstCallbackFramesRead, &firstCallbackFramesWritten,
                           &firstCallbackTime);
        mCurrentSnapshot.get(&currentCallbackFramesRead, &currentCallbackFramesWritten,
                             &currentCallbackTime);
        double timeDiffInSeconds =
                (double)(currentCallbackTime - firstCallbackTime) / NANOS_PER_SECOND;

        // For input stream, frames written is the frames provided by the HAL.
        // For output stream, frames read is the frames consumed by the HAL.
        if (direction == AAUDIO_DIRECTION_INPUT) {
            double measuredSampleRate =
                    (currentCallbackFramesWritten - firstCallbackFramesWritten) / timeDiffInSeconds;
            EXPECT_NEAR(sampleRate, measuredSampleRate, sampleRate * sampleRateToleranceRatio);
        } else {
            double measuredSampleRate =
                    (currentCallbackFramesRead - firstCallbackFramesRead) / timeDiffInSeconds;
            EXPECT_NEAR(sampleRate, measuredSampleRate, sampleRate * sampleRateToleranceRatio);
        }

        EXPECT_EQ(AAUDIO_OK, AAudioStream_requestStop(aaudioStream));

        EXPECT_EQ(AAUDIO_OK, AAudioStream_close(aaudioStream));
    }

    static aaudio_data_callback_result_t SampleRateDataCallbackProc(AAudioStream* stream,
                                                                    void* userData,
                                                                    void* /*audioData*/,
                                                                    int32_t /*numFrames*/) {
        TestAAudioFramesProcessed* testClass = (TestAAudioFramesProcessed*)userData;
        int64_t currentTime = getNanoseconds(CLOCK_MONOTONIC);
        if (testClass->mStartTime == 0 || currentTime - testClass->mStartTime < kCallbackSkipTime) {
            // At first starting the stream, the callback may not be stable enough
            return AAUDIO_CALLBACK_RESULT_CONTINUE;
        }

        if (testClass->mIsFirstCallback) {
            testClass->mFirstSnapshot.update(AAudioStream_getFramesRead(stream),
                                             AAudioStream_getFramesWritten(stream), currentTime);
            testClass->mIsFirstCallback = false;
        } else {
            testClass->mCurrentSnapshot.update(AAudioStream_getFramesRead(stream),
                                               AAudioStream_getFramesWritten(stream), currentTime);
        }

        return AAUDIO_CALLBACK_RESULT_CONTINUE;
    }

    static constexpr int kProcessTimeSeconds = 5;
    static constexpr double kSampleRateToleranceRatioForLowLatency = .05;
    static constexpr double kSampleRateToleranceRatio = .1;
    static constexpr int64_t kCallbackSkipTime = 500 * NANOS_PER_MILLISECOND;

    std::atomic<int64_t> mStartTime = 0;
    bool mIsFirstCallback = true;
    FramesInfoSnapshot mFirstSnapshot;
    FramesInfoSnapshot mCurrentSnapshot;
};

TEST_P(TestAAudioFramesProcessed, TestFramesProcessed) {
    testConfiguration(std::get<PARAM_DIRECTION>(GetParam()),
                      std::get<PARAM_SAMPLE_RATE>(GetParam()));
}

INSTANTIATE_TEST_SUITE_P(
        AAudioFramesProcessed, TestAAudioFramesProcessed,
        ::testing::Values(TestAAudioFramesProcessedParams({AAUDIO_DIRECTION_OUTPUT, 8000}),
                          TestAAudioFramesProcessedParams({AAUDIO_DIRECTION_INPUT, 8000}),
                          TestAAudioFramesProcessedParams({AAUDIO_DIRECTION_OUTPUT, 44100}),
                          TestAAudioFramesProcessedParams({AAUDIO_DIRECTION_INPUT, 44100}),
                          TestAAudioFramesProcessedParams({AAUDIO_DIRECTION_OUTPUT, 96000}),
                          TestAAudioFramesProcessedParams({AAUDIO_DIRECTION_INPUT, 96000})),
        &TestAAudioFramesProcessed::getTestName);