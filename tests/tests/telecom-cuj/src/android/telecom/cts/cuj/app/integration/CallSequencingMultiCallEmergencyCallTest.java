/*
 * Copyright (C) 2025 The Android Open Source Project
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
import static android.telecom.Call.STATE_DIALING;
import static android.telecom.Call.STATE_DISCONNECTED;
import static android.telecom.Call.STATE_HOLDING;
import static android.telecom.Call.STATE_RINGING;
import static android.telecom.cts.apps.TelecomTestApp.ConnectionServiceVoipAppMain;
import static android.telecom.cts.apps.TelecomTestApp.ManagedConnectionServiceApp;
import static android.telecom.cts.apps.TelecomTestApp.ManagedConnectionServiceAppClone;
import static android.telecom.cts.apps.TelecomTestApp.TransactionalVoipAppMain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.RequiresFlagsEnabled;
import android.telecom.cts.apps.AppControlWrapper;
import android.telecom.cts.apps.CallSequencingValidator;
import android.telecom.cts.apps.CallStateTransitionOperation;
import android.telecom.cts.apps.TelecomTestApp;
import android.telecom.cts.cuj.BaseAppVerifier;
import android.telecom.cts.cuj.TestUtils;

import com.android.server.telecom.flags.Flags;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.List;

/** Call sequencing multi-call tests dealing with ECC */
@RunWith(Parameterized.class)
@RequiresFlagsEnabled({Flags.FLAG_ENABLE_CALL_SEQUENCING})
public class CallSequencingMultiCallEmergencyCallTest extends BaseAppVerifier {
    /**
     * Test parameters that consist of the following information: (1) telecom test apps being used
     * for the normal + normal + ECC (2) the call holdable state of the 1st + 2nd calls and if the
     * 2nd call is an outgoing call. (3) the expected call states of the 1st + 2nd calls after
     * placing the 2nd call and the call states of the same calls after the ECC is placed. The last
     * value indicates how many calls should be disconnected as a result of placing the ECC.
     */
    private static TelecomTestApp[][] sTelecomTestApps = {
        /* 1st call test app
         * 2nd call test app
         * ECC test app (expectation)
         */
        {
            ManagedConnectionServiceAppClone,
            ManagedConnectionServiceApp,
            ManagedConnectionServiceApp
        }, // 1
        {
            ManagedConnectionServiceApp,
            ManagedConnectionServiceAppClone,
            ManagedConnectionServiceAppClone
        }, // 2
        {
            ManagedConnectionServiceApp, ManagedConnectionServiceApp, ManagedConnectionServiceApp
        }, // 3
        {TransactionalVoipAppMain, ManagedConnectionServiceApp, ManagedConnectionServiceApp}, // 4
        {ManagedConnectionServiceApp, TransactionalVoipAppMain, ManagedConnectionServiceApp}, // 5
        {
            ConnectionServiceVoipAppMain, ManagedConnectionServiceApp, ManagedConnectionServiceApp
        }, // 6
        {
            ManagedConnectionServiceApp, ConnectionServiceVoipAppMain, ManagedConnectionServiceApp
        }, // 7
        {
            ManagedConnectionServiceApp,
            ManagedConnectionServiceAppClone,
            ManagedConnectionServiceApp
        }, // 8
        {
            ManagedConnectionServiceAppClone,
            ManagedConnectionServiceApp,
            ManagedConnectionServiceAppClone
        }, // 9
        {
            ManagedConnectionServiceApp, ManagedConnectionServiceApp, ManagedConnectionServiceApp
        }, // 10
        {
            ManagedConnectionServiceApp,
            ManagedConnectionServiceAppClone,
            ManagedConnectionServiceApp
        }, // 11
        {
            ManagedConnectionServiceAppClone,
            ManagedConnectionServiceApp,
            ManagedConnectionServiceAppClone
        }, // 12
        // Todo: - Disabling this test for now (this will be corrected by b/403620519)
        //        {
        //            ManagedConnectionServiceApp, ManagedConnectionServiceApp,
        // ManagedConnectionServiceApp
        //        }, // 13
        {
            ManagedConnectionServiceApp,
            ManagedConnectionServiceAppClone,
            ManagedConnectionServiceApp
        }, // 14
        {
            ManagedConnectionServiceAppClone,
            ManagedConnectionServiceApp,
            ManagedConnectionServiceAppClone
        }, // 15
        {TransactionalVoipAppMain, ManagedConnectionServiceApp, ManagedConnectionServiceApp}, // 16
        {
            ConnectionServiceVoipAppMain, ManagedConnectionServiceApp, ManagedConnectionServiceApp
        }, // 17
        {
            ManagedConnectionServiceApp,
            ManagedConnectionServiceAppClone,
            ManagedConnectionServiceApp
        }, // 18
        {
            ManagedConnectionServiceAppClone,
            ManagedConnectionServiceApp,
            ManagedConnectionServiceAppClone
        }, // 19
        {
            ManagedConnectionServiceApp, ManagedConnectionServiceApp, ManagedConnectionServiceApp
        }, // 20
        {
            ManagedConnectionServiceApp, ManagedConnectionServiceApp, ManagedConnectionServiceApp
        }, // 21
    };

