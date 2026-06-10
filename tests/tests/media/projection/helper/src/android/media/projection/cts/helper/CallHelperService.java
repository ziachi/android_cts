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

package android.media.projection.cts.helper;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.OutcomeReceiver;
import android.telecom.CallAttributes;
import android.telecom.CallControl;
import android.telecom.CallControlCallback;
import android.telecom.CallEndpoint;
import android.telecom.CallEventCallback;
import android.telecom.CallException;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Activity used for testing (phone) call related features */
public class CallHelperService extends Service {

    private static final String CALL_HELPER_START_CALL = "start_call";
    static final String CALL_HELPER_STOP_CALL = "stop_call";

    private int mTimeoutMs;
    private TelecomManager mTelecomManager;
    private PhoneAccountHandle mPhoneAccountHandle;
    private PhoneAccount mPhoneAccount;
    private CallControl mCallControl;
    private OutcomeReceiver<CallControl, CallException> mOutcomeReceiver;

    @Override
    public void onCreate() {
        super.onCreate();
        mTimeoutMs = 1000;
        mTelecomManager = getSystemService(TelecomManager.class);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();

        if (CALL_HELPER_START_CALL.equals(action)) {
            try {
                startPhoneCall();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        if (CALL_HELPER_STOP_CALL.equals(action)) {
            endPhoneCall();
            stopSelf();
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        endPhoneCall();
        super.onDestroy();
    }

    private void startPhoneCall() throws InterruptedException {
        mPhoneAccountHandle =
                new PhoneAccountHandle(
                        new ComponentName(this, PhoneConnectionService.class),
                        "test_phone_account_handle");
        mPhoneAccount =
                new PhoneAccount.Builder(mPhoneAccountHandle, "test_phone_account")
                        .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
                        .build();
        mTelecomManager.registerPhoneAccount(mPhoneAccount);
        CallAttributes attributes =
                new CallAttributes.Builder(
                                mPhoneAccountHandle,
                                CallAttributes.DIRECTION_INCOMING,
                                "a call!",
                                Uri.parse("tel:555-5555"))
                        .build();

        CountDownLatch latch = new CountDownLatch(1);
        mOutcomeReceiver =
                result -> {
                    mCallControl = result;
                    latch.countDown();
                };
        mTelecomManager.addCall(
                attributes,
                getMainExecutor(),
                mOutcomeReceiver,
                new HelperCallControlCallback(),
                new HelperCallEventCallback());

        boolean mCallAdded = latch.await(mTimeoutMs, TimeUnit.MILLISECONDS);
        if (mCallAdded) {
            mCallControl.answer(CallAttributes.AUDIO_CALL, getMainExecutor(), result -> {});
        }
    }

    private void endPhoneCall() {
        if (mCallControl != null) {
            mCallControl.disconnect(
                    new DisconnectCause(DisconnectCause.LOCAL), getMainExecutor(), result -> {});
        }
        if (mPhoneAccountHandle != null) {
            mTelecomManager.unregisterPhoneAccount(mPhoneAccountHandle);
            mPhoneAccountHandle = null;
            mPhoneAccount = null;
        }
    }

    private static final class HelperCallEventCallback implements CallEventCallback {
        @Override
        public void onCallEndpointChanged(CallEndpoint newCallEndpoint) {}

        @Override
        public void onAvailableCallEndpointsChanged(List<CallEndpoint> availableEndpoints) {}

        @Override
        public void onMuteStateChanged(boolean isMuted) {}

        @Override
        public void onCallStreamingFailed(int reason) {}

        @Override
        public void onEvent(String event, Bundle extras) {}
    }

    private static final class HelperCallControlCallback implements CallControlCallback {
        @Override
        public void onSetActive(Consumer<Boolean> wasCompleted) {}

        @Override
        public void onSetInactive(Consumer<Boolean> wasCompleted) {}

        @Override
        public void onAnswer(int videoState, Consumer<Boolean> wasCompleted) {}

        @Override
        public void onDisconnect(DisconnectCause disconnectCause, Consumer<Boolean> wasCompleted) {}

        @Override
        public void onCallStreamingStarted(Consumer<Boolean> wasCompleted) {}
    }

    private static final class PhoneConnectionService extends ConnectionService {}
}
