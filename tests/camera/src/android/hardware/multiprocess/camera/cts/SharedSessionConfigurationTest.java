/*
 * Copyright (C) 2025 The Android Open Source Project
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

package android.hardware.multiprocess.camera.cts;

import static junit.framework.Assert.*;

import android.graphics.ColorSpace;
import android.graphics.ImageFormat;
import android.hardware.DataSpace;
import android.hardware.HardwareBuffer;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SharedSessionConfiguration;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Size;

import com.android.internal.camera.flags.Flags;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;

@RunWith(JUnit4.class)
public class SharedSessionConfigurationTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAMERA_MULTI_CLIENT)
    public void testConstructorAndGetters_validInput() {
        int colorSpace = /* ColorSpace.Named.SRGB */ 0;
        long[] sharedOutputConfigs = {
            /* SURFACE_TYPE_SURFACE_VIEW */ 0,
            /* width */ 1920,
            /* height */ 1080,
            ImageFormat.YUV_420_888,
            OutputConfiguration.MIRROR_MODE_NONE,
            /* isReadOutTimestampEnabled */ 1,
            OutputConfiguration.TIMESTAMP_BASE_DEFAULT,
            DataSpace.DATASPACE_SRGB,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE | HardwareBuffer.USAGE_COMPOSER_OVERLAY,
            CameraMetadata.SCALER_AVAILABLE_STREAM_USE_CASES_PREVIEW,
            /* physicalCameraIdLen */ 1,
            /* physicalCameraId */ '1'
        };
        SharedSessionConfiguration config =
                new SharedSessionConfiguration(colorSpace, sharedOutputConfigs);

        assertEquals(ColorSpace.get(ColorSpace.Named.values()[colorSpace]), config.getColorSpace());
        List<SharedSessionConfiguration.SharedOutputConfiguration> outputs =
                config.getOutputStreamsInformation();
        assertEquals(1, outputs.size());
        assertEquals(sharedOutputConfigs[0], outputs.get(0).getSurfaceType());
        assertEquals(
                new Size((int) sharedOutputConfigs[1], (int) sharedOutputConfigs[2]),
                outputs.get(0).getSize());
        assertEquals(sharedOutputConfigs[3], outputs.get(0).getFormat());
        assertEquals(sharedOutputConfigs[4], outputs.get(0).getMirrorMode());
        assertEquals(sharedOutputConfigs[5] != 0, outputs.get(0).isReadoutTimestampEnabled());
        assertEquals(sharedOutputConfigs[6], outputs.get(0).getTimestampBase());
        assertEquals(sharedOutputConfigs[7], outputs.get(0).getDataspace());
        assertEquals(sharedOutputConfigs[8], outputs.get(0).getUsage());
        assertEquals(sharedOutputConfigs[9], outputs.get(0).getStreamUseCase());
        assertEquals(Character.toString((char) sharedOutputConfigs[11]),
                outputs.get(0).getPhysicalCameraId());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAMERA_MULTI_CLIENT)
    public void testConstructorAndGetters_multipleOutputs() {
        int colorSpace = /* ColorSpace.Named.ADOBE_RGB */ 10;
        long[] sharedOutputConfigs = {
            /* SURFACE_TYPE_SURFACE_TEXTURE */ 1,
            /* width */ 1280,
            /* height */ 720,
            ImageFormat.JPEG,
            OutputConfiguration.MIRROR_MODE_H,
            /* isReadOutTimestampEnabled */ 0,
            OutputConfiguration.TIMESTAMP_BASE_MONOTONIC,
            DataSpace.DATASPACE_UNKNOWN,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE,
            CameraMetadata.SCALER_AVAILABLE_STREAM_USE_CASES_STILL_CAPTURE,
            /* physicalCameraIdLen */ 1,
            /* physicalCameraId */ '2',
            /* SURFACE_TYPE_MEDIA_RECORDER */ 2,
            /* width */ 640,
            /* height */ 480,
            ImageFormat.PRIVATE,
            OutputConfiguration.MIRROR_MODE_V,
            /* isReadOutTimestampEnabled */ 1,
            OutputConfiguration.TIMESTAMP_BASE_REALTIME,
            DataSpace.DATASPACE_BT2020_PQ,
            HardwareBuffer.USAGE_VIDEO_ENCODE,
            CameraMetadata.SCALER_AVAILABLE_STREAM_USE_CASES_VIDEO_RECORD,
            /* physicalCameraIdLen */ 0
        };
        SharedSessionConfiguration config =
                new SharedSessionConfiguration(colorSpace, sharedOutputConfigs);

        assertEquals(ColorSpace.get(ColorSpace.Named.values()[colorSpace]), config.getColorSpace());
        List<SharedSessionConfiguration.SharedOutputConfiguration> outputs =
                config.getOutputStreamsInformation();
        assertEquals(2, outputs.size());

        assertEquals(sharedOutputConfigs[0], outputs.get(0).getSurfaceType());
        assertEquals(
                new Size((int) sharedOutputConfigs[1], (int) sharedOutputConfigs[2]),
                outputs.get(0).getSize());
        assertEquals(sharedOutputConfigs[3], outputs.get(0).getFormat());
        assertEquals(sharedOutputConfigs[4], outputs.get(0).getMirrorMode());
        assertEquals(sharedOutputConfigs[5] != 0, outputs.get(0).isReadoutTimestampEnabled());
        assertEquals(sharedOutputConfigs[6], outputs.get(0).getTimestampBase());
        assertEquals(sharedOutputConfigs[7], outputs.get(0).getDataspace());
        assertEquals(sharedOutputConfigs[8], outputs.get(0).getUsage());
        assertEquals(sharedOutputConfigs[9], outputs.get(0).getStreamUseCase());
        assertEquals(Character.toString((char) sharedOutputConfigs[11]),
                outputs.get(0).getPhysicalCameraId());

        assertEquals(sharedOutputConfigs[12], outputs.get(1).getSurfaceType());
        assertEquals(
                new Size((int) sharedOutputConfigs[13], (int) sharedOutputConfigs[14]),
                outputs.get(1).getSize());
        assertEquals(sharedOutputConfigs[15], outputs.get(1).getFormat());
        assertEquals(sharedOutputConfigs[16], outputs.get(1).getMirrorMode());
        assertEquals(sharedOutputConfigs[17] != 0, outputs.get(1).isReadoutTimestampEnabled());
        assertEquals(sharedOutputConfigs[18], outputs.get(1).getTimestampBase());
        assertEquals(sharedOutputConfigs[19], outputs.get(1).getDataspace());
        assertEquals(sharedOutputConfigs[20], outputs.get(1).getUsage());
        assertEquals(sharedOutputConfigs[21], outputs.get(1).getStreamUseCase());
        assertNull(outputs.get(1).getPhysicalCameraId());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CAMERA_MULTI_CLIENT)
    public void testEmptySharedOutputConfigurations() {
        int colorSpace = /* ColorSpace.Named.SRGB */ 0;
        long[] sharedOutputConfigs = {};
        SharedSessionConfiguration config =
                new SharedSessionConfiguration(colorSpace, sharedOutputConfigs);

        assertEquals(ColorSpace.get(ColorSpace.Named.values()[colorSpace]), config.getColorSpace());
        List<SharedSessionConfiguration.SharedOutputConfiguration> outputs =
                config.getOutputStreamsInformation();
        assertEquals(0, outputs.size());
    }
}
