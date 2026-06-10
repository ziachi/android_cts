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

package android.os.health.cts;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.os.CpuHeadroomParams;
import android.os.Flags;
import android.os.GpuHeadroomParams;
import android.os.health.SystemHealthManager;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Pair;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provides test cases for android.os.health.SystemHealthManager headroom APIs.
 */
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class HeadroomTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private SystemHealthManager mManager;
    private boolean mCpuHeadroomSupported = true;
    private long mCpuHeadroomInterval = 1000;
    private boolean mGpuHeadroomSupported = true;
    private long mGpuHeadroomInterval = 1000;

    private void validateHeadroom(float headroom) {
        if (!Float.isNaN(headroom)) {
            assertTrue("Headroom should be between 0.0 and 100.0, but was " + headroom,
                    headroom >= 0.0f && headroom <= 100.0f);
        }
    }

    @Before
    public void setup() {
        getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.DEVICE_POWER);
        final Context context = getInstrumentation().getTargetContext();
        mManager = context.getSystemService(SystemHealthManager.class);
        assertNotNull(mManager);
        try {
            validateHeadroom(mManager.getCpuHeadroom(new CpuHeadroomParams.Builder().build()));
        } catch (UnsupportedOperationException e) {
            mCpuHeadroomSupported = false;
        }
        if (mCpuHeadroomSupported) {
            mCpuHeadroomInterval = mManager.getCpuHeadroomMinIntervalMillis();
            assertTrue(mCpuHeadroomInterval > 0);
            assertTrue(mCpuHeadroomInterval < 10000);
        }
        try {
            validateHeadroom(mManager.getGpuHeadroom(new GpuHeadroomParams.Builder().build()));
        } catch (UnsupportedOperationException e) {
            mGpuHeadroomSupported = false;
        }
        if (mGpuHeadroomSupported) {
            mGpuHeadroomInterval = mManager.getGpuHeadroomMinIntervalMillis();
            assertTrue(mGpuHeadroomInterval > 0);
            assertTrue(mGpuHeadroomInterval < 10000);
        }
    }

    @After
    public void tearDown() throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();
    }

    private void checkCpuHeadroomSupport() {
        assumeTrue("Skipping test as CPU headroom is unsupported", mCpuHeadroomSupported);
    }

    private void checkGpuHeadroomSupport() {
        assumeTrue("Skipping test as GPU headroom is unsupported", mGpuHeadroomSupported);
    }

    private void runCheckCpuHeadroom(CpuHeadroomParams params) throws Exception {
        Thread.sleep(mCpuHeadroomInterval);
        validateHeadroom(mManager.getCpuHeadroom(params));
    }

    private void runCheckGpuHeadroom(GpuHeadroomParams params) throws Exception {
        Thread.sleep(mGpuHeadroomInterval);
        validateHeadroom(mManager.getGpuHeadroom(params));
    }

    private int[] createThreads(int threadCount, CountDownLatch stopLatch)
            throws InterruptedException {
        int[] tids = new int[threadCount];
        AtomicInteger k = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(threadCount);
        for (int j = 0; j < threadCount; j++) {
            Thread thread = new Thread(() -> {
                try {
                    tids[k.getAndIncrement()] = android.os.Process.myTid();
                    latch.countDown();
                    stopLatch.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
            thread.start();
        }
        latch.await();
        return tids;
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CPU_GPU_HEADROOMS)
    public void testCpuHeadroomParams_toBuilder() {
        CpuHeadroomParams params =
                new CpuHeadroomParams.Builder()
                        .setTids(1, 2, 3)
                        .setCalculationType(CpuHeadroomParams.CPU_HEADROOM_CALCULATION_TYPE_AVERAGE)
                        .setCalculationWindowMillis(1000)
                        .build();

        params = params.toBuilder().build();
        assertArrayEquals(new int[] {1, 2, 3}, params.getTids());
        assertEquals(
                CpuHeadroomParams.CPU_HEADROOM_CALCULATION_TYPE_AVERAGE,
                params.getCalculationType());
        assertEquals(1000, params.getCalculationWindowMillis());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CPU_GPU_HEADROOMS)
    public void testCpuHeadroomParams_invalidCalculationType() {
        CpuHeadroomParams.Builder builder = new CpuHeadroomParams.Builder();
        assertThrows(IllegalArgumentException.class, () -> builder.setCalculationType(-1));
        assertThrows(IllegalArgumentException.class, () -> builder.setCalculationType(100));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CPU_GPU_HEADROOMS)
    public void testCpuHeadroomParams_invalidWindow() {
        CpuHeadroomParams.Builder builder = new CpuHeadroomParams.Builder();
        assertThrows(IllegalArgumentException.class, () -> builder.setCalculationWindowMillis(0));
        assertThrows(IllegalArgumentException.class, () -> builder.setCalculationWindowMillis(-1));
        checkCpuHeadroomSupport();
        final Pair<Integer, Integer> validRange = mManager.getCpuHeadroomCalculationWindowRange();
        builder.setCalculationWindowMillis(validRange.first - 1);
        assertThrows(
                IllegalArgumentException.class, () -> mManager.getCpuHeadroom(builder.build()));
        builder.setCalculationWindowMillis(validRange.second + 1);
        assertThrows(
                IllegalArgumentException.class, () -> mManager.getCpuHeadroom(builder.build()));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CPU_GPU_HEADROOMS)
    public void testCpuHeadroomParams_invalidTids() {
        CpuHeadroomParams.Builder builder = new CpuHeadroomParams.Builder();
        assertThrows(IllegalArgumentException.class, () -> builder.setTids(0));
        assertThrows(IllegalArgumentException.class, () -> builder.setTids(-1));
        checkCpuHeadroomSupport();
        builder.setTids(1, 2, 3, 4, 5, 6);
        assertThrows(
                IllegalArgumentException.class, () -> mManager.getCpuHeadroom(builder.build()));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CPU_GPU_HEADROOMS)
    public void testGpuHeadroomParams_invalidCalculationType() {
        GpuHeadroomParams.Builder builder = new GpuHeadroomParams.Builder();
        assertThrows(IllegalArgumentException.class, () -> builder.setCalculationType(-1));
        assertThrows(IllegalArgumentException.class, () -> builder.setCalculationType(100));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CPU_GPU_HEADROOMS)
    public void testGpuHeadroomParams_invalidWindow() {
        GpuHeadroomParams.Builder builder = new GpuHeadroomParams.Builder();
        assertThrows(IllegalArgumentException.class, () -> builder.setCalculationWindowMillis(0));
        assertThrows(IllegalArgumentException.class, () -> builder.setCalculationWindowMillis(-1));
        checkGpuHeadroomSupport();
        final Pair<Integer, Integer> validRange = mManager.getCpuHeadroomCalculationWindowRange();
        builder.setCalculationWindowMillis(validRange.first - 1);
        assertThrows(
                IllegalArgumentException.class, () -> mManager.getGpuHeadroom(builder.build()));
        builder.setCalculationWindowMillis(validRange.second + 1);
        assertThrows(
                IllegalArgumentException.class, () -> mManager.getGpuHeadroom(builder.build()));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CPU_GPU_HEADROOMS)
    public void testGetMaxCpuHeadroomTidsSize() throws Exception {
        checkCpuHeadroomSupport();
        int size = mManager.getMaxCpuHeadroomTidsSize();
        assertTrue("Expected positive max CPU headroom TID size but got " + size, size > 0);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CPU_GPU_HEADROOMS)
    public void testGetCpuHeadroom_default() throws Exception {
        checkCpuHeadroomSupport();
        CpuHeadroomParams params = new CpuHeadroomParams.Builder().build();
        assertEquals(CpuHeadroomParams.CPU_HEADROOM_CALCULATION_TYPE_MIN,
                params.getCalculationType());
        runCheckCpuHeadroom(params);
        runCheckCpuHeadroom(null);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CPU_GPU_HEADROOMS)
    public void testGetCpuHeadroom_average() throws Exception {
        checkCpuHeadroomSupport();
        CpuHeadroomParams params =
                new CpuHeadroomParams.Builder()
                        .setCalculationType(CpuHeadroomParams.CPU_HEADROOM_CALCULATION_TYPE_AVERAGE)
                        .build();
        assertEquals(CpuHeadroomParams.CPU_HEADROOM_CALCULATION_TYPE_AVERAGE,
                params.getCalculationType());
        runCheckCpuHeadroom(params);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CPU_GPU_HEADROOMS)
    public void testGetCpuHeadroom_customWindow() throws Exception {
        checkCpuHeadroomSupport();
        final Pair<Integer, Integer> range = mManager.getGpuHeadroomCalculationWindowRange();
        final int expectedMinMillis = 1000;
        assertTrue(
                "Min CPU headroom calculation window should be less or equal to "
                        + expectedMinMillis,
                range.first <= expectedMinMillis);
        final int expectedMaxMillis = 10000;
        assertTrue(
                "Max CPU headroom calculation window should be greater or equal to "
                        + expectedMaxMillis,
                range.second >= expectedMaxMillis);
        CpuHeadroomParams params;
        params = new CpuHeadroomParams.Builder().setCalculationWindowMillis(range.first).build();
        assertEquals((int) range.first, params.getCalculationWindowMillis());
        runCheckCpuHeadroom(params);
        params = new CpuHeadroomParams.Builder().setCalculationWindowMillis(range.second).build();
        assertEquals((int) range.second, params.getCalculationWindowMillis());
        runCheckCpuHeadroom(params);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CPU_GPU_HEADROOMS)
    public void testGetCpuHeadroom_customTids() throws Exception {
        checkCpuHeadroomSupport();
        CountDownLatch latch = new CountDownLatch(1);
        int[] tids = createThreads(mManager.getMaxCpuHeadroomTidsSize(), latch);
        Thread.sleep(mCpuHeadroomInterval);
        CpuHeadroomParams params = new CpuHeadroomParams.Builder().setTids(tids).build();
        runCheckCpuHeadroom(params);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CPU_GPU_HEADROOMS)
    public void testGetCpuHeadroom_notAppTids() throws Exception {
        checkCpuHeadroomSupport();
        ActivityManager activityManager =
                (ActivityManager) getInstrumentation().getTargetContext().getSystemService(
                        Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> appProcesses =
                ShellIdentityUtils.invokeMethodWithShellPermissions(
                        activityManager, ActivityManager::getRunningAppProcesses,
                        android.Manifest.permission.REAL_GET_TASKS);
        int otherPid = -1;
        for (int i = 0; i < appProcesses.size(); i++) {
            if (appProcesses.get(i).pid != android.os.Process.myPid()) {
                otherPid = appProcesses.get(i).pid;
                break;
            }
        }
        assumeTrue("No other app processes running " + appProcesses.size(), otherPid != -1);
        Thread.sleep(mCpuHeadroomInterval);
        CpuHeadroomParams params = new CpuHeadroomParams.Builder().setTids(otherPid).build();
        assertThrows(SecurityException.class, () -> mManager.getCpuHeadroom(params));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CPU_GPU_HEADROOMS)
    public void testCpuHeadroomParams_Builder() throws Exception {
        checkCpuHeadroomSupport();
        final Pair<Integer, Integer> range = mManager.getGpuHeadroomCalculationWindowRange();
        CpuHeadroomParams params1 =
                new CpuHeadroomParams.Builder()
                        .setCalculationType(CpuHeadroomParams.CPU_HEADROOM_CALCULATION_TYPE_AVERAGE)
                        .setCalculationWindowMillis(range.first)
                        .setTids(new int[] {1, 2, 3})
                        .build();
        CpuHeadroomParams params2 = new CpuHeadroomParams.Builder(params1).build();
        assertEquals(params1, params2);
        assertEquals(params1.hashCode(), params2.hashCode());
        CpuHeadroomParams params3 =
                new CpuHeadroomParams.Builder(params1).setTids(new int[] {1, 2}).build();
        assertNotEquals(params1, params3);
        assertNotEquals(params1.hashCode(), params3.hashCode());
        params3 =
                new CpuHeadroomParams.Builder(params1)
                        .setCalculationType(CpuHeadroomParams.CPU_HEADROOM_CALCULATION_TYPE_MIN)
                        .build();
        assertNotEquals(params1, params3);
        assertNotEquals(params1.hashCode(), params3.hashCode());
        params3 =
                new CpuHeadroomParams.Builder(params1)
                        .setCalculationWindowMillis(range.second)
                        .build();
        assertNotEquals(params1, params3);
        assertNotEquals(params1.hashCode(), params3.hashCode());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CPU_GPU_HEADROOMS)
    public void testGetGpuHeadroom_default() throws Exception {
        checkGpuHeadroomSupport();
        GpuHeadroomParams params = new GpuHeadroomParams.Builder().build();
        assertEquals(GpuHeadroomParams.GPU_HEADROOM_CALCULATION_TYPE_MIN,
                params.getCalculationType());
        runCheckGpuHeadroom(params);
        runCheckGpuHeadroom(null);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CPU_GPU_HEADROOMS)
    public void testGetGpuHeadroom_average() throws Exception {
        checkGpuHeadroomSupport();
        GpuHeadroomParams params =
                new GpuHeadroomParams.Builder()
                        .setCalculationType(GpuHeadroomParams.GPU_HEADROOM_CALCULATION_TYPE_AVERAGE)
                        .build();
        assertEquals(GpuHeadroomParams.GPU_HEADROOM_CALCULATION_TYPE_AVERAGE,
                params.getCalculationType());
        runCheckGpuHeadroom(params);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CPU_GPU_HEADROOMS)
    public void testGetGpuHeadroom_customWindow() throws Exception {
        checkGpuHeadroomSupport();
        final Pair<Integer, Integer> range = mManager.getGpuHeadroomCalculationWindowRange();
        final int expectedMinMillis = 1000;
        assertTrue(
                "Min GPU headroom calculation window should be less or equal to "
                        + expectedMinMillis,
                range.first <= expectedMinMillis);
        final int expectedMaxMillis = 1000;
        assertTrue(
                "Max GPU headroom calculation window should be greater or equal to "
                        + expectedMaxMillis,
                range.second >= expectedMaxMillis);
        GpuHeadroomParams params;
        params = new GpuHeadroomParams.Builder().setCalculationWindowMillis(range.first).build();
        assertEquals((int) range.first, params.getCalculationWindowMillis());
        runCheckGpuHeadroom(params);
        params = new GpuHeadroomParams.Builder().setCalculationWindowMillis(range.second).build();
        assertEquals((int) range.second, params.getCalculationWindowMillis());
        runCheckGpuHeadroom(params);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CPU_GPU_HEADROOMS)
    public void testGpuHeadroomParams_Builder() throws Exception {
        checkGpuHeadroomSupport();
        final Pair<Integer, Integer> range = mManager.getGpuHeadroomCalculationWindowRange();
        GpuHeadroomParams params1 =
                new GpuHeadroomParams.Builder()
                        .setCalculationType(GpuHeadroomParams.GPU_HEADROOM_CALCULATION_TYPE_AVERAGE)
                        .setCalculationWindowMillis(range.first)
                        .build();
        GpuHeadroomParams params2 = new GpuHeadroomParams.Builder(params1).build();
        assertEquals(params1, params2);
        assertEquals(params1.hashCode(), params2.hashCode());
        GpuHeadroomParams params3 =
                new GpuHeadroomParams.Builder(params1)
                        .setCalculationType(GpuHeadroomParams.GPU_HEADROOM_CALCULATION_TYPE_MIN)
                        .build();
        assertNotEquals(params1, params3);
        assertNotEquals(params1.hashCode(), params3.hashCode());
        params3 =
                new GpuHeadroomParams.Builder(params1)
                        .setCalculationWindowMillis(range.second)
                        .build();
        assertNotEquals(params1, params3);
        assertNotEquals(params1.hashCode(), params3.hashCode());
    }
}
