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

import static android.adpf.atom.common.ADPFAtomTestConstants.ACTION_CREATE_DEAD_TIDS_THEN_GO_BACKGROUND;
import static android.adpf.atom.common.ADPFAtomTestConstants.ACTION_CREATE_REGULAR_HINT_SESSIONS;
import static android.adpf.atom.common.ADPFAtomTestConstants.ACTION_CREATE_REGULAR_HINT_SESSIONS_MULTIPLE;
import static android.adpf.atom.common.ADPFAtomTestConstants.CONTENT_COLUMN_KEY;
import static android.adpf.atom.common.ADPFAtomTestConstants.CONTENT_COLUMN_VALUE;
import static android.adpf.atom.common.ADPFAtomTestConstants.CONTENT_URI_STRING;
import static android.adpf.atom.common.ADPFAtomTestConstants.INTENT_ACTION_KEY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.device.collectors.util.SendToInstrumentation;
import android.net.Uri;
import android.os.Bundle;
import android.os.PerformanceHintManager;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class PerformanceHintManagerTests {
    private static final String TAG = PerformanceHintManagerTests.class.getSimpleName();
    private static final String ADPF_ATOM_TEST_GAME_PKG = "android.adpf.atom.app";
    private static final String ADPF_ATOM_TEST_APP_PKG = "android.adpf.atom.app2";

    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();

    @Test
    public void testAdpfTidCleanup() {
        final Context instContext = InstrumentationRegistry.getContext();
        PerformanceHintManager perfHintManager = instContext.getSystemService(
                PerformanceHintManager.class);
        assertNotNull(perfHintManager);
        assumeTrue("ADPF is not supported on this device",
                perfHintManager.getPreferredUpdateRateNanos() >= TimeUnit.MILLISECONDS.toNanos(1));

        // Initialize UiDevice instance
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        // Launch the app
        Context appContext = ApplicationProvider.getApplicationContext();
        final Intent intent = appContext.getPackageManager()
                .getLaunchIntentForPackage(ADPF_ATOM_TEST_GAME_PKG);
        intent.putExtra(INTENT_ACTION_KEY, ACTION_CREATE_DEAD_TIDS_THEN_GO_BACKGROUND);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        appContext.startActivity(intent);

        // Wait for the app to appear
        device.wait(Until.hasObject(By.pkg(ADPF_ATOM_TEST_GAME_PKG).depth(0)), 2000);
        device.waitForIdle();
        Log.d(TAG, "Wait for idle finished");

        Uri uri = Uri.parse(CONTENT_URI_STRING);
        Cursor cursor = appContext.getContentResolver().query(uri, null, null, null, null);
        assertTrue("Invalid cursor from querying content resolver",
                cursor != null && cursor.moveToFirst());
        Bundle bundle = new Bundle();
        do {
            int keyIndex = cursor.getColumnIndex(CONTENT_COLUMN_KEY);
            assertNotEquals("data key index is -1", keyIndex, -1);
            assertEquals("unexpected key type", Cursor.FIELD_TYPE_STRING,
                    cursor.getType(keyIndex));

            int valueIndex = cursor.getColumnIndex(CONTENT_COLUMN_VALUE);
            assertNotEquals("data value index is -1", valueIndex, -1);
            assertEquals("unexpected value type", Cursor.FIELD_TYPE_STRING,
                    cursor.getType(valueIndex));
            Log.d(TAG,
                    "Content parsed with key: " + cursor.getString(keyIndex) + ", value: "
                            + cursor.getString(valueIndex));
            bundle.putString(cursor.getString(keyIndex), cursor.getString(valueIndex));
        } while (cursor.moveToNext());
        cursor.close();

        SendToInstrumentation.sendBundle(mInstrumentation, bundle);
    }

    @Test
    public void testCreateHintSession() {
        Context context = InstrumentationRegistry.getContext();
        PerformanceHintManager phm = context.getSystemService(PerformanceHintManager.class);

        assertNotNull(phm);

        // If the device does not support ADPF hint session,
        // getPreferredUpdateRateNanos() returns -1.
        // We only test the devices supporting it and will check
        // if assumption fails in PerformanceHintManagerStatsTests#testCreateHintSessionStatsd
        assumeTrue(phm.getPreferredUpdateRateNanos() != -1);

        // Initialize UiDevice instance
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        // Launch the app
        launchApp(device, ADPF_ATOM_TEST_GAME_PKG, ACTION_CREATE_REGULAR_HINT_SESSIONS);
    }

    @Test
    public void testAdpfSessionSnapshotTwoAppsOn() {
        Context context = InstrumentationRegistry.getContext();
        PerformanceHintManager phm = context.getSystemService(PerformanceHintManager.class);

        assertNotNull(phm);
        assumeTrue(phm.getPreferredUpdateRateNanos() != -1);

        // Initialize UiDevice instance
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        // Launch the apps
        // first we test two apps creating APP/GAME sessions
        // Here the HWUI sessions should be created upon app activity starting
        launchApp(device, ADPF_ATOM_TEST_APP_PKG, ACTION_CREATE_REGULAR_HINT_SESSIONS_MULTIPLE);
        launchApp(device, ADPF_ATOM_TEST_GAME_PKG, ACTION_CREATE_REGULAR_HINT_SESSIONS);
    }

    @Test
    public void testAdpfSessionSnapshotTwoAppsOnKillOne() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        PerformanceHintManager phm = context.getSystemService(PerformanceHintManager.class);

        assertNotNull(phm);

        // If the device does not support ADPF hint session,
        // getPreferredUpdateRateNanos() returns -1.
        // We only test the devices supporting it and will check
        // if assumption fails in PerformanceHintManagerStatsTests#testCreateHintSessionStatsd
        assumeTrue(phm.getPreferredUpdateRateNanos() != -1);

        // Initialize UiDevice instance
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        // Launch the apps
        // first we test two apps creating APP/GAME sessions
        // Here the HWUI sessions should be created upon app activity starting
        launchApp(device, ADPF_ATOM_TEST_APP_PKG, ACTION_CREATE_REGULAR_HINT_SESSIONS_MULTIPLE);
        launchApp(device, ADPF_ATOM_TEST_GAME_PKG, ACTION_CREATE_REGULAR_HINT_SESSIONS);

        // Force stop one app to have HintManagerService to close the sessions associated to
        // the UID of the killed app.
        device.executeShellCommand("am force-stop " + ADPF_ATOM_TEST_APP_PKG);
        device.waitForIdle();
    }

    private void launchApp(UiDevice device, String pkgName, String intentActionValue) {
        Context appContext = ApplicationProvider.getApplicationContext();
        final Intent intent = appContext.getPackageManager()
                .getLaunchIntentForPackage(pkgName);
        intent.putExtra(INTENT_ACTION_KEY, intentActionValue);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        appContext.startActivity(intent);

        // Wait for the app to appear
        device.wait(Until.hasObject(By.pkg(pkgName).depth(0)), 2000);
        device.waitForIdle();
        Log.d(TAG, "Wait for " + pkgName + " idle finished");
    }
}
