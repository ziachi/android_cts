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

package android.virtualdevice.cts.camera;

import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_CUSTOM;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_CAMERA;
import static android.companion.virtual.camera.VirtualCameraConfig.SENSOR_ORIENTATION_0;
import static android.hardware.camera2.CameraMetadata.LENS_FACING_FRONT;
import static android.virtualdevice.cts.camera.util.VirtualCameraUtils.FRONT_CAMERA_ID;
import static android.virtualdevice.cts.camera.util.VirtualCameraUtils.createVirtualCameraConfig;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeNoException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.camera.VirtualCamera;
import android.companion.virtual.camera.VirtualCameraCallback;
import android.companion.virtual.camera.VirtualCameraConfig;
import android.content.Context;
import android.graphics.PixelFormat;
import android.hardware.camera2.CameraManager;
import android.hardware.display.VirtualDisplay;
import android.platform.test.annotations.AppModeFull;
import android.view.Display;
import android.virtualdevice.cts.camera.util.NativeCameraManager;
import android.virtualdevice.cts.camera.util.NativeCameraTestActivity;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.common.collect.Iterables;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
@RunWith(AndroidJUnit4.class)
public class VirtualCameraNdkTest {

    private static final long TIMEOUT_MILLIS = 2000L;
    private static final String CAMERA_NAME = "Virtual camera";
    private static final int CAMERA_WIDTH = 640;
    private static final int CAMERA_HEIGHT = 480;
    private static final int CAMERA_INPUT_FORMAT = PixelFormat.RGBA_8888;
    private static final int CAMERA_MAX_FPS = 30;

    @Rule public VirtualDeviceRule mRule = VirtualDeviceRule.createDefault();

    @Mock
    private VirtualCameraCallback mVirtualCameraCallback;

    @Mock
    private NativeCameraManager.AvailabilityCallback mMockAvailabilityCallback;

    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private final Executor mExecutor = mContext.getMainExecutor();
    private VirtualDeviceManager.VirtualDevice mVirtualDevice;
    private VirtualDisplay mVirtualDisplay;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mVirtualDevice = mRule.createManagedVirtualDevice(
                new VirtualDeviceParams.Builder()
                        .setDevicePolicy(POLICY_TYPE_CAMERA, DEVICE_POLICY_CUSTOM)
                        .build());
        mVirtualDisplay = mRule.createManagedVirtualDisplay(mVirtualDevice,
                VirtualDeviceRule.createTrustedVirtualDisplayConfigBuilder());
    }

    @Test
    public void getCameraIds_onDefaultDevice_returnsDefaultCameras() throws Exception {
        CameraManager cameraManager = mContext.getSystemService(CameraManager.class);

        String[] defaultDeviceCameras = cameraManager.getCameraIdListNoLazy();

        try (VirtualCamera ignored = createVirtualCamera()) {
            NativeCameraTestActivity activity = mRule.startActivityOnDisplaySync(
                    Display.DEFAULT_DISPLAY, NativeCameraTestActivity.class);
            NativeCameraManager nativeCameraManager = activity.getNativeCameraManager();
            List<String> ndkCameras = Arrays.asList(nativeCameraManager.getCameraIds());

            assertThat(ndkCameras).containsExactlyElementsIn(defaultDeviceCameras);
            for (String cameraId : ndkCameras) {
                assertThat(nativeCameraManager.getDeviceId(cameraId)).isEqualTo(
                        Context.DEVICE_ID_DEFAULT);
            }
        }
    }

    @Test
    public void getCameraIds_onVirtualDevice_returnsOnlyVirtualCamera() throws Exception {
        CameraManager cameraManager = mContext
                .createDeviceContext(mVirtualDevice.getDeviceId())
                .getSystemService(CameraManager.class);

        try (VirtualCamera ignored = createVirtualCamera()) {
            NativeCameraTestActivity activity = mRule.startActivityOnDisplaySync(
                    mVirtualDisplay, NativeCameraTestActivity.class);

            NativeCameraManager nativeCameraManager = activity.getNativeCameraManager();
            List<String> ndkCameras = Arrays.asList(nativeCameraManager.getCameraIds());

            assertThat(ndkCameras).containsExactly(FRONT_CAMERA_ID);
            assertThat(ndkCameras).containsExactlyElementsIn(cameraManager.getCameraIdListNoLazy());
            assertThat(nativeCameraManager.getDeviceId(
                    Iterables.getOnlyElement(ndkCameras))).isEqualTo(mVirtualDevice.getDeviceId());
        }
    }

    @Test
    public void availabilityCallbacks_onVirtualDevice_triggeredOnlyForVirtualCamera() {
        NativeCameraTestActivity activity = mRule.startActivityOnDisplaySync(
                mVirtualDisplay, NativeCameraTestActivity.class);
        NativeCameraManager nativeCameraManager = activity.getNativeCameraManager();
        nativeCameraManager.registerAvailabilityCallback(mMockAvailabilityCallback);

        try (VirtualCamera ignored = createVirtualCamera()) {
            verify(mMockAvailabilityCallback, timeout(TIMEOUT_MILLIS))
                    .onCameraAvailable(FRONT_CAMERA_ID);
        }
        verify(mMockAvailabilityCallback, timeout(TIMEOUT_MILLIS))
                .onCameraUnavailable(FRONT_CAMERA_ID);

        verifyNoMoreInteractions(mMockAvailabilityCallback);
    }

    @Test
    public void availabilityCallbacks_onDefaultDevice_triggeredOnlyForDefaultCameras() throws Exception {
        String[] defaultCameraIds = mContext.getSystemService(
                CameraManager.class).getCameraIdListNoLazy();
        NativeCameraTestActivity activity = mRule.startActivityOnDisplaySync(
                Display.DEFAULT_DISPLAY, NativeCameraTestActivity.class);
        NativeCameraManager nativeCameraManager = activity.getNativeCameraManager();
        nativeCameraManager.registerAvailabilityCallback(mMockAvailabilityCallback);

        // We expect callbacks to be invoked for default cameras right after registration.
        for (String cameraId : defaultCameraIds) {
            verify(mMockAvailabilityCallback, timeout(TIMEOUT_MILLIS)).onCameraAvailable(cameraId);
        }
        clearInvocations(mMockAvailabilityCallback);

        try (VirtualCamera ignored = createVirtualCamera()) {
            verify(mMockAvailabilityCallback, after(TIMEOUT_MILLIS).never()).onCameraAvailable(
                    anyString());
        }
        verify(mMockAvailabilityCallback, after(TIMEOUT_MILLIS).never()).onCameraUnavailable(
                anyString());
    }

    private VirtualCamera createVirtualCamera() {
        VirtualCameraConfig config = createVirtualCameraConfig(CAMERA_WIDTH, CAMERA_HEIGHT,
                CAMERA_INPUT_FORMAT, CAMERA_MAX_FPS, SENSOR_ORIENTATION_0, LENS_FACING_FRONT,
                CAMERA_NAME, mExecutor, mVirtualCameraCallback);
        try {
            return mVirtualDevice.createVirtualCamera(config);
        } catch (UnsupportedOperationException e) {
            assumeNoException("Virtual camera is not available on this device", e);
        }

        // Never happens.
        return null;
    }
}
