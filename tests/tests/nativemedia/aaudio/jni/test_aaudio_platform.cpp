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

// Test platform related API from AAudio

#include <aaudio/AAudio.h>
#include <gtest/gtest.h>

#include <functional>
#include <set>
#include <sstream>
#include <string>

#include "utils.h"

#define AAUDIO_CASE_ENUM(name) \
    case name:                 \
        return #name

static std::string deviceToString(AAudio_DeviceType device) {
    switch (device) {
        AAUDIO_CASE_ENUM(AAUDIO_DEVICE_BUILTIN_EARPIECE);
        AAUDIO_CASE_ENUM(AAUDIO_DEVICE_BUILTIN_SPEAKER);
        AAUDIO_CASE_ENUM(AAUDIO_DEVICE_WIRED_HEADSET);
        AAUDIO_CASE_ENUM(AAUDIO_DEVICE_WIRED_HEADPHONES);
        AAUDIO_CASE_ENUM(AAUDIO_DEVICE_LINE_ANALOG);
        AAUDIO_CASE_ENUM(AAUDIO_DEVICE_LINE_DIGITAL);
        AAUDIO_CASE_ENUM(AAUDIO_DEVICE_BLUETOOTH_SCO);
        AAUDIO_CASE_ENUM(AAUDIO_DEVICE_BLUETOOTH_A2DP);
        AAUDIO_CASE_ENUM(AAUDIO_DEVICE_HDMI);
        AAUDIO_CASE_ENUM(AAUDIO_DEVICE_HDMI_ARC);
        AAUDIO_CASE_ENUM(AAUDIO_DEVICE_USB_DEVICE);
        AAUDIO_CASE_ENUM(AAUDIO_DEVICE_USB_ACCESSORY);
        AAUDIO_CASE_ENUM(AAUDIO_DEVICE_DOCK);
        AAUDIO_CASE_ENUM(AAUDIO_DEVICE_FM);
        AAUDIO_CASE_ENUM(AAUDIO_DEVICE_BUILTIN_MIC);
        AAUDIO_CASE_ENUM(AAUDIO_DEVICE_FM_TUNER);
        AAUDIO_CASE_ENUM(AAUDIO_DEVICE_TV_TUNER);
        AAUDIO_CASE_ENUM(AAUDIO_DEVICE_TELEPHONY);
        AAUDIO_CASE_ENUM(AAUDIO_DEVICE_AUX_LINE);
        AAUDIO_CASE_ENUM(AAUDIO_DEVICE_IP);
        AAUDIO_CASE_ENUM(AAUDIO_DEVICE_BUS);
        AAUDIO_CASE_ENUM(AAUDIO_DEVICE_USB_HEADSET);
        AAUDIO_CASE_ENUM(AAUDIO_DEVICE_HEARING_AID);
        AAUDIO_CASE_ENUM(AAUDIO_DEVICE_BUILTIN_SPEAKER_SAFE);
        AAUDIO_CASE_ENUM(AAUDIO_DEVICE_REMOTE_SUBMIX);
        AAUDIO_CASE_ENUM(AAUDIO_DEVICE_BLE_HEADSET);
        AAUDIO_CASE_ENUM(AAUDIO_DEVICE_BLE_SPEAKER);
        AAUDIO_CASE_ENUM(AAUDIO_DEVICE_HDMI_EARC);
        AAUDIO_CASE_ENUM(AAUDIO_DEVICE_BLE_BROADCAST);
        AAUDIO_CASE_ENUM(AAUDIO_DEVICE_DOCK_ANALOG);
        default:
            return std::to_string(device);
    }
}

using PlatformPolicyTestParam = std::tuple<AAudio_DeviceType, aaudio_direction_t>;

class PlatformPolicyTest : public AAudioCtsBase,
                           public ::testing::WithParamInterface<PlatformPolicyTestParam> {
public:
    static std::string getTestName(const ::testing::TestParamInfo<PlatformPolicyTestParam>& info);

protected:
    enum policy_type_t {
        DEFAULT,
        EXCLUSIVE,
    };

    void runTest(AAudio_DeviceType device, aaudio_direction_t direction, policy_type_t policyType);
};

