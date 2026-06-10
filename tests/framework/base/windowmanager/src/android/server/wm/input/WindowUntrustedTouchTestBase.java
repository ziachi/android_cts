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

package android.server.wm.input;

import static android.Manifest.permission.ACCESS_SURFACE_FLINGER;
import static android.app.AppOpsManager.MODE_ALLOWED;
import static android.app.AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW;
import static android.server.wm.BuildUtils.HW_TIMEOUT_MULTIPLIER;
import static android.server.wm.UiDeviceUtils.pressUnlockButton;
import static android.server.wm.UiDeviceUtils.pressWakeupButton;
import static android.server.wm.WindowManagerState.STATE_RESUMED;
import static android.server.wm.overlay.Components.UntrustedTouchTestService.EXTRA_DISPLAY_ID;
import static android.view.WindowInsets.Type.navigationBars;
import static android.view.WindowInsets.Type.statusBars;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.Instrumentation;
import android.app.NotificationManager;
import android.app.WindowConfiguration;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Rect;
import android.hardware.input.InputManager;
import android.hardware.input.InputSettings;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.ComponentNameUtils;
import android.server.wm.Condition;
import android.server.wm.CtsWindowInfoUtils;
import android.server.wm.FutureConnection;
import android.server.wm.TouchHelper;
import android.server.wm.WindowManagerState;
import android.server.wm.WindowManagerStateHelper;
import android.server.wm.overlay.Components;
import android.server.wm.shared.BlockingResultReceiver;
import android.server.wm.shared.IUntrustedTouchTestService;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager.LayoutParams;
import android.widget.Toast;

import androidx.annotation.AnimRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.ext.junit.rules.ActivityScenarioRule;

