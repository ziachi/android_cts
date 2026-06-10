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

// #define LOG_NDEBUG 0
#define LOG_TAG "NativeSystemHealthTest"

#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <android-base/thread_annotations.h>
#include <android/system_health.h>
#include <binder/Status.h>
#include <inttypes.h>
#include <jni.h>
#include <log/log.h>
#include <math.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>
#include <utils/Errors.h>

#include <atomic>
#include <condition_variable>
#include <mutex>
#include <optional>
#include <thread>
#include <vector>

using namespace android;
using namespace std::chrono_literals;
using android::base::StringPrintf;

static inline std::optional<jstring> returnJString(JNIEnv* env, std::optional<std::string> result) {
    if (result.has_value()) {
        return std::make_optional(env->NewStringUTF(result.value().c_str()));
    } else {
        return std::nullopt;
    }
}

static inline std::optional<std::string> returnUnsupported() {
    return "Unsupported";
}

static inline std::pair<std::optional<std::string>, int64_t> getCpuHeadroomMinIntervalMillis() {
    int64_t minIntervalMillis = 0;
    int ret = ASystemHealth_getCpuHeadroomMinIntervalMillis(&minIntervalMillis);
    if (ret != OK) {
        if (ret == ENOTSUP) {
            return std::make_pair(returnUnsupported(), -1);
        }
        return std::make_pair(StringPrintf("Failed to get CPU headroom min interval: %d", ret), -2);
    }
    return std::make_pair(std::nullopt, minIntervalMillis);
}

static inline std::pair<std::optional<std::string>, int64_t> getGpuHeadroomMinIntervalMillis() {
    int64_t minIntervalMillis = 0;
    int ret = ASystemHealth_getGpuHeadroomMinIntervalMillis(&minIntervalMillis);
    if (ret != OK) {
        if (ret == ENOTSUP) {
            return std::make_pair(returnUnsupported(), -1);
        }
        return std::make_pair(StringPrintf("Failed to get GPU headroom min interval: %d", ret), -2);
    }
    return std::make_pair(std::nullopt, minIntervalMillis);
}

static inline std::optional<std::string> checkCpuHeadroom(const ACpuHeadroomParams* params,
                                                          int64_t minIntervalMillis) {
    if (!params) {
        params = ACpuHeadroomParams_create();
        if (!params) {
            return "ACpuHeadroomParams_create failed";
        }
    }
    std::this_thread::sleep_for(std::chrono::milliseconds(minIntervalMillis));
    float outHeadroom;
    int ret = ASystemHealth_getCpuHeadroom(params, &outHeadroom);
    if (ret != OK) {
        if (ret == ENOTSUP) {
            return returnUnsupported();
        }
        return StringPrintf("Failed to get CPU headroom result: %d", ret);
    }
    if (isnan(outHeadroom) || outHeadroom < 0.0f || outHeadroom > 100.0f) {
        return StringPrintf("Expected headroom in range [0, 100] but got %2.2f", outHeadroom);
    }
    return std::nullopt;
}

static inline std::optional<std::string> checkGpuHeadroom(const AGpuHeadroomParams* params,
                                                          int64_t minIntervalMillis) {
    if (!params) {
        params = AGpuHeadroomParams_create();
    }
    std::this_thread::sleep_for(std::chrono::milliseconds(minIntervalMillis));
    float outHeadroom;
    int ret = ASystemHealth_getGpuHeadroom(params, &outHeadroom);
    if (ret != OK) {
        if (ret == ENOTSUP) {
            return returnUnsupported();
        }
        return StringPrintf("Failed to get GPU headroom result: %d", ret);
    }
    if (isnan(outHeadroom) || outHeadroom < 0.0f || outHeadroom > 100.0f) {
        return StringPrintf("Expected headroom in range [0, 100] but got %2.2f", outHeadroom);
    }
    return std::nullopt;
}

