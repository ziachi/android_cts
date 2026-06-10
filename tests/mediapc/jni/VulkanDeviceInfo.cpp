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

#include <android/log.h>
#include <jni.h>
#include <vkjson.h>

namespace {

jstring GetVkJSON(JNIEnv* env, jclass /*clazz*/) {
    std::string vkjson(VkJsonInstanceToJson(VkJsonGetInstance()));
    return env->NewStringUTF(vkjson.c_str());
}

static JNINativeMethod gMethods[] = {
        {"nativeGetVkJSON", "()Ljava/lang/String;", (void*) GetVkJSON},
};

} // anonymous namespace

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    jclass clazz = env->FindClass("android/mediapc/cts/VulkanTest");
    int status = env->RegisterNatives(clazz, gMethods, sizeof(gMethods) / sizeof(JNINativeMethod));
    if (status != JNI_OK) {
        return JNI_ERR;
    }
    return JNI_VERSION_1_6;
}
