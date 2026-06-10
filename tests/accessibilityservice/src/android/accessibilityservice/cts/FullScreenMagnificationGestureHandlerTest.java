/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.accessibilityservice.cts;

import static android.accessibilityservice.cts.utils.AsyncUtils.await;
import static android.accessibilityservice.cts.utils.AsyncUtils.waitOn;
import static android.accessibilityservice.cts.utils.CtsTestUtils.DEFAULT_GLOBAL_TIMEOUT_MS;
import static android.accessibilityservice.cts.utils.CtsTestUtils.DEFAULT_IDLE_TIMEOUT_MS;
import static android.accessibilityservice.cts.utils.CtsTestUtils.isAutomotive;
import static android.accessibilityservice.cts.utils.GestureUtils.add;
import static android.accessibilityservice.cts.utils.GestureUtils.click;
import static android.accessibilityservice.cts.utils.GestureUtils.dispatchGesture;
import static android.accessibilityservice.cts.utils.GestureUtils.distance;
import static android.accessibilityservice.cts.utils.GestureUtils.doubleTap;
import static android.accessibilityservice.cts.utils.GestureUtils.drag;
import static android.accessibilityservice.cts.utils.GestureUtils.endTimeOf;
import static android.accessibilityservice.cts.utils.GestureUtils.lastPointOf;
import static android.accessibilityservice.cts.utils.GestureUtils.longClick;
import static android.accessibilityservice.cts.utils.GestureUtils.path;
import static android.accessibilityservice.cts.utils.GestureUtils.pointerDown;
import static android.accessibilityservice.cts.utils.GestureUtils.pointerUp;
import static android.accessibilityservice.cts.utils.GestureUtils.startingAt;
import static android.accessibilityservice.cts.utils.GestureUtils.swipe;
import static android.accessibilityservice.cts.utils.GestureUtils.tripleTap;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_UP;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.accessibility.cts.common.AccessibilityDumpOnFailureRule;
import android.accessibility.cts.common.InstrumentedAccessibilityService;
import android.accessibility.cts.common.InstrumentedAccessibilityServiceTestRule;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.accessibilityservice.GestureDescription.StrokeDescription;
import android.accessibilityservice.MagnificationConfig;
import android.accessibilityservice.cts.AccessibilityGestureDispatchTest.GestureDispatchActivity;
import android.accessibilityservice.cts.utils.EventCapturingMotionEventListener;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.pm.PackageManager;
import android.graphics.Insets;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.Region;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;
import android.provider.Settings;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.WindowMetrics;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.GestureNavSwitchHelper;
import com.android.compatibility.common.util.SettingsStateChangerRule;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

