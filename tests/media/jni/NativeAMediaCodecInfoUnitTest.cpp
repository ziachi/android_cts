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

//#define LOG_NDEBUG 0
#define LOG_TAG "NativeAMediaCodecInfoUnitTest"

#include <jni.h>
#include <media/NdkMediaCodecInfo.h>
#include <media/NdkMediaCodecStore.h>
#include <media/NdkMediaExtractor.h>
#include <sys/stat.h>

#include "NativeMediaCommon.h"

#include <vector>

template <typename T>
bool equals(const T& op1, const T& op2) {
    return op1 == op2;
}

template <>
bool equals(const char* const& op1, const char* const& op2) {
    if (op1 != nullptr && op2 != nullptr)
        return strcmp(op1, op2) == 0;
    else
        return op1 == op2;
}

template <>
bool equals(const AIntRange& op1, const AIntRange& op2) {
    return op1.mLower == op2.mLower && op1.mUpper == op2.mUpper;
}

template <>
bool equals(const AMediaCodecKind& kind1, const AMediaCodecKind& kind2) {
    return kind1 == kind2;
}

template <>
bool equals(const ADoubleRange& op1, const ADoubleRange& op2) {
    return op1.mLower == op2.mLower && op1.mUpper == op2.mUpper;
}

template <typename T>
std::string toString(const T& val) {
    return std::to_string(val);
}

template <>
std::string toString(const char* const& val) {
    if (val == nullptr) return "null";
    return std::string(val);
}

template <>
std::string toString(const AIntRange& val) {
    return StringFormat("range lower %d, range upper %d", val.mLower, val.mUpper);
}

template <>
std::string toString(const ADoubleRange& val) {
    return StringFormat("range lower %f, range upper %f", val.mLower, val.mUpper);
}

template <>
std::string toString(const AMediaFormat* const& val) {
    return std::string(AMediaFormat_toString((AMediaFormat*)val));
}

#define CLEANUP_IF_FALSE(cond) \
    if (!(isPass = (cond))) {  \
        goto CleanUp;          \
    }

class NativeAMediaCodecInfoUnitTest {
private:
    const char* mCodecName;
    const AMediaCodecInfo* mCodecInfo;
    const ACodecVideoCapabilities* mVideoCaps;
    const ACodecAudioCapabilities* mAudioCaps;
    const ACodecEncoderCapabilities* mEncoderCaps;
    std::vector<const ACodecPerformancePoint*> mPerformancePoints;
    std::string mErrorLogs;

    template <typename T, typename U>
    bool validateGetCodecMetadata(const T* obj, U (*func)(const T* obj), U expResultForInvalidArgs,
                                  U expResult, const char* funcName);

    template <typename T, typename U>
    bool validateGetCodecMetadataArgs(const T* obj, int32_t (*func)(const T* obj, const U arg),
                                      int32_t expResultForInvalidArgs, U arg, int32_t expResult,
                                      const char* funcName);

    template <typename T>
    bool validateGetCapabilities(const T* obj,
                                 media_status_t (*func)(const AMediaCodecInfo* info, const T** obj),
                                 media_status_t expResult, const char* funcName);

    template <typename T>
    bool validateGetCodecMetadataIntRange(const T* obj,
                                          media_status_t (*func)(const T* obj, AIntRange* outRange),
                                          AIntRange& expRange, const char* funcName);

    template <typename T, typename U>
    bool validateGetCodecMetadataArgsArray(const T* obj,
                                           media_status_t (*func)(const T* obj, const U** outArray,
                                                                  size_t* outCount),
                                           const U* expArray, size_t expCount,
                                           const char* funcName);

    template <typename T, typename U>
    bool validateGetCodecMetadataIntRangeFor(
            const T* obj, media_status_t (*func)(const T* obj, U input, AIntRange* outRange),
            U input, const AIntRange& expRange, const char* funcName);

    template <typename T, typename U>
    bool validateGetCodecMetadataDoubleRangeFor(
            const T* obj,
            media_status_t (*func)(const T* obj, U width, U height, ADoubleRange* outRange),
            U width, U height, const ADoubleRange& expRange, const char* funcName);

    template <typename T>
    bool validatePerformancePoint(const ACodecPerformancePoint* pp, const T* arg,
                                  int32_t (*func)(const ACodecPerformancePoint*, const T*),
                                  int32_t expected, const char* funcName);

public:
    NativeAMediaCodecInfoUnitTest(const char* codecName);
    NativeAMediaCodecInfoUnitTest(const char* codecName, bool testAudio, bool testVideo);
    ~NativeAMediaCodecInfoUnitTest() = default;

    bool validateCodecKind(int codecKind);
    bool validateIsVendor(bool isVendor);
    bool validateCanonicalName(const char* name);
    bool validateMaxSupportedInstances(int maxSupportedInstances);
    bool validateMediaCodecInfoType(int expectedCodecType);
    bool validateMediaType(const char* expectedMediaType);
    bool validateIsFeatureSupported(const char* feature, int hasSupport);
    bool validateIsFeatureRequired(const char* feature, int isRequired);
    bool validateIsFormatSupported(const char* file, const char* mediaType, bool isSupported);
    bool validateGetAudioCaps(bool isAudio);
    bool validateGetVideoCaps(bool isVideo);
    bool validateGetEncoderCaps(bool isEncoder);

    bool validateVideoCodecBitRateRange(int lower, int higher);
    bool validateVideoCodecWidthRange(int lower, int higher);
    bool validateVideoCodecHeightRange(int lower, int higher);
    bool validateVideoCodecFrameRatesRange(int lower, int higher);
    bool validateVideoCodecWidthAlignment(int alignment);
    bool validateVideoCodecHeightAlignment(int alignment);

    bool validateGetSupportedWidthsFor(int height, int expectedLower, int expectedUpper);
    bool validateGetSupportedHeightsFor(int width, int expectedLower, int expectedUpper);
    bool validateGetSupportedFrameRatesFor(int width, int height, double expectedLower,
                                           double expectedUpper);
    bool validateGetAchievableFrameRatesFor(int width, int height, double expectedLower,
                                            double expectedUpper);
    bool validateSizeSupport(int width, int height, bool expected);
    bool validateSizeAndRateSupport(int width, int height, double frameRate, bool expected);

    bool getPerformancePointsList(int expSize);
    bool validatePerformancePointCoversFormat(int width, int height, float frameRate, int expMap);
    bool validatePerformancePointCoversEqualsPoint(int width, int height, int frameRate,
                                                   int coversMap, int equalsMap);

    bool validateAudioCodecBitRateRange(int mLower, int mUpper);
    bool validateAudioCodecMaxInputChannelCount(int maxInputChannelCount);
    bool validateAudioCodecMinInputChannelCount(int minInputChannelCount);
    bool validateAudioCodecSupportedSampleRates(int* sampleRates, int count);
    bool validateAudioCodecSupportedSampleRateRanges(int* sampleRateRanges, int count);
    bool validateAudioCodecInputChannelCountRanges(int* channelCountRanges, int count);
    bool validateAudioCodecIsSampleRateSupported(int sampleRate, int isSupported);

    bool validateEncoderComplexityRange(int lower, int higher);
    bool validateEncoderQualityRange(int lower, int higher);
    bool validateEncoderIsBitrateModeSupported(int bitrateMode, int isSupported);

    std::string getErrorMsg() { return mErrorLogs; };
};

NativeAMediaCodecInfoUnitTest::NativeAMediaCodecInfoUnitTest(const char* codecName) {
    mCodecName = codecName;
    mCodecInfo = nullptr;
    mVideoCaps = nullptr;
    mAudioCaps = nullptr;
    mEncoderCaps = nullptr;
    if (__builtin_available(android 36, *)) {
        media_status_t val = AMediaCodecStore_getCodecInfo(codecName, &mCodecInfo);
        if (AMEDIA_OK != val) {
            mErrorLogs.append(
                    StringFormat("AMediaCodecStore_getCodecInfo returned with error %d \n", val));
            return;
        }
        if (equals(AMediaCodecInfo_getKind(mCodecInfo), AMediaCodecKind_ENCODER)) {
            val = AMediaCodecInfo_getEncoderCapabilities(mCodecInfo, &mEncoderCaps);
            if (AMEDIA_OK != val) {
                mErrorLogs.append(StringFormat("AMediaCodecInfo_getEncoderCapabilities "
                                               "returned with error %d \n",
                                               val));
            }
        }
    }
}

NativeAMediaCodecInfoUnitTest::NativeAMediaCodecInfoUnitTest(const char* codecName, bool testAudio,
                                                             bool testVideo)
      : NativeAMediaCodecInfoUnitTest(codecName) {
    if (__builtin_available(android 36, *)) {
        if (mCodecInfo != nullptr) {
            if (testVideo) {
                media_status_t val = AMediaCodecInfo_getVideoCapabilities(mCodecInfo, &mVideoCaps);
                if (AMEDIA_OK != val) {
                    mErrorLogs.append(StringFormat("AMediaCodecInfo_getVideoCapabilities returned "
                                                   "with error %d \n",
                                                   val));
                }
            }
            if (testAudio) {
                media_status_t val = AMediaCodecInfo_getAudioCapabilities(mCodecInfo, &mAudioCaps);
                if (AMEDIA_OK != val) {
                    mErrorLogs.append(StringFormat("AMediaCodecInfo_getAudioCapabilities returned "
                                                   "with error %d \n",
                                                   val));
                }
            }
        }
    }
}

