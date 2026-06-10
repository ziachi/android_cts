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

package com.android.cts.verifier.presence.uwb;

import android.content.Context;
import android.os.Handler;
import android.ranging.RangingCapabilities;
import android.ranging.RangingManager;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Run the UWB accuracy test. */
public class UwbTestRunner {
    private static final String TAG = UwbTestRunner.class.getName();
    private static final int CAPABILITIES_TIMEOUT_MS = 2_000;
    private final Context mApplicationContext;
    private final RangingManager mRangingManager;
    private final Handler mHandler;
    private final UwbTestRunnerCallback mUwbTestRunnerCallback;
    private final boolean mIsInitiator;
    private TestRunningState mTestRunningState = TestRunningState.STOPPED;
    @Nullable private UwbRanger mUwbRanger = null;
    private final AtomicReference<RangingCapabilities> mCapabilities = new AtomicReference<>();
    private final CountDownLatch mCountDownLatch = new CountDownLatch(1);

    public UwbTestRunner(
            Context applicationdContext,
            boolean isInitiator,
            UwbTestRunnerCallback uwbTestRunnerCallback,
            Handler mainHandler) {
        mApplicationContext = applicationdContext;
        mUwbTestRunnerCallback = uwbTestRunnerCallback;
        mHandler = mainHandler;
        mIsInitiator = isInitiator;
        mRangingManager = applicationdContext.getSystemService(RangingManager.class);
        mRangingManager.registerCapabilitiesCallback(
                Executors.newSingleThreadExecutor(),
                (capabilities) -> {
                    mCapabilities.set(capabilities);
                    mCountDownLatch.countDown();
                });
    }

    /** Start the test */
    public void startTest() {
        if (!isUwbAvailable()) {
            mUwbTestRunnerCallback.onDeviceFindingError("UWB is not enabled.");
            return;
        }
        if (mTestRunningState != TestRunningState.STOPPED) {
            Log.d(TAG, "the test is ongoing, stop the test first.");
            return;
        }
        mUwbRanger =
                new UwbRanger(
                        mApplicationContext,
                        mIsInitiator,
                        new UwbRanger.DistanceMeasurementCallback() {
                            public void onStopWithError(UUID deviceId) {
                                mUwbTestRunnerCallback.onMeasurementError(
                                        deviceId, "measurement error, check the log.");
                                mTestRunningState = TestRunningState.STOPPED;
                                mUwbRanger = null;
                            }

                            public void onDistanceResult(UUID deviceId, double meters) {
                                mUwbTestRunnerCallback.onDistanceResult(deviceId, meters);
                            }
                        },
                        mHandler::post);
        mUwbRanger.startRanging();
        mTestRunningState = TestRunningState.MEASURING;
    }

    /** Stop the test */
    public void stopTest() {
        if (mUwbRanger == null) {
            Log.d(TAG, "The measurement hasn't started.");
            return;
        } else if (mTestRunningState == TestRunningState.MEASURING) {
            mUwbRanger.stopRanging();
        }
        mTestRunningState = TestRunningState.STOPPED;
        mUwbRanger = null;
    }

    private boolean isUwbAvailable() {
        try {
            mCountDownLatch.await(CAPABILITIES_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
        }
        RangingCapabilities capabilities = mCapabilities.get();
        if (capabilities == null) return false;
        return capabilities.getTechnologyAvailability().get(RangingManager.UWB)
                == RangingCapabilities.ENABLED;
    }

    private enum TestRunningState {
        STOPPED,
        MEASURING,
    }

    /** Callbak from the TestRunner */
    public interface UwbTestRunnerCallback {
        /** notify error when it can not find the reference device. retry. */
        void onDeviceFindingError(String errorMessage);

        /** notify the measurement result */
        void onDistanceResult(UUID deviceId, double meters);

        /** notify the measurement error, test error */
        void onMeasurementError(UUID deviceId, String errorMessage);
    }
}
