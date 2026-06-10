// Copyright 2024 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include <android/binder_status.h>
#include <assert.h>
#include <dlfcn.h>
#include <jni.h>
#include <log/log.h>

#include <optional>
#include <stdexcept>
#include <string>
#include <vector>

#include "nativehelper/scoped_local_ref.h"
#include "nativehelper/scoped_utf_chars.h"
#include "nativehelper/utils.h"

const char kLibandroidPath[] = "libandroid.so";

struct ADynamicInstrumentationManager_MethodDescriptor;
typedef struct ADynamicInstrumentationManager_MethodDescriptor
        ADynamicInstrumentationManager_MethodDescriptor;

struct ADynamicInstrumentationManager_TargetProcess;
typedef struct ADynamicInstrumentationManager_TargetProcess
        ADynamicInstrumentationManager_TargetProcess;

struct ADynamicInstrumentationManager_ExecutableMethodFileOffsets;
typedef struct ADynamicInstrumentationManager_ExecutableMethodFileOffsets
        ADynamicInstrumentationManager_ExecutableMethodFileOffsets;

typedef ADynamicInstrumentationManager_TargetProcess* (
        *ADynamicInstrumentationManager_TargetProcess_create)(uid_t uid, pid_t pid,
                                                              const char* processName);
typedef void (*ADynamicInstrumentationManager_TargetProcess_destroy)(
        const ADynamicInstrumentationManager_TargetProcess* instance);

typedef ADynamicInstrumentationManager_MethodDescriptor* (
        *ADynamicInstrumentationManager_MethodDescriptor_create)(
        const char* fullyQualifiedClassName, const char* methodName,
        const char* fullyQualifiedParameters[], unsigned int numParameters);
typedef void (*ADynamicInstrumentationManager_MethodDescriptor_destroy)(
        const ADynamicInstrumentationManager_MethodDescriptor* instance);

typedef const char* (*ADynamicInstrumentationManager_ExecutableMethodFileOffsets_getContainerPath)(
        const ADynamicInstrumentationManager_ExecutableMethodFileOffsets* instance);
typedef unsigned long (
        *ADynamicInstrumentationManager_ExecutableMethodFileOffsets_getContainerOffset)(
        const ADynamicInstrumentationManager_ExecutableMethodFileOffsets* instance);
typedef unsigned long (*ADynamicInstrumentationManager_ExecutableMethodFileOffsets_getMethodOffset)(
        const ADynamicInstrumentationManager_ExecutableMethodFileOffsets* instance);
typedef void (*ADynamicInstrumentationManager_ExecutableMethodFileOffsets_destroy)(
        const ADynamicInstrumentationManager_ExecutableMethodFileOffsets* instance);

typedef binder_status_t (*ADynamicInstrumentationManager_getExecutableMethodFileOffsets)(
        const ADynamicInstrumentationManager_TargetProcess& targetProcess,
        const ADynamicInstrumentationManager_MethodDescriptor& methodDescriptor,
        const ADynamicInstrumentationManager_ExecutableMethodFileOffsets** out);

template <typename T>
void getLibFunction(void* handle, const char* identifier, T* out) {
    auto result = reinterpret_cast<T>(dlsym(handle, identifier));
    if (!result) {
        ALOGE("dlsym error: %s %s %s", __func__, dlerror(), identifier);
        assert(result);
    }
    *out = result;
}

