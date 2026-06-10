/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtualdevice.flags.Flags;
import android.content.Context;
import android.graphics.Insets;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.server.wm.Condition;
import android.view.Display;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = " cannot be accessed by instant apps")
public class VirtualDisplayTest {

    private static final String PERMISSION_CAPTURE_VIDEO_OUTPUT =
            "android.permission.CAPTURE_VIDEO_OUTPUT";

    private static final String STATUS_BAR_NAME = "android.virtualdevice.cts.core.STATUS_BAR";
    private static final int STATUS_BAR_HEIGHT = 80;

    @Rule
    public VirtualDeviceRule mRule = VirtualDeviceRule.createDefault();

    private VirtualDeviceManager mVirtualDeviceManager;
    private VirtualDevice mVirtualDevice;

    @Before
    public void setUp() {
        mVirtualDeviceManager =
                getApplicationContext().getSystemService(VirtualDeviceManager.class);
        mVirtualDevice = mRule.createManagedVirtualDevice();
    }

    @Test
    public void createVirtualDisplay_shouldSucceed() {
        VirtualDisplay virtualDisplay = mRule.createManagedVirtualDisplay(mVirtualDevice);
        verifyDisplay(virtualDisplay);
    }

    @Test
    public void createVirtualDisplay_deprecatedOverload_shouldSucceed() {
        VirtualDisplay virtualDisplay = mVirtualDevice.createVirtualDisplay(
                /* width= */ VirtualDeviceRule.DEFAULT_VIRTUAL_DISPLAY_WIDTH,
                /* height= */ VirtualDeviceRule.DEFAULT_VIRTUAL_DISPLAY_HEIGHT,
                /* densityDpi= */ VirtualDeviceRule.DEFAULT_VIRTUAL_DISPLAY_DPI,
                /* surface= */ null,
                /* flags= */ 0,
                /* executor= */null,
                /* callback= */null);

        assertThat(virtualDisplay).isNotNull();
        try {
            verifyDisplay(virtualDisplay);
        } finally {
            virtualDisplay.release();
            mRule.assertDisplayDoesNotExist(virtualDisplay.getDisplay().getDisplayId());
        }
    }

    @Test
    public void createVirtualDisplay_defaultVirtualDisplayFlags() {
        VirtualDisplay virtualDisplay = mRule.createManagedVirtualDisplay(mVirtualDevice);

        Display display = virtualDisplay.getDisplay();
        assertThat(display.isValid()).isTrue();
        assertThat(display.getFlags()).isEqualTo(
                Display.FLAG_PRIVATE | Display.FLAG_TOUCH_FEEDBACK_DISABLED);
        // Private displays always destroy their content on removal
        assertThat(display.getRemoveMode()).isEqualTo(Display.REMOVE_MODE_DESTROY_CONTENT);
    }

    @Test
    public void createVirtualDisplay_public_createsMirrorDisplay() {
        VirtualDisplay virtualDisplay = mRule.createManagedVirtualDisplayWithFlags(mVirtualDevice,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC);

        assertThat(virtualDisplay).isNotNull();
        Display display = virtualDisplay.getDisplay();
        assertThat(display.isValid()).isTrue();
        assertThat(mVirtualDeviceManager.isVirtualDeviceOwnedMirrorDisplay(display.getDisplayId()))
                .isTrue();
    }

    @Test
    public void createVirtualDisplay_autoMirror_createsMirrorDisplay() {
        VirtualDisplay virtualDisplay = mRule.createManagedVirtualDisplayWithFlags(mVirtualDevice,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR);

        assertThat(virtualDisplay).isNotNull();
        Display display = virtualDisplay.getDisplay();
        assertThat(display.isValid()).isTrue();
        assertThat(mVirtualDeviceManager.isVirtualDeviceOwnedMirrorDisplay(display.getDisplayId()))
                .isTrue();
    }

