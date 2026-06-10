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

package android.accessibilityservice.cts;

import static android.accessibilityservice.cts.utils.AsyncUtils.await;
import static android.accessibilityservice.cts.utils.CtsTestUtils.DEFAULT_GLOBAL_TIMEOUT_MS;
import static android.accessibilityservice.cts.utils.CtsTestUtils.DEFAULT_IDLE_TIMEOUT_MS;
import static android.accessibilityservice.cts.utils.GestureUtils.add;
import static android.accessibilityservice.cts.utils.GestureUtils.click;
import static android.accessibilityservice.cts.utils.GestureUtils.dispatchGesture;
import static android.accessibilityservice.cts.utils.GestureUtils.doubleTap;
import static android.accessibilityservice.cts.utils.GestureUtils.interruptedSwipe;
import static android.accessibilityservice.cts.utils.GestureUtils.swipe;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_HOVER_ENTER;
import static android.view.MotionEvent.ACTION_HOVER_EXIT;
import static android.view.MotionEvent.ACTION_HOVER_MOVE;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_POINTER_DOWN;
import static android.view.MotionEvent.ACTION_POINTER_UP;
import static android.view.MotionEvent.ACTION_UP;
import static android.view.accessibility.AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_END;
import static android.view.accessibility.AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START;
import static android.view.accessibility.AccessibilityEvent.TYPE_TOUCH_INTERACTION_END;
import static android.view.accessibility.AccessibilityEvent.TYPE_TOUCH_INTERACTION_START;
import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.accessibility.cts.common.AccessibilityDumpOnFailureRule;
import android.accessibility.cts.common.InstrumentedAccessibilityService;
import android.accessibility.cts.common.InstrumentedAccessibilityServiceTestRule;
import android.accessibilityservice.GestureDescription;
import android.accessibilityservice.GestureDescription.StrokeDescription;
import android.accessibilityservice.TouchInteractionController;
import android.accessibilityservice.cts.AccessibilityGestureDispatchTest.GestureDispatchActivity;
import android.accessibilityservice.cts.utils.EventCapturingClickListener;
import android.accessibilityservice.cts.utils.EventCapturingLongClickListener;
import android.accessibilityservice.cts.utils.EventCapturingMotionEventListener;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.pm.PackageManager;
import android.graphics.PointF;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.lifecycle.Lifecycle;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.FlakyTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.Configurator;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.SettingsStateChangerRule;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.concurrent.Executors;

