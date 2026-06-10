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

package com.android.server.cts.device.statsdatom;

import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.os.PowerManager;
import android.os.WorkSource;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.Test;

public class PowerManagerTests {
    public static String TAG = "PowerManagerTests";
    public static String WAKELOCK_TAG = "TestWakelockForCts";

    @Test
    public void testAcquireModifyAndReleasedWakelock() {
        Context context = InstrumentationRegistry.getContext();
        PowerManager powerManager = context.getSystemService(PowerManager.class);
        assertNotNull(powerManager);
        PowerManager.WakeLock wakelock = powerManager
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG);

        Log.i(TAG, "Acquiring the wakelock");
        wakelock.acquire(10000);

        WorkSource workSource = new WorkSource();
        workSource.add(1010);
        workSource.add(2010);
        Log.i(TAG, "Changing the worksource of the acquired wakelock");
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> wakelock.setWorkSource(workSource));

        WorkSource newWorksource = new WorkSource();
        newWorksource.add(3010);

        Log.i(TAG, "Changing the worksource again of the acquired wakelock");
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> wakelock.setWorkSource(newWorksource));

        try {
            Log.i(TAG, "Releasing the wakelock");
            wakelock.release();
        } catch (RuntimeException ex) {
            Log.e(TAG, "Failed to release wakelock", ex);
            throw ex;
        }
    }

    @Test
    public void testUpdateWakelockUids() {
        Context context = InstrumentationRegistry.getContext();
        PowerManager powerManager = context.getSystemService(PowerManager.class);

        assertNotNull(powerManager);

        String tag = "cts:TEST_UPDATE_WAKELOCK_UIDS";
        PowerManager.WakeLock wakelock =
                powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag);

        Log.i(TAG, "Acquiring the wakelock");
        wakelock.acquire(10000);

        Log.i(TAG, "Updating the wakelock uids");
        ShellIdentityUtils.invokeWithShellPermissions(() -> wakelock.updateUids(new int[] {1010}));

        Log.i(TAG, "Updating the wakelock uids");
        ShellIdentityUtils.invokeWithShellPermissions(
                () -> wakelock.updateUids(new int[] {1020, 1030}));

        try {
            Log.i(TAG, "Releasing the wakelock");
            wakelock.release();
        } catch (RuntimeException ex) {
            Log.e(TAG, "Failed to release wakelock", ex);
            throw ex;
        }
    }
}
