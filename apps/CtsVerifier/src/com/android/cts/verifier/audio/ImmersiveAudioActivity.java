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

package com.android.cts.verifier.audio;

import static android.content.Context.RECEIVER_EXPORTED;

import static com.android.cts.verifier.TestListActivity.sCurrentDisplayMode;
import static com.android.cts.verifier.TestListAdapter.setTestNameSuffix;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.verifier.CtsVerifierReportLog;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.audio.audiolib.AudioSystemFlags;
import com.android.cts.verifier.libs.ui.HtmlFormatter;
import com.android.cts.verifier.libs.ui.PlainTextFormatter;
import com.android.cts.verifier.libs.ui.TextFormatter;

// Here is the current specification for the Intent from the Immersive Audio test harness
/*
adb shell am start -n com.android.cts.verifier/.audio.ImmersiveAudioActivity \
    --es test headtracking_latency \
    --es result "Pass" \
    --es err_code "n/a" \
    --ef latency 234.56 \
    --ef reliability 82.34 \
    --ei passing_threshold 300 \
    --ei passing_reliability 80 \
    --ei passing_range 40 \
    --es version_code "0.0.1"
 */

public class ImmersiveAudioActivity extends PassFailButtons.Activity {
    private static final String TAG = ImmersiveAudioActivity.class.getSimpleName();

    ImmersiveAudioActivity mTheIAActivity;

    // UI
    private boolean mSupportsHeadTracking;

    // Intent Handling
    Intent mIntent;
    IntentFilter mIntentFilter;

    // Intent Extras
    String mTestCode;
    String mResultString;
    String mErrorCode;
    float mLatency;
    float mReliability;
    int mPassThreshold;
    int mPassReliability;
    int mPassRange;
    String mVersionCode;

    public static final String IMMERSIVE_AUDIO_RESULTS =
            "com.android.cts.verifier.audio.IMMERSIVE_AUDIO_RESULTS";
    IABroadcastReceiver mBroadcastReceiver;

    public ImmersiveAudioActivity() {
        mTheIAActivity = this;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSupportsHeadTracking = AudioSystemFlags.claimsHeadTrackingLowLatency(this);

        mIntent = getIntent();

        setContentView(R.layout.immersive_audio_activity);
        setInfoResources(R.string.immersive_audio_test, R.string.immersive_audio_test_info, -1);

        setPassFailButtonClickListeners();

        mRequireReportLogToPass = true;

        mBroadcastReceiver = new IABroadcastReceiver();
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(IMMERSIVE_AUDIO_RESULTS);

        displayIntent(mIntent);

        getPassButton().setEnabled(calculatePass());
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(mBroadcastReceiver);
    }

    @Override
    public void onResume() {
        super.onResume();
        registerReceiver(mBroadcastReceiver, mIntentFilter, RECEIVER_EXPORTED);
    }

    private boolean calculatePass() {
        if (!mSupportsHeadTracking) {
            return true;
        }

        if (mIntent != null) {
            // this probably needs to be more complex
            if (mResultString == null) {
                return false;
            }

            if (convertResult(mResultString) <= 0) {
                return false;
            }

            return mLatency <= mPassThreshold && mReliability >= mPassReliability;
        } else {
            return false;
        }
    }

    private static final int RESULT_PASS = 1;
    private static final int RESULT_RETEST = 0;
    private static final int RESULT_UNDEFINED = -1;
    private static final int RESULT_FAIL = -2;

    private static final String RESULTSTR_PASS = "Pass";
    private static final String RESULTSTR_FAIL = "Fail";
    private static final String RESULTSTR_RETEST = "Retest";

    private int convertResult(String resultString) {
        if (resultString == null) {
            return RESULT_UNDEFINED;
        } else if (resultString.equals(RESULTSTR_PASS)) {
            return RESULT_PASS;
        } else if (resultString.equals(RESULTSTR_FAIL)) {
            return RESULT_FAIL;
        } else if (resultString.equals(RESULTSTR_RETEST)) {
            return RESULT_RETEST;
        } else {
            return RESULT_UNDEFINED;
        }
    }

    private int convertVersion(String versionString) {
        if (versionString == null) {
            return 0;
        } else {
            // sent in this format "0.0.1"
            // "12.34.56" -> (int32)123456
            // Decompose
            String[] parts = versionString.split("\\.");
            return Integer.parseInt(parts[0]) * 10000
                    + Integer.parseInt(parts[1]) * 100
                    + Integer.parseInt(parts[2]);
        }
    }

    // Intent Extra Keys
    private static final String INTENT_EXTRA_TESTCODE = "test";
    private static final String INTENT_EXTRA_RESULT = "result";
    private static final String INTENT_EXTRA_ERRORCODE = "err_code";
    private static final String INTENT_EXTRA_LATENCY = "latency";
    private static final String INTENT_EXTRA_RELIABILITY = "reliability";
    private static final String INTENT_EXTRA_PASSTHRESHOLD = "passing_threshold";
    private static final String INTENT_EXTRA_PASSRELIABILITY = "passing_reliability";
    private static final String INTENT_EXTRA_PASSRANGE = "passing_range";
    private static final String INTENT_EXTRA_VERSIONCODE = "version_code";

    // (different) JSON Keys
    // Corresponds to INTENT_EXTRA_PASSTHRESHOLD
    private static final String KEY_PASSINGLATENCY = "passing_latency";
    // Corresponds to INTENT_EXTRA_ERRORCODE
    private static final String KEY_ERRORCODE = "error_code";
    private static final String KEY_LOWLATENCY = "feature_low_latency";

