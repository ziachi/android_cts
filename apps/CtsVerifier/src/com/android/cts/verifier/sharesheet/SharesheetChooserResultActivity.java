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

package com.android.cts.verifier.sharesheet;

import static android.service.chooser.ChooserResult.CHOOSER_RESULT_COPY;
import static android.service.chooser.ChooserResult.CHOOSER_RESULT_EDIT;
import static android.service.chooser.ChooserResult.CHOOSER_RESULT_SELECTED_COMPONENT;
import static android.service.chooser.ChooserResult.CHOOSER_RESULT_UNKNOWN;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.service.chooser.ChooserResult;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.StringRes;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.util.Objects;

abstract class SharesheetChooserResultActivity extends PassFailButtons.Activity {
    private static final String TAG = "ChooserResultTest";

    private static final String CHOOSER_RESULT =
            "com.android.cts.verifier.sharesheet.CHOOSER_RESULT";

    private ChooserResult mResultExpected;
    private ChooserResult mResultReceived;
    private TextView mInstructiontext;
    private Button mShareButton;

    private View mAfterShareSection;
    private Button mCouldNotLocate;
    private Button mActionPerformed;

    private Handler mHandler;
    private boolean mWaitingForResult;
    private boolean mResumed;
    private boolean mTestPassed;
    private boolean mTestComplete;

    private final Runnable NO_RESULT_RECEIVED = this::handleNoResultReceived;
    private final Runnable RELAUNCH_TEST = () -> startActivity(getTestActivityIntent());


    protected abstract Intent getTestActivityIntent();

    private final BroadcastReceiver mChooserCallbackReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            onChooserResultReceived(Objects.requireNonNull(intent.getParcelableExtra(
                    Intent.EXTRA_CHOOSER_RESULT,
                    ChooserResult.class)));
        }
    };

    protected final void setInstructions(@StringRes int instructions) {
        mInstructiontext.setText(instructions);
    }

    protected final void setAfterShareButtonLabels(@StringRes int actionTakenLabel,
            @StringRes int notFoundLabel) {
        mActionPerformed.setText(actionTakenLabel);
        mCouldNotLocate.setText(notFoundLabel);
    }

    protected final void setExpectedResult(
            int type, ComponentName selectedComponent, boolean isShortcut) {
        Parcel values = Parcel.obtain();
        try {
            values.writeInt(type);
            ComponentName.writeToParcel(selectedComponent, values);
            values.writeBoolean(isShortcut);
            values.setDataPosition(0);
            mResultExpected = ChooserResult.CREATOR.createFromParcel(values);
        } finally {
            values.recycle();
        }
    }

    protected abstract Intent createChooserIntent();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new Handler(Looper.getMainLooper());
        setContentView(R.layout.sharesheet_chooser_result_activity);
        setPassFailButtonClickListeners();

        mInstructiontext = requireViewById(R.id.instructions);
        mAfterShareSection = requireViewById(R.id.sharesheet_result_test_instructions_after_share);

        mCouldNotLocate = requireViewById(R.id.sharesheet_result_test_not_found);
        mActionPerformed = requireViewById(R.id.sharesheet_result_test_pressed);
        mAfterShareSection.setVisibility(View.GONE);

        mShareButton = requireViewById(R.id.sharesheet_share_button);
        mShareButton.setText(R.string.sharesheet_share_label);
        mShareButton.setOnClickListener(v -> {
            mWaitingForResult = true;
            startActivity(createChooserIntent());
        });

        // Can't pass until steps are completed.
        getPassButton().setVisibility(View.GONE);

    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(mChooserCallbackReceiver, new IntentFilter(CHOOSER_RESULT),
                RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(mChooserCallbackReceiver);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mResumed = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        mResumed = true;
        mHandler.removeCallbacks(RELAUNCH_TEST);

        if (mTestComplete) {
            finishTest();
            return;
        }

        if (mWaitingForResult && mResultReceived == null) {
            Log.d(TAG, "waiting for result (100ms)");
            mHandler.postDelayed(NO_RESULT_RECEIVED, 100);
        }
    }

    private void handleNoResultReceived() {
        Log.d(TAG, "Timed out while waiting for result (100ms)");
        mWaitingForResult = false;

        // No ChooserResult was received. Ask the user if they pressed the button (or retry)
        mInstructiontext.setText(R.string.sharesheet_result_test_instructions_after_share);
        mAfterShareSection.setVisibility(View.VISIBLE);
        mShareButton.setText(R.string.sharesheet_result_test_try_again);

        // If there's no action to take, then the test is passed.
        mCouldNotLocate.setOnClickListener(v -> {
            Toast.makeText(this, R.string.sharesheet_result_test_no_button,
                    Toast.LENGTH_SHORT).show();
            setTestResultAndFinish(true);

        });

        // Tester performed the requested action but no result received. FAIL.
        mActionPerformed.setOnClickListener(v -> {
            Toast.makeText(this, R.string.sharesheet_result_test_no_result_message,
                    Toast.LENGTH_SHORT).show();
            setTestResultAndFinish(false);
        });
    }

    private void onChooserResultReceived(ChooserResult result) {
        Log.d(TAG, "onChooserResultReceived: " + resultToString(result));
        mHandler.removeCallbacks(NO_RESULT_RECEIVED);
        mResultReceived = result;

        if (!mWaitingForResult) {
            return;
        }

        mTestPassed =  mResultExpected.equals(result);
        mTestComplete = true;

        if (mResumed) {
            finishTest();
        } else {
            mHandler.postDelayed(RELAUNCH_TEST, 100);
        }
    }

    private void finishTest() {
        if (!mTestPassed) {
            Log.d(TAG,
                    "ChooserResult incorrect!\n expected: " + resultToString(mResultExpected)
                            + "\nreceived: " + resultToString(mResultReceived));
            Toast.makeText(this, R.string.sharesheet_result_test_incorrect_result,
                    Toast.LENGTH_SHORT).show();
        }
        setTestResultAndFinish(mTestPassed);
    }

    protected Intent wrapWithChooserIntent(Intent shareIntent) {
        Intent resultIntent = new Intent(CHOOSER_RESULT).setPackage(getPackageName());
        PendingIntent shareResultIntent = PendingIntent.getBroadcast(
                /* context= */ this,
                /* flags= */ 0,
                /* intent= */ resultIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);

        Intent chooserIntent = Intent.createChooser(
                /* target= */ shareIntent,
                /* title= */ null,
                /* sender= */ shareResultIntent.getIntentSender()
        );
        chooserIntent.putExtra(Intent.EXTRA_AUTO_LAUNCH_SINGLE_CHOICE, false);
        chooserIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        chooserIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return chooserIntent;
    }

    private static String typeToString(int type) {
        switch (type) {
            case CHOOSER_RESULT_SELECTED_COMPONENT:
                return "SELECTED_COMPONENT";
            case CHOOSER_RESULT_COPY:
                return "COPY";
            case CHOOSER_RESULT_EDIT:
                return "EDIT";
            case CHOOSER_RESULT_UNKNOWN:
            default:
                return "UNKNOWN";
        }
    }

    private static String resultToString(ChooserResult result) {
        return "ChooserResult{"
                + "type=" + typeToString(result.getType())
                + " component=" + result.getSelectedComponent()
                + " isShortcut=" + result.isShortcut()
                + "}";
    }
}