import com.android.compatibility.common.util.AppOpsUtils;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.TestName;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class WindowUntrustedTouchTestBase {
    private static final String TAG = "WindowUntrustedTouchTest";

    /**
     * Opacity (or alpha) is represented as a half-precision floating point number (16b) in surface
     * flinger and the conversion from the single-precision float provided to window manager happens
     * in Layer::setAlpha() by android::half::ftoh(). So, many small non-zero values provided to
     * window manager end up becoming zero due to loss of precision (this is fine as long as the
     * zeros are also used to render the pixels on the screen). So, the minimum opacity possible is
     * actually the minimum positive value representable in half-precision float, which is
     * 0_00001_0000000000, whose equivalent in float is 0_01110001_00000000000000000000000.
     *
     * Note that from float -> half conversion code we don't produce any subnormal half-precision
     * floats during conversion.
     */
    public static final float MIN_POSITIVE_OPACITY =
            Float.intBitsToFloat(0b00111000100000000000000000000000);

    static final float MAXIMUM_OBSCURING_OPACITY = .8f;
    static final long TIMEOUT_MS = 5000L * HW_TIMEOUT_MULTIPLIER;
    static final long MAX_ANIMATION_DURATION_MS = 3000L;
    static final long ANIMATION_DURATION_TOLERANCE_MS = 500L;

    private static final int OVERLAY_COLOR = 0xFFFF0000;
    private static final int ACTIVITY_COLOR = 0xFFFFFFFF;
    static final String APP_A =
            android.server.wm.second.Components.class.getPackage().getName();
    static final String APP_B =
            android.server.wm.third.Components.class.getPackage().getName();
    static final String WINDOW_1 = "W1";
    static final String WINDOW_2 = "W2";

    private static final String[] APPS = {APP_A, APP_B};

    final WindowManagerStateHelper mWmState = new WindowManagerStateHelper();
    private final Map<String, FutureConnection<IUntrustedTouchTestService>> mConnections =
            new ArrayMap<>();
    private Instrumentation mInstrumentation;
    private Context mContext;
    Resources mResources;
    TouchHelper mTouchHelper;
    private Handler mMainHandler;
    InputManager mInputManager;
    private ActivityManager mActivityManager;
    private NotificationManager mNotificationManager;
    TestActivity mActivity;
    View mContainer;
    private Toast mToast;
    float mPreviousTouchOpacity;
    private int mPreviousSawAppOp;
    private final Set<String> mSawWindowsAdded = new ArraySet<>();
    private final AtomicInteger mTouchesReceived = new AtomicInteger(0);

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    @ClassRule
    public static ActivityManagerTestBase.DisableImmersiveModeConfirmationRule
            mDisableImmersiveModeConfirmationRule =
            new ActivityManagerTestBase.DisableImmersiveModeConfirmationRule();

    @Rule
    public TestName testNameRule = new TestName();

    @Rule
    public ActivityScenarioRule<TestActivity> activityRule =
            new ActivityScenarioRule<>(TestActivity.class, createLaunchActivityOptionsBundle());

    @NonNull
    abstract String getAppSelf();

    @Before
    public void setUp() throws Exception {
        activityRule.getScenario().onActivity(activity -> {
            mActivity = activity;
            mContainer = mActivity.view;
            // On ARC++, text toast is fixed on the screen. Its position may overlays the navigation
            // bar. Hide it to ensure the text toast overlays the app. b/191075641
            mContainer.getWindowInsetsController().hide(statusBars() | navigationBars());
            mContainer.setOnTouchListener(this::onTouchEvent);
        });
        mInstrumentation = getInstrumentation();
        mContext = mInstrumentation.getContext();
        mResources = mContext.getResources();
        mTouchHelper = new TouchHelper(mInstrumentation, mWmState);
        mMainHandler = new Handler(Looper.getMainLooper());
        mInputManager = mContext.getSystemService(InputManager.class);
        mActivityManager = mContext.getSystemService(ActivityManager.class);
        mNotificationManager = mContext.getSystemService(NotificationManager.class);

        mPreviousSawAppOp = AppOpsUtils.getOpMode(getAppSelf(), OPSTR_SYSTEM_ALERT_WINDOW);
        AppOpsUtils.setOpMode(getAppSelf(), OPSTR_SYSTEM_ALERT_WINDOW, MODE_ALLOWED);
        mPreviousTouchOpacity = setMaximumObscuringOpacityForTouch(MAXIMUM_OBSCURING_OPACITY);
        SystemUtil.runWithShellPermissionIdentity(
                () -> mNotificationManager.setToastRateLimitingEnabled(false));

        pressWakeupButton();
        pressUnlockButton();
    }

    @After
    public void tearDown() throws Throwable {
        mWmState.waitForAppTransitionIdleOnDisplay(Display.DEFAULT_DISPLAY);
        mTouchesReceived.set(0);
        removeOverlays();
        for (FutureConnection<IUntrustedTouchTestService> connection : mConnections.values()) {
            mContext.unbindService(connection);
        }
        mConnections.clear();
        for (String app : APPS) {
            stopPackage(app);
        }
        SystemUtil.runWithShellPermissionIdentity(
                () -> mNotificationManager.setToastRateLimitingEnabled(true));
        setMaximumObscuringOpacityForTouch(mPreviousTouchOpacity);
        AppOpsUtils.setOpMode(getAppSelf(), OPSTR_SYSTEM_ALERT_WINDOW, mPreviousSawAppOp);
    }

    private boolean onTouchEvent(View view, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            mTouchesReceived.incrementAndGet();
        }
        return true;
    }

    void assertTouchReceived() {
        mInstrumentation.waitForIdleSync();
        assertThat(mTouchesReceived.get()).isEqualTo(1);
        mTouchesReceived.set(0);
    }

    void assertTouchNotReceived() {
        mInstrumentation.waitForIdleSync();
        assertThat(mTouchesReceived.get()).isEqualTo(0);
        mTouchesReceived.set(0);
    }

    void assertAnimationRunning() {
        assertThat(mWmState.getDisplay(Display.DEFAULT_DISPLAY).getAppTransitionState()).isEqualTo(
                WindowManagerStateHelper.APP_STATE_RUNNING);
    }

    void addToastOverlay(String packageName, boolean custom) throws Exception {
        // Making sure there are no toasts currently since we can only check for the presence of
        // *any* toast afterwards and we don't want to be in a situation where this method returned
        // because another toast was being displayed.
        waitForNoToastOverlays();
        if (custom) {
            if (packageName.equals(getAppSelf())) {
                // We add the custom toast here because we already have foreground status due to
                // the activity rule, so no need to start another activity.
                addMyCustomToastOverlay();
            } else {
                // We have to use an activity that will display the toast then finish itself because
                // custom toasts cannot be posted from the background.
                Intent intent = new Intent();
                intent.setComponent(repackage(packageName, Components.ToastActivity.COMPONENT));
                mActivity.startActivity(intent);
            }
        } else {
            getService(packageName).showToast();
        }

        Condition.waitFor(
                new Condition<WindowManagerState.WindowState>(null)
                        .setRetryIntervalMs(100)
                        .setRetryLimit(20)
                        .setResultSupplier(
                                () -> {
                                    mWmState.computeState();
                                    return mWmState.findFirstWindowWithType(
                                            LayoutParams.TYPE_TOAST);
                                })
                        .setResultValidator(
                                toastWindow -> {
                                    String invalidReason = validateToastWindow(toastWindow);
                                    if (invalidReason != null) {
                                        Log.d(TAG, "Failed to validate toast window: " +
                                            invalidReason);
                                        return false;
                                    }
                                    return true;
                                })
                        .setOnFailure(
                                toastWindow ->
                                        fail(
                                                "Toast from app "
                                                        + packageName
                                                        + " did not appear on time or is outside of"
                                                        + " container bounds. Last reason: "
                                                        + validateToastWindow(toastWindow))));
    }

    private String validateToastWindow(WindowManagerState.WindowState toastWindow) {
        if (toastWindow == null) {
            return "toast window not found";
        }
        if (toastWindow.getType() != LayoutParams.TYPE_TOAST) {
            return "window is not a toast window";
        }
        if (!toastWindow.isSurfaceShown()) {
            return "toast surface not shown";
        }
        Rect toastBounds = toastWindow.getFrame();
        int[] viewXY = new int[2];
        mContainer.getLocationOnScreen(viewXY);
        Rect containerRect =
                new Rect(
                        viewXY[0],
                        viewXY[1],
                        viewXY[0] + mContainer.getWidth(),
                        viewXY[1] + mContainer.getHeight());
        if (!containerRect.contains(toastBounds.centerX(), toastBounds.centerY())) {
            return "toast window is outside container bounds";
        }
        // Toast window is valid
        return null;
    }

    private void addMyCustomToastOverlay() {
        mActivity.runOnUiThread(() -> {
            mToast = new Toast(mContext);
            View view = new View(mContext);
            view.setBackgroundColor(OVERLAY_COLOR);
            mToast.setView(view);
            mToast.setGravity(Gravity.FILL, 0, 0);
            mToast.setDuration(Toast.LENGTH_LONG);
            mToast.show();
        });
        mInstrumentation.waitForIdleSync();
    }

    private void removeMyCustomToastOverlay() {
        mActivity.runOnUiThread(() -> {
            if (mToast != null) {
                mToast.cancel();
                mToast = null;
            }
        });
        mInstrumentation.waitForIdleSync();
    }

    private void waitForNoToastOverlays() {
        waitForNoToastOverlays("Toast windows did not hide on time");
    }

    private void waitForNoToastOverlays(String message) {
        if (!mWmState.waitFor("no toast windows",
                state -> state.getMatchingWindowType(LayoutParams.TYPE_TOAST).isEmpty())) {
            fail(message + " Still visible toasts: " + mWmState.getMatchingWindowType(
                    LayoutParams.TYPE_TOAST));
        }
    }

    void addExitAnimationActivity(String packageName) {
        // This activity responds to broadcasts to exit with animations and it's opaque (translucent
        // activities don't honor custom exit animations).
        addActivity(repackage(packageName, Components.ExitAnimationActivity.COMPONENT),
                /* extras */ null, /* options */ null);
    }

    void sendFinishToExitAnimationActivity(String packageName, int exitAnimation) {
        Intent intent = new Intent(Components.ExitAnimationActivityReceiver.ACTION_FINISH);
        intent.setPackage(packageName);
        intent.putExtra(Components.ExitAnimationActivityReceiver.EXTRA_ANIMATION, exitAnimation);
        mContext.sendBroadcast(intent);
    }

    void addAnimatedActivityOverlay(String packageName, boolean touchable,
            @AnimRes int enterAnim, @AnimRes int exitAnim) {
        ConditionVariable animationsStarted = new ConditionVariable(false);
        ActivityOptions options = ActivityOptions.makeCustomAnimation(mContext, enterAnim, exitAnim,
                0, mMainHandler, (t) -> animationsStarted.open(), /* finishedListener */ null);
        // We're testing the opacity coming from the animation here, not the one declared in the
        // activity, so we set its opacity to 1
        addActivityOverlay(packageName, /* opacity */ 1, touchable, options.toBundle());
        animationsStarted.block();
    }

    void addActivityChildWindow(String packageName, String windowSuffix, IBinder token)
            throws Exception {
        String name = getWindowName(packageName, windowSuffix);
        getService(packageName).showActivityChildWindow(name, token);
        if (!mWmState.waitFor("activity child window " + name,
                state -> state.isWindowVisible(name) && state.isWindowSurfaceShown(name))) {
            fail("Activity child window " + name + " did not appear on time");
        }
    }

    void addActivityOverlay(String packageName, float opacity) {
        addActivityOverlay(packageName, opacity, /* touchable */ false, /* options */ null);
    }

    void addActivityOverlay(String packageName, float opacity, boolean allowPassThrough) {
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setAllowPassThroughOnTouchOutside(allowPassThrough);
        addActivityOverlay(packageName, opacity, /* touchable */ false, options.toBundle());
    }

    private void addActivityOverlay(String packageName, float opacity, boolean touchable,
            @Nullable Bundle options) {
        Bundle extras = new Bundle();
        extras.putFloat(Components.OverlayActivity.EXTRA_OPACITY, opacity);
        extras.putBoolean(Components.OverlayActivity.EXTRA_TOUCHABLE, touchable);
        addActivityOverlay(packageName, extras, options);
    }

    void addActivityOverlay(String packageName, float opacity,
            BlockingResultReceiver tokenReceiver) {
        addActivityOverlay(packageName, opacity, tokenReceiver, /* options */ null);
    }

    void addActivityOverlay(String packageName, float opacity,
            BlockingResultReceiver tokenReceiver, @Nullable Bundle options) {
        Bundle extras = new Bundle();
        extras.putFloat(Components.OverlayActivity.EXTRA_OPACITY, opacity);
        extras.putParcelable(Components.OverlayActivity.EXTRA_TOKEN_RECEIVER, tokenReceiver);
        addActivityOverlay(packageName, extras, options);
    }

    private void addActivityOverlay(String packageName, @Nullable Bundle extras,
            @Nullable Bundle options) {
        addActivity(repackage(packageName, Components.OverlayActivity.COMPONENT), extras, options);
    }

    private void addActivity(ComponentName component, @Nullable Bundle extras,
            @Nullable Bundle options) {
        Intent intent = new Intent();
        intent.setComponent(component);
        if (extras != null) {
            intent.putExtras(extras);
        }
        mActivity.startActivity(intent, options);
        String packageName = component.getPackageName();
        String activity = ComponentNameUtils.getActivityName(component);
        if (!mWmState.waitFor("activity window " + activity,
                state -> activity.equals(state.getFocusedActivity())
                        && state.hasActivityState(component, STATE_RESUMED)
                        && state.isWindowSurfaceShown(activity))) {
            fail("Activity from app " + packageName + " did not appear on time");
        }

        // We need to make sure that InputFlinger has populated window info with correct bounds
        // before proceeding.
        // Note that com.android.server.wm.WindowState computes InputWindowHandle's name by
        // concatenating its hash and title.
        WindowManagerState.WindowState focusedWindowState = mWmState.getWindowState(component);
        Rect expectedBounds = mWmState.getActivity(component).getBounds();
        SystemUtil.runWithShellPermissionIdentity(() -> {
            if (!CtsWindowInfoUtils.waitForWindowOnTop(
                    Duration.ofSeconds(5L * HW_TIMEOUT_MULTIPLIER),
                    window -> window.name.contains(focusedWindowState.getToken())
                            && window.name.contains(focusedWindowState.getName()))) {
                fail("Window " + focusedWindowState.getName() + " did not appear in InputFlinger "
                        + "with an expected bounds " + expectedBounds);
            }
        }, ACCESS_SURFACE_FLINGER);
    }

    private void removeActivityOverlays() {
        Intent intent = new Intent(mContext, mActivity.getClass());
        // Will clear any activity on top of it and it will become the new top
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        mActivity.startActivity(intent);
    }

    private void waitForNoActivityOverlays(String message) {
        // Base activity focused means no activities on top
        ComponentName component = mActivity.getComponentName();
        String name = ComponentNameUtils.getActivityName(component);
        if (!mWmState.waitFor("test rule activity focused",
                state -> name.equals(state.getFocusedActivity())
                        && state.hasActivityState(component, STATE_RESUMED))) {
            fail(message);
        }
    }

    void addSawOverlay(String packageName, String windowSuffix, float opacity)
            throws Throwable {
        String name = getWindowName(packageName, windowSuffix);
        int[] viewXY = new int[2];
        mContainer.getLocationOnScreen(viewXY);
        getService(packageName).showSystemAlertWindow(name, opacity, viewXY[0], viewXY[1]);
        mSawWindowsAdded.add(name);
        if (!mWmState.waitFor("saw window " + name,
                state -> state.isWindowVisible(name) && state.isWindowSurfaceShown(name))) {
            fail("Saw window " + name + " did not appear on time");
        }

        // Make sure the System Alert Window is displayed on top of the container activity.
        Rect sawBounds = mWmState.waitForResult("saw bounds",
                state -> state.findFirstWindowWithType(
                        LayoutParams.TYPE_APPLICATION_OVERLAY).getFrame());
        Rect containerRect = new Rect(viewXY[0], viewXY[1], viewXY[0] + mContainer.getWidth(),
                viewXY[1] + mContainer.getHeight());
        assumeTrue("Saw displayed outside of container bounds.",
                containerRect.contains(sawBounds.centerX(), sawBounds.centerY()));
    }

    private void waitForNoSawOverlays(String message) {
        if (!mWmState.waitFor("no SAW windows",
                state -> mSawWindowsAdded.stream().allMatch(w -> !state.isWindowVisible(w)))) {
            fail(message);
        }
        mSawWindowsAdded.clear();
    }

    private void removeOverlays() throws Throwable {
        for (FutureConnection<IUntrustedTouchTestService> connection : mConnections.values()) {
            IUntrustedTouchTestService service = connection.getCurrent();
            if (service != null) {
                service.removeOverlays();
            }
        }
        // We need to stop the app because not every overlay is created via the service (eg.
        // activity overlays and custom toasts)
        for (String app : APPS) {
            stopPackage(app);
        }
        waitForNoSawOverlays("SAWs not removed on time");
        removeActivityOverlays();
        waitForNoActivityOverlays("Activities not removed on time");
        removeMyCustomToastOverlay();
        waitForNoToastOverlays("Toasts not removed on time");
    }

    private void stopPackage(String packageName) {
        SystemUtil.runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(packageName));
    }

    float setMaximumObscuringOpacityForTouch(float opacity) throws Exception {
        return SystemUtil.callWithShellPermissionIdentity(() -> {
            float previous = mInputManager.getMaximumObscuringOpacityForTouch();
            InputSettings.setMaximumObscuringOpacityForTouch(mContext, opacity);
            return previous;
        });
    }

    private IUntrustedTouchTestService getService(String packageName) throws Exception {
        return mConnections.computeIfAbsent(packageName, this::connect).get(TIMEOUT_MS);
    }

    private FutureConnection<IUntrustedTouchTestService> connect(String packageName) {
        FutureConnection<IUntrustedTouchTestService> connection =
                new FutureConnection<>(IUntrustedTouchTestService.Stub::asInterface);
        Intent intent = new Intent();
        intent.putExtra(EXTRA_DISPLAY_ID, mActivity.getDisplay().getDisplayId());
        intent.setComponent(repackage(packageName, Components.UntrustedTouchTestService.COMPONENT));
        assertTrue(mContext.bindService(intent, connection, Context.BIND_AUTO_CREATE));
        return connection;
    }

    private static String getWindowName(String packageName, String windowSuffix) {
        return packageName + "." + windowSuffix;
    }

    private static ComponentName repackage(String packageName, ComponentName baseComponent) {
        return new ComponentName(packageName, baseComponent.getClassName());
    }

    private static Bundle createLaunchActivityOptionsBundle() {
        final ActivityOptions options = ActivityOptions.makeBasic();
        // Launch test in the fullscreen mode with navigation bar hidden,
        // in order to ensure text toast is tappable and overlays above the test app
        // on freeform first devices. b/191075641.
        options.setLaunchWindowingMode(WindowConfiguration.WINDOWING_MODE_FULLSCREEN);
        return options.toBundle();
    }

    public static class TestActivity extends Activity {
        public View view;

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            requestWindowFeature(Window.FEATURE_NO_TITLE);
            view = new View(this);
            view.setBackgroundColor(ACTIVITY_COLOR);
            setContentView(view);
        }
    }
}
