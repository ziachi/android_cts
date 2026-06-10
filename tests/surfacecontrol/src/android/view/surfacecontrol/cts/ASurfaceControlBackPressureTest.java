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

package android.view.surfacecontrol.cts;

import static android.server.wm.BuildUtils.HW_TIMEOUT_MULTIPLIER;
import static android.view.cts.util.ASurfaceControlTestUtils.nSurfaceTransaction_setEnableBackPressure;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.hardware.HardwareBuffer;
import android.server.wm.CtsWindowInfoUtils;
import android.util.Log;
import android.view.SurfaceControl;

import androidx.test.filters.LargeTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiObjectNotFoundException;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class ASurfaceControlBackPressureTest {
    private static final String TAG = "ASurfaceControlBackPressureTest";
    @Rule
    public ActivityTestRule<SurfaceViewCtsActivity> mActivityRule = new ActivityTestRule<>(
            SurfaceViewCtsActivity.class);
    @Rule
    public TestName mName = new TestName();
    SurfaceViewCtsActivity mActivity;

    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
    }

    @After
    public void tearDown() throws UiObjectNotFoundException {
        mActivityRule.finishActivity();
    }

    private SurfaceControl createChildSurfaceControl(boolean enableBackPressure)
            throws InterruptedException {
        // create a surface control for testing and reparent it to the activity window
        SurfaceControl surfaceControl = new SurfaceControl.Builder().setName(
                "testSurfaceTransaction_setEnableBackPressure").build();
        SurfaceControl.Transaction transaction =
                mActivity.getWindow().getDecorView().getRootSurfaceControl()
                        .buildReparentTransaction(surfaceControl);

        // set back pressure flag
        nSurfaceTransaction_setEnableBackPressure(transaction, surfaceControl, enableBackPressure);
        final CountDownLatch transactionCompleted = new CountDownLatch(1);
        transaction.addTransactionCompletedListener(Runnable::run,
                (unused) -> transactionCompleted.countDown());
        transaction.apply();
        assertTrue("Failed to receive all transaction completed callbacks. Num missing = "
                        + transactionCompleted.getCount(),
                transactionCompleted.await(5000L * HW_TIMEOUT_MULTIPLIER, TimeUnit.MILLISECONDS));
        return surfaceControl;
    }

    private long[] submitBuffers(SurfaceControl surfaceControl, int numFrames)
            throws InterruptedException {
        HardwareBuffer[] hardwareBuffers = new HardwareBuffer[2];
        for (int i = 0; i < hardwareBuffers.length; i++) {
            hardwareBuffers[i] = HardwareBuffer.create(100, // width
                    100, // height
                    HardwareBuffer.RGBA_8888, // format
                    1, // layers
                    HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE | HardwareBuffer.USAGE_COMPOSER_OVERLAY);
        }

        final long[] latchTimesNanos = new long[numFrames];
        final CountDownLatch lastTransactionCompleted = new CountDownLatch(numFrames);
        SurfaceControl.Transaction transaction = new SurfaceControl.Transaction();
        for (int i = 0; i < numFrames; i++) {
            int finalI = i;
            transaction.setBuffer(surfaceControl, hardwareBuffers[i % 2]);
            transaction.addTransactionCompletedListener(Runnable::run, (stats) -> {
                latchTimesNanos[finalI] = stats.getLatchTimeNanos();
                lastTransactionCompleted.countDown();
            });
            transaction.apply();
        }
        assertTrue("Failed to receive all transaction completed callbacks. Num missing = "
                        + lastTransactionCompleted.getCount(),
                lastTransactionCompleted.await(5000L * HW_TIMEOUT_MULTIPLIER,
                        TimeUnit.MILLISECONDS));
        return latchTimesNanos;
    }

    private int validateLatchTimes(long[] latchTimesNanos) {
        long previousLatchTime = -1;
        int numBuffersLatchedInSameVsync = 0;
        for (int i = 0; i < latchTimesNanos.length; i++) {
            final long latchTime = latchTimesNanos[i];

            // track buffers that were not latched
            if (latchTime == -1) {
                numBuffersLatchedInSameVsync++;
                continue;
            }

            // check if we had multiple latches within a vsync
            if (latchTime - previousLatchTime < TimeUnit.MILLISECONDS.toNanos(1)) {
                numBuffersLatchedInSameVsync++;
            }

            // check to see if we latched out of order
            assertTrue("unexpected latch time. previous buffer latch time = " + previousLatchTime
                            + " latch time = " + latchTime + " frameNum=" + i,
                    latchTime > previousLatchTime);

            Log.d(TAG, "previous buffer latch time = " + previousLatchTime + " latch time = "
                    + latchTime + " frameNum=" + i);
            previousLatchTime = latchTime;
        }
        return numBuffersLatchedInSameVsync;
    }

    @Test
    public void testSurfaceTransaction_setEnableBackPressure() throws Throwable {
        // wait for activity window to be visible
        CtsWindowInfoUtils.waitForWindowVisible(mActivity.getWindow().getDecorView());
        SurfaceControl surfaceControl = createChildSurfaceControl(true /* enableBackPressure */);
        final int numFrames = 100;
        long[] latchTimesNanos = submitBuffers(surfaceControl, numFrames);
        int numBuffersLatchedInSameVsync = validateLatchTimes(latchTimesNanos);
        assertEquals("Error: frames latched in same vsync" + numBuffersLatchedInSameVsync, 0,
                numBuffersLatchedInSameVsync);
    }

    @Test
    public void testSurfaceTransaction_defaultBackPressureDisabled() throws Throwable {
        // wait for activity window to be visible
        CtsWindowInfoUtils.waitForWindowVisible(mActivity.getWindow().getDecorView());
        SurfaceControl surfaceControl = createChildSurfaceControl(false /* enableBackPressure */);
        final int numFrames = 100;
        long[] latchTimesNanos = submitBuffers(surfaceControl, numFrames);
        int numBuffersLatchedInSameVsync = validateLatchTimes(latchTimesNanos);
        assertNotEquals("Error: No frames latched in same vsync", 0, numBuffersLatchedInSameVsync);
    }
}
