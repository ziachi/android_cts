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

import static android.content.Intent.EXTRA_INITIAL_INTENTS;
import static android.service.chooser.ChooserResult.CHOOSER_RESULT_SELECTED_COMPONENT;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;

import com.android.cts.verifier.R;

public final class SharesheetChooserResultComponentActivity extends SharesheetChooserResultActivity {
    @Override
    protected Intent getTestActivityIntent() {
        return new Intent(this, SharesheetChooserResultComponentActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }

    @Override
    protected Intent createChooserIntent() {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT,
                getString(R.string.sharesheet_result_test_component_instructions_short));
        Intent chooserIntent = wrapWithChooserIntent(sendIntent);
        chooserIntent.putExtra(EXTRA_INITIAL_INTENTS, new Intent[]{
                new Intent(this, SharesheetChooserResultComponentActivity.class)
        });
        return chooserIntent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setInfoResources(
                R.string.sharesheet_result_test_component,
                R.string.sharesheet_result_test_component_info,
                -1);

        setInstructions(R.string.sharesheet_result_test_component_instructions);

        setAfterShareButtonLabels(
                R.string.sharesheet_result_test_component_selected,
                R.string.sharesheet_result_test_component_not_found);

        setExpectedResult(
                CHOOSER_RESULT_SELECTED_COMPONENT,
                new ComponentName(this, SharesheetChooserResultComponentActivity.class),
                false);
    }
}