// static
std::string PlatformPolicyTest::getTestName(
        const ::testing::TestParamInfo<PlatformPolicyTestParam>& info) {
    return deviceToString(std::get<0>(info.param)) + "_as_" +
            (std::get<1>(info.param) == AAUDIO_DIRECTION_INPUT ? "input" : "output");
}

void PlatformPolicyTest::runTest(AAudio_DeviceType device, aaudio_direction_t direction,
                                 policy_type_t policyType) {
    const bool isValidValue = direction == AAUDIO_DIRECTION_INPUT
            ? (ALL_VALID_INPUT_DEVICES.count(device) != 0)
            : (ALL_VALID_OUTPUT_DEVICES.count(device) != 0);
    auto result = policyType == DEFAULT
            ? AAudioExtensions::getInstance().getPlatformMMapPolicy(device, direction)
            : AAudioExtensions::getInstance().getPlatformMMapExclusivePolicy(device, direction);
    if (isValidValue) {
        ASSERT_GT(ALL_VALID_POLICIES.count(result), 0);
    } else {
        ASSERT_EQ(AAUDIO_ERROR_ILLEGAL_ARGUMENT, result);
    }
}

TEST_P(PlatformPolicyTest, getDefaultPolicy) {
    ASSERT_NO_FATAL_FAILURE(runTest(std::get<0>(GetParam()), std::get<1>(GetParam()), DEFAULT));
}

TEST_P(PlatformPolicyTest, getExclusivePolicy) {
    ASSERT_NO_FATAL_FAILURE(runTest(std::get<0>(GetParam()), std::get<1>(GetParam()), EXCLUSIVE));
}

INSTANTIATE_TEST_CASE_P(
        AAudioTestPlatform, PlatformPolicyTest,
        ::testing::Combine(
                ::testing::Values(
                        AAUDIO_DEVICE_BUILTIN_EARPIECE, AAUDIO_DEVICE_BUILTIN_SPEAKER,
                        AAUDIO_DEVICE_WIRED_HEADSET, AAUDIO_DEVICE_WIRED_HEADPHONES,
                        AAUDIO_DEVICE_LINE_ANALOG, AAUDIO_DEVICE_LINE_DIGITAL,
                        AAUDIO_DEVICE_BLUETOOTH_SCO, AAUDIO_DEVICE_BLUETOOTH_A2DP,
                        AAUDIO_DEVICE_HDMI, AAUDIO_DEVICE_HDMI_ARC, AAUDIO_DEVICE_USB_DEVICE,
                        AAUDIO_DEVICE_USB_ACCESSORY, AAUDIO_DEVICE_DOCK, AAUDIO_DEVICE_FM,
                        AAUDIO_DEVICE_BUILTIN_MIC, AAUDIO_DEVICE_FM_TUNER, AAUDIO_DEVICE_TV_TUNER,
                        AAUDIO_DEVICE_TELEPHONY, AAUDIO_DEVICE_AUX_LINE, AAUDIO_DEVICE_IP,
                        AAUDIO_DEVICE_BUS, AAUDIO_DEVICE_USB_HEADSET, AAUDIO_DEVICE_HEARING_AID,
                        AAUDIO_DEVICE_BUILTIN_SPEAKER_SAFE, AAUDIO_DEVICE_REMOTE_SUBMIX,
                        AAUDIO_DEVICE_BLE_HEADSET, AAUDIO_DEVICE_BLE_SPEAKER,
                        AAUDIO_DEVICE_HDMI_EARC, AAUDIO_DEVICE_BLE_BROADCAST,
                        AAUDIO_DEVICE_DOCK_ANALOG,
                        1234567 /*random number that is not yet defined as a device type*/),
                ::testing::Values(AAUDIO_DIRECTION_INPUT, AAUDIO_DIRECTION_OUTPUT)),
        &PlatformPolicyTest::getTestName);