    private void displayIntent(Intent intent) {
        LinearLayout resultsLayout = findViewById(R.id.immersive_test_result);
        resultsLayout.removeViews(0, resultsLayout.getChildCount());

        TextFormatter textFormatter;
        View resultsView;

        if (AudioSystemFlags.supportsWebView(this)) {
            textFormatter = new HtmlFormatter();
            resultsView = new WebView(this);
        } else {
            // No WebView
            textFormatter = new PlainTextFormatter();
            resultsView = new TextView(this);
        }

        resultsLayout.addView(resultsView,
                new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT));

        textFormatter.clear();
        textFormatter.openDocument();

        if (!mSupportsHeadTracking) {
            textFormatter.openBold()
                    .appendText(getString(R.string.immersive_audio_noheadtracking))
                    .closeBold()
                    .appendBreak().appendBreak();
        }
        if (intent != null) {
            mTestCode = intent.getStringExtra(INTENT_EXTRA_TESTCODE);
            mResultString = intent.getStringExtra(INTENT_EXTRA_RESULT);
            mErrorCode = intent.getStringExtra(INTENT_EXTRA_ERRORCODE);
            mLatency = intent.getFloatExtra(INTENT_EXTRA_LATENCY, -1.0f);
            mReliability = intent.getFloatExtra(INTENT_EXTRA_RELIABILITY, -1.0f);
            mPassThreshold = intent.getIntExtra(INTENT_EXTRA_PASSTHRESHOLD, -1);
            mPassReliability = intent.getIntExtra(INTENT_EXTRA_PASSRELIABILITY, -1);
            mPassRange = intent.getIntExtra(INTENT_EXTRA_PASSRANGE, -1);
            mVersionCode = intent.getStringExtra(INTENT_EXTRA_VERSIONCODE);

            textFormatter.appendText(INTENT_EXTRA_TESTCODE + ": " + mTestCode);
            textFormatter.appendBreak();
            textFormatter.appendText(INTENT_EXTRA_RESULT + ": " + mResultString);
            textFormatter.appendBreak();
            textFormatter.appendText(INTENT_EXTRA_ERRORCODE + ": " + mErrorCode);
            textFormatter.appendBreak();
            textFormatter.appendText(INTENT_EXTRA_LATENCY + ": " + mLatency);
            textFormatter.appendBreak();
            textFormatter.appendText(INTENT_EXTRA_RELIABILITY + ": " + mReliability);
            textFormatter.appendBreak();
            textFormatter.appendText(INTENT_EXTRA_PASSTHRESHOLD + ": " + mPassThreshold);
            textFormatter.appendBreak();
            textFormatter.appendText(INTENT_EXTRA_PASSRELIABILITY + ": " + mPassReliability);
            textFormatter.appendBreak();
            textFormatter.appendText(INTENT_EXTRA_PASSRANGE + ": " + mPassRange);
            textFormatter.appendBreak();
            textFormatter.appendText(INTENT_EXTRA_VERSIONCODE + ": " + mVersionCode);
            textFormatter.appendBreak();
        } else {
            textFormatter.openBold();
            textFormatter.appendText("No Intent Received!");
            textFormatter.closeBold();
        }
        textFormatter.closeDocument();
        textFormatter.put(resultsView);
    }

    //
    // CtsVerifierReportLog handling
    //
    @Override
    public boolean requiresReportLog() {
        return true;
    }

    @Override
    public String getReportFileName() {
        return PassFailButtons.AUDIO_TESTS_REPORT_LOG_NAME;
    }

    @Override
    public final String getReportSectionName() {
        return setTestNameSuffix(sCurrentDisplayMode, "immersive_audio_latency");
    }

    @Override
    public void recordTestResults() {
        CtsVerifierReportLog reportLog = getReportLog();

        reportLog.addValue(
                INTENT_EXTRA_RESULT,
                convertResult(mResultString),
                ResultType.NEUTRAL,
                ResultUnit.NONE);
        reportLog.addValue(
                INTENT_EXTRA_LATENCY,
                mLatency,
                ResultType.NEUTRAL,
                ResultUnit.NONE);
        reportLog.addValue(
                INTENT_EXTRA_RELIABILITY,
                mReliability,
                ResultType.NEUTRAL,
                ResultUnit.NONE);
        reportLog.addValue(
                KEY_PASSINGLATENCY,
                mPassThreshold,
                ResultType.NEUTRAL,
                ResultUnit.NONE);
        reportLog.addValue(
                INTENT_EXTRA_PASSRELIABILITY,
                mPassReliability,
                ResultType.NEUTRAL,
                ResultUnit.NONE);
        reportLog.addValue(
                INTENT_EXTRA_PASSRANGE,
                mPassRange,
                ResultType.NEUTRAL,
                ResultUnit.NONE);
        reportLog.addValue(
                INTENT_EXTRA_VERSIONCODE,
                convertVersion(mVersionCode),
                ResultType.NEUTRAL,
                ResultUnit.NONE);
        reportLog.addValue(
                KEY_LOWLATENCY,
                mSupportsHeadTracking,
                ResultType.NEUTRAL,
                ResultUnit.NONE);
        reportLog.submit();
    }

    private class IABroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            mIntent = intent;
            displayIntent(mIntent);

            Toast.makeText(mTheIAActivity, "onReceive()", Toast.LENGTH_LONG).show();
        }
    }
}