/**
 * A set of tests for testing touch exploration. Each test dispatches a gesture and checks for the
 * appropriate hover and/or touch events followed by the appropriate accessibility events. Some
 * tests will then check for events from the view.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull
@Presubmit
@CddTest(requirements = {"3.10/C-1-1,C-1-2"})
public class TouchInteractionControllerTest {
    // Constants
    private static final float GESTURE_LENGTH_MM = 15.0f;
    private static final float MIN_SCREEN_WIDTH_MM = 40.0f;

    private static Instrumentation sInstrumentation;
    private static UiAutomation sUiAutomation;

    @ClassRule
    public static final SettingsStateChangerRule sEnabledAccessibilityServicesSettingRule =
            new SettingsStateChangerRule(
                    InstrumentationRegistry.getInstrumentation().getTargetContext(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    null);

    private TouchExplorationStubAccessibilityService mService;
    private boolean mHasTouchscreen;
    private boolean mScreenBigEnough;
    private long mSwipeTimeMillis;
    private EventCapturingMotionEventListener mMotionEventListener =
            new EventCapturingMotionEventListener(false);
    private EventCapturingClickListener mClickListener = new EventCapturingClickListener();
    private EventCapturingLongClickListener mLongClickListener =
            new EventCapturingLongClickListener();

    private ActivityScenarioRule<GestureDispatchActivity> mActivityRule =
            new ActivityScenarioRule<>(GestureDispatchActivity.class);

    private InstrumentedAccessibilityServiceTestRule<TouchExplorationStubAccessibilityService>
            mServiceRule =
                    new InstrumentedAccessibilityServiceTestRule<>(
                            TouchExplorationStubAccessibilityService.class, false);

    private AccessibilityDumpOnFailureRule mDumpOnFailureRule =
            new AccessibilityDumpOnFailureRule();

    @Rule
    public final RuleChain mRuleChain =
            RuleChain.outerRule(mActivityRule).around(mServiceRule).around(mDumpOnFailureRule);

    PointF mTapLocation; // Center of activity. Gestures all start from around this point.
    float mSwipeDistance;
    View mView;
    TouchInteractionController mController;

    @BeforeClass
    public static void oneTimeSetup() {
        Configurator.getInstance()
                .setUiAutomationFlags(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        sInstrumentation = InstrumentationRegistry.getInstrumentation();
        // Disable all services before enabling Accessibility service to prevent flakiness
        // that depends on which services are enabled.
        InstrumentedAccessibilityService.disableAllServices();
        sUiAutomation =
                sInstrumentation.getUiAutomation(
                        UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
    }

    @AfterClass
    public static void postTestTearDown() {
        sUiAutomation.destroy();
    }

    @Before
    public void setUp() throws Exception {
        sUiAutomation.waitForIdle(DEFAULT_IDLE_TIMEOUT_MS, DEFAULT_GLOBAL_TIMEOUT_MS);
        PackageManager pm = sInstrumentation.getContext().getPackageManager();
        mHasTouchscreen =
                pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
                        || pm.hasSystemFeature(PackageManager.FEATURE_FAKETOUCH);
        assumeTrue("The test device should have a touch screen", mHasTouchscreen);
        mActivityRule.getScenario().moveToState(Lifecycle.State.RESUMED);
        // Find window size, check that it is big enough for gestures.
        // Gestures will start in the center of the window, so we need enough horiz/vert space.
        mService = mServiceRule.enableService();
        final DisplayMetrics metrics = new DisplayMetrics();
        mActivityRule.getScenario().onActivity(activity -> {
            mView = activity.findViewById(R.id.full_screen_text_view);
            activity.getDisplay().getRealMetrics(metrics);
            mScreenBigEnough =
                    mView.getWidth()
                            > TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_MM, MIN_SCREEN_WIDTH_MM, metrics);
        });
        assumeTrue("The screen is not big enough", mScreenBigEnough);

        mView.setOnHoverListener(mMotionEventListener);
        mView.setOnTouchListener(mMotionEventListener);
        mActivityRule.getScenario().onActivity(activity -> {
            int[] viewLocation = new int[2];
            mView = activity.findViewById(R.id.full_screen_text_view);
            final int midX = mView.getWidth() / 2;
            final int midY = mView.getHeight() / 2;
            mView.getLocationOnScreen(viewLocation);
            mTapLocation = new PointF(viewLocation[0] + midX, viewLocation[1] + midY);
            mSwipeDistance =
                    TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_MM, GESTURE_LENGTH_MM, metrics);
            // This must be slower than 10mm per 150ms to be detected as touch exploration.
            final double swipeDistanceMm = mSwipeDistance / metrics.xdpi * 25.4;
            mSwipeTimeMillis = (long) swipeDistanceMm * 20;

            mView.setOnClickListener(mClickListener);
            mView.setOnLongClickListener(mLongClickListener);
            mView.requestFocusFromTouch();
        });
        mController = mService.getTouchInteractionController(Display.DEFAULT_DISPLAY);
    }

    @After
    public void tearDown() {
        if (mService != null) {
            mService.disableSelfAndRemove();
        }
    }

    public void assertBasicConsistency() {
        assertThat(Display.DEFAULT_DISPLAY).isEqualTo(mController.getDisplayId());
        assertThat(mController.getMaxPointerCount()).isGreaterThan(0);
        int state = mController.getState();
        assertThat("Unknown state: " + state)
                .isNotEqualTo(TouchInteractionController.stateToString(state));
    }

    /** Test whether we can initiate touch exploration when performing a single tap. */
    @Test
    @AppModeFull
    public void testSingleTap_initiatesTouchExploration() {
        assertBasicConsistency();
        mController.registerCallback(
                Executors.newSingleThreadExecutor(),
                new BaseCallback() {
                    public void onMotionEvent(MotionEvent event) {
                        if (event.getActionMasked() == ACTION_DOWN) {
                            mController.requestTouchExploration();
                        }
                    }
                });
        dispatch(click(mTapLocation));
        mMotionEventListener.assertPropagated(ACTION_HOVER_ENTER, ACTION_HOVER_EXIT);
    }

    /**
     * Test whether interrupting and continuing touch exploration generates a consistent motion
     * event stream.
     */
    @Test
    @AppModeFull
    public void testInterruptedSwipe_generatesConsistentEventStream() {
        assertBasicConsistency();
        mController.registerCallback(
                Executors.newSingleThreadExecutor(),
                new BaseCallback() {
                    public void onMotionEvent(MotionEvent event) {
                        if (event.getActionMasked() == ACTION_DOWN
                                && mController.getState()
                                        != TouchInteractionController.STATE_TOUCH_EXPLORING) {
                            mController.requestTouchExploration();
                        }
                    }
                });
        PointF startPoint = add(mTapLocation, -mSwipeDistance, 0);
        PointF endPoint = add(mTapLocation, mSwipeDistance, 0);
        dispatch(interruptedSwipe(startPoint, endPoint, mSwipeTimeMillis));
        mMotionEventListener.assertPropagated(
                ACTION_HOVER_ENTER, ACTION_HOVER_MOVE, ACTION_HOVER_EXIT);
        // If the touch exploration request arrives to the framework too late, we might only see one
        // hover interaction that combines both swipes.
        // All we care about is that the stream is consistent.
        if (mMotionEventListener.peek() != null) {
            mMotionEventListener.assertPropagated(
                    ACTION_HOVER_ENTER, ACTION_HOVER_MOVE, ACTION_HOVER_EXIT);
        }
    }

    /** Test whether we can initiate a drag. */
    @Test
    @AppModeFull
    public void testTwoFingerDrag_sendsTouchEvents() {
        assertBasicConsistency();
        mController.registerCallback(
                Executors.newSingleThreadExecutor(),
                new BaseCallback() {
                    public void onMotionEvent(MotionEvent event) {
                        if (event.getActionMasked() == ACTION_POINTER_DOWN) {
                            mController.requestDragging(event.getPointerId(0));
                        }
                    }
                });
        // A two point moving that are in the same direction can perform a drag gesture by
        // TouchExplorer while one point moving can not perform a drag gesture. We use two
        // swipes
        // to emulate a two finger drag gesture.
        final int twoFingerOffset = (int) mSwipeDistance;
        final PointF dragStart = mTapLocation;
        final PointF dragEnd = add(dragStart, 0, mSwipeDistance);
        final PointF finger1Start = add(dragStart, twoFingerOffset, 0);
        final PointF finger1End = add(finger1Start, 0, mSwipeDistance);
        final PointF finger2Start = add(dragStart, -twoFingerOffset, 0);
        final PointF finger2End = add(finger2Start, 0, mSwipeDistance);
        dispatch(
                swipe(finger1Start, finger1End, mSwipeTimeMillis),
                swipe(finger2Start, finger2End, mSwipeTimeMillis));
        mMotionEventListener.assertPropagated(ACTION_DOWN, ACTION_MOVE, ACTION_UP);
    }

    /**
     * This method tests the case where two fingers are moving independently. The gesture should be
     * delegated to the view as-is. This is distinct from dragging, where two fingers are delegated
     * to the view as one finger.
     */
    @Test
    @AppModeFull
    public void testTwoFingersMovingIndependently_shouldDelegate() {
        assertBasicConsistency();
        mController.registerCallback(
                Executors.newSingleThreadExecutor(),
                new BaseCallback() {
                    public void onMotionEvent(MotionEvent event) {
                        if (event.getActionMasked() == ACTION_POINTER_DOWN) {
                            mController.requestDelegating();
                        }
                    }
                });
        // Move two fingers towards eacher slowly.
        PointF finger1Start = add(mTapLocation, -mSwipeDistance, 0);
        PointF finger1End = add(mTapLocation, -10, 0);
        StrokeDescription swipe1 = swipe(finger1Start, finger1End, mSwipeTimeMillis);
        PointF finger2Start = add(mTapLocation, mSwipeDistance, 0);
        PointF finger2End = add(mTapLocation, 10, 0);
        StrokeDescription swipe2 = swipe(finger2Start, finger2End, mSwipeTimeMillis);
        dispatch(swipe1, swipe2);
        mMotionEventListener.assertPropagated(
                ACTION_DOWN, ACTION_POINTER_DOWN, ACTION_MOVE, ACTION_POINTER_UP, ACTION_UP);
    }

    /** Insure that double-tap is recognized as a single interaction. */
    @Test
    @AppModeFull
    public void testDoubleTap_producesSingleInteraction() {
        assertBasicConsistency();
        dispatch(doubleTap(mTapLocation));
        mService.assertPropagated(TYPE_TOUCH_INTERACTION_START, TYPE_TOUCH_INTERACTION_END);
    }

    /**
     * Test the case where we want to click on the item that has accessibility focus by using
     * AccessibilityNodeInfo.performAction. Note that this test does not request that double tap be
     * dispatched to the accessibility service, meaning that it will be handled by the framework and
     * the view will be clicked.
     */
    @Test
    @FlakyTest
    @AppModeFull
    public void testPerformClickAccessibilityFocus_performsClick() {
        assertBasicConsistency();
        syncAccessibilityFocusToInputFocus();
        mController.performClick();
        mService.assertPropagated(TYPE_VIEW_ACCESSIBILITY_FOCUSED);
        // The click should not be delivered via touch events in this case.
        mMotionEventListener.assertNonePropagated();
        mClickListener.assertClicked(mView);
    }

    /**
     * Test the case where we double tap but there is no accessibility focus. Nothing should happen.
     */
    @Test
    @AppModeFull
    public void testPerformClickNoFocus_doesNotPerformClick() {
        assertBasicConsistency();
        mController.performClick();
        mMotionEventListener.assertNonePropagated();
        mClickListener.assertNoneClicked();
    }

    /** Set the accessibility focus to the element that has input focus. */
    @Test
    @AppModeFull
    public void testPerformLongClick_sendsMotionEvents() {
        assertBasicConsistency();
        // First perform touch exploration.
        mController.registerCallback(
                Executors.newSingleThreadExecutor(),
                new BaseCallback() {
                    public void onMotionEvent(MotionEvent event) {
                        if (event.getActionMasked() == ACTION_DOWN) {
                            mController.requestTouchExploration();
                        }
                    }
                });
        dispatch(click(mTapLocation));
        mMotionEventListener.assertPropagated(ACTION_HOVER_ENTER, ACTION_HOVER_EXIT);
        // Wait for the interaction ends before beginning a new one.
        mService.assertPropagated(
                TYPE_TOUCH_INTERACTION_START,
                TYPE_TOUCH_EXPLORATION_GESTURE_START,
                TYPE_TOUCH_EXPLORATION_GESTURE_END,
                TYPE_TOUCH_INTERACTION_END);
        mController.unregisterAllCallbacks();
        mController.registerCallback(
                Executors.newSingleThreadExecutor(),
                new BaseCallback() {
                    public void onMotionEvent(MotionEvent event) {
                        if (event.getActionMasked() == ACTION_DOWN) {
                            mController.performLongClickAndStartDrag();
                        }
                    }
                });
        PointF endPoint = add(mTapLocation, mSwipeDistance, 0);
        dispatch(swipe(mTapLocation, add(mTapLocation, mSwipeDistance, 0), mSwipeTimeMillis));
        mMotionEventListener.assertPropagated(ACTION_DOWN, ACTION_MOVE, ACTION_UP);
    }

    @Test
    @AppModeFull
    public void testRemove_shouldReturnControlToFramework() {
        assertBasicConsistency();
        TouchInteractionController.Callback callback = new BaseCallback();
        mController.registerCallback(Executors.newSingleThreadExecutor(), callback);
        dispatch(click(mTapLocation));
        // Nothing should happen because the callback is empty.
        mMotionEventListener.assertNonePropagated();
        mController.unregisterCallback(callback);
        mMotionEventListener.assertNonePropagated();
        dispatch(click(mTapLocation));
        mMotionEventListener.assertPropagated(ACTION_HOVER_ENTER, ACTION_HOVER_EXIT);
    }

    /** Test whether service gesture detection remains active when we rebuild the input filter. */
    @Test
    @AppModeFull
    public void testRebuildInputFilter_shouldRetainState() {
        assertBasicConsistency();
        // Set up a touch interaction controller that records incoming motion events.
        mController.registerCallback(
                Executors.newSingleThreadExecutor(),
                new BaseCallback() {
                    public void onMotionEvent(MotionEvent event) {
                        mMotionEventListener.onTouch(null, event);
                    }
                });
        dispatch(click(mTapLocation));
        mMotionEventListener.assertPropagated(ACTION_DOWN, ACTION_UP);
        // Start another service
        StubMagnificationAccessibilityService secondService;
        secondService =
                InstrumentedAccessibilityService.enableService(
                        StubMagnificationAccessibilityService.class);
        try {
            // We should still be recording incoming motion events.
            dispatch(click(mTapLocation));
            mMotionEventListener.assertPropagated(ACTION_DOWN, ACTION_UP);
        } finally {
            secondService.disableSelfAndRemove();
        }
    }

    private void syncAccessibilityFocusToInputFocus() {
        AccessibilityNodeInfo focus =
                mService.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        assertWithMessage("Could not find input focused node").that(focus).isNotNull();
        focus.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
        mService.waitForAccessibilityFocus();
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

    class BaseCallback implements TouchInteractionController.Callback {

        @Override
        public void onMotionEvent(MotionEvent event) {}

        @Override
        public void onStateChanged(int state) {}
    }
}
