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
#pragma once

#include <EGL/egl.h>
#include <android/native_activity.h>
#include <android/native_window.h>
#include <android/performance_hint.h>
#include <android/surface_control.h>
#include <jni.h>

#include <chrono>
#include <map>
#include <memory>
#include <optional>

#include "EventLoop.h"
#include "Model.h"
#include "Shader.h"
#include "external/android_native_app_glue.h"

const constexpr size_t kStages = 3;
const constexpr size_t kHeadsPerStage = 4;
const constexpr size_t kHeads = kStages * kHeadsPerStage;

struct android_app;

struct Vsync {
    size_t index;
    int64_t deadlineNanos;
    int64_t presentationNanos;
    int64_t vsyncId;
};

struct VsyncData {
    int64_t frameTimeNanos;
    size_t numVsyncs;
    size_t preferredVsync;
    std::vector<Vsync> vsyncs{};
};

/*!
 * The output of running the per-stage lambda
 */
struct StageState {
    int64_t start;
    int64_t end;
    std::array<Model, kHeadsPerStage> models{};
};

/*!
 * The output state of running the whole stack of the per-stage updates for each stage
 */
struct StackState {
    Vsync intendedVsync;
    std::array<StageState, kStages> stages{};
};

Vsync &getClosestCallbackToTarget(int64_t target, VsyncData &data);

struct FrameStats {
    /*!
     * Median of the durations
     */
    int64_t medianWorkDuration;
    /*!
     * Median of the intervals
     */
    int64_t medianFrameInterval;
    /*!
     * Standard deviation of a given run
     */
    double deviation;
    /*!
     * The total number of frames that exceeded target
     */
    std::optional<int64_t> exceededCount;
    /*!
     * The percent of frames that exceeded target
     */
    std::optional<double> exceededFraction;
    /*!
     * Efficiency of a given run is calculated by how close to min(target, baseline) the median is
     */
    std::optional<double> efficiency;
    /*!
     * Median of the target duration that was closest to what we wanted
     */
    int64_t medianClosestDeadlineToTarget;
    // Actual target duration, related to session target but not exactly the same
    // This is based on the intended frame deadline based on results from Choreographer
    int64_t actualTargetDuration;
    void dump(std::string testName) const {
        aerr << "Stats for: " << testName;
        aerr << "medianWorkDuration: " << medianWorkDuration << std::endl;
        aerr << "medianFrameInterval: " << medianFrameInterval << std::endl;
        aerr << "deviation: " << deviation << std::endl;
        aerr << "exceededCount: " << exceededCount.value_or(-1) << std::endl;
        aerr << "exceededFraction: " << exceededFraction.value_or(-1) << std::endl;
        aerr << "efficiency: " << efficiency.value_or(-1) << std::endl;
        aerr << "medianClosestDeadlineToTarget: " << medianClosestDeadlineToTarget << std::endl;
        aerr << "actualTargetDuration: " << actualTargetDuration << std::endl;
    };
};

struct FrameBatchData {
    std::vector<int64_t> durations{};
    std::vector<int64_t> intervals{};
    std::vector<VsyncData> vsyncs{};
    std::vector<Vsync> selectedVsync{};
};

Renderer *getRenderer();

class Renderer {
public:
    /*!
     * @param pApp the android_app this Renderer belongs to, needed to configure GL
     */
    inline Renderer(android_app *pApp)
          : app_(pApp),
            display_(EGL_NO_DISPLAY),
            surface_(EGL_NO_SURFACE),
            context_(EGL_NO_CONTEXT),
            width_(0),
            height_(0),
            shaderNeedsNewProjectionMatrix_(true) {
        threadedInit();
    }

    virtual ~Renderer();

    /*!
     * Draw all models waiting to render in a given StackState.
     */
    void render(const StackState &state);

    /*!
     * Attempts to start hint session and returns whether ADPF is supported on a given device.
     */
    bool startHintSession(int64_t target);
    void closeHintSession();
    void reportActualWorkDuration(int64_t duration);
    void updateTargetWorkDuration(int64_t target);
    bool isHintSessionRunning();
    int64_t getTargetWorkDuration();

    /*!
     * Sets the number of iterations of physics before during each draw to control the CPU overhead.
     */
    void setPhysicsIterations(int iterations);

