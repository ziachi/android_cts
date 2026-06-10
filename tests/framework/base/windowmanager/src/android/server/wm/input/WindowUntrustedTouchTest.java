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
 * limitations under the License.
 */

package android.server.wm.input;

import static android.server.wm.overlay.Components.OverlayActivity.EXTRA_TOKEN;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.ActivityOptions;
import android.graphics.Rect;
import android.os.IBinder;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.server.wm.overlay.Components;
import android.server.wm.overlay.R;
import android.server.wm.shared.BlockingResultReceiver;
import android.view.Display;
import android.view.WindowManager.LayoutParams;

import androidx.annotation.NonNull;
import androidx.test.filters.FlakyTest;

import com.android.compatibility.common.util.FeatureUtil;

import org.junit.Test;

/**
 * Build/Install/Run:
 * atest CtsWindowManagerDeviceInput:WindowUntrustedTouchTest
 */
@Presubmit
public class WindowUntrustedTouchTest extends WindowUntrustedTouchTestBase {

    private static final String APP_SELF = "android.server.wm.cts";

    @Override
    @NonNull
    String getAppSelf() {
        return APP_SELF;
    }

    @Test
    public void testMaximumObscuringOpacity() throws Throwable {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        // Setting the previous value since we override this on setUp()
        setMaximumObscuringOpacityForTouch(mPreviousTouchOpacity);

        assertEquals(0.8f, mInputManager.getMaximumObscuringOpacityForTouch());
    }

