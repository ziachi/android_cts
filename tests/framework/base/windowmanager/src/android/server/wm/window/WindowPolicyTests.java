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

package android.server.wm.window;

import static android.app.StatusBarManager.NAV_BAR_MODE_DEFAULT;
import static android.app.StatusBarManager.NAV_BAR_MODE_KIDS;
import static android.content.pm.PackageManager.FEATURE_SCREEN_LANDSCAPE;
import static android.content.pm.PackageManager.FEATURE_SCREEN_PORTRAIT;
import static android.server.wm.ShellCommandHelper.executeShellCommand;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

import android.app.StatusBarManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.server.wm.NestedShellPermission;
import android.server.wm.cts.R;
import android.view.View;
import android.view.Window;

import com.android.compatibility.common.util.PollingCheck;
import com.android.window.flags.Flags;

import org.junit.Test;

/**
 * Ensure window policies are applied as expected.
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceWindow:WindowPolicyTests
 */
@Presubmit
public class WindowPolicyTests extends WindowPolicyTestBase {

    private static final long TIMEOUT_NAV_BAR_MODE_CHANGED = 2000L;

    @RequiresFlagsEnabled(Flags.FLAG_ENFORCE_EDGE_TO_EDGE)
    @Test
    public void testWindowInsets() {
        if (Flags.disableOptOutEdgeToEdge()) {
            TestActivity.sStyleIdList.add(R.style.OptOutEdgeToEdgeEnforcement);
        }
        final TestActivity activity = startActivitySync(TestActivity.class);

        runOnMainSync(() -> {
            assertEquals(
                    "WindowInsets must not be changed.",
                    activity.getContentView().getRootWindowInsets(),
                    activity.getContentView().getWindowInsets());
        });
    }

    private static void assertFillWindowBounds(TestActivity activity) {
        runOnMainSync(() -> {
            assertEquals(
                    "Decor view must fill window bounds.",
                    activity.getResources().getConfiguration().windowConfiguration.getBounds(),
                    getFrameOnScreen(activity.getWindow().getDecorView()));
            assertEquals(
                    "Content view must fill window bounds.",
                    activity.getResources().getConfiguration().windowConfiguration.getBounds(),
                    getFrameOnScreen(activity.getContentView()));
        });
    }

    private static Rect getFrameOnScreen(View view) {
        final int[] location = {0, 0};
        view.getLocationOnScreen(location);
        return new Rect(
                location[0],
                location[1],
                location[0] + view.getWidth(),
                location[1] + view.getHeight());
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENFORCE_EDGE_TO_EDGE)
    @Test
    public void testWindowStyleLayoutInDisplayCutoutMode_unspecified() {
        if (Flags.disableOptOutEdgeToEdge()) {
            TestActivity.sStyleIdList.add(R.style.OptOutEdgeToEdgeEnforcement);
        }
        TestActivity.sStyleIdList.add(R.style.LayoutInDisplayCutoutModeUnspecified);
        assertFillWindowBounds(startActivitySync(TestActivity.class));
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENFORCE_EDGE_TO_EDGE)
    @Test
    public void testWindowStyleLayoutInDisplayCutoutMode_never() {
        if (Flags.disableOptOutEdgeToEdge()) {
            TestActivity.sStyleIdList.add(R.style.OptOutEdgeToEdgeEnforcement);
        }
        TestActivity.sStyleIdList.add(R.style.LayoutInDisplayCutoutModeNever);
        assertFillWindowBounds(startActivitySync(TestActivity.class));
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENFORCE_EDGE_TO_EDGE)
    @Test
    public void testWindowStyleLayoutInDisplayCutoutMode_default() {
        if (Flags.disableOptOutEdgeToEdge()) {
            TestActivity.sStyleIdList.add(R.style.OptOutEdgeToEdgeEnforcement);
        }
        TestActivity.sStyleIdList.add(R.style.LayoutInDisplayCutoutModeDefault);
        assertFillWindowBounds(startActivitySync(TestActivity.class));
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENFORCE_EDGE_TO_EDGE)
    @Test
    public void testWindowStyleLayoutInDisplayCutoutMode_shortEdges() {
        if (Flags.disableOptOutEdgeToEdge()) {
            TestActivity.sStyleIdList.add(R.style.OptOutEdgeToEdgeEnforcement);
        }
        TestActivity.sStyleIdList.add(R.style.LayoutInDisplayCutoutModeShortEdges);
        assertFillWindowBounds(startActivitySync(TestActivity.class));
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENFORCE_EDGE_TO_EDGE)
    @Test
    public void testWindowStyleLayoutInDisplayCutoutMode_always() {
        if (Flags.disableOptOutEdgeToEdge()) {
            TestActivity.sStyleIdList.add(R.style.OptOutEdgeToEdgeEnforcement);
        }
        TestActivity.sStyleIdList.add(R.style.LayoutInDisplayCutoutModeAlways);
        assertFillWindowBounds(startActivitySync(TestActivity.class));
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENFORCE_EDGE_TO_EDGE)
    @Test
    public void testLayoutParamsLayoutInDisplayCutoutMode_unspecified() {
        if (Flags.disableOptOutEdgeToEdge()) {
            TestActivity.sStyleIdList.add(R.style.OptOutEdgeToEdgeEnforcement);
        }
        assertFillWindowBounds(startActivitySync(TestActivity.class));
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENFORCE_EDGE_TO_EDGE)
    @Test
    public void testLayoutParamsLayoutInDisplayCutoutMode_never() {
        if (Flags.disableOptOutEdgeToEdge()) {
            TestActivity.sStyleIdList.add(R.style.OptOutEdgeToEdgeEnforcement);
        }
        TestActivity.sLayoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER;
        assertFillWindowBounds(startActivitySync(TestActivity.class));
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENFORCE_EDGE_TO_EDGE)
    @Test
    public void testLayoutParamsLayoutInDisplayCutoutMode_default() {
        if (Flags.disableOptOutEdgeToEdge()) {
            TestActivity.sStyleIdList.add(R.style.OptOutEdgeToEdgeEnforcement);
        }
        TestActivity.sLayoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
        assertFillWindowBounds(startActivitySync(TestActivity.class));
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENFORCE_EDGE_TO_EDGE)
    @Test
    public void testLayoutParamsLayoutInDisplayCutoutMode_shortEdges() {
        if (Flags.disableOptOutEdgeToEdge()) {
            TestActivity.sStyleIdList.add(R.style.OptOutEdgeToEdgeEnforcement);
        }
        TestActivity.sLayoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        assertFillWindowBounds(startActivitySync(TestActivity.class));
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENFORCE_EDGE_TO_EDGE)
    @Test
    public void testLayoutParamsLayoutInDisplayCutoutMode_always() {
        if (Flags.disableOptOutEdgeToEdge()) {
            TestActivity.sStyleIdList.add(R.style.OptOutEdgeToEdgeEnforcement);
        }
        TestActivity.sLayoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        assertFillWindowBounds(startActivitySync(TestActivity.class));
    }