    @Test
    public void createVirtualDisplay_publicAutoMirror_createsMirrorDisplay() {
        VirtualDisplay virtualDisplay = mRule.createManagedVirtualDisplayWithFlags(mVirtualDevice,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR);

        assertThat(virtualDisplay).isNotNull();
        Display display = virtualDisplay.getDisplay();
        assertThat(display.isValid()).isTrue();
        assertThat(mVirtualDeviceManager.isVirtualDeviceOwnedMirrorDisplay(display.getDisplayId()))
                .isTrue();
    }

    @Test
    public void createVirtualDisplay_ownContentOnly_doesNotCreateMirrorDisplay() {
        VirtualDisplay virtualDisplay = mRule.createManagedVirtualDisplayWithFlags(mVirtualDevice,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);

        assertThat(virtualDisplay).isNotNull();
        Display display = virtualDisplay.getDisplay();
        assertThat(display.isValid()).isTrue();
        assertThat(mVirtualDeviceManager.isVirtualDeviceOwnedMirrorDisplay(display.getDisplayId()))
                .isFalse();
    }

    @Test
    public void createVirtualDisplay_autoMirrorAndOwnContentOnly_doesNotCreateMirrorDisplay() {
        VirtualDisplay virtualDisplay = mRule.createManagedVirtualDisplayWithFlags(mVirtualDevice,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR);

        assertThat(virtualDisplay).isNotNull();
        Display display = virtualDisplay.getDisplay();
        assertThat(display.isValid()).isTrue();
        assertThat(mVirtualDeviceManager.isVirtualDeviceOwnedMirrorDisplay(display.getDisplayId()))
                .isFalse();
    }

    @Test
    public void createVirtualDisplay_autoMirror_flagAlwaysUnlockedNotSet() {
        VirtualDevice virtualDevice = mRule.createManagedVirtualDevice(
                new VirtualDeviceParams.Builder()
                        .setLockState(VirtualDeviceParams.LOCK_STATE_ALWAYS_UNLOCKED)
                        .build());
        VirtualDisplay virtualDisplay = mRule.createManagedVirtualDisplayWithFlags(virtualDevice,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR);

        assertThat(virtualDisplay).isNotNull();
        Display display = virtualDisplay.getDisplay();
        assertThat(display.getFlags() & Display.FLAG_ALWAYS_UNLOCKED).isEqualTo(0);
    }

    @Test
    public void createVirtualDisplay_public_flagAlwaysUnlockedNotSet() {
        VirtualDevice virtualDevice = mRule.createManagedVirtualDevice(
                new VirtualDeviceParams.Builder()
                        .setLockState(VirtualDeviceParams.LOCK_STATE_ALWAYS_UNLOCKED)
                        .build());
        VirtualDisplay virtualDisplay = mRule.createManagedVirtualDisplayWithFlags(virtualDevice,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC);

        assertThat(virtualDisplay).isNotNull();
        Display display = virtualDisplay.getDisplay();
        assertThat(display.getFlags() & Display.FLAG_ALWAYS_UNLOCKED).isEqualTo(0);
    }

    @Test
    public void createVirtualDisplay_autoMirror_flagPresentationNotSet() {
        VirtualDisplay virtualDisplay = mRule.createManagedVirtualDisplayWithFlags(mVirtualDevice,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION);

        assertThat(virtualDisplay).isNotNull();
        Display display = virtualDisplay.getDisplay();
        assertThat(display.getFlags() & Display.FLAG_PRESENTATION).isEqualTo(0);
    }

    @Test
    public void createVirtualDisplay_public_flagPresentationNotSet() {
        VirtualDisplay virtualDisplay = mRule.createManagedVirtualDisplayWithFlags(mVirtualDevice,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION);

        assertThat(virtualDisplay).isNotNull();
        Display display = virtualDisplay.getDisplay();
        assertThat(display.getFlags() & Display.FLAG_PRESENTATION).isEqualTo(0);
    }

    @Test
    public void isVirtualDeviceOwnedMirrorDisplay_invalidDisplay_returnsFalse() {
        assertThat(mVirtualDeviceManager.isVirtualDeviceOwnedMirrorDisplay(Display.INVALID_DISPLAY))
                .isFalse();
    }

