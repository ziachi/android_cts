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
import static android.telecom.Call.STATE_DISCONNECTED;
import static android.telecom.Call.STATE_HOLDING;
import static android.telecom.cts.apps.TelecomTestApp.ConnectionServiceVoipAppMain;
import static android.telecom.cts.apps.TelecomTestApp.ManagedConnectionServiceApp;
import static android.telecom.cts.apps.TelecomTestApp.TransactionalVoipAppMain;

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
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;

/** Basic call sequencing emergency call related test cases */
@RunWith(JUnit4.class)
@RequiresFlagsEnabled({Flags.FLAG_ENABLE_CALL_SEQUENCING})
public class CallSequencingBasicEmergencyCallTest extends BaseAppVerifier {

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

    /**
     * Adds a managed normal (holdable) call and then tries to place an emergency call. The normal
     * call should be held before the ECC is placed (which should go active).
     */
    @Test
    public void testAddEmergencyCallWithManagedCall() throws Exception {
        verifyAddEccWithSingleCall(
                ManagedConnectionServiceApp, STATE_HOLDING, true /* isNormalCallHoldable */);
    }

    /**
     * Adds a managed normal (non-holdable) call and then tries to place an emergency call. The
     * normal call should be held before the ECC is placed (which should go active). This simulates
     * the VZW case where we just "swap" the calls.
     */
    @Test
    public void testAddEmergencyCallWithManagedNonHoldableCall() throws Exception {
        verifyAddEccWithSingleCall(
                ManagedConnectionServiceApp, STATE_HOLDING, false /* isNormalCallHoldable */);
    }

    /**
     * Adds a transactional call and then tries to place an emergency call. The transactional call
     * should be disconnected before the ECC is placed (which should go active).
     */
    @Test
    public void testAddEmergencyCallWithTransactionalCall() throws Exception {
        verifyAddEccWithSingleCall(
                TransactionalVoipAppMain, STATE_DISCONNECTED, true /* isNormalCallHoldable */);
    }

    /**
     * Adds a self-managed call and then tries to place an emergency call. The self-managed call
     * should be disconnected before the ECC is placed (which should go active).
     */
    @Test
    public void testAddEmergencyCallWithSelfManagedCall() throws Exception {
        verifyAddEccWithSingleCall(
                ConnectionServiceVoipAppMain, STATE_DISCONNECTED, true /* isNormalCallHoldable */);
    }

    private void verifyAddEccWithSingleCall(
            TelecomTestApp app1, int expectedNormalCallState, boolean isNormalCallHoldable)
            throws Exception {
        AppControlWrapper controlWrapperApp1 = null;
        AppControlWrapper controlWrapperApp2 = null;
        CallSequencingValidator c1Validator = new CallSequencingValidator();
        CallSequencingValidator c2Validator = new CallSequencingValidator();

        try {
            // Place a normal call.
            controlWrapperApp1 = bindToApp(app1);
            // We will always place the ECC on the managed phone account.
            controlWrapperApp2 = bindToApp(ManagedConnectionServiceApp);
            String normalCall =
                    addCallAndVerify(
                            controlWrapperApp1,
                            getRandomAttributes(
                                    app1,
                                    true /* isOutgoing */,
                                    isNormalCallHoldable /* isHoldable */),
                            c1Validator);
            setCallStateAndVerify(controlWrapperApp1, normalCall, STATE_ACTIVE);

            // Place emergency call and verify existing call is either put on hold or disconnected
            // (for transactional, self-managed, non-holdable calls) when the emergency call is
            // set active.
            int numDisconnectDueToEcc = expectedNormalCallState == STATE_DISCONNECTED ? 1 : 0;
            String emergencyCall =
                    addEmergencyCallAndVerify(
                            controlWrapperApp2, c2Validator, numDisconnectDueToEcc);
            int transitionOpType =
                    expectedNormalCallState == STATE_DISCONNECTED
                            ? CallStateTransitionOperation.OPERATION_DISCONNECT
                            : CallStateTransitionOperation.OPERATION_HOLD;
            String opString = expectedNormalCallState == STATE_DISCONNECTED ? "DISCONNECT" : "HOLD";
            CallStateTransitionOperation op =
                    c1Validator.completePendingOperationOrTimeout(transitionOpType);
            assertNotNull(opString + " operation never received for first call " + normalCall, op);
            verifyCallIsInState(normalCall, expectedNormalCallState);
            setCallStateAndVerify(controlWrapperApp2, emergencyCall, STATE_ACTIVE);

            // Clean up calls
            if (expectedNormalCallState != STATE_DISCONNECTED) {
                setCallStateAndVerify(controlWrapperApp1, normalCall, STATE_DISCONNECTED);
            }
            setCallStateAndVerify(controlWrapperApp2, emergencyCall, STATE_DISCONNECTED);
        } finally {
            List<AppControlWrapper> controls = new ArrayList<>();
            controls.add(controlWrapperApp1);
            controls.add(controlWrapperApp2);
            tearDownApps(controls);
        }
    }
}
