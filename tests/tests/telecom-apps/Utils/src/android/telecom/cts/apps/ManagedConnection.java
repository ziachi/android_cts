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

package android.telecom.cts.apps;

import android.content.Context;
import android.telecom.CallEndpoint;
import android.telecom.Connection;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.VideoProfile;
import android.util.Log;

import java.util.List;
import java.util.function.Consumer;

public class ManagedConnection extends Connection {
    private static final String TAG = ManagedConnection.class.getSimpleName();
    private String mId = "";
    private boolean mIsMuted = false;
    private CallEndpoint mCallEndpoint = null;
    private List<CallEndpoint> mCallEndpoints = null;
    private final ConnectionService mConnectionService;

    // Delegates the completion of call state transition operations to potentially another entity
    // to control the completion.
    private Consumer<CallStateTransitionOperation> mOperationConsumer;

    public ManagedConnection(ConnectionService service) {
        mConnectionService = service;
    }

    public boolean isMuted() {
        return mIsMuted;
    }

    public List<CallEndpoint> getCallEndpoints() {
        return mCallEndpoints;
    }

    public CallEndpoint getCurrentCallEndpointFromCallback() {
        return mCallEndpoint;
    }

    public void setId(String id) {
        mId = id;
    }

    public void setCallToDialing() {
        this.setDialing();
    }

    public void setCallToRinging() {
        this.setRinging();
    }

    public void setCallToActive() {
        this.setActive();
    }

    public void setCallToInactive() {
        this.setOnHold();
    }

    public void setCallToDisconnected(Context context) {
        setCallToDisconnected(context, new DisconnectCause(DisconnectCause.LOCAL));
    }

    public void setCallToDisconnected(Context context, DisconnectCause cause) {
        this.setDisconnected(cause);
        processDestroy();
    }

    /**
     * Delegate operation completion to the test process so that it can control completion of the
     * request.
     */
    public void setOperationConsumer(Consumer<CallStateTransitionOperation> consumer) {
        mOperationConsumer = consumer;
    }

    @Override
    public void onStateChanged(int callState) {

    }

    @Override
    public void onCallEndpointChanged(CallEndpoint callEndpoint) {
        mCallEndpoint = callEndpoint;
    }


    @Override
    public void onAvailableCallEndpointsChanged(List<CallEndpoint> endpoints) {
        mCallEndpoints = endpoints;
    }

    @Override
    public void onHold() {
        setOnHold();
        super.onHold();
        if (mOperationConsumer != null) {
            mOperationConsumer.accept(new CallStateTransitionOperation(
                    CallStateTransitionOperation.OPERATION_HOLD, System.currentTimeMillis()));
        }
    }

    @Override
    public void onUnhold() {
        setActive();
        super.onUnhold();
        if (mOperationConsumer != null) {
            mOperationConsumer.accept(new CallStateTransitionOperation(
                    CallStateTransitionOperation.OPERATION_UNHOLD, System.currentTimeMillis()));
        }
    }

    @Override
    public void onAnswer(int videoState) {
        setVideoState(videoState);
        // Special case - we expect the ConnectionService to handle holding an existing call if the
        // new call is on the same PhoneAccount
        for (Connection c : mConnectionService.getAllConnections()) {
            if (c.getState() == STATE_ACTIVE) {
                c.onHold();
            }
        }

        setActive();
        super.onAnswer(videoState);
        if (mOperationConsumer != null) {
            mOperationConsumer.accept(new CallStateTransitionOperation(
                    CallStateTransitionOperation.OPERATION_ANSWER, System.currentTimeMillis()));
        }
    }

    @Override
    public void onAnswer() {
        onAnswer(VideoProfile.STATE_AUDIO_ONLY);
    }

    @Override
    public void onDisconnect() {
        this.setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
        processDestroy();
        super.onDisconnect();
        if (mOperationConsumer != null) {
            mOperationConsumer.accept(new CallStateTransitionOperation(
                    CallStateTransitionOperation.OPERATION_DISCONNECT, System.currentTimeMillis()));
        }
    }

    @Override
    public void onReject() {
        this.setDisconnected(new DisconnectCause(DisconnectCause.REJECTED));
        processDestroy();
        super.onReject();
        if (mOperationConsumer != null) {
            mOperationConsumer.accept(
                    new CallStateTransitionOperation(
                            CallStateTransitionOperation.OPERATION_DISCONNECT,
                            System.currentTimeMillis()));
        }
    }

    @Override
    public void onReject(int rejectReason) {
        this.setDisconnected(
                new DisconnectCause(DisconnectCause.REJECTED, Integer.toString(rejectReason)));
        processDestroy();
        super.onReject(rejectReason);
        if (mOperationConsumer != null) {
            mOperationConsumer.accept(
                    new CallStateTransitionOperation(
                            CallStateTransitionOperation.OPERATION_DISCONNECT,
                            System.currentTimeMillis()));
        }
    }

    @Override
    public void onReject(String reason) {
        this.setDisconnected(new DisconnectCause(DisconnectCause.REJECTED, reason));
        processDestroy();
        super.onReject();
        if (mOperationConsumer != null) {
            mOperationConsumer.accept(
                    new CallStateTransitionOperation(
                            CallStateTransitionOperation.OPERATION_DISCONNECT,
                            System.currentTimeMillis()));
        }
    }

    @Override
    public void onMuteStateChanged(boolean isMuted) {
        super.onMuteStateChanged(isMuted);
        mIsMuted = isMuted;
    }

    /**
     * Helper that removes the Connection.CAPABILITY_HOLD && Connection.CAPABILITY_SUPPORT_HOLD
     * capabilities from a given Connection.
     */
    public void clearHoldCapabilities() {
        Log.i(TAG, String.format("Current capabilities as list=[%s]",
                Connection.capabilitiesToString(this.getConnectionCapabilities())));
        int mask = (1 << 31) - 1;
        int holdCapabilities = Connection.CAPABILITY_HOLD | Connection.CAPABILITY_SUPPORT_HOLD;
        int clearHold = (~holdCapabilities) & mask;
        int finalCaps = this.getConnectionCapabilities() & clearHold;
        this.setConnectionCapabilities(finalCaps);
        Log.i(TAG, String.format("Final capabilities as list=[%s]",
                Connection.capabilitiesToString(this.getConnectionCapabilities())));
    }

    private void processDestroy() {
        HoldableTracker.removeHoldable(this);
        this.destroy();
    }
}
