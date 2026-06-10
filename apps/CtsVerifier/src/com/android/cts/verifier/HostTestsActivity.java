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

package com.android.cts.verifier;

import static com.android.cts.verifier.PassFailButtons.showInfoDialog;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ListView;

import com.android.cts.verifier.TestListAdapter.TestListItem;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * General activity to support host-side tests in CtsVerifier.
 *
 * <ul>
 *   <li>Show a list of tests.
 *   <li>Register a BroadcastReceiver to record results of tests executed on the host.
 *   <li>Parse and update the results of the tests.
 * </ul>
 */
public class HostTestsActivity extends PassFailButtons.TestListActivity {

    private static final String TAG = "HostTestsActivity";

    // Add module categories
    private static final HostTestCategory[] HOST_TEST_CATEGORIES = {
        new HostTestCategory("CompanionDeviceManager Tests")
                .addTest(
                        "CtsCompanionDeviceManagerMultiDeviceTestCases",
                        "CtsCompanionDeviceManagerMultiDeviceTestCases"),
        new HostTestCategory("NFC Tests")
                .addTest("CtsNfcHceMultiDeviceTestCases", "CtsNfcHceMultiDeviceTestCases"),
        new HostTestCategory("UWB Tests")
                .addTest("CtsUwbMultiDeviceFiraRangingTests", "CtsUwbMultiDeviceFiraRangingTests")
                .addTest("CtsMultiDeviceGenericRangingTests", "CtsMultiDeviceGenericRangingTests")
                .addTest("CtsUwbMultiDeviceUwbManagerTests", "CtsUwbMultiDeviceUwbManagerTests"),
        new HostTestCategory("Wi-Fi Tests")
                .addTest("CtsWifiAwareTests", "CtsWifiAwareTests")
                .addTest("CtsWifiSoftApTestCases", "CtsWifiSoftApTestCases")
    };

    // The action to identify the broadcast Intent.
    private static final String ACTION_HOST_TEST_RESULT =
            "com.android.cts.verifier.ACTION_HOST_TEST_RESULT";
    // The key of the test results passed as the extra data of a broadcast Intent.
    private static final String EXTRA_HOST_TEST_RESULT =
            "com.android.cts.verifier.extra.HOST_TEST_RESULT";
    // The key for a test result in the JSON data.
    private static final String TEST_RESULT_KEY = "result";
    // Represents a pass test result.
    private static final String TEST_RESULT_PASS = "PASS";
    // Represents a fail test result.
    private static final String TEST_RESULT_FAIL = "FAIL";
    // The key for a test details string in the JSON data.
    private static final String TEST_DETAILS_KEY = "details";
    // The key for subtests in the JSON data.
    private static final String TEST_SUBTESTS_KEY = "subtests";
    // Separator between module, class and testcase of host-side tests
    static final String TEST_ID_SEPARATOR = "#";

    /** Represents a host-side test case in CtsVerifier. It's a test without an {@link Intent}. */
    public static final class HostTestListItem extends TestListItem {

        /**
         * Creates a test case shown in the UI with required test name and ID.
         *
         * @param testName name of the test shown in the UI
         * @param testId ID of the test to record its result in test report
         */
        public HostTestListItem(String testName, String testId) {
            super(
                    testName,
                    testId,
                    /* intent= */ null,
                    /* requiredFeatures= */ null,
                    /* excludedFeatures= */ null,
                    /* applicableFeatures= */ null);
        }

        @Override
        boolean isTest() {
            return true;
        }
    }

    /** Represents a category and its belonging tests. */
    public static final class HostTestCategory {
        // The title of the category.
        private final String mTitle;
        // Test name -> test ID mappings of all tests of this category.
        private final TreeMap<String, String> mTests;
        // IDs of all tests of this category.
        private final Set<String> mTestIds;

        public HostTestCategory(String title) {
            mTitle = title;
            mTests = new TreeMap<>();
            mTestIds = new HashSet<>();
        }

        /** Adds a test that belongs to this category. */
        public HostTestCategory addTest(String testName, String testId) {
            mTests.put(testName, testId);
            mTestIds.add(testId);
            return this;
        }

        /** Generates a list of {@link TestListItem}s to render this test category in the UI. */
        public List<TestListItem> generateTestListItems() {
            List<TestListItem> testListItems = new ArrayList<>();
            testListItems.add(TestListItem.newCategory(mTitle));
            for (Map.Entry<String, String> entry : mTests.entrySet()) {
                testListItems.add(new HostTestListItem(entry.getKey(), entry.getValue()));
            }
            return testListItems;
        }

        /** Gets the IDs of all tests that belong to this test category. */
        public Set<String> getTestIds() {
            return mTestIds;
        }
    }

