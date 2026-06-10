/*
 * Copyright (C) 2025 The Android Open Source Project
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

import android.media.AudioDeviceInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.android.cts.verifier.R;
import com.android.cts.verifier.audio.audiolib.AudioDeviceUtils;

import org.hyphonate.megaaudio.player.AudioSourceProvider;
import org.hyphonate.megaaudio.player.sources.SparseChannelAudioSourceProvider;
import org.hyphonate.megaaudio.recorder.AudioSinkProvider;
import org.hyphonate.megaaudio.recorder.sinks.AppCallbackAudioSinkProvider;

public class AudioDataPathsUSBInterfaceActivity extends AudioDataPathsBaseActivity {
    private static final String TAG = "AudioDataPathsUSBInterfaceActivity";

    private int mUsbInterfaceSupport;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.audio_datapaths_usb);

        mUsbInterfaceSupport = AudioDeviceUtils.supportsUsbAudioInterface(this);

        super.onCreate(savedInstanceState);
        setInfoResources(
                R.string.audio_datapaths_USB_interface_test,
                R.string.audio_datapaths_USB_interface_info, -1);

        enableTestButtons(mUsbInterfaceSupport != AudioDeviceUtils.SUPPORTSDEVICE_NO);

        ((TextView) findViewById(R.id.audio_datapaths_deviceprompt))
                .setText(getString(R.string.audio_datapaths_usb_interface_nodevices));
    }

    @Override
    protected String getTestCategory() {
        return getString(R.string.audio_datapaths_USB_interface_test);
    }

    @Override
    void gatherTestModules(TestManager testManager) {
        AudioSourceProvider leftSineSourceProvider = new SparseChannelAudioSourceProvider(
                SparseChannelAudioSourceProvider.CHANNELMASK_LEFT);
        AudioSourceProvider rightSineSourceProvider = new SparseChannelAudioSourceProvider(
                SparseChannelAudioSourceProvider.CHANNELMASK_RIGHT);

        AudioSinkProvider analysisSinkProvider =
                new AppCallbackAudioSinkProvider(mAnalysisCallbackHandler);

        TestModule testModule;

        // Signal Presence
        testModule = new TestModule(
                AudioDeviceInfo.TYPE_USB_DEVICE, 48000, 2,
                AudioDeviceInfo.TYPE_USB_DEVICE, 48000, 2);
        testModule.setSectionTitle("USB Device");
        testModule.setSources(leftSineSourceProvider, analysisSinkProvider);
        testModule.setDescription("Dev:2:L Dev:2");
        testModule.setAnalysisChannel(0);
        testManager.addTestModule(testModule);

        testModule = new TestModule(
                AudioDeviceInfo.TYPE_USB_DEVICE, 48000, 2,
                AudioDeviceInfo.TYPE_USB_DEVICE, 48000, 2);
        testModule.setSources(rightSineSourceProvider, analysisSinkProvider);
        testModule.setDescription("Dev:2:R Dev:2");
        testModule.setAnalysisChannel(1);
        testManager.addTestModule(testModule);

        // Signal Absence
        testModule = new TestModule(
                AudioDeviceInfo.TYPE_USB_DEVICE, 48000, 2,
                AudioDeviceInfo.TYPE_USB_DEVICE, 48000, 2);
        testModule.setSources(leftSineSourceProvider, analysisSinkProvider);
        testModule.setDescription("Dev:2:L Dev:2 [Cross Talk]");
        testModule.setAnalysisChannel(1);
        testModule.setAnalysisType(TestModule.TYPE_SIGNAL_ABSENCE);
        testManager.addTestModule(testModule);

        testModule = new TestModule(
                AudioDeviceInfo.TYPE_USB_DEVICE, 48000, 2,
                AudioDeviceInfo.TYPE_USB_DEVICE, 48000, 2);
        testModule.setSources(rightSineSourceProvider, analysisSinkProvider);
        testModule.setDescription("Dev:2:R Dev:2 [Cross Talk]");
        testModule.setAnalysisChannel(0);
        testModule.setAnalysisType(TestModule.TYPE_SIGNAL_ABSENCE);
        testManager.addTestModule(testModule);
    }

    @Override
    void postValidateTestDevices(int numValidTestModules) {
        TextView promptView = (TextView) findViewById(R.id.audio_datapaths_deviceprompt);
        if (mIsHandheld) {
            if (mUsbInterfaceSupport == AudioDeviceUtils.SUPPORTSDEVICE_YES) {
                if (mTestManager.calculatePass()) {
                    promptView.setVisibility(View.GONE);
                } else {
                    promptView.setVisibility(numValidTestModules == 0 ? View.VISIBLE : View.GONE);
                }
            } else if (mUsbInterfaceSupport == AudioDeviceUtils.SUPPORTSDEVICE_NO) {
                promptView.setText(
                        getResources().getString(R.string.audio_datapaths_usb_nosupport));
            } else {
                // AudioDeviceUtils.SUPPORTSDEVICE_UNDETERMINED
                promptView.setText(getResources().getString(
                        R.string.audio_datapaths_usb_undetermined));
            }
        } else {
            promptView.setText(getResources()
                    .getString(R.string.audio_datapaths_nonhandheld_autopass));
        }

        enableTestButtons(numValidTestModules != 0);
    }

    @Override
    protected boolean hasPeripheralSupport() {
        return mUsbInterfaceSupport != AudioDeviceUtils.SUPPORTSDEVICE_NO;
    }

    @Override
    String getRouteDescription() {
        return "usb_interface";
    }

    @Override
    protected boolean grantAutoPass() {
        return true;
    }
}
