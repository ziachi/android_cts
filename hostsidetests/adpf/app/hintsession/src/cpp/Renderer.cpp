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
#include "Renderer.h"

#include <android/imagedecoder.h>

#include <memory>
#include <numeric>
#include <string>
#include <vector>
// #include <iostream>

#include <android/hardware_buffer_jni.h>

#include <chrono>

#include "AndroidOut.h"
#include "JNIManager.h"
#include "Shader.h"
#include "TextureAsset.h"
#include "Utility.h"
#include "android/performance_hint.h"

using namespace std::chrono_literals;
//! executes glGetString and outputs the result to logcat
#define PRINT_GL_STRING(s) \
    { aout << #s ": " << glGetString(s) << std::endl; }

/*!
 * @brief if glGetString returns a space separated list of elements, prints each one on a new line
 *
 * This works by creating an istringstream of the input c-style string. Then that is used to create
 * a vector -- each element of the vector is a new element in the input string. Finally a foreach
 * loop consumes this and outputs it to logcat using @a aout
 */
#define PRINT_GL_STRING_AS_LIST(s)                                                 \
    {                                                                              \
        std::istringstream extensionStream((const char *)glGetString(s));          \
        std::vector<std::string>                                                   \
                extensionList(std::istream_iterator<std::string>{extensionStream}, \
                              std::istream_iterator<std::string>());               \
        aout << #s ":\n";                                                          \
        for (auto &extension : extensionList) {                                    \
            aout << extension << "\n";                                             \
        }                                                                          \
        aout << std::endl;                                                         \
    }

//! Color for cornflower blue. Can be sent directly to glClearColor
#define CORNFLOWER_BLUE 100 / 255.f, 149 / 255.f, 237 / 255.f, 1

// Vertex shader, you'd typically load this from assets
static const char *vertex = R"vertex(#version 300 es
in vec3 inPosition;
in vec2 inUV;

out vec2 fragUV;

uniform mat4 uProjection;

void main() {
    fragUV = inUV;
    gl_Position = uProjection * vec4(inPosition, 1.0);
}
)vertex";

// Fragment shader, you'd typically load this from assets
static const char *fragment = R"fragment(#version 300 es
precision mediump float;

in vec2 fragUV;

uniform sampler2D uTexture;

out vec4 outColor;

void main() {
    outColor = texture(uTexture, fragUV);
}
)fragment";

/*!
 * Half the height of the projection matrix. This gives you a renderable area of height 4 ranging
 * from -2 to 2
 */
static constexpr float kProjectionHalfHeight = 2.f;

/*!
 * The near plane distance for the projection matrix. Since this is an orthographic projection
 * matrix, it's convenient to have negative values for sorting (and avoiding z-fighting at 0).
 */
static constexpr float kProjectionNearPlane = -1.f;

/*!
 * The far plane distance for the projection matrix. Since this is an orthographic projection
 * matrix, it's convenient to have the far plane equidistant from 0 as the near plane.
 */
static constexpr float kProjectionFarPlane = 1.f;

std::shared_ptr<TextureAsset> Model::texture = nullptr;

