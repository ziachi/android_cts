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

package android.virtualdevice.cts.core;

import static android.Manifest.permission.ADD_ALWAYS_UNLOCKED_DISPLAY;
import static android.Manifest.permission.ADD_MIRROR_DISPLAY;
import static android.Manifest.permission.ADD_TRUSTED_DISPLAY;
import static android.Manifest.permission.CREATE_VIRTUAL_DEVICE;
import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_CUSTOM;
import static android.companion.virtual.VirtualDeviceParams.LOCK_STATE_ALWAYS_UNLOCKED;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_CLIPBOARD;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtualdevice.flags.Flags;
import android.hardware.display.DisplayManager;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/** Tests to verify the behavior of the permissions granted by the VDM roles. */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class StreamingRoleBehaviorTest {

    private static final List<String> PERMISSIONS = List.of(
            CREATE_VIRTUAL_DEVICE,
            ADD_TRUSTED_DISPLAY,
            ADD_ALWAYS_UNLOCKED_DISPLAY,
            ADD_MIRROR_DISPLAY
    );

    @Rule
    public VirtualDeviceRule mRule = VirtualDeviceRule.createDefault();

    private VirtualDevice mVirtualDevice;

    @Before
    public void setUp() throws Exception {
        mVirtualDevice = mRule.createManagedVirtualDevice();
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_LIMITED_VDM_ROLE)
    @Test
    public void createVirtualDisplay_public_withoutMirrorDisplayPermission_throws() {
        // DisplayManager creates an auto-mirror display by default for public virtual displays.
        assertNeedsPermission(ADD_MIRROR_DISPLAY,
                () -> mRule.createManagedVirtualDisplayWithFlags(mVirtualDevice,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC));
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_LIMITED_VDM_ROLE)
    @Test
    public void createVirtualDisplay_autoMirror_withoutMirrorDisplayPermission_throws() {
        assertNeedsPermission(ADD_MIRROR_DISPLAY,
                () -> mRule.createManagedVirtualDisplayWithFlags(mVirtualDevice,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR));
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_LIMITED_VDM_ROLE)
    @Test
    public void createVirtualDisplay_publicAutoMirror_withoutMirrorDisplayPermission_throws() {
        assertNeedsPermission(ADD_MIRROR_DISPLAY,
                () -> mRule.createManagedVirtualDisplayWithFlags(mVirtualDevice,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                                | DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR));
    }

    @Test
    public void createVirtualDisplay_trusted_withoutTrustedDisplayPermission_throws() {
        assertNeedsPermission(ADD_TRUSTED_DISPLAY,
                () -> mRule.createManagedVirtualDisplay(mVirtualDevice,
                        VirtualDeviceRule.createTrustedVirtualDisplayConfigBuilder()));
    }

    @Test
    public void createVirtualDevice_customClipboardPolicy_withoutTrustedDisplayPermission_throws() {
        assertNeedsPermission(ADD_TRUSTED_DISPLAY,
                () -> mRule.createManagedVirtualDevice(new VirtualDeviceParams.Builder()
                        .setDevicePolicy(POLICY_TYPE_CLIPBOARD, DEVICE_POLICY_CUSTOM)
                        .build()));
    }

    @Test
    public void dynamicallySetCustomClipboardPolicy_withoutTrustedDisplayPermission_throws() {
        assertNeedsPermission(ADD_TRUSTED_DISPLAY,
                () -> mVirtualDevice.setDevicePolicy(POLICY_TYPE_CLIPBOARD, DEVICE_POLICY_CUSTOM));
    }

    @Test
    public void createVirtualDevice_alwaysUnlocked_withoutAlwaysUnlockedPermission_throws() {
        assertNeedsPermission(ADD_ALWAYS_UNLOCKED_DISPLAY,
                () -> mRule.createManagedVirtualDevice(new VirtualDeviceParams.Builder()
                        .setLockState(LOCK_STATE_ALWAYS_UNLOCKED)
                        .build()));
    }

    private void assertNeedsPermission(String permission, Runnable runnable) {
        List<String> permissions = new ArrayList<>(PERMISSIONS);
        assertThat(permissions.remove(permission)).isTrue();
        assertThrows(SecurityException.class, () ->
                mRule.runWithTemporaryPermission(runnable, permissions.toArray(new String[0])));
    }
}
