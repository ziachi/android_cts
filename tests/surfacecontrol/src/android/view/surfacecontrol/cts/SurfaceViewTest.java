/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.view.surfacecontrol.cts;

import static android.server.wm.BuildUtils.HW_TIMEOUT_MULTIPLIER;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.Region;
import android.os.IBinder;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.server.wm.CtsWindowInfoUtils;
import android.util.Pair;
import android.view.SurfaceControlViewHost;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.flags.Flags;
import android.widget.Button;
import android.window.WindowInfosListenerForTest;

import androidx.test.annotation.UiThreadTest;
import androidx.test.filters.MediumTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.WidgetTestUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

@Presubmit
@MediumTest
@RunWith(AndroidJUnit4.class)
public class SurfaceViewTest {
    private SurfaceViewCtsActivity mActivity;
    private SurfaceViewCtsActivity.TestSurfaceView mTestSurfaceView;
    private static final long WAIT_TIME_SECONDS = 5L * HW_TIMEOUT_MULTIPLIER;

    @Rule
    public ActivityTestRule<SurfaceViewCtsActivity> mActivityRule =
            new ActivityTestRule<>(SurfaceViewCtsActivity.class);
    private SurfaceControlViewHost mSurfaceControlViewHost;
    private SurfaceControlViewHost.SurfacePackage mSurfacePackage;

    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
        mTestSurfaceView = mActivity.getSurfaceView();
        CtsWindowInfoUtils.waitForWindowFocus(mTestSurfaceView, true);
    }

    @UiThreadTest
    @Test
    public void testConstructor() {
        new SurfaceView(mActivity);
        new SurfaceView(mActivity, null);
        new SurfaceView(mActivity, null, 0);
    }

    @Test
    public void testSurfaceView() {
        final int left = 40;
        final int top = 30;
        final int right = 320;
        final int bottom = 240;

        assertTrue(mTestSurfaceView.isDraw());
        assertTrue(mTestSurfaceView.isOnAttachedToWindow());
        assertTrue(mTestSurfaceView.isDispatchDraw());
        assertTrue(mTestSurfaceView.isSurfaceCreatedCalled());
        assertTrue(mTestSurfaceView.isSurfaceChanged());

        assertTrue(mTestSurfaceView.isOnWindowVisibilityChanged());
        int expectedVisibility = mTestSurfaceView.getVisibility();
        int actualVisibility = mTestSurfaceView.getVInOnWindowVisibilityChanged();
        assertEquals(expectedVisibility, actualVisibility);

        assertTrue(mTestSurfaceView.isOnMeasureCalled());
        int expectedWidth = mTestSurfaceView.getMeasuredWidth();
        int expectedHeight = mTestSurfaceView.getMeasuredHeight();
        int actualWidth = mTestSurfaceView.getWidthInOnMeasure();
        int actualHeight = mTestSurfaceView.getHeightInOnMeasure();
        assertEquals(expectedWidth, actualWidth);
        assertEquals(expectedHeight, actualHeight);

        Region region = new Region();
        region.set(left, top, right, bottom);
        assertTrue(mTestSurfaceView.gatherTransparentRegion(region));

        mTestSurfaceView.setFormat(PixelFormat.TRANSPARENT);
        assertFalse(mTestSurfaceView.gatherTransparentRegion(region));

        SurfaceHolder actual = mTestSurfaceView.getHolder();
        assertNotNull(actual);
        assertTrue(actual instanceof SurfaceHolder);
    }

    /**
     * check point:
     * check surfaceView size before and after layout
     */
    @UiThreadTest
    @Test
    public void testOnSizeChanged() {
        final int left = 40;
        final int top = 30;
        final int right = 320;
        final int bottom = 240;

        // change the SurfaceView size
        int beforeLayoutWidth = mTestSurfaceView.getWidth();
        int beforeLayoutHeight = mTestSurfaceView.getHeight();
        mTestSurfaceView.resetOnSizeChangedFlag(false);
        assertFalse(mTestSurfaceView.isOnSizeChangedCalled());
        mTestSurfaceView.layout(left, top, right, bottom);
        assertTrue(mTestSurfaceView.isOnSizeChangedCalled());
        assertEquals(beforeLayoutWidth, mTestSurfaceView.getOldWidth());
        assertEquals(beforeLayoutHeight, mTestSurfaceView.getOldHeight());
        assertEquals(right - left, mTestSurfaceView.getWidth());
        assertEquals(bottom - top, mTestSurfaceView.getHeight());
    }

    /**
     * check point:
     * check surfaceView scroll X and y before and after scrollTo
     */
    @UiThreadTest
    @Test
    public void testOnScrollChanged() {
        final int scrollToX = 200;
        final int scrollToY = 200;

        int oldHorizontal = mTestSurfaceView.getScrollX();
        int oldVertical = mTestSurfaceView.getScrollY();
        assertFalse(mTestSurfaceView.isOnScrollChanged());
        mTestSurfaceView.scrollTo(scrollToX, scrollToY);
        assertTrue(mTestSurfaceView.isOnScrollChanged());
        assertEquals(oldHorizontal, mTestSurfaceView.getOldHorizontal());
        assertEquals(oldVertical, mTestSurfaceView.getOldVertical());
        assertEquals(scrollToX, mTestSurfaceView.getScrollX());
        assertEquals(scrollToY, mTestSurfaceView.getScrollY());
    }

    @Test
    public void testOnDetachedFromWindow() {
        assertFalse(mTestSurfaceView.isDetachedFromWindow());
        assertTrue(mTestSurfaceView.isShown());
        mActivityRule.finishActivity();
        PollingCheck.waitFor(() -> mTestSurfaceView.isDetachedFromWindow()
                && !mTestSurfaceView.isShown());
    }

    @Test
    public void surfaceInvalidatedWhileDetaching() throws Throwable {
        assertTrue(mTestSurfaceView.mSurface.isValid());
        assertFalse(mTestSurfaceView.isDetachedFromWindow());
        WidgetTestUtils.runOnMainAndLayoutSync(mActivityRule, () -> {
            ((ViewGroup) mTestSurfaceView.getParent()).removeView(mTestSurfaceView);
        }, false);
        assertTrue(mTestSurfaceView.isDetachedFromWindow());
        assertFalse(mTestSurfaceView.mSurface.isValid());
    }

    @Test
    public void testSurfaceRemainsValidWhileCanvasLocked() throws Throwable {
        assertFalse(mTestSurfaceView.isDetachedFromWindow());
        assertTrue(mTestSurfaceView.isShown());
        mTestSurfaceView.awaitSurfaceCreated(3, TimeUnit.SECONDS);
        assertTrue(mTestSurfaceView.isSurfaceCreatedCalled());
        Canvas canvas = mTestSurfaceView.getHolder().lockCanvas();
        assertNotNull(canvas);
        // Try to detach the surfaceview. Since the surface is locked by the lock canvas called,
        // the surface will remain valid.
        mActivityRule.getActivity().runOnUiThread(() ->
                ((ViewGroup) mTestSurfaceView.getParent()).removeView(mTestSurfaceView));
        assertTrue(mTestSurfaceView.mSurface.isValid());
        mTestSurfaceView.getHolder().unlockCanvasAndPost(canvas);
        PollingCheck.waitFor(() -> mTestSurfaceView.isDetachedFromWindow()
                && !mTestSurfaceView.isShown());
    }

    @Test
    @ApiTest(apis = {"android.view.SurfaceView#getChildSurfacePackage"})
    @RequiresFlagsEnabled(Flags.FLAG_SURFACE_VIEW_GET_SURFACE_PACKAGE)
    public void testSurfaceViewGetChildSurfacePackage() throws InterruptedException {
        mActivityRule.getActivity().runOnUiThread(() -> {
            assertNull(mTestSurfaceView.getChildSurfacePackage());

            mSurfaceControlViewHost =
                    new SurfaceControlViewHost(mActivity, mActivity.getDisplay(),
                        mTestSurfaceView.getHostToken());
            mSurfaceControlViewHost.setView(new Button(mActivity), 10, 10);
            mSurfacePackage = mSurfaceControlViewHost.getSurfacePackage();
            assertNotNull(mSurfacePackage);

            mTestSurfaceView.setChildSurfacePackage(mSurfacePackage);
        });

        assertTrue(CtsWindowInfoUtils.waitForNthWindowFromTop(
                Duration.ofSeconds(WAIT_TIME_SECONDS),
                () -> mSurfaceControlViewHost.getView().getWindowToken(), 1));

        mActivityRule.getActivity().runOnUiThread(() ->
                assertEquals(mSurfacePackage, mTestSurfaceView.getChildSurfacePackage())
        );
    }

    @Test
    @ApiTest(apis = {"android.view.SurfaceView#getChildSurfacePackage",
            "android.view.SurfaceView#clearChildSurfacePackage"})
    @RequiresFlagsEnabled(Flags.FLAG_SURFACE_VIEW_GET_SURFACE_PACKAGE)
    public void testSurfaceViewClearChildSurfacePackage() throws InterruptedException {
        mActivityRule.getActivity().runOnUiThread(() -> {
            assertNull(mTestSurfaceView.getChildSurfacePackage());

            mSurfaceControlViewHost =
                    new SurfaceControlViewHost(mActivity, mActivity.getDisplay(),
                            mTestSurfaceView.getHostToken());
            mSurfaceControlViewHost.setView(new Button(mActivity), 10, 10);
            mSurfacePackage = mSurfaceControlViewHost.getSurfacePackage();
            assertNotNull(mSurfacePackage);

            mTestSurfaceView.setChildSurfacePackage(mSurfacePackage);
        });

        assertTrue(CtsWindowInfoUtils.waitForNthWindowFromTop(
                Duration.ofSeconds(WAIT_TIME_SECONDS),
                () -> mSurfaceControlViewHost.getView().getWindowToken(), 1));

        mActivityRule.getActivity().runOnUiThread(() -> {
            assertEquals(mSurfacePackage, mTestSurfaceView.getChildSurfacePackage());
            mTestSurfaceView.clearChildSurfacePackage();
            assertNull(mTestSurfaceView.getChildSurfacePackage());
        });

        assertTrue(CtsWindowInfoUtils.waitForWindowInvisible(
                () ->  mSurfaceControlViewHost.getView().getWindowToken(),
                Duration.ofSeconds(WAIT_TIME_SECONDS)));
    }

    @Test
    @ApiTest(apis = {"android.view.SurfaceView#setCompositionOrder",
            "android.view.SurfaceView#getCompositionOrder"})
    @RequiresFlagsEnabled(Flags.FLAG_SURFACE_VIEW_SET_COMPOSITION_ORDER)
    public void testSurfaceViewSetZOrderWithVisibilityChange() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            assertEquals(-2, mTestSurfaceView.getCompositionOrder());

            mTestSurfaceView.setCompositionOrder(1 /* composition order */);
            assertEquals(1, mTestSurfaceView.getCompositionOrder());
        });

        assertTrue(CtsWindowInfoUtils.waitForSurfaceViewVisible(mTestSurfaceView));
        verifySurfaceViewZOrder(mTestSurfaceView, true /* isAboveActivity */);

        // Change the visibility of the SurfaceView.
        mActivityRule.runOnUiThread(() -> {
            mTestSurfaceView.setVisibility(View.INVISIBLE);
        });
        assertTrue(CtsWindowInfoUtils.waitForSurfaceViewInvisible(mTestSurfaceView));

        mActivityRule.runOnUiThread(() -> {
            mTestSurfaceView.setVisibility(View.VISIBLE);
        });
        assertTrue(CtsWindowInfoUtils.waitForSurfaceViewVisible(mTestSurfaceView));
        mActivityRule.runOnUiThread(() -> {
            assertEquals(1, mTestSurfaceView.getCompositionOrder());
        });
        verifySurfaceViewZOrder(mTestSurfaceView, true /* isAboveActivity */);
    }

    @Test
    @ApiTest(apis = {"android.view.SurfaceView#setCompositionOrder",
            "android.view.SurfaceView#getCompositionOrder"})
    @RequiresFlagsEnabled(Flags.FLAG_SURFACE_VIEW_SET_COMPOSITION_ORDER)
    public void testSurfaceViewSetZOrderWhenAttachingAndDetachingFromWindow()
            throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            assertFalse(mTestSurfaceView.isDetachedFromWindow());
            assertEquals(-2, mTestSurfaceView.getCompositionOrder());
        });
        verifySurfaceViewZOrder(mTestSurfaceView, false /* isAboveActivity */);

        mActivityRule.runOnUiThread(() -> {
            mTestSurfaceView.setCompositionOrder(1 /* composition order */);
            assertEquals(1, mTestSurfaceView.getCompositionOrder());
        });
        verifySurfaceViewZOrder(mTestSurfaceView, true /* isAboveActivity */);

        ViewGroup viewParent = (ViewGroup) mTestSurfaceView.getParent();

        // Detach the SurfaceView from the window.
        mActivityRule.runOnUiThread(() -> {
            viewParent.removeView(mTestSurfaceView);
            assertTrue(mTestSurfaceView.isDetachedFromWindow());
            assertFalse(mTestSurfaceView.mSurface.isValid());
        });

        // Attach the SurfaceView to the window.
        mActivityRule.runOnUiThread(() -> {
            viewParent.addView(mTestSurfaceView);
        });
        assertTrue(CtsWindowInfoUtils.waitForSurfaceViewVisible(mTestSurfaceView));

        mActivityRule.runOnUiThread(() -> {
            assertEquals(1, mTestSurfaceView.getCompositionOrder());
        });
        verifySurfaceViewZOrder(mTestSurfaceView, true /* isAboveActivity */);
    }

    @Test
    @ApiTest(apis = {"android.view.SurfaceView#setCompositionOrder",
            "android.view.SurfaceView#getCompositionOrder"})
    @RequiresFlagsEnabled(Flags.FLAG_SURFACE_VIEW_SET_COMPOSITION_ORDER)
    public void testSurfaceViewSetZOrderBeforeAttachingToWindow()
            throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            assertFalse(mTestSurfaceView.isDetachedFromWindow());
            assertEquals(-2, mTestSurfaceView.getCompositionOrder());
        });
        verifySurfaceViewZOrder(mTestSurfaceView, false /* isAboveActivity */);

        mActivityRule.runOnUiThread(() -> {
            // Detach the SurfaceView from the window.
            ViewGroup viewParent = (ViewGroup) mTestSurfaceView.getParent();
            viewParent.removeView(mTestSurfaceView);
            assertTrue(mTestSurfaceView.isDetachedFromWindow());
            assertFalse(mTestSurfaceView.mSurface.isValid());

            // Update the composition order when the SurfaceView is detached from the window.
            mTestSurfaceView.setCompositionOrder(2 /* composition order */);
            // Attach the SurfaceView to the window.
            viewParent.addView(mTestSurfaceView);
        });
        assertTrue(CtsWindowInfoUtils.waitForSurfaceViewVisible(mTestSurfaceView));
        mActivityRule.runOnUiThread(() -> {
            assertEquals(2, mTestSurfaceView.getCompositionOrder());
        });
        verifySurfaceViewZOrder(mTestSurfaceView, true /* isAboveActivity */);
    }

    @Test
    @ApiTest(apis = {"android.view.SurfaceView#setCompositionOrder",
            "android.view.SurfaceView#getCompositionOrder"})
    @RequiresFlagsEnabled(Flags.FLAG_SURFACE_VIEW_SET_COMPOSITION_ORDER)
    public void testSurfaceViewSetZOrderMultipleSurfaceViews()
            throws Throwable {
        // 4 extra SurfaceViews will be created. In total 5 SurfaceViews will be under test.
        int[] compositionOrders = new int[]{4, 3, 2, -3, -4};
        ArrayList<Pair<SurfaceView, Integer>> surfaceViews = new ArrayList<>();

        mActivityRule.runOnUiThread(() -> {
            for (int compositionOrder : compositionOrders) {
                SurfaceViewCtsActivity.TestSurfaceView surfaceView =
                        new SurfaceViewCtsActivity.TestSurfaceView(mActivity);
                mActivity.addSurfaceView(surfaceView);

                surfaceView.setCompositionOrder(compositionOrder);
                surfaceViews.add(new Pair<>(surfaceView, compositionOrder));
            }
        });

        for (Pair<SurfaceView, Integer> pair : surfaceViews) {
            CtsWindowInfoUtils.waitForWindowVisible(pair.first);
            mActivityRule.runOnUiThread(() -> {
                assertEquals(pair.second.intValue(), pair.first.getCompositionOrder());
            });
        }

        verifySurfaceViewZOrder(surfaceViews.get(2).first, true /* isAboveActivity */);
        verifySurfaceViewZOrder(surfaceViews.get(3).first, false /* isAboveActivity */);

        // Checks if the SurfaceViews are ordered by their compositionOrder.
        assertTrue(CtsWindowInfoUtils.waitForWindowInfos(windowInfos -> {
            int currentSurfaceViewIdx = 0;
            String surfaceViewHashCode =
                    getHashCode(surfaceViews.get(currentSurfaceViewIdx).first);

            for (int i = 0; i < windowInfos.size(); i++) {
                if (windowInfos.get(i).name.startsWith(surfaceViewHashCode)) {
                    currentSurfaceViewIdx++;

                    if (currentSurfaceViewIdx >= surfaceViews.size()) {
                        return true;
                    }

                    surfaceViewHashCode =
                            getHashCode(surfaceViews.get(currentSurfaceViewIdx).first);
                }
            }
            return false;
        }, Duration.ofSeconds(WAIT_TIME_SECONDS)));
    }

    private void verifySurfaceViewZOrder(SurfaceView surfaceView, boolean isAboveActivity)
            throws InterruptedException {
        assertTrue(CtsWindowInfoUtils.waitForWindowInfos(windowInfos -> {
            int activityIdx = -1;
            int surfaceViewIdx = -1;

            String surfaceViewHashCode = getHashCode(surfaceView);
            IBinder activityToken = mActivity.getSurfaceView().getWindowToken();

            for (int i = 0; i < windowInfos.size(); i++) {
                WindowInfosListenerForTest.WindowInfo windowInfo = windowInfos.get(i);
                if (activityIdx == -1 && windowInfo.windowToken != null
                        && windowInfo.windowToken.equals(activityToken)) {
                    activityIdx = i;
                }
                if (surfaceViewIdx == -1
                        && windowInfo.name.startsWith(surfaceViewHashCode)) {
                    surfaceViewIdx = i;
                }
            }

            return activityIdx != -1 && surfaceViewIdx != -1
                    && (surfaceViewIdx < activityIdx == isAboveActivity);
        }, Duration.ofSeconds(WAIT_TIME_SECONDS)));
    }

    private String getHashCode(Object obj) {
        return Integer.toHexString(System.identityHashCode(obj));
    }
}