    private static boolean[][] sCallsHoldableAndSecondCallOutgoing = {
        /* is1stCallHoldable,
         * is2ndCallHoldable,
         * is2ndCallOutgoing
         */
        /* holdable tests */
        {true, true, true}, // 1
        {true, true, true}, // 2
        {true, true, true}, // 3
        {true, true, true}, // 4
        {true, true, true}, // 5
        {true, true, true}, // 6
        {true, true, true}, // 7
        {true, true, true}, // 8
        {true, true, true}, // 9
        {true, true, true}, // 10
        {true, true, false}, // 11
        {true, true, false}, // 12
        //        {true, true, false}, // 13
        /* non-holdable tests */
        {true, false, true}, // 14
        {true, false, true}, // 15
        {true, false, true}, // 16
        {true, false, true}, // 17
        {false, true, false}, // 18
        {false, true, false}, // 19
        {false, false, false}, // 20
        {false, false, true}, // 21
    };
    private static int[][] sExpectedCallStatesAndNumCallsDisconnected = {
        /* expected1stCallStateAfter2nd,
         * expected2ndCallStateAfter1st,
         * expected1stCallStateAfterEcc,
         * expected2ndCallStateAfterEcc,
         * numDisconnectDueToEcc
         */
        {STATE_HOLDING, STATE_ACTIVE, STATE_DISCONNECTED, STATE_HOLDING, 1}, // 1
        {STATE_HOLDING, STATE_ACTIVE, STATE_DISCONNECTED, STATE_HOLDING, 1}, // 2
        {STATE_HOLDING, STATE_ACTIVE, STATE_HOLDING, STATE_DISCONNECTED, 1}, // 3
        {STATE_HOLDING, STATE_ACTIVE, STATE_DISCONNECTED, STATE_HOLDING, 1}, // 4
        {STATE_HOLDING, STATE_ACTIVE, STATE_HOLDING, STATE_DISCONNECTED, 1}, // 5
        {STATE_HOLDING, STATE_ACTIVE, STATE_DISCONNECTED, STATE_HOLDING, 1}, // 6
        {STATE_HOLDING, STATE_ACTIVE, STATE_HOLDING, STATE_DISCONNECTED, 1}, // 7
        {STATE_ACTIVE, STATE_DIALING, STATE_HOLDING, STATE_DISCONNECTED, 1}, // 8
        {STATE_ACTIVE, STATE_DIALING, STATE_HOLDING, STATE_DISCONNECTED, 1}, // 9
        {STATE_ACTIVE, STATE_DIALING, STATE_HOLDING, STATE_DISCONNECTED, 1}, // 10
        {STATE_ACTIVE, STATE_RINGING, STATE_HOLDING, STATE_DISCONNECTED, 1}, // 11
        {STATE_ACTIVE, STATE_RINGING, STATE_HOLDING, STATE_DISCONNECTED, 1}, // 12
        //        {STATE_ACTIVE, STATE_RINGING, STATE_HOLDING, STATE_DISCONNECTED, 1}, // 13
        {STATE_HOLDING, STATE_ACTIVE, STATE_HOLDING, STATE_DISCONNECTED, 1}, // 14
        {STATE_HOLDING, STATE_ACTIVE, STATE_HOLDING, STATE_DISCONNECTED, 1}, // 15
        {STATE_HOLDING, STATE_ACTIVE, STATE_DISCONNECTED, STATE_HOLDING, 1}, // 16
        {STATE_HOLDING, STATE_ACTIVE, STATE_DISCONNECTED, STATE_HOLDING, 1}, // 17
        {STATE_ACTIVE, STATE_RINGING, STATE_HOLDING, STATE_DISCONNECTED, 1}, // 18
        {STATE_ACTIVE, STATE_RINGING, STATE_HOLDING, STATE_DISCONNECTED, 1}, // 19
        {STATE_ACTIVE, STATE_RINGING, STATE_HOLDING, STATE_DISCONNECTED, 1}, // 20
        {STATE_HOLDING, STATE_ACTIVE, STATE_HOLDING, STATE_DISCONNECTED, 1}, // 21
    };