    /*!
     * Adds an entry to the final result map that gets passed up to the Java side of the app, and
     * eventually to the test runner.
     */
    void addResult(std::string name, std::string value);

    /*!
     * Retrieve the results map.
     */
    std::map<std::string, std::string> &getResults();

    /*!
     * Sets the baseline median, used to determine efficiency score
     */
    void setBaselineMedian(int64_t median);

    /*!
     * Calculates the above frame stats for a given run
     */
    FrameStats getFrameStats(FrameBatchData &batchData, std::string &testName);

    void setChoreographer(AChoreographer *choreographer);
    FrameStats drawFramesSync(int frames, int &events, android_poll_source *&pSource,
                              std::string testName = "");

    /*!
     * Run a few frames to figure out what vsync targets SF uses
     */
    std::vector<int64_t> findVsyncTargets(int &events, android_poll_source *&pSource,
                                          int numFrames);

    static Renderer *getInstance();
    static void makeInstance(android_app *pApp);

private:
    /*!
     * Creates the set of android heads for load testing
     */
    void createHeads();

    /*!
     * Swap the waiting render buffer after everything is drawn
     */
    void swapBuffers(const StackState &state);

    /*!
     * Performs necessary OpenGL initialization. Customize this if you want to change your EGL
     * context or application-wide settings.
     */
    void initRenderer(std::promise<bool> &syncOnCompletion);

    /*!
     * Run initRenderer on the drawing thread to hold the context there
     */
    void threadedInit();

    /*!
     * @brief we have to check every frame to see if the framebuffer has changed in size. If it has,
     * update the viewport accordingly
     */
    void updateRenderArea();

    /*!
     * Run physics and spin the models in a pipeline
     */
    StageState updateModels(StageState &lastUpdate);

    void choreographerCallback(const AChoreographerFrameCallbackData *data);

    /*!
     * Passed directly choreographer as the callback method
     */
    static void rawChoreographerCallback(const AChoreographerFrameCallbackData *data, void *appPtr);

    /*!
     * Post a new callback to Choreographer for the next frame
     */
    void postCallback();

    android_app *app_;
    EGLDisplay display_;
    EGLSurface surface_;
    EGLContext context_;
    EGLint width_;
    EGLint height_;
    APerformanceHintSession *hintSession_ = nullptr;
    APerformanceHintManager *hintManager_ = nullptr;
    AChoreographer *choreographer_;
    int64_t lastTarget_ = 0;
    int64_t baselineMedian_ = 0;

    bool shaderNeedsNewProjectionMatrix_ = true;

    /*!
     * Schedules a method to run against each level of the pipeline. At each step, the
     * method will be passed a "Data" object and output an object of the same type, allowing
     * different levels of the same pipeline to easily share data or work on the same problem.
     */
    template <class Data>
    void recursiveSchedule(std::function<void(int level, Data &data)> fn, int level, Data &&data) {
        loops_[level].queueWork(
                [=, this, fn = std::move(fn), dataInner = std::move(data)]() mutable {
                    // Run the method
                    fn(level, dataInner);

                    if (level > 0) {
                        this->recursiveSchedule(fn, level - 1, std::move(dataInner));
                    }
                });
    }

    template <class Data>
    void stackSchedule(std::function<void(int level, Data &data)> fn, Data startData) {
        recursiveSchedule<Data>(fn, loops_.size() - 1, std::move(startData));
    }

    std::unique_ptr<Shader> shader_;
    std::array<StageState, kStages> latest_stage_models_;
    int headsSize_ = 0;
    int physicsIterations_ = 1;

    /*!
     * Holds onto the results object in the renderer, so
     * we can reach the data anywhere in the rendering step.
     */
    std::map<std::string, std::string> results_;

    std::array<EventLoop, kStages> loops_{};
    EventLoop drawLoop_{};
    std::vector<pid_t> tids_{};
    std::vector<int64_t> targets_{};

    FrameBatchData batchData_{};

    std::optional<std::function<void(VsyncData &data)>> wakeupMethod_ = std::nullopt;

    std::optional<int64_t> lastStart_ = std::nullopt;
    int framesRemaining_ = 0;
    std::promise<bool> framesDone_{};

    static std::unique_ptr<Renderer> sRenderer;
};