    @Test
    public void isVirtualDeviceOwnedMirrorDisplay_defaultDisplay_returnsFalse() {
        assertThat(mVirtualDeviceManager.isVirtualDeviceOwnedMirrorDisplay(Display.DEFAULT_DISPLAY))
                .isFalse();
    }

    @Test
    public void isVirtualDeviceOwnedMirrorDisplay_unownedAutoMirrorDisplay_returnsFalse() {
        final VirtualDisplay virtualDisplay = mRule.runWithTemporaryPermission(
                () -> mRule.createManagedUnownedVirtualDisplayWithFlags(
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR),
                PERMISSION_CAPTURE_VIDEO_OUTPUT);

        final int displayId = virtualDisplay.getDisplay().getDisplayId();
        assertThat(mVirtualDeviceManager.isVirtualDeviceOwnedMirrorDisplay(displayId)).isFalse();
    }

    @Test
    public void isVirtualDeviceOwnedMirrorDisplay_unownedPublicDisplay_returnsFalse() {
        final VirtualDisplay virtualDisplay = mRule.runWithTemporaryPermission(
                () -> mRule.createManagedUnownedVirtualDisplayWithFlags(
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC),
                PERMISSION_CAPTURE_VIDEO_OUTPUT);

        final int displayId = virtualDisplay.getDisplay().getDisplayId();
        assertThat(mVirtualDeviceManager.isVirtualDeviceOwnedMirrorDisplay(displayId)).isFalse();
    }

    @Test
    public void isVirtualDeviceOwnedMirrorDisplay_unownedPublicAutoMirrorDisplay_returnsFalse() {
        final VirtualDisplay virtualDisplay = mRule.runWithTemporaryPermission(
                () -> mRule.createManagedUnownedVirtualDisplayWithFlags(
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                                | DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR),
                PERMISSION_CAPTURE_VIDEO_OUTPUT);

        final int displayId = virtualDisplay.getDisplay().getDisplayId();
        assertThat(mVirtualDeviceManager.isVirtualDeviceOwnedMirrorDisplay(displayId)).isFalse();
    }

    @Test
    public void isVirtualDeviceOwnedMirrorDisplay_unownedDisplay_returnsFalse() {
        VirtualDisplay virtualDisplay = mRule.createManagedUnownedVirtualDisplay();

        final int displayId = virtualDisplay.getDisplay().getDisplayId();
        assertThat(mVirtualDeviceManager.isVirtualDeviceOwnedMirrorDisplay(displayId)).isFalse();
    }

    @Test
    public void createVirtualDisplay_trustedDisplay_shouldSpecifyOwnFocusFlag() {
        VirtualDisplay virtualDisplay = mRule.createManagedVirtualDisplayWithFlags(mVirtualDevice,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED);

        assertThat(virtualDisplay).isNotNull();
        Display display = virtualDisplay.getDisplay();
        assertThat(display.isValid()).isTrue();
        int displayFlags = display.getFlags();
        assertWithMessage(
                String.format(
                        "Virtual display flags (0x%x) should contain FLAG_TRUSTED",
                        displayFlags))
                .that(displayFlags & Display.FLAG_TRUSTED)
                .isEqualTo(Display.FLAG_TRUSTED);
        assertWithMessage(
                String.format(
                        "Virtual display flags (0x%x) should contain FLAG_OWN_FOCUS",
                        displayFlags))
                .that(displayFlags & Display.FLAG_OWN_FOCUS)
                .isEqualTo(Display.FLAG_OWN_FOCUS);
    }

