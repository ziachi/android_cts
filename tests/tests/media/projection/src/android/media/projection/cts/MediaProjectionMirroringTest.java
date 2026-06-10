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
package android.media.projection.cts;

import static android.content.pm.PackageManager.FEATURE_SCREEN_LANDSCAPE;
import static android.content.pm.PackageManager.FEATURE_SCREEN_PORTRAIT;
import static android.server.wm.CtsWindowInfoUtils.assertAndDumpWindowState;
import static android.server.wm.CtsWindowInfoUtils.waitForStableWindowGeometry;
import static android.server.wm.CtsWindowInfoUtils.waitForWindowInfo;
import static android.view.Surface.ROTATION_270;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static org.junit.Assume.assumeTrue;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.display.VirtualDisplay;
import android.media.cts.MediaProjectionRule;
import android.media.projection.MediaProjection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.UserHandle;
import android.server.wm.RotationSession;
import android.server.wm.WindowManagerStateHelper;
import android.util.Log;
import android.view.Surface;
import android.view.WindowMetrics;
import android.window.WindowInfosListenerForTest.WindowInfo;

import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.FrameworkSpecificTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.time.Duration;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Test {@link MediaProjection} successfully mirrors the display contents.
 *
 * <p>Validate that mirrored views are the expected size, for both full display and single app
 * capture (if offered). Instead of examining the pixels match exactly (which is historically a
 * flaky way of validating mirroring), examine the structure of the mirrored hierarchy, to ensure
 * that mirroring is initiated correctly, and any transformations are applied as expected.
 *
 * <p>Run with:
 * atest CtsMediaProjectionTestCases:MediaProjectionMirroringTest
 */
@FrameworkSpecificTest
public class MediaProjectionMirroringTest {
    private static final String TAG = "MediaProjectionMirroringTest";
    private static final int TOLERANCE = 1;
    private static final int TIMEOUT_MS = 1000;
    private Context mContext;

    @Rule public MediaProjectionRule mMediaProjectionRule = new MediaProjectionRule();

