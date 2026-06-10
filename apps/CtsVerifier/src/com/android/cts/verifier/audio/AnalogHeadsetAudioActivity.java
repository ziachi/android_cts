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
 * limitations under the License.
 */

package com.android.cts.verifier.audio;

import static com.android.cts.verifier.TestListActivity.sCurrentDisplayMode;
import static com.android.cts.verifier.TestListAdapter.setTestNameSuffix;

import android.graphics.Color;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.verifier.CtsVerifierReportLog;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.audio.audiolib.AudioDeviceUtils;
import com.android.cts.verifier.audio.audiolib.AudioSystemFlags;
import com.android.cts.verifier.audio.audiolib.DisplayUtils;

@CddTest(requirement = "7.8.2.1/C-1-1,C-1-2,C-1-3,C-1-4,C-2-1")
public class AnalogHeadsetAudioActivity
        extends PassFailButtons.Activity {
    private static final String TAG = AnalogHeadsetAudioActivity.class.getSimpleName();
    private static final boolean DEBUG = false;

    private AudioManager    mAudioManager;
    private ConnectListener mConnectListener;
    private boolean mIsTVOrFixedVolume;

    // UI
    private TextView mHeadsetStatusTxt;

    private TextView mButtonsPromptTxt;
    private TextView mHeadsetHookText;
    private TextView mHeadsetVolUpText;
    private TextView mHeadsetVolDownText;

    // Devices
    int mHeadsetSupport;

    private AudioDeviceInfo mHeadsetDeviceInfo;

    // Buttons
    private boolean mHasHeadsetHook;
    private boolean mHasPlayPause;
    private boolean mHasVolUp;
    private boolean mHasVolDown;

    private TextView mResultsTxt;

    // ReportLog Schema
    private static final String SECTION_ANALOG_HEADSET = "analog_headset_activity";
    private static final String KEY_HAS_HEADSET_PORT = "has_headset_port";
    private static final String KEY_CLAIMS_HEADSET_PORT = "claims_headset_port";
    private static final String KEY_HEADSET_CONNECTED = "headset_connected";
    private static final String KEY_KEYCODE_HEADSETHOOK = "keycode_headset_hook";
    private static final String KEY_KEYCODE_PLAY_PAUSE = "keycode_play_pause";
    private static final String KEY_KEYCODE_VOLUME_UP = "keycode_volume_up";
    private static final String KEY_KEYCODE_VOLUME_DOWN = "keycode_volume_down";

    public AnalogHeadsetAudioActivity() {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.audio_headset_audio_activity);

        mAudioManager = getSystemService(AudioManager.class);
        mConnectListener = new ConnectListener();
        mIsTVOrFixedVolume = AudioSystemFlags.isTV(this) || mAudioManager.isVolumeFixed();

        mHeadsetStatusTxt = findViewById(R.id.headset_analog_jackstatus);

        // Keycodes
        mButtonsPromptTxt = findViewById(R.id.analog_headset_keycodes_prompt);
        mHeadsetHookText = findViewById(R.id.headset_keycode_headsethook);
        mHeadsetVolUpText = findViewById(R.id.headset_keycode_volume_up);
        mHeadsetVolDownText = findViewById(R.id.headset_keycode_volume_down);

        if (mIsTVOrFixedVolume) {
            mButtonsPromptTxt.setVisibility(View.GONE);
            mHeadsetHookText.setVisibility(View.GONE);
            mHeadsetVolUpText.setVisibility(View.GONE);
            mHeadsetVolDownText.setVisibility(View.GONE);
            findViewById(R.id.headset_keycodes).setVisibility(View.GONE);
        }

        mResultsTxt = findViewById(R.id.headset_results);

        showKeyMessagesState();

        setInfoResources(R.string.analog_headset_test, mIsTVOrFixedVolume
                ? R.string.analog_headset_test_info_tv : R.string.analog_headset_test_info, -1);

        mHeadsetSupport = AudioDeviceUtils.supportsAnalogHeadset(this);
        displayHeadsetSupport();

        setPassFailButtonClickListeners();
        getPassButton().setEnabled(calculatePass());

        DisplayUtils.setKeepScreenOn(this, true);
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

    private void displayHeadsetSupport() {
        switch (mHeadsetSupport) {
            case AudioDeviceUtils.SUPPORTSDEVICE_NO:
                mHeadsetStatusTxt.setText(
                        getString(R.string.analog_headset_dutdoesntsupportheadset));
                break;

            case AudioDeviceUtils.SUPPORTSDEVICE_YES:
                mHeadsetStatusTxt.setText(getString(R.string.analog_headset_supportsheadset));
                break;

            case AudioDeviceUtils.SUPPORTSDEVICE_UNDETERMINED:
                mHeadsetStatusTxt.setText(getString(R.string.analog_headset_supportundertermined));
                break;
        }
    }

    private String generateStateString() {
        StringBuilder sb = new StringBuilder();

        if (mHeadsetSupport == AudioDeviceUtils.SUPPORTSDEVICE_UNDETERMINED) {
            sb.append(getString(R.string.analog_headset_heasdsetunknown));
        } else if (mHeadsetSupport == AudioDeviceUtils.SUPPORTSDEVICE_NO) {
            sb.append(getString(R.string.analog_headset_reportnojack));
        } else if (mHeadsetDeviceInfo == null) {
            sb.append(getString(R.string.analog_headset_connect_headset));
        } else if (!mIsTVOrFixedVolume
                && mHeadsetDeviceInfo != null
                && (!(mHasHeadsetHook || mHasPlayPause)
                        || !mHasVolUp || !mHasVolDown)) {
            sb.append(getString(R.string.analog_headset_test_buttons));
        } else {
            sb.append(getString(R.string.analog_headset_pass));
        }
        return sb.toString();
    }

    private boolean calculatePass() {
        if (mHeadsetSupport != AudioDeviceUtils.SUPPORTSDEVICE_YES) {
            mResultsTxt.setText(getString(R.string.analog_headset_nosupportpass));
            return true;
        } else if (!isReportLogOkToPass()) {
            mResultsTxt.setText(getString(R.string.audio_general_reportlogtest));
            return false;
        } else {
            boolean pass = mHeadsetDeviceInfo != null
                    && (mIsTVOrFixedVolume
                        || ((mHasHeadsetHook || mHasPlayPause) && mHasVolUp && mHasVolDown));
            if (pass) {
                mResultsTxt.setText(getString(R.string.analog_headset_pass));
            }
            mResultsTxt.setText(generateStateString());
            return pass;
        }
    }

    @Override
    public boolean requiresReportLog() {
        return true;
    }

    @Override
    public String getReportFileName() { return PassFailButtons.AUDIO_TESTS_REPORT_LOG_NAME; }

    @Override
    public final String getReportSectionName() {
        return setTestNameSuffix(sCurrentDisplayMode, SECTION_ANALOG_HEADSET);
    }

    //
    // Reporting
    //
    @Override
    public void recordTestResults() {
        CtsVerifierReportLog reportLog = getReportLog();
        reportLog.addValue(
                KEY_HAS_HEADSET_PORT,
                mHeadsetDeviceInfo != null ? 1 : 0,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

//        reportLog.addValue(
//                KEY_CLAIMS_HEADSET_PORT,
//                mPlaybackSuccess ? 1 : 0,
//                ResultType.NEUTRAL,
//                ResultUnit.NONE);

        reportLog.addValue(
                KEY_HEADSET_CONNECTED,
                mHeadsetDeviceInfo != null ? 1 : 0,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                KEY_KEYCODE_HEADSETHOOK,
                mHasHeadsetHook ? 1 : 0,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                KEY_KEYCODE_PLAY_PAUSE,
                mHasPlayPause ? 1 : 0,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                KEY_KEYCODE_VOLUME_UP,
                mHasVolUp ? 1 : 0,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.addValue(
                KEY_KEYCODE_VOLUME_DOWN,
                mHasVolDown ? 1 : 0,
                ResultType.NEUTRAL,
                ResultUnit.NONE);

        reportLog.submit();
    }

    //
    // UI
    //
    private void resetButtonMessages() {
        mHasHeadsetHook = false;
        mHasPlayPause = false;
        mHasVolUp = false;
        mHasVolDown = false;
        showKeyMessagesState();
    }

    private void showKeyMessagesState() {
        mHeadsetHookText.setTextColor((mHasHeadsetHook || mHasPlayPause)
                ? Color.WHITE : Color.GRAY);
        mHeadsetVolUpText.setTextColor(mHasVolUp ? Color.WHITE : Color.GRAY);
        mHeadsetVolDownText.setTextColor(mHasVolDown ? Color.WHITE : Color.GRAY);
    }

    //
    // Devices
    //
    private void scanPeripheralList(AudioDeviceInfo[] devices) {
        mHeadsetDeviceInfo = null;
        for (AudioDeviceInfo devInfo : devices) {
            if (devInfo.getType() == AudioDeviceInfo.TYPE_WIRED_HEADSET) {
                mHeadsetDeviceInfo = devInfo;
                break;
            }
        }

        mResultsTxt.setText(generateStateString());
    }

    private class ConnectListener extends AudioDeviceCallback {
        /*package*/ ConnectListener() {}

        //
        // AudioDevicesManager.OnDeviceConnectionListener
        //
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            scanPeripheralList(mAudioManager.getDevices(AudioManager.GET_DEVICES_ALL));
            resetButtonMessages();
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            scanPeripheralList(mAudioManager.getDevices(AudioManager.GET_DEVICES_ALL));
            resetButtonMessages();
        }
    }

    //
    // Keycodes
    //
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mHeadsetDeviceInfo != null) {
            // we have an analog headset plugged in
            switch (keyCode) {
                case KeyEvent.KEYCODE_HEADSETHOOK:
                    mHasHeadsetHook = true;
                    showKeyMessagesState();
                    getPassButton().setEnabled(calculatePass());
                    break;

                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                    mHasPlayPause = true;
                    showKeyMessagesState();
                    getPassButton().setEnabled(calculatePass());
                    break;

                case KeyEvent.KEYCODE_VOLUME_UP:
                    mHasVolUp = true;
                    showKeyMessagesState();
                    getPassButton().setEnabled(calculatePass());
                    break;

                case KeyEvent.KEYCODE_VOLUME_DOWN:
                    mHasVolDown = true;
                    showKeyMessagesState();
                    getPassButton().setEnabled(calculatePass());
                    break;
            }
        }
        return super.onKeyDown(keyCode, event);
    }
}
