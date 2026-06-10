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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertisingSet;
import android.bluetooth.le.AdvertisingSetCallback;
import android.bluetooth.le.AdvertisingSetParameters;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Run the BLE channel sounding accuracy test. */
public class ChannelSoundingTestRunner {
    private static final String TAG = ChannelSoundingTestRunner.class.getName();
    private static final int DEVICE_FINDING_TIMEOUT_MS = 300_000; // 5 minates
    private static final UUID CS_TEST_SERVICE_UUID =
            UUID.nameUUIDFromBytes("cs_accuracy_verifier".getBytes());

    private final Context mApplicationContext;
    private final CsTestRunnerCallback mCsTestRunnerCallback;
    private final BluetoothAdapter mBluetoothAdapter;
    private final BluetoothManager mBluetoothManager;
    private final Handler mMainHandler;
    @Nullable private BluetoothGatt mBluetoothGatt = null;
    private TestRunningState mTestRunningState = TestRunningState.STOPPED;
    @Nullable private CsRanger mCsRanger = null;

    public ChannelSoundingTestRunner(
            Context applicationdContext,
            CsTestRunnerCallback csTestRunnerCallback,
            Handler mainHandler) {
        this.mApplicationContext = applicationdContext;
        this.mCsTestRunnerCallback = csTestRunnerCallback;
        this.mMainHandler = mainHandler;
        mBluetoothManager = applicationdContext.getSystemService(BluetoothManager.class);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
    }

    private AdvertisingSetCallback mAdvertisingSetCallback =
            new AdvertisingSetCallback() {
                @Override
                public void onAdvertisingSetStarted(
                        AdvertisingSet advertisingSet, int txPower, int status) {
                    Log.d(
                            TAG,
                            "onAdvertisingSetStarted(): txPower:"
                                    + txPower
                                    + " , status: "
                                    + status);
                }

                @Override
                public void onAdvertisingDataSet(AdvertisingSet advertisingSet, int status) {
                    Log.d(TAG, "onAdvertisingDataSet() :status:" + status);
                }

                @Override
                public void onScanResponseDataSet(AdvertisingSet advertisingSet, int status) {
                    Log.d(TAG, "onScanResponseDataSet(): status:" + status);
                }

                @Override
                public void onAdvertisingSetStopped(AdvertisingSet advertisingSet) {
                    Log.d(TAG, "onAdvertisingSetStopped()");
                }
            };

    /** Start advertising as reference device */
    public void startAdvertising() {
        Log.d(TAG, "start ble advertising.");
        if (!isBluetoothEnabled()) {
            mCsTestRunnerCallback.onDeviceFindingError("Bluetooth is not enabled.");
            return;
        }
        BluetoothLeAdvertiser advertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
        AdvertisingSetParameters parameters =
                new AdvertisingSetParameters.Builder()
                        .setLegacyMode(false) // True by default, but set here as a reminder.
                        .setConnectable(true)
                        .setInterval(AdvertisingSetParameters.INTERVAL_LOW)
                        .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_MEDIUM)
                        .build();

