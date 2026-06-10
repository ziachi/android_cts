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

package android.server.wm.jetpack.utils;

import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_UNDEFINED;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
import static android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE;
import static android.content.pm.PackageManager.FEATURE_SCREEN_LANDSCAPE;
import static android.content.pm.PackageManager.FEATURE_SCREEN_PORTRAIT;
import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;
import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static android.server.wm.WindowManagerState.STATE_RESUMED;
import static android.server.wm.jetpack.utils.TestActivityLauncher.KEY_ACTIVITY_ID;
import static android.view.Display.INVALID_DISPLAY;
import static android.view.Surface.ROTATION_0;
import static android.view.Surface.ROTATION_180;
import static android.view.Surface.ROTATION_90;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.ActivityTaskManager;
import android.app.Application;
import android.app.Instrumentation;
import android.app.PictureInPictureParams;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.IBinder;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.RotationSession;
import android.server.wm.WindowManagerState;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.window.extensions.layout.FoldingFeature;
import androidx.window.sidecar.SidecarDeviceState;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** Base class for all tests in the module. */
public class WindowManagerJetpackTestBase extends ActivityManagerTestBase {

    public static final String EXTRA_EMBED_ACTIVITY = "EmbedActivity";
    public static final String EXTRA_SPLIT_RATIO = "SplitRatio";

    public Instrumentation mInstrumentation;
    public Context mContext;
    public Application mApplication;

