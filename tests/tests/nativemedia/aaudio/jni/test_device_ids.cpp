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

// Test AAudioStream_getDeviceIds

#include <aaudio/AAudio.h>
#include <gtest/gtest.h>
#include <stdio.h>
#include <unistd.h>

#include "test_aaudio.h"
#include "utils.h"

class AAudioTestDeviceIds : public AAudioCtsBase {};

TEST_F(AAudioTestDeviceIds, null_num_ids) {
    AAudioStreamBuilder *aaudioBuilder = nullptr;
    AAudioStream *aaudioStream = nullptr;
    ASSERT_EQ(AAUDIO_OK, AAudio_createStreamBuilder(&aaudioBuilder));
    ASSERT_EQ(AAUDIO_OK, AAudioStreamBuilder_openStream(aaudioBuilder, &aaudioStream));

    int32_t deviceIds[4];
    aaudio_result_t getDeviceIdResult = AAudioStream_getDeviceIds(aaudioStream, deviceIds, nullptr);
    ASSERT_EQ(getDeviceIdResult, AAUDIO_ERROR_ILLEGAL_ARGUMENT);

    AAudioStream_close(aaudioStream);
    AAudioStreamBuilder_delete(aaudioBuilder);
}

TEST_F(AAudioTestDeviceIds, small_num_ids) {
    AAudioStreamBuilder *aaudioBuilder = nullptr;
    AAudioStream *aaudioStream = nullptr;
    ASSERT_EQ(AAUDIO_OK, AAudio_createStreamBuilder(&aaudioBuilder));
    ASSERT_EQ(AAUDIO_OK, AAudioStreamBuilder_openStream(aaudioBuilder, &aaudioStream));

    int deviceIdSize = 0;
    int32_t deviceIds[deviceIdSize];
    aaudio_result_t getDeviceIdResult =
            AAudioStream_getDeviceIds(aaudioStream, deviceIds, &deviceIdSize);
    ASSERT_EQ(getDeviceIdResult, AAUDIO_ERROR_OUT_OF_RANGE);
    ASSERT_GT(deviceIdSize, 0);

    AAudioStream_close(aaudioStream);
    AAudioStreamBuilder_delete(aaudioBuilder);
}

TEST_F(AAudioTestDeviceIds, null_ids) {
    AAudioStreamBuilder *aaudioBuilder = nullptr;
    AAudioStream *aaudioStream = nullptr;
    ASSERT_EQ(AAUDIO_OK, AAudio_createStreamBuilder(&aaudioBuilder));
    ASSERT_EQ(AAUDIO_OK, AAudioStreamBuilder_openStream(aaudioBuilder, &aaudioStream));

    int deviceIdSize = 4;
    aaudio_result_t getDeviceIdResult =
            AAudioStream_getDeviceIds(aaudioStream, nullptr, &deviceIdSize);
    ASSERT_EQ(getDeviceIdResult, AAUDIO_ERROR_ILLEGAL_ARGUMENT);

    AAudioStream_close(aaudioStream);
    AAudioStreamBuilder_delete(aaudioBuilder);
}

TEST_F(AAudioTestDeviceIds, matches_get_device_id) {
    AAudioStreamBuilder *aaudioBuilder = nullptr;
    AAudioStream *aaudioStream = nullptr;
    ASSERT_EQ(AAUDIO_OK, AAudio_createStreamBuilder(&aaudioBuilder));
    ASSERT_EQ(AAUDIO_OK, AAudioStreamBuilder_openStream(aaudioBuilder, &aaudioStream));

    int deviceIdSize = 16;
    int32_t deviceIds[deviceIdSize];
    aaudio_result_t getDeviceIdResult =
            AAudioStream_getDeviceIds(aaudioStream, deviceIds, &deviceIdSize);
    ASSERT_EQ(getDeviceIdResult, AAUDIO_OK);
    ASSERT_EQ(AAudioStream_getDeviceId(aaudioStream), deviceIds[0]);
    ASSERT_GT(deviceIdSize, 0);
    for (int i = 1; i < deviceIdSize; i++) {
        ASSERT_NE(AAUDIO_UNSPECIFIED, deviceIds[i]);
    }

    AAudioStream_close(aaudioStream);
    AAudioStreamBuilder_delete(aaudioBuilder);
}
