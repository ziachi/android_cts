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

package com.android.cts.usb.serialtest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.util.Log;

/**
 * Broadcast receiver to print out usb states
 */
public class CtsUsbStateBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "CtsUsbStateBroadcastReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (UsbManager.ACTION_USB_STATE.equals(intent.getAction())) {
            if (intent.getBooleanExtra(UsbManager.USB_CONFIGURED, false)) {
                Log.i(TAG, "CONFIGURED");
            } else if (intent.getBooleanExtra(UsbManager.USB_CONNECTED, false)) {
                Log.i(TAG, "CONNECTED");
            } else {
                Log.i(TAG, "DISCONNECTED");
            }
        }
    }
}
