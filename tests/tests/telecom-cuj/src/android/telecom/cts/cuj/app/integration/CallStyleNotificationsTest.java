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

import static android.os.SystemClock.sleep;
import static android.telecom.Call.STATE_ACTIVE;
import static android.telecom.Call.STATE_DISCONNECTED;
import static android.telecom.cts.apps.TelecomTestApp.ConnectionServiceVoipAppMain;
import static android.telecom.cts.apps.TelecomTestApp.TransactionalVoipAppMain;
import static android.telecom.cts.apps.WaitUntil.waitUntilConditionIsTrueOrTimeout;

import android.app.Notification;
import android.app.NotificationManager;
import android.os.Bundle;
import android.os.OutcomeReceiver;
import android.os.RemoteException;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.telecom.Call;
import android.telecom.CallAttributes;
import android.telecom.CallControlCallback;
import android.telecom.CallEventCallback;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;
import android.telecom.cts.apps.AppControlWrapper;
import android.telecom.cts.apps.Condition;
import android.telecom.cts.cuj.BaseAppVerifier;

import com.android.server.telecom.flags.Flags;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.Executor;

/**
 * This test class should test common calling scenarios that involve {@link
 * android.app.Notification.CallStyle} notifications.
 */
@RunWith(JUnit4.class)
public class CallStyleNotificationsTest extends BaseAppVerifier {
    private static final long FGS_REVOKED_TIMEOUT = 6000L;

