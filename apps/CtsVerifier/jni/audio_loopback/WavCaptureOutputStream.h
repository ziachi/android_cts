/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef __WAVCAPTUREOUTPUTSTREAM_H__
#define __WAVCAPTUREOUTPUTSTREAM_H__

#include "WaveFileWriter.h"

#include <vector>

class WavCaptureOutputStream : public WaveFileOutputStream {
public:
    WavCaptureOutputStream() {
    }

    void init() {
        mData.clear();
    }

    void write(uint8_t b) override {
        mData.push_back(b);
    }

    void write(uint8_t* bytes, size_t numBytes) {
        while (numBytes-- > 0) {
            mData.push_back(*bytes++);
        }
    }

    int32_t length() {
        return (int32_t) mData.size();
    }

    uint8_t *getData() {
        return mData.data();
    }

    void abandonData() {
        mData.clear();
    }
private:
    std::vector<uint8_t> mData;
};

#endif
