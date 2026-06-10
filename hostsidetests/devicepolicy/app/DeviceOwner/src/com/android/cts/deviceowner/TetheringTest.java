/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.cts.deviceowner;

import static android.net.TetheringManager.TETHERING_WIFI;
import static android.net.TetheringManager.TETHER_ERROR_NO_CHANGE_TETHERING_PERMISSION;
import static android.net.TetheringManager.TETHER_ERROR_UNKNOWN_REQUEST;

import static org.junit.Assume.assumeTrue;

import android.net.TetheringManager.TetheringRequest;
import android.net.cts.util.CtsTetheringUtils;
import android.net.cts.util.CtsTetheringUtils.StartTetheringCallback;
import android.net.cts.util.CtsTetheringUtils.StopTetheringCallback;
import android.net.cts.util.CtsTetheringUtils.TestTetheringEventCallback;
import android.net.wifi.SoftApConfiguration;

import com.android.modules.utils.build.SdkLevel;

public class TetheringTest extends BaseDeviceOwnerTest {
    private static final String TAG = "TetheringTest";

    private static final SoftApConfiguration TEST_SOFT_AP_CONFIG =
            new SoftApConfiguration.Builder()
                    .setSsid("This AP was started by a Device Owner app.")
                    .build();
    private CtsTetheringUtils mCtsTetheringUtils;
    private TestTetheringEventCallback mEventCallback;

    @SuppressWarnings("JUnit4ClassUsedInJUnit3")
    @Override
    public void setUp() throws Exception {
        super.setUp();

        // Skip if tethering for DO app isn't enabled.
        assumeTrue(SdkLevel.isAtLeastB());

        mCtsTetheringUtils = new CtsTetheringUtils(mContext);
        mEventCallback = mCtsTetheringUtils.registerTetheringEventCallback();
        mEventCallback.assumeWifiTetheringSupported(mContext);
    }

    @SuppressWarnings("JUnit4ClassUsedInJUnit3")
    @Override
    public void tearDown() {
        mCtsTetheringUtils.stopAllTethering();
        mCtsTetheringUtils.unregisterTetheringEventCallback(mEventCallback);
    }

    @SuppressWarnings("JUnit4ClassUsedInJUnit3")
    public void testStartTetheringNonWifiFails() {
        StartTetheringCallback callback = new StartTetheringCallback();
        TetheringRequest request = new TetheringRequest.Builder(TETHERING_WIFI + 1).build();

        mTetheringManager.startTethering(request, Runnable::run, callback);

        callback.expectTetheringFailed(TETHER_ERROR_NO_CHANGE_TETHERING_PERMISSION);
    }

    @SuppressWarnings("JUnit4ClassUsedInJUnit3")
    public void testStartTetheringWifiWithoutConfigFails() {
        StartTetheringCallback callback = new StartTetheringCallback();
        TetheringRequest request = new TetheringRequest.Builder(TETHERING_WIFI).build();

        mTetheringManager.startTethering(request, Runnable::run, callback);

        callback.expectTetheringFailed(TETHER_ERROR_NO_CHANGE_TETHERING_PERMISSION);
    }

    @SuppressWarnings("JUnit4ClassUsedInJUnit3")
    public void testStartTetheringWifiWithConfigSucceeds() {
        mCtsTetheringUtils.startWifiTethering(mEventCallback, TEST_SOFT_AP_CONFIG);
    }

    @SuppressWarnings("JUnit4ClassUsedInJUnit3")
    public void testStopTetheringNonWifiFails() {
        TetheringRequest request = new TetheringRequest.Builder(TETHERING_WIFI + 1).build();
        StopTetheringCallback callback = new StopTetheringCallback();

        mTetheringManager.stopTethering(request, Runnable::run, callback);

        callback.expectStopTetheringFailed(TETHER_ERROR_NO_CHANGE_TETHERING_PERMISSION);
    }

    @SuppressWarnings("JUnit4ClassUsedInJUnit3")
    public void testStopTetheringWifiWithoutConfigFails() {
        TetheringRequest request = new TetheringRequest.Builder(TETHERING_WIFI).build();
        StopTetheringCallback callback = new StopTetheringCallback();

        mTetheringManager.stopTethering(request, Runnable::run, callback);

        callback.expectStopTetheringFailed(TETHER_ERROR_NO_CHANGE_TETHERING_PERMISSION);
    }

    @SuppressWarnings("JUnit4ClassUsedInJUnit3")
    public void testStopTetheringWifiWithConfigButNoActiveRequestFails() {
        TetheringRequest request =
                new TetheringRequest.Builder(TETHERING_WIFI)
                        .setSoftApConfiguration(TEST_SOFT_AP_CONFIG)
                        .build();
        StopTetheringCallback callback = new StopTetheringCallback();

        mTetheringManager.stopTethering(request, Runnable::run, callback);

        callback.expectStopTetheringFailed(TETHER_ERROR_UNKNOWN_REQUEST);
    }

    @SuppressWarnings("JUnit4ClassUsedInJUnit3")
    public void testStopTetheringWifiWithConfigSucceeds() {
        mCtsTetheringUtils.startWifiTethering(mEventCallback, TEST_SOFT_AP_CONFIG);
        TetheringRequest request =
                new TetheringRequest.Builder(TETHERING_WIFI)
                        .setSoftApConfiguration(TEST_SOFT_AP_CONFIG)
                        .build();
        StopTetheringCallback callback = new StopTetheringCallback();

        mTetheringManager.stopTethering(request, Runnable::run, callback);

        callback.verifyStopTetheringSucceeded();
    }
}
