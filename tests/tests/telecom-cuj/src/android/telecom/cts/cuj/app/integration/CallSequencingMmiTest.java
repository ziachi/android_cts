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
import static android.telecom.cts.apps.TelecomTestApp.ManagedConnectionServiceApp;
import static android.telecom.cts.apps.TelecomTestApp.ManagedConnectionServiceAppClone;

import android.platform.test.annotations.RequiresFlagsEnabled;
import android.telecom.CallAttributes;
import android.telecom.cts.apps.AppControlWrapper;
import android.telecom.cts.cuj.BaseAppVerifier;

import com.android.server.telecom.flags.Flags;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;

@RunWith(JUnit4.class)
public class CallSequencingMmiTest extends BaseAppVerifier {

    @Test
    @RequiresFlagsEnabled({Flags.FLAG_ENABLE_CALL_SEQUENCING})
    public void testInCallMmiCodeBlocked() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper managedApp = null;
        AppControlWrapper managedAppClone = null;

        try {
            managedApp = bindToApp(ManagedConnectionServiceApp);
            String mo1 = addOutgoingCallAndVerify(managedApp);
            setCallStateAndVerify(managedApp, mo1, STATE_ACTIVE);

            managedAppClone = bindToApp(ManagedConnectionServiceAppClone);
            String mo2 = addOutgoingCallAndVerify(managedAppClone);
            setCallStateAndVerify(managedAppClone, mo2, STATE_ACTIVE);
            verifyCallIsInState(mo1, STATE_HOLDING);

            // Verify that dialing an MMI code on a phone account when there are ongoing calls on a
            // different account results in an early failure in Telecom. We should've never
            // attempted to create the connection.
            CallAttributes mmiAttributes =
                    getDefaultMmiAttributes(ManagedConnectionServiceApp, true /* inCallMmi */);
            addFailedCallWithCreateConnectionVerify(managedApp, mmiAttributes);

            // Verify that the states of the ongoing calls are unchanged.
            verifyCallIsInState(mo1, STATE_HOLDING);
            verifyCallIsInState(mo2, STATE_ACTIVE);
            setCallStateAndVerify(managedAppClone, mo2, STATE_DISCONNECTED);
            setCallStateAndVerify(managedApp, mo1, STATE_DISCONNECTED);
        } finally {
            List<AppControlWrapper> controls = new ArrayList<>();
            controls.add(managedApp);
            controls.add(managedAppClone);
            tearDownApps(controls);
        }
    }

    @Test
    @RequiresFlagsEnabled({Flags.FLAG_ENABLE_CALL_SEQUENCING})
    public void testInCallMmiCodeAllowed() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper managedApp = null;

        try {
            managedApp = bindToApp(ManagedConnectionServiceApp);
            String mo = addOutgoingCallAndVerify(managedApp);
            setCallStateAndVerify(managedApp, mo, STATE_ACTIVE);

            // Verify that the connection was created successfully when the MMI code is dialed on
            // the same phone account.
            CallAttributes mmiAttributes =
                    getDefaultMmiAttributes(ManagedConnectionServiceApp, true /* inCallMmi */);
            String mmiDial = addCallAndVerify(managedApp, mmiAttributes);

            setCallStateAndVerify(managedApp, mo, STATE_DISCONNECTED);
            setCallStateAndVerify(managedApp, mmiDial, STATE_DISCONNECTED);
        } finally {
            List<AppControlWrapper> controls = new ArrayList<>();
            controls.add(managedApp);
            tearDownApps(controls);
        }
    }

    @Test
    @RequiresFlagsEnabled({Flags.FLAG_ENABLE_CALL_SEQUENCING})
    public void testMmiCodeAllowed_SingleSim() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper managedApp = null;

        try {
            managedApp = bindToApp(ManagedConnectionServiceApp);
            String mo = addOutgoingCallAndVerify(managedApp);
            setCallStateAndVerify(managedApp, mo, STATE_ACTIVE);

            // Verify that the connection was created successfully when the MMI code is dialed on
            // the same phone account.
            CallAttributes mmiAttributes =
                    getDefaultMmiAttributes(ManagedConnectionServiceApp, false /* inCallMmi */);
            String mmiDial = addCallAndVerify(managedApp, mmiAttributes);

            setCallStateAndVerify(managedApp, mo, STATE_DISCONNECTED);
            setCallStateAndVerify(managedApp, mmiDial, STATE_DISCONNECTED);
        } finally {
            List<AppControlWrapper> controls = new ArrayList<>();
            controls.add(managedApp);
            tearDownApps(controls);
        }
    }

    @Test
    @RequiresFlagsEnabled({Flags.FLAG_ENABLE_CALL_SEQUENCING})
    public void testMmiCodeAllowed_DualSim() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper managedApp = null;
        AppControlWrapper managedAppClone = null;

        try {
            managedApp = bindToApp(ManagedConnectionServiceApp);
            String mo1 = addOutgoingCallAndVerify(managedApp);
            setCallStateAndVerify(managedApp, mo1, STATE_ACTIVE);

            managedAppClone = bindToApp(ManagedConnectionServiceAppClone);
            String mo2 = addOutgoingCallAndVerify(managedAppClone);
            setCallStateAndVerify(managedAppClone, mo2, STATE_ACTIVE);
            verifyCallIsInState(mo1, STATE_HOLDING);

            // Verify that dialing an MMI code on a phone account when there are ongoing calls on a
            // different account results in an early failure in Telecom. We should've never
            // attempted to create the connection.
            CallAttributes mmiAttributes =
                    getDefaultMmiAttributes(ManagedConnectionServiceApp, false /* inCallMmi */);
            String mmiDial = addCallAndVerify(managedApp, mmiAttributes);

            // Verify that the states of the ongoing calls are unchanged.
            verifyCallIsInState(mo1, STATE_HOLDING);
            verifyCallIsInState(mo2, STATE_ACTIVE);
            setCallStateAndVerify(managedAppClone, mo2, STATE_DISCONNECTED);
            setCallStateAndVerify(managedApp, mo1, STATE_DISCONNECTED);
            setCallStateAndVerify(managedApp, mmiDial, STATE_DISCONNECTED);
        } finally {
            List<AppControlWrapper> controls = new ArrayList<>();
            controls.add(managedApp);
            controls.add(managedAppClone);
            tearDownApps(controls);
        }
    }
}