    private static class CallParameters {
        private TelecomTestApp[] mTestApps;
        private int[] mExpectedCallStatesAndNumCallsDisconnected;
        private boolean[] mCallsHoldableAndSecondCallOutgoing;

        CallParameters(
                TelecomTestApp[] testApps,
                int[] expectedCallStatesAndNumCallsDisconnected,
                boolean[] callsHoldableAndSecondCallOutgoing) {
            mTestApps = testApps;
            mExpectedCallStatesAndNumCallsDisconnected = expectedCallStatesAndNumCallsDisconnected;
            mCallsHoldableAndSecondCallOutgoing = callsHoldableAndSecondCallOutgoing;
        }

        private TelecomTestApp[] getTestApps() {
            return mTestApps;
        }

        private int[] getExpectedCallStatesAndNumsCallsDisconnected() {
            return mExpectedCallStatesAndNumCallsDisconnected;
        }

        private boolean[] getCallsHoldableAndSecondCallOutgoing() {
            return mCallsHoldableAndSecondCallOutgoing;
        }

        @Override
        public String toString() {
            StringBuilder nameBuilder = new StringBuilder();
            nameBuilder.append("\nTelecom test apps for 1st + 2nd + 3rd call: ");
            for (TelecomTestApp testApp : mTestApps) {
                nameBuilder.append(testApp);
                nameBuilder.append(", ");
            }
            nameBuilder.append(
                    "\nFirst and second call holdability and second " + "call is outgoing: ");
            for (boolean holdabilityOrOutgoingState : mCallsHoldableAndSecondCallOutgoing) {
                nameBuilder.append(holdabilityOrOutgoingState);
                nameBuilder.append(", ");
            }
            nameBuilder.append(
                    "\n"
                        + "Expected call states of 1st + 2nd call after placing 2nd call, 1st + 2nd"
                        + " call after placing the ECC, and expected number of calls disconnected"
                        + " as a result of placing the ECC: ");
            for (int expectedCallStateOrNumsCallsDisconnected :
                    mExpectedCallStatesAndNumCallsDisconnected) {
                nameBuilder.append(expectedCallStateOrNumsCallsDisconnected);
                nameBuilder.append(", ");
            }
            return nameBuilder.toString();
        }
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        // Emergency calling is not supported on devices without FEATURE_TELEPHONY
        // or FEATURE_TELECOM (i.e. Tangor).
        assumeTrue(mShouldTestTelecom && TestUtils.hasTelephonyFeature(mContext));
        setupForEmergencyCalling();
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        tearDownEmergencyCalling();
    }

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<CallParameters> data() {
        List<CallParameters> params = new ArrayList<>();
        for (int i = 0; i < sTelecomTestApps.length; i++) {
            CallParameters param =
                    new CallParameters(
                            sTelecomTestApps[i],
                            sExpectedCallStatesAndNumCallsDisconnected[i],
                            sCallsHoldableAndSecondCallOutgoing[i]);
            params.add(param);
        }
        return params;
    }

    public final CallParameters mParams;

    public CallSequencingMultiCallEmergencyCallTest(CallParameters params) {
        mParams = params;
    }

    @Test
    public void testMultiCallEcc() throws Exception {
        TelecomTestApp[] testApps = mParams.getTestApps();
        int[] expectedCallStatesAndNumCallsDisconnected =
                mParams.getExpectedCallStatesAndNumsCallsDisconnected();
        boolean[] callsHoldableAndSecondCallOutgoing =
                mParams.getCallsHoldableAndSecondCallOutgoing();
        assertEquals(testApps.length, 3);
        assertEquals(callsHoldableAndSecondCallOutgoing.length, 3);
        assertEquals(expectedCallStatesAndNumCallsDisconnected.length, 5);
        verifyAddEccWithTwoCalls(
                testApps[0],
                testApps[1],
                testApps[2],
                callsHoldableAndSecondCallOutgoing[0],
                callsHoldableAndSecondCallOutgoing[1],
                callsHoldableAndSecondCallOutgoing[2],
                expectedCallStatesAndNumCallsDisconnected[0],
                expectedCallStatesAndNumCallsDisconnected[1],
                expectedCallStatesAndNumCallsDisconnected[2],
                expectedCallStatesAndNumCallsDisconnected[3],
                expectedCallStatesAndNumCallsDisconnected[4]);
    }