/**
 * Class for testing
 * {@link com.android.server.accessibility.magnification.FullScreenMagnificationGestureHandler}.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull
@CddTest(requirements = {"3.10/C-1-1,C-1-2"})
@Presubmit
public class FullScreenMagnificationGestureHandlerTest {

    private static final double MIN_SCALE = 1.2;

    // Taps with interval over than this timeout should not be detected as contiguous taps.
    private static final int CONTIGUOUS_TAPS_DETECT_TIMEOUT = 400;

    // See Settings.Secure.ACCESSIBILITY_SINGLE_FINGER_PANNING_ENABLED
    private static final String ACCESSIBILITY_SINGLE_FINGER_PANNING_ENABLED =
            "accessibility_single_finger_panning_enabled";

    private static UiAutomation sUiAutomation;

    private boolean mIsGestureNavigationMode;
    private InstrumentedAccessibilityService mService;
    private Instrumentation mInstrumentation;
    private EventCapturingMotionEventListener mTouchListener =
            new EventCapturingMotionEventListener();

    boolean mCurrentActivated = false;
    float mCurrentScale = 1f;
    PointF mCurrentZoomCenter = null;
    PointF mNavigationBarTapLocation;
    PointF mTapLocation;
    PointF mTapLocation2;
    float mPan;

    private final Object mZoomLock = new Object();

    private final ActivityScenarioRule<GestureDispatchActivity> mActivityRule =
            new ActivityScenarioRule<>(GestureDispatchActivity.class);

    // Disable Single Finger Panning to test the original behavior
    // TODO(b/342089257): add test cases for single finger panning scenario
    private final SettingsStateChangerRule mSingleFingerPanningSettingRule =
            new SettingsStateChangerRule(
                    InstrumentationRegistry.getInstrumentation().getTargetContext(),
                    ACCESSIBILITY_SINGLE_FINGER_PANNING_ENABLED,
                    "0");

    private final SettingsStateChangerRule mMagnificationCapabilitySettingRule =
            new SettingsStateChangerRule(
                    InstrumentationRegistry.getInstrumentation().getTargetContext(),
                    Settings.Secure.ACCESSIBILITY_MAGNIFICATION_CAPABILITY,
                    Integer.toString(Settings.Secure.ACCESSIBILITY_MAGNIFICATION_MODE_FULLSCREEN));
    private final SettingsStateChangerRule mMagnificationModeSettingRule =
            new SettingsStateChangerRule(
                    InstrumentationRegistry.getInstrumentation().getTargetContext(),
                    Settings.Secure.ACCESSIBILITY_MAGNIFICATION_MODE,
                    Integer.toString(Settings.Secure.ACCESSIBILITY_MAGNIFICATION_MODE_FULLSCREEN));
    private final SettingsStateChangerRule mMagnificationEnabledSettingRule =
            new SettingsStateChangerRule(
                    InstrumentationRegistry.getInstrumentation().getTargetContext(),
                    Settings.Secure.ACCESSIBILITY_DISPLAY_MAGNIFICATION_ENABLED,
                    "1");

    private final InstrumentedAccessibilityServiceTestRule<StubMagnificationAccessibilityService>
            mServiceRule =
                    new InstrumentedAccessibilityServiceTestRule<>(
                            StubMagnificationAccessibilityService.class, false);

    private final AccessibilityDumpOnFailureRule mDumpOnFailureRule =
            new AccessibilityDumpOnFailureRule();

    @Rule
    public final RuleChain mRuleChain =
            RuleChain.outerRule(mActivityRule)
                    .around(mSingleFingerPanningSettingRule)
                    .around(mMagnificationCapabilitySettingRule)
                    .around(mMagnificationModeSettingRule)
                    .around(mMagnificationEnabledSettingRule)
                    .around(mServiceRule)
                    .around(mDumpOnFailureRule);

    @BeforeClass
    public static void oneTimeSetUp() {
        sUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
    }

    @Before
    public void setUp() throws Exception {
        sUiAutomation.waitForIdle(DEFAULT_IDLE_TIMEOUT_MS, DEFAULT_GLOBAL_TIMEOUT_MS);
        mIsGestureNavigationMode = new GestureNavSwitchHelper().isGestureMode();
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        PackageManager pm = mInstrumentation.getContext().getPackageManager();

        boolean hasTouchscreen = pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
                || pm.hasSystemFeature(PackageManager.FEATURE_FAKETOUCH);
        assumeTrue(hasTouchscreen);
        assumeFalse("Magnification is not supported on Automotive.",
                isAutomotive(mInstrumentation.getTargetContext()));

        mService = mServiceRule.enableService();
        mService.getMagnificationController().addListener(
                new AccessibilityService.MagnificationController.OnMagnificationChangedListener() {
                    @Override
                    public void onMagnificationChanged(
                            @NonNull AccessibilityService.MagnificationController controller,
                            @NonNull Region region, float scale, float centerX, float centerY) {
                        // do nothing
                    }

                    @Override
                    public void onMagnificationChanged(
                            @NonNull AccessibilityService.MagnificationController controller,
                            @NonNull Region region, @NonNull MagnificationConfig config) {
                        mCurrentActivated = config.isActivated();
                        mCurrentScale = config.getScale();
                        mCurrentZoomCenter = isZoomed()
                                ? new PointF(config.getCenterX(), config.getCenterY())
                                : null;

                        synchronized (mZoomLock) {
                            mZoomLock.notifyAll();
                        }
                    }
                });

        mActivityRule
                .getScenario()
                .moveToState(Lifecycle.State.RESUMED)
                .onActivity(
                        activity -> {
                            TextView view = activity.findViewById(R.id.full_screen_text_view);
                            WindowMetrics windowMetrics =
                                    activity.getWindow()
                                            .getWindowManager()
                                            .getCurrentWindowMetrics();
                            Rect windowBounds = windowMetrics.getBounds();
                            WindowInsets insets = windowMetrics.getWindowInsets();
                            Insets navBarInsets =
                                    insets.getInsetsIgnoringVisibility(
                                            WindowInsets.Type.navigationBars());
                            int navBarCenterY = windowBounds.bottom - (navBarInsets.bottom / 2);

                            view.setOnTouchListener(mTouchListener);
                            int[] xy = new int[2];
                            view.getLocationOnScreen(xy);
                            mNavigationBarTapLocation =
                                    new PointF(xy[0] + view.getWidth() / 2, navBarCenterY);
                            mTapLocation =
                                    new PointF(
                                            xy[0] + view.getWidth() / 2,
                                            xy[1] + view.getHeight() / 2);
                            mTapLocation2 = add(mTapLocation, 31, 29);
                            mPan = view.getWidth() / 4;
                        });
    }

    @After
    public void tearDown() throws Exception {
        if (isActivated()) {
            // Sleep a timeout to prevent the triple tap events be detected as contiguous gesture
            // events with previous testing gestures.
            SystemClock.sleep(CONTIGUOUS_TAPS_DETECT_TIMEOUT);
            setZoomByTripleTapping(false);
        }
    }

    @Test
    public void testZoomOnOff() {
        assertFalse(isZoomed());

        assertGesturesPropagateToView();
        assertFalse(isZoomed());

        setZoomByTripleTapping(true);

        assertGesturesPropagateToView();
        assertTrue(isZoomed());

        setZoomByTripleTapping(false);
    }

    @Test
    public void testViewportDragging() {
        assertFalse(isZoomed());
        tripleTapAndDragViewport();
        waitOn(mZoomLock, () -> !isZoomed());

        setZoomByTripleTapping(true);
        tripleTapAndDragViewport();
        assertTrue(isZoomed());

        setZoomByTripleTapping(false);
    }

    @Test
    public void testPanning() {
        //The minimum movement to transit to panningState.
        final float minSwipeDistance = ViewConfiguration.get(
                mInstrumentation.getContext()).getScaledTouchSlop() + 1;
        final boolean screenBigEnough = mPan > minSwipeDistance;
        assumeTrue(screenBigEnough);
        assertFalse(isZoomed());

        setZoomByTripleTapping(true);
        final PointF oldCenter = mCurrentZoomCenter;

        // Dispatch a swipe gesture composed of two consecutive gestures; the first one to transit
        // to panningState, and the second one to moves the window.
        final GestureDescription.Builder builder1 = new GestureDescription.Builder();
        final GestureDescription.Builder builder2 = new GestureDescription.Builder();

        final long totalDuration = ViewConfiguration.getTapTimeout();
        final long firstDuration = (long) (totalDuration * (minSwipeDistance / mPan));

        for (final PointF startPoint : new PointF[]{mTapLocation, mTapLocation2}) {
            final PointF midPoint = add(startPoint, -minSwipeDistance, 0);
            final PointF endPoint = add(startPoint, -mPan, 0);
            final StrokeDescription firstStroke = new StrokeDescription(path(startPoint, midPoint),
                    0, firstDuration, true);
            final StrokeDescription secondStroke = firstStroke.continueStroke(
                    path(midPoint, endPoint), 0, totalDuration - firstDuration, false);
            builder1.addStroke(firstStroke);
            builder2.addStroke(secondStroke);
        }

        dispatch(builder1.build());
        dispatch(builder2.build());

        waitOn(mZoomLock,
                () -> (mCurrentZoomCenter.x - oldCenter.x
                        >= (mPan - minSwipeDistance) / mCurrentScale * 0.9));

        setZoomByTripleTapping(false);
    }

    @Test
    public void testTapNavigationBar_zooming_keepZooming() {
        // Only test when device is in gesture navigation mode.
        assumeTrue(mIsGestureNavigationMode);

        assertFalse(isZoomed());

        assertGesturesPropagateToView();
        setZoomByTripleTapping(true);

        // One tap on navigation bar would trigger window transition events, but the events should
        // not cause the magnification zooming out.
        dispatch(click(mNavigationBarTapLocation));
        assertTrue(isZoomed());
    }

    @Test
    public void testSwipeUpFromNavigationBar_zooming_zoomOut() throws Exception {
        // Only test when device is in gesture navigation mode.
        assumeTrue(mIsGestureNavigationMode);

        assertFalse(isZoomed());

        assertGesturesPropagateToView();
        setZoomByTripleTapping(true);

        // Swipe up from navigation bar would show the recents app or back to home screen, and the
        // window transition events will cause the magnification zooming out.
        dispatch(swipe(mNavigationBarTapLocation, mTapLocation));
        waitOn(mZoomLock, () -> !isZoomed());
    }

    private void setZoomByTripleTapping(boolean desiredActivatedState) {
        if (isActivated() == desiredActivatedState) return;
        // Clear the cached events in mTouchListener to prevent the already cached events making
        // the assertNonePropagated fail.
        mTouchListener.clear();
        dispatch(tripleTap(mTapLocation));
        waitOn(mZoomLock, () -> {
            if (desiredActivatedState) {
                // Since there may be a case the magnification is activated but not zooming in,
                // we also need to check isZoomed here.
                return isActivated() && isZoomed();
            } else {
                return !isActivated();
            }
        });
        mTouchListener.assertNonePropagated();
    }

    private void tripleTapAndDragViewport() {
        StrokeDescription down = tripleTapAndHold();

        PointF oldCenter = mCurrentZoomCenter;

        StrokeDescription drag = drag(down, add(lastPointOf(down), mPan, 0f));
        dispatch(drag);
        waitOn(mZoomLock, () -> distance(mCurrentZoomCenter, oldCenter) >= mPan / 5);
        assertTrue(isZoomed());
        mTouchListener.assertNonePropagated();

        dispatch(pointerUp(drag));
        mTouchListener.assertNonePropagated();
    }

    private StrokeDescription tripleTapAndHold() {
        StrokeDescription tap1 = click(mTapLocation);
        StrokeDescription tap2 = startingAt(endTimeOf(tap1) + 20, click(mTapLocation2));
        StrokeDescription down = startingAt(endTimeOf(tap2) + 20, pointerDown(mTapLocation));
        dispatch(tap1, tap2, down);
        waitOn(mZoomLock, () -> isZoomed());
        return down;
    }

    private void assertGesturesPropagateToView() {
        dispatch(click(mTapLocation));
        mTouchListener.assertPropagated(ACTION_DOWN, ACTION_UP);

        dispatch(longClick(mTapLocation));
        mTouchListener.assertPropagated(ACTION_DOWN, ACTION_UP);

        dispatch(doubleTap(mTapLocation));
        mTouchListener.assertPropagated(ACTION_DOWN, ACTION_UP, ACTION_DOWN, ACTION_UP);

        // Smaller display devices does not have much screen space in Zoomed state
        PackageManager pm = mInstrumentation.getTargetContext().getPackageManager();
        int y = (pm.hasSystemFeature(pm.FEATURE_WATCH)) ? 5 : 29;
        dispatch(swipe(
                mTapLocation,
                add(mTapLocation, 0, y)));
        mTouchListener.assertPropagated(ACTION_DOWN, ACTION_MOVE, ACTION_UP);
    }

    private boolean isActivated() {
        return mCurrentActivated;
    }

    private boolean isZoomed() {
        return mCurrentScale >= MIN_SCALE;
    }

    public void dispatch(StrokeDescription firstStroke, StrokeDescription... rest) {
        GestureDescription.Builder builder =
                new GestureDescription.Builder().addStroke(firstStroke);
        for (StrokeDescription stroke : rest) {
            builder.addStroke(stroke);
        }
        dispatch(builder.build());
    }

    public void dispatch(GestureDescription gesture) {
        await(dispatchGesture(mService, gesture));
    }
}
