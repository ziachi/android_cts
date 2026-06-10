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

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.widget.Button;

import com.android.cts.verifier.R;

class AudioLoopbackUtilitiesHandler implements View.OnClickListener {
    Context mContext;

    private Button mCalibrateButton;
    private Button mDevicesButton;

    AudioLoopbackUtilitiesHandler(Activity activity) {
        mContext = activity;
        mCalibrateButton = activity.findViewById(R.id.audio_utilities_calibrate_button);
        mCalibrateButton.setOnClickListener(this);

        mDevicesButton = activity.findViewById(R.id.audio_utilities_devices_button);
        mDevicesButton.setOnClickListener(this);
    }

    public void setEnabled(boolean enable) {
        mCalibrateButton.setEnabled(enable);
        mDevicesButton.setEnabled(enable);
    }

    //
    // View.OnClickHandler
    //
    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.audio_utilities_calibrate_button) {
            (new AudioLoopbackCalibrationDialog(mContext)).show();
        } else if (id == R.id.audio_utilities_devices_button) {
            (new AudioDevicesDialog(mContext)).show();
        }
    }
}