static std::vector<std::thread> createThreads(int threadCount, std::atomic<bool>& stop,
                                              std::vector<int32_t>& tids, std::mutex& tids_mutex) {
    std::vector<std::thread> threads;
    threads.reserve(threadCount);
    tids.reserve(threadCount);
    for (int i = 0; i < threadCount; ++i) {
        std::thread thread([&stop, &tids, &tids_mutex]() {
            tids_mutex.lock();
            tids.push_back(gettid());
            tids_mutex.unlock();
            while (!stop.load()) {
                std::this_thread::sleep_for(std::chrono::milliseconds(1));
            }
        });
        threads.push_back(std::move(thread));
    }
    return threads;
}

static std::optional<std::string> testGetMaxCpuHeadroomTidsSize() {
    size_t size;
    auto res = ASystemHealth_getMaxCpuHeadroomTidsSize(&size);
    if (res == ENOTSUP) {
        return returnUnsupported();
    }
    if (size <= 0) {
        return StringPrintf("Expected positive max CPU headroom TID size but got %zu", size);
    }
    return std::nullopt;
}

static jstring nativeTestGetMaxCpuHeadroomTidsSize(JNIEnv* env, jclass) {
    return returnJString(env, testGetMaxCpuHeadroomTidsSize()).value_or(nullptr);
}

static std::optional<std::string> testGetCpuHeadroomDefault() {
    auto res = getCpuHeadroomMinIntervalMillis();
    if (res.second < 0) {
        return res.first;
    }
    auto params = ACpuHeadroomParams_create();
    return checkCpuHeadroom(params, res.second);
}

static jstring nativeTestGetCpuHeadroomDefault(JNIEnv* env, jclass) {
    return returnJString(env, testGetCpuHeadroomDefault()).value_or(nullptr);
}

static std::optional<std::string> testGetGpuHeadroomDefault() {
    auto res = getGpuHeadroomMinIntervalMillis();
    if (res.second < 0) {
        return res.first;
    }
    auto params = AGpuHeadroomParams_create();
    return checkGpuHeadroom(params, res.second);
}

static jstring nativeTestGetGpuHeadroomDefault(JNIEnv* env, jclass) {
    return returnJString(env, testGetGpuHeadroomDefault()).value_or(nullptr);
}

static std::optional<std::string> testGetCpuHeadroomAverage() {
    auto res = getCpuHeadroomMinIntervalMillis();
    if (res.second < 0) {
        return res.first;
    }
    auto params = ACpuHeadroomParams_create();
    ACpuHeadroomParams_setCalculationType(params, ACPU_HEADROOM_CALCULATION_TYPE_AVERAGE);
    if (ACpuHeadroomParams_getCalculationType(params) != ACPU_HEADROOM_CALCULATION_TYPE_AVERAGE) {
        return StringPrintf("ACpuHeadroomParams_getCalculationType return different value from "
                            "ACPU_HEADROOM_CALCULATION_TYPE_AVERAGE: %d",
                            ACpuHeadroomParams_getCalculationType(params));
    }
    return checkCpuHeadroom(params, res.second);
}

static jstring nativeTestGetCpuHeadroomAverage(JNIEnv* env, jclass) {
    return returnJString(env, testGetCpuHeadroomAverage()).value_or(nullptr);
}

static std::optional<std::string> testGetGpuHeadroomAverage() {
    auto res = getGpuHeadroomMinIntervalMillis();
    if (res.second < 0) {
        return res.first;
    }
    auto params = AGpuHeadroomParams_create();
    AGpuHeadroomParams_setCalculationType(params, AGPU_HEADROOM_CALCULATION_TYPE_AVERAGE);
    if (AGpuHeadroomParams_getCalculationType(params) != AGPU_HEADROOM_CALCULATION_TYPE_AVERAGE) {
        return StringPrintf("AGpuHeadroomParams_getCalculationType return different value from "
                            "AGPU_HEADROOM_CALCULATION_TYPE_AVERAGE: %d",
                            AGpuHeadroomParams_getCalculationType(params));
    }
    return checkGpuHeadroom(params, res.second);
}

