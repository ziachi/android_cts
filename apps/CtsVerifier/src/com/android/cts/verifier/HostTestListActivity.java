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

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Activity to list testcases of host-side tests in CtsVerifier. */
public class HostTestListActivity extends PassFailButtons.TestListActivity {

    private static final String TAG = "InteractiveTestsListActivity";

    static final String MODULE_TITLE = "com.android.cts.verifier.host.module_title";
    static final String MODULE_NAME = "com.android.cts.verifier.host.module_name";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        setTitle(intent.getStringExtra(MODULE_TITLE));
        String moduleName = intent.getStringExtra(MODULE_NAME);

        // Load test list from TestResultsProvider
        ArrayTestListAdapter testListAdapter = new ArrayTestListAdapter(this);
        ContentResolver resolver = getContentResolver();
        Map<String, List<TestListAdapter.TestListItem>> testsByClass = new HashMap<>();
        try (Cursor cursor =
                resolver.query(
                        TestResultsProvider.getResultContentUri(this),
                        new String[] {
                            TestResultsProvider.COLUMN_TEST_NAME,
                            TestResultsProvider.COLUMN_TEST_DETAILS
                        },
                        TestResultsProvider.COLUMN_TEST_NAME + " LIKE ?",
                        new String[] {moduleName + "%"},
                        TestResultsProvider.COLUMN_TEST_NAME)) {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    String testId = cursor.getString(0);
                    if (testId.equals(moduleName)
                            && !cursor.isNull(1)
                            && !cursor.getString(1).isEmpty()) {
                        showAlertDialog(moduleName, cursor.getString(1));
                    }
                    // Format: Module#Class#Testcase
                    String[] parts = testId.split(HostTestsActivity.TEST_ID_SEPARATOR, 3);
                    if (parts.length < 3) {
                        Log.i(TAG, "Skip invalid host-side sub-test " + testId);
                        continue;
                    }
                    List<TestListAdapter.TestListItem> testcases =
                            testsByClass.computeIfAbsent(parts[1], k -> new ArrayList<>());
                    testcases.add(new HostTestsActivity.HostTestListItem(parts[2], testId));
                } while (cursor.moveToNext());
            }
        }
        for (String className : testsByClass.keySet()) {
            testListAdapter.add(TestListAdapter.TestListItem.newCategory(className));
            testListAdapter.addAll(testsByClass.get(className));
        }
        setTestListAdapter(testListAdapter);
    }

    @Override
    protected void handleItemClick(ListView l, View v, int position, long id) {
        // Does nothing.
    }

    private void showAlertDialog(String moduleName, String details) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(moduleName);
        builder.setMessage(
                String.format(
                        "%s For the detailed reason, check results and logs on the host.",
                        details));

        builder.setPositiveButton(
                "OK",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

        AlertDialog dialog = builder.create();
        dialog.show();
    }
}
