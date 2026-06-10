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
package android.media.projection.cts;

import static android.server.wm.BuildUtils.HW_TIMEOUT_MULTIPLIER;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.display.VirtualDisplay;
import android.media.cts.MediaProjectionRule;
import android.media.projection.MediaProjection;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.WindowManager;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.FrameworkSpecificTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test {@link MediaProjection} lifecycle & callbacks.
 *
 * Note that there are other tests verifying that screen capturing actually works correctly in
 * CtsWindowManagerDeviceTestCases.
 *
 * Run with:
 * atest CtsMediaProjectionTestCases:MediaProjectionTest
 */
@FrameworkSpecificTest
public class MediaProjectionTest {
    private static final String TAG = "MediaProjectionTest";
    private static final int RECORDING_WIDTH = 500;
    private static final int RECORDING_HEIGHT = 700;

    @Rule public MediaProjectionRule mMediaProjectionRule = new MediaProjectionRule();

    private MediaProjection.Callback mCallback = null;
    private Context mContext;
    private int mTimeoutMs;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        runWithShellPermissionIdentity(() -> {
            mContext.getPackageManager().revokeRuntimePermission(
                    mContext.getPackageName(),
                    android.Manifest.permission.SYSTEM_ALERT_WINDOW,
                    new UserHandle(mContext.getUserId()));
        });
        mTimeoutMs = 1000 * HW_TIMEOUT_MULTIPLIER;
    }

    /**
     * This test starts and stops a MediaProjection screen capture session using
     * MediaProjectionActivity.
     *
     * Currently, we check that we are able to draw overlay windows during the session but not
     * before
     * or after. (We request SYSTEM_ALERT_WINDOW permission, but it is not granted, so by default
     * we
     * cannot).
     */
    @Test
    public void testOverlayAllowedDuringScreenCapture() throws Exception {
        assertFalse(Settings.canDrawOverlays(mContext));

        MediaProjection mediaProjection = mMediaProjectionRule.startMediaProjection();
        assertTrue(Settings.canDrawOverlays(mContext));

        CountDownLatch latch = new CountDownLatch(1);
        mMediaProjectionRule.registerCallback(
                new MediaProjection.Callback() {
                    @Override
                    public void onStop() {
                        latch.countDown();
                    }
                });
        mediaProjection.stop();

        assertTrue("Could not stop the MediaProjection in " + mTimeoutMs + "ms",
                latch.await(mTimeoutMs, TimeUnit.MILLISECONDS));

        assertFalse(Settings.canDrawOverlays(mContext));
    }

    @ApiTest(apis = "android.media.projection.MediaProjection#createVirtualDisplay")
    @Test
    public void testCreateVirtualDisplay() throws Exception {
        mMediaProjectionRule.startMediaProjection();
        VirtualDisplay virtualDisplay =
                mMediaProjectionRule.createVirtualDisplay(RECORDING_WIDTH, RECORDING_HEIGHT);

        assertThat(virtualDisplay).isNotNull();
        Point virtualDisplayDimensions = new Point();
        virtualDisplay.getDisplay().getSize(virtualDisplayDimensions);
        assertThat(virtualDisplayDimensions).isEqualTo(
                new Point(RECORDING_WIDTH, RECORDING_HEIGHT));
    }

    @ApiTest(apis = "android.media.projection.MediaProjection#unregisterCallback")
    @Test
    public void testUnregisterCallback() throws Exception {
        MediaProjection mediaProjection = mMediaProjectionRule.startMediaProjection();

        CountDownLatch latch = new CountDownLatch(1);
        mCallback = new MediaProjection.Callback() {
            @Override
            public void onStop() {
                latch.countDown();
            }
        };
        mediaProjection.registerCallback(mCallback, new Handler(Looper.getMainLooper()));
        mediaProjection.unregisterCallback(mCallback);

        mMediaProjectionRule.createVirtualDisplay();
        mediaProjection.stop();
        assertFalse("Callback is not invoked after " + mTimeoutMs + " ms if unregistere",
                latch.await(mTimeoutMs, TimeUnit.MILLISECONDS));
    }

    @ApiTest(apis = {
            "android.media.projection.MediaProjection#registerCallback",
            "android.media.projection.MediaProjection#stop",
            "android.media.projection.MediaProjection.Callback#onStop"
    })
    @Test
    public void testCallbackOnStop() throws Exception {
        MediaProjection mediaProjection = mMediaProjectionRule.startMediaProjection();

        CountDownLatch latch = new CountDownLatch(1);
        mMediaProjectionRule.registerCallback(
                new MediaProjection.Callback() {
                    @Override
                    public void onStop() {
                        latch.countDown();
                    }
                });
        mMediaProjectionRule.createVirtualDisplay();
        mediaProjection.stop();

        assertTrue("Could not stop the MediaProjection in " + mTimeoutMs + "ms",
                latch.await(mTimeoutMs, TimeUnit.MILLISECONDS));
    }

    @ApiTest(apis = "android.media.projection.MediaProjection.Callback#onCapturedContentResize")
    @Test
    public void testCallbackOnCapturedContentResize() throws Exception {
        mMediaProjectionRule.startMediaProjection();

        CountDownLatch latch = new CountDownLatch(1);
        Point mContentSize = new Point();

        mMediaProjectionRule.registerCallback(
                new MediaProjection.Callback() {
                    @Override
                    public void onCapturedContentResize(int width, int height) {
                        mContentSize.x = width;
                        mContentSize.y = height;
                        latch.countDown();
                    }
                });
        mMediaProjectionRule.createVirtualDisplay();
        assertTrue(
                "Did not get callback after starting recording on the MediaProjection in "
                        + mTimeoutMs
                        + "ms",
                latch.await(mTimeoutMs, TimeUnit.MILLISECONDS));
        Rect maxWindowMetrics =
                mMediaProjectionRule
                        .getActivity()
                        .getSystemService(WindowManager.class)
                        .getMaximumWindowMetrics()
                        .getBounds();
        assertThat(mContentSize).isEqualTo(
                new Point(maxWindowMetrics.width(), maxWindowMetrics.height()));
    }

    @ApiTest(apis = "android.media.projection.MediaProjection"
            + ".Callback#onCapturedContentVisibilityChanged")
    @Test
    public void testCallbackOnCapturedContentVisibilityChanged() throws Exception {
        mMediaProjectionRule.startMediaProjection();
        CountDownLatch latch = new CountDownLatch(1);
        final boolean[] isVisibleUpdate = {false};
        mMediaProjectionRule.registerCallback(
                new MediaProjection.Callback() {
                    @Override
                    public void onCapturedContentVisibilityChanged(boolean isVisible) {
                        super.onCapturedContentVisibilityChanged(isVisible);
                        isVisibleUpdate[0] = isVisible;
                        latch.countDown();
                    }
                });
        mMediaProjectionRule.createVirtualDisplay();
        assertTrue("Did not get callback after starting recording on the MediaProjection in "
                        + mTimeoutMs + "ms",
                latch.await(mTimeoutMs, TimeUnit.MILLISECONDS));
        assertThat(isVisibleUpdate[0]).isTrue();
    }
}
