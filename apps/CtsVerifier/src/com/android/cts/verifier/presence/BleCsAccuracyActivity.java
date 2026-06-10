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

package com.android.cts.verifier.presence;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.presence.cs.ChannelSoundingTestRunner;
import com.android.cts.verifier.presence.cs.ChannelSoundingTestRunner.CsTestRunnerCallback;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * Tests BLE Channel Sounding Presence calibration requirements.
 *
 * <p>Link to requirement documentation is at <a
 * href="https://source.android.com/docs/core/connect/presence-requirements#requirement_743c-11-2
 * ">...</a>.
 */
public class BleCsAccuracyActivity extends PassFailButtons.Activity {
    private static final String TAG = BleCsAccuracyActivity.class.getName();

    private static final double MAX_ACCEPTABLE_RANGE_METERS = 0.5;
    private static final int NUMBER_OF_TEST_SAMPLES = 100;

    // Report log schema
    private static final String KEY_REFERENCE_DEVICE = "reference_device";

    private HashMap<UUID, ArrayList<Double>> mReceivedSamples;

    private TestResult mTestResult;
    private TestDistance mCurrentTestDistance = TestDistance.ONE_METER;
    private Button mStartTestButton;
    private Button mStartAdvertisingButton;
    private CheckBox mReferenceDeviceCheckbox;
    private CheckBox mIsManualPassCheckbox;
    private LinearLayout mDutModeLayout;
    private LinearLayout mRefModeLayout;
    private TextView mDeviceFoundTextView;
    private TextView mTestStatusTextView;
    private String mReferenceDeviceName = "";
    private boolean mIsManualPass;

    private ChannelSoundingTestRunner mChannelSoundingTestRunner;
    private Handler mMainHandler;

