/*
 * Copyright (C) 2022 The Android Open Source Project
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
#include <android/native_window_jni.h>
#include <android/performance_hint.h>
#include <android/surface_control_jni.h>
#include <errno.h>
#include <jni.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#include <future>
#include <list>
#include <sstream>
#include <vector>

static jstring toJString(JNIEnv *env, const char* c_str) {
    return env->NewStringUTF(c_str);
}

constexpr int64_t DEFAULT_TARGET_NS = 16666666L;

#define CHECK_SESSION_RETURN(retval, session, situation)                               \
    if (retval != 0 || session == nullptr) {                                           \
        return toJString(env,                                                          \
                         std::format(situation " returned status: {}{}", retval,       \
                                     session == nullptr ? " with a null session" : "") \
                                 .c_str());                                            \
    }

template <class T, void (*D)(T*)>
std::shared_ptr<T> wrapSP(T* incoming) {
    return incoming == nullptr ? nullptr : std::shared_ptr<T>(incoming, [](T* ptr) { D(ptr); });
}

// Preferable to template specialization bc if we wrap something unsupported, it's more obvious
constexpr auto&& wrapSession = wrapSP<APerformanceHintSession, APerformanceHint_closeSession>;
constexpr auto&& wrapConfig = wrapSP<ASessionCreationConfig, ASessionCreationConfig_release>;
constexpr auto&& wrapWorkDuration = wrapSP<AWorkDuration, AWorkDuration_release>;
constexpr auto&& wrapSurfaceControl = wrapSP<ASurfaceControl, ASurfaceControl_release>;
constexpr auto&& wrapNativeWindow = wrapSP<ANativeWindow, ANativeWindow_release>;

std::shared_ptr<APerformanceHintSession> createSession(APerformanceHintManager* manager) {
    int32_t pid = getpid();
    return wrapSession(APerformanceHint_createSession(manager, &pid, 1u, DEFAULT_TARGET_NS));
}

std::shared_ptr<APerformanceHintSession> createSessionWithConfig(
        APerformanceHintManager* manager, std::shared_ptr<ASessionCreationConfig> config,
        int* out) {
    APerformanceHintSession* sessionOut;
    *out = APerformanceHint_createSessionUsingConfig(manager, config.get(), &sessionOut);
    return wrapSession(sessionOut);
}

std::shared_ptr<ASessionCreationConfig> createConfig() {
    return wrapConfig(ASessionCreationConfig_create());
}

struct WorkDurationCreator {
    int64_t workPeriodStart;
    int64_t totalDuration;
    int64_t cpuDuration;
    int64_t gpuDuration;
};

struct ConfigCreator {
    std::vector<int32_t> tids{getpid()};
    int64_t targetDuration = DEFAULT_TARGET_NS;
    bool powerEfficient = false;
    bool graphicsPipeline = false;
    std::vector<ANativeWindow*> nativeWindows{};
    std::vector<ASurfaceControl*> surfaceControls{};
    bool autoCpu = false;
    bool autoGpu = false;
};

struct SupportHelper {
    bool hintSessions : 1;
    bool powerEfficiency : 1;
    bool bindToSurface : 1;
    bool graphicsPipeline : 1;
    bool autoCpu : 1;
    bool autoGpu : 1;
};

SupportHelper getSupportHelper() {
    return {
            .hintSessions = APerformanceHint_isFeatureSupported(APERF_HINT_SESSIONS),
            .powerEfficiency = APerformanceHint_isFeatureSupported(APERF_HINT_POWER_EFFICIENCY),
            .bindToSurface = APerformanceHint_isFeatureSupported(APERF_HINT_SURFACE_BINDING),
            .graphicsPipeline = APerformanceHint_isFeatureSupported(APERF_HINT_GRAPHICS_PIPELINE),
            .autoCpu = APerformanceHint_isFeatureSupported(APERF_HINT_AUTO_CPU),
            .autoGpu = APerformanceHint_isFeatureSupported(APERF_HINT_AUTO_GPU),
    };
}

std::shared_ptr<ASessionCreationConfig> configFromCreator(ConfigCreator&& creator) {
    auto config = createConfig();

    ASessionCreationConfig_setTids(config.get(), creator.tids.data(), creator.tids.size());
    ASessionCreationConfig_setTargetWorkDurationNanos(config.get(), creator.targetDuration);
    ASessionCreationConfig_setPreferPowerEfficiency(config.get(), creator.powerEfficient);
    ASessionCreationConfig_setGraphicsPipeline(config.get(), creator.graphicsPipeline);
    ASessionCreationConfig_setNativeSurfaces(config.get(),
                                             creator.nativeWindows.size() > 0
                                                     ? creator.nativeWindows.data()
                                                     : nullptr,
                                             creator.nativeWindows.size(),
                                             creator.surfaceControls.size() > 0
                                                     ? creator.surfaceControls.data()
                                                     : nullptr,
                                             creator.surfaceControls.size());
    ASessionCreationConfig_setUseAutoTiming(config.get(), creator.autoCpu, creator.autoGpu);
    return config;
}

static std::shared_ptr<AWorkDuration> createWorkDuration(WorkDurationCreator creator) {
    AWorkDuration* out = AWorkDuration_create();
    AWorkDuration_setWorkPeriodStartTimestampNanos(out, creator.workPeriodStart);
    AWorkDuration_setActualTotalDurationNanos(out, creator.totalDuration);
    AWorkDuration_setActualCpuDurationNanos(out, creator.cpuDuration);
    AWorkDuration_setActualGpuDurationNanos(out, creator.gpuDuration);
    return wrapWorkDuration(out);
}

static jboolean nativeGetSessionsAreSupported() {
    return APerformanceHint_isFeatureSupported(APERF_HINT_SESSIONS);
}

static jstring nativeTestCreateHintSession(JNIEnv *env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");
    auto a = createSession(manager);
    auto b = createSession(manager);
    if (a == nullptr) {
        return toJString(env, "a is null");
    } else if (b == nullptr) {
        return toJString(env, "b is null");
    } else if (a.get() == b.get()) {
        return toJString(env, "a and b matches");
    }
    return nullptr;
}

static jstring nativeTestCreateHintSessionUsingConfig(JNIEnv* env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");
    auto config = configFromCreator({});
    if (config == nullptr) {
        return toJString(env, "config is null");
    }

    int returnVal = 0;
    auto a = createSessionWithConfig(manager, config, &returnVal);
    if (returnVal != 0) {
        return toJString(env,
                         std::format("Session creation failed with status: {}", returnVal).c_str());
    }

    auto b = createSessionWithConfig(manager, config, &returnVal);
    if (a == nullptr) {
        return toJString(env, "a is null");
    } else if (b == nullptr) {
        return toJString(env, "b is null");
    } else if (a.get() == b.get()) {
        return toJString(env, "a and b matches");
    }
    return nullptr;
}

static jstring nativeTestGetPreferredUpdateRateNanos(JNIEnv *env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");
    auto session = createSession(manager);
    if (session != nullptr) {
        bool positive = APerformanceHint_getPreferredUpdateRateNanos(manager) > 0;
        if (!positive)
          return toJString(env, "preferred rate is not positive");
    } else {
        if (APerformanceHint_getPreferredUpdateRateNanos(manager) != -1)
          return toJString(env, "preferred rate is not -1");
    }
    return nullptr;
}

static jstring nativeUpdateTargetWorkDuration(JNIEnv *env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");
    auto session = createSession(manager);
    if (session == nullptr) return nullptr;

    int result = APerformanceHint_updateTargetWorkDuration(session.get(), 100);
    if (result != 0) {
        return toJString(env, "updateTargetWorkDuration did not return 0");
    }
    return nullptr;
}

static jstring nativeUpdateTargetWorkDurationWithNegativeDuration(JNIEnv *env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");
    auto session = createSession(manager);
    if (session == nullptr) return nullptr;

    int result = APerformanceHint_updateTargetWorkDuration(session.get(), -1);
    if (result != EINVAL) {
        return toJString(env, "updateTargetWorkDuration did not return EINVAL");
    }
    return nullptr;
}

static jstring nativeReportActualWorkDuration(JNIEnv *env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");
    auto session = createSession(manager);
    if (session == nullptr) return nullptr;

    int result = APerformanceHint_reportActualWorkDuration(session.get(), 100);
    if (result != 0) {
        return toJString(env, "reportActualWorkDuration(100) did not return 0");
    }

    result = APerformanceHint_reportActualWorkDuration(session.get(), 1);
    if (result != 0) {
        return toJString(env, "reportActualWorkDuration(1) did not return 0");
    }

    result = APerformanceHint_reportActualWorkDuration(session.get(), 100);
    if (result != 0) {
        return toJString(env, "reportActualWorkDuration(100) did not return 0");
    }

    result = APerformanceHint_reportActualWorkDuration(session.get(), 1000);
    if (result != 0) {
        return toJString(env, "reportActualWorkDuration(1000) did not return 0");
    }

    return nullptr;
}

static jstring nativeReportActualWorkDurationWithIllegalArgument(JNIEnv *env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");
    auto session = createSession(manager);
    if (session == nullptr) return nullptr;

    int result = APerformanceHint_reportActualWorkDuration(session.get(), -1);
    if (result != EINVAL) {
        return toJString(env, "reportActualWorkDuration did not return EINVAL");
    }
    return nullptr;
}

static jstring nativeTestSetThreadsWithInvalidTid(JNIEnv* env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) {
        return toJString(env, "null manager");
    }
    auto session = createSession(manager);
    if (session == nullptr) {
        return nullptr;
    }

    std::vector<pid_t> tids;
    tids.push_back(2);
    int result = APerformanceHint_setThreads(session.get(), tids.data(), 1);
    if (result != EPERM) {
        return toJString(env, "setThreads did not return EPERM");
    }
    return nullptr;
}


static jstring nativeSetPreferPowerEfficiency(JNIEnv* env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");
    auto session = createSession(manager);
    if (session == nullptr) return nullptr;

    int result = APerformanceHint_setPreferPowerEfficiency(session.get(), false);
    if (result != 0) {
        return toJString(env, "setPreferPowerEfficiency(false) did not return 0");
    }

    result = APerformanceHint_setPreferPowerEfficiency(session.get(), true);
    if (result != 0) {
        return toJString(env, "setPreferPowerEfficiency(true) did not return 0");
    }

    result = APerformanceHint_setPreferPowerEfficiency(session.get(), true);
    if (result != 0) {
        return toJString(env, "setPreferPowerEfficiency(true) did not return 0");
    }
    return nullptr;
}

static jstring nativeTestReportActualWorkDuration2(JNIEnv* env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");
    auto session = createSession(manager);
    if (session == nullptr) return nullptr;

    std::vector<WorkDurationCreator> testCases = {
        {
            .workPeriodStart = 1000,
            .totalDuration = 14,
            .cpuDuration = 11,
            .gpuDuration = 8
        },
        {
            .workPeriodStart = 1016,
            .totalDuration = 14,
            .cpuDuration = 12,
            .gpuDuration = 4
        },
        {
            .workPeriodStart = 1016,
            .totalDuration = 14,
            .cpuDuration = 12,
            .gpuDuration = 4
        },
        {
            .workPeriodStart = 900,
            .totalDuration = 20,
            .cpuDuration = 20,
            .gpuDuration = 0
        },
        {
            .workPeriodStart = 800,
            .totalDuration = 20,
            .cpuDuration = 0,
            .gpuDuration = 20
        }
    };

    for (auto && testCase : testCases) {
        auto&& testObj = createWorkDuration(testCase);
        int result = APerformanceHint_reportActualWorkDuration2(session.get(), testObj.get());
        if (result != 0) {
            std::stringstream stream;
            stream << "APerformanceHint_reportActualWorkDuration2({"
                   << "workPeriodStartTimestampNanos = " << testCase.workPeriodStart << ", "
                   << "actualTotalDurationNanos = " << testCase.totalDuration << ", "
                   << "actualCpuDurationNanos = " << testCase.cpuDuration << ", "
                   << "actualGpuDurationNanos = " << testCase.gpuDuration << "}) did not return 0";
            return toJString(env, stream.str().c_str());
        }
    }

    return nullptr;
}

static jstring nativeTestReportActualWorkDuration2WithIllegalArgument(JNIEnv* env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");
    auto session = createSession(manager);
    if (session == nullptr) return nullptr;

    std::vector<WorkDurationCreator> testCases = {
        {
            .workPeriodStart = -1,
            .totalDuration = 14,
            .cpuDuration = 11,
            .gpuDuration = 8
        },
        {
            .workPeriodStart = 1000,
            .totalDuration = -1,
            .cpuDuration = 11,
            .gpuDuration = 8
        },
        {
            .workPeriodStart = 1000,
            .totalDuration = 14,
            .cpuDuration = -1,
            .gpuDuration = 8
        },
        {
            .workPeriodStart = 1000,
            .totalDuration = 14,
            .cpuDuration = 11,
            .gpuDuration = -1
        },
        {
            .workPeriodStart = 1000,
            .totalDuration = 14,
            .cpuDuration = 0,
            .gpuDuration = 0
        }
    };

    for (auto && testCase : testCases) {
        auto&& testObj = createWorkDuration(testCase);
        int result = APerformanceHint_reportActualWorkDuration2(session.get(), testObj.get());
        if (result != EINVAL) {
            std::stringstream stream;
            stream << "APerformanceHint_reportActualWorkDuration2({"
                   << "workPeriodStartTimestampNanos = " << testCase.workPeriodStart << ", "
                   << "actualTotalDurationNanos = " << testCase.totalDuration << ", "
                   << "actualCpuDurationNanos = " << testCase.cpuDuration << ", "
                   << "actualGpuDurationNanos = " << testCase.gpuDuration
                   << "}) did not return EINVAL";
            return toJString(env, stream.str().c_str());
        }
    }

    return nullptr;
}

static jstring nativeTestLoadHints(JNIEnv* env, jobject) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");
    auto session = createSession(manager);
    if (session == nullptr) return nullptr;

    int result = APerformanceHint_notifyWorkloadIncrease(session.get(), true, false, "CTS hint 1");
    if (result != 0 && result != EBUSY) {
        return toJString(env,
                         std::format("notifyWorkloadIncrease(true, false) returned {}", result)
                                 .c_str());
    }

    result = APerformanceHint_notifyWorkloadReset(session.get(), false, true, "CTS hint 2");
    if (result != 0 && result != EBUSY) {
        return toJString(env,
                         std::format("notifyWorkloadReset(false, true) returned {}", result)
                                 .c_str());
    }

    result = APerformanceHint_notifyWorkloadIncrease(session.get(), true, true, "CTS hint 3");
    if (result != 0 && result != EBUSY) {
        return toJString(env,
                         std::format("notifyWorkloadIncrease(true, true) returned {}", result)
                                 .c_str());
    }

    result = APerformanceHint_notifyWorkloadIncrease(session.get(), false, false, "CTS hint 4");
    if (result != 0 && result != EBUSY) {
        return toJString(env,
                         std::format("notifyWorkloadIncrease(false, false) returned {}", result)
                                 .c_str());
    }

    result = APerformanceHint_notifyWorkloadReset(session.get(), false, false, "CTS hint 5");
    if (result != 0 && result != EBUSY) {
        return toJString(env,
                         std::format("notifyWorkloadReset(false, false) returned {}", result)
                                 .c_str());
    }

    result = APerformanceHint_notifyWorkloadSpike(session.get(), true, false, "CTS hint 6");
    if (result != 0 && result != EBUSY) {
        return toJString(env,
                         std::format("notifyWorkloadSpike(true, false) returned {}", result)
                                 .c_str());
    }

    result = APerformanceHint_notifyWorkloadSpike(session.get(), false, true, "CTS hint 7");
    if (result != 0 && result != EBUSY) {
        return toJString(env,
                         std::format("notifyWorkloadSpike(false, true) returned {}", result)
                                 .c_str());
    }

    result = APerformanceHint_notifyWorkloadSpike(session.get(), false, false, "CTS hint 8");
    if (result != 0 && result != EBUSY) {
        return toJString(env,
                         std::format("notifyWorkloadSpike(false, false) returned {}", result)
                                 .c_str());
    }

    return nullptr;
}

static jlong nativeBorrowSessionFromJava(JNIEnv* env, jobject, jobject sessionObj) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return 0;

    APerformanceHintSession* session = APerformanceHint_borrowSessionFromJava(env, sessionObj);

    // Test a basic synchronous operation to ensure the session is valid.
    int32_t pid = getpid();
    int retval = APerformanceHint_setThreads(session, &pid, 1u);
    if (retval != 0) {
        return 0;
    }

    return reinterpret_cast<jlong>(session);
}

static jstring nativeTestCreateGraphicsPipelineSession(JNIEnv* env, jobject) {
    auto supportInfo = getSupportHelper();
    if (!supportInfo.graphicsPipeline) {
        return nullptr;
    }

    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");

    int errCode = 0;
    const int count = APerformanceHint_getMaxGraphicsPipelineThreadsCount(manager);
    auto config = configFromCreator({.graphicsPipeline = true});
    auto session = createSessionWithConfig(manager, config, &errCode);
    CHECK_SESSION_RETURN(errCode, session, "Graphics pipeline session creation");

    return nullptr;
}

static jstring nativeTestSetNativeSurfaces(JNIEnv* env, jobject, jobject surfaceControlFromJava,
                                           jobject surfaceFromJava) {
    auto supportInfo = getSupportHelper();
    if (!supportInfo.bindToSurface) {
        return nullptr;
    }

    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");

    int errCode = 0;

    auto surfaceControl = wrapSurfaceControl(ASurfaceControl_fromJava(env, surfaceControlFromJava));
    auto nativeWindow = wrapNativeWindow(ANativeWindow_fromSurface(env, surfaceFromJava));

    // Test native window session creation
    auto nativeWindowConfig = configFromCreator({
            .graphicsPipeline = true,
            .nativeWindows = {nativeWindow.get()},
    });
    auto nativeWindowSession = createSessionWithConfig(manager, nativeWindowConfig, &errCode);
    CHECK_SESSION_RETURN(errCode, nativeWindowSession, "Session creation with an ANativeWindow");

    // Test surfaceControl session creation
    auto surfaceControlConfig = configFromCreator({
            .graphicsPipeline = true,
            .surfaceControls = {surfaceControl.get()},
    });
    auto surfaceControlSession = createSessionWithConfig(manager, surfaceControlConfig, &errCode);
    CHECK_SESSION_RETURN(errCode, nativeWindowSession, "Session creation with an ASurfaceControl");

    // Test both
    auto bothConfig = configFromCreator({
            .graphicsPipeline = true,
            .nativeWindows = {nativeWindow.get()},
            .surfaceControls = {surfaceControl.get()},
    });
    auto bothSession = createSessionWithConfig(manager, bothConfig, &errCode);
    CHECK_SESSION_RETURN(errCode, bothSession,
                         "Session creation with both ASurfaceControl and ANativeWindow");

    // Test unsetting
    errCode = APerformanceHint_setNativeSurfaces(bothSession.get(), nullptr, 0, nullptr, 0);
    if (errCode != 0) {
        return toJString(env,
                         std::format("Setting empty native surfaces failed with: {}", errCode)
                                 .c_str());
    }

    // Test setting it back
    ANativeWindow* windowArr[]{nativeWindow.get()};
    ASurfaceControl* controlArr[]{surfaceControl.get()};

    errCode = APerformanceHint_setNativeSurfaces(bothSession.get(), windowArr, 1, controlArr, 1);
    if (errCode != 0) {
        return toJString(env,
                         std::format("Setting native surfaces on re-used empty session failed "
                                     "with: {}",
                                     errCode)
                                 .c_str());
    }

    // Create new empty sessions
    auto unassociatedConfig = configFromCreator({
            .graphicsPipeline = true,
    });

    auto unassociatedSession = createSessionWithConfig(manager, unassociatedConfig, &errCode);
    CHECK_SESSION_RETURN(errCode, unassociatedSession, "Session creation while unassociated");

    errCode = APerformanceHint_setNativeSurfaces(nativeWindowSession.get(), windowArr, 1,
                                                 controlArr, 1);
    if (errCode != 0) {
        return toJString(env,
                         std::format("Setting native surfaces on fresh unassociated session failed "
                                     "with: {}",
                                     errCode)
                                 .c_str());
    }

    return nullptr;
}

static jstring nativeTestAutoSessionTiming(JNIEnv* env, jobject, jobject surfaceControlFromJava) {
    APerformanceHintManager* manager = APerformanceHint_getManager();
    if (!manager) return toJString(env, "null manager");

    auto supportInfo = getSupportHelper();

    std::string out;
    int errCode;

    auto surfaceControl = wrapSurfaceControl(ASurfaceControl_fromJava(env, surfaceControlFromJava));

    if (supportInfo.autoCpu) {
        // Create a surfaceControl-associated session for auto CPU
        auto autoCpuSessionConfig = configFromCreator({
                .graphicsPipeline = true,
                .surfaceControls = {surfaceControl.get()},
                .autoCpu = true,
        });
        if (autoCpuSessionConfig == nullptr) {
            return toJString(env, out.c_str());
        }

        auto autoCpuSession = createSessionWithConfig(manager, autoCpuSessionConfig, &errCode);
        CHECK_SESSION_RETURN(errCode, autoCpuSession, "Cpu auto session creation");
    }

    if (supportInfo.autoGpu) {
        // Create a surfaceControl-associated session for auto GPU
        auto autoGpuSessionConfig = configFromCreator({
                .graphicsPipeline = true,
                .surfaceControls = {surfaceControl.get()},
                .autoGpu = true,
        });
        if (autoGpuSessionConfig == nullptr) {
            return toJString(env, out.c_str());
        }

        auto autoGpuSession = createSessionWithConfig(manager, autoGpuSessionConfig, &errCode);
        CHECK_SESSION_RETURN(errCode, autoGpuSession, "Gpu auto session creation");
    }

    if (supportInfo.autoCpu && supportInfo.autoGpu) {
        // Create a surfaceControl-associated session for both
        auto fullAutoSessionConfig = configFromCreator({
                .graphicsPipeline = true,
                .surfaceControls = {surfaceControl.get()},
                .autoCpu = true,
                .autoGpu = true,
        });
        if (fullAutoSessionConfig == nullptr) {
            return toJString(env, out.c_str());
        }

        auto fullAutoSession = createSessionWithConfig(manager, fullAutoSessionConfig, &errCode);
        CHECK_SESSION_RETURN(errCode, fullAutoSession, "Full auto session creation");
    }

    return nullptr;
}

static jstring nativeTestSupportChecking(JNIEnv* env, jobject) {
    union {
        // This checks every single support enum
        SupportHelper supportInfo = getSupportHelper();
        int32_t supportInt;
    };
    if (!supportInfo.hintSessions) {
        // If any mode other than hintSessions is enabled
        if (supportInt & -2) {
            return toJString(env, "Exposed support for session functionality but not for sessions");
        }
    } else if (supportInfo.autoCpu || supportInfo.autoGpu) {
        if (!supportInfo.graphicsPipeline) {
            return toJString(env,
                             "Exposed support for auto timing without support for graphics "
                             "pipelines");
        } else if (!supportInfo.bindToSurface) {
            return toJString(env,
                             "Exposed support for auto timing without support for native surface"
                             "binding");
        }
    }
    return nullptr;
}

static JNINativeMethod gMethods[] = {
        {"nativeGetSessionsAreSupported", "()Z",
         (void*)nativeGetSessionsAreSupported},
        {"nativeTestCreateHintSession", "()Ljava/lang/String;",
         (void*)nativeTestCreateHintSession},
        {"nativeTestCreateGraphicsPipelineSession", "()Ljava/lang/String;",
         (void*)nativeTestCreateGraphicsPipelineSession},
        {"nativeTestCreateHintSessionUsingConfig", "()Ljava/lang/String;",
         (void*)nativeTestCreateHintSessionUsingConfig},
        {"nativeTestGetPreferredUpdateRateNanos", "()Ljava/lang/String;",
         (void*)nativeTestGetPreferredUpdateRateNanos},
        {"nativeUpdateTargetWorkDuration", "()Ljava/lang/String;",
         (void*)nativeUpdateTargetWorkDuration},
        {"nativeUpdateTargetWorkDurationWithNegativeDuration", "()Ljava/lang/String;",
         (void*)nativeUpdateTargetWorkDurationWithNegativeDuration},
        {"nativeReportActualWorkDuration", "()Ljava/lang/String;",
         (void*)nativeReportActualWorkDuration},
        {"nativeReportActualWorkDurationWithIllegalArgument", "()Ljava/lang/String;",
         (void*)nativeReportActualWorkDurationWithIllegalArgument},
        {"nativeTestSetThreadsWithInvalidTid", "()Ljava/lang/String;",
         (void*)nativeTestSetThreadsWithInvalidTid},
        {"nativeSetPreferPowerEfficiency", "()Ljava/lang/String;",
         (void*)nativeSetPreferPowerEfficiency},
        {"nativeTestReportActualWorkDuration2", "()Ljava/lang/String;",
         (void*)nativeTestReportActualWorkDuration2},
        {"nativeTestReportActualWorkDuration2WithIllegalArgument", "()Ljava/lang/String;",
         (void*)nativeTestReportActualWorkDuration2WithIllegalArgument},
        {"nativeTestLoadHints", "()Ljava/lang/String;", (void*)nativeTestLoadHints},
        {"nativeBorrowSessionFromJava", "(Landroid/os/PerformanceHintManager$Session;)J",
         (void*)nativeBorrowSessionFromJava},
        {"nativeTestSetNativeSurfaces",
         "(Landroid/view/SurfaceControl;Landroid/view/Surface;)Ljava/lang/String;",
         (void*)nativeTestSetNativeSurfaces},
        {"nativeTestAutoSessionTiming", "(Landroid/view/SurfaceControl;)Ljava/lang/String;",
         (void*)nativeTestAutoSessionTiming},
        {"nativeTestSupportChecking", "()Ljava/lang/String;", (void*)nativeTestSupportChecking},
};

int register_android_os_cts_PerformanceHintManagerTest(JNIEnv* env) {
    jclass clazz = env->FindClass("android/os/cts/PerformanceHintManagerTest");

    return env->RegisterNatives(clazz, gMethods, sizeof(gMethods) / sizeof(JNINativeMethod));
}