    private final ActivityOptions.LaunchCookie mLaunchCookie = new ActivityOptions.LaunchCookie();
    private final WindowManagerStateHelper mWmState = new WindowManagerStateHelper();
    /**
     * Whether to wait for the rotation to be stable state after testing. It can be set if the
     * display rotation may be changed by test.
     */
    private boolean mWaitForRotationOnTearDown;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        runWithShellPermissionIdentity(() -> {
            mContext.getPackageManager().revokeRuntimePermission(
                    mContext.getPackageName(),
                    android.Manifest.permission.SYSTEM_ALERT_WINDOW,
                    new UserHandle(mContext.getUserId()));
        });
    }

    @After
    public void tearDown() {
        if (mWaitForRotationOnTearDown) {
            mWmState.waitForDisplayUnfrozen();
        }
    }

    // Validate that the mirrored hierarchy is the expected size.
    @Test
    public void testDisplayCapture() throws Exception {
        Intent testActivityIntent = new Intent(mContext, Activity.class);
        // Start full screen capture.
        mMediaProjectionRule.startMediaProjection();

        final WindowMetrics maxWindowMetrics =
                mMediaProjectionRule.getActivity().getWindowManager().getMaximumWindowMetrics();

        VirtualDisplay virtualDisplay =
                mMediaProjectionRule.createVirtualDisplay(
                        maxWindowMetrics.getBounds().width(),
                        maxWindowMetrics.getBounds().height());

        try (ActivityScenario<Activity> activityScenario =
                ActivityScenario.launch(testActivityIntent)) {
            activityScenario.onActivity(
                    activity -> {
                        // Get the bounds of the activity on screen - use getGlobalVisibleRect to
                        // account for
                        // possible insets caused by DisplayCutout
                        final Rect activityRect = new Rect();
                        activity.getWindow().getDecorView().getGlobalVisibleRect(activityRect);
                        validateMirroredHierarchy(
                                activity,
                                virtualDisplay.getDisplay().getDisplayId(),
                                new Point(activityRect.width(), activityRect.height()));
                    });
        }
    }

    // Validate that the mirrored hierarchy is the expected size after rotating the default display.
    @Test
    public void testDisplayCapture_rotation() throws Exception {
        assumeTrue("Skipping test: no rotation support", supportsRotation());

        // Start full screen capture.
        mMediaProjectionRule.startMediaProjection();
        Activity activity = mMediaProjectionRule.getActivity();

        final WindowMetrics maxWindowMetrics =
                activity.getWindowManager().getMaximumWindowMetrics();
        final int initialRotation = activity.getDisplay().getRotation();

        VirtualDisplay virtualDisplay =
                mMediaProjectionRule.createVirtualDisplay(
                        maxWindowMetrics.getBounds().width(),
                        maxWindowMetrics.getBounds().height());

        Intent testActivityIntent = new Intent(mContext, TestRotationActivity.class);

        try (ActivityScenario<TestRotationActivity> activityScenario =
                        ActivityScenario.launch(testActivityIntent);
                RotationSession rotationSession = createManagedRotationSession(); ) {
            rotateDeviceAndWaitForActivity(rotationSession, initialRotation);
            // Re-fetch the activity since reference may have been modified during rotation.
            activityScenario.onActivity(
                    testActivity -> {
                        // Get the bounds of the activity on screen - use getGlobalVisibleRect to
                        // account for
                        // possible insets caused by DisplayCutout
                        final Rect activityRect = new Rect();
                        testActivity.getWindow().getDecorView().getGlobalVisibleRect(activityRect);

                        final Point mirroredSize =
                                calculateScaledMirroredActivitySize(
                                        testActivity.getWindowManager().getCurrentWindowMetrics(),
                                        virtualDisplay,
                                        new Point(activityRect.width(), activityRect.height()));
                        validateMirroredHierarchy(
                                testActivity,
                                virtualDisplay.getDisplay().getDisplayId(),
                                mirroredSize);
                    });
        }
    }

    // Validate that the mirrored hierarchy is the expected size.
    @Test
    public void testSingleAppCapture() throws Exception {
        // Start full screen capture.
        mMediaProjectionRule.startMediaProjection(mLaunchCookie);
        final WindowMetrics maxWindowMetrics =
                mMediaProjectionRule.getActivity().getWindowManager().getMaximumWindowMetrics();
        VirtualDisplay virtualDisplay =
                mMediaProjectionRule.createVirtualDisplay(
                        maxWindowMetrics.getBounds().width(),
                        maxWindowMetrics.getBounds().height());

        try (ActivityScenario<Activity> activityScenario =
                ActivityScenario.launch(
                        new Intent(mContext, Activity.class),
                        createActivityScenarioWithLaunchCookie(mLaunchCookie))) {
            activityScenario.onActivity(
                    activity -> {
                        // Get the bounds of the activity on screen - use getGlobalVisibleRect to
                        // account for
                        // possible insets caused by DisplayCutout
                        final Rect activityRect = new Rect();
                        activity.getWindow().getDecorView().getGlobalVisibleRect(activityRect);

                        validateMirroredHierarchy(
                                activity,
                                virtualDisplay.getDisplay().getDisplayId(),
                                new Point(activityRect.width(), activityRect.height()));
                    });
        }
    }

    // TODO (b/284968776): test single app capture in split screen

    /**
     * Returns ActivityOptions with the given launch cookie set.
     */
    private static Bundle createActivityScenarioWithLaunchCookie(
            @NonNull ActivityOptions.LaunchCookie launchCookie) {
        ActivityOptions activityOptions = ActivityOptions.makeBasic();
        activityOptions.setLaunchCookie(launchCookie);
        return activityOptions.toBundle();
    }

    /**
     * Rotates the device 90 degrees & waits for the display & activity configuration to stabilize.
     */
    private void rotateDeviceAndWaitForActivity(
            @NonNull RotationSession rotationSession, @Surface.Rotation int initialRotation) {
        // Rotate the device by 90 degrees
        rotationSession.set((initialRotation + 1) % (ROTATION_270 + 1),
                /* waitForDeviceRotation=*/ true);
        try {
            waitForStableWindowGeometry(Duration.ofMillis(TIMEOUT_MS));
        } catch (InterruptedException e) {
            Log.e(TAG, "Unable to wait for window to stabilize after rotation: " + e.getMessage());
        }
    }

    /**
     * Calculate the size of the activity, scaled to fit on the VirtualDisplay.
     *
     * @param currentWindowMetrics The size of the source activity, before it is mirrored
     * @param virtualDisplay       The VirtualDisplay the mirrored content is sent to and scaled to
     *                             fit
     * @return The expected size of the mirrored activity on the VirtualDisplay
     */
    private static Point calculateScaledMirroredActivitySize(
            @NonNull WindowMetrics currentWindowMetrics,
            @NonNull VirtualDisplay virtualDisplay, @Nullable Point visibleBounds) {
        // Calculate the aspect ratio of the original activity.
        final Point currentBounds = new Point(currentWindowMetrics.getBounds().width(),
                currentWindowMetrics.getBounds().height());
        final float aspectRatio = currentBounds.x * 1f / currentBounds.y;
        // Find the size of the surface we are mirroring to.
        final Point surfaceSize = virtualDisplay.getSurface().getDefaultSize();
        int mirroredWidth;
        int mirroredHeight;

        // Calculate any width & height deltas caused by DisplayCutout insets
        Point sizeDifference = new Point();
        if (visibleBounds != null) {
            int widthDifference = currentBounds.x - visibleBounds.x;
            int heightDifference = currentBounds.y - visibleBounds.y;
            sizeDifference.set(widthDifference, heightDifference);
        }

        if (surfaceSize.x < surfaceSize.y) {
            // Output surface is portrait, so its width constrains. The mirrored activity is
            // scaled down to fill the width entirely, and will have horizontal black bars at the
            // top and bottom.
            // Also apply scaled insets, to handle case where device has a display cutout which
            // shifts the content horizontally when landscape.
            int adjustedHorizontalInsets = Math.round(sizeDifference.x / aspectRatio);
            int adjustedVerticalInsets = Math.round(sizeDifference.y / aspectRatio);
            mirroredWidth = surfaceSize.x - adjustedHorizontalInsets;
            mirroredHeight = Math.round(surfaceSize.x / aspectRatio) - adjustedVerticalInsets;
        } else {
            // Output surface is landscape, so its height constrains. The mirrored activity is
            // scaled down to fill the height entirely, and will have horizontal black bars on the
            // left and right.
            // Also apply scaled insets, to handle case where device has a display cutout which
            // shifts the content vertically when portrait.
            int adjustedHorizontalInsets = Math.round(sizeDifference.x * aspectRatio);
            int adjustedVerticalInsets = Math.round(sizeDifference.y * aspectRatio);
            mirroredWidth = Math.round(surfaceSize.y * aspectRatio) - adjustedHorizontalInsets;
            mirroredHeight = surfaceSize.y - adjustedVerticalInsets;
        }
        return new Point(mirroredWidth, mirroredHeight);
    }

    /**
     * Validate the given activity is in the hierarchy mirrored to the VirtualDisplay.
     *
     * <p>Note that the hierarchy is present on the VirtualDisplay because the hierarchy is mirrored
     * to the Surface provided to #createVirtualDisplay.
     *
     * @param activity           The activity that we expect to be mirrored
     * @param virtualDisplayId   The id of the virtual display we are mirroring to
     * @param expectedWindowSize The expected size of the mirrored activity
     */
    private static void validateMirroredHierarchy(
            Activity activity, int virtualDisplayId,
            @NonNull Point expectedWindowSize) {
        Predicate<WindowInfo> hasExpectedDimensions = windowInfo -> {
            int widthDiff = Math.abs(windowInfo.bounds.width() - expectedWindowSize.x);
            int heightDiff = Math.abs(windowInfo.bounds.height() - expectedWindowSize.y);
            return widthDiff <= TOLERANCE && heightDiff <= TOLERANCE;
        };
        Supplier<IBinder> taskWindowTokenSupplier =
                activity.getWindow().getDecorView()::getWindowToken;
        try {
            Log.e(TAG, "WindowToken: " + taskWindowTokenSupplier.get());
            boolean condition = waitForWindowInfo(hasExpectedDimensions, Duration.ofSeconds(5),
                    taskWindowTokenSupplier, virtualDisplayId);
            assertAndDumpWindowState(TAG,
                    "Mirrored activity isn't the expected size of " + expectedWindowSize,
                    condition);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private RotationSession createManagedRotationSession() {
        mWaitForRotationOnTearDown = true;
        return new RotationSession(mWmState);
    }

    /**
     * Rotation support is indicated by explicitly having both landscape and portrait
     * features or not listing either at all.
     */
    protected boolean supportsRotation() {
        final boolean supportsLandscape = hasDeviceFeature(FEATURE_SCREEN_LANDSCAPE);
        final boolean supportsPortrait = hasDeviceFeature(FEATURE_SCREEN_PORTRAIT);
        return (supportsLandscape && supportsPortrait)
                || (!supportsLandscape && !supportsPortrait);
    }

    protected boolean hasDeviceFeature(final String requiredFeature) {
        return mContext.getPackageManager()
                .hasSystemFeature(requiredFeature);
    }

    /**
     * Stub activity for launching an activity meant to be rotated.
     */
    public static class TestRotationActivity extends Activity {
        // Stub
    }
}
