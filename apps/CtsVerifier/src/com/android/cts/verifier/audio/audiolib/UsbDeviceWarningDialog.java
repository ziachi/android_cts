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

package com.android.cts.verifier.audio.audiolib;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.webkit.WebView;

import com.android.cts.verifier.R;
import com.android.cts.verifier.libs.ui.HtmlFormatter;

class UsbDeviceWarningDialog extends Dialog
        implements OnClickListener {
    private static final String TAG = "AudioLoopbackCalibrationDialog";

    private Context mContext;
    private WebView mMessageView;

    UsbDeviceWarningDialog(Context context) {
        super(context);

        mContext = context;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(mContext.getString(R.string.usbwarning_message_caption));

        setContentView(R.layout.usb_device_warning_dialog);

        mMessageView = (WebView) findViewById(R.id.usbdevicewarning_info);

        HtmlFormatter htmlFormatter = new HtmlFormatter();
        htmlFormatter.openDocument();

        htmlFormatter.openHeading(3)
                .appendText(mContext.getString(R.string.usbwarning_message_heading))
                .closeHeading(3);

        htmlFormatter.openParagraph()
                .appendText(mContext.getString(R.string.usbwarning_paragraph_0))
                .closeParagraph();

        htmlFormatter.openParagraph()
                .appendText(mContext.getString(R.string.usbwarning_paragraph_1));

        htmlFormatter.openParagraph()
                .appendText(mContext.getString(R.string.usbwarning_paragraph_2));

        htmlFormatter.closeDocument();
        htmlFormatter.put(mMessageView);

        getWindow().setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        findViewById(R.id.audio_usbwarning_done).setOnClickListener(this);
    }

    //
    // OnClickListener
    //
    public void onClick(View v) {
        if (v.getId() == R.id.audio_usbwarning_done) {
            dismiss();
        }
    }
}
