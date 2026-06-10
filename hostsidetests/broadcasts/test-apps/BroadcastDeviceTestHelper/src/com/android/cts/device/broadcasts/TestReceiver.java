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

package com.android.cts.device.broadcasts;

import static com.android.cts.host.broadcasts.Constants.BROADCAST_PROCESSING_TIME_MS;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

public class TestReceiver extends BroadcastReceiver {
    private static final String TAG = "TestReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "onReceive: " + intent);

        // Introduce a delay to ensure broadcast processing takes longer than 10 milliseconds.
        // This is necessary for testing BroadcastProcessed atom logging.
        SystemClock.sleep(BROADCAST_PROCESSING_TIME_MS);
    }
}
