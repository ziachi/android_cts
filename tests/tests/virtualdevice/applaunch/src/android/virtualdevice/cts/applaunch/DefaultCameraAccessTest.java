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

package android.virtualdevice.cts.applaunch;

import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_CUSTOM;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_DEFAULT_DEVICE_CAMERA_ACCESS;

import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtualdevice.flags.Flags;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.virtualdevice.cts.defaultcameraaccesstestbase.DefaultCameraAccessTestBase;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class DefaultCameraAccessTest extends DefaultCameraAccessTestBase {

    @RequiresFlagsDisabled(Flags.FLAG_DEFAULT_DEVICE_CAMERA_ACCESS_POLICY)
    @Test
    public void appsInVirtualDevice_withFlagDisabled_shouldNotHaveAccessToCamera()
            throws Exception {
        VirtualDevice virtualDevice = virtualDeviceRule.createManagedVirtualDevice();
        verifyCameraAccessBlocked(virtualDevice);
    }

    @RequiresFlagsEnabled(Flags.FLAG_DEFAULT_DEVICE_CAMERA_ACCESS_POLICY)
    @Test
    public void appsInVirtualDevice_withDefaultCameraAccessPolicy_shouldHaveAccessToCamera()
            throws Exception {
        VirtualDevice virtualDevice = virtualDeviceRule.createManagedVirtualDevice();
        verifyCameraAccessAllowed(virtualDevice);
    }

    @RequiresFlagsEnabled(Flags.FLAG_DEFAULT_DEVICE_CAMERA_ACCESS_POLICY)
    @Test
    public void appsInVirtualDevice_withCustomCameraAccessPolicy_shouldNotHaveAccessToCamera()
            throws Exception {
        VirtualDevice virtualDevice = virtualDeviceRule.createManagedVirtualDevice(
                new VirtualDeviceParams.Builder().setDevicePolicy(
                        POLICY_TYPE_DEFAULT_DEVICE_CAMERA_ACCESS, DEVICE_POLICY_CUSTOM).build());
        verifyCameraAccessBlocked(virtualDevice);
    }
}
