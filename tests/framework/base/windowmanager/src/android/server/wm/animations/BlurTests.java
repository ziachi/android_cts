/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.server.wm.animations;

import static android.server.wm.ComponentNameUtils.getWindowName;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowInsets.Type.systemBars;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;

import android.app.Activity;
import android.content.ComponentName;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Rect;
import android.os.Bundle;
import android.platform.test.annotations.Presubmit;
import android.provider.Settings;
import android.server.wm.DumpOnFailure;
import android.server.wm.WindowManagerState;
import android.server.wm.WindowManagerTestBase;
import android.server.wm.cts.R;
import android.server.wm.settings.SettingsSession;
import android.util.TypedValue;
import android.view.RoundedCorner;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.core.view.WindowCompat;
import androidx.test.filters.FlakyTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.uiautomator.UiDevice;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.ColorUtils;
import com.android.compatibility.common.util.PollingCheck;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.mockito.Mockito;

import java.util.function.Consumer;

@Presubmit
public class BlurTests extends WindowManagerTestBase {
    private static final int BACKGROUND_BLUR_PX = 80;
    private static final int BLUR_BEHIND_PX = 40;
    private static final int NO_BLUR_BACKGROUND_COLOR = 0xFF550055;
    private static final int BROADCAST_WAIT_TIMEOUT = 300;

    private Rect mBackgroundActivityBounds;
    private Rect mPixelTestBounds;

    private final DumpOnFailure mDumpOnFailure = new DumpOnFailure();

    private final TestRule mEnableBlurRule = SettingsSession.overrideForTest(
            Settings.Global.getUriFor(Settings.Global.DISABLE_WINDOW_BLURS),
            Settings.Global::getInt,
            Settings.Global::putInt,
            0);
    private final TestRule mDisableTransitionAnimationRule = SettingsSession.overrideForTest(
            Settings.Global.getUriFor(Settings.Global.TRANSITION_ANIMATION_SCALE),
            Settings.Global::getFloat,
            Settings.Global::putFloat,
            0f);

    private final ActivityTestRule<BackgroundActivity> mBackgroundActivity =
            new ActivityTestRule<>(BackgroundActivity.class);

    @Rule
    public final TestRule methodRules = RuleChain.outerRule(mDumpOnFailure)
            .around(mEnableBlurRule)
            .around(mDisableTransitionAnimationRule)
            .around(mBackgroundActivity);

    @Before
    public void setUp() {
        assumeTrue(supportsBlur());
        ComponentName cn = mBackgroundActivity.getActivity().getComponentName();
        waitAndAssertResumedActivity(cn, cn + " must be resumed");
        mBackgroundActivity.getActivity().waitAndAssertWindowFocusState(true);

        // Use the background activity's bounds when taking the device screenshot.
        // This is needed for multi-screen devices (foldables) where
        // the launched activity covers just one screen
        WindowManagerState.WindowState windowState = mWmState.getWindowState(cn);
        WindowManagerState.Activity act = mWmState.getActivity(cn);
        mBackgroundActivityBounds = act.getBounds();

        // Wait for the first frame *after* the splash screen is removed to take screenshots.
        // Currently there isn't a definite event / callback for this.
        mWmState.waitForAppTransitionIdleOnDisplay(DEFAULT_DISPLAY);
        waitForActivityIdle(mBackgroundActivity.getActivity());

        insetGivenFrame(windowState,
                insetsSource -> (insetsSource.is(WindowInsets.Type.captionBar())),
                mBackgroundActivityBounds);

        // Exclude rounded corners from screenshot comparisons.
        mPixelTestBounds = new Rect(mBackgroundActivityBounds);
        mPixelTestBounds.inset(mBackgroundActivity.getActivity().getInsetsToBeIgnored());

        // Basic checks common to all tests
        verifyOnlyBackgroundImageVisible();
        assertTrue(mContext.getSystemService(WindowManager.class).isCrossWindowBlurEnabled());
    }

    @Test
    @ApiTest(apis = {"android.view.Window#setBackgroundBlurRadius(int)"})
    public void testBackgroundBlurSimple() {
        final BlurActivity blurActivity = startTestActivity(BlurActivity.class);
        getInstrumentation().runOnMainSync(() -> {
            blurActivity.setBackgroundBlurRadius(BACKGROUND_BLUR_PX);
        });

        waitForActivityIdle(blurActivity);

        final Rect windowFrame = getFloatingWindowFrame(blurActivity);
        assertOnScreenshot(screenshot -> {
            assertBackgroundBlur(screenshot, windowFrame);
        });
    }