template <typename T, typename U>
bool NativeAMediaCodecInfoUnitTest::validateGetCodecMetadata(const T* obj, U (*func)(const T* obj),
                                                             U expResultForInvalidArgs, U expResult,
                                                             const char* funcName) {
    if (__builtin_available(android 36, *)) {
        U got = func(nullptr);
        if (!equals(got, expResultForInvalidArgs)) {
            mErrorLogs.append(StringFormat("For invalid args %s returned %s expected %s \n",
                                           funcName, toString(got).c_str(),
                                           toString(expResultForInvalidArgs).c_str()));
        } else if (nullptr != obj) {
            got = func(obj);
            if (!equals(got, expResult)) {
                mErrorLogs.append(StringFormat("For codec %s, %s returned %s expected %s \n",
                                               mCodecName, funcName, toString(got).c_str(),
                                               toString(expResult).c_str()));
            } else {
                return true;
            }
        }
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateCodecKind(int expectedKind) {
    if (__builtin_available(android 36, *)) {
        return validateGetCodecMetadata<AMediaCodecInfo,
                                        AMediaCodecKind>(mCodecInfo, AMediaCodecInfo_getKind,
                                                         AMediaCodecKind_INVALID,
                                                         (AMediaCodecKind)expectedKind,
                                                         "AMediaCodecInfo_getKind");
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateIsVendor(bool isVendor) {
    if (__builtin_available(android 36, *)) {
        return validateGetCodecMetadata<AMediaCodecInfo, int32_t>(mCodecInfo,
                                                                  AMediaCodecInfo_isVendor, -1,
                                                                  isVendor,
                                                                  "AMediaCodecInfo_isVendor");
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateCanonicalName(const char* name) {
    if (__builtin_available(android 36, *)) {
        return validateGetCodecMetadata<AMediaCodecInfo,
                                        const char*>(mCodecInfo, AMediaCodecInfo_getCanonicalName,
                                                     nullptr, name,
                                                     "AMediaCodecInfo_getCanonicalName");
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateMaxSupportedInstances(int maxSupportedInstances) {
    if (__builtin_available(android 36, *)) {
        return validateGetCodecMetadata<AMediaCodecInfo,
                                        int32_t>(mCodecInfo,
                                                 AMediaCodecInfo_getMaxSupportedInstances, -1,
                                                 maxSupportedInstances,
                                                 "AMediaCodecInfo_getMaxSupportedInstances");
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateMediaCodecInfoType(int expectedCodecType) {
    if (__builtin_available(android 36, *)) {
        return validateGetCodecMetadata<AMediaCodecInfo,
                                        AMediaCodecType>(mCodecInfo,
                                                         AMediaCodecInfo_getMediaCodecInfoType,
                                                         AMediaCodecType_INVALID_CODEC_INFO,
                                                         (AMediaCodecType)expectedCodecType,
                                                         "AMediaCodecInfo_getMediaCodecInfoType");
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateMediaType(const char* expectedMediaType) {
    if (__builtin_available(android 36, *)) {
        return validateGetCodecMetadata<AMediaCodecInfo,
                                        const char*>(mCodecInfo, AMediaCodecInfo_getMediaType,
                                                     nullptr, expectedMediaType,
                                                     "AMediaCodecInfo_getMediaType");
    }
    return false;
}

template <typename T, typename U>
bool NativeAMediaCodecInfoUnitTest::validateGetCodecMetadataArgs(
        const T* obj, int32_t (*func)(const T* obj, const U arg), int32_t expResultForInvalidArgs,
        U arg, int32_t expResult, const char* funcName) {
    if (__builtin_available(android 36, *)) {
        int got = func(nullptr, arg);
        if (!equals(got, expResultForInvalidArgs)) {
            mErrorLogs.append(StringFormat("For invalid args %s returned %s expected %s \n",
                                           funcName, toString(got).c_str(),
                                           toString(expResultForInvalidArgs).c_str()));
            return false;
        }
        if (std::is_pointer_v<U>) {
            got = func(nullptr, (U)0);
            if (!equals(got, expResultForInvalidArgs)) {
                mErrorLogs.append(StringFormat("For invalid args %s returned %s expected %s \n",
                                               funcName, toString(got).c_str(),
                                               toString(expResultForInvalidArgs).c_str()));
                return false;
            }
        }
        if (nullptr != obj) {
            if (std::is_pointer_v<U>) {
                got = func(obj, (U)0);
                if (!equals(got, expResultForInvalidArgs)) {
                    mErrorLogs.append(StringFormat("For invalid args %s returned %s expected %s \n",
                                                   funcName, toString(got).c_str(),
                                                   toString(expResultForInvalidArgs).c_str()));
                    return false;
                }
            }
            got = func(obj, arg);
            if (!equals(got, expResult)) {
                mErrorLogs.append(
                        StringFormat("For codec %s, input %s, %s returned %s expected %s \n",
                                     mCodecName, toString(arg).c_str(), funcName,
                                     toString(got).c_str(), toString(expResult).c_str()));
                return false;
            }
            return true;
        }
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateIsFeatureSupported(const char* feature,
                                                               int hasSupport) {
    if (__builtin_available(android 36, *)) {
        return validateGetCodecMetadataArgs<AMediaCodecInfo,
                                            const char*>(mCodecInfo,
                                                         AMediaCodecInfo_isFeatureSupported, -1,
                                                         feature, hasSupport,
                                                         "AMediaCodecInfo_isFeatureSupported");
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateIsFeatureRequired(const char* feature, int isRequired) {
    if (__builtin_available(android 36, *)) {
        return validateGetCodecMetadataArgs<AMediaCodecInfo,
                                            const char*>(mCodecInfo,
                                                         AMediaCodecInfo_isFeatureRequired, -1,
                                                         feature, isRequired,
                                                         "AMediaCodecInfo_isFeatureRequired");
    }
    return false;
}

AMediaFormat* setUpExtractor(const char* srcFile, const char* mediaType) {
    FILE* fp = fopen(srcFile, "rbe");
    if (!fp) return nullptr;
    struct stat buf {};
    AMediaFormat* currFormat = nullptr;
    if (!fstat(fileno(fp), &buf)) {
        AMediaExtractor* extractor = AMediaExtractor_new();
        media_status_t res = AMediaExtractor_setDataSourceFd(extractor, fileno(fp), 0, buf.st_size);
        if (res == AMEDIA_OK) {
            for (size_t trackID = 0; trackID < AMediaExtractor_getTrackCount(extractor);
                 trackID++) {
                currFormat = AMediaExtractor_getTrackFormat(extractor, trackID);
                const char* currMediaType = nullptr;
                AMediaFormat_getString(currFormat, AMEDIAFORMAT_KEY_MIME, &currMediaType);
                if (strcmp(currMediaType, mediaType) == 0) {
                    break;
                }
                AMediaFormat_delete(currFormat);
                currFormat = nullptr;
            }
        }
        AMediaExtractor_delete(extractor);
    }
    fclose(fp);
    return currFormat;
}

bool NativeAMediaCodecInfoUnitTest::validateIsFormatSupported(const char* file,
                                                              const char* mediaType,
                                                              bool isSupported) {
    bool isPass = false;
    if (__builtin_available(android 36, *)) {
        AMediaFormat* format = setUpExtractor(file, mediaType);
        if (format == nullptr) {
            mErrorLogs.append(StringFormat("Encountered unknown error while getting track format "
                                           "from file %s, mediaType %s \n",
                                           file, mediaType));
            return false;
        }
        isPass = validateGetCodecMetadataArgs<
                AMediaCodecInfo, const AMediaFormat*>(mCodecInfo, AMediaCodecInfo_isFormatSupported,
                                                      -1, format, isSupported,
                                                      "AMediaCodecInfo_isFormatSupported");
        AMediaFormat_delete(format);
    }
    return isPass;
}

template <typename T>
bool NativeAMediaCodecInfoUnitTest::validateGetCapabilities(
        const T* obj, media_status_t (*func)(const AMediaCodecInfo* info, const T** obj),
        media_status_t expResult, const char* funcName) {
    if (__builtin_available(android 36, *)) {
        media_status_t status = func(nullptr, nullptr);
        if (AMEDIA_ERROR_INVALID_PARAMETER != status) {
            mErrorLogs.append(StringFormat("For invalid args, %s returned %d, expected %d\n",
                                           funcName, status, AMEDIA_ERROR_INVALID_PARAMETER));
            return false;
        }
        status = func(nullptr, &obj);
        if (AMEDIA_ERROR_INVALID_PARAMETER != status) {
            mErrorLogs.append(StringFormat("For invalid args, %s returned %d, expected %d\n",
                                           funcName, status, AMEDIA_ERROR_INVALID_PARAMETER));
            return false;
        }
        if (mCodecInfo != nullptr) {
            status = func(mCodecInfo, nullptr);
            if (AMEDIA_ERROR_INVALID_PARAMETER != status) {
                mErrorLogs.append(StringFormat("For invalid args, %s returned %d, expected %d\n",
                                               funcName, status, AMEDIA_ERROR_INVALID_PARAMETER));
                return false;
            }
            status = func(mCodecInfo, &obj);
            if (expResult != status) {
                mErrorLogs.append(StringFormat("For codec %s, %s returned %d, expected %d\n",
                                               mCodecName, funcName, status, expResult));
                return false;
            }
            return true;
        }
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateGetAudioCaps(bool isAudio) {
    if (__builtin_available(android 36, *)) {
        return validateGetCapabilities<
                ACodecAudioCapabilities>(mAudioCaps, AMediaCodecInfo_getAudioCapabilities,
                                         isAudio ? AMEDIA_OK : AMEDIA_ERROR_UNSUPPORTED,
                                         "AMediaCodecInfo_getAudioCapabilities");
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateGetVideoCaps(bool isVideo) {
    if (__builtin_available(android 36, *)) {
        return validateGetCapabilities<
                ACodecVideoCapabilities>(mVideoCaps, AMediaCodecInfo_getVideoCapabilities,
                                         isVideo ? AMEDIA_OK : AMEDIA_ERROR_UNSUPPORTED,
                                         "AMediaCodecInfo_getVideoCapabilities");
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateGetEncoderCaps(bool isEncoder) {
    if (__builtin_available(android 36, *)) {
        return validateGetCapabilities<
                ACodecEncoderCapabilities>(mEncoderCaps, AMediaCodecInfo_getEncoderCapabilities,
                                           isEncoder ? AMEDIA_OK : AMEDIA_ERROR_UNSUPPORTED,
                                           "AMediaCodecInfo_getEncoderCapabilities");
    }
    return false;
}

template <typename T>
bool NativeAMediaCodecInfoUnitTest::validateGetCodecMetadataIntRange(
        const T* obj, media_status_t (*func)(const T* obj, AIntRange* outRange),
        AIntRange& expRange, const char* funcName) {
    if (__builtin_available(android 36, *)) {
        media_status_t status = func(nullptr, nullptr);
        if (AMEDIA_ERROR_INVALID_PARAMETER != status) {
            mErrorLogs.append(StringFormat("For invalid args %s returned %d expected %d \n",
                                           funcName, status, AMEDIA_ERROR_INVALID_PARAMETER));
            return false;
        }
        AIntRange got;
        status = func(nullptr, &got);
        if (AMEDIA_ERROR_INVALID_PARAMETER != status) {
            mErrorLogs.append(StringFormat("For invalid args %s returned %d expected %d \n",
                                           funcName, status, AMEDIA_ERROR_INVALID_PARAMETER));
            return false;
        }
        if (obj != nullptr) {
            status = func(obj, nullptr);
            if (AMEDIA_ERROR_INVALID_PARAMETER != status) {
                mErrorLogs.append(StringFormat("For invalid args %s returned %d expected %d \n",
                                               funcName, status, AMEDIA_ERROR_INVALID_PARAMETER));
                return false;
            }
            status = func(obj, &got);
            if (AMEDIA_OK != status) {
                mErrorLogs.append(StringFormat("For codec %s, %s returned %d expected %d \n",
                                               mCodecName, funcName, status, AMEDIA_OK));
                return false;
            }
            if (!equals(expRange, got)) {
                mErrorLogs.append(StringFormat("For codec %s, %s returned %s, expected %s \n",
                                               mCodecName, funcName, toString(got).c_str(),
                                               toString(expRange).c_str()));
                return false;
            }
            return true;
        }
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateVideoCodecBitRateRange(int lower, int higher) {
    if (__builtin_available(android 36, *)) {
        AIntRange expected = {lower, higher};
        return validateGetCodecMetadataIntRange<
                ACodecVideoCapabilities>(mVideoCaps, ACodecVideoCapabilities_getBitrateRange,
                                         expected, "ACodecVideoCapabilities_getBitrateRange");
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateVideoCodecWidthRange(int lower, int higher) {
    if (__builtin_available(android 36, *)) {
        AIntRange expected = {lower, higher};
        return validateGetCodecMetadataIntRange<
                ACodecVideoCapabilities>(mVideoCaps, ACodecVideoCapabilities_getSupportedWidths,
                                         expected, "ACodecVideoCapabilities_getSupportedWidths");
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateVideoCodecHeightRange(int lower, int higher) {
    if (__builtin_available(android 36, *)) {
        AIntRange expected = {lower, higher};
        return validateGetCodecMetadataIntRange<
                ACodecVideoCapabilities>(mVideoCaps, ACodecVideoCapabilities_getSupportedHeights,
                                         expected, "ACodecVideoCapabilities_getSupportedHeights");
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateVideoCodecFrameRatesRange(int lower, int higher) {
    if (__builtin_available(android 36, *)) {
        AIntRange expected = {lower, higher};
        return validateGetCodecMetadataIntRange<
                ACodecVideoCapabilities>(mVideoCaps, ACodecVideoCapabilities_getSupportedFrameRates,
                                         expected,
                                         "ACodecVideoCapabilities_getSupportedFrameRates");
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateVideoCodecWidthAlignment(int alignment) {
    if (__builtin_available(android 36, *)) {
        return validateGetCodecMetadata<ACodecVideoCapabilities,
                                        int32_t>(mVideoCaps,
                                                 ACodecVideoCapabilities_getWidthAlignment, -1,
                                                 alignment,
                                                 "ACodecVideoCapabilities_getWidthAlignment");
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateVideoCodecHeightAlignment(int alignment) {
    if (__builtin_available(android 36, *)) {
        return validateGetCodecMetadata<ACodecVideoCapabilities,
                                        int32_t>(mVideoCaps,
                                                 ACodecVideoCapabilities_getHeightAlignment, -1,
                                                 alignment,
                                                 "ACodecVideoCapabilities_getHeightAlignment");
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateAudioCodecBitRateRange(int lower, int higher) {
    if (__builtin_available(android 36, *)) {
        AIntRange expected = {lower, higher};
        return validateGetCodecMetadataIntRange<
                ACodecAudioCapabilities>(mAudioCaps, ACodecAudioCapabilities_getBitrateRange,
                                         expected, "ACodecAudioCapabilities_getBitrateRange");
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateAudioCodecMaxInputChannelCount(
        int maxInputChannelCount) {
    if (__builtin_available(android 36, *)) {
        return validateGetCodecMetadata<ACodecAudioCapabilities,
                                        int32_t>(mAudioCaps,
                                                 ACodecAudioCapabilities_getMaxInputChannelCount,
                                                 -1, maxInputChannelCount,
                                                 "ACodecAudioCapabilities_getMaxInputChannelCount");
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateAudioCodecMinInputChannelCount(
        int minInputChannelCount) {
    if (__builtin_available(android 36, *)) {
        return validateGetCodecMetadata<ACodecAudioCapabilities,
                                        int32_t>(mAudioCaps,
                                                 ACodecAudioCapabilities_getMinInputChannelCount,
                                                 -1, minInputChannelCount,
                                                 "ACodecAudioCapabilities_getMinInputChannelCount");
    }
    return false;
}

template <typename T, typename U>
bool NativeAMediaCodecInfoUnitTest::validateGetCodecMetadataArgsArray(
        const T* obj, media_status_t (*func)(const T* obj, const U** outArray, size_t* outCount),
        const U* expArray, size_t expCount, const char* funcName) {
    if (__builtin_available(android 36, *)) {
        const U* gotArray = nullptr;
        size_t gotCount = 0;

        media_status_t status = func(nullptr, &gotArray, &gotCount);
        if (status != AMEDIA_ERROR_INVALID_PARAMETER) {
            mErrorLogs.append(StringFormat("For invalid args %s returned %d, expected %d\n",
                                           funcName, status, AMEDIA_ERROR_INVALID_PARAMETER));
            return false;
        }
        if (nullptr != obj) {
            status = func(obj, nullptr, &gotCount);
            if (status != AMEDIA_ERROR_INVALID_PARAMETER) {
                mErrorLogs.append(StringFormat("For invalid args %s returned %d, expected %d\n",
                                               funcName, status, AMEDIA_ERROR_INVALID_PARAMETER));
                return false;
            }
            status = func(obj, &gotArray, nullptr);
            if (status != AMEDIA_ERROR_INVALID_PARAMETER) {
                mErrorLogs.append(StringFormat("For invalid args %s returned %d, expected %d\n",
                                               funcName, status, AMEDIA_ERROR_INVALID_PARAMETER));
                return false;
            }
            status = func(obj, &gotArray, &gotCount);
            if (status != AMEDIA_OK) {
                mErrorLogs.append(
                        StringFormat("%s returned %d, expected %d\n", funcName, status, AMEDIA_OK));
                return false;
            }
            if (gotCount != expCount) {
                mErrorLogs.append(StringFormat("%s returned array count as %d, expected %d\n",
                                               funcName, gotCount, expCount));
                return false;
            }
            for (int i = 0; i < expCount; ++i) {
                if (!equals(expArray[i], gotArray[i])) {
                    mErrorLogs.append(StringFormat("For %s, array item at index %d: returned %s, "
                                                   "expected %s\n",
                                                   funcName, i, toString(gotArray[i]).c_str(),
                                                   toString(expArray[i]).c_str()));
                    return false;
                }
            }
            return true;
        }
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateAudioCodecSupportedSampleRates(int* sampleRates,
                                                                           int count) {
    if (__builtin_available(android 36, *)) {
        return validateGetCodecMetadataArgsArray<
                ACodecAudioCapabilities, int>(mAudioCaps,
                                              ACodecAudioCapabilities_getSupportedSampleRates,
                                              sampleRates, count,
                                              "ACodecAudioCapabilities_getSupportedSampleRates");
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateAudioCodecSupportedSampleRateRanges(
        int* sampleRateRanges, int count) {
    if (__builtin_available(android 36, *)) {
        AIntRange ranges[count];
        for (int i = 0; i < count; i++) {
            ranges[i].mLower = sampleRateRanges[2 * i];
            ranges[i].mUpper = sampleRateRanges[2 * i + 1];
        }
        return validateGetCodecMetadataArgsArray<
                ACodecAudioCapabilities,
                AIntRange>(mAudioCaps, ACodecAudioCapabilities_getSupportedSampleRateRanges, ranges,
                           count, "ACodecAudioCapabilities_getSupportedSampleRateRanges");
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateAudioCodecInputChannelCountRanges(
        int* channelCountRanges, int count) {
    if (__builtin_available(android 36, *)) {
        AIntRange ranges[count];
        for (int i = 0; i < count; i++) {
            ranges[i].mLower = channelCountRanges[2 * i];
            ranges[i].mUpper = channelCountRanges[2 * i + 1];
        }
        return validateGetCodecMetadataArgsArray<
                ACodecAudioCapabilities,
                AIntRange>(mAudioCaps, ACodecAudioCapabilities_getInputChannelCountRanges, ranges,
                           count, "ACodecAudioCapabilities_getInputChannelCountRanges");
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateAudioCodecIsSampleRateSupported(int sampleRate,
                                                                            int isSupported) {
    if (__builtin_available(android 36, *)) {
        return validateGetCodecMetadataArgs<
                ACodecAudioCapabilities, int32_t>(mAudioCaps,
                                                  ACodecAudioCapabilities_isSampleRateSupported, -1,
                                                  sampleRate, isSupported,
                                                  "ACodecAudioCapabilities_isSampleRateSupported");
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateEncoderIsBitrateModeSupported(int bitrateMode,
                                                                          int isSupported) {
    if (__builtin_available(android 36, *)) {
        return validateGetCodecMetadataArgs<
                ACodecEncoderCapabilities,
                ABitrateMode>(mEncoderCaps, ACodecEncoderCapabilities_isBitrateModeSupported, -1,
                              (ABitrateMode)bitrateMode, isSupported,
                              "ACodecEncoderCapabilities_isBitrateModeSupported");
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateEncoderComplexityRange(int lower, int higher) {
    if (__builtin_available(android 36, *)) {
        AIntRange expected = {lower, higher};
        return validateGetCodecMetadataIntRange<
                ACodecEncoderCapabilities>(mEncoderCaps,
                                           ACodecEncoderCapabilities_getComplexityRange, expected,
                                           "ACodecEncoderCapabilities_getComplexityRange");
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateEncoderQualityRange(int lower, int higher) {
    if (__builtin_available(android 36, *)) {
        AIntRange expected = {lower, higher};
        return validateGetCodecMetadataIntRange<
                ACodecEncoderCapabilities>(mEncoderCaps, ACodecEncoderCapabilities_getQualityRange,
                                           expected, "ACodecEncoderCapabilities_getQualityRange");
    }
    return false;
}

template <typename T, typename U>
bool NativeAMediaCodecInfoUnitTest::validateGetCodecMetadataIntRangeFor(
        const T* obj, media_status_t (*func)(const T* obj, U input, AIntRange* outRange), U input,
        const AIntRange& expRange, const char* funcName) {
    if (__builtin_available(android 36, *)) {
        AIntRange range;
        media_status_t status = func(nullptr, input, &range);
        if (status != AMEDIA_ERROR_INVALID_PARAMETER) {
            mErrorLogs.append(StringFormat("For invalid args %s returned %d expected "
                                           "AMEDIA_ERROR_INVALID_PARAMETER\n",
                                           funcName, status));
            return false;
        }
        if (nullptr != obj) {
            status = func(obj, input, nullptr);
            if (status != AMEDIA_ERROR_INVALID_PARAMETER) {
                mErrorLogs.append(StringFormat("For invalid args %s returned %d expected "
                                               "AMEDIA_ERROR_INVALID_PARAMETER\n",
                                               funcName, status));
                return false;
            }
            status = func(obj, input, &range);
            if (AMEDIA_ERROR_UNSUPPORTED == status) {
                if (!equals({-1, -1}, expRange)) {
                    mErrorLogs.append(StringFormat("For codec %s, %s returned "
                                                   "AMEDIA_ERROR_UNSUPPORTED but expected is %s \n",
                                                   mCodecName, funcName,
                                                   toString(expRange).c_str()));
                    return false;
                }
                return true;
            } else {
                if (AMEDIA_OK != status) {
                    mErrorLogs.append(
                            StringFormat("For codec %s, %s returned %d expected AMEDIA_OK\n",
                                         mCodecName, funcName, status));
                    return false;
                }
                if (!equals(range, expRange)) {
                    mErrorLogs.append(StringFormat("For codec %s with input %s, %s returned %s, "
                                                   "expected %s\n",
                                                   mCodecName, toString(input).c_str(), funcName,
                                                   toString(range).c_str(),
                                                   toString(expRange).c_str()));
                    return false;
                }
            }
        }
        return true;
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateGetSupportedWidthsFor(int height, int expectedLower,
                                                                  int expectedUpper) {
    if (__builtin_available(android 36, *)) {
        AIntRange expectedRange = {expectedLower, expectedUpper};
        return validateGetCodecMetadataIntRangeFor<
                ACodecVideoCapabilities, int>(mVideoCaps,
                                              ACodecVideoCapabilities_getSupportedWidthsFor, height,
                                              expectedRange,
                                              "ACodecVideoCapabilities_getSupportedWidthsFor");
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateGetSupportedHeightsFor(int width, int expectedLower,
                                                                   int expectedUpper) {
    if (__builtin_available(android 36, *)) {
        AIntRange expectedRange = {expectedLower, expectedUpper};
        return validateGetCodecMetadataIntRangeFor<
                ACodecVideoCapabilities, int>(mVideoCaps,
                                              ACodecVideoCapabilities_getSupportedHeightsFor, width,
                                              expectedRange,
                                              "ACodecVideoCapabilities_getSupportedHeightsFor");
    }
    return false;
}

template <typename T, typename U>
bool NativeAMediaCodecInfoUnitTest::validateGetCodecMetadataDoubleRangeFor(
        const T* obj,
        media_status_t (*func)(const T* obj, U width, U height, ADoubleRange* outRange), U width,
        U height, const ADoubleRange& expRange, const char* funcName) {
    if (__builtin_available(android 36, *)) {
        ADoubleRange range;
        media_status_t status = func(nullptr, width, height, &range);
        if (status != AMEDIA_ERROR_INVALID_PARAMETER) {
            mErrorLogs.append(StringFormat("For invalid args %s returned %d expected "
                                           "AMEDIA_ERROR_INVALID_PARAMETER\n",
                                           funcName, status));
            return false;
        }
        if (nullptr != obj) {
            status = func(obj, width, height, nullptr);
            if (status != AMEDIA_ERROR_INVALID_PARAMETER) {
                mErrorLogs.append(StringFormat("For invalid args %s returned %d expected "
                                               "AMEDIA_ERROR_INVALID_PARAMETER\n",
                                               funcName, status));
                return false;
            }
            status = func(obj, width, height, &range);
            if (AMEDIA_ERROR_UNSUPPORTED == status) {
                if (!(equals({-1, -1}, expRange) || equals({-2, -2}, expRange))) {
                    mErrorLogs.append(
                            StringFormat("For codec %s with width %s, height %s, %s returned "
                                         "AMEDIA_ERROR_UNSUPPORTED but expected %s \n",
                                         mCodecName, toString(width).c_str(),
                                         toString(height).c_str(), funcName,
                                         toString(expRange).c_str()));
                    return false;
                }
                return true;
            } else {
                if (AMEDIA_OK != status) {
                    mErrorLogs.append(
                            StringFormat("For codec %s, %s returned %d expected AMEDIA_OK\n",
                                         mCodecName, funcName, status));
                    return false;
                }
                if (!equals(range, expRange)) {
                    mErrorLogs.append(
                            StringFormat("For codec %s with width %s, height %s %s returned "
                                         "%s, expected %s\n",
                                         mCodecName, toString(width).c_str(),
                                         toString(height).c_str(), funcName,
                                         toString(range).c_str(), toString(expRange).c_str()));
                    return false;
                }
            }
        }
        return true;
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateGetSupportedFrameRatesFor(int width, int height,
                                                                      double expectedLower,
                                                                      double expectedUpper) {
    if (__builtin_available(android 36, *)) {
        ADoubleRange expectedRange = {expectedLower, expectedUpper};
        return validateGetCodecMetadataDoubleRangeFor<
                ACodecVideoCapabilities, int>(mVideoCaps,
                                              ACodecVideoCapabilities_getSupportedFrameRatesFor,
                                              width, height, expectedRange,
                                              "ACodecVideoCapabilities_getSupportedFrameRatesFor");
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateGetAchievableFrameRatesFor(int width, int height,
                                                                       double expectedLower,
                                                                       double expectedUpper) {
    if (__builtin_available(android 36, *)) {
        ADoubleRange expectedRange = {expectedLower, expectedUpper};
        return validateGetCodecMetadataDoubleRangeFor<
                ACodecVideoCapabilities, int>(mVideoCaps,
                                              ACodecVideoCapabilities_getAchievableFrameRatesFor,
                                              width, height, expectedRange,
                                              "ACodecVideoCapabilities_getAchievableFrameRatesFor");
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateSizeSupport(int width, int height,
                                                        bool expectedResult) {
    if (__builtin_available(android 36, *)) {
        int got = ACodecVideoCapabilities_isSizeSupported(nullptr, width, height);
        if (got != -1) {
            mErrorLogs.append(
                    StringFormat("For invalid args ACodecVideoCapabilities_isSizeSupported "
                                 "returned %d expected -1\n",
                                 got));
            return false;
        }
        if (mVideoCaps != nullptr) {
            got = ACodecVideoCapabilities_isSizeSupported(mVideoCaps, width, height);
            if (got != expectedResult) {
                mErrorLogs.append(StringFormat("For ACodecVideoCapabilities_isSizeSupported width "
                                               "%d, height %d, expected %d, returned %d\n",
                                               width, height, expectedResult, got));
                return false;
            }
            return true;
        }
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validateSizeAndRateSupport(int width, int height,
                                                               double frameRate, bool expected) {
    if (__builtin_available(android 36, *)) {
        int got =
                ACodecVideoCapabilities_areSizeAndRateSupported(nullptr, width, height, frameRate);
        if (got != -1) {
            mErrorLogs.append(
                    StringFormat("For invalid args ACodecVideoCapabilities_areSizeAndRateSupported "
                                 "returned %d expected -1\n",
                                 got));
            return false;
        }
        if (mVideoCaps != nullptr) {
            got = ACodecVideoCapabilities_areSizeAndRateSupported(mVideoCaps, width, height,
                                                                  frameRate);
            if (got != expected) {
                mErrorLogs.append(
                        StringFormat("For ACodecVideoCapabilities_areSizeAndRateSupported width "
                                     "%d, height %d, rate %f, expected %d, "
                                     "returned %d\n",
                                     width, height, frameRate, expected, got));
                return false;
            }
            return true;
        }
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::getPerformancePointsList(int expSize) {
    if (__builtin_available(android 36, *)) {
        const ACodecPerformancePoint* outPoint = nullptr;
        media_status_t status =
                ACodecVideoCapabilities_getNextSupportedPerformancePoint(nullptr, &outPoint);
        if (AMEDIA_ERROR_INVALID_PARAMETER != status) {
            mErrorLogs.append(
                    StringFormat("For invalid args "
                                 "ACodecVideoCapabilities_getNextSupportedPerformancePoint "
                                 "returned %d expected %d\n",
                                 status, AMEDIA_ERROR_INVALID_PARAMETER));
            return false;
        }
        status = ACodecVideoCapabilities_getNextSupportedPerformancePoint(mVideoCaps, nullptr);
        if (AMEDIA_ERROR_INVALID_PARAMETER != status) {
            mErrorLogs.append(
                    StringFormat("For invalid args "
                                 "ACodecVideoCapabilities_getNextSupportedPerformancePoint "
                                 "returned %d expected %d\n",
                                 status, AMEDIA_ERROR_INVALID_PARAMETER));
            return false;
        }
        do {
            status =
                    ACodecVideoCapabilities_getNextSupportedPerformancePoint(mVideoCaps, &outPoint);
            if (AMEDIA_ERROR_UNSUPPORTED == status && outPoint != nullptr) {
                mErrorLogs.append("reached end of performance points list, but outPerformancePoint "
                                  "is not null\n");
                return false;
            }
            if (AMEDIA_OK == status && outPoint == nullptr) {
                mErrorLogs.append(
                        "call to ACodecVideoCapabilities_getNextSupportedPerformancePoint "
                        "succeeded, but outPerformancePoint is null\n");
                return false;
            }
            if (AMEDIA_OK == status) {
                mPerformancePoints.push_back(outPoint);
            }
        } while (outPoint != nullptr);
        if (mPerformancePoints.size() != expSize) {
            mErrorLogs.append(StringFormat("Performance points list size exp %d, got %d \n",
                                           expSize, mPerformancePoints.size()));
            return false;
        }
        return true;
    }
    return false;
}

template <typename T>
bool NativeAMediaCodecInfoUnitTest::validatePerformancePoint(
        const ACodecPerformancePoint* pp, const T* arg,
        int32_t (*func)(const ACodecPerformancePoint*, const T*), int32_t expected,
        const char* funcName) {
    if (__builtin_available(android 36, *)) {
        auto got = func(nullptr, arg);
        if (-1 != got) {
            mErrorLogs.append(
                    StringFormat("for invalid args, %s returned %d expected -1\n", funcName, got));
            return false;
        }
        got = func(pp, nullptr);
        if (-1 != got) {
            mErrorLogs.append(
                    StringFormat("for invalid args, %s returned %d expected -1\n", funcName, got));
            return false;
        }
        got = func(pp, arg);
        if (expected != got) {
            mErrorLogs.append(
                    StringFormat("%s returned %d expected %d\n", funcName, got, expected));
            return false;
        }
        return true;
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validatePerformancePointCoversFormat(int width, int height,
                                                                         float frameRate,
                                                                         int expMap) {
    if (__builtin_available(android 36, *)) {
        AMediaFormat* format = AMediaFormat_new();
        const char* mediaType = AMediaCodecInfo_getMediaType(mCodecInfo);
        AMediaFormat_setString(format, AMEDIAFORMAT_KEY_MIME, mediaType);
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_WIDTH, width);
        AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_HEIGHT, height);
        AMediaFormat_setFloat(format, AMEDIAFORMAT_KEY_FRAME_RATE, frameRate);
        bool res;
        for (auto& it : mPerformancePoints) {
            res = validatePerformancePoint<AMediaFormat>(it, format,
                                                         ACodecPerformancePoint_coversFormat,
                                                         expMap & 1,
                                                         "ACodecPerformancePoint_coversFormat");
            if (!res) break;
            expMap >>= 1;
        }
        AMediaFormat_delete(format);
        return res;
    }
    return false;
}

bool NativeAMediaCodecInfoUnitTest::validatePerformancePointCoversEqualsPoint(int width, int height,
                                                                              int frameRate,
                                                                              int coversMap,
                                                                              int equalsMap) {
    if (__builtin_available(android 36, *)) {
        ACodecPerformancePoint* point = ACodecPerformancePoint_create(width, height, frameRate);
        if (point == nullptr) {
            mErrorLogs.append("ACodecPerformancePoint_create failed, expected non-null\n");
            return false;
        }
        bool res;
        for (auto& it : mPerformancePoints) {
            res = validatePerformancePoint<ACodecPerformancePoint>(it, point,
                                                                   ACodecPerformancePoint_covers,
                                                                   coversMap & 1,
                                                                   "ACodecPerformancePoint_covers");
            if (!res) break;
            coversMap >>= 1;
            res = validatePerformancePoint<ACodecPerformancePoint>(it, point,
                                                                   ACodecPerformancePoint_equals,
                                                                   equalsMap & 1,
                                                                   "ACodecPerformancePoint_equals");
            if (!res) break;
            equalsMap >>= 1;
        }
        ACodecPerformancePoint_destroy(point);
        if (res) {
            ACodecPerformancePoint_destroy(nullptr);
        }
        return res;
    }
    return false;
}

jboolean nativeTestAMediaCodecInfo(JNIEnv* env, jobject, jstring jCodecName, jboolean isEncoder,
                                   jint jCodecKind, jboolean isVendor, jstring jCanonicalName,
                                   jint jMaxSupportedInstances, jint jExpectedCodecType,
                                   jstring jMediaType, jobjectArray jFeaturesList,
                                   jint jFeatureSupportMap, jint jFeatureRequiredMap,
                                   jobjectArray jFileArray, jbooleanArray jIsFormatSupportedArray,
                                   jobject jRetMsg) {
    const char* codecName = env->GetStringUTFChars(jCodecName, nullptr);
    const char* canonicalName = env->GetStringUTFChars(jCanonicalName, nullptr);
    const char* mediaType = env->GetStringUTFChars(jMediaType, nullptr);
    auto testUtil = new NativeAMediaCodecInfoUnitTest(codecName);
    jsize featureCount = env->GetArrayLength(jFeaturesList);
    jsize formatCount = env->GetArrayLength(jFileArray);
    jstring jFeature, jFile;
    const char *feature = nullptr, *file = nullptr;
    jboolean* isFormatSupportedArray = nullptr;
    bool isPass;
    CLEANUP_IF_FALSE(testUtil->validateCodecKind(jCodecKind))
    CLEANUP_IF_FALSE(testUtil->validateIsVendor(isVendor))
    CLEANUP_IF_FALSE(testUtil->validateCanonicalName(canonicalName))
    CLEANUP_IF_FALSE(testUtil->validateMaxSupportedInstances(jMaxSupportedInstances))
    CLEANUP_IF_FALSE(testUtil->validateMediaCodecInfoType(jExpectedCodecType))
    CLEANUP_IF_FALSE(testUtil->validateMediaType(mediaType))
    for (auto i = 0; i < featureCount; i++) {
        jFeature = (jstring)env->GetObjectArrayElement(jFeaturesList, i);
        feature = env->GetStringUTFChars(jFeature, nullptr);
        CLEANUP_IF_FALSE(testUtil->validateIsFeatureSupported(feature, jFeatureSupportMap & 1))
        jFeatureSupportMap >>= 1;
        CLEANUP_IF_FALSE(testUtil->validateIsFeatureRequired(feature, jFeatureRequiredMap & 1))
        jFeatureRequiredMap >>= 1;
        env->ReleaseStringUTFChars(jFeature, feature);
        feature = nullptr;
    }
    isFormatSupportedArray = env->GetBooleanArrayElements(jIsFormatSupportedArray, nullptr);
    for (auto i = 0; i < formatCount; i++) {
        jFile = (jstring)env->GetObjectArrayElement(jFileArray, i);
        file = env->GetStringUTFChars(jFile, nullptr);
        CLEANUP_IF_FALSE(
                testUtil->validateIsFormatSupported(file, mediaType, isFormatSupportedArray[i]))
        env->ReleaseStringUTFChars(jFile, file);
        file = nullptr;
    }
    CLEANUP_IF_FALSE(
            testUtil->validateGetAudioCaps(strncmp(mediaType, "audio/", strlen("audio/")) == 0))
    CLEANUP_IF_FALSE(
            testUtil->validateGetVideoCaps(strncmp(mediaType, "video/", strlen("video/")) == 0 ||
                                           strcasecmp(mediaType, "image/vnd.android.heic") == 0))
    CLEANUP_IF_FALSE(testUtil->validateGetEncoderCaps(isEncoder))
CleanUp:
    std::string msg = isPass ? std::string{} : testUtil->getErrorMsg();
    delete testUtil;
    jclass clazz = env->GetObjectClass(jRetMsg);
    jmethodID mId =
            env->GetMethodID(clazz, "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
    env->CallObjectMethod(jRetMsg, mId, env->NewStringUTF(msg.c_str()));
    env->ReleaseStringUTFChars(jCodecName, codecName);
    env->ReleaseStringUTFChars(jCanonicalName, canonicalName);
    env->ReleaseStringUTFChars(jMediaType, mediaType);
    if (feature) env->ReleaseStringUTFChars(jFeature, feature);
    if (file) env->ReleaseStringUTFChars(jFile, file);
    if (isFormatSupportedArray)
        env->ReleaseBooleanArrayElements(jIsFormatSupportedArray, isFormatSupportedArray, 0);
    return static_cast<jboolean>(isPass);
}

jboolean nativeTestAMediaCodecInfoVideoCaps(JNIEnv* env, jobject, jstring jCodecName,
                                            jint jBitRateRangeLower, jint jBitRateRangeUpper,
                                            jint jSupportedWidthLower, jint jSupportedWidthUpper,
                                            jint jSupportedHeightLower, jint jSupportedHeightUpper,
                                            jint jSupportedFrameRateLower,
                                            jint jSupportedFrameRateUpper, jint jWidthAlignment,
                                            jint jHeightAlignment, jobject jRetMsg) {
    const char* codecName = env->GetStringUTFChars(jCodecName, nullptr);
    auto testUtil = new NativeAMediaCodecInfoUnitTest(codecName, false, true);
    bool isPass;
    CLEANUP_IF_FALSE(
            testUtil->validateVideoCodecBitRateRange(jBitRateRangeLower, jBitRateRangeUpper))
    CLEANUP_IF_FALSE(
            testUtil->validateVideoCodecWidthRange(jSupportedWidthLower, jSupportedWidthUpper))
    CLEANUP_IF_FALSE(
            testUtil->validateVideoCodecHeightRange(jSupportedHeightLower, jSupportedHeightUpper))
    CLEANUP_IF_FALSE(testUtil->validateVideoCodecFrameRatesRange(jSupportedFrameRateLower,
                                                                 jSupportedFrameRateUpper))
    CLEANUP_IF_FALSE(testUtil->validateVideoCodecWidthAlignment(jWidthAlignment))
    CLEANUP_IF_FALSE(testUtil->validateVideoCodecHeightAlignment(jHeightAlignment))
CleanUp:
    std::string msg = isPass ? std::string{} : testUtil->getErrorMsg();
    delete testUtil;
    jclass clazz = env->GetObjectClass(jRetMsg);
    jmethodID mId =
            env->GetMethodID(clazz, "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
    env->CallObjectMethod(jRetMsg, mId, env->NewStringUTF(msg.c_str()));
    env->ReleaseStringUTFChars(jCodecName, codecName);
    return static_cast<jboolean>(isPass);
}

jboolean nativeTestAMediaCodecInfoVideoCapsGetSupportFor(
        JNIEnv* env, jobject, jstring jCodecName, jint jTestWidth, jint jTestHeight,
        jdouble jFrameRate, jint jSupportedWidthLowerForHeight, jint jSupportedWidthUpperForHeight,
        jint jSupportedHeightLowerForWidth, jint jSupportedHeightUpperForWidth,
        jdouble jSupportedFrameRateLower, jdouble jSupportedFrameRateUpper,
        jdouble jAchievedFrameRateLower, jdouble jAchievedFrameRateUpper, jboolean jIsSizeSupported,
        jboolean jAreSizeAndRateSupported, jobject jRetMsg) {
    const char* codecName = env->GetStringUTFChars(jCodecName, nullptr);
    auto testUtil = new NativeAMediaCodecInfoUnitTest(codecName, false, true);
    bool isPass;
    CLEANUP_IF_FALSE(testUtil->validateGetSupportedWidthsFor(jTestHeight,
                                                             jSupportedWidthLowerForHeight,
                                                             jSupportedWidthUpperForHeight))
    CLEANUP_IF_FALSE(testUtil->validateGetSupportedHeightsFor(jTestWidth,
                                                              jSupportedHeightLowerForWidth,
                                                              jSupportedHeightUpperForWidth))
    CLEANUP_IF_FALSE(testUtil->validateGetSupportedFrameRatesFor(jTestWidth, jTestHeight,
                                                                 jSupportedFrameRateLower,
                                                                 jSupportedFrameRateUpper))
    CLEANUP_IF_FALSE(testUtil->validateGetAchievableFrameRatesFor(jTestWidth, jTestHeight,
                                                                  jAchievedFrameRateLower,
                                                                  jAchievedFrameRateUpper))
    CLEANUP_IF_FALSE(testUtil->validateSizeSupport(jTestWidth, jTestHeight, jIsSizeSupported))
    CLEANUP_IF_FALSE(testUtil->validateSizeAndRateSupport(jTestWidth, jTestHeight, jFrameRate,
                                                          jAreSizeAndRateSupported))
CleanUp:
    std::string msg = isPass ? std::string{} : testUtil->getErrorMsg();
    delete testUtil;
    jclass clazz = env->GetObjectClass(jRetMsg);
    jmethodID mId =
            env->GetMethodID(clazz, "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
    env->CallObjectMethod(jRetMsg, mId, env->NewStringUTF(msg.c_str()));
    env->ReleaseStringUTFChars(jCodecName, codecName);
    return static_cast<jboolean>(isPass);
}

jboolean nativeTestACodecPerformancePoint(JNIEnv* env, jobject, jstring jCodecName, jint jWidth,
                                          jint jHeight, jdouble jFrameRate, jint jCoversFormat,
                                          jint jCoversPoint, jint jEqualsPoint, jint jPpListSize,
                                          jobject jRetMsg) {
    const char* codecName = env->GetStringUTFChars(jCodecName, nullptr);
    auto testUtil = new NativeAMediaCodecInfoUnitTest(codecName, false, true);
    bool isPass;
    CLEANUP_IF_FALSE(testUtil->getPerformancePointsList(jPpListSize))
    if (jPpListSize != 0) {
        CLEANUP_IF_FALSE(testUtil->validatePerformancePointCoversFormat(jWidth, jHeight,
                                                                        (float)jFrameRate,
                                                                        jCoversFormat))
        CLEANUP_IF_FALSE(testUtil->validatePerformancePointCoversEqualsPoint(jWidth, jHeight,
                                                                             (int)jFrameRate,
                                                                             jCoversPoint,
                                                                             jEqualsPoint))
    }
CleanUp:
    std::string msg = isPass ? std::string{} : testUtil->getErrorMsg();
    delete testUtil;
    env->ReleaseStringUTFChars(jCodecName, codecName);
    jclass clazz = env->GetObjectClass(jRetMsg);
    jmethodID mId =
            env->GetMethodID(clazz, "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
    env->CallObjectMethod(jRetMsg, mId, env->NewStringUTF(msg.c_str()));
    return static_cast<jboolean>(isPass);
}

jboolean nativeTestAMediaCodecInfoGetAudioCapabilities(
        JNIEnv* env, jobject, jstring jCodecName, jint jBitRateRangeLower, jint jBitRateRangeUpper,
        jint jMaxInputChannelCount, jint jMinInputChannelCount, jintArray jSampleRates,
        jintArray jSampleRateRanges, jintArray jInputChannelCountRanges,
        jintArray jStandardSampleRatesArray, jint jStandardSampleRatesSupportMap, jobject jRetMsg) {
    const char* codecName = env->GetStringUTFChars(jCodecName, nullptr);
    jint* sampleRatesArray = nullptr;
    jsize sampleRatesCount = 0;
    if (jSampleRates != nullptr) {
        sampleRatesArray = env->GetIntArrayElements(jSampleRates, nullptr);
        sampleRatesCount = env->GetArrayLength(jSampleRates);
    }
    jint* sampleRateRanges = env->GetIntArrayElements(jSampleRateRanges, nullptr);
    jsize sampleRateRangeCount = env->GetArrayLength(jSampleRateRanges);
    jint* channelCountRanges = env->GetIntArrayElements(jInputChannelCountRanges, nullptr);
    jsize channelCountRangeCount = env->GetArrayLength(jInputChannelCountRanges);
    jint* standardSampleRatesArray = env->GetIntArrayElements(jStandardSampleRatesArray, nullptr);
    jsize standardSampleRatesCount = env->GetArrayLength(jStandardSampleRatesArray);
    auto testUtil = new NativeAMediaCodecInfoUnitTest(codecName, true, false);
    bool isPass;
    CLEANUP_IF_FALSE(
            testUtil->validateAudioCodecBitRateRange(jBitRateRangeLower, jBitRateRangeUpper))
    CLEANUP_IF_FALSE(testUtil->validateAudioCodecMinInputChannelCount(jMinInputChannelCount))
    CLEANUP_IF_FALSE(testUtil->validateAudioCodecMaxInputChannelCount(jMaxInputChannelCount))
    if (sampleRatesArray) {
        CLEANUP_IF_FALSE(testUtil->validateAudioCodecSupportedSampleRates(sampleRatesArray,
                                                                          sampleRatesCount))
    }
    CLEANUP_IF_FALSE(
            testUtil->validateAudioCodecSupportedSampleRateRanges(sampleRateRanges,
                                                                  sampleRateRangeCount / 2))
    CLEANUP_IF_FALSE(
            testUtil->validateAudioCodecInputChannelCountRanges(channelCountRanges,
                                                                channelCountRangeCount / 2))
    for (auto i = 0; i < standardSampleRatesCount; i++) {
        CLEANUP_IF_FALSE(
                testUtil->validateAudioCodecIsSampleRateSupported(standardSampleRatesArray[i],
                                                                  jStandardSampleRatesSupportMap &
                                                                          1))
        jStandardSampleRatesSupportMap >>= 1;
    }
CleanUp:
    std::string msg = isPass ? std::string{} : testUtil->getErrorMsg();
    delete testUtil;
    env->ReleaseStringUTFChars(jCodecName, codecName);
    env->ReleaseIntArrayElements(jStandardSampleRatesArray, standardSampleRatesArray, 0);
    if (sampleRatesArray) env->ReleaseIntArrayElements(jSampleRates, sampleRatesArray, 0);
    env->ReleaseIntArrayElements(jSampleRateRanges, sampleRateRanges, 0);
    env->ReleaseIntArrayElements(jInputChannelCountRanges, channelCountRanges, 0);
    jclass clazz = env->GetObjectClass(jRetMsg);
    jmethodID mId =
            env->GetMethodID(clazz, "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
    env->CallObjectMethod(jRetMsg, mId, env->NewStringUTF(msg.c_str()));
    return static_cast<jboolean>(isPass);
}

jboolean nativeTestAMediaCodecInfoGetEncoderCapabilities(
        JNIEnv* env, jobject, jstring jCodecName, jint jComplexityRangeLower,
        jint jComplexityRangeUpper, jint jQualityRangeLower, jint jQualityRangeUpper,
        jint jBitrateModeSupportMap, jobject jRetMsg) {
    const char* codecName = env->GetStringUTFChars(jCodecName, nullptr);
    auto testUtil = new NativeAMediaCodecInfoUnitTest(codecName);
    bool isPass;
    CLEANUP_IF_FALSE(
            testUtil->validateEncoderComplexityRange(jComplexityRangeLower, jComplexityRangeUpper))
    CLEANUP_IF_FALSE(testUtil->validateEncoderQualityRange(jQualityRangeLower, jQualityRangeUpper))
    for (int i = 0; i < 4; i++) {
        CLEANUP_IF_FALSE(
                testUtil->validateEncoderIsBitrateModeSupported(i, jBitrateModeSupportMap & 1))
        jBitrateModeSupportMap >>= 1;
    }
CleanUp:
    std::string msg = isPass ? std::string{} : testUtil->getErrorMsg();
    delete testUtil;
    env->ReleaseStringUTFChars(jCodecName, codecName);
    jclass clazz = env->GetObjectClass(jRetMsg);
    jmethodID mId =
            env->GetMethodID(clazz, "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
    env->CallObjectMethod(jRetMsg, mId, env->NewStringUTF(msg.c_str()));
    return static_cast<jboolean>(isPass);
}

jboolean nativeTestAMediaCodecStoreGetSupportedTypes(JNIEnv* env, jobject,
                                                     jobjectArray jMediaTypesArray,
                                                     jintArray jModesArray, jobject jRetMsg) {
    bool isPass = false;
    if (__builtin_available(android 36, *)) {
        const AMediaCodecSupportedMediaType* outMediaTypes = nullptr;
        size_t outCount = 0;
        std::string errorLogs;
        jint* modesArray = env->GetIntArrayElements(jModesArray, nullptr);
        jsize modesCount = env->GetArrayLength(jModesArray);
        jstring jMediaType = nullptr;
        const char* mediaType = nullptr;

        auto status = AMediaCodecStore_getSupportedMediaTypes(nullptr, nullptr);
        if (status != AMEDIA_ERROR_INVALID_PARAMETER) {
            errorLogs.append(StringFormat("For invalid args, %s returned %d, expected "
                                          "AMEDIA_ERROR_INVALID_PARAMETER\n",
                                          "AMediaCodecSupportedMediaType", status));
            goto CleanUp;
        }
        status = AMediaCodecStore_getSupportedMediaTypes(nullptr, &outCount);
        if (status != AMEDIA_ERROR_INVALID_PARAMETER) {
            errorLogs.append(StringFormat("For invalid args, %s returned %d, expected "
                                          "AMEDIA_ERROR_INVALID_PARAMETER\n",
                                          "AMediaCodecSupportedMediaType", status));
            goto CleanUp;
        }
        status = AMediaCodecStore_getSupportedMediaTypes(&outMediaTypes, nullptr);
        if (status != AMEDIA_ERROR_INVALID_PARAMETER) {
            errorLogs.append(StringFormat("For invalid args, %s returned %d, expected "
                                          "AMEDIA_ERROR_INVALID_PARAMETER\n",
                                          "AMediaCodecSupportedMediaType", status));
            goto CleanUp;
        }
        status = AMediaCodecStore_getSupportedMediaTypes(&outMediaTypes, &outCount);
        if (status != AMEDIA_OK) {
            errorLogs.append(StringFormat("%s returned %d, expected AMEDIA_OK\n",
                                          "AMediaCodecSupportedMediaType", status));
            goto CleanUp;
        }
        if (outCount != modesCount) {
            errorLogs.append(StringFormat("%s returned %d supported media types, expected %d\n",
                                          "AMediaCodecSupportedMediaType", outCount, modesCount));
            goto CleanUp;
        }
        for (auto i = 0; i < modesCount; i++) {
            jMediaType = (jstring)env->GetObjectArrayElement(jMediaTypesArray, i);
            mediaType = env->GetStringUTFChars(jMediaType, nullptr);
            bool found = false;
            for (auto j = 0; j < outCount; j++) {
                if (strcmp(outMediaTypes[j].mMediaType, mediaType) == 0) {
                    found = true;
                    if (outMediaTypes[j].mMode != modesArray[i]) {
                        errorLogs.append(StringFormat("For mediaType %s, supported modes got is "
                                                      "%d, expected %d \n",
                                                      mediaType, outMediaTypes[j].mMode,
                                                      modesArray[i]));
                        goto CleanUp;
                    }
                }
            }
            if (!found) {
                errorLogs.append(
                        StringFormat("no entry seen for mediaType %s.\nAvailable entries are : ",
                                     mediaType));
                for (auto j = 0; j < outCount; j++) {
                    errorLogs.append(StringFormat("%s, ", outMediaTypes[j].mMediaType));
                }
                goto CleanUp;
            }
            env->ReleaseStringUTFChars(jMediaType, mediaType);
            mediaType = nullptr;
        }
        isPass = true;
    CleanUp:
        std::string msg = isPass ? std::string{} : errorLogs;
        env->ReleaseIntArrayElements(jModesArray, modesArray, 0);
        if (mediaType) env->ReleaseStringUTFChars(jMediaType, mediaType);
        jclass clazz = env->GetObjectClass(jRetMsg);
        jmethodID mId =
                env->GetMethodID(clazz, "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
        env->CallObjectMethod(jRetMsg, mId, env->NewStringUTF(msg.c_str()));
    }
    return static_cast<jboolean>(isPass);
}

jboolean nativeTestAMediaCodecStoreGetNextCodecsForFormat(JNIEnv* env, jobject,
                                                          jstring jFormatString,
                                                          jstring jFormatSeparator,
                                                          jobjectArray jCodecs, jboolean isEncoder,
                                                          jobject jRetMsg) {
    bool isPass = false;
    if (__builtin_available(android 36, *)) {
        media_status_t (*func)(const AMediaFormat* format, const AMediaCodecInfo** outCodecInfo);
        func = isEncoder ? AMediaCodecStore_findNextEncoderForFormat
                         : AMediaCodecStore_findNextDecoderForFormat;
        const char* label = isEncoder ? "AMediaCodecStore_findNextEncoderForFormat"
                                      : "AMediaCodecStore_findNextDecoderForFormat";
        std::string errorLogs;
        const AMediaCodecInfo* outCodecInfo;
        std::vector<const char*> codecs;
        media_status_t status;
        AMediaFormat* format = nullptr;
        const char* formatString = nullptr;
        const char* formatSeparator = nullptr;
        const char* formatStringBeautify = "null format";
        if (jFormatString != nullptr) {
            formatString = env->GetStringUTFChars(jFormatString, nullptr);
        }
        if (jFormatSeparator != nullptr) {
            formatSeparator = env->GetStringUTFChars(jFormatSeparator, nullptr);
        }
        if (formatString && formatSeparator) {
            format = deSerializeMediaFormat(formatString, formatSeparator);
            formatStringBeautify = AMediaFormat_toString(format);
        }
        jsize codecsCount = env->GetArrayLength(jCodecs);
        jstring jCodec;
        const char* codec = nullptr;
        status = func(format, nullptr);
        if (status != AMEDIA_ERROR_INVALID_PARAMETER) {
            errorLogs.append(StringFormat("For invalid args, %s returned %d, expected "
                                          "AMEDIA_ERROR_INVALID_PARAMETER\n",
                                          label, status));
            goto CleanUp;
        }
        outCodecInfo = nullptr;
        if (format != nullptr) {
            AMediaFormat* formatDup = AMediaFormat_new();
            AMediaFormat_clear(formatDup);
            status = func(formatDup, &outCodecInfo);
            AMediaFormat_delete(formatDup);
            if (status != AMEDIA_ERROR_INVALID_PARAMETER) {
                if (outCodecInfo != nullptr) {
                    errorLogs.append(
                            StringFormat("%s returned %d but format does not have key - 'mime'. "
                                         "expected AMEDIA_ERROR_INVALID_PARAMETER \n",
                                         label, status));
                    goto CleanUp;
                }
            }
        }
        while (1) {
            status = func(format, &outCodecInfo);
            if (status == AMEDIA_OK) {
                if (outCodecInfo == nullptr) {
                    errorLogs.append(StringFormat("%s returned AMEDIA_OK but outCodecInfo is "
                                                  "pointing to nullptr \n",
                                                  label));
                    goto CleanUp;
                }
                codecs.push_back(AMediaCodecInfo_getCanonicalName(outCodecInfo));
            } else if (status == AMEDIA_ERROR_UNSUPPORTED) {
                if (outCodecInfo != nullptr) {
                    errorLogs.append(StringFormat("%s returned AMEDIA_ERROR_UNSUPPORTED but "
                                                  "outCodecInfo is not pointing to nullptr \n",
                                                  label));
                    goto CleanUp;
                }
                break;
            }
        }
        if (codecs.size() != codecsCount) {
            errorLogs.append(StringFormat("For format %s codecs %d supported, expected %d\n",
                                          formatStringBeautify, codecs.size(), codecsCount));
            goto CleanUp;
        }
        for (auto i = 0; i < codecsCount; i++) {
            jCodec = (jstring)env->GetObjectArrayElement(jCodecs, i);
            codec = env->GetStringUTFChars(jCodec, nullptr);
            bool found = false;
            for (auto j = 0; j < codecs.size(); j++) {
                if (strcmp(codecs[j], codec) == 0) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                errorLogs.append(StringFormat("For format %s, sdk indicates %s is supported, but "
                                              "ndk indicates otherwise. List of ndk entries, \n",
                                              formatStringBeautify, codec));
                for (auto j = 0; j < codecs.size(); j++) {
                    errorLogs.append(StringFormat("%s, ", codecs[i]));
                }
                goto CleanUp;
            }
            env->ReleaseStringUTFChars(jCodec, codec);
            codec = nullptr;
        }
        isPass = true;
    CleanUp:
        std::string msg = isPass ? std::string{} : errorLogs;
        if (formatString) env->ReleaseStringUTFChars(jFormatString, formatString);
        if (formatSeparator) env->ReleaseStringUTFChars(jFormatSeparator, formatSeparator);
        if (format) AMediaFormat_delete(format);
        if (codec) env->ReleaseStringUTFChars(jCodec, codec);
        jclass clazz = env->GetObjectClass(jRetMsg);
        jmethodID mId =
                env->GetMethodID(clazz, "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
        env->CallObjectMethod(jRetMsg, mId, env->NewStringUTF(msg.c_str()));
    }
    return static_cast<jboolean>(isPass);
}

jboolean nativeTestAMediaCodecStoreGetCodecInfo(JNIEnv* env, jobject, jobject retMsg) {
    bool isPass = false;
    if (__builtin_available(android 36, *)) {
        std::string errorLogs;
        media_status_t status;
        const AMediaCodecInfo* outCodecInfo = nullptr;
        status = AMediaCodecStore_getCodecInfo(nullptr, nullptr);
        if (AMEDIA_ERROR_INVALID_PARAMETER != status) {
            errorLogs.append(StringFormat("For null parameters, returned %d, expected "
                                          "AMEDIA_ERROR_INVALID_PARAMETER\n",
                                          status));
            goto CleanUp;
        }
        status = AMediaCodecStore_getCodecInfo(nullptr, &outCodecInfo);
        if (AMEDIA_ERROR_INVALID_PARAMETER != status) {
            errorLogs.append(StringFormat("For null name parameter, returned %d, expected "
                                          "AMEDIA_ERROR_INVALID_PARAMETER\n",
                                          status));
            goto CleanUp;
        }
        status = AMediaCodecStore_getCodecInfo("c2.android.avc.decoder", nullptr);
        if (AMEDIA_ERROR_INVALID_PARAMETER != status) {
            errorLogs.append(StringFormat("For null output parameter, returned %d, expected "
                                          "AMEDIA_ERROR_INVALID_PARAMETER\n",
                                          status));
            goto CleanUp;
        }
        status = AMediaCodecStore_getCodecInfo("non.existent.codec", &outCodecInfo);
        if (AMEDIA_ERROR_UNSUPPORTED != status) {
            errorLogs.append(StringFormat("For non-existent codec, returned %d, expected "
                                          "AMEDIA_ERROR_UNSUPPORTED\n",
                                          status));
            goto CleanUp;
        }
        if (outCodecInfo != nullptr) {
            errorLogs.append("CodecInfo should be null for non-existent codec\n");
            goto CleanUp;
        }
        isPass = true;
    CleanUp:
        std::string msg = isPass ? std::string{} : errorLogs;
        jstring jErrorMsg = env->NewStringUTF(errorLogs.c_str());
        jclass clazz = env->GetObjectClass(retMsg);
        jmethodID mId =
                env->GetMethodID(clazz, "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
        env->CallObjectMethod(retMsg, mId, jErrorMsg);
        env->DeleteLocalRef(jErrorMsg);
    }
    return static_cast<jboolean>(isPass);
}

int registerAndroidMediaV2CtsNativeMediaCodecInfoUnitTest(JNIEnv* env) {
    const JNINativeMethod methodTable[] = {
            {"nativeTestAMediaCodecInfo",
             "(Ljava/lang/String;ZIZLjava/lang/String;IILjava/lang/String;[Ljava/lang/"
             "String;II[Ljava/lang/String;[ZLjava/lang/StringBuilder;)Z",
             (void*)nativeTestAMediaCodecInfo},
            {"nativeTestAMediaCodecInfoVideoCaps",
             "(Ljava/lang/String;IIIIIIIIIILjava/lang/StringBuilder;)Z",
             (void*)nativeTestAMediaCodecInfoVideoCaps},
            {"nativeTestAMediaCodecInfoVideoCapsGetSupportFor",
             "(Ljava/lang/String;IIDIIIIDDDDZZLjava/lang/StringBuilder;)Z",
             (void*)nativeTestAMediaCodecInfoVideoCapsGetSupportFor},
            {"nativeTestACodecPerformancePoint",
             "(Ljava/lang/String;IIDIIIILjava/lang/StringBuilder;)Z",
             (void*)nativeTestACodecPerformancePoint},
            {"nativeTestAMediaCodecInfoGetAudioCapabilities",
             "(Ljava/lang/String;IIII[I[I[I[IILjava/lang/StringBuilder;)Z",
             (void*)nativeTestAMediaCodecInfoGetAudioCapabilities},
            {"nativeTestAMediaCodecInfoGetEncoderCapabilities",
             "(Ljava/lang/String;IIIIILjava/lang/StringBuilder;)Z",
             (void*)nativeTestAMediaCodecInfoGetEncoderCapabilities},
    };
    jclass c = env->FindClass("android/mediav2/cts/NativeAMediaCodecInfoTest");
    return env->RegisterNatives(c, methodTable, sizeof(methodTable) / sizeof(JNINativeMethod));
}

int registerAndroidMediaV2CtsNativeMediaCodecStoreUnitTest(JNIEnv* env) {
    const JNINativeMethod methodTable[] = {
            {"nativeTestAMediaCodecStoreGetSupportedTypes",
             "([Ljava/lang/String;[ILjava/lang/StringBuilder;)Z",
             (void*)nativeTestAMediaCodecStoreGetSupportedTypes},
            {"nativeTestAMediaCodecStoreGetNextCodecsForFormat",
             "(Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;ZLjava/lang/StringBuilder;)Z",
             (void*)nativeTestAMediaCodecStoreGetNextCodecsForFormat},
            {"nativeTestAMediaCodecStoreGetCodecInfo", "(Ljava/lang/StringBuilder;)Z",
             (void*)nativeTestAMediaCodecStoreGetCodecInfo},
    };
    jclass c = env->FindClass("android/mediav2/cts/NativeAMediaCodecStoreTest");
    return env->RegisterNatives(c, methodTable, sizeof(methodTable) / sizeof(JNINativeMethod));
}

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    if (registerAndroidMediaV2CtsNativeMediaCodecInfoUnitTest(env) != JNI_OK) return JNI_ERR;
    if (registerAndroidMediaV2CtsNativeMediaCodecStoreUnitTest(env) != JNI_OK) return JNI_ERR;
    return JNI_VERSION_1_6;
}