static jstring nativeTestGetGpuHeadroomAverage(JNIEnv* env, jclass) {
    return returnJString(env, testGetGpuHeadroomAverage()).value_or(nullptr);
}

static std::optional<std::string> testGetCpuHeadroomCustomWindow() {
    auto intervalRes = getCpuHeadroomMinIntervalMillis();
    if (intervalRes.second < 0) {
        return intervalRes.first;
    }
    int32_t minMillis = 0;
    int32_t maxMillis = 0;
    int rangeRes = ASystemHealth_getCpuHeadroomCalculationWindowRange(&minMillis, &maxMillis);
    if (rangeRes != OK) {
        if (rangeRes == ENOTSUP) {
            return returnUnsupported();
        }
        return StringPrintf("Failed to get CPU headroom calculation window range: %d", rangeRes);
    }
    const int expectedMinMillis = 1000;
    if (minMillis > expectedMinMillis) {
        return StringPrintf("Min CPU headroom calculation window should be less or equal to %d but "
                            "got %d",
                            expectedMinMillis, minMillis);
    }
    const int expectedMaxMillis = 10000;
    if (maxMillis < expectedMaxMillis) {
        return StringPrintf("Max CPU headroom calculation window should be greater or equal to "
                            "%d but got %d",
                            expectedMaxMillis, maxMillis);
    }

    auto params = ACpuHeadroomParams_create();
    ACpuHeadroomParams_setCalculationWindowMillis(params, expectedMinMillis);
    if (ACpuHeadroomParams_getCalculationWindowMillis(params) != expectedMinMillis) {
        return StringPrintf("ACpuHeadroomParams_getCalculationWindowMillis return different value "
                            "from %d: %d",
                            expectedMinMillis,
                            ACpuHeadroomParams_getCalculationWindowMillis(params));
    }
    auto headroomRes = checkCpuHeadroom(params, intervalRes.second);
    if (headroomRes.has_value()) {
        return headroomRes;
    }

    ACpuHeadroomParams_setCalculationWindowMillis(params, expectedMaxMillis);
    if (ACpuHeadroomParams_getCalculationWindowMillis(params) != expectedMaxMillis) {
        return StringPrintf("ACpuHeadroomParams_getCalculationWindowMillis return different value "
                            "from %d: %d",
                            expectedMaxMillis,
                            ACpuHeadroomParams_getCalculationWindowMillis(params));
    }
    return checkCpuHeadroom(params, intervalRes.second);
}

static jstring nativeTestGetCpuHeadroomCustomWindow(JNIEnv* env, jclass) {
    return returnJString(env, testGetCpuHeadroomCustomWindow()).value_or(nullptr);
}

static std::optional<std::string> testGetGpuHeadroomCustomWindow() {
    auto intervalRes = getGpuHeadroomMinIntervalMillis();
    if (intervalRes.second < 0) {
        return intervalRes.first;
    }
    int32_t minMillis = 0;
    int32_t maxMillis = 0;
    int rangeRes = ASystemHealth_getGpuHeadroomCalculationWindowRange(&minMillis, &maxMillis);
    if (rangeRes != OK) {
        if (rangeRes == ENOTSUP) {
            return returnUnsupported();
        }
        return StringPrintf("Failed to get GPU headroom calculation window range: %d", rangeRes);
    }
    const int expectedMinMillis = 1000;
    if (minMillis > expectedMinMillis) {
        return StringPrintf("Min GPU headroom calculation window should be less or equal to %d but "
                            "got %d",
                            expectedMinMillis, minMillis);
    }
    const int expectedMaxMillis = 10000;
    if (maxMillis < expectedMaxMillis) {
        return StringPrintf("Max GPU headroom calculation window should be greater or equal to "
                            "%d but got %d",
                            expectedMaxMillis, maxMillis);
    }
    auto params = AGpuHeadroomParams_create();
    AGpuHeadroomParams_setCalculationWindowMillis(params, expectedMinMillis);
    if (AGpuHeadroomParams_getCalculationWindowMillis(params) != expectedMinMillis) {
        return StringPrintf("AGpuHeadroomParams_getCalculationWindowMillis return different value "
                            "from %d: %d",
                            expectedMinMillis,
                            AGpuHeadroomParams_getCalculationWindowMillis(params));
    }
    auto headroomRes = checkGpuHeadroom(params, intervalRes.second);
    if (headroomRes.has_value()) {
        return headroomRes;
    }
    AGpuHeadroomParams_setCalculationWindowMillis(params, expectedMaxMillis);
    if (AGpuHeadroomParams_getCalculationWindowMillis(params) != expectedMaxMillis) {
        return StringPrintf("AGpuHeadroomParams_getCalculationWindowMillis return different value "
                            "from %d: %d",
                            expectedMaxMillis,
                            AGpuHeadroomParams_getCalculationWindowMillis(params));
    }
    return checkGpuHeadroom(params, intervalRes.second);
}