    @Test
    @ApiTest(apis = {"android.view.WindowManager.LayoutParams#setBlurBehindRadius",
                     "android.R.styleable#Window_windowBlurBehindEnabled"})
    public void testBlurBehindSimple() throws Exception {
        final BlurActivity blurActivity = startTestActivity(BlurActivity.class);
        getInstrumentation().runOnMainSync(() -> {
            blurActivity.setBlurBehindRadius(BLUR_BEHIND_PX);
        });
        waitForActivityIdle(blurActivity);
        final Rect windowFrame = getFloatingWindowFrame(blurActivity);

        assertOnScreenshot(screenshot -> {
            assertBlurBehind(screenshot, windowFrame);
            assertNoBackgroundBlur(screenshot, windowFrame);
        });

        getInstrumentation().runOnMainSync(() -> {
            blurActivity.setBlurBehindRadius(0);
        });
        waitForActivityIdle(blurActivity);

        assertOnScreenshot(screenshot -> {
            assertNoBlurBehind(screenshot, windowFrame);
            assertNoBackgroundBlur(screenshot, windowFrame);
        });
    }

    @Test
    @ApiTest(apis = {"android.view.Window#setBackgroundBlurRadius"})
    public void testNoBackgroundBlurForNonTranslucentWindow() {
        final BlurActivity blurActivity = startTestActivity(BadBlurActivity.class);
        getInstrumentation().runOnMainSync(() -> {
            blurActivity.setBackgroundBlurRadius(BACKGROUND_BLUR_PX);
            blurActivity.setBackgroundColor(Color.TRANSPARENT);
        });
        waitForActivityIdle(blurActivity);

        verifyOnlyBackgroundImageVisible();
    }

    @Test
    @ApiTest(apis = {"android.view.WindowManager.LayoutParams#setBlurBehindRadius",
                     "android.R.styleable#Window_windowBlurBehindEnabled"})
    public void testNoBlurBehindWhenFlagNotSet() {
        final BlurActivity blurActivity = startTestActivity(BadBlurActivity.class);
        getInstrumentation().runOnMainSync(() -> {
            blurActivity.setBlurBehindRadius(BLUR_BEHIND_PX);
            blurActivity.setBackgroundColor(Color.TRANSPARENT);
        });
        waitForActivityIdle(blurActivity);

        verifyOnlyBackgroundImageVisible();
    }

    @Test
    @ApiTest(apis = {"android.view.WindowManager.LayoutParams#setBlurBehindRadius",
                     "android.R.styleable#Window_windowBlurBehindEnabled"})
    public void testBlurBehindDisabledDynamically() {
        final BlurActivity blurActivity = startTestActivity(BlurActivity.class);
        getInstrumentation().runOnMainSync(() -> {
            blurActivity.setBlurBehindRadius(BLUR_BEHIND_PX);
        });
        waitForActivityIdle(blurActivity);
        final Rect windowFrame = getFloatingWindowFrame(blurActivity);

        assertOnScreenshot(screenshot -> {
            assertBlurBehind(screenshot, windowFrame);
            assertNoBackgroundBlur(screenshot, windowFrame);
        });

        getInstrumentation().runOnMainSync(() -> {
            blurActivity.setBlurBehindRadius(0);
        });
        waitForActivityIdle(blurActivity);

        assertOnScreenshot(screenshot -> {
            assertNoBackgroundBlur(screenshot, windowFrame);
            assertNoBlurBehind(screenshot, windowFrame);
        });

        getInstrumentation().runOnMainSync(() -> {
            blurActivity.setBlurBehindRadius(BLUR_BEHIND_PX);
        });
        waitForActivityIdle(blurActivity);

        assertOnScreenshot(screenshot -> {
            assertBlurBehind(screenshot,  windowFrame);
            assertNoBackgroundBlur(screenshot, windowFrame);
        });
    }

