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

#include "EventLoop.h"

#include <unistd.h>

#include <mutex>

#include "Utility.h"

void EventLoop::queueWork(std::function<void()>&& job) {
    {
        std::scoped_lock lock(mQueueMutex);
        mEventsQueue->emplace(std::move(job));
    }
    mCV.notify_all();
}

EventLoop::~EventLoop() {
    mStopping = true;
    mCV.notify_all();
    mRunner->join();
}

void EventLoop::run() {
    mTidPromise.set_value(gettid());
    while (!mStopping) {
        {
            std::unique_lock lock(mQueueMutex);
            mCV.wait(lock, [&] { return (!mEventsQueue->empty() || mStopping); });
            if (mStopping) {
                return;
            }
            if (!mEventsQueue->empty()) {
                // Swap the front and back queue
                mEventsQueue.swap(mEventsProcessing);
            }
        }
        // Process all queued events
        // lock not needed here since mEventsProcessing is held by this thread uniquely
        while (!mEventsProcessing->empty() && !mStopping) {
            // Run the item
            {
                std::scoped_lock lock{mRunnerMutex};
                (mEventsProcessing->front())();
            }
            mEventsProcessing->pop();
        }
    }
}

int EventLoop::getlooptid() {
    if (!mTid.has_value()) {
        mTid = mTidFuture.get();
    }
    return *mTid;
}