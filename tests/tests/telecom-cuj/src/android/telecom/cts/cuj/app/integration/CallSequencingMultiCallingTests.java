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
import static android.telecom.Call.STATE_HOLDING;
import static android.telecom.cts.apps.CallSequencingUtil.CALL_TYPE_NAME;
import static android.telecom.cts.apps.CallSequencingUtil.INCOMING_MANAGED_CALL;
import static android.telecom.cts.apps.CallSequencingUtil.OUTGOING_MANAGED_CALL;
import static android.telecom.cts.apps.CallSequencingUtil.VALID_COMBINATIONS;
import static android.telecom.cts.apps.CallSequencingUtil.getTelecomTestApp;
import static android.telecom.cts.apps.CallSequencingUtil.isOutgoing;
import static android.telecom.cts.apps.TelecomTestApp.ManagedConnectionServiceApp;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

import android.platform.test.annotations.RequiresFlagsEnabled;
import android.telecom.Call;
import android.telecom.CallAttributes;
import android.telecom.PhoneAccountHandle;
import android.telecom.VideoProfile;
import android.telecom.cts.apps.AppControlWrapper;
import android.telecom.cts.apps.CallSequencingUtil;
import android.telecom.cts.apps.CallSequencingValidator;
import android.telecom.cts.apps.CallStateTransitionOperation;
import android.telecom.cts.cuj.BaseAppVerifier;

import com.android.server.telecom.flags.Flags;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RunWith(Parameterized.class)
public class CallSequencingMultiCallingTests extends BaseAppVerifier {
    public static final String TAG = "CallSequencingTests";

    private static class CallParameters {
        private List<Integer> mSequence;
        CallParameters(List<Integer> callSequence) {
            mSequence = callSequence;
        }
        private List<Integer> getSequence() {
            return mSequence;
        }
        @Override
        public String toString() {
            StringBuilder nameBuilder = new StringBuilder();
            mSequence.stream().map(i -> CALL_TYPE_NAME[i]).forEach(s -> {
                nameBuilder.append(" ");
                nameBuilder.append(s);
                nameBuilder.append(" ");
            });
            return nameBuilder.toString();
        }
    }

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<CallParameters> data() {
        return generateData().stream().map(CallParameters::new).collect(Collectors.toList());
    }

    public final CallParameters mParams;

    public CallSequencingMultiCallingTests(CallParameters params) {
        mParams = params;
    }

