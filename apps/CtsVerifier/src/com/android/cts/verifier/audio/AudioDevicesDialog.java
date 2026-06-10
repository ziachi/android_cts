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

import android.app.Dialog;
import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.audio.Flags;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.cts.verifier.R;
import com.android.cts.verifier.audio.audiolib.AudioDeviceUtils;
import com.android.cts.verifier.audio.audiolib.AudioSystemFlags;
import com.android.cts.verifier.libs.ui.HtmlFormatter;
import com.android.cts.verifier.libs.ui.PlainTextFormatter;
import com.android.cts.verifier.libs.ui.TextFormatter;

import java.util.Set;

public class AudioDevicesDialog extends Dialog implements OnClickListener {
    private Context mContext;
    private AudioManager mAudioManager;
    private ConnectionListener mConnectListener;

    private View mInfoPanel;
    private TextFormatter mTextFormatter;

    AudioDevicesDialog(Context context) {
        super(context);

        mContext = context;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAudioManager = mContext.getSystemService(AudioManager.class);

        mConnectListener = new ConnectionListener();

        setTitle(mContext.getString(R.string.audio_devicesupport_title));

        setContentView(R.layout.audio_devices_dialog);
        getWindow().setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

        findViewById(R.id.audio_devices_done).setOnClickListener(this);

        LinearLayout infoPanelLayout = findViewById(R.id.audio_devices_info);
        if (AudioSystemFlags.supportsWebView(mContext)) {
            mTextFormatter = new HtmlFormatter();
            mInfoPanel = new WebView(mContext);
        } else {
            // No WebView
            mTextFormatter = new PlainTextFormatter();
            mInfoPanel = new TextView(mContext);
        }
        infoPanelLayout.addView(mInfoPanel,
                new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT));

        displayDeviceSupport();
    }

    @Override
    public void onStart() {
        super.onStart();
        mAudioManager.registerAudioDeviceCallback(mConnectListener, null);
    }

    @Override
    public void onStop() {
        mAudioManager.unregisterAudioDeviceCallback(mConnectListener);
        super.onStop();
    }

    /**
     * Build and show an HTML page with the device info.
     */
    void displayDeviceSupport() {
        final int headerLevel = 2;

        mTextFormatter.clear();
        mTextFormatter.openDocument();

        // Supported Devices
        mTextFormatter.openHeading(headerLevel)
                .appendText(mContext.getString(R.string.audio_general_supporteddevices))
                .closeHeading(headerLevel);
        mTextFormatter.appendText(mContext.getString(R.string.audio_supported_devices_explain))
                .appendBreak()
                .appendBreak();

        if (!Flags.supportedDeviceTypesApi()) {
            // Can't determine support (i.e. the AudioManager.getSupporteDeviceTypes() not
            // unflagged
            mTextFormatter.openItalic()
                    .appendText(mContext.getString(R.string.audio_devicesupport_cantdetermine))
                    .closeItalic()
                    .appendBreak();
        } else {
            // Inputs
            mTextFormatter.openBold()
                    .appendText(mContext.getString(R.string.audio_general_inputscolon))
                    .closeBold()
                    .appendBreak();

            Set<Integer> inputDevices =
                    mAudioManager.getSupportedDeviceTypes(AudioManager.GET_DEVICES_INPUTS);
            mTextFormatter.openBulletList();
            for (Integer deviceType : inputDevices) {
                mTextFormatter.openListItem()
                        .appendText(AudioDeviceUtils.getShortDeviceTypeName(deviceType))
                        .closeListItem();
            }
            mTextFormatter.closeBulletList();

            // Outputs
            mTextFormatter.openBold()
                    .appendText(mContext.getString(R.string.audio_general_outputscolon))
                    .closeBold()
                    .appendBreak();

            Set<Integer> outputDevices =
                    mAudioManager.getSupportedDeviceTypes(AudioManager.GET_DEVICES_OUTPUTS);
            mTextFormatter.openBulletList();
            for (Integer deviceType : outputDevices) {
                mTextFormatter.openListItem()
                        .appendText(AudioDeviceUtils.getShortDeviceTypeName(deviceType))
                        .closeListItem();
            }
            mTextFormatter.closeBulletList();
        }

        // Available Devices
        mTextFormatter.openHeading(headerLevel)
                .appendText(mContext.getString(R.string.audio_general_availabledevices))
                .closeHeading(headerLevel);
        mTextFormatter.appendText(mContext.getString(R.string.audio_available_devices_explain))
                .appendBreak()
                .appendBreak();

        // Inputs
        mTextFormatter.openBold()
                .appendText(mContext.getString(R.string.audio_general_inputscolon))
                .closeBold()
                .appendBreak();

        mTextFormatter.openBulletList();
        AudioDeviceInfo[] inputDevices =
                mAudioManager.getDevices(AudioManager.GET_DEVICES_INPUTS);
        for (AudioDeviceInfo deviceInfo : inputDevices) {
            mTextFormatter.openListItem()
                    .appendText(AudioDeviceUtils.getShortDeviceTypeName(deviceInfo.getType()))
                    .closeListItem();
        }
        mTextFormatter.closeBulletList();

        // Outputs
        mTextFormatter.openBold()
                .appendText(mContext.getString(R.string.audio_general_outputscolon))
                .closeBold()
                .appendBreak();

        AudioDeviceInfo[] outputDevices =
                mAudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        mTextFormatter.openBulletList();
        for (AudioDeviceInfo deviceInfo : outputDevices) {
            mTextFormatter.openListItem()
                    .appendText(AudioDeviceUtils.getShortDeviceTypeName(deviceInfo.getType()))
                    .closeListItem();
        }
        mTextFormatter.closeBulletList();

        mTextFormatter.closeDocument();
        mTextFormatter.put(mInfoPanel);
    }

    //
    // OnClickListener
    //
    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.audio_devices_done) {
            dismiss();
        }
    }

    class ConnectionListener extends AudioDeviceCallback {
        ConnectionListener() {}

        //
        // AudioDevicesManager.OnDeviceConnectionListener
        //
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            displayDeviceSupport();
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            displayDeviceSupport();
        }
    }
}
