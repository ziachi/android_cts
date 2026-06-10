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
import static android.telecom.Call.STATE_CONNECTING;
import static android.telecom.Call.STATE_DIALING;
import static android.telecom.Call.STATE_DISCONNECTED;
import static android.telecom.Call.STATE_HOLDING;
import static android.telecom.Call.STATE_RINGING;
import static android.telecom.cts.apps.ShellCommandExecutor.COMMAND_WAIT_FOR_AUDIO_ACTIVE;
import static android.telecom.cts.apps.ShellCommandExecutor.COMMAND_WAIT_FOR_AUDIO_OPS_COMPLETE;
import static android.telecom.cts.apps.ShellCommandExecutor.executeShellCommand;
import static android.telecom.cts.apps.TelecomTestApp.ConnectionServiceVoipAppClone;
import static android.telecom.cts.apps.TelecomTestApp.ConnectionServiceVoipAppMain;
import static android.telecom.cts.apps.TelecomTestApp.ManagedConnectionServiceApp;
import static android.telecom.cts.apps.TelecomTestApp.TransactionalVoipAppClone;
import static android.telecom.cts.apps.TelecomTestApp.TransactionalVoipAppMain;

import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.OutcomeReceiver;
import android.telecom.CallAttributes;
import android.telecom.CallControlCallback;
import android.telecom.CallEndpoint;
import android.telecom.CallEventCallback;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;
import android.telecom.VideoProfile;
import android.telecom.cts.apps.AppControlWrapper;
import android.telecom.cts.cuj.BaseAppVerifier;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.server.telecom.flags.Flags;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;
import java.util.concurrent.Executor;

/** This test class should test common calling scenarios that involve only a single application */
@RunWith(JUnit4.class)
public class CallAudioRouteTest extends BaseAppVerifier {
    @After
    public void waitOnHandlers() throws Exception {
        executeShellCommand(
                InstrumentationRegistry.getInstrumentation(), COMMAND_WAIT_FOR_AUDIO_OPS_COMPLETE);
    }

