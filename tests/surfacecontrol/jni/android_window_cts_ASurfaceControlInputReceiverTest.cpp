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

#include <memory>
#include <utility>
#define LOG_TAG "ASurfaceControlInputReceiverTest"

#include <android/choreographer.h>
#include <android/input.h>
#include <android/input_transfer_token_jni.h>
#include <android/log.h>
#include <android/looper.h>
#include <android/surface_control.h>
#include <android/surface_control_input_receiver.h>
#include <android/surface_control_jni.h>
#include <jni.h>
#include <nativehelper/JNIHelp.h>
#include <nativehelper/scoped_local_ref.h>

#define ALOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

namespace {
static struct {
    jmethodID onMotionEvent;
    jmethodID onKeyEvent;
} gInputReceiver;

class InputReceiverCallbackWrapper {
public:
    explicit InputReceiverCallbackWrapper(JNIEnv* env, jobject object) {
        env->GetJavaVM(&mVm);
        mInputReceiverObj = env->NewGlobalRef(object);
        mAInputReceiverCallbacks = AInputReceiverCallbacks_create(this);
        AInputReceiverCallbacks_setKeyEventCallback(mAInputReceiverCallbacks, onKeyEventThunk);
        AInputReceiverCallbacks_setMotionEventCallback(mAInputReceiverCallbacks,
                                                       onMotionEventThunk);
    }

    bool onKeyEvent(AInputEvent* inputEvent) {
        JNIEnv* env = getenv();
        ScopedLocalRef<jobject> event(env, AInputEvent_toJava(env, inputEvent));
        AInputEvent_release(inputEvent);
        return env->CallBooleanMethod(mInputReceiverObj, gInputReceiver.onKeyEvent, event.get());
    }

    bool onMotionEvent(AInputEvent* inputEvent) {
        JNIEnv* env = getenv();
        ScopedLocalRef<jobject> event(env, AInputEvent_toJava(env, inputEvent));
        AInputEvent_release(inputEvent);
        return env->CallBooleanMethod(mInputReceiverObj, gInputReceiver.onMotionEvent, event.get());
    }

    static bool onKeyEventThunk(void* context, AInputEvent* inputEvent) {
        InputReceiverCallbackWrapper* listener =
                reinterpret_cast<InputReceiverCallbackWrapper*>(context);
        return listener->onKeyEvent(inputEvent);
    }

    static bool onMotionEventThunk(void* context, AInputEvent* inputEvent) {
        InputReceiverCallbackWrapper* listener =
                reinterpret_cast<InputReceiverCallbackWrapper*>(context);
        return listener->onMotionEvent(inputEvent);
    }

    AInputReceiverCallbacks* getAInputReceiverCallbacks() { return mAInputReceiverCallbacks; }

    ~InputReceiverCallbackWrapper() {
        getenv()->DeleteGlobalRef(mInputReceiverObj);
        AInputReceiverCallbacks_release(mAInputReceiverCallbacks);
    }

private:
    JNIEnv* getenv() {
        JNIEnv* env;
        mVm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
        return env;
    }

    jobject mInputReceiverObj;
    JavaVM* mVm;
    AInputReceiverCallbacks* mAInputReceiverCallbacks;
};

class InputReceiverWrapper {
public:
    InputReceiverWrapper(AInputReceiver* aInputReceiver,
                         std::unique_ptr<InputReceiverCallbackWrapper> callback)
          : mInputReceiver(aInputReceiver), mInputReceiverCallbackWrapper(std::move(callback)) {}

    ~InputReceiverWrapper() { AInputReceiver_release(mInputReceiver); }
    AInputReceiver* mInputReceiver;

private:
    std::unique_ptr<InputReceiverCallbackWrapper> mInputReceiverCallbackWrapper;
};

static jlong nativeCreateInputReceiver(JNIEnv* env, jclass, jboolean batched,
                                       jobject hostInputTransferTokenObj, jlong surfaceControlObj,
                                       jobject inputReceiverObj) {
    AInputTransferToken* hostTransferToken =
            AInputTransferToken_fromJava(env, hostInputTransferTokenObj);
    ASurfaceControl* aSurfaceControl = reinterpret_cast<ASurfaceControl*>(surfaceControlObj);

    std::unique_ptr<InputReceiverCallbackWrapper> callbackWrapper =
            std::make_unique<InputReceiverCallbackWrapper>(env, inputReceiverObj);

    AInputReceiver* receiver;
    ALooper* looper = ALooper_prepare(0);

    if (batched) {
        AChoreographer* choreographer = AChoreographer_getInstance();
        receiver =
                AInputReceiver_createBatchedInputReceiver(choreographer, hostTransferToken,
                                                          aSurfaceControl,
                                                          callbackWrapper
                                                                  ->getAInputReceiverCallbacks());
    } else {
        receiver =
                AInputReceiver_createUnbatchedInputReceiver(looper, hostTransferToken,
                                                            aSurfaceControl,
                                                            callbackWrapper
                                                                    ->getAInputReceiverCallbacks());
    }

    InputReceiverWrapper* inputReceiverWrapper =
            new InputReceiverWrapper(receiver, std::move(callbackWrapper));
    return reinterpret_cast<jlong>(inputReceiverWrapper);
}

static void nativeDeleteInputReceiver(JNIEnv*, jclass, jlong receiverObj) {
    InputReceiverWrapper* receiver = reinterpret_cast<InputReceiverWrapper*>(receiverObj);
    delete receiver;
}

static jobject nativeGetInputTransferToken(JNIEnv* env, jclass, jlong receiverObj) {
    InputReceiverWrapper* receiver = reinterpret_cast<InputReceiverWrapper*>(receiverObj);
    return AInputTransferToken_toJava(env,
                                      AInputReceiver_getInputTransferToken(
                                              receiver->mInputReceiver));
}

static const JNINativeMethod sMethods[] = {
        // clang-format off
         {"nCreateInputReceiver",
          "(ZLandroid/window/InputTransferToken;JLandroid/view/cts/util/ASurfaceControlInputReceiverTestUtils$InputReceiver;)J",
          (void *) nativeCreateInputReceiver},
         {"nDeleteInputReceiver", "(J)V", (void *) nativeDeleteInputReceiver},
         {"nGetInputTransferToken", "(J)Landroid/window/InputTransferToken;", (void*) nativeGetInputTransferToken},
        // clang-format on
};

} // anonymous namespace

jint register_android_window_cts_ASurfaceControlInputReceiverTest(JNIEnv* env) {
    jclass clazz = env->FindClass("android/view/cts/util/ASurfaceControlInputReceiverTestUtils");
    int err = env->RegisterNatives(clazz, sMethods, NELEM(sMethods));

    jclass inputReceiver = env->FindClass(
            "android/view/cts/util/ASurfaceControlInputReceiverTestUtils$InputReceiver");
    gInputReceiver.onMotionEvent =
            env->GetMethodID(inputReceiver, "onMotionEvent", "(Landroid/view/MotionEvent;)Z");
    gInputReceiver.onKeyEvent =
            env->GetMethodID(inputReceiver, "onKeyEvent", "(Landroid/view/KeyEvent;)Z");
    return err;
}