    /** Untrusted display goes in the default display group, so it can't be always unlocked. */
    @Test
    public void createVirtualDisplay_alwaysUnlocked_untrusted_shouldNotSpecifyAlwaysUnlockedFlag() {
        VirtualDevice virtualDevice = mRule.createManagedVirtualDevice(
                new VirtualDeviceParams.Builder()
                        .setLockState(VirtualDeviceParams.LOCK_STATE_ALWAYS_UNLOCKED)
                        .build());
        VirtualDisplay virtualDisplay = mRule.createManagedVirtualDisplay(virtualDevice);

        assertThat(virtualDisplay).isNotNull();
        Display display = virtualDisplay.getDisplay();
        assertThat(display.isValid()).isTrue();
        int displayFlags = display.getFlags();
        assertThat(displayFlags & Display.FLAG_ALWAYS_UNLOCKED).isEqualTo(0);
    }

    /** A trusted display goes in the virtual device display group, so it can be always unlocked. */
    @Test
    public void createVirtualDisplay_alwaysUnlocked_trusted_shouldSpecifyAlwaysUnlockedFlag() {
        VirtualDevice virtualDevice = mRule.createManagedVirtualDevice(
                new VirtualDeviceParams.Builder()
                        .setLockState(VirtualDeviceParams.LOCK_STATE_ALWAYS_UNLOCKED)
                        .build());
        VirtualDisplay virtualDisplay = mRule.createManagedVirtualDisplayWithFlags(virtualDevice,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED);

        assertThat(virtualDisplay).isNotNull();
        Display display = virtualDisplay.getDisplay();
        assertThat(display.isValid()).isTrue();
        int displayFlags = display.getFlags();
        assertWithMessage(
                String.format(
                        "Virtual display flags (0x%x) should contain FLAG_ALWAYS_UNLOCKED",
                        displayFlags))
                .that(displayFlags & Display.FLAG_ALWAYS_UNLOCKED)
                .isEqualTo(Display.FLAG_ALWAYS_UNLOCKED);
    }

    @Test
    public void createVirtualDisplay_nullExecutorButNonNullCallback_shouldThrow() {
        assertThrows(NullPointerException.class,
                () -> mVirtualDevice.createVirtualDisplay(
                        VirtualDeviceRule.createDefaultVirtualDisplayConfigBuilder().build(),
                        /* executor= */ null,
                        new VirtualDisplay.Callback() {
                        }));
    }

    @Test
    public void virtualDisplay_createAndRemoveSeveralDisplays() {
        ArrayList<VirtualDisplay> displays = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            displays.add(mRule.createManagedVirtualDisplay(mVirtualDevice));
        }

        // Releasing several displays in quick succession should not cause deadlock
        displays.forEach(VirtualDisplay::release);