    /**
     * Test the scenario where a new managed outgoing call is created and transitions to the ACTIVE
     * and DISCONNECTED states, while the user is playing music. We want to ensure that music
     * playback loses focus when the call starts and regains it when the call stops.
     *
     * <h3>Test Steps: </h3>
     *
     * <ol>
     *   <li>CTS test acquires audio focus for music playback
     *   <li>create a managed call that is backed by a {@link android.telecom.ConnectionService }
     *       via {@link android.telecom.TelecomManager#placeCall(Uri, Bundle)}
     *   <li>transition the call to ACTIVE via {@link Connection#setActive()}
     *   <li>confirm that audio focus is lost for music playback
     *   <li>transition the call to DISCONNECTED via {@link
     *       Connection#setDisconnected(DisconnectCause)}
     *   <li>confirm that audio focus is re-gained for music playback.
     * </ol>
     *
     * Assert the call was successfully added and transitioned to the ACTIVE state without errors
     * and that audio focus for music playback behaved as expected.
     */
    @Test
    public void testOutgoingCallWhileMusicPlaying_ManagedConnectionServiceApp() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper managedApp = null;
        try {
            managedApp = bindToApp(ManagedConnectionServiceApp);
            acquireAudioFocusForMusic();
            verifyOutgoingCallStateTransitionsWithAudioFocus(managedApp);
            waitForAndVerifyMusicFocus(true, AudioManager.AUDIOFOCUS_GAIN);
        } finally {
            releaseAudioFocusForMusic();
            tearDownApp(managedApp);
        }
    }

    /**
     * Test the scenario where a new MANAGED incoming call is created and transitions to the ACTIVE
     * and DISCONNECTED states, while the user is playing music. We want to ensure that music
     * playback loses focus when the call starts and regains it when the call stops.
     *
     * <h3>Test Steps: </h3>
     *
     * <ol>
     *   <li>CTS test acquires audio focus for music playback
     *   <li>create a managed call that is backed by a {@link android.telecom.ConnectionService }
     *       via {@link android.telecom.TelecomManager#addNewIncomingCall(PhoneAccountHandle,
     *       Bundle)}
     *   <li>transition the call to ACTIVE via {@link Connection#setActive()}
     *   <li>confirm that audio focus is lost for music playback
     *   <li>transition the call to DISCONNECTED via {@link
     *       Connection#setDisconnected(DisconnectCause)}
     *   <li>confirm that audio focus is re-gained for music playback.
     * </ol>
     *
     * Assert the call was successfully added and transitioned to the ACTIVE state without errors
     */
    @Test
    public void testIncomingCallWhileMusicPlaying_ManagedConnectionServiceApp() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper managedApp = null;
        try {
            managedApp = bindToApp(ManagedConnectionServiceApp);
            acquireAudioFocusForMusic();
            verifyIncomingCallStateTransitionsWithAudioFocus(managedApp);
            waitForAndVerifyMusicFocus(true, AudioManager.AUDIOFOCUS_GAIN);
        } finally {
            releaseAudioFocusForMusic();
            tearDownApp(managedApp);
        }
    }

    /**
     * Test the scenario where a new SELF-MANAGED outgoing call is created and transitions to the
     * ACTIVE and DISCONNECTED states, while the user is playing music. We want to ensure that music
     * playback loses focus when the call starts and regains it when the call stops.
     *
     * <h3>Test Steps: </h3>
     *
     * <ol>
     *   <li>acquire audio focus for music playback
     *   <li>create a self-managed call that is backed by a {@link android.telecom.ConnectionService
     *       } via {@link android.telecom.TelecomManager#placeCall(Uri, Bundle)}
     *   <li>transition the call to ACTIVE via {@link Connection#setActive()}
     *   <li>transition the call to DISCONNECTED via {@link
     *       Connection#setDisconnected(DisconnectCause)}
     *   <li>release audio focus for music playback
     * </ol>
     *
     * Assert the call was successfully added and transitioned to the ACTIVE state without errors
     * and that focus was lost and regained as expected.
     */
    @Test
    public void testOutgoingCallWhileMusicPlaying_ConnectionServiceVoipAppMain() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper voipCsApp = null;

        try {
            acquireAudioFocusForMusic();
            voipCsApp = bindToApp(ConnectionServiceVoipAppMain);
            verifyOutgoingCallStateTransitionsWithAudioFocus(voipCsApp);
            waitForAndVerifyMusicFocus(true, AudioManager.AUDIOFOCUS_GAIN);
        } finally {
            releaseAudioFocusForMusic();
            tearDownApp(voipCsApp);
        }
    }

    /**
     * Test the scenario where a new SELF_MANAGED incoming call is created and transitions to the
     * ACTIVE and DISCONNECTED states, while the user is playing music. We want to ensure that music
     * playback loses focus when the call starts and regains it when the call stops.
     *
     * <h3>Test Steps: </h3>
     *
     * <ol>
     *   <li>acquire audio focus for music playback
     *   <li>create a self-mgd call that is backed by a {@link android.telecom.ConnectionService}
     *       via {@link android.telecom.TelecomManager#addNewIncomingCall(PhoneAccountHandle,
     *       Bundle)}
     *   <li>transition the call to ACTIVE via {@link Connection#setActive()}
     *   <li>transition the call to DISCONNECTED via {@link
     *       Connection#setDisconnected(DisconnectCause)}
     *   <li>release audio focus for music playback.
     * </ol>
     *
     * Assert the call was successfully added and transitioned to the ACTIVE state without errors
     * and that audio focus was lost and gained as expected.
     */
    @Test
    public void testIncomingCallWhileMusicPlaying_ConnectionServiceVoipAppMain() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper voipCsApp = null;

        try {
            acquireAudioFocusForMusic();
            voipCsApp = bindToApp(ConnectionServiceVoipAppMain);
            verifyIncomingCallStateTransitionsWithAudioFocus(voipCsApp);
            waitForAndVerifyMusicFocus(true, AudioManager.AUDIOFOCUS_GAIN);

        } finally {
            releaseAudioFocusForMusic();
            tearDownApp(voipCsApp);
        }
    }

    /**
     * Test the scenario where a new SELF-MANAGED outgoing call is created and transitions to the
     * ACTIVE and DISCONNECTED states, while the user is playing music. We want to ensure that music
     * playback loses focus when the call starts and regains it when the call stops.
     *
     * <h3>Test Steps: </h3>
     *
     * <ol>
     *   <li>Acquire audio focus for music playback
     *   <li>create a VoIP call that is added via {@link
     *       android.telecom.TelecomManager#addCall(CallAttributes, Executor, OutcomeReceiver,
     *       CallControlCallback, CallEventCallback)}
     *   <li>transition the call to ACTIVE via {@link
     *       android.telecom.CallControl#setActive(Executor, OutcomeReceiver)}
     *   <li>transition the call to DISCONNECTED via {@link
     *       android.telecom.CallControl#disconnect(DisconnectCause, Executor, OutcomeReceiver)}
     *   <li>Release audio focus for music playback
     * </ol>
     *
     * Assert the call was successfully added and transitioned to the ACTIVE state without errors,
     * and that audio focus is lost and regained as expected.
     */
    @Test
    public void testOutgoingCallWhileMusicPlaying_TransactionalVoipAppMain() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper transactionalApp = null;

        try {
            acquireAudioFocusForMusic();
            transactionalApp = bindToApp(TransactionalVoipAppMain);
            verifyOutgoingCallStateTransitionsWithAudioFocus(transactionalApp);
            waitForAndVerifyMusicFocus(true, AudioManager.AUDIOFOCUS_GAIN);
        } finally {
            releaseAudioFocusForMusic();
            tearDownApp(transactionalApp);
        }
    }

    /**
     * Test the scenario where an incoming <b>AUDIO</b> call is created and transitions to the
     * ACTIVE and DISCONNECTED states.
     *
     * <h3>Test Steps: </h3>
     *
     * <ol>
     *   <li>Acquire audio focus for music playback.
     *   <li>create a VoIP call via {@link android.telecom.TelecomManager#addCall(CallAttributes,
     *       Executor, OutcomeReceiver, CallControlCallback, CallEventCallback)}
     *   <li>transition the call to ACTIVE via {@link
     *       android.telecom.CallControl#setActive(Executor, OutcomeReceiver)}
     *   <li>transition the call to DISCONNECTED via {@link
     *       android.telecom.CallControl#disconnect(DisconnectCause, Executor, OutcomeReceiver)}
     *   <li>Release audio focus for music playback
     * </ol>
     *
     * Assert the call was successfully added and transitioned to the ACTIVE state without errors,
     * and that audio focus is lost and gained as expected.
     */
    @Test
    public void testIncomingCallWhileMusicPlaying_TransactionalVoipAppMain() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper transactionalApp = null;

        try {
            acquireAudioFocusForMusic();
            transactionalApp = bindToApp(TransactionalVoipAppMain);
            verifyIncomingCallStateTransitionsWithAudioFocus(transactionalApp);
            waitForAndVerifyMusicFocus(true, AudioManager.AUDIOFOCUS_GAIN);
        } finally {
            releaseAudioFocusForMusic();
            tearDownApp(transactionalApp);
        }
    }

    /**
     * Test the scenario where a client application requests to switch the current {@link
     * android.telecom.CallEndpoint}
     *
     * <h3>Test Steps: </h3>
     *
     * <ul>
     *   1. create a managed call that is backed by a {@link android.telecom.ConnectionService } via
     *   {@link android.telecom.TelecomManager#addNewIncomingCall(PhoneAccountHandle, Bundle)}
     *   <p>2. collect the current {@link CallEndpoint} via {@link
     *   android.telecom.CallEventCallback#onCallEndpointChanged(CallEndpoint)}
     *   <p>3. collect the available {@link CallEndpoint}s via {@link
     *   android.telecom.CallEventCallback#onAvailableCallEndpointsChanged(List)}
     *   <p>4. find another endpoint that is not the current endpoint and request an audio endpoint
     *   switch via {@link android.telecom.CallControl#requestCallEndpointChange(CallEndpoint,
     *   Executor, OutcomeReceiver)}
     *   <p>
     * </ul>
     *
     * Assert the current {@link CallEndpoint} is switched successfully
     */
    @Test
    public void testBasicAudioSwitchTest_ManagedConnectionServiceApp() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper managedApp = null;
        try {
            managedApp = bindToApp(ManagedConnectionServiceApp);
            verifySwitchEndpoints(managedApp);
        } finally {
            tearDownApp(managedApp);
        }
    }

    /**
     * Test the scenario where a client application requests to switch the current {@link
     * android.telecom.CallEndpoint}
     *
     * <h3>Test Steps: </h3>
     *
     * <ul>
     *   1. create a self-managed call that is backed by a {@link android.telecom.ConnectionService}
     *   via {@link android.telecom.TelecomManager#addNewIncomingCall(PhoneAccountHandle, Bundle)}
     *   <p>2. collect the current {@link CallEndpoint} via {@link
     *   android.telecom.CallEventCallback#onCallEndpointChanged(CallEndpoint)}
     *   <p>3. collect the available {@link CallEndpoint}s via {@link
     *   android.telecom.CallEventCallback#onAvailableCallEndpointsChanged(List)}
     *   <p>4. find another endpoint that is not the current endpoint and request an audio endpoint
     *   switch via {@link android.telecom.CallControl#requestCallEndpointChange(CallEndpoint,
     *   Executor, OutcomeReceiver)}
     *   <p>
     * </ul>
     *
     * Assert the current {@link CallEndpoint} is switched successfully
     */
    @Test
    public void testBasicAudioSwitchTest_ConnectionServiceVoipAppMain() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper voipCsApp = null;
        try {
            voipCsApp = bindToApp(ConnectionServiceVoipAppMain);
            verifySwitchEndpoints(voipCsApp);
        } finally {
            tearDownApp(voipCsApp);
        }
    }

    /**
     * Test the scenario where a client application requests to switch the current {@link
     * android.telecom.CallEndpoint}
     *
     * <h3>Test Steps: </h3>
     *
     * <ul>
     *   1. create a self-managed call that is backed by a {@link android.telecom.ConnectionService}
     *   via {@link android.telecom.TelecomManager#addNewIncomingCall(PhoneAccountHandle, Bundle)}
     *   <p>2. collect the current {@link CallEndpoint} via {@link
     *   android.telecom.CallEventCallback#onCallEndpointChanged(CallEndpoint)}
     *   <p>3. collect the available {@link CallEndpoint}s via {@link
     *   android.telecom.CallEventCallback#onAvailableCallEndpointsChanged(List)}
     *   <p>4. find another endpoint that is not the current endpoint and request an audio endpoint
     *   switch via {@link android.telecom.CallControl#requestCallEndpointChange(CallEndpoint,
     *   Executor, OutcomeReceiver)}
     *   <p>
     * </ul>
     *
     * Assert the current {@link CallEndpoint} is switched successfully
     */
    @Test
    public void testBasicAudioSwitchTest_ConnectionServiceVoipAppClone() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper voipCsApp = null;

        try {
            voipCsApp = bindToApp(ConnectionServiceVoipAppClone);
            verifySwitchEndpoints(voipCsApp);
        } finally {
            tearDownApp(voipCsApp);
        }
    }

    /**
     * Test the scenario where a client application requests to switch the current {@link
     * android.telecom.CallEndpoint}
     *
     * <h3>Test Steps: </h3>
     *
     * <ul>
     *   1. create a self-managed call that is backed by a {@link android.telecom.ConnectionService
     *   } via {@link android.telecom.TelecomManager#addCall(CallAttributes, Executor,
     *   OutcomeReceiver, CallControlCallback, CallEventCallback)}
     *   <p>2. collect the current {@link CallEndpoint} via {@link
     *   android.telecom.CallEventCallback#onCallEndpointChanged(CallEndpoint)}
     *   <p>3. collect the available {@link CallEndpoint}s via {@link
     *   android.telecom.CallEventCallback#onAvailableCallEndpointsChanged(List)}
     *   <p>4. find another endpoint that is not the current endpoint and request an audio endpoint
     *   switch via {@link android.telecom.CallControl#requestCallEndpointChange(CallEndpoint,
     *   Executor, OutcomeReceiver)}
     *   <p>
     * </ul>
     *
     * Assert the current {@link CallEndpoint} is switched successfully
     */
    @Test
    public void testBasicAudioSwitchTest_TransactionalVoipAppMain() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper transactionalApp = null;
        try {
            transactionalApp = bindToApp(TransactionalVoipAppMain);
            verifySwitchEndpoints(transactionalApp);
        } finally {
            tearDownApp(transactionalApp);
        }
    }

    /**
     * Test the scenario where a client application requests to switch the current {@link
     * android.telecom.CallEndpoint}
     *
     * <h3>Test Steps: </h3>
     *
     * <ul>
     *   1. create a self-managed call that is backed by a {@link android.telecom.ConnectionService
     *   } via {@link android.telecom.TelecomManager#addCall(CallAttributes, Executor,
     *   OutcomeReceiver, CallControlCallback, CallEventCallback)}
     *   <p>2. collect the current {@link CallEndpoint} via {@link
     *   android.telecom.CallEventCallback#onCallEndpointChanged(CallEndpoint)}
     *   <p>3. collect the available {@link CallEndpoint}s via {@link
     *   android.telecom.CallEventCallback#onAvailableCallEndpointsChanged(List)}
     *   <p>4. find another endpoint that is not the current endpoint and request an audio endpoint
     *   switch via {@link android.telecom.CallControl#requestCallEndpointChange(CallEndpoint,
     *   Executor, OutcomeReceiver)}
     *   <p>
     * </ul>
     *
     * Assert the current {@link CallEndpoint} is switched successfully
     */
    @Test
    public void testBasicAudioSwitchTest_TransactionalVoipAppClone() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        AppControlWrapper transactionalApp = null;
        try {
            transactionalApp = bindToApp(TransactionalVoipAppClone);
            verifySwitchEndpoints(transactionalApp);
        } finally {
            tearDownApp(transactionalApp);
        }
    }

    private void verifySwitchEndpoints(AppControlWrapper appControlWrapper) throws Exception {
        String mo = addOutgoingCallAndVerify(appControlWrapper);
        setCallStateAndVerify(appControlWrapper, mo, STATE_ACTIVE);
        executeShellCommand(
                InstrumentationRegistry.getInstrumentation(), COMMAND_WAIT_FOR_AUDIO_ACTIVE);
        switchToAnotherCallEndpoint(appControlWrapper, mo);
        setCallStateAndVerify(appControlWrapper, mo, STATE_DISCONNECTED);
    }

    private void verifyOutgoingCallStateTransitionsWithAudioFocus(
            AppControlWrapper appControlWrapper) throws Exception {
        String mo = addOutgoingCallAndVerify(appControlWrapper);

        if (appControlWrapper.isTransactionalControl()
                && !Flags.disconnectSelfManagedStuckStartupCalls()) {
            verifyCallIsInState(mo, STATE_CONNECTING);
        } else {
            verifyCallIsInState(mo, STATE_DIALING);
        }
        setCallStateAndVerify(appControlWrapper, mo, STATE_ACTIVE);
        waitForAndVerifyMusicFocus(
                true, AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);
        setCallStateAndVerify(appControlWrapper, mo, STATE_HOLDING);
        setCallStateAndVerify(appControlWrapper, mo, STATE_DISCONNECTED);
    }

    private void verifyIncomingCallStateTransitionsWithAudioFocus(
            AppControlWrapper appControlWrapper) throws Exception {
        String mt = addIncomingCallAndVerify(appControlWrapper);
        verifyCallIsInState(mt, STATE_RINGING);
        // with incoming/ringing calls it is possible that the audio framework will allow duck
        // so that the ringtone plays overtop of the music.
        boolean result =
                waitForAndVerifyMusicFocus(
                        false,
                        AudioManager.AUDIOFOCUS_LOSS,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK);
        if (!result) {
            // Verify that audio focus is acquired when audio focus is explicitly active.
            // It's possible that vibration is turned on in which case we don't acquire audio
            // focus.
            answerViaInCallServiceAndVerify(mt, VideoProfile.STATE_AUDIO_ONLY);
            verifyCallIsInState(mt, STATE_ACTIVE);
            waitForAndVerifyMusicFocus(
                    true,
                    AudioManager.AUDIOFOCUS_LOSS,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK);
        }
        setCallStateAndVerify(appControlWrapper, mt, STATE_ACTIVE);
        setCallStateAndVerify(appControlWrapper, mt, STATE_HOLDING);
        setCallStateAndVerify(appControlWrapper, mt, STATE_DISCONNECTED);
    }
}