    @Test
    @ApiTest(apis = {"android.view.WindowManager.LayoutParams#setBlurBehindRadius",
                     "android.R.styleable#Window_windowBlurBehindEnabled",
                     "android.view.Window#setBackgroundBlurRadius"})
    public void testBlurBehindAndBackgroundBlur() {
        final BlurActivity blurActivity = startTestActivity(BlurActivity.class);
        getInstrumentation().runOnMainSync(() -> {
            blurActivity.setBlurBehindRadius(BLUR_BEHIND_PX);
            blurActivity.setBackgroundBlurRadius(BACKGROUND_BLUR_PX);
        });
        waitForActivityIdle(blurActivity);
        final Rect windowFrame = getFloatingWindowFrame(blurActivity);

        assertOnScreenshot(screenshot -> {
            assertBlurBehind(screenshot, windowFrame);
            assertBackgroundBlurOverBlurBehind(screenshot, windowFrame);
        });

        getInstrumentation().runOnMainSync(() -> {
            blurActivity.setBlurBehindRadius(0);
            blurActivity.setBackgroundBlurRadius(0);
        });
        waitForActivityIdle(blurActivity);

        assertOnScreenshot(screenshot -> {
            assertNoBackgroundBlur(screenshot, windowFrame);
            assertNoBlurBehind(screenshot, windowFrame);
        });

        getInstrumentation().runOnMainSync(() -> {
            blurActivity.setBlurBehindRadius(BLUR_BEHIND_PX);
            blurActivity.setBackgroundBlurRadius(BACKGROUND_BLUR_PX);
        });
        waitForActivityIdle(blurActivity);

        assertOnScreenshot(screenshot -> {
            assertBlurBehind(screenshot, windowFrame);
            assertBackgroundBlurOverBlurBehind(screenshot, windowFrame);
        });
    }

    @Test
    @ApiTest(apis = {"android.R.styleable#Window_windowBackgroundBlurRadius",
                     "android.R.styleable#Window_windowBlurBehindRadius",
                     "android.R.styleable#Window_windowBlurBehindEnabled"})
    public void testBlurBehindAndBackgroundBlurSetWithAttributes() {
        final Activity blurAttrActivity = startTestActivity(BlurAttributesActivity.class);
        final Rect windowFrame = getFloatingWindowFrame(blurAttrActivity);

        assertOnScreenshot(screenshot -> {
            assertBlurBehind(screenshot, windowFrame);
            assertBackgroundBlurOverBlurBehind(screenshot, windowFrame);
        });
    }

    @FlakyTest(bugId = 382609163)
    @Test
    @ApiTest(
            apis = {
                "android.view.WindowManager.LayoutParams#setBlurBehindRadius",
                "android.R.styleable#Window_windowBlurBehindEnabled",
                "android.view.Window#setBackgroundBlurRadius"
            })
    public void testAllBlurRemovedAndRestoredWhenToggleBlurDisabled() {
        final BlurActivity blurActivity = startTestActivity(BlurActivity.class);
        getInstrumentation().runOnMainSync(() -> {
            blurActivity.setBlurBehindRadius(BLUR_BEHIND_PX);
            blurActivity.setBackgroundBlurRadius(BACKGROUND_BLUR_PX);
        });
        waitForActivityIdle(blurActivity);
        final Rect windowFrame = getFloatingWindowFrame(blurActivity);

        assertOnScreenshot(screenshot -> {
            assertBlurBehind(screenshot, windowFrame);
            assertBackgroundBlurOverBlurBehind(screenshot, windowFrame);
        });

        setAndAssertForceBlurDisabled(true, blurActivity.mBlurEnabledListener);
        getInstrumentation().runOnMainSync(() -> {
            blurActivity.setBlurBehindRadius(BLUR_BEHIND_PX * 2);
            blurActivity.setBackgroundBlurRadius(BACKGROUND_BLUR_PX * 2);
        });
        waitForActivityIdle(blurActivity);

        assertOnScreenshot(screenshot -> {
            assertNoBackgroundBlur(screenshot, windowFrame);
            assertNoBlurBehind(screenshot, windowFrame);
        });

        getInstrumentation().runOnMainSync(() -> {
            blurActivity.setBackgroundColor(Color.TRANSPARENT);
        });
        waitForActivityIdle(blurActivity);
        verifyOnlyBackgroundImageVisible();

        setAndAssertForceBlurDisabled(false, blurActivity.mBlurEnabledListener);
        waitForActivityIdle(blurActivity);
        getInstrumentation().runOnMainSync(() -> {
            blurActivity.setBlurBehindRadius(BLUR_BEHIND_PX);
            blurActivity.setBackgroundBlurRadius(BACKGROUND_BLUR_PX);
        });
        waitForActivityIdle(blurActivity);

        assertOnScreenshot(screenshot -> {
            assertBlurBehind(screenshot, windowFrame);
            assertBackgroundBlurOverBlurBehind(screenshot, windowFrame);
        });
    }

