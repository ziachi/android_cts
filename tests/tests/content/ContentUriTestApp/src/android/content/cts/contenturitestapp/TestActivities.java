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

package android.content.cts.contenturitestapp;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.util.Log;

/**
 * Test Activities for testing the Activity Manifest attribute
 * {@link android.R.attr#requireContentUriPermissionFromCaller}.
 *
 * Once they're started, they send a broadcast to the original test indicating that they started.
 */
public class TestActivities {
    private static final String TEST_PACKAGE = "android.content.cts";
    private static final ComponentName TEST_RECEIVER = new ComponentName(TEST_PACKAGE,
            TEST_PACKAGE + ".ActivityRequireContentUriPermissionFromCallerTest$TestReceiver");
    private static final String TEST_RECEIVER_ACTION =
            "android.content.cts.REQUIRE_CONTENT_URI_TEST_RECEIVER_ACTION";
    private static final String EXTRA_IS_RESULT = "isResult";

    public static class NoneContentUriActivity extends BaseActivity {}

    public static class ReadContentUriActivity extends BaseActivity {}

    public static class WriteContentUriActivity extends BaseActivity {}

    public static class ReadOrWriteContentUriActivity extends BaseActivity {}

    public static class ReadAndWriteContentUriActivity extends BaseActivity {}

    private static class BaseActivity extends Activity {
        private static final String TAG = "BaseActivity";

        @Override
        public void onStart() {
            super.onStart();
            Intent intent = getIntent();
            Log.i(TAG, "intent: " + intent);
            String streamString = intent.getStringExtra(Intent.EXTRA_STREAM);
            String referrerName = intent.getStringExtra(Intent.EXTRA_REFERRER_NAME);
            boolean isResult = intent.getBooleanExtra(EXTRA_IS_RESULT, false);

            Intent resIntent = new Intent();
            resIntent.putExtra(Intent.EXTRA_STREAM, streamString);
            resIntent.putExtra(Intent.EXTRA_REFERRER_NAME, referrerName);

            if (isResult) {
                setResult(RESULT_OK, resIntent);
            } else {
                resIntent.setComponent(TEST_RECEIVER);
                resIntent.setAction(TEST_RECEIVER_ACTION);
                sendBroadcast(resIntent);
            }
            finish();
        }
    }
}
