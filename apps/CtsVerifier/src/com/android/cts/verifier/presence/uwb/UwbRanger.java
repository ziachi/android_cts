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

import static android.ranging.uwb.UwbComplexChannel.UWB_CHANNEL_9;
import static android.ranging.uwb.UwbComplexChannel.UWB_PREAMBLE_CODE_INDEX_11;

import android.content.Context;
import android.os.CancellationSignal;
import android.ranging.RangingData;
import android.ranging.RangingDevice;
import android.ranging.RangingManager;
import android.ranging.RangingPreference;
import android.ranging.RangingSession;
import android.ranging.SensorFusionParams;
import android.ranging.SessionConfig;
import android.ranging.raw.RawInitiatorRangingConfig;
import android.ranging.raw.RawRangingDevice;
import android.ranging.raw.RawResponderRangingConfig;
import android.ranging.uwb.UwbAddress;
import android.ranging.uwb.UwbComplexChannel;
import android.ranging.uwb.UwbRangingParams;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.UUID;
import java.util.concurrent.Executor;

/** Measure the distance between 2 devices using UWB. */
class UwbRanger {
    private static final String TAG = UwbRanger.class.getName();

    private static final int CHANNEL = UWB_CHANNEL_9;
    private static final int PREAMBLE = UWB_PREAMBLE_CODE_INDEX_11;
    private static final int CONFIG_ID = UwbRangingParams.CONFIG_PROVISIONED_MULTICAST_DS_TWR;
    private static final int SESSION_ID = 5;
    private static final UwbAddress UWB_ADDRESSES_INITIATOR =
            UwbAddress.fromBytes(new byte[] {0x5, 0x6});
    private static final UwbAddress UWB_ADDRESSES_RESPONDER =
            UwbAddress.fromBytes(new byte[] {0x6, 0x5});
    private static final byte[] SESSION_KEY =
            new byte[] {
                0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7, 0x8,
                0x8, 0x7, 0x6, 0x5, 0x4, 0x3, 0x2, 0x1
            };
    private static final UUID UUID_INITIATOR = UUID.nameUUIDFromBytes("initiator".getBytes());
    private static final UUID UUID_RESPONDER = UUID.nameUUIDFromBytes("responder".getBytes());

    private final Context mApplicationContext;
    private final boolean mIsInitiator;
    private final RangingManager mRangingManager;
    private final DistanceMeasurementCallback mDistanceMeasurementCallback;
    private final Executor mExecutor;
    private final UUID mTargetDeviceId;
    @Nullable private RangingSession mSession = null;
    @Nullable private CancellationSignal mCancellationSignal = null;

    UwbRanger(
            Context applicationContext,
            boolean isInitiator,
            DistanceMeasurementCallback distanceMeasurementCallback,
            Executor executor) {
        this.mApplicationContext = applicationContext;
        this.mIsInitiator = isInitiator;
        this.mDistanceMeasurementCallback = distanceMeasurementCallback;
        this.mExecutor = executor;
        mTargetDeviceId = isInitiator ? UUID_RESPONDER : UUID_INITIATOR;
        mRangingManager = mApplicationContext.getSystemService(RangingManager.class);
    }

    private RangingPreference createInitiatorPreference() {
        RawRangingDevice.Builder rawRangingDeviceBuilder =
                new RawRangingDevice.Builder()
                        .setRangingDevice(
                                new RangingDevice.Builder().setUuid(mTargetDeviceId).build());
        rawRangingDeviceBuilder.setUwbRangingParams(
                new UwbRangingParams.Builder(
                                SESSION_ID,
                                CONFIG_ID,
                                UWB_ADDRESSES_INITIATOR,
                                UWB_ADDRESSES_RESPONDER)
                        .setComplexChannel(
                                new UwbComplexChannel.Builder()
                                        .setChannel(CHANNEL)
                                        .setPreambleIndex(PREAMBLE)
                                        .build())
                        .setSessionKeyInfo(SESSION_KEY)
                        .build());
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
        return new RangingPreference.Builder(
                        RangingPreference.DEVICE_ROLE_INITIATOR, rawInitiatorRangingConfig)
                .setSessionConfig(sessionConfig)
                .build();
    }

    private RangingPreference createResponderPreference() {
        RawRangingDevice.Builder rawRangingDeviceBuilder =
                new RawRangingDevice.Builder()
                        .setRangingDevice(
                                new RangingDevice.Builder().setUuid(mTargetDeviceId).build());
        rawRangingDeviceBuilder.setUwbRangingParams(
                new UwbRangingParams.Builder(
                                SESSION_ID,
                                CONFIG_ID,
                                UWB_ADDRESSES_RESPONDER,
                                UWB_ADDRESSES_INITIATOR)
                        .setComplexChannel(
                                new UwbComplexChannel.Builder()
                                        .setChannel(CHANNEL)
                                        .setPreambleIndex(PREAMBLE)
                                        .build())
                        .setSessionKeyInfo(SESSION_KEY)
                        .build());
        RawResponderRangingConfig rawResponderRangingConfig =
                new RawResponderRangingConfig.Builder()
                        .setRawRangingDevice(rawRangingDeviceBuilder.build())
                        .build();
        // disable the filter, test the raw device performance.
        SessionConfig sessionConfig =
                new SessionConfig.Builder()
                        .setSensorFusionParams(
                                new SensorFusionParams.Builder()
                                        .setSensorFusionEnabled(false)
                                        .build())
                        .build();
        return new RangingPreference.Builder(
                        RangingPreference.DEVICE_ROLE_RESPONDER, rawResponderRangingConfig)
                .setSessionConfig(sessionConfig)
                .build();
    }

    public void startRanging() {
        RangingPreference rangingPreference = null;
        if (mIsInitiator) {
            rangingPreference = createInitiatorPreference();
        } else {
            rangingPreference = createResponderPreference();
        }
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

    interface DistanceMeasurementCallback {
        void onStopWithError(UUID deviceId);

        void onDistanceResult(UUID deviceId, double distanceMeters);
    }
}