    @Test
    @ApiTest(apis = {"android.view.WindowManager.LayoutParams#setBlurBehindRadius",
                     "android.R.styleable#Window_windowBlurBehindEnabled",
                     "android.view.Window#setBackgroundBlurRadius"})
    public void testBlurDestroyedAfterActivityFinished() {
        final BlurActivity blurActivity = startTestActivity(BlurActivity.class);
        getInstrumentation().runOnMainSync(() -> {
            blurActivity.setBlurBehindRadius(BLUR_BEHIND_PX);
            blurActivity.setBackgroundBlurRadius(BACKGROUND_BLUR_PX);
        });
        waitForActivityIdle(blurActivity);

        final Rect windowFrame = getFloatingWindowFrame(blurActivity);

        assertOnScreenshot(screenshot -> {
            assertBlurBehind(screenshot, windowFrame);
            assertBackgroundBlurOverBlurBehind(screenshot, windowFrame);
        });

        blurActivity.finish();
        mWmState.waitAndAssertActivityRemoved(blurActivity.getComponentName());
        waitForActivityIdle(blurActivity);

        verifyOnlyBackgroundImageVisible();
    }


    @Test
    @ApiTest(apis = {"android.view.WindowManager#addCrossWindowBlurEnabledListener",
                     "android.view.WindowManager#removeCrossWindowBlurEnabledListener"})
    public void testBlurListener() {
        final BlurActivity activity = startTestActivity(BlurActivity.class);
        Mockito.verify(activity.mBlurEnabledListener).accept(true);

        setAndAssertForceBlurDisabled(true, activity.mBlurEnabledListener);
        setAndAssertForceBlurDisabled(false, activity.mBlurEnabledListener);

        activity.finishAndRemoveTask();
        mWmState.waitAndAssertActivityRemoved(activity.getComponentName());

        Mockito.clearInvocations(activity.mBlurEnabledListener);
        setAndAssertForceBlurDisabled(true);
        Mockito.verifyNoMoreInteractions(activity.mBlurEnabledListener);
    }

    public static class BackgroundActivity extends FocusableActivity {
        private Insets mInsetsToBeIgnored = Insets.of(0, 0, 0, 0);

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getSplashScreen().setOnExitAnimationListener(view -> view.remove());

            setContentView(R.layout.background_image);
            WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

            View rootView = findViewById(android.R.id.content);
            rootView.setOnApplyWindowInsetsListener((v, insets) -> {
                Insets systemBarInsets = insets.getInsets(systemBars());

                int bottomLeft = getCornerRadius(insets, RoundedCorner.POSITION_BOTTOM_LEFT);
                int bottomRight = getCornerRadius(insets, RoundedCorner.POSITION_BOTTOM_RIGHT);
                int topLeft = getCornerRadius(insets, RoundedCorner.POSITION_TOP_LEFT);
                int topRight = getCornerRadius(insets, RoundedCorner.POSITION_TOP_RIGHT);

                // For each corner, inset into the apex at 45° so that the corners are excluded
                // from the screenshot region while preserving some amount on circular screens.
                final Insets roundedCornerInsets = Insets.of(
                        /* left= */ (int) (0.5 * Math.max(bottomLeft, topLeft)),
                        /* top= */ (int) (0.5 * Math.max(topLeft, topRight)),
                        /* right= */ (int) (0.5 * Math.max(topRight, bottomRight)),
                        /* bottom= */ (int) (0.5 * Math.max(bottomLeft, bottomRight))
                );

                mInsetsToBeIgnored = Insets.add(systemBarInsets, roundedCornerInsets);

                // Make the insets to be ignored symmetrical horizontally so that the later
                // computation can assume the widths of the blue area and the red area are the same.
                mInsetsToBeIgnored = Insets.of(
                        Math.max(mInsetsToBeIgnored.left, mInsetsToBeIgnored.right),
                        mInsetsToBeIgnored.top,
                        Math.max(mInsetsToBeIgnored.left, mInsetsToBeIgnored.right),
                        mInsetsToBeIgnored.bottom);
                return insets;
            });
        }

