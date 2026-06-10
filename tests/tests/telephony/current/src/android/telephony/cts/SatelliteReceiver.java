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

package android.telephony.cts;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.satellite.SatelliteManager;

public class SatelliteReceiver extends BroadcastReceiver {
    private static final String TAG = "SatelliteReceiver";
    public static final String TEST_INTENT =
            "CTS_ACTION_SATELLITE_SUBSCRIBER_ID_LIST_CHANGED_RECEIVED";

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (action == null) {
            logd("NULL action for intent " + intent);
            return;
        }
        switch (action) {
            case SatelliteManager.ACTION_SATELLITE_SUBSCRIBER_ID_LIST_CHANGED:
                context.sendBroadcast(new Intent(TEST_INTENT));
                logd("onReceive: " + action);
                break;
            default:
                break;
        }
    }

    private void logd(String log) {
        android.util.Log.d(TAG, log);
    }
}
