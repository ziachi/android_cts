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

import static android.service.chooser.ChooserResult.CHOOSER_RESULT_EDIT;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.android.cts.verifier.R;

public final class SharesheetChooserResultEditActivity extends SharesheetChooserResultActivity {

    @Override
    protected Intent getTestActivityIntent() {
        return new Intent(this, SharesheetChooserResultEditActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }

    @Override
    protected Intent createChooserIntent() {
        Uri mTestImageUri = TestContract.Uris.ImageBaseUri.buildUpon()
                .appendQueryParameter(TestContract.UriParams.Name,
                        getString(R.string.sharesheet_result_test_edit_instructions_short))
                .appendQueryParameter(TestContract.UriParams.Type, "image/jpg")
                .build();

        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.setDataAndType(mTestImageUri, "image/jpg");
        sendIntent.putExtra(Intent.EXTRA_STREAM, mTestImageUri);
        sendIntent.setClipData(
                new ClipData("", new String[]{"image/jpg"}, new ClipData.Item(mTestImageUri)));
        sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return wrapWithChooserIntent(sendIntent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setInfoResources(
                R.string.sharesheet_result_test_edit,
                R.string.sharesheet_result_test_edit_info,
                -1);

        setInstructions(R.string.sharesheet_result_test_edit_instructions);

        setAfterShareButtonLabels(
                R.string.sharesheet_result_test_edit_pressed,
                R.string.sharesheet_result_test_edit_not_found);

        setExpectedResult(CHOOSER_RESULT_EDIT, null, false);
    }
}
