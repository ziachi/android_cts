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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.cts.ForegroundServiceUtil;
import android.media.cts.LocalMediaProjectionHelperService;
import android.media.cts.LocalMediaProjectionSecondaryService;
import android.media.cts.MediaProjectionRule;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.FrameworkSpecificTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test {@link MediaProjectionManager}.
 *
 * Run with:
 * atest CtsMediaProjectionTestCases:MediaProjectionManagerTest
 */
@FrameworkSpecificTest
public class MediaProjectionManagerTest {
    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private final ComponentName mSecondaryFgs =
            new ComponentName(mContext, LocalMediaProjectionSecondaryService.class);

    @Rule public MediaProjectionRule mMediaProjectionRule = new MediaProjectionRule();

    @Before
    public void setUp() {
        runWithShellPermissionIdentity(() -> {
            mContext.getPackageManager().revokeRuntimePermission(
                    mContext.getPackageName(),
                    android.Manifest.permission.SYSTEM_ALERT_WINDOW,
                    new UserHandle(mContext.getUserId()));
        });
    }

    @ApiTest(apis = "android.media.projection.MediaProjectionManager#createScreenCaptureIntent")
    @Test
    public void testCreateScreenCaptureIntent() {
        assertThat(mMediaProjectionRule.getMediaProjectionManager().createScreenCaptureIntent())
                .isNotNull();
        assertThat(
                        mMediaProjectionRule
                                .getMediaProjectionManager()
                                .createScreenCaptureIntent(
                                        MediaProjectionConfig.createConfigForDefaultDisplay()))
                .isNotNull();
        assertThat(
                        mMediaProjectionRule
                                .getMediaProjectionManager()
                                .createScreenCaptureIntent(
                                        MediaProjectionConfig.createConfigForUserChoice()))
                .isNotNull();
    }

    @ApiTest(apis = "android.media.projection.MediaProjectionManager#getMediaProjection")
    @Test
    public void testGetMediaProjection() throws Exception {
        // Launch the activity.
        MediaProjection mediaProjection = mMediaProjectionRule.startMediaProjection();
        // Ensure MediaProjection instance is valid.
        assertThat(mediaProjection).isNotNull();
    }

    @Test
    public void testGetMediaProjectionSecondaryProcessFgs() throws Exception {
        // Launch the activity, with a MediaProjection FGS running from another process of this app
        MediaProjection mediaProjection =
                mMediaProjectionRule.startMediaProjection(mSecondaryFgs.getClassName());
        // Ensure MediaProjection instance is valid.
        assertThat(mediaProjection).isNotNull();
    }

    @Test
    public void testGetMediaProjectionWithOtherFgs() throws Exception {
        final ComponentName name =
                new ComponentName(mContext, LocalMediaProjectionHelperService.class);
        final long timeOutMs = 5000L * HW_TIMEOUT_MULTIPLIER;
        final CountDownLatch[] latchHolder = new CountDownLatch[2];
        final Runnable helperFgsStarted = () -> {
            latchHolder[0].countDown();
        };
        final Runnable helperFgsStopped = () -> {
            latchHolder[0].countDown();
        };

        // Start a FGS with a type other than the "mediaProjection"
        latchHolder[0] = new CountDownLatch(1);
        ForegroundServiceUtil.requestStartForegroundService(mContext, name,
                helperFgsStarted, helperFgsStopped);
        assertTrue("Can't start FGS", latchHolder[0].await(timeOutMs, TimeUnit.MILLISECONDS));

        // Launch the activity, with a media projection FGS running from another process of this app
        MediaProjection mediaProjection =
                mMediaProjectionRule.startMediaProjection(mSecondaryFgs.getClassName());

        // Ensure MediaProjection instance is valid.
        assertThat(mediaProjection).isNotNull();

        // Register a callback to the MediaProjection instance.
        latchHolder[1] = new CountDownLatch(1);
        mMediaProjectionRule.registerCallback(
                new MediaProjection.Callback() {
                    @Override
                    public void onStop() {
                        latchHolder[1].countDown();
                    }
                });

        // Stop the first FGS.
        latchHolder[0] = new CountDownLatch(1);
        mContext.stopService(new Intent().setComponent(name));
        assertTrue("Can't stop FGS", latchHolder[0].await(timeOutMs, TimeUnit.MILLISECONDS));

        // Now the MediaProjection instance should still be valid.
        assertFalse(
                "MediaProjection stopped", latchHolder[1].await(timeOutMs, TimeUnit.MILLISECONDS));
    }

