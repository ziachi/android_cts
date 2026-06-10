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

import static android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
import static android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
import static android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Insets;
import android.os.Bundle;
import android.platform.test.annotations.Presubmit;
import android.server.wm.IgnoreOrientationRequestSession;
import android.server.wm.WindowManagerStateHelper;
import android.server.wm.cts.testsdk34.R;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;

import androidx.test.filters.MediumTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@Presubmit
@MediumTest
@RunWith(AndroidJUnit4.class)
public class WindowSdk34Test {

    private Instrumentation mInstrumentation;
    private TestActivity mActivity;
    private Window mWindow;

    /** Used by {@link #setMayAffectDisplayRotation()}. */
    private WindowManagerStateHelper mWmState;
    private int mOriginalRotation = -1;

    @Rule
    public ActivityTestRule<TestActivity> mActivityRule =
            new ActivityTestRule<>(TestActivity.class);

    @Before
    public void setup() {
        mInstrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
        mWindow = mActivity.getWindow();
    }

    @After
    public void tearDown() {
        if (mOriginalRotation >= 0) {
            // The test might launch an activity that changes display rotation. Finish the
            // activity explicitly and wait for the original rotation to avoid the rotation
            // affects the next test.
            mActivityRule.finishActivity();
            mWmState.waitForRotation(mOriginalRotation);
        }
    }

    @Test
    public void testSetFitsContentForInsets_false() throws Throwable {
        mActivityRule.runOnUiThread(() -> mWindow.setDecorFitsSystemWindows(false));
        mInstrumentation.waitForIdleSync();
        assertEquals(mActivity.getContentView().getRootWindowInsets().getSystemWindowInsets(),
                mActivity.getLastInsets().getSystemWindowInsets());
    }

    @Test
    public void testSetFitsContentForInsets_defaultLegacy_sysuiFlags() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            mWindow.getDecorView().setSystemUiVisibility(SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
            mWindow.getDecorView().setSystemUiVisibility(SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        });
        mInstrumentation.waitForIdleSync();
        assertEquals(mActivity.getContentView().getRootWindowInsets().getSystemWindowInsets(),
                mActivity.getLastInsets().getSystemWindowInsets());
    }

    @Test
    public void testSetFitsContentForInsets_displayCutoutInsets_areApplied() throws Throwable {
        try (IgnoreOrientationRequestSession session =
                     new IgnoreOrientationRequestSession(false /* enable */)) {
            setMayAffectDisplayRotation();
            mActivityRule.runOnUiThread(() -> {
                mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                mWindow.setDecorFitsSystemWindows(true);
                WindowManager.LayoutParams attrs = mWindow.getAttributes();
                attrs.layoutInDisplayCutoutMode = LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
                mWindow.setAttributes(attrs);
            });
            mInstrumentation.waitForIdleSync();
            assertEquals(mActivity.getContentView().getRootWindowInsets().getSystemWindowInsets(),
                    mActivity.getAppliedInsets());
        }
    }

    /**
     * Stores the current rotation of device so {@link #tearDown()} can wait for the device to
     * restore to its previous rotation.
     */
    private void setMayAffectDisplayRotation() {
        mWmState = new WindowManagerStateHelper();
        mWmState.computeState();
        mOriginalRotation = mWmState.getRotation();
    }

    @Test
    public void testSetFitsContentForInsets_defaultLegacy_none() throws Throwable {
        mInstrumentation.waitForIdleSync();

        // We don't expect that we even got called.
        assertNull(mActivity.getLastInsets());
    }

    @Test
    public void testSetFitsContentForInsets_true() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            mWindow.setDecorFitsSystemWindows(true);
        });
        mInstrumentation.waitForIdleSync();

        // We don't expect that we even got called.
        assertNull(mActivity.getLastInsets());
    }

    @Test
    public void testSystemBarColors_fromResource() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            // Force mWindow to read system bar colors from resource.
            mWindow.getDecorView();

            assertEquals(
                    mActivity.getColor(R.color.status_bar_color),
                    mWindow.getStatusBarColor());
            assertEquals(
                    mActivity.getColor(R.color.navigation_bar_color),
                    mWindow.getNavigationBarColor());
            assertEquals(
                    mActivity.getColor(R.color.navigation_bar_divider_color),
                    mWindow.getNavigationBarDividerColor());
        });
    }

    @Test
    public void testSystemBarColors_fromMethod() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            mWindow.setStatusBarColor(Color.RED);
            mWindow.setNavigationBarColor(Color.GREEN);
            mWindow.setNavigationBarDividerColor(Color.BLUE);
            assertEquals(
                    Color.RED,
                    mWindow.getStatusBarColor());
            assertEquals(
                    Color.GREEN,
                    mWindow.getNavigationBarColor());
            assertEquals(
                    Color.BLUE,
                    mWindow.getNavigationBarDividerColor());
        });
    }

    public static class TestActivity extends Activity {

        private View mContentView = null;

        private WindowInsets mLastInsets;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getWindow().setDecorFitsSystemWindows(true);
            final View view = getContentView();
            view.setOnApplyWindowInsetsListener((v, insets) -> {
                mLastInsets = insets;
                return insets;
            });
            setContentView(view);
        }

        private View getContentView() {
            if (mContentView == null) {
                mContentView = new View(this);
            }
            return mContentView;
        }

        private Insets getAppliedInsets() {
            View view = (View) getContentView().getParent();
            int[] location = new int[2];
            view.getLocationInWindow(location);
            View decorView = getWindow().getDecorView();
            return Insets.of(location[0], location[1],
                    decorView.getWidth() - (location[0] + view.getWidth()),
                    decorView.getHeight() - (location[1] + view.getHeight()));
        }

        private WindowInsets getLastInsets() {
            return mLastInsets;
        }
    }
}