    /**
     * The {@link BroadcastReceiver} to receive the broadcast {@link Intent} to update
     * status/results of tests.
     *
     * <p>The result data is in JSON format by default, a sample result data is:
     *
     * <pre>
     * {
     *   "test_id_1": {
     *     "result": "PASS"
     *   },
     *   "test_id_2": {
     *     "result": "FAIL",
     *     "details": "expected 'true' but was 'false'"
     *   }
     * }
     * </pre>
     */
    public final class ResultsReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_HOST_TEST_RESULT.equals(intent.getAction())) {
                Log.i(TAG, "Parsing test results...");
                String testResults = intent.getStringExtra(EXTRA_HOST_TEST_RESULT);
                if (testResults == null || testResults.isEmpty()) {
                    Log.i(TAG, EXTRA_HOST_TEST_RESULT + " is empty in the Intent.");
                    return;
                }
                JSONObject jsonResults;
                try {
                    jsonResults = new JSONObject(testResults);
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing json result string: " + testResults, e);
                    return;
                }
                Log.i(TAG, "Parsed test results: " + jsonResults);
                handleJsonResults(jsonResults, /* prefix= */ "");
            } else {
                Log.e(TAG, "Unknown Intent action " + intent.getAction());
            }
        }

        private void handleJsonResults(JSONObject jsonResults, String prefix) {
            Iterator<String> testIds = jsonResults.keys();
            while (testIds.hasNext()) {
                String testId = testIds.next();
                if (prefix.isEmpty() && !mAllTestIds.contains(testId)) {
                    Log.e(
                            TAG,
                            "Unknown test ID " + testId + " that doesn't belong to this activity.");
                    continue;
                }
                String fullTestId = prefix.isEmpty() ? testId : prefix + TEST_ID_SEPARATOR + testId;
                JSONObject testObject;
                String result;
                String testDetails = null;
                try {
                    testObject = jsonResults.getJSONObject(testId);
                    result = testObject.getString(TEST_RESULT_KEY);
                    if (testObject.has(TEST_DETAILS_KEY)) {
                        testDetails = testObject.getString(TEST_DETAILS_KEY);
                    }
                } catch (JSONException e) {
                    Log.e(TAG, "Error getting result of test " + fullTestId, e);
                    continue;
                }

                if (TEST_RESULT_PASS.equals(result)) {
                    updateTestResult(TestResult.TEST_RESULT_PASSED, fullTestId, testDetails);
                } else if (TEST_RESULT_FAIL.equals(result)) {
                    updateTestResult(TestResult.TEST_RESULT_FAILED, fullTestId, testDetails);
                } else {
                    Log.w(TAG, "Unrecognized result " + result + " for test " + fullTestId);
                }

                if (testObject.has(TEST_SUBTESTS_KEY)) {
                    try {
                        handleJsonResults(testObject.getJSONObject(TEST_SUBTESTS_KEY), fullTestId);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error getting subtest results of test " + fullTestId, e);
                    }
                }
            }
        }
    }

    // The resource ID of the title of the dialog to show when entering the activity first time.
    private final int mTitleId;
    // The resource ID of the message of the dialog to show when entering the activity first time.
    private final int mMessageId;
    // All test categories to render in this activity.
    private final List<HostTestCategory> mHostTestCategories;
    // IDs of all tests belong to this activity.
    private final Set<String> mAllTestIds;
    // The receiver to update test results via broadcast.
    private final ResultsReceiver mResultsReceiver = new ResultsReceiver();
    private boolean mReceiverRegistered = false;

    // The adapter to render all tests in a list.
    protected ArrayTestListAdapter mTestListAdapter;

    public HostTestsActivity() {
        this(
                R.string.host_tests_dialog_title,
                R.string.host_tests_dialog_content,
                HOST_TEST_CATEGORIES);
    }

    private HostTestsActivity(int titleId, int messageId, HostTestCategory... hostTestCategories) {
        mTitleId = titleId;
        mMessageId = messageId;
        mHostTestCategories = new ArrayList<>(Arrays.asList(hostTestCategories));
        mAllTestIds = new HashSet<>();
        for (HostTestCategory testCategory : hostTestCategories) {
            mAllTestIds.addAll(testCategory.getTestIds());
        }
    }

    @Override
    protected void handleItemClick(ListView l, View v, int position, long id) {
        TestListAdapter.TestListItem item = mTestListAdapter.getItem(position);
        if (mTestListAdapter.getTestResult(position) == TestResult.TEST_RESULT_NOT_EXECUTED) {
            showInfoDialog(
                    this,
                    R.string.host_tests_dialog_title,
                    R.string.host_tests_dialog_content,
                    R.layout.host_tests_dialog);
            return;
        }
        Intent intent = new Intent(this, HostTestListActivity.class);
        intent.putExtra(HostTestListActivity.MODULE_TITLE, item.title);
        intent.putExtra(HostTestListActivity.MODULE_NAME, item.testName);
        Log.i(TAG, "Launching activity with " + IntentDrivenTestActivity.toString(this, intent));
        startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.pass_fail_list);
        setInfoResources(mTitleId, mMessageId, R.layout.host_tests_dialog);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);

        mTestListAdapter = new ArrayTestListAdapter(this);
        for (HostTestCategory testCategory : mHostTestCategories) {
            mTestListAdapter.addAll(testCategory.generateTestListItems());
        }
        mTestListAdapter.registerDataSetObserver(
                new DataSetObserver() {

                    @Override
                    public void onChanged() {
                        updatePassButton();
                    }
                });
        setTestListAdapter(mTestListAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();

        Log.i(TAG, "Registering broadcast receivers...");
        IntentFilter filter = new IntentFilter(ACTION_HOST_TEST_RESULT);
        registerReceiver(mResultsReceiver, filter, Context.RECEIVER_EXPORTED);
        mReceiverRegistered = true;
        Log.i(TAG, "Registered broadcast receivers.");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Log.i(TAG, "Unregistering broadcast receivers...");
        if (mReceiverRegistered) {
            unregisterReceiver(mResultsReceiver);
            mReceiverRegistered = false;
        }
    }

    /** Updates a test with the given test result. */
    private void updateTestResult(int testResult, String testId, String testDetails) {
        Intent resultIntent = new Intent();
        TestResult.addResultData(
                resultIntent,
                testResult,
                testId,
                testDetails,
                /* reportLog= */ null,
                /* historyCollection= */ null);
        handleLaunchTestResult(RESULT_OK, resultIntent);
    }
}
