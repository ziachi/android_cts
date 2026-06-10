/*
 * Copyright (C) 2024 The Android Open Source Project
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

#define LOG_TAG "CameraOpenerJni"
#define LOG_NDEBUG 0

#include <android/log.h>
#include <log/log.h>

#include <chrono>
#include <condition_variable>
#include <memory>
#include <mutex>
#include <thread>

#include "camera/NdkCameraManager.h"
#include "media/NdkImage.h"
#include "media/NdkImageReader.h"

namespace {

template <typename T, void (*free_ptr)(T*)>
class Auto : public std::unique_ptr<T, void (*)(T*)> {
public:
    Auto() : std::unique_ptr<T, void (*)(T*)>(nullptr, free_ptr) {}
    Auto(T* t) : std::unique_ptr<T, void (*)(T*)>(t, free_ptr) {}
};

class CameraOpenerJni {
public:
    CameraOpenerJni()
          : mCameraManager(ACameraManager_create()),
            mCameraDeviceListener(*this),
            mCameraDeviceCb(&mCameraDeviceListener, CameraDeviceListener::onDisconnectedStatic,
                            CameraDeviceListener::onErrorStatic),
            mImgReaderAnw(nullptr),
            mImgReaderListener(),
            mImgReaderCb(&mImgReaderListener, ImageReaderListener::signalImageStatic),
            mCaptureSessionListener(),
            mCaptureSessionCb(&mCaptureSessionListener, CaptureSessionListener::onClosed,
                              CaptureSessionListener::onReady, CaptureSessionListener::onActive),
            mCaptureResultListener(*this),
            mCaptureResultCb(&mCaptureResultListener, CaptureResultListener::onCaptureStart,
                             CaptureResultListener::onCaptureProgressed,
                             CaptureResultListener::onCaptureCompletedStatic,
                             CaptureResultListener::onCaptureFailedStatic,
                             CaptureResultListener::onCaptureSequenceCompleted,
                             CaptureResultListener::onCaptureSequenceAborted,
                             CaptureResultListener::onCaptureBufferLost) {}

    bool hasCamera() {
        ACameraIdList* cameraIdList = nullptr;
        auto rc = ACameraManager_getCameraIdList(mCameraManager.get(), &cameraIdList);
        if (rc != ACAMERA_OK) {
            ALOGE("Get camera id list failed: ret %d", rc);
            return false;
        }
        jboolean result = cameraIdList->numCameras > 0;
        mCameraIdList.reset(cameraIdList);
        return result;
    }

    int openCamera() {
        const char* cameraId = mCameraIdList->cameraIds[0];

        ACameraDevice* device = nullptr;
        auto rc = ACameraManager_openCamera(mCameraManager.get(), cameraId, &mCameraDeviceCb,
                                            &device);
        if (rc != ACAMERA_OK) {
            ALOGE("Open camera failed: ret %d", rc);
            return rc;
        }

        mCameraDevice.reset(device);

        return ACAMERA_OK;
    }

    int openCameraStream(bool shouldRepeat) {
        const char* cameraId = mCameraIdList->cameraIds[0];
        ACameraMetadata* chars = nullptr;

        auto rc = ACameraManager_getCameraCharacteristics(mCameraManager.get(), cameraId, &chars);
        if (rc != ACAMERA_OK || chars == nullptr) {
            ALOGE("Get camera %s characteristics failure. ret %d, chars %p", cameraId, rc, chars);
            return rc;
        }

        mCameraCharacteristics.reset(chars);
        int width = 0, height = 0;
        rc = getPreviewSize(width, height);
        if (rc != ACAMERA_OK) {
            return rc;
        }

        media_status_t mediaRc = createImageReader(width, height);
        if (mediaRc != AMEDIA_OK) {
            return mediaRc;
        }

        rc = createCaptureSessionOutputContainer();
        if (rc != ACAMERA_OK) {
            return rc;
        }

        rc = ACameraDevice_isSessionConfigurationSupported(mCameraDevice.get(),
                                                           mOutputContainer.get());
        if (rc != ACAMERA_OK) {
            ALOGE("isSessionConfigurationSupported returned %d, device %p outputContainer %p", rc,
                  mCameraDevice.get(), mOutputContainer.get());
            return rc;
        }

        ACameraCaptureSession* captureSession = nullptr;
        rc = ACameraDevice_createCaptureSessionWithSessionParameters(mCameraDevice.get(),
                                                                     mOutputContainer.get(),
                                                                     /*sessionParameters=*/nullptr,
                                                                     &mCaptureSessionCb,
                                                                     &captureSession);
        if (rc != ACAMERA_OK || captureSession == nullptr) {
            ALOGE("Create session for camera %s failed. ret %d session %p", cameraId, rc,
                  captureSession);
            if (rc == ACAMERA_OK) {
                rc = ACAMERA_ERROR_UNKNOWN; // ret OK but session is null
            }
            return rc;
        }

        mCaptureSession.reset(captureSession);

        rc = createCaptureRequest(shouldRepeat ? TEMPLATE_PREVIEW : TEMPLATE_STILL_CAPTURE);
        if (rc != ACAMERA_OK) {
            return rc;
        }

        ACaptureRequest* requests[] = {mCaptureRequest.get()};

        if (shouldRepeat) {
            rc = ACameraCaptureSession_setRepeatingRequest(mCaptureSession.get(), &mCaptureResultCb,
                                                           /*numRequests=*/1, requests,
                                                           /*captureSequenceId=*/nullptr);
            if (rc != ACAMERA_OK) {
                ALOGE("%s: Failed to setRepeatingRequest %d", __FUNCTION__, rc);
                return rc;
            }
        } else {
            rc = ACameraCaptureSession_capture(mCaptureSession.get(), &mCaptureResultCb,
                                               /*numRequests=*/1, requests,
                                               /*captureSequenceId=*/nullptr);
            if (rc != ACAMERA_OK) {
                ALOGE("%s: Failed to capture %d", __FUNCTION__, rc);
                return rc;
            }
        }

        {
            std::unique_lock<std::mutex> l(mMutex);
            if (!mFirstCaptureCompleted && !mStoppedRepeating) {
                ALOGV("%s: %p waiting for first capture", __FUNCTION__, this);
                auto timeout = std::chrono::seconds(10);
                if (!mStateChanged.wait_for(l, timeout, [this]() {
                        ALOGV("%s: wakeup mFirstCaptureCompleted %d mStoppedRepeating %d",
                              __FUNCTION__, mFirstCaptureCompleted, mStoppedRepeating);
                        return mFirstCaptureCompleted || mStoppedRepeating;
                    })) {
                    ALOGE("Timed out waiting for capture to complete.");
                    return ACAMERA_ERROR_UNKNOWN;
                }
            }

            if (mStoppedRepeating) {
                if (mErrorCode != 0) {
                    ALOGE("%s: Stopped repeating (error %d)", __FUNCTION__, mErrorCode);
                    return mErrorCode;
                } else {
                    ALOGE("%s: Stopped repeating for unknown reason", __FUNCTION__);
                    return ACAMERA_ERROR_UNKNOWN;
                }
            }
        }

        ALOGV("%s: exit", __FUNCTION__);
        return ACAMERA_OK;
    }

    int waitStopRepeating() {
        std::unique_lock<std::mutex> l(mMutex);
        if (!mStoppedRepeating) {
            auto timeout = std::chrono::seconds(30);
            if (!mStateChanged.wait_for(l, timeout, [this]() {
                    ALOGV("%s: wakeup %d", __FUNCTION__, mStoppedRepeating);
                    return mStoppedRepeating;
                })) {
                ALOGE("Timed out waiting for stream to stop.");
                return ACAMERA_ERROR_UNKNOWN;
            }
        }
        return mErrorCode;
    }

    int stopRepeating() {
        ALOGV("Enter %s", __FUNCTION__);
        int rc = ACAMERA_OK;
        {
            std::unique_lock<std::mutex> l(mMutex);
            ALOGV("%s: mStoppedRepeating %d", __FUNCTION__, mStoppedRepeating);
            if (!mStoppedRepeating) {
                mStoppedRepeating = true;
                rc = ACameraCaptureSession_stopRepeating(mCaptureSession.get());
            }
        }

        ALOGV("%s: notifying", __FUNCTION__);
        mStateChanged.notify_all();
        return rc;
    }

    void signalStoppedRepeating(int errorCode) {
        {
            std::lock_guard<std::mutex> l(mMutex);
            mStoppedRepeating = true;
            if (mErrorCode == 0) {
                mErrorCode = errorCode;
            }
        }

        ALOGV("%s: notifying", __FUNCTION__);
        mStateChanged.notify_all();
    }

    void onCaptureCompleted() {
        {
            std::lock_guard<std::mutex> l(mMutex);
            mFirstCaptureCompleted = true;
        }

        ALOGV("%s: %p notifying", __FUNCTION__, this);
        mStateChanged.notify_all();
    }

    void onCaptureFailed(ACameraCaptureFailure* failure) {
        if (failure == nullptr) {
            ALOGE("%s: null failure", __FUNCTION__);
            return;
        }
        ALOGV("%s: frameNumber %" PRId64 " reason %d sequenceId %d wasImageCaptured %d",
              __FUNCTION__, failure->frameNumber, failure->reason, failure->sequenceId,
              failure->wasImageCaptured);
    }

    static bool checkNullObj(void* obj, const char* func) {
        if (obj == nullptr) {
            ALOGE("%s: null obj", func);
            return true;
        }
        return false;
    }

    class CameraDeviceListener {
    public:
        CameraDeviceListener(CameraOpenerJni& parent) : mParent(parent) {}

        static void onDisconnectedStatic(void* obj, ACameraDevice* device) {
            if (checkNullObj(obj, __FUNCTION__)) return;
            reinterpret_cast<CameraDeviceListener*>(obj)->onDisconnected(device);
        }

        static void onErrorStatic(void* obj, ACameraDevice* device, int errorCode) {
            if (checkNullObj(obj, __FUNCTION__)) return;
            reinterpret_cast<CameraDeviceListener*>(obj)->onError(device, errorCode);
        }

        static void closeDevice(ACameraDevice* device) {
            auto rc = ACameraDevice_close(device);
            if (rc != ACAMERA_OK) {
                ALOGE("Close camera failed: ret %d", rc);
            }
        }

        void onDisconnected(ACameraDevice* /*device*/) {
            ALOGV("%s", __FUNCTION__);
            mParent.signalStoppedRepeating(0);
        }

        void onError(ACameraDevice* /*device*/, int errorCode) {
            ALOGV("%s: %d", __FUNCTION__, errorCode);
            mParent.signalStoppedRepeating(errorCode);
        }

    private:
        CameraOpenerJni& mParent;
    };

    class ImageReaderListener {
    public:
        static void signalImageStatic(void* obj, AImageReader* reader) {
            if (checkNullObj(obj, __FUNCTION__)) return;
            reinterpret_cast<ImageReaderListener*>(obj)->signalImage(reader);
        }

        void signalImage(AImageReader* reader) {
            AImage* img = nullptr;
            media_status_t rc = AImageReader_acquireNextImage(reader, &img);
            if (rc != AMEDIA_OK || img == nullptr) {
                ALOGE("%s: acquire image from reader %p failed! ret: %d, img %p", __FUNCTION__,
                      reader, rc, img);
                return;
            }
            AImage_delete(img);
        }
    };

    class CaptureSessionListener {
    public:
        static void onClosed([[maybe_unused]] void* obj, ACameraCaptureSession* session) {
            ALOGV("%s: %p", __FUNCTION__, session);
        }

        static void onReady([[maybe_unused]] void* obj, ACameraCaptureSession* session) {
            ALOGV("%s: %p", __FUNCTION__, session);
        }

        static void onActive([[maybe_unused]] void* obj, ACameraCaptureSession* session) {
            ALOGV("%s: %p", __FUNCTION__, session);
        }
    };

    class CaptureResultListener {
    public:
        CaptureResultListener(CameraOpenerJni& parent) : mParent(parent) {}

        static void onCaptureStart(void* /*obj*/, ACameraCaptureSession* /*session*/,
                                   const ACaptureRequest* /*request*/, int64_t /*timestamp*/) {
            ALOGV("%s", __FUNCTION__);
        }

        static void onCaptureProgressed(void* /*obj*/, ACameraCaptureSession* /*session*/,
                                        ACaptureRequest* /*request*/,
                                        const ACameraMetadata* /*result*/) {
            ALOGV("%s", __FUNCTION__);
        }

        static void onCaptureCompletedStatic(void* obj, ACameraCaptureSession* /*session*/,
                                             ACaptureRequest* /*request*/,
                                             const ACameraMetadata* /*result*/) {
            ALOGV("%s", __FUNCTION__);
            if (checkNullObj(obj, __FUNCTION__)) return;
            reinterpret_cast<CaptureResultListener*>(obj)->onCaptureCompleted();
        }

        static void onCaptureFailedStatic(void* obj, ACameraCaptureSession* /*session*/,
                                          ACaptureRequest* /*request*/,
                                          ACameraCaptureFailure* failure) {
            ALOGV("%s", __FUNCTION__);
            if (checkNullObj(obj, __FUNCTION__)) return;
            reinterpret_cast<CaptureResultListener*>(obj)->onCaptureFailed(failure);
        }

        static void onCaptureSequenceCompleted(void* /*obj*/, ACameraCaptureSession* /*session*/,
                                               int /*sequenceId*/, int64_t frameNumber) {
            ALOGV("%s: frameNumber %" PRId64, __FUNCTION__, frameNumber);
        }

        static void onCaptureSequenceAborted(void* /*obj*/, ACameraCaptureSession* /*session*/,
                                             int /*sequenceId*/) {
            ALOGV("%s", __FUNCTION__);
        }

        static void onCaptureBufferLost(void* /*obj*/, ACameraCaptureSession* /*session*/,
                                        ACaptureRequest* /*request*/, ANativeWindow* /*window*/,
                                        int64_t frameNumber) {
            ALOGV("%s: frameNumber %" PRId64, __FUNCTION__, frameNumber);
        }

        void onCaptureCompleted() { mParent.onCaptureCompleted(); }

        void onCaptureFailed(ACameraCaptureFailure* failure) { mParent.onCaptureFailed(failure); }

    private:
        CameraOpenerJni& mParent;
    };

