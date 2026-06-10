/*
 * Copyright 2023 The Android Open Source Project
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
import static android.graphics.ImageFormat.YUV_420_888;
import static android.hardware.camera2.CameraMetadata.LENS_FACING_EXTERNAL;
import static android.hardware.camera2.CameraMetadata.LENS_FACING_FRONT;
import static android.virtualdevice.cts.camera.util.VirtualCameraUtils.assertVirtualCameraConfig;
import static android.virtualdevice.cts.camera.util.VirtualCameraUtils.createVirtualCameraConfig;
import static android.virtualdevice.cts.camera.util.VirtualCameraUtils.getMaximumTextureSize;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static org.junit.Assert.assertThrows;

import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.camera.VirtualCameraCallback;
import android.companion.virtual.camera.VirtualCameraConfig;
import android.os.Parcel;
import android.os.ServiceSpecificException;
import android.platform.test.annotations.AppModeFull;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.Executor;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class VirtualCameraConfigTest {

    private static final String CAMERA_NAME = "Virtual Camera";
    private static final int CAMERA_WIDTH = 640;
    private static final int CAMERA_HEIGHT = 480;
    private static final int CAMERA_FORMAT = YUV_420_888;
    private static final int CAMERA_MAX_FPS = 30;
    private static final int CAMERA_SENSOR_ORIENTATION = SENSOR_ORIENTATION_0;
    private static final int CAMERA_LENS_FACING = LENS_FACING_FRONT;

    @Rule public VirtualDeviceRule mRule = VirtualDeviceRule.createDefault();

    @Mock
    private VirtualCameraCallback mCallback;

    private VirtualDevice mVirtualDevice;
    private int mMaximumTextureSize;

    private final Executor mExecutor = getApplicationContext().getMainExecutor();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mVirtualDevice = mRule.createManagedVirtualDevice(
                new VirtualDeviceParams.Builder()
                        .setDevicePolicy(POLICY_TYPE_CAMERA, DEVICE_POLICY_CUSTOM)
                        .build());
        mMaximumTextureSize = getMaximumTextureSize();
    }

    @Test
    public void virtualCameraConfigBuilder_buildsCorrectConfig() {
        VirtualCameraConfig config = new VirtualCameraConfig.Builder(CAMERA_NAME)
                .addStreamConfig(CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_FORMAT, CAMERA_MAX_FPS)
                .setVirtualCameraCallback(mExecutor, mCallback)
                .setSensorOrientation(CAMERA_SENSOR_ORIENTATION)
                .setLensFacing(CAMERA_LENS_FACING)
                .build();

        assertVirtualCameraConfig(config, CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_FORMAT,
                CAMERA_MAX_FPS, CAMERA_SENSOR_ORIENTATION, CAMERA_LENS_FACING, CAMERA_NAME);
    }

    @Test
    public void virtualCameraConfigBuilder_tooSmallWidth_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualCameraConfig.Builder(CAMERA_NAME)
                        .addStreamConfig(0 /* width */, CAMERA_HEIGHT, CAMERA_FORMAT,
                                CAMERA_MAX_FPS)
                        .setVirtualCameraCallback(mExecutor, mCallback)
                        .setLensFacing(CAMERA_LENS_FACING)
                        .build());
    }

    @Test
    public void virtualCameraConfig_largestWidth_succeeds() throws Exception {
        mVirtualDevice.createVirtualCamera(
            new VirtualCameraConfig.Builder(CAMERA_NAME)
                    .addStreamConfig(mMaximumTextureSize, CAMERA_HEIGHT, CAMERA_FORMAT,
                    CAMERA_MAX_FPS)
                    .setVirtualCameraCallback(mExecutor, mCallback)
                    .setLensFacing(CAMERA_LENS_FACING)
                    .build());
    }

    @Test
    public void virtualCameraConfig_tooLargeWidth_throwsException() throws Exception {
        assertThrows(ServiceSpecificException.class,
                () -> mVirtualDevice.createVirtualCamera(
                        new VirtualCameraConfig.Builder(CAMERA_NAME)
                                .addStreamConfig(mMaximumTextureSize + 1, CAMERA_HEIGHT,
                                        CAMERA_FORMAT, CAMERA_MAX_FPS)
                                .setVirtualCameraCallback(mExecutor, mCallback)
                                .setLensFacing(CAMERA_LENS_FACING)
                                .build()));
    }

    @Test
    public void virtualCameraConfigBuilder_tooSmallHeight_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualCameraConfig.Builder(CAMERA_NAME)
                        .addStreamConfig(CAMERA_WIDTH, 0 /* height */, CAMERA_FORMAT,
                                CAMERA_MAX_FPS)
                        .setVirtualCameraCallback(mExecutor, mCallback)
                        .setLensFacing(CAMERA_LENS_FACING)
                        .build());
    }

    @Test
    public void virtualCameraConfig_largestHeight_succeeds() throws Exception {
        mVirtualDevice.createVirtualCamera(
                new VirtualCameraConfig.Builder(CAMERA_NAME)
                        .addStreamConfig(CAMERA_WIDTH, mMaximumTextureSize, CAMERA_FORMAT,
                                CAMERA_MAX_FPS)
                        .setVirtualCameraCallback(mExecutor, mCallback)
                        .setLensFacing(CAMERA_LENS_FACING)
                        .build());
    }

    @Test
    public void virtualCameraConfig_tooLargeHeight_throwsException() throws Exception {
        assertThrows(ServiceSpecificException.class,
                () -> mVirtualDevice.createVirtualCamera(
                        new VirtualCameraConfig.Builder(CAMERA_NAME)
                                .addStreamConfig(CAMERA_WIDTH, mMaximumTextureSize + 1,
                                        CAMERA_FORMAT, CAMERA_MAX_FPS)
                                .setVirtualCameraCallback(mExecutor, mCallback)
                                .setLensFacing(CAMERA_LENS_FACING)
                                .build()));
    }

    @Test
    public void virtualCameraConfigBuilder_invalidFormat_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualCameraConfig.Builder(CAMERA_NAME)
                        .addStreamConfig(CAMERA_WIDTH, CAMERA_HEIGHT, -1 /* format */,
                                CAMERA_MAX_FPS)
                        .setVirtualCameraCallback(mExecutor, mCallback)
                        .setLensFacing(CAMERA_LENS_FACING)
                        .build());
    }

    @Test
    public void virtualCameraConfigBuilder_tooLowMaximumFramesPerSecond_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualCameraConfig.Builder(CAMERA_NAME)
                        .addStreamConfig(CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_FORMAT,
                                0 /* maximumFramesPerSecond */)
                        .setVirtualCameraCallback(mExecutor, mCallback)
                        .setLensFacing(CAMERA_LENS_FACING)
                        .build());
    }

    @Test
    public void virtualCameraConfigBuilder_tooHighMaximumFramesPerSecond_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualCameraConfig.Builder(CAMERA_NAME)
                        .addStreamConfig(CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_FORMAT,
                                100 /* maximumFramesPerSecond */)
                        .setVirtualCameraCallback(mExecutor, mCallback)
                        .setLensFacing(CAMERA_LENS_FACING)
                        .build());
    }

    @Test
    public void virtualCameraConfigBuilder_nullName_throwsException() {
        assertThrows(NullPointerException.class,
                () -> new VirtualCameraConfig.Builder(null /* name */)
                        .addStreamConfig(CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_FORMAT, CAMERA_MAX_FPS)
                        .setVirtualCameraCallback(mExecutor, mCallback)
                        .setLensFacing(CAMERA_LENS_FACING)
                        .build());
    }

    @Test
    public void virtualCameraConfigBuilder_nullCallback_throwsException() {
        assertThrows(NullPointerException.class,
                () -> new VirtualCameraConfig.Builder(CAMERA_NAME)
                        .addStreamConfig(CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_FORMAT, CAMERA_MAX_FPS)
                        .setVirtualCameraCallback(mExecutor, null /* callback */)
                        .setLensFacing(CAMERA_LENS_FACING)
                        .build());
    }

    @Test
    public void virtualCameraConfigBuilder_nullExecutor_throwsException() {
        assertThrows(NullPointerException.class,
                () -> new VirtualCameraConfig.Builder(CAMERA_NAME)
                        .addStreamConfig(CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_FORMAT, CAMERA_MAX_FPS)
                        .setVirtualCameraCallback(null /* executor */, mCallback)
                        .setLensFacing(CAMERA_LENS_FACING)
                        .build());
    }

    @Test
    public void virtualCameraConfigBuilder_missingLensFacing_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualCameraConfig.Builder(CAMERA_NAME)
                        .addStreamConfig(CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_FORMAT,
                                CAMERA_MAX_FPS)
                        .setVirtualCameraCallback(mExecutor, mCallback)
                        .build());
    }

    @Test
    public void virtualCameraConfigBuilder_unsupportedLensFacing_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualCameraConfig.Builder(CAMERA_NAME)
                        .addStreamConfig(CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_FORMAT,
                                CAMERA_MAX_FPS)
                        .setLensFacing(LENS_FACING_EXTERNAL)
                        .setVirtualCameraCallback(mExecutor, mCallback)
                        .build());
    }

    @Test
    public void parcelAndUnparcel_matches() {
        VirtualCameraConfig original = createVirtualCameraConfig(CAMERA_WIDTH, CAMERA_HEIGHT,
                CAMERA_FORMAT, CAMERA_MAX_FPS, CAMERA_SENSOR_ORIENTATION, CAMERA_LENS_FACING,
                CAMERA_NAME, mExecutor, mCallback);

        final Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0 /* flags */);
        parcel.setDataPosition(0);
        final VirtualCameraConfig recreated =
                VirtualCameraConfig.CREATOR.createFromParcel(parcel);

        assertVirtualCameraConfig(recreated, CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_FORMAT,
                CAMERA_MAX_FPS, CAMERA_SENSOR_ORIENTATION, CAMERA_LENS_FACING, CAMERA_NAME);
    }
}
