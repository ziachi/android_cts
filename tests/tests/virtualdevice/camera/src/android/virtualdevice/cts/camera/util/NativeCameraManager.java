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

package android.virtualdevice.cts.camera.util;

import androidx.annotation.NonNull;

import java.util.Objects;

public class NativeCameraManager {
    static {
        System.loadLibrary("virtualcameratest-jni");
    }
    private final long mNativeCameraManagerPtr;

    public NativeCameraManager() {
        mNativeCameraManagerPtr = createCameraManager();
    }

    @Override
    protected void finalize() {
        releaseCameraManager(mNativeCameraManagerPtr);
    }

    /**
     * Wraps ACameraManager_getCameraIdList NDK call.
     *
     * @return camera ids returned ACameraManager_getCameraIdList NDK call.
     */
    public String[] getCameraIds() {
        return getCameraIds(mNativeCameraManagerPtr);
    }

    /**
     * Calls ACameraManager_getCameraCharacteristics and returns value corresponding
     * to INFO_DEVICE_ID key from camera characteristics.
     *
     * @param cameraId - camera id to fetch device id for.
     * @return device id associated with the camera.
     */
    public int getDeviceId(String cameraId) {
        return getDeviceId(mNativeCameraManagerPtr, Objects.requireNonNull(cameraId));
    }

    /**
     * Corresponds to ACameraManager_registerAvailabilityCallbacks NDK call.
     *
     * @param callback implementation of {@code AvailabilityCallback} called when new camera becomes
     *                 available / unavailable.
     */
    public void registerAvailabilityCallback(@NonNull AvailabilityCallback callback) {
        registerAvailabilityCallback(mNativeCameraManagerPtr, Objects.requireNonNull(callback));
    }

    public interface AvailabilityCallback {
        /**
         * Called when there's new camera available.
         * @param cameraId - camera id of newly available camera.
         */
        void onCameraAvailable(String cameraId);

        /**
         * Called when camera becomes unavailable.
         * @param cameraId - camera id of newly unavailable camera.
         */
        void onCameraUnavailable(String cameraId);
    }

    private static native long createCameraManager();
    private static native void releaseCameraManager(long ptr);
    private static native String[] getCameraIds(long ptr);
    private static native int getDeviceId(long ptr, String cameraId);
    private static native void registerAvailabilityCallback(
            long ptr, AvailabilityCallback callback);
}
