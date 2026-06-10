/*
 * Copyright 2024 The Android Open Source Project
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

#include <functional>
#include <future>
#include <optional>
#include <thread>

class EventLoop {
public:
    void queueWork(std::function<void()>&& job);
    ~EventLoop();
    int getlooptid();

private:
    // Front queue where items are loaded
    std::unique_ptr<std::queue<std::function<void()>>> mEventsQueue{
            std::make_unique<std::queue<std::function<void()>>>()};
    // Back queue where items are processed
    std::unique_ptr<std::queue<std::function<void()>>> mEventsProcessing{
            std::make_unique<std::queue<std::function<void()>>>()};
    std::condition_variable mCV;
    std::mutex mQueueMutex{};
    std::mutex mRunnerMutex{};
    std::promise<int> mTidPromise{};
    std::future<int> mTidFuture = mTidPromise.get_future();
    std::optional<int> mTid;
    bool mStopping = false;
    std::unique_ptr<std::thread> mRunner = std::make_unique<std::thread>(&EventLoop::run, this);
    void run();
};