static jstring nativeTestGetGpuHeadroomCustomWindow(JNIEnv* env, jclass) {
    return returnJString(env, testGetGpuHeadroomCustomWindow()).value_or(nullptr);
}

static std::optional<std::string> testGetCpuHeadroomCustomTids() {
    auto res = getCpuHeadroomMinIntervalMillis();
    if (res.second < 0) {
        return res.first;
    }
    auto params = ACpuHeadroomParams_create();
    std::atomic<bool> stop(false);
    std::vector<int32_t> tids;
    std::mutex tids_mutex;
    size_t maxTidsSize;
    ASystemHealth_getMaxCpuHeadroomTidsSize(&maxTidsSize);
    auto threads = createThreads(maxTidsSize, stop, tids, tids_mutex);
    ACpuHeadroomParams_setTids(params, tids.data(), tids.size());
    auto ret = checkCpuHeadroom(params, res.second);
    stop.store(true);
    for (auto& thread : threads) {
        thread.join();
    }
    return ret;
}

static jstring nativeTestGetCpuHeadroomCustomTids(JNIEnv* env, jclass) {
    return returnJString(env, testGetCpuHeadroomCustomTids()).value_or(nullptr);
}

static jclass gNativeSystemHealthTest_class;

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv* env;
    const JNINativeMethod methodTable[] =
            {{"nativeTestGetCpuHeadroomDefault", "()Ljava/lang/String;",
              (void*)nativeTestGetCpuHeadroomDefault},
             {"nativeTestGetGpuHeadroomDefault", "()Ljava/lang/String;",
              (void*)nativeTestGetGpuHeadroomDefault},
             {"nativeTestGetCpuHeadroomAverage", "()Ljava/lang/String;",
              (void*)nativeTestGetCpuHeadroomAverage},
             {"nativeTestGetGpuHeadroomAverage", "()Ljava/lang/String;",
              (void*)nativeTestGetGpuHeadroomAverage},
             {"nativeTestGetCpuHeadroomCustomWindow", "()Ljava/lang/String;",
              (void*)nativeTestGetCpuHeadroomCustomWindow},
             {"nativeTestGetGpuHeadroomCustomWindow", "()Ljava/lang/String;",
              (void*)nativeTestGetGpuHeadroomCustomWindow},
             {"nativeTestGetCpuHeadroomCustomTids", "()Ljava/lang/String;",
              (void*)nativeTestGetCpuHeadroomCustomTids},
             {"nativeTestGetMaxCpuHeadroomTidsSize", "()Ljava/lang/String;",
              (void*)nativeTestGetMaxCpuHeadroomTidsSize}};
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    gNativeSystemHealthTest_class =
            env->FindClass("android/systemhealth/cts/NativeSystemHealthTest");
    if (env->RegisterNatives(gNativeSystemHealthTest_class, methodTable,
                             sizeof(methodTable) / sizeof(JNINativeMethod)) != JNI_OK) {
        return JNI_ERR;
    }
    return JNI_VERSION_1_6;
}