    private CsTestRunnerCallback mCsTestRunnerCallback =
            new CsTestRunnerCallback() {
                public void onDeviceFound(UUID deviceId, String deviceName) {
                    mMainHandler.post(
                            () -> {
                                mReceivedSamples.put(deviceId, new ArrayList<>());
                                Log.d(
                                        TAG,
                                        "device found: id - " + deviceId + ", name -" + deviceName);
                                mDeviceFoundTextView.setText(
                                        getString(
                                                R.string.device_found_presence,
                                                mReceivedSamples.get(deviceId).size(),
                                                NUMBER_OF_TEST_SAMPLES));
                                mDeviceFoundTextView.setVisibility(View.VISIBLE);
                                makeToast("Reference device name received: " + deviceName);
                                updateTestStatus(TestStatus.IN_PROGRESS);
                            });
                }

                public void onDeviceFindingError(String errorMessage) {
                    mMainHandler.post(
                            () -> {
                                mDeviceFoundTextView.setText(errorMessage);
                                mDeviceFoundTextView.setVisibility(View.VISIBLE);
                                makeToast(errorMessage);
                                mStartAdvertisingButton.setEnabled(true);
                                mStartTestButton.setEnabled(true);
                            });
                }

                public void onMeasurementError(UUID deviceId, String errorMessage) {
                    Log.d(TAG, "the test with " + deviceId + " was stopped, " + errorMessage);
                    mMainHandler.post(() -> updateTestStatus(TestStatus.FAILED));
                }

                public void onDistanceResult(UUID deviceId, double meters) {
                    Log.d(TAG, "measurement result for " + deviceId + " is " + meters);
                    mMainHandler.post(
                            () -> {
                                if (mReceivedSamples.containsKey(deviceId)) {
                                    mReceivedSamples.get(deviceId).add(meters);
                                    mDeviceFoundTextView.setText(
                                            getString(
                                                    R.string.device_found_presence,
                                                    mReceivedSamples.get(deviceId).size(),
                                                    NUMBER_OF_TEST_SAMPLES));
                                    checkDataCollectionStatus(deviceId);
                                }
                            });
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mMainHandler = new Handler(Looper.getMainLooper());
        setContentView(R.layout.ble_cs_accuracy);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);
        mReferenceDeviceCheckbox = findViewById(R.id.is_cs_reference_device);
        mIsManualPassCheckbox = findViewById(R.id.is_cs_manual_pass);
        mStartTestButton = findViewById(R.id.cs_start_test);
        Button stopTestButton = findViewById(R.id.cs_stop_test);
        mStartAdvertisingButton = findViewById(R.id.cs_start_advertising);
        Button stopAdvertisingButton = findViewById(R.id.cs_stop_advertising);
        mDutModeLayout = findViewById(R.id.cs_dut_mode_layout);
        mRefModeLayout = findViewById(R.id.cs_ref_mode_layout);
        mDeviceFoundTextView = findViewById(R.id.cs_device_found_info);
        mTestStatusTextView = findViewById(R.id.cs_test_status_info);
        mTestResult = new TestResult();
        DeviceFeatureChecker.checkFeatureSupported(
                this, getPassButton(), PackageManager.FEATURE_BLUETOOTH_LE_CHANNEL_SOUNDING);

        mReceivedSamples = new HashMap<>();
        mChannelSoundingTestRunner =
                new ChannelSoundingTestRunner(
                        getApplicationContext(), mCsTestRunnerCallback, mMainHandler);
        setUpActivity();
        mIsManualPass = mIsManualPassCheckbox.isChecked();
        mReferenceDeviceCheckbox.setOnCheckedChangeListener(
                (buttonView, isChecked) -> setUpActivity());
        mIsManualPassCheckbox.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {
                    mIsManualPass = isChecked;
                });
        mStartTestButton.setOnClickListener((view) -> startTest());
        stopTestButton.setOnClickListener((view) -> stopTest());
        mDeviceFoundTextView.setVisibility(View.GONE);
        mTestStatusTextView.setText(mTestResult.getTestStatus());
        mStartAdvertisingButton.setOnClickListener((view) -> startAdvertsing());
        stopAdvertisingButton.setOnClickListener((view) -> stopAdvertising());
    }

    @Override
    public boolean requiresReportLog() {
        return true;
    }

    private void setUpActivity() {
        if (mReferenceDeviceCheckbox.isChecked()) {
            mDutModeLayout.setVisibility(View.GONE);
            mRefModeLayout.setVisibility(View.VISIBLE);
        } else {
            mDutModeLayout.setVisibility(View.VISIBLE);
            mRefModeLayout.setVisibility(View.GONE);
        }
    }

    private void startTest() {
        mReceivedSamples.clear();
        mStartTestButton.setEnabled(false);
        mChannelSoundingTestRunner.startTest();
    }

    private void stopTest() {
        mStartTestButton.setEnabled(true);
        mChannelSoundingTestRunner.stopTest();
    }

    private void startAdvertsing() {
        mChannelSoundingTestRunner.startAdvertising();
        mStartAdvertisingButton.setEnabled(false);
    }

    private void stopAdvertising() {
        mChannelSoundingTestRunner.stopAdvertising();
        mStartAdvertisingButton.setEnabled(true);
    }

    private void checkDataCollectionStatus(UUID deviceId) {
        if (mReceivedSamples.get(deviceId).size() >= NUMBER_OF_TEST_SAMPLES) {
            stopTest();
            computeTestResults(mReceivedSamples.get(deviceId));
        }
    }

    private void computeTestResults(List<Double> data) {
        data.removeIf(
                measurementValue ->
                        Math.abs(measurementValue - mCurrentTestDistance.getValue())
                                > MAX_ACCEPTABLE_RANGE_METERS);

        // Calculate range at 90th percentile
        if (data.size() >= (0.9 * NUMBER_OF_TEST_SAMPLES)) {
            updateTestStatus(TestStatus.PASSED);
            Log.d(
                    TAG,
                    "Test passed: "
                            + "\r\nPercentage of results in range: "
                            + new DecimalFormat("#.##").format((data.size() / (double) 100) * 100)
                            + "%");
        } else {
            updateTestStatus(TestStatus.FAILED);
            Log.d(
                    TAG,
                    "Test failed: "
                            + "\r\nPercentage of results in range: "
                            + new DecimalFormat("#.##").format((data.size() / (double) 100) * 100)
                            + "%");
        }
        if (mTestResult.isAllPassed()) {
            if (mIsManualPass) {
                getPassButton().setEnabled(true);
            } else {
                getPassButton().performClick();
            }
        }
        mReceivedSamples.clear();
    }

    private void updateTestStatus(TestStatus status) {
        mTestResult.setTestResult(mCurrentTestDistance, status);
        mTestStatusTextView.setText(mTestResult.getTestStatus());
        if (status == TestStatus.PASSED || status == TestStatus.FAILED) {
            mStartTestButton.setEnabled(true);
        }
    }

    private void makeToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void recordTestResults() {
        if (mTestResult.isAllPassed()) {
            getReportLog()
                    .addValue(
                            KEY_REFERENCE_DEVICE,
                            mReferenceDeviceName,
                            ResultType.NEUTRAL,
                            ResultUnit.NONE);
            getReportLog().submit();
        }
    }

    enum TestDistance {
        ONE_METER(1.0);

        private final double mValue;

        TestDistance(double newValue) {
            mValue = newValue;
        }

        public double getValue() {
            return mValue;
        }
    }

    enum TestStatus {
        NOT_YET_RUN,
        IN_PROGRESS,
        FAILED,
        PASSED
    }

    private static class TestResult {
        private final HashMap<TestDistance, TestStatus> mTestResults;

        TestResult() {
            mTestResults = new HashMap<>();
            for (TestDistance distance : TestDistance.values()) {
                mTestResults.put(distance, TestStatus.NOT_YET_RUN);
            }
        }

        void setTestResult(TestDistance distance, TestStatus status) {
            mTestResults.put(distance, status);
        }

        String getTestStatus() {
            return "Test at 1m: " + mTestResults.get(TestDistance.ONE_METER);
        }

        boolean isAllPassed() {
            for (TestDistance distance : mTestResults.keySet()) {
                if (mTestResults.get(distance) != TestStatus.PASSED) {
                    return false;
                }
            }
            return true;
        }
    }
}