        BluetoothGattServerCallback gattServerCallback =
                new BluetoothGattServerCallback() {
                    @Override
                    public void onConnectionStateChange(
                            BluetoothDevice device, int status, int newState) {
                        super.onConnectionStateChange(device, status, newState);
                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                            Log.d(TAG, "Device connected: " + device.getName());
                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            Log.d(TAG, "Device disconnected: " + device.getName());
                        }
                    }
                };

        BluetoothGattServer bluetoothGattServer =
                mBluetoothManager.openGattServer(mApplicationContext, gattServerCallback);
        if (bluetoothGattServer == null) {
            Log.d(TAG, "error on getting GattServer");
            mCsTestRunnerCallback.onDeviceFindingError(
                    "Bluetooth operation failed. try again before making sure bluetooth works.");
            return;
        }
        AdvertiseData advertiseData =
                new AdvertiseData.Builder()
                        .setIncludeDeviceName(true)
                        .addServiceUuid(new ParcelUuid(CS_TEST_SERVICE_UUID))
                        .build();

        Log.d(TAG, "Start connectable advertising");

        advertiser.startAdvertisingSet(
                parameters, advertiseData, null, null, null, 0, 0, mAdvertisingSetCallback);
    }

    /** Stop advertising as reference device */
    public void stopAdvertising() {
        if (!isBluetoothEnabled()) {
            Log.d(TAG, "do nothing as bluetooth is not enabled.");
            return;
        }
        BluetoothLeAdvertiser advertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
        advertiser.stopAdvertisingSet(mAdvertisingSetCallback);
        Log.d(TAG, "stop advertising");
    }

    /** Start the test */
    public void startTest() {
        if (!isBluetoothEnabled()) {
            mCsTestRunnerCallback.onDeviceFindingError("Bluetooth is not enabled.");
            return;
        }
        if (mTestRunningState != TestRunningState.STOPPED) {
            Log.d(TAG, "the test is ongoing, stop the test first.");
            return;
        }
        findDeviceAndConnect();
    }

    /** Stop the test */
    public void stopTest() {
        if (isBluetoothEnabled() && mTestRunningState == TestRunningState.SCANNING) {
            stopScanning();
        }
        if (mCsRanger == null) {
            Log.d(TAG, "The measurement hasn't started.");
            return;
        } else if (mTestRunningState == TestRunningState.MEASUREING) {
            mCsRanger.stopRanging();
        }
        mTestRunningState = TestRunningState.STOPPED;
        mCsRanger = null;
    }

    private BluetoothGattCallback mGattCallback =
            new BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                    Log.d(
                            TAG,
                            "onConnectionStateChange status:" + status + ", newState:" + newState);
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        Log.d(TAG, gatt.getDevice().getName() + " is connected");
                        mBluetoothGatt = gatt;
                        UUID deviceId = UUID.randomUUID();
                        mCsTestRunnerCallback.onDeviceFound(deviceId, gatt.getDevice().getName());
                        if (gatt.getDevice().getBondState() != BluetoothDevice.BOND_BONDED) {
                            mCsTestRunnerCallback.onDeviceFindingError(
                                    "Pair and bond the device from the bluetooth settings.");
                            mTestRunningState = TestRunningState.STOPPED;
                            return;
                        }
                        // start cs measurement
                        mCsRanger =
                                new CsRanger(
                                        mApplicationContext,
                                        gatt.getDevice(),
                                        deviceId,
                                        new CsRanger.CsDistanceMeasurementCallback() {
                                            public void onStopWithError(UUID deviceId) {
                                                mCsTestRunnerCallback.onMeasurementError(
                                                        deviceId,
                                                        "measurement error, check the log.");
                                                mTestRunningState = TestRunningState.STOPPED;
                                            }

                                            public void onDistanceResult(
                                                    UUID deviceId, double meters) {
                                                mCsTestRunnerCallback.onDistanceResult(
                                                        deviceId, meters);
                                            }
                                        },
                                        mMainHandler::post);
                        mCsRanger.startRanging();
                        mTestRunningState = TestRunningState.MEASUREING;
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        Log.d(TAG, "disconnected from " + gatt.getDevice().getName());
                        mBluetoothGatt.close();
                        mBluetoothGatt = null;
                        mTestRunningState = TestRunningState.STOPPED;
                    }
                }

                public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.d(TAG, "MTU changed to: " + mtu);
                    } else {
                        Log.d(TAG, "MTU change failed: " + status);
                    }
                }
            };

    private ScanCallback mScanCallback =
            new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    List<ParcelUuid> serviceUuids = result.getScanRecord().getServiceUuids();
                    if (serviceUuids != null) {
                        for (ParcelUuid parcelUuid : serviceUuids) {
                            BluetoothDevice btDevice = result.getDevice();
                            Log.d(TAG, "found device - " + btDevice.getName());
                            if (parcelUuid.getUuid().equals(CS_TEST_SERVICE_UUID)) {
                                stopScanning();
                                Log.d(TAG, "connect GATT to: " + btDevice.getName());
                                // Connect to the GATT server
                                mBluetoothGatt =
                                        btDevice.connectGatt(
                                                mApplicationContext,
                                                false,
                                                mGattCallback,
                                                BluetoothDevice.TRANSPORT_LE);
                                mTestRunningState = TestRunningState.CONNECTING;
                            }
                        }
                    }
                }
            };

    private void findDeviceAndConnect() {
        BluetoothLeScanner bluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();

        List<ScanFilter> filters = new ArrayList<>();
        ScanFilter filter =
                new ScanFilter.Builder()
                        .setServiceUuid(
                                new ParcelUuid(CS_TEST_SERVICE_UUID)) // Filter by service UUID
                        .build();
        filters.add(filter);

        ScanSettings settings =
                new ScanSettings.Builder()
                        .setLegacy(false)
                        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .setReportDelay(0)
                        .build();

        Log.d(TAG, "start scanning...");

        // Start scanning
        bluetoothLeScanner.startScan(filters, settings, mScanCallback);
        mTestRunningState = TestRunningState.SCANNING;
        mMainHandler.postDelayed(
                () -> {
                    if (mTestRunningState == TestRunningState.SCANNING) {
                        mCsTestRunnerCallback.onDeviceFindingError(
                                "scanning timeout, can't find the reference device.");
                        stopScanning();
                        mTestRunningState = TestRunningState.STOPPED;
                    }
                },
                DEVICE_FINDING_TIMEOUT_MS);
    }

    private boolean isBluetoothEnabled() {
        return mBluetoothAdapter != null && mBluetoothAdapter.isEnabled();
    }

    private void stopScanning() {
        BluetoothLeScanner bluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        if (bluetoothLeScanner != null) {
            bluetoothLeScanner.stopScan(mScanCallback);
        }
    }

    private enum TestRunningState {
        STOPPED,
        SCANNING,
        CONNECTING,
        MEASUREING,
    }

    /** Callbak from the TestRunner */
    public interface CsTestRunnerCallback {
        /** notify the reference device was found. */
        void onDeviceFound(UUID deviceId, String deviceName);

        /** notify error when it can not find the reference device. retry. */
        void onDeviceFindingError(String errorMessage);

        /** notify the measurement result */
        void onDistanceResult(UUID deviceId, double meters);

        /** notify the measurement error, test error */
        void onMeasurementError(UUID deviceId, String errorMessage);
    }
}
