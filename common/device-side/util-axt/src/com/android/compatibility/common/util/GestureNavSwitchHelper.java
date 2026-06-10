/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.compatibility.common.util;

import static com.google.common.truth.Truth.assertWithMessage;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Build;
import android.util.Log;
import android.view.WindowInsets;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import java.io.IOException;

/**
 * Helper class to enable gesture navigation on the device.
 */
public final class GestureNavSwitchHelper {

    private static final String TAG = "GestureNavSwitchHelper";

    private static final String NAV_BAR_MODE_3BUTTON_OVERLAY =
            "com.android.internal.systemui.navbar.threebutton";

    private static final String NAV_BAR_MODE_GESTURAL_OVERLAY =
            "com.android.internal.systemui.navbar.gestural";

    private static final String STATE_ENABLED = "STATE_ENABLED";

    private static final String STATE_DISABLED = "STATE_DISABLED";

    private static final String STATE_UNKNOWN = "STATE_UNKNOWN";

    private static final long OVERLAY_WAIT_TIMEOUT = 10000;

    private final UiDevice mDevice;
    private final WindowManager mWindowManager;
    /** Whether the device supports the navigation bar. */
    private final boolean mHasNavigationBar;
    /** Failed to enable gesture navigation. */
    private boolean mEnableGestureNavFailed;
    /** Failed to enable three button navigation. */
    private boolean mEnableThreeButtonNavFailed;

