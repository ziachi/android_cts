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

package android.adpf.atom.app2;

import static android.adpf.atom.common.ADPFAtomTestConstants.ACTION_CREATE_REGULAR_HINT_SESSIONS;
import static android.adpf.atom.common.ADPFAtomTestConstants.ACTION_CREATE_REGULAR_HINT_SESSIONS_MULTIPLE;
import static android.adpf.atom.common.ADPFAtomTestConstants.INTENT_ACTION_KEY;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeNotNull;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.PerformanceHintManager;
import android.util.Log;
import android.widget.RelativeLayout;

import com.android.compatibility.common.util.PropertyUtil;

/** An activity which performs ADPF actions. */
public class ADPFAtomTestActivity extends Activity {
    private static final String TAG = ADPFAtomTestActivity.class.getSimpleName();
    private static final int FIRST_API_LEVEL = PropertyUtil.getFirstApiLevel();

    private RelativeLayout mRelativeLayout;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        final Intent intent = this.getIntent();
        assertNotNull(intent);
        final String action = intent.getStringExtra(INTENT_ACTION_KEY);
        assertNotNull(action);
        switch (action) {
            case ACTION_CREATE_REGULAR_HINT_SESSIONS:
                PerformanceHintManager.Session session = createPerformanceHintSession();
                if (FIRST_API_LEVEL < Build.VERSION_CODES.S) {
                    assumeNotNull(session);
                } else {
                    assertNotNull(session);
                }
                drawText();
                Log.i(TAG, "Created hint session.");
                break;
            case ACTION_CREATE_REGULAR_HINT_SESSIONS_MULTIPLE:
                PerformanceHintManager.Session session1 = createPerformanceHintSession();
                PerformanceHintManager.Session session2 = createPerformanceHintSession();
                PerformanceHintManager.Session session3 = createPerformanceHintSession();
                if (FIRST_API_LEVEL < Build.VERSION_CODES.S) {
                    assumeNotNull(session1);
                    assumeNotNull(session2);
                    assumeNotNull(session3);
                } else {
                    assertNotNull(session1);
                    assertNotNull(session2);
                    assertNotNull(session3);
                }
                drawText();
                Log.i(TAG, "Created multiple hint sessions.");
                break;
        }
    }

    private void drawText() {
        setContentView(R.layout.activity_main);

        mRelativeLayout = findViewById(R.id.idRLView);

        ADPFAtomTestPaintView paintView = new ADPFAtomTestPaintView(this);
        mRelativeLayout.addView(paintView);
    }

    private PerformanceHintManager.Session createPerformanceHintSession() {
        final long testTargetDuration = 12345678L;
        PerformanceHintManager hintManager = getApplicationContext().getSystemService(
                PerformanceHintManager.class);
        assertNotNull(hintManager);
        return hintManager.createHintSession(
                new int[]{android.os.Process.myTid()}, testTargetDuration);
    }
}
