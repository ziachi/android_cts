/*
 * Copyright 2025 The Android Open Source Project
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
#include <android_native_app_glue.h>

#include <chrono>
#include <thread>

#include "vulkan_renderer.h"

struct AppState {
    VulkanRenderer renderer;
    bool canRender = false;
};

static void onAppCmd(struct android_app *app, int32_t cmd) {
    auto *appState = (AppState *)app->userData;
    switch (cmd) {
        case APP_CMD_START:
            if (app->window != nullptr) {
                appState->renderer.reset(app->window, app->activity->assetManager);
                appState->renderer.init();
                appState->canRender = true;
            }
        case APP_CMD_INIT_WINDOW:
            if (app->window != nullptr) {
                appState->renderer.reset(app->window, app->activity->assetManager);
                if (!appState->renderer.initialized) {
                    appState->renderer.init();
                }
                appState->canRender = true;
            }
            break;
        case APP_CMD_TERM_WINDOW:
            appState->canRender = false;
            break;
        case APP_CMD_DESTROY:
            appState->renderer.cleanup();
        default:
            break;
    }
}

void android_main(struct android_app *app) {
    AppState appState{};

    app->userData = &appState;
    app->onAppCmd = onAppCmd;

    int frame = 0;

    while (true) {
        int ident;
        int events;
        android_poll_source *source;
        while ((ident = ALooper_pollOnce(appState.canRender ? 0 : -1, nullptr, &events,
                                         (void **)&source)) >= 0) {
            if (source != nullptr) {
                source->process(app, source);
            }
        }

        appState.renderer.render();
        if (++frame % 10 == 0) {
            std::this_thread::sleep_for(std::chrono::milliseconds(200));
        }
    }
}