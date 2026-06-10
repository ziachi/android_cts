/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * limitations under the License
 */

package android.telecom.cts.screeningtestapp;

import android.content.Intent;
import android.os.IBinder;
import android.telecom.Call;
import android.telecom.CallScreeningService;
import android.util.Log;

/**
 * Provides a CTS-test implementation of {@link CallScreeningService}.
 * This emulates a third-party implementation of {@link CallScreeningService}, where
 * {@code MockCallScreeningService} is intended to be the {@link CallScreeningService} bundled with
 * the default dialer app.
 */
public class CtsCallScreeningService extends CallScreeningService {
    private static final String TAG = CtsCallScreeningService.class.getSimpleName();
    private CallScreeningServiceControl mCallScreeningServiceControl;

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind: to call screening service");
        mCallScreeningServiceControl = CallScreeningServiceControl.getInstance();
        mCallScreeningServiceControl.onScreeningServiceBound();
        return super.onBind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "onUnbind: unbinding call screening service");
        mCallScreeningServiceControl = null;
        super.onUnbind(intent);
        return false;
    }

    @Override
    public void onScreenCall(Call.Details callDetails) {
        Log.i(TAG, "onScreenCall");
        if (mCallScreeningServiceControl != null) {
            CallScreeningService.CallResponse r = mCallScreeningServiceControl.getCallResponse();
            Log.i(
                    TAG,
                    String.format(
                            "onScreenCall: shouldDisallowCall=[%b], "
                                    + "shouldRejectCall=[%b], "
                                    + "shouldSilenceCall=[%b], "
                                    + "shouldSkipCallLog=[%b], "
                                    + "shouldSkipNotification=[%b] , mCallResponse.hash=[%d]",
                            r.getDisallowCall(),
                            r.getRejectCall(),
                            r.getSilenceCall(),
                            r.getSkipCallLog(),
                            r.getSkipNotification(),
                            r.hashCode()));
            respondToCall(callDetails, mCallScreeningServiceControl.getCallResponse());
        } else {
            Log.w(TAG, "No control interface.");
        }
    }
}