        Insets getInsetsToBeIgnored() {
            return mInsetsToBeIgnored;
        }

        private int getCornerRadius(WindowInsets insets, int position) {
            final RoundedCorner corner = insets.getRoundedCorner(position);

            // TODO (b/389045836): Make sure that Taskbar's "soft" rounded corners are reported
            // in insets.getRoundedCorner so that this adjustment won't be necessary any more.
            return Math.max((corner != null ? corner.getRadius() : 0),
                getResources().getDimensionPixelSize(R.dimen.taskbar_corner_radius));
        }
    }

    public static class BlurActivity extends FocusableActivity {
        public final Consumer<Boolean> mBlurEnabledListener = spy(new BlurListener());

        private int mBackgroundBlurRadius = 0;
        private int mBlurBehindRadius = 0;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.blur_activity);
            getWindow().setDecorFitsSystemWindows(false);
            getWindow().getAttributes().setFitInsetsSides(0);
        }

        @Override
        public void onAttachedToWindow() {
            super.onAttachedToWindow();
            getWindowManager().addCrossWindowBlurEnabledListener(getMainExecutor(),
                    mBlurEnabledListener);
        }

        @Override
        public void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            getWindowManager().removeCrossWindowBlurEnabledListener(mBlurEnabledListener);
        }

        void setBackgroundBlurRadius(int backgroundBlurRadius) {
            mBackgroundBlurRadius = backgroundBlurRadius;
            getWindow().setBackgroundBlurRadius(mBackgroundBlurRadius);
            setBackgroundColor(
                        mBackgroundBlurRadius > 0 && getWindowManager().isCrossWindowBlurEnabled()
                        ? Color.TRANSPARENT : NO_BLUR_BACKGROUND_COLOR);
        }

        void setBlurBehindRadius(int blurBehindRadius) {
            mBlurBehindRadius = blurBehindRadius;
            getWindow().getAttributes().setBlurBehindRadius(mBlurBehindRadius);
            getWindow().setAttributes(getWindow().getAttributes());
            getWindowManager().updateViewLayout(getWindow().getDecorView(),
                    getWindow().getAttributes());
        }

        void setBackgroundColor(int color) {
            getWindow().getDecorView().setBackgroundColor(color);
            getWindowManager().updateViewLayout(getWindow().getDecorView(),
                    getWindow().getAttributes());
        }

        public class BlurListener implements Consumer<Boolean> {
            @Override
            public void accept(Boolean enabled) {
                setBackgroundBlurRadius(mBackgroundBlurRadius);
                setBlurBehindRadius(mBlurBehindRadius);
            }
        }
    }

    /**
     * This activity is used to test 2 things:
     * 1. Blur behind does not work if WindowManager.LayoutParams.FLAG_BLUR_BEHIND is not set,
     *    respectively if windowBlurBehindEnabled is not set.
     * 2. Background blur does not work for opaque activities (where windowIsTranslucent is false)
     *
     * In the style of this activity windowBlurBehindEnabled is false and windowIsTranslucent is
     * false. As a result, we expect that neither blur behind, nor background blur is rendered,
     * even though they are requested with setBlurBehindRadius and setBackgroundBlurRadius.
     */
    public static class BadBlurActivity extends BlurActivity {
    }

    public static class BlurAttributesActivity extends FocusableActivity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.blur_activity);
            getWindow().setDecorFitsSystemWindows(false);
            getWindow().getAttributes().setFitInsetsSides(0);
        }
    }

    private <T extends FocusableActivity> T startTestActivity(Class<T> activityClass) {
        T activity = startActivity(activityClass);
        ComponentName activityName = activity.getComponentName();
        waitAndAssertResumedActivity(activityName, activityName + " must be resumed");
        waitForActivityIdle(activity);
        return activity;
    }

    private Rect getFloatingWindowFrame(Activity activity) {
        mWmState.computeState(activity.getComponentName());
        String windowName = getWindowName(activity.getComponentName());
        return new Rect(mWmState.getMatchingVisibleWindowState(windowName).get(0).getFrame());
    }

    private void waitForActivityIdle(@Nullable Activity activity) {
        // This helps with the test flakiness
        getInstrumentation().runOnMainSync(() -> {});
        UiDevice.getInstance(getInstrumentation()).waitForIdle();
        getInstrumentation().getUiAutomation().syncInputTransactions();
        if (activity != null) {
            mWmState.computeState(activity.getComponentName());
        }
    }

    private void setAndAssertForceBlurDisabled(boolean disable) {
        setAndAssertForceBlurDisabled(disable, null);
    }

    private void setAndAssertForceBlurDisabled(boolean disable,
                Consumer<Boolean> blurEnabledListener) {
        if (blurEnabledListener != null) {
            Mockito.clearInvocations(blurEnabledListener);
        }
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.DISABLE_WINDOW_BLURS, disable ? 1 : 0);
        if (blurEnabledListener != null) {
            Mockito.verify(blurEnabledListener, timeout(BROADCAST_WAIT_TIMEOUT))
                .accept(!disable);
        }
        PollingCheck.waitFor(BROADCAST_WAIT_TIMEOUT, () -> {
            return disable != mContext.getSystemService(WindowManager.class)
                    .isCrossWindowBlurEnabled();
        });
        assertTrue(!disable == mContext.getSystemService(WindowManager.class)
                    .isCrossWindowBlurEnabled());
    }

    private void assertOnScreenshot(Consumer<Bitmap> assertion) {
        final Bitmap screenshot = takeScreenshot();
        try {
            assertion.accept(screenshot);
        } catch (AssertionError failedAssertion) {
            mDumpOnFailure.dumpOnFailure("preAssertion", screenshot);
            waitForActivityIdle(null);
            mDumpOnFailure.dumpOnFailure("postAssertion", takeScreenshot());
            throw failedAssertion;
        }
    }

    private void assertBlurBehind(Bitmap screenshot, Rect windowFrame) {
        // From top of screenshot (accounting for extent on the edge) to the top of the centered
        // window.
        assertBlur(screenshot, BLUR_BEHIND_PX,
                new Rect(
                        mPixelTestBounds.left + BLUR_BEHIND_PX,
                        mPixelTestBounds.top + BLUR_BEHIND_PX,
                        mPixelTestBounds.right - BLUR_BEHIND_PX,
                        windowFrame.top));
        // From bottom of the centered window to bottom of screenshot accounting for extent on the
        // edge.
        assertBlur(screenshot, BLUR_BEHIND_PX,
                new Rect(
                        mPixelTestBounds.left + BLUR_BEHIND_PX,
                        windowFrame.bottom + 1,
                        mPixelTestBounds.right - BLUR_BEHIND_PX,
                        mPixelTestBounds.bottom - BLUR_BEHIND_PX));
    }

    private void assertBackgroundBlur(Bitmap screenshot, Rect windowFrame) {
        assertBlur(screenshot, BACKGROUND_BLUR_PX, windowFrame);
    }

    private void assertBackgroundBlurOverBlurBehind(Bitmap screenshot, Rect windowFrame) {
        assertBlur(screenshot, (int) Math.hypot(BACKGROUND_BLUR_PX, BLUR_BEHIND_PX), windowFrame);
    }

    private void verifyOnlyBackgroundImageVisible() {
        assertOnScreenshot(screenshot -> {
            assertNoBlurBehind(screenshot, new Rect());
        });
    }

    private void assertNoBlurBehind(Bitmap screenshot, Rect excludeFrame) {
        final int solidColorWidth = mBackgroundActivityBounds.width() / 2;

        forEachPixelInRect(screenshot, mPixelTestBounds, (x, y, actual) -> {
            if (!excludeFrame.contains(x, y)) {
                if ((x - mBackgroundActivityBounds.left) < solidColorWidth) {
                    return assertPixel(x, y, Color.BLUE, actual);
                } else if ((mBackgroundActivityBounds.right - x) <= solidColorWidth) {
                    return assertPixel(x, y, Color.RED, actual);
                }
            }
            return false;
        });
    }

    private void assertNoBackgroundBlur(Bitmap screenshot, Rect windowFrame) {
        forEachPixelInRect(screenshot, windowFrame,
                (x, y, actual) -> assertPixel(x, y, NO_BLUR_BACKGROUND_COLOR, actual));
    }

    private void assertBlur(Bitmap screenshot, int blurRadius, Rect blurRegion) {
        final double midX = (blurRegion.left + blurRegion.right - 1) / 2.0;

        // At 2 * radius there should be no visible blur effects.
        final int unaffectedBluePixelX = (int) Math.floor(midX) - blurRadius * 2 - 1;
        final int unaffectedRedPixelX = (int) Math.ceil(midX) + blurRadius * 2 + 1;

        // Check a smaller part of the blurred area than strictly necessary, in order to accept
        // various blur algorithm approximations used in RenderEngine
        final int blurAreaStartX =
                Math.max(blurRegion.left, (int) Math.floor(midX - blurRadius * 0.75f));
        final int blurAreaEndX =
                Math.min(blurRegion.right - 1, (int) Math.ceil(midX + blurRadius * 0.75f));

        // Check only a limited number of samples per row, instead of every pixel, in order to
        // tolerate approximations other than a full numerically-stable Gaussian kernel.
        final int samples = 6;

        for (int y = blurRegion.top; y < blurRegion.bottom; y++) {
            Color previousColor = null;
            int previousX = -1;
            for (int i = 0; i <= samples; i++) {
                final int x = blurAreaStartX + (i * (blurAreaEndX - blurAreaStartX)) / samples;
                final Color currentColor = screenshot.getColor(x, y);

                if (previousColor != null
                        && !(previousColor.blue() > currentColor.blue()
                                && previousColor.red() < currentColor.red())) {
                    fail(String.format(
                            "Expected a gradient between pixels (%d, %d) and (%d, %d): %s -> %s"
                                    + " should have LESS blue and MORE red",
                            previousX, y, x, y, previousColor, currentColor));
                }
                previousColor = currentColor;
                previousX = x;
            }
        }

        for (int y = blurRegion.top; y < blurRegion.bottom; y++) {
            final int unaffectedBluePixel = screenshot.getPixel(unaffectedBluePixelX, y);
            if (unaffectedBluePixel != Color.BLUE) {
                ColorUtils.verifyColor(
                        "Expected BLUE for pixel (x, y) = (" + unaffectedBluePixelX + ", " + y + ")",
                        Color.BLUE, unaffectedBluePixel, 1);
            }
            final int unaffectedRedPixel = screenshot.getPixel(unaffectedRedPixelX, y);
            if (unaffectedRedPixel != Color.RED) {
                ColorUtils.verifyColor(
                        "Expected RED for pixel (x, y) = (" + unaffectedRedPixelX + ", " + y + ")",
                        Color.RED, unaffectedRedPixel, 1);
            }
        }
    }

    @FunctionalInterface
    private interface PixelTester {
        /**
         * @return true if this pixel was checked, or false if it was ignored, for the purpose
         * of making sure that a reasonable number of pixels were checked.
         */
        boolean test(int x, int y, int color);
    }

    private static void forEachPixelInRect(Bitmap screenshot, Rect bounds, PixelTester tester) {
        @ColorInt int[] pixels = new int[bounds.height() * bounds.width()];
        screenshot.getPixels(pixels, 0, bounds.width(),
                bounds.left, bounds.top, bounds.width(), bounds.height());

        // We should be making an assertion on a reasonable minimum number of pixels. Count how
        // many pixels were actually checked so that we can fail if we didn't check enough.
        int checkedPixels = 0;

        int i = 0;
        for (int y = bounds.top; y < bounds.bottom; y++) {
            for (int x = bounds.left; x < bounds.right; x++, i++) {
                if (tester.test(x, y, pixels[i])) {
                    checkedPixels++;
                }
            }
        }

        assertThat(checkedPixels).isGreaterThan(15);
    }

    /**
     * Wrapper around verifyColor to speed up the test by avoiding constructing an error string
     * unless it will be used.
     */
    private static boolean assertPixel(int x, int y, @ColorInt int expected, @ColorInt int actual) {
        if (actual != expected) {
            ColorUtils.verifyColor(
                   "failed for pixel (x, y) = (" + x + ", " + y + ")", expected, actual, 1);
        }
        return true;
    }
}