    private void verifyAddEccWithTwoCalls(
            TelecomTestApp app1,
            TelecomTestApp app2,
            TelecomTestApp app3,
            boolean is1stCallHoldable,
            boolean is2ndCallHoldable,
            boolean is2ndCallOutgoing,
            int expected1stCallStateAfter2nd,
            int expected2ndCallStateAfter1st,
            int expected1stCallStateAfterEcc,
            int expected2ndCallStateAfterEcc,
            int numDisconnectDueToEcc)
            throws Exception {
        AppControlWrapper controlWrapperApp1 = null;
        AppControlWrapper controlWrapperApp2 = null;
        AppControlWrapper controlWrapperApp3 = null;
        CallSequencingValidator c1Validator = new CallSequencingValidator();
        CallSequencingValidator c2Validator = new CallSequencingValidator();
        CallSequencingValidator c3Validator = new CallSequencingValidator();

        try {
            controlWrapperApp1 = bindToApp(app1);
            controlWrapperApp2 = bindToApp(app2);
            controlWrapperApp3 = bindToApp(app3);
            // Place an outgoing managed call.
            String call1 =
                    addCallAndVerify(
                            controlWrapperApp1,
                            getRandomAttributes(app1, true /* isOutgoing */, is1stCallHoldable),
                            c1Validator);
            verifyCallIsInState(call1, STATE_DIALING);
            verifyCallStateTransition(c1Validator, call1, controlWrapperApp1, STATE_ACTIVE);

            // Place another managed call and verify the existing call is either held or active (in
            // the case of a dialing/ringing call).
            String call2 =
                    addCallAndVerify(
                            controlWrapperApp2,
                            getRandomAttributes(app2, is2ndCallOutgoing, is2ndCallHoldable),
                            c2Validator);
            if (expected1stCallStateAfter2nd != STATE_ACTIVE) {
                verifyCallStateTransition(
                        c1Validator, call1, controlWrapperApp1, expected1stCallStateAfter2nd);
            }
            verifyCallStateTransition(
                    c2Validator, call2, controlWrapperApp2, expected2ndCallStateAfter1st);

            // Place emergency call and verify the 1st and 2nd calls are either held/disconnected
            // depending on the condition (Refer to the expected behavior from the test parameters
            // above). The emergency call should go through the ManagedConnectionServiceApp phone
            // account.
            String emergencyCall =
                    addEmergencyCallAndVerify(
                            controlWrapperApp3, c3Validator, numDisconnectDueToEcc);
            if (expected1stCallStateAfterEcc != expected1stCallStateAfter2nd) {
                verifyCallStateTransition(
                        c1Validator, call1, controlWrapperApp1, expected1stCallStateAfterEcc);
            }
            if (expected2ndCallStateAfterEcc != expected2ndCallStateAfter1st) {
                verifyCallStateTransition(
                        c2Validator, call2, controlWrapperApp2, expected2ndCallStateAfterEcc);
            }
            verifyCallIsInState(emergencyCall, STATE_DIALING);
            setCallStateAndVerify(controlWrapperApp3, emergencyCall, STATE_ACTIVE);

            // Clean up calls
            if (expected1stCallStateAfterEcc != STATE_DISCONNECTED) {
                setCallStateAndVerify(controlWrapperApp1, call1, STATE_DISCONNECTED);
            }
            if (expected2ndCallStateAfterEcc != STATE_DISCONNECTED) {
                setCallStateAndVerify(controlWrapperApp2, call2, STATE_DISCONNECTED);
            }
            setCallStateAndVerify(controlWrapperApp3, emergencyCall, STATE_DISCONNECTED);
        } finally {
            List<AppControlWrapper> controls = new ArrayList<>();
            controls.add(controlWrapperApp1);
            controls.add(controlWrapperApp2);
            controls.add(controlWrapperApp3);
            tearDownApps(controls);
        }
    }

    private void verifyCallStateTransition(
            CallSequencingValidator validator,
            String call,
            AppControlWrapper app,
            int expectedCallState)
            throws Exception {
        if (expectedCallState == STATE_ACTIVE) {
            verifyCallIsInState(call, STATE_DIALING);
            setCallStateAndVerify(app, call, STATE_ACTIVE);
            return;
        } else if (expectedCallState == STATE_RINGING || expectedCallState == STATE_DIALING) {
            verifyCallIsInState(call, expectedCallState);
            return;
        }
        int transitionOp = -1;
        String opString = "";
        if (expectedCallState == STATE_DISCONNECTED) {
            transitionOp = CallStateTransitionOperation.OPERATION_DISCONNECT;
            opString = "DISCONNECT";
        } else if (expectedCallState == STATE_HOLDING) {
            transitionOp = CallStateTransitionOperation.OPERATION_HOLD;
            opString = "HOLD";
        }
        CallStateTransitionOperation op = validator.completePendingOperationOrTimeout(transitionOp);
        assertNotNull(opString + " operation never received for call " + call, op);
        verifyCallIsInState(call, expectedCallState);
    }
}
