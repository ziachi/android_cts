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

#include <android/log.h>
#include <camera/NdkCameraError.h>
#include <camera/NdkCameraManager.h>
#include <camera/NdkCameraMetadata.h>
#include <jni.h>
#include <sys/types.h>

#include <array>
#include <cstdint>
#include <functional>
#include <iterator>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#define TAG "VirtualCameraNdkTest"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace android {
namespace virtualdevice {
namespace cts {

constexpr int kDefaultDeviceId = 0;
constexpr int kInfoDeviceIdTag = ACAMERA_INFO_START + 5;
static jclass gNativeCameraManagerClass = nullptr;
static jclass gAvailabilityCallbackClass = nullptr;

jobjectArray toObjectArray(JNIEnv* env, const std::vector<std::string>& v) {
    jobjectArray array = env->NewObjectArray(v.size(), env->FindClass("java/lang/String"),
                                             /*initialElement=*/nullptr);
    for (int i = 0; i < v.size(); ++i) {
        env->SetObjectArrayElement(array, i, env->NewStringUTF(v[i].c_str()));
    }
    return array;
}

std::string toNativeString(JNIEnv* env, jstring string) {
    const char* cstr = env->GetStringUTFChars(string, /*isCopy=*/nullptr);
    std::string str(cstr);
    env->ReleaseStringUTFChars(string, cstr);
    return str;
}

template <typename jniEnvCallable>
static void executeInJvm(JavaVM* jvm, jniEnvCallable callable) {
    if (jvm == nullptr) {
        LOGE("Cannot execute call in JVM callback, jvm is null");
        return;
    }

    JNIEnv* env = nullptr;
    bool needsToBeDetached = false;
    int status = jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_4);
    if (status == JNI_EDETACHED) {
        // We are called from non-jni thread, we need to attach JNI env to call java
        // and remember to detach it later.
        int err = jvm->AttachCurrentThread(&env, /*thr_ags=*/nullptr);
        if (err != 0 || env == nullptr) {
            LOGE("Failed to attach native thread for java callback, error %d", err);
            return;
        }
        needsToBeDetached = true;
    } else if (status != JNI_OK) {
        LOGE("Cannot get JNI Env -> error %d:", status);
        return;
    }

    callable(env);

    if (needsToBeDetached) {
        jvm->DetachCurrentThread();
    }
}

class AvailabilityCallbackProxy {
public:
    AvailabilityCallbackProxy(JNIEnv* env, jobject javaCallback) {
        env->GetJavaVM(&mJvm);
        mJavaCallback = env->NewGlobalRef(javaCallback);

        mCameraManagerCallbacks.context = reinterpret_cast<void*>(this);
        mCameraManagerCallbacks.onCameraAvailable =
                AvailabilityCallbackProxy::onCameraAvailableCallback;
        mCameraManagerCallbacks.onCameraUnavailable =
                AvailabilityCallbackProxy::onCameraUnavailableCallback;
    }

    ~AvailabilityCallbackProxy() {
        std::lock_guard<std::mutex> lock(mLock);
        if (mJavaCallback == nullptr) {
            return;
        }
        executeInJvm(mJvm, [this](JNIEnv* env) { env->DeleteGlobalRef(mJavaCallback); });
    }

    const ACameraManager_AvailabilityCallbacks* getCallbacksPtr() const {
        return &mCameraManagerCallbacks;
    }

private:
    void invokeCallback(const std::string& cameraId, const bool available) const {
        std::lock_guard<std::mutex> lock(mLock);
        if (mJavaCallback == nullptr) {
            return;
        }

        executeInJvm(mJvm, [&cameraId, available, this](JNIEnv* env) {
            jmethodID method =
                    env->GetMethodID(gAvailabilityCallbackClass,
                                     available ? "onCameraAvailable" : "onCameraUnavailable",
                                     "(Ljava/lang/String;)V");
            env->CallVoidMethod(mJavaCallback, method, env->NewStringUTF(cameraId.c_str()));
        });
    }

    static void onCameraAvailableCallback(void* availabilityCallbackProxy, const char* cameraId) {
        auto proxy = reinterpret_cast<AvailabilityCallbackProxy*>(availabilityCallbackProxy);
        proxy->invokeCallback(cameraId, /*available=*/true);
    }

    static void onCameraUnavailableCallback(void* availabilityCallbackProxy, const char* cameraId) {
        auto proxy = reinterpret_cast<AvailabilityCallbackProxy*>(availabilityCallbackProxy);
        proxy->invokeCallback(cameraId, /*available=*/false);
    }

    mutable std::mutex mLock;
    JavaVM* mJvm;
    jobject mJavaCallback;
    ACameraManager_AvailabilityCallbacks mCameraManagerCallbacks;
};

class CameraManager {
public:
    CameraManager() : mCameraManager(ACameraManager_create(), ACameraManager_delete){};

    ~CameraManager() {
        std::lock_guard<std::mutex> lock(mLock);
        for (int i = 0; i < mAvailabilityCallbacks.size(); i++) {
            ACameraManager_unregisterAvailabilityCallback(mCameraManager.get(),
                                                          mAvailabilityCallbacks[i]
                                                                  ->getCallbacksPtr());
        }
    }