        for (VirtualDisplay display : displays) {
            mRule.assertDisplayDoesNotExist(display.getDisplay().getDisplayId());
        }
    }

    @Test
    public void virtualDisplay_releasedWhenDeviceIsClosed() {
        ArrayList<VirtualDisplay> displays = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            displays.add(mRule.createManagedVirtualDisplay(mVirtualDevice));
        }

        // Closing the virtual device should automatically release displays.
        mVirtualDevice.close();

        for (VirtualDisplay display : displays) {
            mRule.assertDisplayDoesNotExist(display.getDisplay().getDisplayId());
        }
    }

    @Test
    public void getDeviceIdForDisplayId_returnsCorrectId() {
        VirtualDisplay virtualDisplay = mRule.createManagedVirtualDisplay(mVirtualDevice);

        assertThat(mVirtualDeviceManager.getDeviceIdForDisplayId(
                virtualDisplay.getDisplay().getDisplayId())).isEqualTo(
                mVirtualDevice.getDeviceId());
    }

    @Test
    public void getDeviceIdForDisplayId_returnsDefaultIdForReleasedDisplay() {
        VirtualDisplay virtualDisplay = mRule.createManagedVirtualDisplay(mVirtualDevice);
        virtualDisplay.release();

        mRule.assertDisplayDoesNotExist(virtualDisplay.getDisplay().getDisplayId());
        // TODO(b/317872777): Remove the polling once the callback is synchronous.
        android.companion.virtual.VirtualDevice virtualDevice = mRule.getVirtualDevice(
                mVirtualDevice.getDeviceId());
        waitForCondition("display removal", () -> virtualDevice.getDisplayIds().length == 0);
        assertThat(mVirtualDeviceManager.getDeviceIdForDisplayId(
                virtualDisplay.getDisplay().getDisplayId()))
                .isEqualTo(Context.DEVICE_ID_DEFAULT);
    }

    @Test
    public void createAndRelease_isInvalidForReleasedDisplay() {
        VirtualDisplay virtualDisplay = mRule.createManagedVirtualDisplay(mVirtualDevice);

        mVirtualDevice.close();
        mRule.assertDisplayDoesNotExist(virtualDisplay.getDisplay().getDisplayId());

        // Check whether display associated with virtual device is valid.
        Display display = virtualDisplay.getDisplay();
        assertThat(display.isValid()).isFalse();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_STATUS_BAR_AND_INSETS)
    public void addStatusBarAndInsetsOnDisplayOwnedByVirtualDevice_succeeds() {
        VirtualDisplay virtualDisplay = mRule.createManagedVirtualDisplay(mVirtualDevice,
                VirtualDeviceRule.createTrustedVirtualDisplayConfigBuilder());
        Context context = getInstrumentation().getContext().createDisplayContext(
                virtualDisplay.getDisplay());

        WindowManager.LayoutParams lp =
                new WindowManager.LayoutParams(WindowManager.LayoutParams.TYPE_STATUS_BAR);
        lp.setTitle(STATUS_BAR_NAME);
        lp.packageName = context.getPackageName();
        lp.setInsetsParams(List.of(
                new WindowManager.InsetsParams(WindowInsets.Type.statusBars())
                        .setInsetsSize(Insets.of(0, STATUS_BAR_HEIGHT, 0, 0))));

        WindowManager windowManager = context.getSystemService(WindowManager.class);
        View statusBar = new View(context);
        getInstrumentation().runOnMainSync(() -> windowManager.addView(statusBar, lp));

        mRule.getWmState().assertWindowDisplayed(STATUS_BAR_NAME);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_STATUS_BAR_AND_INSETS)
    public void addStatusBarAndInsetsOnDisplayOwnedByVirtualDevice_nonTrustedDisplay_throws() {
        VirtualDisplay virtualDisplay = mRule.createManagedVirtualDisplay(mVirtualDevice,
                VirtualDeviceRule.createDefaultVirtualDisplayConfigBuilder());
        Context context = getInstrumentation().getContext().createDisplayContext(
                virtualDisplay.getDisplay());

        WindowManager.LayoutParams lp =
                new WindowManager.LayoutParams(WindowManager.LayoutParams.TYPE_STATUS_BAR);
        lp.setTitle(STATUS_BAR_NAME);
        lp.packageName = context.getPackageName();
        lp.setInsetsParams(List.of(
                new WindowManager.InsetsParams(WindowInsets.Type.statusBars())
                        .setInsetsSize(Insets.of(0, STATUS_BAR_HEIGHT, 0, 0))));

        WindowManager windowManager = context.getSystemService(WindowManager.class);
        View statusBar = new View(context);
        assertThrows(RuntimeException.class, () ->
                getInstrumentation().runOnMainSync(() -> windowManager.addView(statusBar, lp)));
    }

    private void verifyDisplay(VirtualDisplay virtualDisplay) {
        assertThat(virtualDisplay).isNotNull();
        Display display = virtualDisplay.getDisplay();
        assertThat(display.isValid()).isTrue();
        assertThat(display.getWidth()).isEqualTo(VirtualDeviceRule.DEFAULT_VIRTUAL_DISPLAY_WIDTH);
        assertThat(display.getHeight()).isEqualTo(VirtualDeviceRule.DEFAULT_VIRTUAL_DISPLAY_HEIGHT);
    }

    private static void waitForCondition(String message, BooleanSupplier waitCondition) {
        assertThat(Condition.waitFor(message, waitCondition)).isTrue();
    }
}
