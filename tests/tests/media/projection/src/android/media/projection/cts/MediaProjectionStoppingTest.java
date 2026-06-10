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
package android.media.projection.cts;

import static android.server.wm.BuildUtils.HW_TIMEOUT_MULTIPLIER;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.cts.MediaProjectionRule;
import android.media.projection.MediaProjection;
import android.os.UserHandle;
import android.server.wm.LockScreenSession;
import android.server.wm.WindowManagerStateHelper;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.FrameworkSpecificTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test {@link MediaProjection} stopping behavior.
 *
 * Run with:
 * atest CtsMediaProjectionTestCases:MediaProjectionStoppingTest
 */
@FrameworkSpecificTest
public class MediaProjectionStoppingTest {
    @Rule public MediaProjectionRule mMediaProjectionRule = new MediaProjectionRule();

    private Context mContext;
    private int mTimeoutMs;
    private LockScreenSession mLockScreenSession;

    @Before
    public void setUp() throws InterruptedException {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        runWithShellPermissionIdentity(
                () -> {
                    mContext.getPackageManager()
                            .revokeRuntimePermission(
                                    mContext.getPackageName(),
                                    Manifest.permission.SYSTEM_ALERT_WINDOW,
                                    new UserHandle(mContext.getUserId()));
                });
        mTimeoutMs = 1000 * HW_TIMEOUT_MULTIPLIER;

        final WindowManagerStateHelper wmState = new WindowManagerStateHelper();
        mLockScreenSession = new LockScreenSession(InstrumentationRegistry.getInstrumentation(),
                wmState);
    }

    @After
    public void cleanup() {
        mLockScreenSession.close();
    }

    @Test
    @ApiTest(apis = "android.media.projection.MediaProjection.Callback#onStop")
    public void testMediaProjectionStopsOnKeyguard() throws Exception {
        assumeTrue(mContext.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_SECURE_LOCK_SCREEN));

        mMediaProjectionRule.startMediaProjection();

        CountDownLatch latch = new CountDownLatch(1);
        mMediaProjectionRule.registerCallback(
                new MediaProjection.Callback() {
                    @Override
                    public void onStop() {
                        latch.countDown();
                    }
                });
        mMediaProjectionRule.createVirtualDisplay();

        try {
            mLockScreenSession.sleepDevice();

            assertWithMessage("MediaProjection not stopped in " + mTimeoutMs + "ms")
                    .that(latch.await(mTimeoutMs, TimeUnit.MILLISECONDS)).isTrue();
        } finally {
            mLockScreenSession.wakeUpDevice();
            mLockScreenSession.unlockDevice();
        }
    }

    @Test
    @ApiTest(apis = "android.media.projection.MediaProjection.Callback#onStop")
    public void testMediaProjectionWithoutDisplayDoesNotStopOnKeyguard() throws Exception {
        assumeTrue(mContext.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_SECURE_LOCK_SCREEN));
        mMediaProjectionRule.startMediaProjection();

        CountDownLatch latch = new CountDownLatch(1);
        mMediaProjectionRule.registerCallback(
                new MediaProjection.Callback() {
                    @Override
                    public void onStop() {
                        latch.countDown();
                    }
                });

        try {
            mLockScreenSession.sleepDevice();

            assertWithMessage("MediaProjection was stopped unexpectedly")
                    .that(latch.await(mTimeoutMs, TimeUnit.MILLISECONDS)).isFalse();
        } finally {
            mLockScreenSession.wakeUpDevice();
            mLockScreenSession.unlockDevice();
        }
    }
}