extern "C" jobject
Java_android_os_instrumentation_cts_DynamicInstrumentationManagerTest_getExecutableMethodFileOffsetsNative(
        JNIEnv* env, jclass, jint uid, jint pid, jstring processName, jstring fqcn,
        jstring methodName, jobjectArray fqParameters) {
    void* handle = dlopen(kLibandroidPath, RTLD_NOW | RTLD_LOCAL);
    if (!handle) {
        ALOGE("dlopen error: %s %s", __func__, dlerror());
        return nullptr;
    }

    ADynamicInstrumentationManager_TargetProcess_create targetProcess_create;
    getLibFunction(handle, "ADynamicInstrumentationManager_TargetProcess_create",
                   &targetProcess_create);
    ADynamicInstrumentationManager_TargetProcess_destroy targetProcess_destroy;
    getLibFunction(handle, "ADynamicInstrumentationManager_TargetProcess_destroy",
                   &targetProcess_destroy);
    ADynamicInstrumentationManager_MethodDescriptor_create methodDescriptor_create;
    getLibFunction(handle, "ADynamicInstrumentationManager_MethodDescriptor_create",
                   &methodDescriptor_create);
    ADynamicInstrumentationManager_MethodDescriptor_destroy methodDescriptor_destroy;
    getLibFunction(handle, "ADynamicInstrumentationManager_MethodDescriptor_destroy",
                   &methodDescriptor_destroy);
    ADynamicInstrumentationManager_getExecutableMethodFileOffsets getExecutableMethodFileOffsets;
    getLibFunction(handle, "ADynamicInstrumentationManager_getExecutableMethodFileOffsets",
                   &getExecutableMethodFileOffsets);
    ADynamicInstrumentationManager_ExecutableMethodFileOffsets_destroy
            executableMethodFileOffsets_destroy;
    getLibFunction(handle, "ADynamicInstrumentationManager_ExecutableMethodFileOffsets_destroy",
                   &executableMethodFileOffsets_destroy);
    ADynamicInstrumentationManager_ExecutableMethodFileOffsets_getContainerPath getContainerPath;
    getLibFunction(handle,
                   "ADynamicInstrumentationManager_ExecutableMethodFileOffsets_getContainerPath",
                   &getContainerPath);
    ADynamicInstrumentationManager_ExecutableMethodFileOffsets_getContainerOffset
            getContainerOffset;
    getLibFunction(handle,
                   "ADynamicInstrumentationManager_ExecutableMethodFileOffsets_getContainerOffset",
                   &getContainerOffset);
    ADynamicInstrumentationManager_ExecutableMethodFileOffsets_getMethodOffset getMethodOffset;
    getLibFunction(handle,
                   "ADynamicInstrumentationManager_ExecutableMethodFileOffsets_getMethodOffset",
                   &getMethodOffset);

    jsize numParams = env->GetArrayLength(fqParameters);
    // need to hold the `ScopedUtfChars` outside the loop, so they don't get dropped too early
    std::vector<ScopedUtfChars> scopedParams;
    std::vector<const char*> cParams;
    for (jsize i = 0; i < numParams; i++) {
        ScopedLocalRef<jobject> obj(env, env->GetObjectArrayElement(fqParameters, i));
        scopedParams.push_back(GET_UTF_OR_RETURN(env, static_cast<jstring>(obj.get())));
        cParams.push_back(scopedParams[i].c_str());
    }

    ScopedUtfChars cProcessName = GET_UTF_OR_RETURN(env, processName);
    ScopedUtfChars cFqcn = GET_UTF_OR_RETURN(env, fqcn);
    ScopedUtfChars cMethodName = GET_UTF_OR_RETURN(env, methodName);
    const ADynamicInstrumentationManager_TargetProcess* targetProcess =
            targetProcess_create((uid_t)uid, (pid_t)pid, cProcessName.c_str());
    const ADynamicInstrumentationManager_MethodDescriptor* methodDescriptor =
            methodDescriptor_create(cFqcn.c_str(), cMethodName.c_str(), cParams.data(), numParams);

    const ADynamicInstrumentationManager_ExecutableMethodFileOffsets* offsets = nullptr;
    int32_t result = getExecutableMethodFileOffsets(*targetProcess, *methodDescriptor, &offsets);

    targetProcess_destroy(targetProcess);
    methodDescriptor_destroy(methodDescriptor);

    ScopedLocalRef<jobject> innerClass(env, nullptr);
    if (offsets != nullptr) {
        ScopedLocalRef<jstring> containerPath =
                CREATE_UTF_OR_RETURN(env, getContainerPath(offsets));
        jlong containerOffset = static_cast<jlong>(getContainerOffset(offsets));
        jlong methodOffset = static_cast<jlong>(getMethodOffset(offsets));
        executableMethodFileOffsets_destroy(offsets);
        ScopedLocalRef<jclass>
                clazz(env,
                      env->FindClass(
                              "android/os/instrumentation/cts/"
                              "DynamicInstrumentationManagerTest$ExecutableMethodFileOffsets"));
        if (clazz.get() == nullptr) {
            ALOGE("Could not find JNI test class "
                  "DynamicInstrumentationManagerTest$ExecutableMethodFileOffsets");
            return nullptr;
        }
        jmethodID constructor_id =
                env->GetMethodID(clazz.get(), "<init>", "(Ljava/lang/String;JJ)V");
        innerClass.reset(env->NewObject(clazz.get(), constructor_id, containerPath.get(),
                                        containerOffset, methodOffset));
    }

    ScopedLocalRef<jclass>
            clazz(env,
                  env->FindClass("android/os/instrumentation/cts/"
                                 "DynamicInstrumentationManagerTest$OffsetsWithStatusCode"));
    if (clazz.get() == nullptr) {
        ALOGE("Could not find JNI test class "
              "DynamicInstrumentationManagerTest$OffsetsWithStatusCode");
        return nullptr;
    }

    jmethodID constructor_id =
            env->GetMethodID(clazz.get(), "<init>",
                             "(ILandroid/os/instrumentation/cts/"
                             "DynamicInstrumentationManagerTest$ExecutableMethodFileOffsets;)V");
    return env->NewObject(clazz.get(), constructor_id, result, innerClass.get());
}