    @Test
    public void testAfterSettingThreshold_returnsThresholdSet()
            throws Throwable {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        float threshold = .123f;
        setMaximumObscuringOpacityForTouch(threshold);

        assertEquals(threshold, mInputManager.getMaximumObscuringOpacityForTouch());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAfterSettingThresholdLessThan0_throws() throws Throwable {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        setMaximumObscuringOpacityForTouch(-.5f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAfterSettingThresholdGreaterThan1_throws() throws Throwable {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        setMaximumObscuringOpacityForTouch(1.5f);
    }

    /** SAWs */

    @Test
    public void testWhenOneSawWindowAboveThreshold_allowsTouch() throws Throwable {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        addSawOverlay(APP_A, WINDOW_1, .9f);

        mTouchHelper.tapOnViewCenter(mContainer);

        // Opacity will be automatically capped and touches will pass through.
        assertTouchReceived();
    }

    @Test
    public void testWhenOneSawWindowBelowThreshold_allowsTouch() throws Throwable {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        addSawOverlay(APP_A, WINDOW_1, .7f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    @Test
    public void testWhenOneSawWindowWithZeroOpacity_allowsTouch() throws Throwable {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        addSawOverlay(APP_A, WINDOW_1, 0f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    @Test
    public void testWhenOneSawWindowAtThreshold_allowsTouch() throws Throwable {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        addSawOverlay(APP_A, WINDOW_1, MAXIMUM_OBSCURING_OPACITY);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    @Test
    public void testWhenTwoSawWindowsFromSameAppTogetherBelowThreshold_allowsTouch()
            throws Throwable {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        // Resulting opacity = 1 - (1 - 0.5)*(1 - 0.5) = .75
        addSawOverlay(APP_A, WINDOW_1, .5f);
        addSawOverlay(APP_A, WINDOW_2, .5f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    @Test
    public void testWhenTwoSawWindowsFromSameAppTogetherAboveThreshold_blocksTouch()
            throws Throwable {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        // Resulting opacity = 1 - (1 - 0.7)*(1 - 0.7) = .91
        addSawOverlay(APP_A, WINDOW_1, .7f);
        addSawOverlay(APP_A, WINDOW_2, .7f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @Test
    public void testWhenTwoSawWindowsFromDifferentAppsEachBelowThreshold_allowsTouch()
            throws Throwable {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        addSawOverlay(APP_A, WINDOW_1, .7f);
        addSawOverlay(APP_B, WINDOW_2, .7f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    @Test
    public void testWhenOneSawWindowAboveThresholdAndSelfSawWindow_allowsTouch()
            throws Throwable {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        addSawOverlay(APP_A, WINDOW_1, .9f);
        addSawOverlay(getAppSelf(), WINDOW_1, .7f);

        mTouchHelper.tapOnViewCenter(mContainer);

        // Opacity will be automatically capped and touches will pass through.
        assertTouchReceived();
    }

    @Test
    public void testWhenOneSawWindowBelowThresholdAndSelfSawWindow_allowsTouch()
            throws Throwable {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        addSawOverlay(APP_A, WINDOW_1, .7f);
        addSawOverlay(getAppSelf(), WINDOW_1, .7f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    @Test
    public void testWhenTwoSawWindowsTogetherBelowThresholdAndSelfSawWindow_allowsTouch()
            throws Throwable {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        // Resulting opacity for A = 1 - (1 - 0.5)*(1 - 0.5) = .75
        addSawOverlay(APP_A, WINDOW_1, .5f);
        addSawOverlay(APP_A, WINDOW_1, .5f);
        addSawOverlay(getAppSelf(), WINDOW_1, .7f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    @Test
    public void testWhenThresholdIs0AndSawWindowAtThreshold_allowsTouch()
            throws Throwable {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        setMaximumObscuringOpacityForTouch(0);
        addSawOverlay(APP_A, WINDOW_1, 0);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    @Test
    public void testWhenThresholdIs0AndSawWindowAboveThreshold_allowsTouch()
            throws Throwable {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        setMaximumObscuringOpacityForTouch(0);
        addSawOverlay(APP_A, WINDOW_1, .1f);

        mTouchHelper.tapOnViewCenter(mContainer);

        // Opacity will be automatically capped and touches will pass through.
        assertTouchReceived();
    }

    @Test
    public void testWhenThresholdIs1AndSawWindowAtThreshold_allowsTouch()
            throws Throwable {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        setMaximumObscuringOpacityForTouch(1);
        addSawOverlay(APP_A, WINDOW_1, 1);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    @Test
    public void testWhenThresholdIs1AndSawWindowBelowThreshold_allowsTouch()
            throws Throwable {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        setMaximumObscuringOpacityForTouch(1);
        addSawOverlay(APP_A, WINDOW_1, .9f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    /** Activity windows */

    @Test
    public void testWhenOneActivityWindowBelowThreshold_blocksTouch()
            throws Throwable {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        addActivityOverlay(APP_A, /* opacity */ .5f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @Test
    public void testWhenOneActivityWindowAboveThreshold_blocksTouch()
            throws Throwable {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        addActivityOverlay(APP_A, /* opacity */ .9f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @RequiresFlagsDisabled(com.android.window.flags.Flags.FLAG_TOUCH_PASS_THROUGH_OPT_IN)
    @Test
    public void testWhenOneActivityWindowWithZeroOpacity_allowsTouch()
            throws Throwable {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        addActivityOverlay(APP_A, /* opacity */ 0f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    @RequiresFlagsEnabled(com.android.window.flags.Flags.FLAG_TOUCH_PASS_THROUGH_OPT_IN)
    @Test
    public void testWhenOneActivityWindowWithZeroOpacityWithOptIn_allowsTouch()
            throws Throwable {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        addActivityOverlay(APP_A, /* opacity */ 0f, /* allowPassThrough */ true);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    @RequiresFlagsEnabled(com.android.window.flags.Flags.FLAG_TOUCH_PASS_THROUGH_OPT_IN)
    @Test
    public void testWhenOneActivityWindowWithZeroOpacityNoOptIn_blocksTouch()
            throws Throwable {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        addActivityOverlay(APP_A, /* opacity */ 0f, /* allowPassThrough */ false);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @Test
    public void testWhenOneActivityWindowWithMinPositiveOpacity_blocksTouch()
            throws Throwable {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        addActivityOverlay(APP_A, /* opacity */ MIN_POSITIVE_OPACITY);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @Test
    public void testWhenOneActivityWindowWithSmallOpacity_blocksTouch()
            throws Throwable {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        addActivityOverlay(APP_A, /* opacity */ .01f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @Test
    public void testWhenOneSelfActivityWindow_allowsTouch() throws Throwable {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        addActivityOverlay(getAppSelf(), /* opacity */ .9f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    @Test
    public void testWhenTwoActivityWindowsFromDifferentAppsTogetherBelowThreshold_blocksTouch()
            throws Throwable {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        addActivityOverlay(APP_A, /* opacity */ .7f);
        addActivityOverlay(APP_B, /* opacity */ .7f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @Test
    public void testWhenOneActivityWindowAndOneSawWindowTogetherBelowThreshold_blocksTouch()
            throws Throwable {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        addActivityOverlay(APP_A, /* opacity */ .5f);
        addSawOverlay(APP_A, WINDOW_1, .5f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @Test
    public void testWhenOneActivityWindowAndOneSelfCustomToastWindow_blocksTouch()
            throws Throwable {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        // Toast has to be before otherwise it would be blocked from background
        addToastOverlay(getAppSelf(), /* custom */ true);
        addActivityOverlay(APP_A, /* opacity */ .5f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @Test
    public void testWhenOneActivityWindowAndOneSelfSawWindow_blocksTouch()
            throws Throwable {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        addActivityOverlay(APP_A, /* opacity */ .5f);
        addSawOverlay(getAppSelf(), WINDOW_1, .5f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @Test
    public void testWhenOneActivityWindowAndOneSawWindowBelowThreshold_blocksTouch()
            throws Throwable {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        addActivityOverlay(APP_A, /* opacity */ .5f);
        addSawOverlay(APP_A, WINDOW_1, .5f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @Test
    public void testWhenOneActivityWindowAndOneSawWindowBelowThresholdFromDifferentApp_blocksTouch()
            throws Throwable {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        addActivityOverlay(APP_A, /* opacity */ .5f);
        addSawOverlay(APP_B, WINDOW_1, .5f);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    /** Activity-type child windows on same activity */

    @Test
    public void testWhenActivityChildWindowWithSameTokenFromDifferentApp_allowsTouch()
            throws Exception {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        IBinder token = mActivity.getWindow().getAttributes().token;
        addActivityChildWindow(APP_A, WINDOW_1, token);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    @Test
    public void testWhenActivityChildWindowWithDifferentTokenFromDifferentApp_blocksTouch()
            throws Exception {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        // Creates a new activity with 0 opacity
        BlockingResultReceiver receiver = new BlockingResultReceiver();
        addActivityOverlay(APP_A, /* opacity */ 0f, receiver);

        // Now get its token and put a child window from another app with it
        IBinder token = receiver.getData(TIMEOUT_MS).getBinder(EXTRA_TOKEN);
        addActivityChildWindow(APP_B, WINDOW_1, token);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @RequiresFlagsDisabled(com.android.window.flags.Flags.FLAG_TOUCH_PASS_THROUGH_OPT_IN)
    @Test
    public void testWhenActivityChildWindowWithDifferentTokenFromSameApp_allowsTouch()
            throws Exception {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        // Creates a new activity with 0 opacity
        BlockingResultReceiver receiver = new BlockingResultReceiver();
        addActivityOverlay(APP_A, /* opacity */ 0f, receiver);
        // Now get its token and put a child window owned by us
        IBinder token = receiver.getData(TIMEOUT_MS).getBinder(EXTRA_TOKEN);
        addActivityChildWindow(getAppSelf(), WINDOW_1, token);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    @RequiresFlagsEnabled(com.android.window.flags.Flags.FLAG_TOUCH_PASS_THROUGH_OPT_IN)
    @Test
    public void testWhenActivityChildWindowWithDifferentTokenFromSameAppWithOptIn_allowsTouch()
            throws Exception {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        // Creates a new activity with 0 opacity
        BlockingResultReceiver receiver = new BlockingResultReceiver();
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setAllowPassThroughOnTouchOutside(true);
        addActivityOverlay(APP_A, /* opacity */ 0f, receiver, options.toBundle());
        mTouchHelper.tapOnViewCenter(mContainer);
        assertTouchReceived();

        // Now get its token and put a child window owned by us
        IBinder token = receiver.getData(TIMEOUT_MS).getBinder(EXTRA_TOKEN);
        addActivityChildWindow(getAppSelf(), WINDOW_1, token);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    @RequiresFlagsEnabled(com.android.window.flags.Flags.FLAG_TOUCH_PASS_THROUGH_OPT_IN)
    @Test
    public void testWhenActivityChildWindowWithDifferentTokenFromSameAppNoOptIn_blocksTouch()
            throws Exception {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        // Creates a new activity with 0 opacity
        BlockingResultReceiver receiver = new BlockingResultReceiver();
        addActivityOverlay(APP_A, /* opacity */ 0f, receiver);
        // Now get its token and put a child window owned by us
        IBinder token = receiver.getData(TIMEOUT_MS).getBinder(EXTRA_TOKEN);
        addActivityChildWindow(getAppSelf(), WINDOW_1, token);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    /** Activity transitions */

    @Test
    public void testLongEnterAnimations_areLimited() throws Exception {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        long durationSet = mResources.getInteger(R.integer.long_animation_duration);
        assertThat(durationSet).isGreaterThan(
                MAX_ANIMATION_DURATION_MS + ANIMATION_DURATION_TOLERANCE_MS);
        addAnimatedActivityOverlay(APP_A, /* touchable */ false, R.anim.long_alpha_0_7,
                R.anim.long_alpha_1);
        assertTrue(mWmState.waitForAppTransitionRunningOnDisplay(Display.DEFAULT_DISPLAY));
        long start = SystemClock.elapsedRealtime();

        assertTrue(mWmState.waitForAppTransitionIdleOnDisplay(Display.DEFAULT_DISPLAY));
        long duration = SystemClock.elapsedRealtime() - start;
        assertThat(duration).isAtMost(MAX_ANIMATION_DURATION_MS + ANIMATION_DURATION_TOLERANCE_MS);
    }

    @Test
    public void testLongExitAnimations_areLimited() throws Exception {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        long durationSet = mResources.getInteger(R.integer.long_animation_duration);
        assertThat(durationSet).isGreaterThan(
                MAX_ANIMATION_DURATION_MS + ANIMATION_DURATION_TOLERANCE_MS);
        addExitAnimationActivity(APP_A);

        // Wait for ExitAnimationActivity open transition to complete to avoid counting this
        // transition in the duration of the exit animation below. Otherwise
        // waitForAppTransitionRunningOnDisplay might return immediately if this transition is not
        // done by then instead of waiting for the exit animation to start running.
        assertTrue(mWmState.waitForAppTransitionIdleOnDisplay(Display.DEFAULT_DISPLAY));

        sendFinishToExitAnimationActivity(APP_A,
                Components.ExitAnimationActivityReceiver.EXTRA_VALUE_LONG_ANIMATION_0_7);
        assertTrue(mWmState.waitForAppTransitionRunningOnDisplay(Display.DEFAULT_DISPLAY));
        long start = SystemClock.elapsedRealtime();

        assertTrue(mWmState.waitForAppTransitionIdleOnDisplay(Display.DEFAULT_DISPLAY));
        long duration = SystemClock.elapsedRealtime() - start;
        assertThat(duration).isAtMost(MAX_ANIMATION_DURATION_MS + ANIMATION_DURATION_TOLERANCE_MS);
    }

    @Test
    public void testWhenEnterAnimationAboveThresholdAndNewActivityNotTouchable_blocksTouch()
            throws Exception {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        addAnimatedActivityOverlay(APP_A, /* touchable */ false, R.anim.alpha_0_9, R.anim.alpha_1);
        assertTrue(mWmState.waitForAppTransitionRunningOnDisplay(Display.DEFAULT_DISPLAY));

        mTouchHelper.tapOnViewCenter(mContainer, /* waitAnimations*/ false);

        assertAnimationRunning();
        assertTouchNotReceived();
    }

    @Test
    public void testWhenEnterAnimationBelowThresholdAndNewActivityNotTouchable_allowsTouch()
            throws Exception {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        addAnimatedActivityOverlay(APP_A, /* touchable */ false, R.anim.alpha_0_7, R.anim.alpha_1);
        assertTrue(mWmState.waitForAppTransitionRunningOnDisplay(Display.DEFAULT_DISPLAY));

        mTouchHelper.tapOnViewCenter(mContainer, /* waitAnimations*/ false);

        assertAnimationRunning();
        assertTouchReceived();
    }

    @Test
    public void testWhenEnterAnimationBelowThresholdAndNewActivityTouchable_blocksTouch()
            throws Exception {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        addAnimatedActivityOverlay(APP_A, /* touchable */ true, R.anim.alpha_0_7, R.anim.alpha_1);
        assertTrue(mWmState.waitForAppTransitionRunningOnDisplay(Display.DEFAULT_DISPLAY));

        mTouchHelper.tapOnViewCenter(mContainer, /* waitAnimations*/ false);

        assertAnimationRunning();
        assertTouchNotReceived();
    }

    @Test
    public void testWhenExitAnimationBelowThreshold_allowsTouch()
            throws Exception {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        addExitAnimationActivity(APP_A);

        // Wait for ExitAnimationActivity open transition to complete to avoid
        // waitForAppTransitionRunningOnDisplay returning immediately if this transition is not
        // done by then instead of waiting for the exit animation to start running.
        assertTrue(mWmState.waitForAppTransitionIdleOnDisplay(Display.DEFAULT_DISPLAY));

        sendFinishToExitAnimationActivity(APP_A,
                Components.ExitAnimationActivityReceiver.EXTRA_VALUE_ANIMATION_0_7);
        assertTrue(mWmState.waitForAppTransitionRunningOnDisplay(Display.DEFAULT_DISPLAY));

        mTouchHelper.tapOnViewCenter(mContainer, /* waitAnimations*/ false);

        assertAnimationRunning();
        assertTouchReceived();
    }

    @Test
    public void testWhenExitAnimationAboveThreshold_blocksTouch()
            throws Exception {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        addExitAnimationActivity(APP_A);
        sendFinishToExitAnimationActivity(APP_A,
                Components.ExitAnimationActivityReceiver.EXTRA_VALUE_ANIMATION_0_9);
        assertTrue(mWmState.waitForAppTransitionRunningOnDisplay(Display.DEFAULT_DISPLAY));

        mTouchHelper.tapOnViewCenter(mContainer, /* waitAnimations*/ false);

        assertAnimationRunning();
        assertTouchNotReceived();
    }

    @Test
    public void testWhenExitAnimationAboveThresholdFromSameUid_allowsTouch()
            throws Exception {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        addExitAnimationActivity(getAppSelf());
        sendFinishToExitAnimationActivity(getAppSelf(),
                Components.ExitAnimationActivityReceiver.EXTRA_VALUE_ANIMATION_0_9);
        assertTrue(mWmState.waitForAppTransitionRunningOnDisplay(Display.DEFAULT_DISPLAY));

        mTouchHelper.tapOnViewCenter(mContainer, /* waitAnimations*/ false);

        assertAnimationRunning();
        assertTouchReceived();
    }

    /** Toast windows */
    @FlakyTest(bugId = 293267005)
    @Test
    public void testWhenSelfTextToastWindow_allowsTouch() throws Throwable {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        addToastOverlay(getAppSelf(), /* custom */ false);
        Rect toast = mWmState.waitForResult("toast bounds",
                state -> state.findFirstWindowWithType(LayoutParams.TYPE_TOAST).getFrame());

        mTouchHelper.tapOnCenter(toast, mActivity.getDisplayId());

        assertTouchReceived();
    }

    @Test
    public void testWhenTextToastWindow_allowsTouch() throws Throwable {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        assumeFalse("Watch does not support new Toast behavior yet.", FeatureUtil.isWatch());
        addToastOverlay(APP_A, /* custom */ false);
        Rect toast = mWmState.waitForResult("toast bounds",
                state -> state.findFirstWindowWithType(LayoutParams.TYPE_TOAST).getFrame());

        mTouchHelper.tapOnCenter(toast, mActivity.getDisplayId());

        assertTouchReceived();
    }

    @Test
    public void testWhenOneCustomToastWindow_blocksTouch() throws Throwable {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        addToastOverlay(APP_A, /* custom */ true);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @Test
    public void testWhenOneSelfCustomToastWindow_allowsTouch() throws Throwable {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        addToastOverlay(getAppSelf(), /* custom */ true);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }

    @Test
    public void testWhenOneCustomToastWindowAndOneSelfSawWindow_blocksTouch()
            throws Throwable {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        addSawOverlay(getAppSelf(), WINDOW_1, .9f);
        addToastOverlay(APP_A, /* custom */ true);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @Test
    public void testWhenOneCustomToastWindowAndOneSawWindowBelowThreshold_blocksTouch()
            throws Throwable {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        addSawOverlay(APP_A, WINDOW_1, .5f);
        addToastOverlay(APP_A, /* custom */ true);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @Test
    public void testWhenOneCustomToastWindowAndOneSawWindowBelowThresholdFromDifferentApp_blocksTouch()
            throws Throwable {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        addSawOverlay(APP_A, WINDOW_1, .5f);
        addToastOverlay(APP_B, /* custom */ true);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchNotReceived();
    }

    @Test
    public void testWhenOneSelfCustomToastWindowOneSelfActivityWindowAndOneSawBelowThreshold_allowsTouch()
            throws Throwable {
        // TODO(b/398861504): Ensure this test case is covered by the CTS Verifier.
        assumeFalse("XR device uses a custom window occlusion check tested via CTS Verifier.",
                FeatureUtil.isXrHeadset());

        addActivityOverlay(getAppSelf(), /* opacity */ .9f);
        addSawOverlay(APP_A, WINDOW_1, .5f);
        addToastOverlay(getAppSelf(), /* custom */ true);

        mTouchHelper.tapOnViewCenter(mContainer);

        assertTouchReceived();
    }
}
