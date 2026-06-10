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

package android.app.jank.cts;

import android.app.Activity;
import android.app.jank.AppJankStats;
import android.os.Bundle;
import android.widget.TextView;

/**
 * This activity loads the scrollview_layout and provides a method for test cases to simulate
 * AppJankStats reporting. The layout's textview will display a success message if the stats are
 * reported without errors, which can then be verified by the test.
 */
public class BasicActivity extends Activity {

    private TextView mTextView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.scrollview_layout);
        mTextView = findViewById(R.id.text_view);
    }

    /** Simulate reporting AppJankStats. */
    public void reportAppJankStats(AppJankStats jankStats, String successMessage) {
        if (mTextView == null) return;
        mTextView.reportAppJankStats(jankStats);
        mTextView.setText(successMessage);
    }
}