    private static final Set<Activity> sResumedActivities = new HashSet<>();
    private static final Set<Activity> sVisibleActivities = new HashSet<>();

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mInstrumentation = getInstrumentation();
        assertNotNull(mInstrumentation);
        mContext = getApplicationContext();
        assertNotNull(mContext);
        mApplication = (Application) mContext.getApplicationContext();
        assertNotNull(mApplication);
        clearLaunchParams();
        // Register activity lifecycle callbacks to know which activities are resumed
        registerActivityLifecycleCallbacks();
    }

    @After
    public void tearDown() throws Throwable {
        sResumedActivities.clear();
        sVisibleActivities.clear();
    }

    protected boolean hasDeviceFeature(final String requiredFeature) {
        return mContext.getPackageManager().hasSystemFeature(requiredFeature);
    }

    /** Assume this device supports rotation */
    protected void assumeSupportsRotation() {
        assumeTrue(doesDeviceSupportRotation());
    }

    /**
     * Rotation support is indicated by explicitly having both landscape and portrait
     * features or not listing either at all.
     */
    protected boolean doesDeviceSupportRotation() {
        final boolean supportsLandscape = hasDeviceFeature(FEATURE_SCREEN_LANDSCAPE);
        final boolean supportsPortrait = hasDeviceFeature(FEATURE_SCREEN_PORTRAIT);
        return (supportsLandscape && supportsPortrait) || (!supportsLandscape && !supportsPortrait);
    }

    protected boolean supportsPip() {
        return hasDeviceFeature(FEATURE_PICTURE_IN_PICTURE);
    }

    public <T extends Activity> T startActivityNewTask(@NonNull Class<T> activityClass) {
        return startActivityNewTask(activityClass, null /* activityId */);
    }

    public <T extends Activity> TestActivityLauncher<T> launcherForNewActivity(
            @NonNull Class<T> activityClass, int launchDisplayId) {
        return launcherForActivityNewTask(activityClass, null /* activityId */,
                false /* isFullScreen */, launchDisplayId);
    }

    /**
     * Starts an {@link Activity} with {@code activityId}, of which the windowing mode and launched
     * display follows system default.
     */
    public <T extends Activity> T startActivityNewTask(@NonNull Class<T> activityClass,
            @Nullable String activityId) {
        return startActivityNewTask(activityClass, activityId, null /* displayId */);
    }

    /**
     * Starts an {@link Activity} on given {@code displayId}, of which the windowing mode follows
     * system default.
     */
    public <T extends Activity> T startActivityNewTask(@NonNull Class<T> activityClass,
            @Nullable String activityId, @Nullable Integer displayId) {
        return startActivityNewTaskInternal(activityClass, activityId, false /* isFullscreen */,
                displayId);
    }

    public <T extends Activity> T startFullScreenActivityNewTask(@NonNull Class<T> activityClass) {
        return startFullScreenActivityNewTask(activityClass, null /* activityId */);
    }

    public <T extends  Activity> T startFullScreenActivityNewTask(@NonNull Class<T> activityClass,
            @Nullable String activityId) {
        return startFullScreenActivityNewTask(activityClass, activityId,
                null /* displayId */);
    }

    /**
     * Starts a fullscreen {@link Activity} on given {@code displayId}.
     */
    public <T extends Activity> T startFullScreenActivityNewTask(@NonNull Class<T> activityClass,
            @Nullable String activityId, @Nullable Integer displayId) {
        return startActivityNewTaskInternal(activityClass, activityId, true /* isFullscreen */,
                displayId);
    }

    public static void waitForOrFail(String message, BooleanSupplier condition) {
        Condition.waitFor(new Condition<>(message, condition)
                .setRetryIntervalMs(500)
                .setRetryLimit(5)
                .setOnFailure(unusedResult -> fail("FAILED because unsatisfied: " + message)));
    }

    /**
     * Starts an activity to front and returns the activity instance.
     *
     * @param activityClass the activity class to launch
     * @param activityId the Activity ID to identify the activity
     * @param isFullScreen {@code true} to launch in fullscreen, or {@code false} to follow the
     *                      system default windowing mode
     * @param displayId the display to launch the activity, or {@code null} to follow system default
     * @return the launch activity instance
     * @param <T> A activity type
     */
    private  <T extends Activity> T startActivityNewTaskInternal(@NonNull Class<T> activityClass,
            @Nullable String activityId, boolean isFullScreen, @Nullable Integer displayId) {
        final T activity = launcherForActivityNewTask(activityClass, activityId, isFullScreen,
                displayId)
                .launch(mInstrumentation);
        if (displayId != null) {
            waitAndAssertActivityStateOnDisplay(activity.getComponentName(), STATE_RESUMED,
                    displayId, "Activity must be launched on display#" + displayId);
        }
        return activity;
    }

    private <T extends Activity> TestActivityLauncher<T> launcherForActivityNewTask(
            @NonNull Class<T> activityClass, @Nullable String activityId, boolean isFullScreen,
            @Nullable Integer launchDisplayId) {
        final int windowingMode = isFullScreen ? WINDOWING_MODE_FULLSCREEN :
                WINDOWING_MODE_UNDEFINED;
        final TestActivityLauncher<T> launcher =
                new TestActivityLauncher<>(mContext, activityClass)
                        .addIntentFlag(FLAG_ACTIVITY_NEW_TASK)
                        .setActivityId(activityId)
                        .setWindowingMode(windowingMode);
        if (launchDisplayId != null) {
            launcher.setLaunchDisplayId(launchDisplayId);
        }
        return launcher;
    }

    /**
     * Start an activity using a component name. Can be used for activities from a different UIDs.
     */
    public static void startActivityNoWait(@NonNull Context context,
            @NonNull ComponentName activityComponent, @NonNull Bundle extras) {
        final Intent intent = new Intent()
                .setClassName(activityComponent.getPackageName(), activityComponent.getClassName())
                .addFlags(FLAG_ACTIVITY_NEW_TASK)
                .putExtras(extras);
        context.startActivity(intent);
    }

    /**
     * Start an activity using a component name on the specified display with
     * {@link FLAG_ACTIVITY_SINGLE_TOP}. Can be used for activities from a different UIDs.
     */
    public static void startActivityOnDisplaySingleTop(@NonNull Context context,
            int displayId, @NonNull ComponentName activityComponent, @NonNull Bundle extras) {
        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(displayId);

        Intent intent = new Intent()
                .addFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_SINGLE_TOP)
                .setComponent(activityComponent)
                .putExtras(extras);
        context.startActivity(intent, options.toBundle());
    }

    /**
     * Starts an instance of {@param activityToLaunchClass} from {@param activityToLaunchFrom}
     * and returns the activity ID from the newly launched class.
     */
    public static <T extends Activity> void startActivityFromActivity(Activity activityToLaunchFrom,
            Class<T> activityToLaunchClass, String newActivityId) {
        Intent intent = new Intent(activityToLaunchFrom, activityToLaunchClass);
        intent.putExtra(KEY_ACTIVITY_ID, newActivityId);
        activityToLaunchFrom.startActivity(intent);
    }

    /**
     * Starts a specified activity class from {@param activityToLaunchFrom}.
     */
    public static void startActivityFromActivity(@NonNull Activity activityToLaunchFrom,
            @NonNull ComponentName activityToLaunchComponent, @NonNull String newActivityId,
            @NonNull Bundle extras) {
        Intent intent = new Intent();
        intent.setClassName(activityToLaunchComponent.getPackageName(),
                activityToLaunchComponent.getClassName());
        intent.putExtra(KEY_ACTIVITY_ID, newActivityId);
        intent.putExtras(extras);
        activityToLaunchFrom.startActivity(intent);
    }

    public static IBinder getActivityWindowToken(Activity activity) {
        return activity.getWindow().getAttributes().token;
    }

    public static void assertHasNonNegativeDimensions(@NonNull Rect rect) {
        assertFalse(rect.width() < 0 || rect.height() < 0);
    }

    public static void assertNotBothDimensionsZero(@NonNull Rect rect) {
        assertFalse(rect.width() == 0 && rect.height() == 0);
    }

    public static Rect getActivityBounds(Activity activity) {
        return activity.getWindowManager().getCurrentWindowMetrics().getBounds();
    }

    public static Rect getMaximumActivityBounds(Activity activity) {
        return activity.getWindowManager().getMaximumWindowMetrics().getBounds();
    }

    /**
     * Move an {@link Activity} into PiP windowing mode. Returns {@code true} if the {@link
     * Activity} entered PiP within the timeout, {@code false} otherwise.
     *
     * @param activity to be moved into PiP
     * @return {@code true} if the activity successfully entered PiP.
     */
    public static boolean enterPipActivityHandlesConfigChanges(TestActivity activity) {
        if (activity.isInPictureInPictureMode()) {
            throw new IllegalStateException("Activity must not be in PiP");
        }
        activity.resetOnConfigurationChangeCounter();
        // Change the orientation
        PictureInPictureParams params = (new PictureInPictureParams.Builder()).build();
        activity.enterPictureInPictureMode(params);
        return activity.waitForConfigurationChange();
    }

    /**
     * Move an {@link Activity} out of PiP windowing mode. Returns {@code true} if the {@link
     * Activity} exited PiP within the timeout, {@code false} otherwise.
     *
     * @param activity to be moved out of PiP
     * @return {@code true} if the activity successfully exited PiP.
     */
    public static boolean exitPipActivityHandlesConfigChanges(TestActivity activity) {
        if (!activity.isInPictureInPictureMode()) {
            throw new IllegalStateException("Activity must be in PiP");
        }
        activity.resetOnConfigurationChangeCounter();
        Intent intent = new Intent(activity, activity.getClass());
        intent.addFlags(FLAG_ACTIVITY_SINGLE_TOP);
        activity.startActivity(intent);
        return activity.waitForConfigurationChange();
    }

    public void setActivityOrientationActivityHandlesOrientationChanges(
            TestActivity activity, int orientation) {
        setActivityOrientation(activity, orientation, true);
    }

    public void setActivityOrientationActivityDoesNotHandleOrientationChanges(
            TestActivity activity, int orientation) {
        setActivityOrientation(activity, orientation, false);
    }

    private void setActivityOrientation(TestActivity activity, int orientation,
            boolean activityHandlesOrientationChanges) {
        // Make sure that the provided orientation is a fixed orientation
        assertTrue(orientation == ORIENTATION_PORTRAIT || orientation == ORIENTATION_LANDSCAPE);
        if (isCloseToSquareDisplay() && !activity.isInMultiWindowMode()) {
            // When the display is close to square, the app config orientation may always be
            // landscape excluding the system insets. Rotate the device away from the current
            // orientation to change the activity/hinge orientation instead of requesting an
            // orientation change to the specified orientation. Rotating the device won't work in
            // multi-window mode, so handle that below.
            // TODO(b/358463936): Checking for square display should ideally be done at the
            // callsites of this method not within this method.
            rotateFromCurrentOrientation(activity);
        } else {
            // Do nothing if the orientation already matches
            if (activity.getResources().getConfiguration().orientation == orientation) {
                return;
            }
            if (activityHandlesOrientationChanges) {
                // Change the orientation
                changeOrientation(activity, orientation, activity.isInMultiWindowMode());
                // Wait for the activity to layout, which will happen after the orientation change
                waitForOrFail("Activity orientation must be updated",
                        () -> activity.getResources().getConfiguration()
                                .orientation == orientation);
            } else {
                TestActivity.resetResumeCounter();
                // Change the orientation
                changeOrientation(activity, orientation, activity.isInMultiWindowMode());
                // The activity will relaunch because it does not handle the orientation change
                assertTrue(TestActivity.waitForOnResume());
                assertTrue(activity.isDestroyed());
                // Since the last activity instance would be destroyed and recreated, check the top
                // resumed activity
                Activity resumedActivity = getTopResumedActivity();
                assertNotNull(resumedActivity);
                // Check that orientation matches
                assertEquals(
                        orientation, resumedActivity.getResources().getConfiguration().orientation);
            }
        }
    }

    private void changeOrientation(TestActivity activity, int requestedOrientation,
            boolean activityIsInMultiWindowMode) {
        if (activityIsInMultiWindowMode) {
            // When the activity is in multi-window mode, rotating the device or requesting
            // an orientation change may not result in the app config orientation changing.
            // In this case, resize activity task to trigger the requested orientation.
            resizeActivityTaskToSwitchOrientation(activity);
        } else {
            activity.setRequestedOrientation(requestedOrientation == ORIENTATION_PORTRAIT
                    ? SCREEN_ORIENTATION_PORTRAIT : SCREEN_ORIENTATION_LANDSCAPE);
        }
    }

    public void resizeActivityTaskToSwitchOrientation(TestActivity activity) {
        ComponentName activityName = activity.getComponentName();
        mWmState.computeState(activityName);
        final Rect boundsBeforeResize = mWmState.getTaskByActivity(activityName).getBounds();
        // To account for the case where the task was square (or close to it) before, scale the
        // width/height larger to ensure a different resulting aspect ratio
        final boolean isPortrait = boundsBeforeResize.width() <= boundsBeforeResize.height();
        final double scaledHeight =
                isPortrait ? boundsBeforeResize.height() * 1.5 : boundsBeforeResize.height();
        final double scaledWidth =
                isPortrait ? boundsBeforeResize.width() : boundsBeforeResize.width() * 1.5;
        // Switch the height and width of the bounds for orientation change
        final int newRight = boundsBeforeResize.left + (int) scaledHeight;
        final int newBottom = boundsBeforeResize.top + (int) scaledWidth;
        resizeActivityTask(activity.getComponentName(),
                boundsBeforeResize.left, boundsBeforeResize.top, newRight, newBottom);
        // Check if resize applied correctly
        mWmState.computeState(activityName);
        waitForOrFail("Activity bounds right must be updated",
                () -> mWmState.getTaskByActivity(activityName).getBounds().right == newRight);
        waitForOrFail("Activity bounds bottom must be updated",
                () -> mWmState.getTaskByActivity(activityName).getBounds().bottom == newBottom);
        final Rect boundsAfterResize = mWmState.getTaskByActivity(activityName).getBounds();
        assertEquals(scaledHeight, boundsAfterResize.width(), 1.0f);
        assertEquals(scaledWidth, boundsAfterResize.height(), 1.0f);
    }

    private void rotateFromCurrentOrientation(TestActivity activity) {
        ComponentName activityName = activity.getComponentName();
        mWmState.computeState(activityName);
        final WindowManagerState.Task task = mWmState.getTaskByActivity(activityName);
        final int displayId = mWmState.getRootTask(task.getRootTaskId()).mDisplayId;
        final RotationSession rotationSession = createManagedRotationSession();
        final int currentDeviceRotation = getDeviceRotation(displayId);
        final int newDeviceRotation =
                currentDeviceRotation == ROTATION_0 || currentDeviceRotation == ROTATION_180 ?
                        ROTATION_90 : ROTATION_0;
        rotationSession.set(newDeviceRotation);
        waitForOrFail("Activity display rotation must be updated",
                () -> activity.getResources().getConfiguration().windowConfiguration
                        .getRotation() == newDeviceRotation);
        assertEquals(newDeviceRotation, getDeviceRotation(displayId));
    }

    /**
     * Returns whether the display rotates to respect activity orientation, which will be false if
     * both portrait activities and landscape activities have the same maximum bounds. If the
     * display rotates for orientation, then the maximum portrait bounds will be a rotated version
     * of the maximum landscape bounds.
     */
    // TODO(b/186631239): ActivityManagerTestBase#ignoresOrientationRequests could disable
    // activity rotation, as a result the display area would remain in the old orientation while
    // the activity orientation changes. We should check the existence of this request before
    // running tests that compare orientation values.
    public static boolean doesDisplayRotateForOrientation(@NonNull Rect portraitMaximumBounds,
            @NonNull Rect landscapeMaximumBounds) {
        return !portraitMaximumBounds.equals(landscapeMaximumBounds);
    }

    public static boolean areExtensionAndSidecarDeviceStateEqual(int extensionDeviceState,
            int sidecarDeviceStatePosture) {
        return (extensionDeviceState == FoldingFeature.STATE_FLAT
                && sidecarDeviceStatePosture == SidecarDeviceState.POSTURE_OPENED)
                || (extensionDeviceState == FoldingFeature.STATE_HALF_OPENED
                && sidecarDeviceStatePosture == SidecarDeviceState.POSTURE_HALF_OPENED);
    }

    private void clearLaunchParams() {
        final ActivityTaskManager atm = mContext.getSystemService(ActivityTaskManager.class);
        SystemUtil.runWithShellPermissionIdentity(() -> {
            atm.clearLaunchParamsForPackages(List.of(mContext.getPackageName()));
        }, Manifest.permission.MANAGE_ACTIVITY_TASKS);
    }

    private void registerActivityLifecycleCallbacks() {
        mApplication.registerActivityLifecycleCallbacks(
                new Application.ActivityLifecycleCallbacks() {
                    @Override
                    public void onActivityCreated(@NonNull Activity activity,
                            @Nullable Bundle savedInstanceState) {
                    }

                    @Override
                    public void onActivityStarted(@NonNull Activity activity) {
                        synchronized (sVisibleActivities) {
                            sVisibleActivities.add(activity);
                        }
                    }

                    @Override
                    public void onActivityResumed(@NonNull Activity activity) {
                        synchronized (sResumedActivities) {
                            sResumedActivities.add(activity);
                        }
                    }

                    @Override
                    public void onActivityPaused(@NonNull Activity activity) {
                        synchronized (sResumedActivities) {
                            sResumedActivities.remove(activity);
                        }
                    }

                    @Override
                    public void onActivityStopped(@NonNull Activity activity) {
                        synchronized (sVisibleActivities) {
                            sVisibleActivities.remove(activity);
                        }
                    }

                    @Override
                    public void onActivitySaveInstanceState(@NonNull Activity activity,
                            @NonNull Bundle outState) {
                    }

                    @Override
                    public void onActivityDestroyed(@NonNull Activity activity) {
                    }
        });
    }

    public static boolean isActivityResumed(Activity activity) {
        synchronized (sResumedActivities) {
            return sResumedActivities.contains(activity);
        }
    }

    public static boolean isActivityVisible(Activity activity) {
        synchronized (sVisibleActivities) {
            return sVisibleActivities.contains(activity);
        }
    }

    @Nullable
    public static TestActivityWithId getResumedActivityById(@NonNull String activityId) {
        return getResumedActivityById(activityId, INVALID_DISPLAY);
    }

    /**
     * Gets the activity with specified {@code activityId} on the display with {@code displayId}.
     */
    @Nullable
    public static TestActivityWithId getResumedActivityById(@NonNull String activityId,
            int displayId) {
        synchronized (sResumedActivities) {
            for (Activity activity : sResumedActivities) {
                if (activity instanceof TestActivityWithId
                        && activityId.equals(((TestActivityWithId) activity).getId())
                        && (displayId == INVALID_DISPLAY || displayId == activity.getDisplayId())) {
                    return (TestActivityWithId) activity;
                }
            }
            return null;
        }
    }

    @Nullable
    public static Activity getTopResumedActivity() {
        synchronized (sResumedActivities) {
            return !sResumedActivities.isEmpty() ? sResumedActivities.iterator().next() : null;
        }
    }
}