    /**
     * Assert SELF-MANAGED (backed by a {@link android.telecom.ConnectionService} calling
     * applications can post a {@link android.app.Notification.CallStyle} notification.
     *
     * <h3> Test Steps: </h3>
     * <ul>
     *  1. register a {@link android.telecom.PhoneAccount} with
     *  {@link android.telecom.PhoneAccount#CAPABILITY_SELF_MANAGED}
     *  2. create an incoming SELF-MANAGED call that is backed by a
     *  {@link android.telecom.ConnectionService } via
     *  {@link android.telecom.TelecomManager#addNewIncomingCall(PhoneAccountHandle, Bundle)}
     *  and verify the call is added by using an
     *  {@link android.telecom.InCallService#onCallAdded(Call)}
     * <p>
     *  3. post a {@link android.app.Notification.CallStyle} via
     *  {@link android.app.NotificationManager#notify(int, Notification)} and verify using
     *  {@link NotificationManager#getActiveNotifications()}
     * <p>
     *  4. set the call to disconnected via {@link Connection#setDisconnected(DisconnectCause)}
     *  <p>
     *  </ul>
     */
    @Test
    public void testCallStyleNotificationBehavior_ConnectionServiceVoipAppMain() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper csVoipApp = null;
        try {
            csVoipApp = bindToApp(ConnectionServiceVoipAppMain);
            String mt = addIncomingCallAndVerify(csVoipApp);
            verifyNotificationIsPostedForCall(csVoipApp, mt);
            setCallStateAndVerify(csVoipApp, mt, STATE_DISCONNECTED);
        } finally {
            tearDownApp(csVoipApp);
        }
    }

    /**
     * Assert SELF-MANAGED calling applications that use {@link
     * android.telecom.TelecomManager#addCall(CallAttributes, Executor, OutcomeReceiver,
     * CallControlCallback, CallEventCallback) } can post a {@link
     * android.app.Notification.CallStyle} notification.
     *
     * <h3>Test Steps: </h3>
     *
     * <ul>
     *   1. register a {@link android.telecom.PhoneAccount} with {@link
     *   android.telecom.PhoneAccount#CAPABILITY_SUPPORTS_TRANSACTIONAL_OPERATIONS}
     *   <p>2. create an incoming call via {@link
     *   android.telecom.TelecomManager#addCall(CallAttributes, Executor, OutcomeReceiver,
     *   CallControlCallback, CallEventCallback)} and verify the call is added by using an {@link
     *   android.telecom.InCallService#onCallAdded(Call)}
     *   <p>3. post a {@link android.app.Notification.CallStyle} via {@link
     *   android.app.NotificationManager#notify(int, Notification)} and verify using {@link
     *   NotificationManager#getActiveNotifications()}
     *   <p>4. disconnect the call via {@link
     *   android.telecom.CallControl#disconnect(DisconnectCause, Executor, OutcomeReceiver)}
     *   <p>
     * </ul>
     */
    @RequiresFlagsEnabled(Flags.FLAG_VOIP_CALL_MONITOR_REFACTOR)
    @Test
    public void testCallStyleNotificationBehavior_TransactionalVoipAppMain() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper transactionalVoipApp = null;
        try {
            transactionalVoipApp = bindToApp(TransactionalVoipAppMain);
            PhoneAccountHandle pah =
                    transactionalVoipApp.getDefaultPhoneAccount().getAccountHandle();
            assertForegroundState(transactionalVoipApp, pah, false);
            String mt = addIncomingCallAndVerify(transactionalVoipApp);
            setCallStateAndVerify(transactionalVoipApp, mt, STATE_ACTIVE);
            verifyNotificationIsPostedForCall(transactionalVoipApp, mt);
            sleep(FGS_REVOKED_TIMEOUT);
            assertForegroundState(transactionalVoipApp, pah, true);
            setCallStateAndVerify(transactionalVoipApp, mt, STATE_DISCONNECTED);
            waitUntilFgsIsRevoked(transactionalVoipApp, pah);
        } finally {
            tearDownApp(transactionalVoipApp);
        }
    }

    /**
     * verify that when more than one call is added for a particular app, foreground service is
     * still maintained even after the first call is disconnected
     *
     * @throws Exception
     */
    @RequiresFlagsEnabled(Flags.FLAG_VOIP_CALL_MONITOR_REFACTOR)
    @Test
    public void testFGS_twoCalls_TransactionalVoipAppMain() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper transactionalVoipApp = null;
        try {
            // start state
            transactionalVoipApp = bindToApp(TransactionalVoipAppMain);
            PhoneAccountHandle pah =
                    transactionalVoipApp.getDefaultPhoneAccount().getAccountHandle();
            assertForegroundState(transactionalVoipApp, pah, false);

            // add outgoing call and assert fgs
            String mo = addOutgoingCallAndVerify(transactionalVoipApp);
            verifyNotificationIsPostedForCall(transactionalVoipApp, mo);
            setCallStateAndVerify(transactionalVoipApp, mo, STATE_ACTIVE);
            waitUntilFgsIsGained(transactionalVoipApp, pah);

            // add incoming call and assert fgs
            String mt = addIncomingCallAndVerify(transactionalVoipApp);
            verifyNotificationIsPostedForCall(transactionalVoipApp, mt);
            setCallStateAndVerify(transactionalVoipApp, mt, STATE_ACTIVE);
            assertForegroundState(transactionalVoipApp, pah, true);

            // disconnect the initial call and assert fgs is maintained
            setCallStateAndVerify(transactionalVoipApp, mo, STATE_DISCONNECTED);
            // This is the most important part of the test.  Foreground service delegation should
            // be maintained after the first call is destroyed since FGS is on a per-app basis
            // instead of a per-call basis.
            sleep(FGS_REVOKED_TIMEOUT);
            assertForegroundState(transactionalVoipApp, pah, true);

            // disconnect the last ongoing call and verify fgs is lost
            setCallStateAndVerify(transactionalVoipApp, mt, STATE_DISCONNECTED);
            waitUntilFgsIsRevoked(transactionalVoipApp, pah);
        } finally {
            tearDownApp(transactionalVoipApp);
        }
    }

    /**
     * This helper immediately checks the fgs state and does not wait like the other test helpers
     */
    public void assertForegroundState(
            AppControlWrapper app,
            PhoneAccountHandle phoneAccountHandle,
            boolean hasForegroundService) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return hasForegroundService;
                    }

                    @Override
                    public Object actual() {
                        boolean res = false;
                        try {
                            res = isForegroundServiceDelegationActive(app, phoneAccountHandle);
                        } catch (RemoteException e) {
                            // fall through
                        }
                        return res;
                    }
                });
    }

    /**
     * This helper should only be used when the initial voip call is added and the test should wait
     * until fgs is gained until continuing
     */
    public void waitUntilFgsIsGained(AppControlWrapper app, PhoneAccountHandle phoneAccountHandle) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        try {
                            return isForegroundServiceDelegationActive(app, phoneAccountHandle);
                        } catch (RemoteException e) {
                            // fall through
                        }
                        return false;
                    }
                });
    }

    /**
     * This helper waits until fgs is revoked which is done when a notification for a call is
     * removed or all the calls for an app have disconnected
     */
    public void waitUntilFgsIsRevoked(
            AppControlWrapper app, PhoneAccountHandle phoneAccountHandle) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return false;
                    }

                    @Override
                    public Object actual() {
                        try {
                            return isForegroundServiceDelegationActive(app, phoneAccountHandle);
                        } catch (RemoteException e) {
                            // fall through
                        }
                        return false;
                    }
                });
    }
}
