/*
 * Copyright 2023 The Android Open Source Project
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

#include <android/native_window.h>
#include <android/performance_hint.h>
#include <assert.h>
#include <jni.h>
#include <sys/system_properties.h>

#include <algorithm>
#include <chrono>
#include <cstdlib>
#include <functional>
#include <map>
#include <random>
#include <set>
#include <sstream>
#include <string>
#include <thread>
#include <vector>

#include "AndroidOut.h"
#include "JNIManager.h"
#include "Renderer.h"
#include "Utility.h"

using namespace std::chrono_literals;

const constexpr auto kDrawingTimeout = 3s;
const constexpr int kSamples = 300;
const constexpr int kCalibrationSamples = 50;
bool verboseLogging = true;

FrameStats drawFramesWithTarget(int64_t targetDuration, int &events, android_poll_source *&pSource,
                                std::string testName = "") {
    getRenderer()->updateTargetWorkDuration(targetDuration);
    return getRenderer()->drawFramesSync(kSamples, events, pSource, testName);
}

struct CalibrationStep {
    int physicsSteps;
    int duration;
    FrameStats stats;
};

struct RegressionStats {
    double m;
    double c;
};

RegressionStats calcRegression(std::vector<CalibrationStep> &stepData, bool dropOutlier) {
    RegressionStats out;

    double xAvg = 0, yAvg = 0;
    for (auto &&step : stepData) {
        xAvg += step.physicsSteps;
        yAvg += step.duration;
    }

    // Calculate a least-squares regression across the CalibrationSteps
    double numerator = 0, denominator = 0;
    for (auto &&step : stepData) {
        numerator += (step.physicsSteps - xAvg) * (step.duration - yAvg);
        denominator += (step.physicsSteps - xAvg) * (step.physicsSteps - xAvg);
    }
    out.m = numerator / denominator;
    out.c = yAvg - out.m * xAvg;

    if (dropOutlier) {
        double maxError = 0;
        int maxErrorIndex = 0;
        for (int i = 0; i < stepData.size(); ++i) {
            double error = stepData[i].duration - (out.m * stepData[i].physicsSteps + out.c);
            if (error > maxError) {
                maxError = error;
                maxErrorIndex = i;
            }
        }
        stepData.erase(stepData.begin() + maxErrorIndex);
    }
    return out;
}

CalibrationStep doCalibrationStep(int iter, int steps, int &events, android_poll_source *&pSource) {
    CalibrationStep out{.physicsSteps = steps};
    getRenderer()->setPhysicsIterations(steps);
    std::string testName = "calibration_" + std::to_string(iter);
    out.stats = getRenderer()->drawFramesSync(kCalibrationSamples, events, pSource, testName);
    out.duration = out.stats.medianWorkDuration;
    return out;
}

// Try to create a workload that takes "goal" amount of time
bool calibrate(int64_t goal, int &events, android_poll_source *&pSource) {
    int calibrateIter = 0;
    int bestIter;

    std::vector<CalibrationStep> stepData{};

    double error = 1.0;

    stepData.push_back(doCalibrationStep(calibrateIter++, 5, events, pSource));

    if ((stepData.back().duration + std::chrono::nanoseconds(1ms).count()) > goal) {
        Utility::setFailure("Baseline load is too expensive", getRenderer());
    }

    stepData.push_back(doCalibrationStep(calibrateIter++, 500, events, pSource));

    for (; calibrateIter < 20; ++calibrateIter) {
        // Drop an outlier every few steps because that converges way faster
        RegressionStats stats = calcRegression(stepData, (calibrateIter % 4 == 0));
        int nextStep = (goal - stats.c) / stats.m;
        stepData.push_back(doCalibrationStep(calibrateIter, nextStep, events, pSource));

        error = (abs(static_cast<double>(goal) - static_cast<double>(stepData.back().duration)) /
                 static_cast<double>(goal));

        // If we're ever within 2% of the target, that's good enough
        aerr << "Error: " << error << std::endl;
        if (error < 0.02) {
            break;
        }
    }
    getRenderer()->addResult("goal", std::to_string(goal));
    getRenderer()->addResult("duration", std::to_string(stepData.back().duration));
    getRenderer()->addResult("heads_count", std::to_string(kHeads));
    getRenderer()->addResult("interval", std::to_string(stepData.back().stats.medianFrameInterval));
    getRenderer()->addResult("physics_iterations", std::to_string(stepData.back().physicsSteps));
    return error < 0.02;
}

// /*!
//  * Handles commands sent to this Android application
//  * @param pApp the app the commands are coming from
//  * @param cmd the command to handle
//  */
void handle_cmd(android_app *pApp, int32_t cmd) {
    switch (cmd) {
        case APP_CMD_INIT_WINDOW:
            Renderer::makeInstance(pApp);
            pApp->userData = Renderer::getInstance();
            getRenderer()->setChoreographer(AChoreographer_getInstance());
            break;
        case APP_CMD_TERM_WINDOW:
            // The window is being destroyed. Use this to clean up your userData to avoid leaking
            // resources.
            //
            // We have to check if userData is assigned just in case this comes in really quickly
            if (pApp->userData) {
                auto *pRenderer = getRenderer();
                Utility::setFailure("App was closed while running!", pRenderer);
            }
            break;
        default:
            break;
    }
}

void setFrameRate(android_app *pApp, float frameRate) {
    auto t = ANativeWindow_setFrameRate(pApp->window, frameRate,
                                        ANATIVEWINDOW_FRAME_RATE_COMPATIBILITY_DEFAULT);
    if (t != 0) {
        Utility::setFailure("Can't set frame rate to " + std::to_string(frameRate), getRenderer());
    }
}

