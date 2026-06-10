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

package android.telecom.cts.cuj.app.integration;

import static android.telecom.Call.STATE_ACTIVE;
import static android.telecom.Call.STATE_DISCONNECTED;
import static android.telecom.Call.STATE_HOLDING;
import static android.telecom.cts.apps.CallSequencingUtil.CALL_TYPE_NAME;
import static android.telecom.cts.apps.CallSequencingUtil.INCOMING_MANAGED_CALL;
import static android.telecom.cts.apps.CallSequencingUtil.OUTGOING_MANAGED_CALL;
import static android.telecom.cts.apps.CallSequencingUtil.VALID_COMBINATIONS;
import static android.telecom.cts.apps.CallSequencingUtil.getTelecomTestApp;
import static android.telecom.cts.apps.CallSequencingUtil.isOutgoing;
import static android.telecom.cts.apps.TelecomTestApp.ManagedConnectionServiceApp;

import static junit.framework.Assert.assertNotNull;

import android.platform.test.annotations.RequiresFlagsEnabled;
import android.telecom.CallAttributes;
import android.telecom.PhoneAccountHandle;
import android.telecom.VideoProfile;
import android.telecom.cts.apps.AppControlWrapper;
import android.telecom.cts.apps.CallSequencingValidator;
import android.telecom.cts.apps.CallStateTransitionOperation;
import android.telecom.cts.apps.TelecomTestApp;
import android.telecom.cts.cuj.BaseAppVerifier;
import android.util.Pair;

import com.android.server.telecom.flags.Flags;

import org.junit.runner.RunWith;
import org.junit.Test;
import org.junit.runners.Parameterized;

import java.util.Arrays;

@RunWith(Parameterized.class)
public class CallSequencingSingleCallingTests extends BaseAppVerifier {
    private static class CallTypeParameter {
        private final Integer mCallType;
        CallTypeParameter(Integer callType) {
            mCallType = callType;
        }

        public Integer getCallType() {
            return mCallType;
        }

        @Override
        public String toString() {
            return CALL_TYPE_NAME[mCallType];
        }
    }

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<CallTypeParameter> data() {
        return () ->  Arrays.stream(VALID_COMBINATIONS)
                .mapToObj(CallTypeParameter::new).iterator();
    }

    public final CallTypeParameter mCallTypeParameter;

    public CallSequencingSingleCallingTests(CallTypeParameter callTypeParameter) {
        mCallTypeParameter = callTypeParameter;
    }

    @Test
    @RequiresFlagsEnabled({Flags.FLAG_ENABLE_CALL_SEQUENCING})
    public void testSingleCallOperations() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        int callType = mCallTypeParameter.getCallType();
        CallSequencingValidator c1Validator = new CallSequencingValidator();
        AppControlWrapper app = null;
        CallAttributes attr;
        try {
            TelecomTestApp testApp = getTelecomTestApp(callType);
            app = bindToApp(testApp);
            attr = testApp == ManagedConnectionServiceApp
                    ? getDefaultAttributes(
                    testApp, getPhoneAccountHandle(callType), isOutgoing(callType))
                    : getDefaultAttributes(testApp, isOutgoing(callType));

            String c1 = addCallAndVerify(app, attr, c1Validator);
            if (attr.getDirection() == CallAttributes.DIRECTION_INCOMING) {
                answerViaInCallService(c1, VideoProfile.STATE_AUDIO_ONLY);
                CallStateTransitionOperation answerOp = c1Validator
                        .completePendingOperationOrTimeout(
                                CallStateTransitionOperation.OPERATION_ANSWER);
                assertNotNull("ANSWER operation never received for first call" + c1, answerOp);
                verifyCallIsInState(c1, STATE_ACTIVE);
            } else {
                setCallStateAndVerify(app, c1, STATE_ACTIVE);
            }

            // Hold call via ICS and verify state change
            holdCallViaInCallService(c1);
            CallStateTransitionOperation holdOp = c1Validator.completePendingOperationOrTimeout(
                    CallStateTransitionOperation.OPERATION_HOLD);
            assertNotNull("HOLD operation never received for second call " + c1,
                    holdOp);
            verifyCallIsInState(c1, STATE_HOLDING);
            // Unhold call via ICS and verify state change
            unholdCallViaInCallService(c1);
            CallStateTransitionOperation unholdOp = c1Validator.completePendingOperationOrTimeout(
                    CallStateTransitionOperation.OPERATION_UNHOLD);
            assertNotNull("UNHOLD operation never received for second call " + c1,
                    unholdOp);
            verifyCallIsInState(c1, STATE_ACTIVE);
            // Disconnect call via ICS and verify state change
            disconnectCallViaInCallService(c1);
            CallStateTransitionOperation disconnectOp = c1Validator.completePendingOperationOrTimeout(
                    CallStateTransitionOperation.OPERATION_DISCONNECT);
            assertNotNull("DISCONNECT operation never received for second call " + c1,
                    disconnectOp);
            verifyCallIsInState(c1, STATE_DISCONNECTED);
        } finally {
            c1Validator.cleanup();
            tearDownApp(app);
        }
    }

    public static PhoneAccountHandle getPhoneAccountHandle(int callType) {
        switch (callType) {
            case INCOMING_MANAGED_CALL -> {
                return MANAGED_HANDLE_1;
            }
            case OUTGOING_MANAGED_CALL -> {
                return MANAGED_CLONE_HANDLE_1;
            }
            default -> {
                return null;
            }
        }
    }
}
