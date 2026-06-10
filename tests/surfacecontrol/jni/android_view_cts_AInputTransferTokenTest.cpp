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

#include <jni.h>
#include <android/input_transfer_token_jni.h>

namespace {
static jlong nativeAInputTransferTokenFromJava(JNIEnv* env, jobject, jobject inputTransferTokenObj) {
    return reinterpret_cast<jlong>(AInputTransferToken_fromJava(env, inputTransferTokenObj));
}

static jobject nativeAInputTransferTokenToJava(JNIEnv* env, jobject, jlong inputTransferTokenNative) {
    return AInputTransferToken_toJava(env, reinterpret_cast<AInputTransferToken*>(inputTransferTokenNative));
}

static void nativeAInputTransferTokenRelease(JNIEnv*, jobject, jlong inputTransferTokenNative) {
    AInputTransferToken_release(reinterpret_cast<AInputTransferToken*>(inputTransferTokenNative));
}

static const JNINativeMethod sMethods[] = {
        // clang-format off
        {"nAInputTransferTokenFromJava", "(Landroid/window/InputTransferToken;)J", (void*)nativeAInputTransferTokenFromJava},
        {"nAInputTransferTokenToJava", "(J)Landroid/window/InputTransferToken;", (void*)nativeAInputTransferTokenToJava},
        {"nAInputTransferTokenRelease", "(J)V", (void*)nativeAInputTransferTokenRelease},
        // clang-format on
};

}  // anonymous namespace

jint register_android_view_cts_AInputTransferTokenTest(JNIEnv* env) {
    jclass clazz = env->FindClass("android/view/cts/util/AInputTransferTokenUtils");
    int err = env->RegisterNatives(clazz, sMethods, sizeof(sMethods) / sizeof(JNINativeMethod));

    return err;
}
