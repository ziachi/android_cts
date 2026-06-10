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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Bundle;
import android.widget.TextView;

import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.verifier.CtsVerifierReportLog;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.audio.audiolib.AudioDeviceUtils;
import com.android.cts.verifier.audio.audiolib.DisplayUtils;

import org.hyphonate.megaaudio.common.StreamBase;

public abstract class AudioNotificationsBaseActivity extends PassFailButtons.Activity {
    Context mContext;

    AudioManager mAudioManager;

    // AudioManager.GET_DEVICES_OUTPUTS or AudioManager.GET_DEVICES_INPUTS
    int mRole;

    // Supported Devices State
    int mAnalogHeadsetSupport;
    int mUsbHeadsetSupport;
    int mUsbInterfaceSupport;

    protected String mConnectedPeripheralName;
    protected boolean mSupportsWiredPeripheral;

    // Connected Devices State
    AudioDeviceInfo mAnalogHeadsetDevice;
    AudioDeviceInfo mUsbHeadsetDevice;
    AudioDeviceInfo mUsbInterfaceDevice;

    private BroadcastReceiver mPluginReceiver = new PluginBroadcastReceiver();

    boolean mRoutingNotificationReceived;

    // Widgets
    TextView mDevSupportStateText;
    TextView mResultsText;

    // ReportLog schema
    private static final String KEY_WIRED_PORT_SUPPORTED = "wired_port_supported";
    protected static final String KEY_ROUTING_RECEIVED = "routing_received";
    protected static final String KEY_CONNECTED_PERIPHERAL = "routing_connected_peripheral";

    protected AudioNotificationsBaseActivity(int role) {
        mRole = role;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mContext = this;
        mAudioManager = mContext.getSystemService(AudioManager.class);

        mAnalogHeadsetSupport = AudioDeviceUtils.supportsAnalogHeadset(mContext);
        mUsbHeadsetSupport = AudioDeviceUtils.supportsUsbHeadset(mContext);
        mUsbInterfaceSupport = AudioDeviceUtils.supportsUsbAudioInterface(mContext);

        scanConnectedDevices();

        mDevSupportStateText =
                ((TextView) findViewById(R.id.audio_routingnotification_devsupport));
        mDevSupportStateText.setText(buildDeviceSupportString());

        mResultsText = ((TextView) findViewById(R.id.audio_routingnotification_testresult));

        // "Honor System" buttons
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);

        // handle the (weird) case of a device with NO wired peripheral support
        if (mAnalogHeadsetSupport != AudioDeviceUtils.SUPPORTSDEVICE_YES
                && mUsbHeadsetSupport != AudioDeviceUtils.SUPPORTSDEVICE_YES
                && mUsbInterfaceSupport != AudioDeviceUtils.SUPPORTSDEVICE_YES) {
            mRoutingNotificationReceived = true;
            calculatePass();
        }