private:
    camera_status_t getPreviewSize(int& w, int& h) {
        ACameraMetadata_const_entry entry;
        auto rc = ACameraMetadata_getConstEntry(mCameraCharacteristics.get(),
                                                ACAMERA_SCALER_AVAILABLE_STREAM_CONFIGURATIONS,
                                                &entry);
        if (rc != ACAMERA_OK) {
            ALOGE("Failed to get available stream configurations: %d", rc);
            return rc;
        }

        for (uint32_t i = 0; i < entry.count; i += 4) {
            if (entry.data.i32[i] == AIMAGE_FORMAT_JPEG &&
                entry.data.i32[i + 3] == ACAMERA_SCALER_AVAILABLE_STREAM_CONFIGURATIONS_OUTPUT) {
                w = entry.data.i32[i + 1];
                h = entry.data.i32[i + 2];
                return ACAMERA_OK;
            }
        }

        ALOGE("Could not find preview size for JPEG format");
        return ACAMERA_ERROR_METADATA_NOT_FOUND;
    }

    media_status_t createImageReader(int width, int height) {
        AImageReader* reader = nullptr;

        auto rc = AImageReader_new(width, height, AIMAGE_FORMAT_JPEG, /*maxImages=*/1, &reader);
        if (rc != AMEDIA_OK) {
            ALOGE("Failed to create image reader: %d", rc);
            return rc;
        }
        if (reader == nullptr) {
            ALOGE("Null image reader created.");
            return AMEDIA_ERROR_UNKNOWN;
        }

        mImgReader.reset(reader);
        rc = AImageReader_setImageListener(reader, &mImgReaderCb);
        if (rc != AMEDIA_OK) {
            ALOGE("Set AImageReader listener failed. ret %d", rc);
            return rc;
        }

        rc = AImageReader_getWindow(reader, &mImgReaderAnw);
        if (rc != AMEDIA_OK) {
            ALOGE("AImageReader_getWindow failed. ret %d", rc);
            return rc;
        }
        if (mImgReaderAnw == nullptr) {
            ALOGE("Null ANW from AImageReader!");
            return AMEDIA_ERROR_UNKNOWN;
        }

        return AMEDIA_OK;
    }

    camera_status_t createCaptureSessionOutputContainer() {
        ACaptureSessionOutputContainer* outputContainer = nullptr;
        auto rc = ACaptureSessionOutputContainer_create(&outputContainer);
        if (rc != ACAMERA_OK) {
            ALOGE("Create capture session output container failed. ret %d", rc);
            return rc;
        }

        mOutputContainer.reset(outputContainer);

        ACaptureSessionOutput* output = nullptr;
        rc = ACaptureSessionOutput_create(mImgReaderAnw, &output);
        if (rc != ACAMERA_OK || output == nullptr) {
            ALOGE("Session image reader output create fail! ret %d output %p", rc, output);
            if (rc == ACAMERA_OK) {
                rc = ACAMERA_ERROR_UNKNOWN; // ret OK but output is null
            }
            return rc;
        }

        mOutput.reset(output);

        rc = ACaptureSessionOutputContainer_add(outputContainer, output);
        if (rc != ACAMERA_OK) {
            ALOGE("Session image reader output add failed! ret %d", rc);
            return rc;
        }

        return ACAMERA_OK;
    }

    camera_status_t createCaptureRequest(ACameraDevice_request_template requestTemplate) {
        ACaptureRequest* request = nullptr;
        auto rc =
                ACameraDevice_createCaptureRequest(mCameraDevice.get(), requestTemplate, &request);
        if (rc != ACAMERA_OK) {
            ALOGE("Create capture request failed. ret %d", rc);
            return rc;
        }

        mCaptureRequest.reset(request);

        ACameraOutputTarget* outputTarget = nullptr;
        rc = ACameraOutputTarget_create(mImgReaderAnw, &outputTarget);
        if (rc != ACAMERA_OK) {
            ALOGE("Create request reader output target failed. ret %d", rc);
            return rc;
        }

        mOutputTarget.reset(outputTarget);

        rc = ACaptureRequest_addTarget(request, outputTarget);
        if (rc != ACAMERA_OK) {
            ALOGE("Add capture request output failed. ret %d", rc);
            return rc;
        }

        return ACAMERA_OK;
    }

    Auto<ACameraManager, ACameraManager_delete> mCameraManager;
    Auto<ACameraIdList, ACameraManager_deleteCameraIdList> mCameraIdList;
    CameraDeviceListener mCameraDeviceListener;
    ACameraDevice_StateCallbacks mCameraDeviceCb;
    Auto<ACameraDevice, CameraDeviceListener::closeDevice> mCameraDevice;
    Auto<ACameraMetadata, ACameraMetadata_free> mCameraCharacteristics;
    Auto<AImageReader, AImageReader_delete> mImgReader;
    ANativeWindow* mImgReaderAnw;
    ImageReaderListener mImgReaderListener;
    AImageReader_ImageListener mImgReaderCb;
    Auto<ACaptureSessionOutputContainer, ACaptureSessionOutputContainer_free> mOutputContainer;
    Auto<ACaptureSessionOutput, ACaptureSessionOutput_free> mOutput;
    Auto<ACameraCaptureSession, ACameraCaptureSession_close> mCaptureSession;
    CaptureSessionListener mCaptureSessionListener;
    ACameraCaptureSession_stateCallbacks mCaptureSessionCb;
    Auto<ACaptureRequest, ACaptureRequest_free> mCaptureRequest;
    Auto<ACameraOutputTarget, ACameraOutputTarget_free> mOutputTarget;
    CaptureResultListener mCaptureResultListener;
    ACameraCaptureSession_captureCallbacks mCaptureResultCb;
    std::mutex mMutex;
    std::condition_variable mStateChanged;
    bool mFirstCaptureCompleted = false;
    bool mStoppedRepeating = false;
    int mErrorCode = 0;
};