    /**
     * Initialize all options in System Gesture.
     */
    public GestureNavSwitchHelper() {
        final var instrumentation = InstrumentationRegistry.getInstrumentation();
        mDevice = UiDevice.getInstance(instrumentation);
        final Context context = instrumentation.getTargetContext();

        mWindowManager = context.getSystemService(WindowManager.class);

        final var pm = context.getPackageManager();
        // No bars on embedded devices.
        // No bars on TVs and watches.
        // No bars on PCs.
        mHasNavigationBar = !(pm.hasSystemFeature(PackageManager.FEATURE_WATCH)
                || pm.hasSystemFeature(PackageManager.FEATURE_EMBEDDED)
                || pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                || pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1
                    && pm.hasSystemFeature(PackageManager.FEATURE_PC)));
    }

    /** Whether the device supports the navigation bar. */
    public boolean hasNavigationBar() {
        return mHasNavigationBar && containsNavigationBar();
    }

    private void insetsToRect(Insets insets, Rect outRect) {
        outRect.set(insets.left, insets.top, insets.right, insets.bottom);
    }

    /**
     * Checks whether the gesture navigation mode overlay is installed, regardless of whether it is
     * enabled.
     */
    public boolean hasGestureNavOverlay() {
        return hasOverlay(NAV_BAR_MODE_GESTURAL_OVERLAY);
    }

    /**
     * Checks whether the three button navigation mode overlay is installed, regardless of whether
     * it is enabled.
     */
    public boolean hasThreeButtonNavOverlay() {
        return hasOverlay(NAV_BAR_MODE_3BUTTON_OVERLAY);
    }

    /** Checks whether the given overlay is installed, regardless of whether it is enabled. */
    private boolean hasOverlay(@NonNull String overlayPackage) {
        final String state = getStateForOverlay(overlayPackage);
        return STATE_ENABLED.equals(state) || STATE_DISABLED.equals(state);
    }

    /**
     * Enable gesture navigation mode.
     *
     * @return Whether the navigation mode was successfully set. This is {@code true} if the
     * requested mode is already set.
     */
    public boolean enableGestureNavigationMode() {
        // skip retry
        if (mEnableGestureNavFailed) {
            return false;
        }
        if (!hasNavigationBar()) {
            return false;
        }
        if (isGestureMode()) {
            return true;
        }
        final boolean success = setNavigationMode(NAV_BAR_MODE_GESTURAL_OVERLAY);
        mEnableGestureNavFailed = !success;
        return success;
    }

    /**
     * Enable three button navigation mode.
     *
     * @return Whether the navigation mode was successfully set. This is {@code true} if the
     * requested mode is already set.
     */
    public boolean enableThreeButtonNavigationMode() {
        // skip retry
        if (mEnableThreeButtonNavFailed) {
            return false;
        }
        if (!hasNavigationBar()) {
            return true;
        }
        if (isThreeButtonMode()) {
            return true;
        }
        final boolean success = setNavigationMode(NAV_BAR_MODE_3BUTTON_OVERLAY);
        mEnableThreeButtonNavFailed = !success;
        return success;
    }

    /**
     * Sets the navigation mode to gesture navigation, if necessary.
     *
     * @return an {@link AutoCloseable} that resets the navigation mode, if necessary.
     */
    @NonNull
    public AutoCloseable withGestureNavigationMode() {
        if (isGestureMode() || !hasNavigationBar()) {
            return () -> {};
        }

        assertWithMessage("Gesture navigation mode set")
                .that(enableGestureNavigationMode()).isTrue();
        return () -> assertWithMessage("Gesture navigation mode unset")
                .that(enableThreeButtonNavigationMode()).isTrue();
    }

    /**
     * Sets the navigation mode to three button navigation, if necessary.
     *
     * @return an {@link AutoCloseable} that resets the navigation mode, if necessary.
     */
    @NonNull
    public AutoCloseable withThreeButtonNavigationMode() {
        if (isThreeButtonMode() || !hasNavigationBar()) {
            return () -> {};
        }

        assertWithMessage("Three button navigation mode set")
                .that(enableThreeButtonNavigationMode()).isTrue();
        return () -> assertWithMessage("Three button navigation mode unset")
                .that(enableGestureNavigationMode()).isTrue();
    }

    /**
     * Sets the navigation mode exclusively (disabling all other modes).
     *
     * @param navigationModePkgName the package name of the navigation mode to be set.
     *
     * @return whether the navigation mode was successfully set.
     */
    private boolean setNavigationMode(@NonNull String navigationModePkgName) {
        if (!hasOverlay(navigationModePkgName)) {
            Log.i(TAG, "setNavigationMode, overlay: " + navigationModePkgName + " does not exist");
            return false;
        }
        Log.d(TAG, "setNavigationMode: " + navigationModePkgName);
        try {
            mDevice.executeShellCommand("cmd overlay enable-exclusive --category --user current "
                    + navigationModePkgName);
            mDevice.executeShellCommand("am wait-for-broadcast-barrier");
        } catch (IOException e) {
            Log.w(TAG, "Failed to set navigation mode", e);
            return false;
        }
        return waitForOverlayState(navigationModePkgName, STATE_ENABLED);
    }

    private void getCurrentInsetsSize(@NonNull Rect outSize) {
        outSize.setEmpty();
        if (mWindowManager != null) {
            WindowInsets insets = mWindowManager.getCurrentWindowMetrics().getWindowInsets();
            Insets navInsets = insets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.navigationBars());
            insetsToRect(navInsets, outSize);
        }
    }

    private boolean containsNavigationBar() {
        final var peekSize = new Rect();
        getCurrentInsetsSize(peekSize);
        return peekSize.height() != 0;
    }

    /** Whether three button navigation mode is enabled. */
    public boolean isThreeButtonMode() {
        return containsNavigationBar()
                && STATE_ENABLED.equals(getStateForOverlay(NAV_BAR_MODE_3BUTTON_OVERLAY));
    }

    /** Whether gesture navigation mode is enabled. */
    public boolean isGestureMode() {
        return containsNavigationBar()
                && STATE_ENABLED.equals(getStateForOverlay(NAV_BAR_MODE_GESTURAL_OVERLAY));
    }

    /**
     * Waits for the state of the overlay with the given package name to match the expected state.
     *
     * @param overlayPackage the package name of the overlay to check.
     * @param expectedState  the expected overlay state.
     *
     * @return whether the overlay state eventually matched the expected state.
     */
    private boolean waitForOverlayState(@NonNull String overlayPackage,
            @NonNull String expectedState) {
        final long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime < OVERLAY_WAIT_TIMEOUT) {
            if (expectedState.equals(getStateForOverlay(overlayPackage))) {
                return true;
            }
        }

        Log.i(TAG, "waitForOverlayState, overlayPackage: " + overlayPackage + " state was not: "
                + expectedState);
        return false;
    }

    /**
     * Returns the state of the overlay with the given package name.
     *
     * @param overlayPackage the package name of the overlay to check.
     */
    @NonNull
    private String getStateForOverlay(@NonNull String overlayPackage) {
        try {
            final String res = mDevice.executeShellCommand("cmd overlay dump --user current state "
                    + overlayPackage);
            // When current user is not 0 (e.g. HSUM), the output will contain an additional line.
            return res.split("\\r?\\n")[0].trim();
        } catch (IOException e) {
            Log.w(TAG, "Failed to get overlay state", e);
            return STATE_UNKNOWN;
        }
    }
}