        DisplayUtils.setKeepScreenOn(this, true);
    }

    @Override
    public void onResume() {
        super.onResume();
        startAudio();
        registerReceiver(mPluginReceiver, new IntentFilter(Intent.ACTION_HEADSET_PLUG));
    }

    @Override
    public void onPause() {
        unregisterReceiver(mPluginReceiver);
        stopAudio();
        super.onPause();
    }

    //
    // UI Helpers
    //
    String getSupportString(int devSupport) {
        switch (devSupport) {
            case AudioDeviceUtils.SUPPORTSDEVICE_NO:
                return getString(R.string.audio_routingnotification_nosupport);

            case AudioDeviceUtils.SUPPORTSDEVICE_YES:
                return getString(R.string.audio_routingnotification_supported);

            case AudioDeviceUtils.SUPPORTSDEVICE_UNDETERMINED:
                return getString(R.string.audio_routingnotification_supportundetermined);
        }
        return "";
    }

    String buildDeviceSupportString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.audio_routingnotification_devicesupportcr));

        // Analog Headset
        sb.append(getString(R.string.audio_routingnotification_analogheadsetcolon));
        if (mAnalogHeadsetDevice != null) {
            sb.append(" " + getString(R.string.audio_routingnotification_connected));
        } else {
            sb.append(" " + getSupportString(mAnalogHeadsetSupport));
        }

        // USB Headset
        sb.append("\n" + getString(R.string.audio_routingnotification_usbheadsetcolon));
        if (mUsbHeadsetDevice != null) {
            sb.append(" " + getString(R.string.audio_routingnotification_connected));
        } else {
            sb.append(" " + getSupportString(mUsbHeadsetSupport));
        }

        // USB Interface
        sb.append("\n" + getString(R.string.audio_routingnotification_usbdevicecolon));
        if (mUsbInterfaceDevice != null) {
            sb.append(" " + getString(R.string.audio_routingnotification_connected));
        } else {
            sb.append(" " + getSupportString(mUsbInterfaceSupport));
        }

        return sb.toString();
    }

    //
    // Device State Helpers
    //
    /**
     *
     */
    void scanConnectedDevices() {
        mAnalogHeadsetDevice = null;
        mUsbHeadsetDevice = null;
        mUsbInterfaceDevice = null;

        AudioDeviceInfo[] audioDevices = mAudioManager.getDevices(mRole);
        for (AudioDeviceInfo devInfo : audioDevices) {
            if (devInfo.getType() == AudioDeviceInfo.TYPE_WIRED_HEADSET) {
                mAnalogHeadsetDevice = devInfo;
                mAnalogHeadsetSupport = AudioDeviceUtils.SUPPORTSDEVICE_YES;
                mSupportsWiredPeripheral = true;
            } else if (devInfo.getType() == AudioDeviceInfo.TYPE_USB_HEADSET) {
                mUsbHeadsetDevice = devInfo;
                mUsbHeadsetSupport = AudioDeviceUtils.SUPPORTSDEVICE_YES;
                mSupportsWiredPeripheral = true;
            } else if (devInfo.getType() == AudioDeviceInfo.TYPE_USB_DEVICE) {
                mUsbInterfaceDevice = devInfo;
                mUsbInterfaceSupport = AudioDeviceUtils.SUPPORTSDEVICE_YES;
                mSupportsWiredPeripheral = true;
            }
        }
    }

    void calculatePass() {
        getPassButton().setEnabled(isReportLogOkToPass() && mRoutingNotificationReceived);

        if (!isReportLogOkToPass()) {
            mResultsText.setText(getResources().getString(R.string.audio_general_reportlogtest));
        } else if (mRoutingNotificationReceived) {
            mResultsText.setText(getString(R.string.audio_routingnotification_passmessage));
        }
    }

    protected void showStartupError(String deviceName,
                                    int buildResult, int openResult, int startResult) {
        if (buildResult != StreamBase.OK) {
            mResultsText.setText(String.format(getString(R.string.audio_general_build_fail),
                    deviceName, buildResult));
        } else if (openResult != StreamBase.OK) {
            mResultsText.setText(String.format(getString(R.string.audio_general_open_fail),
                    deviceName, openResult));
        } else if (startResult != StreamBase.OK) {
            mResultsText.setText(String.format(getString(R.string.audio_general_start_fail),
                    deviceName, startResult));
        }
    }

    //
    // PassFailButtons Overrides
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
    public void recordTestResults() {
        super.recordTestResults();

        // Subclasses should submit after adding their data
        CtsVerifierReportLog reportLog = getReportLog();
        reportLog.addValue(
                KEY_WIRED_PORT_SUPPORTED,
                mSupportsWiredPeripheral ? 1 : 0,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                KEY_CONNECTED_PERIPHERAL,
                mConnectedPeripheralName,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                KEY_ROUTING_RECEIVED,
                mRoutingNotificationReceived ? 1 : 0,
                ResultType.NEUTRAL,
                ResultUnit.NONE);
    }

    /**
     * Receive a broadcast Intent when a headset is plugged in or unplugged.
     */
    public class PluginBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            scanConnectedDevices();
            mDevSupportStateText.setText(buildDeviceSupportString());
        }
    }

    abstract void startAudio();
    abstract void stopAudio();
}
