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
import static android.telecom.cts.apps.TelecomTestApp.ConnectionServiceVoipAppClone;
import static android.telecom.cts.apps.TelecomTestApp.ConnectionServiceVoipAppMain;
import static android.telecom.cts.apps.TelecomTestApp.ManagedConnectionServiceApp;
import static android.telecom.cts.apps.TelecomTestApp.ManagedConnectionServiceAppClone;

import android.platform.test.annotations.RequiresFlagsEnabled;
import android.telecom.CallAttributes;
import android.telecom.Connection;
import android.telecom.cts.apps.AppControlWrapper;
import android.telecom.cts.apps.TelecomTestApp;
import android.telecom.cts.cuj.BaseAppVerifier;
import android.util.Log;

import com.android.server.telecom.flags.Flags;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;

/** Basic call sequencing call tests */
@RunWith(JUnit4.class)
@RequiresFlagsEnabled({Flags.FLAG_ENABLE_CALL_SEQUENCING})
public class CallSequencingBasicCallTest extends BaseAppVerifier {
    public static final String TAG = CallSequencingBasicCallTest.class.getSimpleName();

    /**
     * Verify that for the managed case that we disallow an incoming call to be received when
     * there's already another ringing (unanswered) call.
     */
    @Test
    public void testTwoRingingCallsFail() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        verifySecondRingingCallFailsHelper(true /* testFirstCallIncoming */);
    }

    /**
     * Verify that for the managed case that we disallow an incoming call to be received when
     * there's already a dialing call.
     */
    @Test
    public void testDialingAndRingingCallsFail() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        verifySecondRingingCallFailsHelper(false /* testFirstCallIncoming */);
    }

    /**
     * Verify that when there's a managed unholdable call and an incoming managed call is received
     * on another sim that we add the EXTRA_ANSWERING_DROPS_FG_CALL extra to that call to indicate
     * that answering it will drop the unholdable call.
     */
    @Test
    public void testAnswerIncomingDropsFg_BothManaged() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        verifyAnswerIncomingDropsFg(
                ManagedConnectionServiceApp,
                ManagedConnectionServiceApp,
                true /* verifyExtraPresent */);
    }

    /**
     * Verify that when there's a self-managed unholdable call and an incoming managed call is
     * received on a sim that we add the EXTRA_ANSWERING_DROPS_FG_CALL extra to that call to
     * indicate that answering it will drop the unholdable call.
     */
    @Test
    public void testAnswerIncomingDropsFg_SelfManagedManaged() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        verifyAnswerIncomingDropsFg(
                ConnectionServiceVoipAppMain,
                ManagedConnectionServiceApp,
                true /* verifyExtraPresent */);
    }

    /**
     * Verify that when there's a self-managed unholdable call and an incoming self-managed call is
     * received on a sim that we add the EXTRA_ANSWERING_DROPS_FG_CALL extra to that call to
     * indicate that answering it will drop the unholdable call.
     */
    @Test
    public void testAnswerIncomingDropsFg_BothSelfManaged() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        verifyAnswerIncomingDropsFg(
                ConnectionServiceVoipAppMain,
                ConnectionServiceVoipAppClone,
                true /* verifyExtraPresent */);
    }

    /**
     * Verify that when there's a managed unholdable call and an incoming self-managed call is
     * received on a sim that we do NOT add the EXTRA_ANSWERING_DROPS_FG_CALL extra to that call to
     * indicate that answering it will drop the unholdable call.
     */
    @Test
    public void testAnswerIncomingDoesNotDropFg_ManagedSelfManaged() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        // Verify that the extra isn't included for the managed + self-managed case.
        verifyAnswerIncomingDropsFg(
                ManagedConnectionServiceApp,
                ConnectionServiceVoipAppMain,
                false /* verifyExtraPresent */);
    }

    /**
     * Verify that when two calls are swapped that if a call resume fails for the bg call, that we
     * unhold the fg call.
     */
    @Test
    public void testHandleCallResumeFailed() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper firstApp = null;
        AppControlWrapper secondApp = null;
        try {
            // Create an unholdable self-managed active call and then try receiving an incoming
            // managed call and verify that the answering drops fg call extra is present on that
            // call.
            firstApp = bindToApp(ManagedConnectionServiceApp);
            secondApp = bindToApp(ManagedConnectionServiceAppClone);
            String call1 = addOutgoingCallAndVerify(firstApp);
            verifyCallIsInState(call1, STATE_DIALING);
            // Put the first call on hold
            setCallStateAndVerify(firstApp, call1, STATE_HOLDING);
            String call2 = addOutgoingCallAndVerify(secondApp);
            verifyCallIsInState(call2, STATE_DIALING);
            // Put the second call on hold too to emulate a call resume failure.
            setCallStateAndVerify(secondApp, call2, STATE_HOLDING);
            // Send call resume failure for second call and verify that first call is unheld
            sendConnectionEvent(secondApp, call2, Connection.EVENT_CALL_RESUME_FAILED);
            verifyCallIsInState(call1, STATE_ACTIVE);
            // Clean up calls
            setCallStateAndVerify(firstApp, call1, STATE_DISCONNECTED);
            setCallStateAndVerify(secondApp, call2, STATE_DISCONNECTED);

        } finally {
            List<AppControlWrapper> controls = new ArrayList<>();
            controls.add(firstApp);
            controls.add(secondApp);
            tearDownApps(controls);
        }
    }

    private void verifyAnswerIncomingDropsFg(
            TelecomTestApp firstAppName,
            TelecomTestApp secondTestAppName,
            boolean verifyExtraPresent)
            throws Exception {
        AppControlWrapper firstApp = null;
        AppControlWrapper secondApp = null;
        try {
            // Create an unholdable self-managed active call and then try receiving an incoming
            // managed call and verify that the answering drops fg call extra is present on that
            // call.
            firstApp = bindToApp(firstAppName);
            secondApp = bindToApp(secondTestAppName);
            String activeCall = createUnholdableActiveCall(firstApp, firstAppName);
            String incomingCall = addIncomingCallAndVerify(secondApp);
            // Verify if the call extra should be present based on verifyExtraPresent
            verifyCallExtraPresent(
                    incomingCall, Connection.EXTRA_ANSWERING_DROPS_FG_CALL, verifyExtraPresent);
            // Clean up calls
            setCallStateAndVerify(firstApp, activeCall, STATE_DISCONNECTED);
            setCallStateAndVerify(secondApp, incomingCall, STATE_DISCONNECTED);

        } finally {
            List<AppControlWrapper> controls = new ArrayList<>();
            controls.add(firstApp);
            controls.add(secondApp);
            tearDownApps(controls);
        }
    }

    private String createUnholdableActiveCall(AppControlWrapper app, TelecomTestApp appName)
            throws Exception {
        String call = addOutgoingCallAndVerify(app, false /*isHoldable*/);
        Log.i(TAG, "createActiveCall: created active call " + call + " on app " + appName);
        setCallStateAndVerify(app, call, STATE_ACTIVE);
        return call;
    }

    private void verifySecondRingingCallFailsHelper(boolean testFirstCallIncoming)
            throws Exception {
        AppControlWrapper managedApp = null;
        AppControlWrapper managedAppClone = null;
        int expectedCallState = testFirstCallIncoming ? STATE_RINGING : STATE_DIALING;

        try {
            // Place either a dialing or ringing call based on testFirstCallIncoming
            managedApp = bindToApp(ManagedConnectionServiceApp);
            String call =
                    testFirstCallIncoming
                            ? addIncomingCallAndVerify(managedApp)
                            : addOutgoingCallAndVerify(managedApp);
            verifyCallIsInState(call, expectedCallState);

            // Verify that incoming call failed and we never attempted to create the connection.
            managedAppClone = bindToApp(ManagedConnectionServiceAppClone);
            CallAttributes defaultIncomingAttrs =
                    getDefaultAttributes(ManagedConnectionServiceAppClone, false);
            addFailedCallWithCreateConnectionVerify(managedAppClone, defaultIncomingAttrs);

            // Verify that the state of the first call is unchanged
            verifyCallIsInState(call, expectedCallState);
            setCallStateAndVerify(managedApp, call, STATE_DISCONNECTED);
        } finally {
            List<AppControlWrapper> controls = new ArrayList<>();
            controls.add(managedApp);
            controls.add(managedAppClone);
            tearDownApps(controls);
        }
    }
}
