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

package com.android.cts.verifier.presence.cs;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.CancellationSignal;
import android.ranging.RangingData;
import android.ranging.RangingDevice;
import android.ranging.RangingManager;
import android.ranging.RangingPreference;
import android.ranging.RangingSession;
import android.ranging.SensorFusionParams;
import android.ranging.SessionConfig;
import android.ranging.ble.cs.BleCsRangingParams;
import android.ranging.raw.RawInitiatorRangingConfig;
import android.ranging.raw.RawRangingDevice;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.UUID;
import java.util.concurrent.Executor;

/** Measure the distance between 2 ble devices using channan sounding. */
class CsRanger {
    private static final String TAG = CsRanger.class.getName();
    private final Context mApplicationContext;
    private final BluetoothDevice mTargetDevice;
    private final UUID mTargetDeviceId;
    private final RangingManager mRangingManager;
    private final CsDistanceMeasurementCallback mDistanceMeasurementCallback;
    private final Executor mExecutor;
    @Nullable private RangingSession mSession = null;
    @Nullable private CancellationSignal mCancellationSignal = null;

    CsRanger(
            Context applicationContext,
            BluetoothDevice targetDevice,
            UUID targetDeviceId,
            CsDistanceMeasurementCallback distanceMeasurementCallback,
            Executor executor) {
        this.mApplicationContext = applicationContext;
        this.mTargetDevice = targetDevice;
        this.mTargetDeviceId = targetDeviceId;
        this.mDistanceMeasurementCallback = distanceMeasurementCallback;
        this.mExecutor = executor;
        mRangingManager = mApplicationContext.getSystemService(RangingManager.class);
    }

    public void startRanging() {
        RawRangingDevice.Builder rawRangingDeviceBuilder =
                new RawRangingDevice.Builder()
                        .setRangingDevice(
                                new RangingDevice.Builder().setUuid(mTargetDeviceId).build());
        // Set the BLE channel sounding paramsters
        rawRangingDeviceBuilder.setCsRangingParams(
                new BleCsRangingParams.Builder(mTargetDevice.getAddress()).build());
        RawInitiatorRangingConfig rawInitiatorRangingConfig =
                new RawInitiatorRangingConfig.Builder()
                        .addRawRangingDevice(rawRangingDeviceBuilder.build())
                        .build();
        // disable the filter, test the raw device performance.
        SessionConfig sessionConfig =
                new SessionConfig.Builder()
                        .setSensorFusionParams(
                                new SensorFusionParams.Builder()
                                        .setSensorFusionEnabled(false)
                                        .build())
                        .build();
        RangingPreference rangingPreference =
                new RangingPreference.Builder(
                                RangingPreference.DEVICE_ROLE_INITIATOR, rawInitiatorRangingConfig)
                        .setSessionConfig(sessionConfig)
                        .build();
        mSession = mRangingManager.createRangingSession(mExecutor, mRangingSessionCallback);
        mCancellationSignal = mSession.start(rangingPreference);
    }

    public void stopRanging() {
        if (mSession == null || mCancellationSignal == null) {
            return;
        }
        mCancellationSignal.cancel();
        mSession = null;
        mCancellationSignal = null;
    }

    private RangingSession.Callback mRangingSessionCallback =
            new RangingSession.Callback() {

                public void onOpened() {
                    Log.d(TAG, "DistanceMeasurement onOpened! ");
                }

                public void onOpenFailed(int reason) {
                    Log.d(TAG, "DistanceMeasurement onOpenFailed! " + reason);
                    mDistanceMeasurementCallback.onStopWithError(mTargetDeviceId);
                }

                public void onStarted(RangingDevice peer, int technology) {
                    Log.d(TAG, "DistanceMeasurement onStarted ! ");
                }

                public void onStopped(RangingDevice peer, int technology) {
                    Log.d(TAG, "DistanceMeasurement onStopped! " + technology);
                }

                public void onClosed(int reason) {
                    Log.d(TAG, "DistanceMeasurement onClosed! " + reason);
                    if (reason != RangingSession.Callback.REASON_LOCAL_REQUEST) {
                        mDistanceMeasurementCallback.onStopWithError(mTargetDeviceId);
                    }
                }

                public void onResults(RangingDevice peer, RangingData data) {
                    Log.d(TAG, "DistanceMeasurement onResults ! " + peer + ": " + data);
                    mDistanceMeasurementCallback.onDistanceResult(
                            peer.getUuid(), data.getDistance().getMeasurement());
                }
            };

    interface CsDistanceMeasurementCallback {
        void onStopWithError(UUID deviceId);

        void onDistanceResult(UUID deviceId, double distanceMeters);
    }
}
