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
 */

package android.virtualdevice.cts.defaultcameraaccesstestbase;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeNotNull;

import android.app.Activity;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.ConditionVariable;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Rule;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public abstract class DefaultCameraAccessTestBase {

    private static final long TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(3);
    private static final int NO_ERROR = 0;

    @Rule
    public VirtualDeviceRule virtualDeviceRule = VirtualDeviceRule.createDefault();

    protected void verifyCameraAccessAllowed(VirtualDevice virtualDevice) throws Exception {
        Activity activity = setupWithVirtualDevice(virtualDevice);
        String[] cameras = getCameraIds();
        for (String cameraId : cameras) {
            assertThat(accessCameraFromActivity(activity, cameraId)).isEqualTo(NO_ERROR);
        }
    }

    protected void verifyCameraAccessBlocked(VirtualDevice virtualDevice) throws Exception {
        Activity activity = setupWithVirtualDevice(virtualDevice);
        String[] cameras = getCameraIds();
        for (String cameraId : cameras) {
            assertThat(accessCameraFromActivity(activity, cameraId)).isGreaterThan(NO_ERROR);
        }
    }

    private Activity setupWithVirtualDevice(VirtualDevice virtualDevice) {
        VirtualDisplay virtualDisplay = virtualDeviceRule.createManagedVirtualDisplayWithFlags(
                virtualDevice,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);
        return virtualDeviceRule.startActivityOnDisplaySync(
                virtualDisplay, Activity.class);
    }

    private String[] getCameraIds() throws Exception {
        CameraManager manager = getApplicationContext().getSystemService(CameraManager.class);
        String[] cameras = manager.getCameraIdList();
        assumeNotNull((Object) cameras);
        return cameras;
    }

    private int accessCameraFromActivity(Activity activity, String cameraId) {
        ConditionVariable cond = new ConditionVariable();
        final CameraManager cameraManager = activity.getSystemService(CameraManager.class);
        final int[] cameraError = {NO_ERROR};
        final List<CameraDevice> cameraDevices = new ArrayList<>();
        final CameraDevice.StateCallback cameraCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice cameraDevice) {
                cameraDevices.add(cameraDevice);
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                cameraError[0] = CameraDevice.StateCallback.ERROR_CAMERA_DISABLED;
                cond.open();
            }

            @Override
            public void onError(@NonNull CameraDevice cameraDevice, int error) {
                cameraError[0] = error;
                cond.open();
            }
        };

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            try {
                cameraManager.openCamera(cameraId, cameraCallback, null);
            } catch (CameraAccessException e) {
                cameraError[0] = CameraDevice.StateCallback.ERROR_CAMERA_DISABLED;
            }
        });
        cond.block(TIMEOUT_MILLIS);
        for (CameraDevice device : cameraDevices) {
            device.close();
        }
        return cameraError[0];
    }
}