Renderer::~Renderer() {
    if (display_ != EGL_NO_DISPLAY) {
        eglMakeCurrent(display_, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (context_ != EGL_NO_CONTEXT) {
            eglDestroyContext(display_, context_);
            context_ = EGL_NO_CONTEXT;
        }
        if (surface_ != EGL_NO_SURFACE) {
            eglDestroySurface(display_, surface_);
            surface_ = EGL_NO_SURFACE;
        }
        eglTerminate(display_);
        display_ = EGL_NO_DISPLAY;
    }
}

Renderer *getRenderer() {
    return Renderer::getInstance();
}

StageState Renderer::updateModels(StageState &lastUpdate) {
    StageState output{
            .models = lastUpdate.models,
            .start = Utility::now(),
    };

    // Get desired seconds per rotation
    double spr = duration_cast<std::chrono::nanoseconds>(2s).count();

    // Figure out what angle the models need to be at
    double offset = (output.start - lastUpdate.start);

    // Calculate the required spin as a fraction of a circle
    auto spin = offset / spr;

    for (Model &model : output.models) {
        model.addRotation(M_PI * 2.0 * spin);
    }

    for (int j = 0; j < physicsIterations_; ++j) {
        Model::applyPhysics(0.1f, output.models.data(), output.models.size(),
                            static_cast<float>(width_) * kProjectionHalfHeight * 2 / height_,
                            kProjectionHalfHeight * 2);
    }

    output.end = Utility::now();
    return output;
}

void Renderer::render(const StackState &stack) {
    // Check to see if the surface has changed size. This is _necessary_ to do every frame when
    // using immersive mode as you'll get no other notification that your renderable area has
    // changed.

    updateRenderArea();
    assert(display_ != nullptr);
    assert(surface_ != nullptr);
    assert(shader_ != nullptr);

    // When the renderable area changes, the fprojection matrix has to also be updated. This is true
    // even if you change from the sample orthographic projection matrix as your aspect ratio has
    // likely changed.
    if (shaderNeedsNewProjectionMatrix_) {
        // a placeholder projection matrix allocated on the stack. Column-major memory layout
        float projectionMatrix[16] = {0};

        // build an orthographic projection matrix for 2d rendering
        Utility::buildOrthographicMatrix(projectionMatrix, kProjectionHalfHeight,
                                         float(width_) / height_, kProjectionNearPlane,
                                         kProjectionFarPlane);

        // send the matrix to the shader
        // Note: the shader must be active for this to work.
        assert(projectionMatrix != nullptr);

        if (shader_ != nullptr) {
            shader_->setProjectionMatrix(projectionMatrix);
        }

        // make sure the matrix isn't generated every frame
        shaderNeedsNewProjectionMatrix_ = false;
    }

    // clear the color buffer
    glClear(GL_COLOR_BUFFER_BIT);

    // Render all the models. There's no depth testing in this sample so they're accepted in the
    // order provided. But the sample EGL setup requests a 24 bit depth buffer so you could
    // configure it at the end of initRenderer
    for (auto &&stage : stack.stages) {
        for (const Model &model : stage.models) {
            shader_->drawModel(model);
        }
    }
}

void Renderer::swapBuffers(const StackState &) {
    auto swapResult = eglSwapBuffers(display_, surface_);
}

void Renderer::threadedInit() {
    std::promise<bool> syncOnCompletion;

    drawLoop_.queueWork([this, &syncOnCompletion]() { initRenderer(syncOnCompletion); });

    syncOnCompletion.get_future().get();

    for (int i = 0; i < loops_.size(); ++i) {
        tids_.push_back(loops_[i].getlooptid());
    }
    tids_.push_back(drawLoop_.getlooptid());
}

void Renderer::initRenderer(std::promise<bool> &syncOnCompletion) {
    // Choose your render attributes
    constexpr EGLint attribs[] = {EGL_RENDERABLE_TYPE,
                                  EGL_OPENGL_ES3_BIT,
                                  EGL_SURFACE_TYPE,
                                  EGL_WINDOW_BIT,
                                  EGL_BLUE_SIZE,
                                  8,
                                  EGL_GREEN_SIZE,
                                  8,
                                  EGL_RED_SIZE,
                                  8,
                                  EGL_DEPTH_SIZE,
                                  24,
                                  EGL_NONE};

    // The default display is probably what you want on Android
    auto display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    eglInitialize(display, nullptr, nullptr);

    // figure out how many configs there are
    EGLint numConfigs;
    eglChooseConfig(display, attribs, nullptr, 0, &numConfigs);

    // get the list of configurations
    std::unique_ptr<EGLConfig[]> supportedConfigs(new EGLConfig[numConfigs]);
    eglChooseConfig(display, attribs, supportedConfigs.get(), numConfigs, &numConfigs);

    // Find a config we like.
    // Could likely just grab the first if we don't care about anything else in the config.
    // Otherwise hook in your own heuristic
    auto config =
            *std::find_if(supportedConfigs.get(), supportedConfigs.get() + numConfigs,
                          [&display](const EGLConfig &config) {
                              EGLint red, green, blue, depth;
                              if (eglGetConfigAttrib(display, config, EGL_RED_SIZE, &red) &&
                                  eglGetConfigAttrib(display, config, EGL_GREEN_SIZE, &green) &&
                                  eglGetConfigAttrib(display, config, EGL_BLUE_SIZE, &blue) &&
                                  eglGetConfigAttrib(display, config, EGL_DEPTH_SIZE, &depth)) {
                                  aout << "Found config with " << red << ", " << green << ", "
                                       << blue << ", " << depth << std::endl;
                                  return red == 8 && green == 8 && blue == 8 && depth == 24;
                              }
                              return false;
                          });

    // create the proper window surface
    EGLint format;
    eglGetConfigAttrib(display, config, EGL_NATIVE_VISUAL_ID, &format);
    EGLSurface surface = eglCreateWindowSurface(display, config, app_->window, nullptr);

    // Create a GLES 3 context
    EGLint contextAttribs[] = {EGL_CONTEXT_CLIENT_VERSION, 3, EGL_NONE};
    EGLContext context = eglCreateContext(display, config, nullptr, contextAttribs);

    // get some window metrics
    auto madeCurrent = eglMakeCurrent(display, surface, surface, context);
    assert(madeCurrent);

    display_ = display;
    surface_ = surface;
    context_ = context;

    width_ = static_cast<uint32_t>(ANativeWindow_getWidth(app_->window));
    height_ = static_cast<uint32_t>(ANativeWindow_getHeight(app_->window));

    PRINT_GL_STRING(GL_VENDOR);
    PRINT_GL_STRING(GL_RENDERER);
    PRINT_GL_STRING(GL_VERSION);
    PRINT_GL_STRING_AS_LIST(GL_EXTENSIONS);

    shader_ = std::unique_ptr<Shader>(
            Shader::loadShader(vertex, fragment, "inPosition", "inUV", "uProjection"));
    assert(shader_);

    // Note: there's only one shader in this demo, so I'll activate it here. For a more complex game
    // you'll want to track the active shader and activate/deactivate it as necessary
    shader_->activate();

    // setup any other gl related global states
    glClearColor(CORNFLOWER_BLUE);

    // enable alpha globally for now, you probably don't want to do this in a game
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

    createHeads();
    syncOnCompletion.set_value(true);
}

void Renderer::updateRenderArea() {
    EGLint width = static_cast<uint32_t>(ANativeWindow_getWidth(app_->window));
    EGLint height = static_cast<uint32_t>(ANativeWindow_getHeight(app_->window));

    if (width != width_ || height != height_) {
        width_ = width;
        height_ = height;
        glViewport(0, 0, width, height);

        // make sure that we lazily recreate the projection matrix before we render
        shaderNeedsNewProjectionMatrix_ = true;
    }
}

std::vector<int64_t> Renderer::findVsyncTargets(int &events, android_poll_source *&pSource,
                                                int numFrames) {
    targets_ = {};
    drawFramesSync(numFrames, events, pSource, "findVsyncTargets");
    return targets_;
}

void Renderer::createHeads() {
    auto assetManager = app_->activity->assetManager;
    Model::texture = TextureAsset::loadAsset(assetManager, "android.png");
    std::vector<Vertex> vertices = {
            Vertex(Vector3{{-0.3, 0.3, 0}}, Vector2{{0, 0}}), // 0
            Vertex(Vector3{{0.3, 0.3, 0}}, Vector2{{1, 0}}),  // 1
            Vertex(Vector3{{0.3, -0.3, 0}}, Vector2{{1, 1}}), // 2
            Vertex(Vector3{{-0.3, -0.3, 0}}, Vector2{{0, 1}}) // 3
    };
    std::vector<Index> indices = {0, 1, 2, 0, 2, 3};
    Model baseModel{vertices, indices};
    int id = 0;
    for (auto &&stage : latest_stage_models_) {
        for (int i = 0; i < stage.models.size(); ++i) {
            ++id;
            float angle = 2 * M_PI * (static_cast<float>(rand()) / static_cast<float>(RAND_MAX));
            float x = 1.5 * static_cast<float>(rand()) / static_cast<float>(RAND_MAX) - 0.75;
            float y = 3.0 * static_cast<float>(rand()) / static_cast<float>(RAND_MAX) - 1.5;
            float mass = rand() % 10 + 1;
            Vector3 offset{{x, y, 0}};
            Model toAdd{baseModel};
            toAdd.move(offset);
            toAdd.setRotationOffset(angle);
            toAdd.setMass(mass);
            toAdd.setId(id);
            stage.models[i] = toAdd;
        }
    }
}

void Renderer::setPhysicsIterations(int iterations) {
    physicsIterations_ = iterations;
}

bool Renderer::startHintSession(int64_t target) {
    if (hintManager_ == nullptr) {
        hintManager_ = APerformanceHint_getManager();
    }
    long preferredRate = APerformanceHint_getPreferredUpdateRateNanos(hintManager_);
    results_["preferredRate"] = std::to_string(preferredRate);
    if (preferredRate > 0 && hintSession_ == nullptr && hintManager_ != nullptr) {
        std::vector<int> toSend(tids_);
        toSend.push_back(gettid());
        lastTarget_ = target;
        hintSession_ =
                APerformanceHint_createSession(hintManager_, toSend.data(), toSend.size(), target);
    }
    bool supported = preferredRate > 0 && hintSession_ != nullptr;
    results_["isHintSessionSupported"] = supported ? "true" : "false";
    return supported;
}

void Renderer::reportActualWorkDuration(int64_t duration) {
    if (isHintSessionRunning()) {
        int ret = APerformanceHint_reportActualWorkDuration(hintSession_, duration);
        if (ret < 0) {
            Utility::setFailure("Failed to report actual work duration with code " +
                                        std::to_string(ret),
                                this);
        }
    }
}

void Renderer::updateTargetWorkDuration(int64_t target) {
    lastTarget_ = target;
    if (isHintSessionRunning()) {
        int ret = APerformanceHint_updateTargetWorkDuration(hintSession_, target);
        if (ret < 0) {
            Utility::setFailure("Failed to update target duration with code " + std::to_string(ret),
                                this);
        }
    }
}

int64_t Renderer::getTargetWorkDuration() {
    return lastTarget_;
}

bool Renderer::isHintSessionRunning() {
    return hintSession_ != nullptr;
}

void Renderer::closeHintSession() {
    APerformanceHint_closeSession(hintSession_);
}

void Renderer::addResult(std::string name, std::string value) {
    results_[name] = value;
}

std::map<std::string, std::string> &Renderer::getResults() {
    return results_;
}

void Renderer::setBaselineMedian(int64_t median) {
    baselineMedian_ = median;
}

template <typename T>
T getMedian(std::vector<T> values) {
    std::sort(values.begin(), values.end());
    return values[values.size() / 2];
}

void Renderer::setChoreographer(AChoreographer *choreographer) {
    choreographer_ = choreographer;
}

void dumpCallbacksData(VsyncData &data) {
    aerr << "Frame time: " << data.frameTimeNanos << std::endl;
    aerr << "Num callbacks: " << data.numVsyncs << std::endl;
    aerr << "Preferred callback: " << data.preferredVsync << std::endl;
    for (auto &&callback : data.vsyncs) {
        aerr << "Callback " << callback.index << " has deadline: " << callback.deadlineNanos
             << " which is: " << callback.deadlineNanos - data.frameTimeNanos
             << " away, vsyncId: " << callback.vsyncId << std::endl;
    }
    aerr << "Finished dump " << std::endl;
}

Vsync &getClosestCallbackToTarget(int64_t target, VsyncData &data) {
    int min_i = 0;
    int64_t min_diff = std::abs(data.vsyncs[0].deadlineNanos - data.frameTimeNanos - target);
    for (int i = 1; i < data.vsyncs.size(); ++i) {
        int64_t diff = std::abs(data.vsyncs[i].deadlineNanos - data.frameTimeNanos - target);
        if (diff < min_diff) {
            min_diff = diff;
            min_i = i;
        }
    }
    return data.vsyncs[min_i];
}

VsyncData fromFrameCallbackData(const AChoreographerFrameCallbackData *data) {
    VsyncData out{.frameTimeNanos = AChoreographerFrameCallbackData_getFrameTimeNanos(data),
                  .numVsyncs = AChoreographerFrameCallbackData_getFrameTimelinesLength(data),
                  .preferredVsync =
                          AChoreographerFrameCallbackData_getPreferredFrameTimelineIndex(data),
                  .vsyncs{}};
    for (size_t i = 0; i < out.numVsyncs; ++i) {
        out.vsyncs.push_back(
                {.index = i,
                 .deadlineNanos =
                         AChoreographerFrameCallbackData_getFrameTimelineDeadlineNanos(data, i),
                 .presentationNanos =
                         AChoreographerFrameCallbackData_getFrameTimelineExpectedPresentationTimeNanos(
                                 data, i),
                 .vsyncId = AChoreographerFrameCallbackData_getFrameTimelineVsyncId(data, i)});
    }
    return out;
}

void Renderer::rawChoreographerCallback(const AChoreographerFrameCallbackData *data, void *) {
    static std::mutex choreographerMutex{};
    assert(appPtr != nullptr);
    std::scoped_lock lock(choreographerMutex);
    getRenderer()->choreographerCallback(data);
}

void Renderer::choreographerCallback(const AChoreographerFrameCallbackData *data) {
    if (framesRemaining_ > 0) {
        --framesRemaining_;
    }
    VsyncData callbackData = fromFrameCallbackData(data);
    batchData_.vsyncs.push_back(callbackData);
    // Allow a passed-in method to check on the callback data
    if (wakeupMethod_ != std::nullopt) {
        (*wakeupMethod_)(callbackData);
    }
    if (framesRemaining_ == 0 && targets_.size() == 0) {
        dumpCallbacksData(callbackData);
        for (int i = 0; i < callbackData.vsyncs.size(); ++i) {
            targets_.push_back(callbackData.vsyncs[i].deadlineNanos - callbackData.frameTimeNanos);
        }
    }

    int64_t start = Utility::now();
    if (lastStart_.has_value()) {
        batchData_.intervals.push_back(start - *lastStart_);
    }
    lastStart_ = start;

    StackState startState{
            .intendedVsync = getClosestCallbackToTarget(lastTarget_, callbackData),
            .stages{},
    };

    batchData_.selectedVsync.push_back(startState.intendedVsync);

    if (framesRemaining_ > 0) {
        postCallback();
    }

    // Copy here so the lambdas can use that copy and not the global one
    int remain = framesRemaining_;

    stackSchedule<StackState>(
            [=, startTime = start, this](int level, StackState &state) {
                // Render against the most recent physics data for this pipeline
                state.stages[level] = updateModels(latest_stage_models_[level]);

                // Save the output from this physics step for the next frame to use
                latest_stage_models_[level] = state.stages[level];

                // If we're at the end of the stack, push a draw job to the draw loop
                if (level == 0) {
                    drawLoop_.queueWork([=, this]() {
                        if (remain == 0) {
                            framesDone_.set_value(true);
                        }

                        render(state);
                        // Update final stage time after render finishes

                        int64_t duration = Utility::now() - startTime;
                        batchData_.durations.push_back(duration);
                        if (isHintSessionRunning()) {
                            reportActualWorkDuration(duration);
                        }

                        swapBuffers(state);
                    });
                }
            },
            std::move(startState));
}

FrameStats Renderer::drawFramesSync(int frames, int &events, android_poll_source *&pSource,
                                    std::string testName) {
    bool namedTest = testName.size() > 0;

    // Make sure this can only run once at a time because the callback is unique
    static std::mutex mainThreadLock;
    framesDone_ = std::promise<bool>();
    auto frameFuture = framesDone_.get_future();

    std::scoped_lock lock(mainThreadLock);
    batchData_ = {};
    framesRemaining_ = frames;
    postCallback();

    while (true) {
        int retval = ALooper_pollOnce(30, nullptr, &events, (void **)&pSource);
        if (retval > 0 && pSource) {
            pSource->process(app_, pSource);
        }
        auto status = frameFuture.wait_for(0s);
        if (status == std::future_status::ready) {
            break;
        }
    }
    if (namedTest) {
        addResult(testName + "_durations", Utility::serializeValues(batchData_.durations));
        addResult(testName + "_intervals", Utility::serializeValues(batchData_.intervals));
    }

    return getFrameStats(batchData_, testName);
}

void Renderer::postCallback() {
    AChoreographer_postVsyncCallback(choreographer_, rawChoreographerCallback, nullptr);
}

FrameStats Renderer::getFrameStats(FrameBatchData &batchData, std::string &testName) {
    FrameStats stats;
    stats.actualTargetDuration = lastTarget_;

    std::vector<int64_t> targets;
    for (int i = 0; i < batchData.vsyncs.size(); ++i) {
        VsyncData &vsyncData = batchData.vsyncs[i];
        Vsync &selectedVsync = batchData.selectedVsync[i];
        targets.push_back(selectedVsync.deadlineNanos - vsyncData.frameTimeNanos);
    }

    stats.medianClosestDeadlineToTarget = getMedian(targets);

    double sum = std::accumulate(batchData.durations.begin(), batchData.durations.end(),
                                 static_cast<double>(0.0));
    double mean = sum / static_cast<double>(batchData.durations.size());
    int dropCount = 0;
    double varianceSum = 0;
    for (int64_t &duration : batchData.durations) {
        if (isHintSessionRunning() && duration > lastTarget_) {
            ++dropCount;
        }
        varianceSum += (duration - mean) * (duration - mean);
    }
    std::vector<int64_t> selectedDeadlineDurations;
    for (int i = 0; i < batchData.vsyncs.size(); ++i) {
        selectedDeadlineDurations.push_back(batchData.selectedVsync[i].deadlineNanos -
                                            batchData.vsyncs[i].frameTimeNanos);
    }
    if (batchData.durations.size() > 0) {
        stats.medianWorkDuration = getMedian(batchData.durations);
    }
    if (batchData.intervals.size() > 0) {
        stats.medianFrameInterval = getMedian(batchData.intervals);
    }
    stats.deviation = std::sqrt(varianceSum / static_cast<double>(batchData.durations.size() - 1));
    if (isHintSessionRunning()) {
        stats.exceededCount = dropCount;
        stats.exceededFraction =
                static_cast<double>(dropCount) / static_cast<double>(batchData.durations.size());
        stats.efficiency = static_cast<double>(sum) /
                static_cast<double>(batchData.durations.size() *
                                    std::min(lastTarget_, baselineMedian_));
    }

    if (testName.size() > 0) {
        addResult(testName + "_selected_deadline_durations",
                  Utility::serializeValues(selectedDeadlineDurations));
        addResult(testName + "_sum", std::to_string(sum));
        addResult(testName + "_num_durations", std::to_string(batchData.durations.size()));
        addResult(testName + "_mean", std::to_string(mean));
        addResult(testName + "_median", std::to_string(stats.medianWorkDuration));
        addResult(testName + "_median_interval", std::to_string(stats.medianFrameInterval));
        addResult(testName + "_median_deadline_duration",
                  std::to_string(stats.medianClosestDeadlineToTarget));
        addResult(testName + "_intended_deadline_duration",
                  std::to_string(stats.actualTargetDuration));
        addResult(testName + "_deviation", std::to_string(stats.deviation));
        if (isHintSessionRunning()) {
            addResult(testName + "_target", std::to_string(getTargetWorkDuration()));
            addResult(testName + "_target_exceeded_count", std::to_string(*stats.exceededCount));
            addResult(testName + "_target_exceeded_fraction",
                      std::to_string(*stats.exceededFraction));
            addResult(testName + "_efficiency", std::to_string(*stats.efficiency));
        }
    }

    return stats;
}

void Renderer::makeInstance(android_app *pApp) {
    if (sRenderer == nullptr) {
        sRenderer = std::make_unique<Renderer>(pApp);
    }
}

Renderer *Renderer::getInstance() {
    if (sRenderer == nullptr) {
        return nullptr;
    }
    return sRenderer.get();
}

std::unique_ptr<Renderer> Renderer::sRenderer = nullptr;