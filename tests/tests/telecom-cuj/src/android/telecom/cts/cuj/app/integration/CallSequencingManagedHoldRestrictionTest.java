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
import static android.telecom.cts.apps.TelecomTestApp.ManagedConnectionServiceApp;
import static android.telecom.cts.apps.TelecomTestApp.ManagedConnectionServiceAppClone;

import android.platform.test.annotations.RequiresFlagsEnabled;
import android.telecom.Call;
import android.telecom.VideoProfile;
import android.telecom.cts.apps.AppControlWrapper;
import android.telecom.cts.apps.TelecomTestApp;
import android.telecom.cts.cuj.BaseAppVerifier;
import android.util.Log;

import com.android.server.telecom.flags.Flags;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.HashMap;
import java.util.Map;

/**
 * Tests related to placing calls on Managed ConnectionServices that may not be able to hold and
 * verifying the platform handles these cases properly.
 */
@RunWith(JUnit4.class)
public class CallSequencingManagedHoldRestrictionTest extends BaseAppVerifier {
    private static final String TAG = "CallSeqMHRTest";
    private static final TelecomTestApp MANAGED_0 = ManagedConnectionServiceApp;
    private static final TelecomTestApp MANAGED_1 = ManagedConnectionServiceAppClone;

    /**
     * Given there is an ACTIVE call on managed acct 0 that does NOT support hold, and a RINGING
     * call on managed acct 0, verify that answering RINGING call results in the ConnectionService
     * determining how the ANSWER command is handled. In this case, the ConnectionService holds the
     * ACTIVE call and moves the RINGING call to ACTIVE.
     */
    @Test
    @RequiresFlagsEnabled({Flags.FLAG_ENABLE_CALL_SEQUENCING})
    public void testActive0_Answer0() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        HashMap<Integer, AppControlWrapper> apps = new HashMap<>(2);
        try {
            // initial state
            String call1 = createActiveCall(apps, MANAGED_0);
            // transition event
            String call2 = createRingingCall(apps, MANAGED_0);
            // NOTE: It is the ManagedConnectionService that sets call1 to the HOLD state for calls
            // with the same PhoneAccount.
            answerViaInCallServiceAndVerify(call2, VideoProfile.STATE_AUDIO_ONLY);
            // verify final state
            verifyCallIsInState(call1, STATE_HOLDING);
            verifyCallIsInState(call2, STATE_ACTIVE);
            // clean up
            disconnectCall(apps, MANAGED_0, call1);
            disconnectCall(apps, MANAGED_0, call2);
            waitUntilExpectedCallCount(0);
        } finally {
            tearDownApps(apps.values().stream().toList());
        }
    }

    /**
     * Given there is an ACTIVE call on managed acct 0 that does NOT support hold, and a RINGING
     * call on managed acct 1, verify that ANSWERING the RINGING call results in the ACTIVE call on
     * acct 0 to be DISCONNECTED.
     */
    @Test
    @RequiresFlagsEnabled({Flags.FLAG_ENABLE_CALL_SEQUENCING})
    public void testActive0_Answer1() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        HashMap<Integer, AppControlWrapper> apps = new HashMap<>(2);
        try {
            // initial state
            createActiveCall(apps, MANAGED_0);
            // transition event
            String call2 = createRingingCall(apps, MANAGED_1);
            answerViaInCallServiceAndVerify(call2, VideoProfile.STATE_AUDIO_ONLY);
            // verify final state
            verifyCallIsInState(call2, STATE_ACTIVE);
            // first call has been disconnected
            waitUntilExpectedCallCount(1);
            // clean up
            disconnectCall(apps, MANAGED_1, call2);
            waitUntilExpectedCallCount(0);
        } finally {
            tearDownApps(apps.values().stream().toList());
        }
    }

    /**
     * Given there is an ACTIVE call on managed acct 0 that does NOT support hold, place a new
     * outgoing DIALING call on managed acct 0 and ensure that the ConnectionService associated with
     * managed acct 0 handles the request to DIAL a new call. In this case, the ConnectionService
     * moves the ACTIVE call to HOLDING before DIALING the new call.
     */
    @Test
    @RequiresFlagsEnabled({Flags.FLAG_ENABLE_CALL_SEQUENCING})
    public void testActive0_Place0() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        HashMap<Integer, AppControlWrapper> apps = new HashMap<>(2);
        try {
            // initial state
            String call1 = createActiveCall(apps, MANAGED_0);
            // transition event
            String call2 = createDialingCall(apps, MANAGED_0);
            // verify final state
            // NOTE: Telecom assumes that the ConnectionService for MANAGED_0 will handle holding
            // call1 in order to dial call2
            verifyCallIsInState(call1, STATE_HOLDING);
            verifyCallIsInState(call2, STATE_DIALING);
            // clean up
            disconnectCall(apps, MANAGED_0, call1);
            disconnectCall(apps, MANAGED_0, call2);
            waitUntilExpectedCallCount(0);
        } finally {
            tearDownApps(apps.values().stream().toList());
        }
    }

    /**
     * Given there is an ACTIVE call on managed acct 0 that does NOT support hold, place a new
     * outgoing DIALING call on managed acct 1 and ensure that the call on managed acct 1 fails to
     * be placed by Telecom.
     */
    @Test
    @RequiresFlagsEnabled({Flags.FLAG_ENABLE_CALL_SEQUENCING})
    public void testActive0_Place1() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        HashMap<Integer, AppControlWrapper> apps = new HashMap<>(2);
        try {
            // initial state
            String call1 = createActiveCall(apps, MANAGED_0);
            // transition event
            verifyDialingCallFailed(apps, MANAGED_1);
            // verify final state
            verifyCallIsInState(call1, STATE_ACTIVE);
            // verify dialing call failed to connect
            waitUntilExpectedCallCount(1);
            // clean up
            disconnectCall(apps, MANAGED_0, call1);
            waitUntilExpectedCallCount(0);
        } finally {
            tearDownApps(apps.values().stream().toList());
        }
    }

    /**
     * Given there is a HOLDING and ACTIVE call on managed acct 0, ensure that when a new RINGING
     * call on managed acct 0 is answered, the associated ConnectionService handles how the ANSWER
     * request is managed. In this implementation of the ConnectionService, the ACTIVE call is moved
     * to HOLDING and the RINGING call moves to ACTIVE.
     */
    @Test
    @RequiresFlagsEnabled({Flags.FLAG_ENABLE_CALL_SEQUENCING})
    public void testHeld0Active0_Answer0() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        HashMap<Integer, AppControlWrapper> apps = new HashMap<>(2);
        try {
            // initial state
            String call1 = createHeldCall(apps, MANAGED_0);
            String call2 = createActiveCall(apps, MANAGED_0);
            // transition event
            String call3 = createRingingCall(apps, MANAGED_0);
            // NOTE call 2 is held by the ManagedConnectionService because it is up to the
            // ConnectionService to manage multiple calls using the same PhoneAccount.
            answerViaInCallServiceAndVerify(call3, VideoProfile.STATE_AUDIO_ONLY);
            // verify final state
            verifyCallIsInState(call2, STATE_HOLDING);
            verifyCallIsInState(call3, STATE_ACTIVE);
            // NOTE: the original held call still exists in this test, however in telephony's case,
            // the call would have been disconnected when call3 was answered to make room.
            // clean up
            disconnectCall(apps, MANAGED_0, call1);
            disconnectCall(apps, MANAGED_0, call2);
            disconnectCall(apps, MANAGED_0, call3);
            waitUntilExpectedCallCount(0);
        } finally {
            tearDownApps(apps.values().stream().toList());
        }
    }

    /**
     * Given there is a HOLDING and ACTIVE call on managed acct 0, ensure that when a new DIALING
     * call on managed acct 0 is placed, Telecom fails to place the call.
     */
    @Test
    @RequiresFlagsEnabled({Flags.FLAG_ENABLE_CALL_SEQUENCING})
    public void testHeld0Active0_Place0() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        HashMap<Integer, AppControlWrapper> apps = new HashMap<>(2);
        try {
            // initial state
            String call1 = createHeldCall(apps, MANAGED_0);
            String call2 = createActiveCall(apps, MANAGED_0);
            // transition event
            verifyDialingCallFailed(apps, MANAGED_0);
            // verify final state
            verifyCallIsInState(call1, STATE_HOLDING);
            verifyCallIsInState(call2, STATE_ACTIVE);
            waitUntilExpectedCallCount(2);
            // clean up
            disconnectCall(apps, MANAGED_0, call1);
            disconnectCall(apps, MANAGED_0, call2);
            waitUntilExpectedCallCount(0);
        } finally {
            tearDownApps(apps.values().stream().toList());
        }
    }

    /**
     * Given there is a HOLDING and ACTIVE call on managed acct 0, ensure that when a new DIALING
     * call on managed acct 1 is placed, Telecom fails to place the call.
     */
    @Test
    @RequiresFlagsEnabled({Flags.FLAG_ENABLE_CALL_SEQUENCING})
    public void testHeld0Active0_Place1() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        HashMap<Integer, AppControlWrapper> apps = new HashMap<>(2);
        try {
            // initial state
            String call1 = createHeldCall(apps, MANAGED_0);
            String call2 = createActiveCall(apps, MANAGED_0);
            // transition event
            verifyDialingCallFailed(apps, MANAGED_1);
            // verify final state
            verifyCallIsInState(call1, STATE_HOLDING);
            verifyCallIsInState(call2, STATE_ACTIVE);
            waitUntilExpectedCallCount(2);
            // clean up
            disconnectCall(apps, MANAGED_0, call1);
            disconnectCall(apps, MANAGED_0, call2);
            waitUntilExpectedCallCount(0);
        } finally {
            tearDownApps(apps.values().stream().toList());
        }
    }

    /**
     * Given there is a HOLDING and ACTIVE non-holdable call on managed acct 0, ensure that when a
     * new RINGING call on managed acct 1 is placed, Telecom disconnects the HOLDING + ACTIVE call
     * before answering the RINGING call.
     */
    @Test
    @RequiresFlagsEnabled({Flags.FLAG_ENABLE_CALL_SEQUENCING})
    public void testHeld0Active0_Answer1() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        HashMap<Integer, AppControlWrapper> apps = new HashMap<>(2);
        try {
            // initial state
            String call1 = createHeldCall(apps, MANAGED_0);
            String call2 = createActiveCall(apps, MANAGED_0);
            // Create ringing call on other sub and answer
            String call3 = createRingingCall(apps, MANAGED_1);
            answerViaInCallServiceAndVerify(call3, VideoProfile.STATE_AUDIO_ONLY);
            // verify final state
            verifyCallIsInState(call1, STATE_DISCONNECTED);
            verifyCallIsInState(call2, STATE_DISCONNECTED);
            verifyCallIsInState(call3, STATE_ACTIVE);
            waitUntilExpectedCallCount(1);
            // clean up
            disconnectCall(apps, MANAGED_1, call3);
            waitUntilExpectedCallCount(0);
        } finally {
            tearDownApps(apps.values().stream().toList());
        }
    }

    /**
     * Given there is a HOLDING call on managed acct 0 and ACTIVE non-holdable call on managed acct
     * 1, ensure that when a new RINGING call on managed acct 1 is placed, Telecom disconnects the
     * HOLDING call and the ACTIVE call is held before answering the RINGING call.
     */
    @Test
    @RequiresFlagsEnabled({Flags.FLAG_ENABLE_CALL_SEQUENCING})
    public void testHeld0Active1_Answer1() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        HashMap<Integer, AppControlWrapper> apps = new HashMap<>(2);
        try {
            // initial state
            String call1 = createHeldCall(apps, MANAGED_0);
            String call2 = createActiveCall(apps, MANAGED_1);
            // Create ringing call on other sub and answer
            String call3 = createRingingCall(apps, MANAGED_1);
            answerViaInCallServiceAndVerify(call3, VideoProfile.STATE_AUDIO_ONLY);
            // verify final state
            verifyCallIsInState(call1, STATE_DISCONNECTED);
            verifyCallIsInState(call2, STATE_HOLDING);
            verifyCallIsInState(call3, STATE_ACTIVE);
            waitUntilExpectedCallCount(2);
            // clean up
            disconnectCall(apps, MANAGED_1, call2);
            disconnectCall(apps, MANAGED_1, call3);
            waitUntilExpectedCallCount(0);
        } finally {
            tearDownApps(apps.values().stream().toList());
        }
    }

    private String createHeldCall(Map<Integer, AppControlWrapper> apps, TelecomTestApp app)
            throws Exception {
        AppControlWrapper wrapper = createOrGetAppWrapper(apps, app);
        String call = addOutgoingCallAndVerify(wrapper, false /*isHoldable*/);
        Log.i(TAG, "createHeldCall: created held call " + call + " on app " + app);
        setCallStateAndVerify(wrapper, call, Call.STATE_HOLDING);
        return call;
    }

    private String createActiveCall(Map<Integer, AppControlWrapper> apps, TelecomTestApp app)
            throws Exception {
        AppControlWrapper wrapper = createOrGetAppWrapper(apps, app);
        String call = addOutgoingCallAndVerify(wrapper, false /*isHoldable*/);
        Log.i(TAG, "createActiveCall: created active call " + call + " on app " + app);
        setCallStateAndVerify(wrapper, call, Call.STATE_ACTIVE);
        return call;
    }

    private String createRingingCall(Map<Integer, AppControlWrapper> apps, TelecomTestApp app)
            throws Exception {
        AppControlWrapper wrapper = createOrGetAppWrapper(apps, app);
        String call = addIncomingCallAndVerify(wrapper);
        Log.i(TAG, "createRingingCall: created ringing call " + call + " on app " + app);
        verifyCallIsInState(call, STATE_RINGING);
        return call;
    }

    private String createDialingCall(Map<Integer, AppControlWrapper> apps, TelecomTestApp app)
            throws Exception {
        AppControlWrapper wrapper = createOrGetAppWrapper(apps, app);
        String call = addOutgoingCallAndVerify(wrapper);
        Log.i(TAG, "createDialingCall: created dialing call " + call + " on app " + app);
        verifyCallIsInState(call, STATE_DIALING);
        return call;
    }

    private void disconnectCall(
            Map<Integer, AppControlWrapper> apps, TelecomTestApp app, String call)
            throws Exception {
        AppControlWrapper wrapper = createOrGetAppWrapper(apps, app);
        setCallStateAndVerify(wrapper, call, STATE_DISCONNECTED);
    }

    private void verifyDialingCallFailed(Map<Integer, AppControlWrapper> apps, TelecomTestApp app)
            throws Exception {
        AppControlWrapper wrapper = createOrGetAppWrapper(apps, app);
        Log.i(TAG, "verifyDialingCallFailed: creating failed call on app " + app);
        addOutgoingCallAndVerifyFailure(wrapper);
    }

    private AppControlWrapper createOrGetAppWrapper(
            Map<Integer, AppControlWrapper> wrappers, TelecomTestApp app) throws Exception {
        AppControlWrapper wrapper = wrappers.get(app.toInteger());
        if (wrapper == null) {
            wrapper = bindToApp(app);
            wrappers.put(app.toInteger(), wrapper);
        }
        return wrapper;
    }
}
