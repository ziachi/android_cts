/*
 * Copyright (C) 2020 The Android Open Source Project
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
 * limitations under the License
 */

package android.server.wm.window;

import static android.server.wm.ComponentNameUtils.getWindowName;
import static android.server.wm.app.Components.HIDE_OVERLAY_WINDOWS_ACTIVITY;
import static android.server.wm.app.Components.HideOverlayWindowsActivity.ACTION;
import static android.server.wm.app.Components.HideOverlayWindowsActivity.MOTION_EVENT_EXTRA;
import static android.server.wm.app.Components.HideOverlayWindowsActivity.PONG;
import static android.server.wm.app.Components.HideOverlayWindowsActivity.REPORT_TOUCH;
import static android.server.wm.app.Components.HideOverlayWindowsActivity.SHOULD_HIDE;
import static android.view.Gravity.LEFT;
import static android.view.Gravity.TOP;
import static android.view.WindowManager.LayoutParams.FIRST_SUB_WINDOW;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeFalse;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.CliIntentExtra;
import android.server.wm.CtsWindowInfoUtils;
import android.server.wm.app.Components;
import android.view.MotionEvent;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.compatibility.common.util.FeatureUtil;
import com.android.compatibility.common.util.SystemUtil;
import com.android.window.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.time.Duration;

/**
 * Build/Install/Run:
 * atest CtsWindowManagerDeviceWindow:HideOverlayWindowsTest
 */
@Presubmit
public class HideOverlayWindowsTest extends ActivityManagerTestBase {

    private static final String POP_UP_WINDOW = "POP_UP_WINDOW";
    private static final String WINDOW_NAME_EXTRA = "window_name";
    private static final String SYSTEM_APPLICATION_OVERLAY_EXTRA = "system_application_overlay";
    private static final Duration WINDOW_ORDER_WAIT_TIMEOUT = Duration.ofSeconds(3);
    private PongReceiver mPongReceiver;
    private TouchReceiver mTouchReceiver;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        mPongReceiver = new PongReceiver();
        mContext.registerReceiver(mPongReceiver, new IntentFilter(PONG), Context.RECEIVER_EXPORTED);
        mTouchReceiver = new TouchReceiver();
        mContext.registerReceiver(mTouchReceiver, new IntentFilter(REPORT_TOUCH),
                Context.RECEIVER_EXPORTED);

