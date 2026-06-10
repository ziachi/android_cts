/*
 * Copyright (C) 2023 The Android Open Source Project
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
package android.telecom.cts.apps.managedapp;

import android.content.Intent;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.cts.apps.HoldableTracker;
import android.telecom.cts.apps.ManagedConnection;
import android.util.Log;

import java.util.concurrent.CountDownLatch;

public class ManagedConnectionService extends ConnectionService {
    private static final String LOG_TAG = "ManagedConnectionService";
    public static ManagedConnectionService sConnectionService;
    public static ManagedConnection sLastConnection = null;
    public static ConnectionRequest sLastFailedRequest = null;
    public static CountDownLatch sCreateOutgoingConnectionLatch = new CountDownLatch(1);
    public static CountDownLatch sCreateIncomingConnectionLatch = new CountDownLatch(1);

    @Override
    public void onBindClient(Intent intent) {
        Log.i(LOG_TAG, String.format("onBindClient: intent=[%s]", intent));
        sConnectionService = this;
        sLastConnection = null;
        // sLastFailedRequest needs to be cleaned up by the control interface for verification after
        // unbind in some cases.
        sCreateOutgoingConnectionLatch = new CountDownLatch(1);
        sCreateIncomingConnectionLatch = new CountDownLatch(1);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(LOG_TAG, String.format("onUnbind: intent=[%s]", intent));
        sConnectionService = null;
        sLastConnection = null;
        // sLastFailedRequest needs to be cleaned up by the control interface for verification after
        // unbind in some cases.
        sCreateOutgoingConnectionLatch = new CountDownLatch(1);
        sCreateIncomingConnectionLatch = new CountDownLatch(1);
        return super.onUnbind(intent);
    }

    @Override
    public Connection onCreateOutgoingConnection(PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        Log.i(LOG_TAG, String.format("onCreateOutgoingConnection: account=[%s], request=[%s]",
                connectionManagerPhoneAccount, request));
        sCreateOutgoingConnectionLatch.countDown();
        // For calls from the same managed connection service, operations are handled at the
        // connection service level.
        if (getAllConnections().stream()
                .filter(c-> c.getState() != Connection.STATE_DISCONNECTED).count() > 1) {
            return Connection.createFailedConnection(new DisconnectCause(DisconnectCause.ERROR));
        }
        // Special case: for multiple call scenarios on the same PA, Telecom assumes the
        // ConnectionService will handle holding the existing call.
        for (Connection c : getAllConnections()) {
            if (c.getState() == Connection.STATE_ACTIVE) {
                c.onHold();
            }
        }
        return createConnection(request, true);
    }

    @Override
    public void onCreateOutgoingConnectionFailed(PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        Log.i(LOG_TAG, String.format("onCreateOutgoingConnectionFailed: account=[%s], request=[%s]",
                connectionManagerPhoneAccount, request));
        sLastFailedRequest = request;
        super.onCreateOutgoingConnectionFailed(connectionManagerPhoneAccount, request);
    }

    @Override
    public Connection onCreateIncomingConnection(PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        Log.i(LOG_TAG, String.format("onCreateIncomingConnection: account=[%s], request=[%s]",
                connectionManagerPhoneAccount, request));
        sCreateIncomingConnectionLatch.countDown();
        return createConnection(request, false);
    }

    @Override
    public void onCreateIncomingConnectionFailed(PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        Log.i(LOG_TAG, String.format("onCreateIncomingConnectionFailed: account=[%s], request=[%s]",
                connectionManagerPhoneAccount, request));
        sLastFailedRequest = request;
        super.onCreateIncomingConnectionFailed(connectionManagerPhoneAccount, request);
    }

    private Connection createConnection(ConnectionRequest request, boolean isOutgoing) {
        ManagedConnection connection = new ManagedConnection(this);
        Log.i("ManagedConnectionService", "Creating managed connection");
        sLastConnection = connection;

        if (isOutgoing) {
            connection.setDialing();
        } else {
            connection.setRinging();
        }

        connection.setAddress(request.getAddress(), TelecomManager.PRESENTATION_ALLOWED);
        connection.setConnectionCapabilities(
                Connection.CAPABILITY_HOLD | Connection.CAPABILITY_SUPPORT_HOLD
        );
        connection.setAudioModeIsVoip(false);
        HoldableTracker.addHoldable(connection);
        return connection;
    }
}
