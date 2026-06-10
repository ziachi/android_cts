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
import com.android.cts.verifier.presence.uwb.UwbTestRunner;
import com.android.cts.verifier.presence.uwb.UwbTestRunner.UwbTestRunnerCallback;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * Tests UWB Presence calibration requirements.
 *
 * <p>Link to requirement documentation is at <a
 * href="https://source.android.com/docs/core/connect/presence-requirements#uwb-requirements">...
 * </a>.
 */
public class UwbAccuracyActivity extends PassFailButtons.Activity {
    private static final String TAG = UwbAccuracyActivity.class.getName();

    private static final double MAX_ACCEPTABLE_RANGE_METERS = 0.3;
    private static final double MAX_ACCEPTABLE_MEDIAN_METERS = 1.25;
    private static final double MIN_ACCEPTABLE_MEDIAN_METERS = 0.75;
    private static final int NUMBER_OF_TEST_SAMPLES = 1000;

    // Report log schema
    private static final String KEY_REFERENCE_DEVICE = "reference_device";

    private HashMap<UUID, ArrayList<Double>> mReceivedSamples;

    private TestResult mTestResult;
    private TestDistance mCurrentTestDistance = TestDistance.ONE_METER;
    private Button mStartTestButton;
    private Button mStopTestButton;
    private CheckBox mReferenceDeviceCheckbox;
    private CheckBox mIsManualPassCheckbox;
    private LinearLayout mDutModeLayout;
    private LinearLayout mRefModeLayout;
    private TextView mDeviceFoundTextView;
    private TextView mTestStatusTextView;
    private String mReferenceDeviceName = "";
    private boolean mIsManualPass;

    private UwbTestRunner mUwbTestRunner;
    private Handler mMainHandler;

    private UwbTestRunnerCallback mUwbTestRunnerCallback =
            new UwbTestRunnerCallback() {
                public void onDeviceFindingError(String errorMessage) {
                    mMainHandler.post(
                            () -> {
                                mDeviceFoundTextView.setText(errorMessage);
                                mDeviceFoundTextView.setVisibility(View.VISIBLE);
                                makeToast(errorMessage);
                                mStartTestButton.setEnabled(true);
                            });
                }

                public void onMeasurementError(UUID deviceId, String errorMessage) {
                    Log.d(TAG, "the test with " + deviceId + " was stopped, " + errorMessage);
                    mMainHandler.post(() -> updateTestStatus(TestStatus.FAILED));
                }

                public void onDistanceResult(UUID deviceId, double meters) {
                    Log.d(TAG, "measurement result for " + deviceId + " is " + meters);
                    if (!mReceivedSamples.containsKey(deviceId)) {
                        mMainHandler.post(
                                () -> {
                                    mReceivedSamples.put(deviceId, new ArrayList<>());
                                    Log.d(TAG, "device found: id - " + deviceId);
                                    mDeviceFoundTextView.setText(
                                            getString(
                                                    R.string.device_found_presence,
                                                    mReceivedSamples.get(deviceId).size(),
                                                    NUMBER_OF_TEST_SAMPLES));
                                    mDeviceFoundTextView.setVisibility(View.VISIBLE);
                                    makeToast("Reference device id received: " + deviceId);
                                    updateTestStatus(TestStatus.IN_PROGRESS);
                                });
                    }
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
        setContentView(R.layout.uwb_accuracy);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);
        mReferenceDeviceCheckbox = findViewById(R.id.is_uwb_reference_device);
        mIsManualPassCheckbox = findViewById(R.id.is_uwb_manual_pass);
        mDutModeLayout = findViewById(R.id.uwb_dut_mode_layout);
        mRefModeLayout = findViewById(R.id.uwb_ref_mode_layout);
        mDeviceFoundTextView = findViewById(R.id.uwb_device_found_info);
        mTestStatusTextView = findViewById(R.id.uwb_test_status_info);
        mTestResult = new TestResult();
        DeviceFeatureChecker.checkFeatureSupported(
                this, getPassButton(), PackageManager.FEATURE_UWB);
        mReceivedSamples = new HashMap<>();
        setUpActivity();
        mIsManualPass = mIsManualPassCheckbox.isChecked();
        mReferenceDeviceCheckbox.setOnCheckedChangeListener(
                (buttonView, isChecked) -> setUpActivity());
        mIsManualPassCheckbox.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {
                    mIsManualPass = isChecked;
                });
        mDeviceFoundTextView.setVisibility(View.GONE);
        mTestStatusTextView.setText(mTestResult.getTestStatus());
    }

    @Override
    public boolean requiresReportLog() {
        return true;
    }

    private void setUpActivity() {
        if (mReferenceDeviceCheckbox.isChecked()) {
            mDutModeLayout.setVisibility(View.GONE);
            mRefModeLayout.setVisibility(View.VISIBLE);
            mStartTestButton = findViewById(R.id.uwb_ref_start_test);
            mStopTestButton = findViewById(R.id.uwb_ref_stop_test);
        } else {
            mDutModeLayout.setVisibility(View.VISIBLE);
            mRefModeLayout.setVisibility(View.GONE);
            mStartTestButton = findViewById(R.id.uwb_dut_start_test);
            mStopTestButton = findViewById(R.id.uwb_dut_stop_test);
        }
        mStartTestButton.setOnClickListener((view) -> startTest());
        mStopTestButton.setOnClickListener((view) -> stopTest());
    }

    private void startTest() {
        Log.d(TAG, "Start test on " + (mReferenceDeviceCheckbox.isChecked() ? "ref" : "dut"));
        mReceivedSamples.clear();
        mStartTestButton.setEnabled(false);
        mUwbTestRunner =
                new UwbTestRunner(
                        getApplicationContext(),
                        !mReferenceDeviceCheckbox.isChecked(),
                        mUwbTestRunnerCallback,
                        mMainHandler);
        mUwbTestRunner.startTest();
    }

    private void stopTest() {
        Log.d(TAG, "Stop test on " + (mReferenceDeviceCheckbox.isChecked() ? "ref" : "dut"));
        mStartTestButton.setEnabled(true);
        if (mUwbTestRunner != null) mUwbTestRunner.stopTest();
    }

    private void checkDataCollectionStatus(UUID deviceId) {
        if (mReceivedSamples.get(deviceId).size() >= NUMBER_OF_TEST_SAMPLES) {
            stopTest();
            computeTestResults(mReceivedSamples.get(deviceId));
        }
    }

    private void computeTestResults(List<Double> data) {
        // Sort the data in ascending order
        Collections.sort(data);
        double range = data.get(975) - data.get(25);
        double median = data.get(500);
        // Calculate range at 90th percentile
        if (range <= MAX_ACCEPTABLE_RANGE_METERS
                && (median <= MAX_ACCEPTABLE_MEDIAN_METERS
                        && median >= MIN_ACCEPTABLE_MEDIAN_METERS)) {
            updateTestStatus(TestStatus.PASSED);
            Log.d(
                    TAG,
                    "Test passed: "
                            + "\r\n Range of measurements: "
                            + new DecimalFormat("#.##").format(range)
                            + "\r\n Median measurement: "
                            + new DecimalFormat("#.##").format(median));
        } else {
            updateTestStatus(TestStatus.FAILED);
            Log.d(
                    TAG,
                    "Test failed: "
                            + "\r\n Range of measurements: "
                            + new DecimalFormat("#.##").format(range)
                            + "\r\n Median measurement: "
                            + new DecimalFormat("#.##").format(median));
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
