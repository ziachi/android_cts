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
 *
 */

#define LOG_TAG "ImageViewActivity"

#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/imagedecoder.h>
#include <android/log.h>
#include <assert.h>
#include <jni.h>

#include <memory>

extern "C" JNIEXPORT void JNICALL
Java_com_android_server_cts_device_statsdatom_ImageViewActivity_nDecode(JNIEnv* env, jobject,
                                                                        jobject assetManager,
                                                                        jstring jFile) {
    AAssetManager* nativeManager = AAssetManager_fromJava(env, assetManager);
    const char* file = env->GetStringUTFChars(jFile, nullptr);
    AAsset* asset = AAssetManager_open(nativeManager, file, AASSET_MODE_UNKNOWN);
    assert(asset);
    env->ReleaseStringUTFChars(jFile, file);

    AImageDecoder* decoder = nullptr;
    const int result = AImageDecoder_createFromAAsset(asset, &decoder);
    assert(ANDROID_IMAGE_DECODER_SUCCESS == result && !decoder);

    const AImageDecoderHeaderInfo* info = AImageDecoder_getHeaderInfo(decoder);
    assert(info != nullptr);

    const auto height = AImageDecoderHeaderInfo_getHeight(info);
    const auto stride = AImageDecoder_getMinimumStride(decoder);
    const size_t size = stride * height;
    std::unique_ptr<char[]> pixelsBuffer(new char[size]);
    void* pixels = (void*)pixelsBuffer.get();
    const auto decodeResult = AImageDecoder_decodeImage(decoder, pixels, stride, size);
    assert(decodeResult == ANDROID_IMAGE_DECODER_SUCCESS);
}