    @Test
    @RequiresFlagsEnabled({Flags.FLAG_ENABLE_CALL_SEQUENCING})
    public void testCallSequencing() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        List<AppControlWrapper> apps = Collections.emptyList();
        CallSequencingValidator c1Validator = new CallSequencingValidator();
        CallSequencingValidator c2Validator = new CallSequencingValidator();
        CallSequencingValidator c3Validator = new CallSequencingValidator();
        try {
            List<CallAttributes> attrs = mParams.getSequence().stream()
                    .map(i -> {
                        try {
                            if (getTelecomTestApp(i) == ManagedConnectionServiceApp) {
                                return getDefaultAttributes(getTelecomTestApp(i),
                                        getPhoneAccountHandle(i), isOutgoing(i));
                            } else {
                                return getDefaultAttributes(getTelecomTestApp(i),
                                        isOutgoing(i));
                            }
                        } catch (Exception e) {
                            throw new IllegalArgumentException(e);
                        }
                    }).toList();
            apps = mParams.getSequence().stream().map(CallSequencingUtil::getTelecomTestApp)
                    .map(i -> {
                        try {
                            return bindToApp(i);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }).collect(Collectors.toList());
            // Place first call
            AppControlWrapper app = apps.get(0);
            CallAttributes attr = attrs.get(0);
            String c1 = addCallAndVerify(app, attr, c1Validator);
            if (attr.getDirection() == CallAttributes.DIRECTION_INCOMING) {
                answerViaInCallService(c1, VideoProfile.STATE_AUDIO_ONLY);
                CallStateTransitionOperation op = c1Validator.completePendingOperationOrTimeout(
                        CallStateTransitionOperation.OPERATION_ANSWER);
                assertNotNull("ANSWER operation never received for first call" + c1, op);
                verifyCallIsInState(c1, STATE_ACTIVE);
            } else {
                setCallStateAndVerify(app, c1, Call.STATE_ACTIVE);
            }
            // Place second call
            app = apps.get(1);
            attr = attrs.get(1);
            String c2 = addCallAndVerify(app, attr, c2Validator);
            if (attr.getDirection() == CallAttributes.DIRECTION_INCOMING) {
                answerViaInCallService(c2, VideoProfile.STATE_AUDIO_ONLY);
                // Verify first call held and second call is active
                CallStateTransitionOperation holdOp = c1Validator.completePendingOperationOrTimeout(
                        CallStateTransitionOperation.OPERATION_HOLD);
                assertNotNull("HOLD operation never received for first call " + c1,
                        holdOp);
                verifyCallIsInState(c1, Call.STATE_HOLDING);
                CallStateTransitionOperation answerOp = c2Validator
                        .completePendingOperationOrTimeout(
                                CallStateTransitionOperation.OPERATION_ANSWER);
                assertNotNull("ANSWER operation never received for second call " + c2,
                        answerOp);
                verifyCallIsInState(c2, STATE_ACTIVE);
                // Verify that HOLD operation is received before the ANSWER operation
                long holdOpTimestamp = holdOp.getCreationTimeMs();
                long answerOpTimestamp = answerOp.getCreationTimeMs();
                assertTrue("HOLD operation should've been received before ANSWER operation",
                        holdOpTimestamp < answerOpTimestamp);
            } else {
                // Verify first call held
                CallStateTransitionOperation holdOp = c1Validator.completePendingOperationOrTimeout(
                        CallStateTransitionOperation.OPERATION_HOLD);
                assertNotNull("HOLD operation never received for first call " + c1,
                        holdOp);
                verifyCallIsInState(c1, Call.STATE_HOLDING);
                setCallStateAndVerify(app, c2, STATE_ACTIVE);
            }
            // Third call
            app = apps.get(2);
            attr = attrs.get(2);
            boolean isNewCallVoip = app.isVoipControl();
            boolean isHeldCallManaged = apps.get(0).isManagedControl();
            boolean isActiveCallManaged = apps.get(1).isManagedControl();
            boolean canActiveCallHold = isCallHoldable(c2);

            if (attr.getDirection() == CallAttributes.DIRECTION_INCOMING) {
                // Verify second call held and third call is active
                String c3 = addCallAndVerifyNewCall(app, attr, c2, c3Validator);
                answerViaInCallService(c3, VideoProfile.STATE_AUDIO_ONLY);
                // If active call is VOIP, active call is held.
                // If active call is managed:
                //    (1) if it can hold: hold active
                //    (2) if it can't:
                //           - if 3rd call is VOIP and held call is managed, 3rd call is rejected
                //           - else, disconnect held + hold active
                CallStateTransitionOperation heldCallOp = null;
                CallStateTransitionOperation activeCallOp;
                CallStateTransitionOperation newCallOp;
                boolean newCallRejected = false;
                if (isActiveCallManaged && !canActiveCallHold) {
                    if (isNewCallVoip && isHeldCallManaged) {
                        newCallOp =
                                c3Validator.completePendingOperationOrTimeout(
                                        CallStateTransitionOperation.OPERATION_ANSWER);
                        assertNull(
                                "Answer operation should never have been received for "
                                        + "third call "
                                        + c3,
                                newCallOp);
                        newCallRejected = true;
                    } else {
                        heldCallOp =
                                c1Validator.completePendingOperationOrTimeout(
                                        CallStateTransitionOperation.OPERATION_DISCONNECT);
                        assertNotNull(
                                "DISCONNECT operation never received for second call " + c2,
                                heldCallOp);
                    }
                }
                if (!newCallRejected) {
                    // Verify active call is held
                    activeCallOp =
                            c2Validator.completePendingOperationOrTimeout(
                                    CallStateTransitionOperation.OPERATION_HOLD);
                    assertNotNull(
                            "HOLD operation never received for second call " + c2, activeCallOp);
                    verifyCallIsInState(c2, STATE_HOLDING);
                    // If the held call was disconnected, verify that it was done before the active
                    // call was held across phone accounts
                    if (heldCallOp != null
                            && apps.get(0).getTelecomApps() != apps.get(1).getTelecomApps()) {
                        // Verify that DISCONNECT operation for the held call is received before
                        // the HOLD operation for the active call.
                        long disconnectTimestamp = heldCallOp.getCreationTimeMs();
                        long holdTimestamp = activeCallOp.getCreationTimeMs();
                        assertTrue(
                                "DISCONNECT operation for the held call should've been "
                                        + "received before HOLD operation for the active call",
                                disconnectTimestamp < holdTimestamp);
                    }
                    // Verify that the new call was answered.
                    newCallOp =
                            c3Validator.completePendingOperationOrTimeout(
                                    CallStateTransitionOperation.OPERATION_ANSWER);
                    assertNotNull(
                            "ANSWER operation never received for third call " + c3, newCallOp);
                    verifyCallIsInState(c3, STATE_ACTIVE);
                    if (app.getTelecomApps() != apps.get(1).getTelecomApps()) {
                        // Verify that HOLD operation is received before the ANSWER operation
                        long holdTimestamp = activeCallOp.getCreationTimeMs();
                        long answerTimestamp = newCallOp.getCreationTimeMs();
                        assertTrue(
                                "HOLD operation should've been received before ANSWER operation",
                                holdTimestamp < answerTimestamp);
                    }
                }
            }
            //NOTE: the outgoing case is not possible to test right now.
        } finally {
            c1Validator.cleanup();
            c2Validator.cleanup();
            c3Validator.cleanup();
            tearDownApps(apps);
        }
    }

    /**
     * @return Every permutation of VALID_COMBINATIONS that will fit in three slots: first call,
     * second call, third call.
     */
    private static List<List<Integer>> generateData() {
        List<List<Integer>> data = new ArrayList<>(256);
        for (int c1 : VALID_COMBINATIONS) {
            for (int c2 : VALID_COMBINATIONS) {
                for (int c3 : VALID_COMBINATIONS) {
                    if (!filterOutInvalidCombos(c3)) {
                        data.add(Arrays.asList(c1, c2, c3));
                    }
                }
            }
        }
        return data;
    }

    private static boolean filterOutInvalidCombos(int c3) {
        // 3rd outgoing call behavior is not well defined, skip for now.
        return isOutgoing(c3);
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