void android_main(struct android_app *pApp) {
    app_dummy();

    // Register an event handler for Android events
    pApp->onAppCmd = handle_cmd;

    JNIManager &manager = JNIManager::getInstance();
    manager.setApp(pApp);

    int events;
    android_poll_source *pSource = nullptr;

    // adb shell setprop debug.adpf_cts_verbose_logging true
    const prop_info *pi_verbose = __system_property_find("debug.adpf_cts_verbose_logging");
    if (pi_verbose != nullptr) {
        char value[PROP_VALUE_MAX];
        __system_property_read(pi_verbose, nullptr, value);
        if (strcmp(value, "false") == 0) {
            verboseLogging = false;
        } else {
            verboseLogging = true;
        }
    }

    // Ensure renderer is initialized
    while (!pApp->userData) {
        int retval = ALooper_pollOnce(0, nullptr, &events, (void **)&pSource);
        while (retval == ALOOPER_POLL_CALLBACK) {
            retval = ALooper_pollOnce(0, nullptr, &events, (void **)&pSource);
        }
        if (retval >= 0 && pSource) {
            pSource->process(pApp, pSource);
        }
    }

    float frameRate = 60.0f;
    // adb shell setprop debug.adpf_cts_frame_rate 60.0
    const prop_info *pi_frame_rate = __system_property_find("debug.adpf_cts_frame_rate");
    if (pi_frame_rate != nullptr) {
        char value[PROP_VALUE_MAX];
        __system_property_read(pi_frame_rate, nullptr, value);
        char *endptr; // To check for valid conversion
        float floatValue = strtof(value, &endptr);
        if (*endptr == '\0') {
            frameRate = floatValue;
        }
    }
    setFrameRate(pApp, frameRate);

    // Make sure everything has long enough to get processed
    std::this_thread::sleep_for(1s);

    int64_t fpsPeriod = duration_cast<std::chrono::nanoseconds>(1s / frameRate).count();

    bool supported = getRenderer()->startHintSession(fpsPeriod * 2);
    if (!supported) {
        JNIManager::sendResultsToJava(getRenderer()->getResults());
        return;
    }

    getRenderer()->updateTargetWorkDuration(fpsPeriod * 2);

    // Do an initial load with the session to let CPU settle and measure frame deadlines
    std::vector<int64_t> targets = getRenderer()->findVsyncTargets(events, pSource, kSamples);

    int64_t maxIntendedTarget = fpsPeriod * 1.5;
    int intendedTargetIndex;
    for (intendedTargetIndex = 0;
         intendedTargetIndex < targets.size() && targets[intendedTargetIndex] < maxIntendedTarget;
         ++intendedTargetIndex);
    intendedTargetIndex--;

    // The "Heavy Target" is the vsync callback time we expect to get from Choreographer after
    // running for a while. We use this to decide how expensive to make the workload.
    const int64_t heavyTarget = targets[intendedTargetIndex];

    // The light target is the third soonest available vsync callback time we get from Choreographer
    // after running for a while. We use this as a realistic "heavily pipelined" long frame target,
    // where we would expect the boost to be low.
    const int64_t lightTarget = targets[intendedTargetIndex + 2];

    aout << "Heavy load: " << heavyTarget << " light load: " << lightTarget << std::endl;

    auto testNames = JNIManager::getInstance().getTestNames();
    std::set<std::string> testSet{testNames.begin(), testNames.end()};
    std::vector<std::function<void()>> tests;

    // TODO(b/344696346): skip the test if the frame interval is significantly larger than
    // 16ms? Say the device has limited performance to run this test, or its FPS capped at 30

    // Calibrate a workload that's 30% longer
    int64_t goal = heavyTarget * 1.3;
    double benchmarkedError;
    FrameStats baselineStats;

    bool calibrated = false;
    for (int i = 0; i < 10 && !calibrated; ++i) {
        if (!calibrate(goal, events, pSource)) {
            continue;
        }

        baselineStats = getRenderer()->drawFramesSync(kSamples, events, pSource, "baseline");

        benchmarkedError = (abs(static_cast<double>(baselineStats.medianWorkDuration - goal)) /
                            static_cast<double>(goal));

        if (benchmarkedError < 0.05) {
            calibrated = true;
            getRenderer()->addResult("median_frame_interval",
                                     std::to_string(baselineStats.medianFrameInterval));
        }
    }

    if (!calibrated) {
        Utility::setFailure("Failed to calibrate", getRenderer());
    }

    double calibrationAccuracy = 1.0 - benchmarkedError;

    getRenderer()->addResult("calibration_accuracy", std::to_string(calibrationAccuracy));

    // Used to figure out efficiency score on actual runs
    getRenderer()->setBaselineMedian(baselineStats.medianWorkDuration);

    if (testSet.count("heavy_load") > 0) {
        tests.push_back(
                [&]() { drawFramesWithTarget(heavyTarget, events, pSource, "heavy_load"); });
    }

    if (testSet.count("light_load") > 0) {
        tests.push_back(
                [&]() { drawFramesWithTarget(lightTarget, events, pSource, "light_load"); });
    }

    if (testSet.count("transition_load") > 0) {
        tests.push_back([&]() {
            drawFramesWithTarget(lightTarget, events, pSource, "transition_load_1");
            drawFramesWithTarget(heavyTarget, events, pSource, "transition_load_2");
            drawFramesWithTarget(lightTarget, events, pSource, "transition_load_3");
        });
    }

    std::shuffle(tests.begin(), tests.end(), std::default_random_engine{});

    for (auto &test : tests) {
        test();
    }

    for (auto &&result : getRenderer()->getResults()) {
        aout << result.first << " : " << result.second << std::endl;
    }

    JNIManager::sendResultsToJava(getRenderer()->getResults());
}