    @ApiTest(apis = "android.media.projection.MediaProjectionManager#getMediaProjection")
    @Test
    public void testGetMediaProjectionWithOtherFgsAlter() throws Exception {
        final ComponentName name =
                new ComponentName(mContext, LocalMediaProjectionHelperService.class);
        final long timeOutMs = 5000L * HW_TIMEOUT_MULTIPLIER;
        final CountDownLatch[] latchHolder = new CountDownLatch[2];
        final Runnable helperFgsStarted = () -> {
            latchHolder[0].countDown();
        };
        final Runnable helperFgsStopped = () -> {
            latchHolder[0].countDown();
        };

        // Launch the activity, with a media projection FGS running from another process of this app
        MediaProjection mediaProjection =
                mMediaProjectionRule.startMediaProjection(mSecondaryFgs.getClassName());

        // Ensure MediaProjection instance is valid.
        assertThat(mediaProjection).isNotNull();

        // Register a callback to the MediaProjection instance.
        latchHolder[1] = new CountDownLatch(1);
        mMediaProjectionRule.registerCallback(
                new MediaProjection.Callback() {
                    @Override
                    public void onStop() {
                        latchHolder[1].countDown();
                    }
                });

        // Start a FGS with a type other than the "mediaProjection"
        latchHolder[0] = new CountDownLatch(1);
        ForegroundServiceUtil.requestStartForegroundService(mContext, name,
                helperFgsStarted, helperFgsStopped);
        assertTrue("Can't start FGS", latchHolder[0].await(timeOutMs, TimeUnit.MILLISECONDS));

        // Stop the second FGS.
        latchHolder[0] = new CountDownLatch(1);
        mContext.stopService(new Intent().setComponent(name));
        assertTrue("Can't stop FGS", latchHolder[0].await(timeOutMs, TimeUnit.MILLISECONDS));

        // Now the MediaProjection instance should still be valid.
        assertFalse(
                "MediaProjection stopped", latchHolder[1].await(timeOutMs, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testGetMediaProjectionMultipleProjections() throws Exception {
        final long timeOutMs = 5000L * HW_TIMEOUT_MULTIPLIER;
        final CountDownLatch[] latchHolder = new CountDownLatch[2];

        // Launch the first activity with a media projection
        MediaProjection mediaProjection = mMediaProjectionRule.startMediaProjection();
        // Ensure MediaProjection instance is valid.
        assertThat(mediaProjection).isNotNull();

        // Register a callback to the first MediaProjection instance.
        latchHolder[0] = new CountDownLatch(1);
        mMediaProjectionRule.registerCallback(
                new MediaProjection.Callback() {
                    @Override
                    public void onStop() {
                        latchHolder[0].countDown();
                    }
                });

        // Launch the second activity, with a media projection FGS running from another process
        MediaProjection mediaProjection2 =
                mMediaProjectionRule.startMediaProjection(mSecondaryFgs.getClassName());

        final MediaProjection.Callback callback2 = new MediaProjection.Callback() {
            @Override
            public void onStop() {
                latchHolder[1].countDown();
            }
        };
        try {
            // Ensure the second MediaProjection instance is valid.
            assertThat(mediaProjection2).isNotNull();

            latchHolder[1] = new CountDownLatch(1);
            // Register a callback to the second MediaProjection instance.
            mediaProjection2.registerCallback(callback2, new Handler(Looper.getMainLooper()));

            // Check that first projection IS stopped, but second projection IS NOT stopped
            assertTrue(
                    "First MediaProjection was not stopped",
                    latchHolder[0].await(timeOutMs, TimeUnit.MILLISECONDS));
            assertFalse(
                    "Second projection was stopped",
                    latchHolder[1].await(timeOutMs, TimeUnit.MILLISECONDS));
        } finally {
            mediaProjection2.unregisterCallback(callback2);
            mediaProjection2.stop();
        }
    }

    @ApiTest(apis = "android.media.projection.MediaProjectionManager#getMediaProjection")
    @Test
    public void testGetMediaProjection_displayConfig() throws Exception {
        MediaProjection mediaProjection =
                mMediaProjectionRule.startMediaProjection(
                        MediaProjectionConfig.createConfigForDefaultDisplay());
        assertThat(mediaProjection).isNotNull();
    }

    @ApiTest(apis = "android.media.projection.MediaProjectionManager#getMediaProjection")
    @Test
    public void testGetMediaProjection_usersChoiceConfig() throws Exception {
        MediaProjection mediaProjection =
                mMediaProjectionRule.startMediaProjection(
                        MediaProjectionConfig.createConfigForUserChoice());
        assertThat(mediaProjection).isNotNull();
    }
}
