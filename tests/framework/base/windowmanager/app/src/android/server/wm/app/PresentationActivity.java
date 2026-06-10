/*
 * Copyright (C) 2020 The Android Open Source Project
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
 * limitations under the License
 */

package android.server.wm.app;

import android.app.Activity;
import android.app.Presentation;
import android.content.Intent;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;

public class PresentationActivity extends Activity {

    private static final String TAG = PresentationActivity.class.getSimpleName();
    private Presentation mPresentation;
    private Intent mLastIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mLastIntent = getIntent();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        mLastIntent = intent;
        if (mPresentation != null) {
            mPresentation.dismiss();
            mPresentation = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mPresentation == null) {
            createPresentationWindow();
        }
    }

    protected void onStop() {
        super.onStop();
        if (mPresentation != null) {
            mPresentation.dismiss();
            mPresentation = null;
        }
    }

    private void createPresentationWindow() {
        if (mLastIntent == null) return;

        final int displayId =
                mLastIntent.getExtras().getInt(Components.PresentationActivity.KEY_DISPLAY_ID);
        final Display presentationDisplay =
                getSystemService(DisplayManager.class).getDisplay(displayId);
        if (presentationDisplay == null) {
            Log.w(TAG, "Presentation display not found");
            return;
        }

        final TextView view = new TextView(this);
        view.setText("I'm a presentation");
        view.setGravity(Gravity.CENTER);
        view.setBackgroundColor(Color.RED);
        mPresentation = new Presentation(this, presentationDisplay);
        mPresentation.setContentView(view);
        mPresentation.setTitle(getPackageName());
        try {
            mPresentation.show();
        } catch (WindowManager.InvalidDisplayException e) {
            mPresentation = null;
            Log.w(TAG, "Presentation blocked", e);
        }
    }
}