    @RequiresFlagsEnabled(Flags.FLAG_ENFORCE_EDGE_TO_EDGE)
    @Test
    public void testSystemBarColor() {
        if (Flags.disableOptOutEdgeToEdge()) {
            TestActivity.sStyleIdList.add(R.style.OptOutEdgeToEdgeEnforcement);
        }
        TestActivity.sStyleIdList.add(R.style.BlackSystemBars);
        final TestActivity activity = startActivitySync(TestActivity.class);
        runOnMainSync(() -> {
            final Window window = activity.getWindow();
            assertEquals("Status bar color must be transparent.",
                    Color.TRANSPARENT, Color.alpha(window.getStatusBarColor()));
            assertEquals("Navigation bar color must be transparent.",
                    Color.TRANSPARENT, window.getNavigationBarColor());
            assertEquals("Navigation bar divider color must be transparent.",
                    Color.TRANSPARENT, window.getNavigationBarDividerColor());

            window.setStatusBarColor(Color.BLACK);
            assertEquals("Status bar color must not be changed.",
                    Color.TRANSPARENT, window.getStatusBarColor());
            window.setNavigationBarColor(Color.BLACK);
            assertEquals("Navigation bar color must not be changed.",
                    Color.TRANSPARENT, window.getNavigationBarColor());
            window.setNavigationBarDividerColor(Color.BLACK);
            assertEquals("Navigation bar divider color not be changed.",
                    Color.TRANSPARENT, window.getNavigationBarDividerColor());
        });
    }

    @Test
    public void testOrientationInKidsMode_portrait() {
        assumeTrue(hasDeviceFeature(FEATURE_SCREEN_PORTRAIT));

        runInKidsModeSync(
                () -> {
                    final TestActivity activity = startActivitySync(PortraitTestActivity.class);
                    PollingCheck.waitFor(
                            TIMEOUT_NAV_BAR_MODE_CHANGED,
                            () ->
                                    activity.getResources().getConfiguration().orientation
                                            == Configuration.ORIENTATION_PORTRAIT,
                            "Activity must be launched in portrait mode.");
                });
    }

    @Test
    public void testOrientationInKidsMode_landscape() {
        assumeTrue(hasDeviceFeature(FEATURE_SCREEN_LANDSCAPE));

        runInKidsModeSync(
                () -> {
                    final TestActivity activity = startActivitySync(LandscapeTestActivity.class);
                    PollingCheck.waitFor(
                            TIMEOUT_NAV_BAR_MODE_CHANGED,
                            () ->
                                    activity.getResources().getConfiguration().orientation
                                            == Configuration.ORIENTATION_LANDSCAPE,
                            "Activity must be launched in landscape mode.");
                });
    }

    private void runInKidsModeSync(Runnable runnable) {
        final StatusBarManager statusBarManager = mContext.getSystemService(StatusBarManager.class);
        assumeNotNull(statusBarManager);

        try {
            NestedShellPermission.run(() -> statusBarManager.setNavBarMode(NAV_BAR_MODE_KIDS));
            runnable.run();
        } finally {
            NestedShellPermission.run(() -> statusBarManager.setNavBarMode(NAV_BAR_MODE_DEFAULT));

            // Wait for restoring nav bar mode before leaving. It is to prevent the next test from
            // getting affected by unexpected configuration changes.
            executeShellCommand("am wait-for-broadcast-barrier");
        }
    }

    public static class PortraitTestActivity extends TestActivity {}

    public static class LandscapeTestActivity extends TestActivity {}
}