static std::unique_ptr<CameraOpenerJni> sThis = nullptr;

static bool checkNullThis(const char* func) {
    if (sThis == nullptr) {
        ALOGE("%s: sThis is null", func);
        return true;
    }
    return false;
}

} // namespace

extern "C" {

void Java_android_security_cts_camera_open_CameraOpener_nativeInit() {
    ALOGV("Enter %s", __FUNCTION__);
    sThis = std::make_unique<CameraOpenerJni>();
}

void Java_android_security_cts_camera_open_CameraOpener_nativeCleanup() {
    ALOGV("Enter %s", __FUNCTION__);
    sThis = nullptr;
}

jboolean Java_android_security_cts_camera_open_CameraOpener_nativeHasCamera() {
    if (checkNullThis(__FUNCTION__)) return false;
    return sThis->hasCamera();
}

jint Java_android_security_cts_camera_open_CameraOpener_nativeOpenCamera() {
    if (checkNullThis(__FUNCTION__)) return ACAMERA_ERROR_UNKNOWN;
    return sThis->openCamera();
}

jint Java_android_security_cts_camera_open_CameraOpener_nativeOpenCameraStream(
        jboolean shouldRepeat) {
    if (checkNullThis(__FUNCTION__)) return ACAMERA_ERROR_UNKNOWN;
    return sThis->openCameraStream(shouldRepeat);
}

jint Java_android_security_cts_camera_open_CameraOpener_nativeWaitStopRepeating() {
    if (checkNullThis(__FUNCTION__)) return ACAMERA_ERROR_UNKNOWN;
    return sThis->waitStopRepeating();
}

jint Java_android_security_cts_camera_open_CameraOpener_nativeStopRepeating() {
    if (checkNullThis(__FUNCTION__)) return ACAMERA_ERROR_UNKNOWN;
    return sThis->stopRepeating();
}
}