    std::vector<std::string> getCameraIds() const {
        ACameraIdList* cameraIdList;
        camera_status_t status =
                ACameraManager_getCameraIdList(mCameraManager.get(), &cameraIdList);
        if (status != ACAMERA_OK || cameraIdList == nullptr) {
            return {};
        }

        std::vector<std::string> cameraIds;
        cameraIds.reserve(cameraIdList->numCameras);
        std::copy(cameraIdList->cameraIds, cameraIdList->cameraIds + cameraIdList->numCameras,
                  std::back_inserter(cameraIds));
        ACameraManager_deleteCameraIdList(cameraIdList);
        return cameraIds;
    }

    std::unique_ptr<ACameraMetadata, void (*)(ACameraMetadata*)> getCameraCharacteristics(
            const std::string& cameraId) const {
        ACameraMetadata* characteristics = nullptr;
        ACameraManager_getCameraCharacteristics(mCameraManager.get(), cameraId.c_str(),
                                                &characteristics);
        return {characteristics, ACameraMetadata_free};
    }

    int getCameraDeviceId(const std::string& cameraId) const {
        auto characteristics = getCameraCharacteristics(cameraId);
        if (characteristics == nullptr) {
            return kDefaultDeviceId;
        }

        ACameraMetadata_const_entry entry;
        camera_status_t status =
                ACameraMetadata_getConstEntry(characteristics.get(), kInfoDeviceIdTag, &entry);
        if (status != ACAMERA_OK) {
            return kDefaultDeviceId;
        }
        return entry.data.i32[0];
    }

    void registerAvailabilityCallback(std::unique_ptr<AvailabilityCallbackProxy> callbackProxy) {
        ACameraManager_registerAvailabilityCallback(mCameraManager.get(),
                                                    callbackProxy->getCallbacksPtr());
        std::lock_guard<std::mutex> lock(mLock);
        mAvailabilityCallbacks.emplace_back(std::move(callbackProxy));
    }

private:
    const std::unique_ptr<ACameraManager, void (*)(ACameraManager*)> mCameraManager;

    std::mutex mLock;
    std::vector<std::unique_ptr<AvailabilityCallbackProxy>> mAvailabilityCallbacks;
};

std::unique_ptr<ACameraManager, void (*)(ACameraManager*)> getCameraManager() {
    return {ACameraManager_create(), ACameraManager_delete};
}

jlong createCameraManager(JNIEnv*, jclass) {
    return reinterpret_cast<jlong>(new CameraManager);
}

void releaseCameraManager(JNIEnv*, jclass, jlong cameraManagerPtr) {
    CameraManager* ptr = reinterpret_cast<CameraManager*>(cameraManagerPtr);
    if (ptr != nullptr) {
        delete ptr;
    }
}

jobjectArray getCameraIds(JNIEnv* env, jclass, jlong cameraManagerPtr) {
    CameraManager* ptr = reinterpret_cast<CameraManager*>(cameraManagerPtr);
    if (ptr == nullptr) {
        return nullptr;
    }
    return toObjectArray(env, ptr->getCameraIds());
}

jint getDeviceId(JNIEnv* env, jclass, jlong cameraManagerPtr, jstring cameraId) {
    CameraManager* ptr = reinterpret_cast<CameraManager*>(cameraManagerPtr);
    if (ptr == nullptr) {
        return kDefaultDeviceId;
    }
    return ptr->getCameraDeviceId(toNativeString(env, cameraId));
}

void registerAvailabilityCallback(JNIEnv* env, jclass, jlong cameraManagerPtr,
                                  jobject availabilityCallback) {
    CameraManager* ptr = reinterpret_cast<CameraManager*>(cameraManagerPtr);
    if (ptr == nullptr || availabilityCallback == nullptr) {
        return;
    }

    ptr->registerAvailabilityCallback(
            std::make_unique<AvailabilityCallbackProxy>(env, availabilityCallback));
}

// ------------------------------------------------------------------------------------------------

static std::array<JNINativeMethod, 5>
        gMethods{JNINativeMethod{"createCameraManager", "()J", (void*)createCameraManager},
                 JNINativeMethod{"releaseCameraManager", "(J)V", (void*)releaseCameraManager},
                 JNINativeMethod{"getCameraIds", "(J)[Ljava/lang/String;", (void*)getCameraIds},
                 JNINativeMethod{"getDeviceId", "(JLjava/lang/String;)I", (void*)getDeviceId},
                 JNINativeMethod{"registerAvailabilityCallback",
                                 "(JLandroid/virtualdevice/cts/camera/util/"
                                 "NativeCameraManager$AvailabilityCallback;)V",
                                 (void*)registerAvailabilityCallback}};

int register_android_virtualdevice_cts_camera_util_NativeCameraTestActivity(JNIEnv* env) {
    jclass clazz = env->FindClass("android/virtualdevice/cts/camera/util/NativeCameraManager");
    gNativeCameraManagerClass = reinterpret_cast<jclass>(env->NewGlobalRef(clazz));

    gAvailabilityCallbackClass = reinterpret_cast<jclass>(env->NewGlobalRef(env->FindClass(
            "android/virtualdevice/cts/camera/util/NativeCameraManager$AvailabilityCallback")));
    return env->RegisterNatives(clazz, gMethods.data(), gMethods.size());
    return 0;
}

} // namespace cts
} // namespace virtualdevice
} // namespace android