        // Wait for any animations to be finished.
        mInstrumentation.getUiAutomation().syncInputTransactions();
    }

    @After
    public void tearDown() throws Exception {
        mContext.unregisterReceiver(mPongReceiver);
        mContext.unregisterReceiver(mTouchReceiver);
    }

    @Test
    public void testApplicationOverlayHiddenWhenRequested() {
        String windowName = "SYSTEM_ALERT_WINDOW";
        ComponentName componentName = new ComponentName(
                mContext, SystemWindowActivity.class);

        SystemUtil.runWithShellPermissionIdentity(() -> {
            launchActivity(componentName,
                    CliIntentExtra.extraString(WINDOW_NAME_EXTRA, windowName));
            mWmState.waitAndAssertWindowSurfaceShown(windowName, true);
        }, Manifest.permission.SYSTEM_ALERT_WINDOW);

        launchActivity(HIDE_OVERLAY_WINDOWS_ACTIVITY);
        mWmState.waitAndAssertWindowSurfaceShown(windowName, true);

        setHideOverlayWindowsAndWaitForPong(true);
        mWmState.waitAndAssertWindowSurfaceShown(windowName, false);

        setHideOverlayWindowsAndWaitForPong(false);
        mWmState.waitAndAssertWindowSurfaceShown(windowName, true);
    }

    @Test
    public void testSystemApplicationOverlayFlagNoEffectWithoutPermission() {
        String windowName = "SYSTEM_ALERT_WINDOW";
        ComponentName componentName = new ComponentName(
                mContext, SystemWindowActivity.class);

        SystemUtil.runWithShellPermissionIdentity(() -> {
            launchActivity(componentName,
                    CliIntentExtra.extraString(WINDOW_NAME_EXTRA, windowName),
                    CliIntentExtra.extraBool(SYSTEM_APPLICATION_OVERLAY_EXTRA, true));
            mWmState.waitAndAssertWindowSurfaceShown(windowName, true);
        }, Manifest.permission.SYSTEM_ALERT_WINDOW);

        launchActivity(HIDE_OVERLAY_WINDOWS_ACTIVITY);
        mWmState.waitAndAssertWindowSurfaceShown(windowName, true);

        setHideOverlayWindowsAndWaitForPong(true);
        mWmState.waitAndAssertWindowSurfaceShown(windowName, false);

        setHideOverlayWindowsAndWaitForPong(false);
        mWmState.waitAndAssertWindowSurfaceShown(windowName, true);
    }

    @Test
    public void testInternalSystemApplicationOverlaysNotHidden() {
        String windowName = "INTERNAL_SYSTEM_WINDOW";
        ComponentName componentName = new ComponentName(
                mContext, InternalSystemWindowActivity.class);

        SystemUtil.runWithShellPermissionIdentity(() -> {
            launchActivity(componentName,
                    CliIntentExtra.extraString(WINDOW_NAME_EXTRA, windowName));
            mWmState.waitAndAssertWindowSurfaceShown(windowName, true);
        }, Manifest.permission.INTERNAL_SYSTEM_WINDOW);

        launchActivity(HIDE_OVERLAY_WINDOWS_ACTIVITY);
        setHideOverlayWindowsAndWaitForPong(true);
        mWmState.waitAndAssertWindowSurfaceShown(windowName, true);
    }

    @Test
    public void testSystemApplicationOverlaysNotHidden() {
        String windowName = "SYSTEM_APPLICATION_OVERLAY";
        ComponentName componentName = new ComponentName(
                mContext, SystemApplicationOverlayActivity.class);
        SystemUtil.runWithShellPermissionIdentity(() -> {
            launchActivity(componentName,
                    CliIntentExtra.extraString(WINDOW_NAME_EXTRA, windowName),
                    CliIntentExtra.extraBool(SYSTEM_APPLICATION_OVERLAY_EXTRA, true));
            mWmState.waitAndAssertWindowSurfaceShown(windowName, true);
        }, Manifest.permission.SYSTEM_APPLICATION_OVERLAY);

        launchActivity(HIDE_OVERLAY_WINDOWS_ACTIVITY);
        setHideOverlayWindowsAndWaitForPong(true);
        mWmState.waitAndAssertWindowSurfaceShown(windowName, true);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_FIX_HIDE_OVERLAY_API)
    public void testHideOverlayWindowsFromSameUidNotHidden() {
        String windowName = "SELF_APPLICATION_OVERLAY";
        ComponentName overlayComponent =
                new ComponentName(mContext, SystemApplicationOverlayActivity.class);
        ComponentName baseComponent = new ComponentName(mContext, SameUidActivity.class);

        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    launchActivity(
                            overlayComponent,
                            CliIntentExtra.extraString(WINDOW_NAME_EXTRA, windowName));
                    mWmState.waitAndAssertWindowSurfaceShown(windowName, true);
                },
                Manifest.permission.SYSTEM_ALERT_WINDOW);

        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    launchActivity(baseComponent);
                    // Set hide overlay window and wait for PONG.
                    Intent intent = new Intent(ACTION);
                    intent.putExtra(
                            Components.HideOverlayWindowsActivity.SHOULD_HIDE,
                            /* shouldHide= */ true);
                    mContext.sendBroadcast(intent);
                    mPongReceiver.waitForPong();
                },
                Manifest.permission.HIDE_OVERLAY_WINDOWS);

        mWmState.waitAndAssertWindowSurfaceShown(windowName, true);
    }

    @Test
    public void testSystemApplicationOverlayHiddenWithoutFlag() {
        String windowName = "SYSTEM_APPLICATION_OVERLAY";
        ComponentName componentName = new ComponentName(
                mContext, SystemApplicationOverlayActivity.class);
        SystemUtil.runWithShellPermissionIdentity(() -> {
            launchActivity(componentName,
                    CliIntentExtra.extraString(WINDOW_NAME_EXTRA, windowName));
            mWmState.waitAndAssertWindowSurfaceShown(windowName, true);
        }, Manifest.permission.SYSTEM_APPLICATION_OVERLAY);

        launchActivity(HIDE_OVERLAY_WINDOWS_ACTIVITY);
        setHideOverlayWindowsAndWaitForPong(true);
        mWmState.waitAndAssertWindowSurfaceShown(windowName, false);
    }

    @Test
    public void testSystemApplicationOverlayAllowsTouchWithoutObscured() {
        String windowName = "SYSTEM_APPLICATION_OVERLAY";
        ComponentName componentName = new ComponentName(
                mContext, SystemApplicationOverlayActivity.class);
        SystemUtil.runWithShellPermissionIdentity(() -> {
            launchActivity(componentName,
                    CliIntentExtra.extraString(WINDOW_NAME_EXTRA, windowName),
                    CliIntentExtra.extraBool(SYSTEM_APPLICATION_OVERLAY_EXTRA, true));
            mWmState.waitAndAssertWindowSurfaceShown(windowName, true);
        }, Manifest.permission.SYSTEM_APPLICATION_OVERLAY);
        Rect appOverlayActivityFrame = mWmState.getWindowState(componentName).getFrame();

        launchActivity(HIDE_OVERLAY_WINDOWS_ACTIVITY);
        setHideOverlayWindowsAndWaitForPong(true);
        mWmState.waitAndAssertWindowSurfaceShown(windowName, true);

        MotionEvent motionEvent = touchCenterOfBoundsAndWaitForMotionEvent(appOverlayActivityFrame);

        assertThat(
                motionEvent.getFlags() & MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED).isEqualTo(
                0);
    }

    @Test
    public void testApplicationOverlay_touchIsObscuredWithoutCorrectPermission() throws Throwable {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        String windowName = "SYSTEM_APPLICATION_OVERLAY";
        ComponentName componentName = new ComponentName(
                mContext, SystemWindowActivity.class);
        SystemUtil.runWithShellPermissionIdentity(() -> {
            launchActivity(componentName,
                    CliIntentExtra.extraString(WINDOW_NAME_EXTRA, windowName),
                    CliIntentExtra.extraBool(SYSTEM_APPLICATION_OVERLAY_EXTRA, true));
            mWmState.waitAndAssertWindowSurfaceShown(windowName, true);
        }, Manifest.permission.SYSTEM_ALERT_WINDOW);

        launchActivityInFullscreen(HIDE_OVERLAY_WINDOWS_ACTIVITY);
        setHideOverlayWindowsAndWaitForPong(false);
        mWmState.waitAndAssertWindowSurfaceShown(windowName, true);

        // Wait for HIDE_OVERLAY_WINDOWS_ACTIVITY to be right behind SYSTEM_APPLICATION_OVERLAY
        // without anything in between before tapping.
        // The reason this wait is needed is that there could be a splash screen coming in between
        // them and fully obscuring HIDE_OVERLAY_WINDOWS_ACTIVITY for a moment. If we don't wait
        // for the splash screen to go away before tapping, then a full occlusion would be detected
        // leading to MotionEvent.FLAG_WINDOW_IS_OBSCURED set in the MotionEvent.
        assertThat(
                        CtsWindowInfoUtils.waitForNthWindowFromTop(
                                WINDOW_ORDER_WAIT_TIMEOUT,
                                windowInfo ->
                                        windowInfo.name.endsWith(
                                                getWindowName(HIDE_OVERLAY_WINDOWS_ACTIVITY)),
                                1))
                .isTrue();

        // Tap the center of HIDE_OVERLAY_WINDOWS_ACTIVITY.
        Rect taskBounds = mWmState.getTaskByActivity(HIDE_OVERLAY_WINDOWS_ACTIVITY).getBounds();
        MotionEvent motionEvent = touchCenterOfBoundsAndWaitForMotionEvent(taskBounds);
        assertThat(
                motionEvent.getFlags() & MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED).isEqualTo(
                MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED);
    }

    @Test
    public void testApplicationOverlayWithPopUpHiddenWhenRequested() {
        String windowName = "SYSTEM_ALERT_WINDOW";
        ComponentName componentName = new ComponentName(
                mContext, SystemWindowActivity.class);

        SystemUtil.runWithShellPermissionIdentity(() -> {
            launchActivity(componentName,
                    CliIntentExtra.extraString(WINDOW_NAME_EXTRA, windowName));
            mWmState.waitAndAssertWindowSurfaceShown(windowName, true);
        }, Manifest.permission.SYSTEM_ALERT_WINDOW);

        SystemUtil.runWithShellPermissionIdentity(() -> {
            launchActivity(componentName,
                    CliIntentExtra.extraString(WINDOW_NAME_EXTRA, POP_UP_WINDOW));
            mWmState.waitAndAssertWindowSurfaceShown(POP_UP_WINDOW, true);
        }, Manifest.permission.SYSTEM_ALERT_WINDOW);

        launchActivity(HIDE_OVERLAY_WINDOWS_ACTIVITY);
        mWmState.waitAndAssertWindowSurfaceShown(POP_UP_WINDOW, true);
        mWmState.waitAndAssertWindowSurfaceShown(windowName, true);

        setHideOverlayWindowsAndWaitForPong(true);
        mWmState.waitAndAssertWindowSurfaceShown(windowName, false);
        mWmState.waitAndAssertWindowSurfaceShown(POP_UP_WINDOW, false);

        setHideOverlayWindowsAndWaitForPong(false);
        mWmState.waitAndAssertWindowSurfaceShown(windowName, true);
        mWmState.waitAndAssertWindowSurfaceShown(POP_UP_WINDOW, true);
    }

    private MotionEvent touchCenterOfBoundsAndWaitForMotionEvent(Rect bounds) {
        mTouchHelper.tapOnCenter(bounds, getMainDisplayId());
        return mTouchReceiver.getMotionEvent();
    }

    void setHideOverlayWindowsAndWaitForPong(boolean hide) {
        Intent intent = new Intent(ACTION);
        intent.putExtra(Components.HideOverlayWindowsActivity.SHOULD_HIDE, hide);
        mContext.sendBroadcast(intent);
        mPongReceiver.waitForPong();
    }

    public static class SameUidActivity extends Activity {
        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            registerReceiver(
                    mBroadcastReceiver, new IntentFilter(ACTION), Context.RECEIVER_EXPORTED);
        }

        BroadcastReceiver mBroadcastReceiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if (ACTION.equals(intent.getAction())) {
                            boolean shouldHide = intent.getBooleanExtra(SHOULD_HIDE, false);
                            getWindow().setHideOverlayWindows(shouldHide);
                            sendBroadcast(new Intent(PONG));
                        }
                    }
                };
    }

    public static class BaseSystemWindowActivity extends Activity {

        TextView mTextView;
        TextView mSubWindow;

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            String windowName = getIntent().getStringExtra(WINDOW_NAME_EXTRA);

            Rect activityBounds = new Rect();
            getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(
                    new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            // Remove the listener to avoid multiple calls
                            getWindow().getDecorView().getViewTreeObserver()
                                    .removeOnGlobalLayoutListener(this);
                            getWindow().getDecorView().getBoundsOnScreen(activityBounds, true);

                            WindowManager.LayoutParams params =
                                    new WindowManager.LayoutParams(TYPE_APPLICATION_OVERLAY,
                                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
                            params.x = activityBounds.left;
                            params.y = activityBounds.top;
                            params.width = (activityBounds.right - activityBounds.left) / 3;
                            params.height = (activityBounds.bottom - activityBounds.top) / 3;
                            params.gravity = TOP | LEFT;
                            params.setTitle(windowName);
                            params.setSystemApplicationOverlay(
                                    getIntent().getBooleanExtra(SYSTEM_APPLICATION_OVERLAY_EXTRA,
                                            false));

                            mTextView = new TextView(BaseSystemWindowActivity.this);
                            mTextView.setText(windowName + "   type=" + TYPE_APPLICATION_OVERLAY);
                            mTextView.setBackgroundColor(Color.GREEN);

                            getWindowManager().addView(mTextView, params);
                        }
                    });
        }

        @Override
        protected void onNewIntent(Intent intent) {
            super.onNewIntent(intent);
            if (POP_UP_WINDOW.equals(intent.getStringExtra(WINDOW_NAME_EXTRA))) {
                final Point size = new Point();
                getDisplay().getRealSize(size);

                WindowManager.LayoutParams params =
                        new WindowManager.LayoutParams(FIRST_SUB_WINDOW,
                                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
                params.width = size.x / 3;
                params.height = size.y / 6;
                params.gravity = TOP | LEFT;
                params.setTitle(POP_UP_WINDOW);
                params.token = mTextView.getWindowToken();

                mSubWindow = new TextView(this);
                mSubWindow.setText(POP_UP_WINDOW + "   type=" + FIRST_SUB_WINDOW);
                mSubWindow.setBackgroundColor(Color.RED);

                getWindowManager().addView(mSubWindow, params);
            }
        }

        @Override
        protected void onDestroy() {
            super.onDestroy();
            if (mSubWindow != null) {
                getWindowManager().removeView(mSubWindow);
            }
            getWindowManager().removeView(mTextView);
        }
    }

    // These activities are running the same code, but in different processes to ensure that they
    // each create their own WindowSession, using the correct permissions. If they are run in the
    // same process WindowSession is cached and might end up not matching the permissions set up
    // with adoptShellPermissions
    public static class InternalSystemWindowActivity extends BaseSystemWindowActivity {}
    public static class SystemApplicationOverlayActivity extends BaseSystemWindowActivity {}
    public static class SystemWindowActivity extends BaseSystemWindowActivity {}

    private static class PongReceiver extends BroadcastReceiver {

        volatile ConditionVariable mConditionVariable = new ConditionVariable();

        @Override
        public void onReceive(Context context, Intent intent) {
            mConditionVariable.open();
        }

        public void waitForPong() {
            assertThat(mConditionVariable.block(10000L)).isTrue();
            mConditionVariable = new ConditionVariable();
        }
    }

    private static class TouchReceiver extends BroadcastReceiver {

        volatile ConditionVariable mConditionVariable = new ConditionVariable();
        MotionEvent mMotionEvent;

        @Override
        public void onReceive(Context context, Intent intent) {
            mMotionEvent = intent.getParcelableExtra(MOTION_EVENT_EXTRA, MotionEvent.class);
            mConditionVariable.open();
        }

        public MotionEvent getMotionEvent() {
            assertThat(mConditionVariable.block(10000L)).isTrue();
            mConditionVariable = new ConditionVariable();
            return mMotionEvent;
        }
    }

}
