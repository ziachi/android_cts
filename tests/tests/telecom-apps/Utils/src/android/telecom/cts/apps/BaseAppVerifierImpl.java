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

import static android.os.SystemClock.sleep;
import static android.telecom.Call.STATE_ACTIVE;
import static android.telecom.Call.STATE_DISCONNECTED;
import static android.telecom.Call.STATE_RINGING;
import static android.telecom.cts.apps.AttributesUtil.getDefaultAttributesForApp;
import static android.telecom.cts.apps.AttributesUtil.getDefaultAttributesForManaged;
import static android.telecom.cts.apps.AttributesUtil.getDefaultMmiAttributesForApp;
import static android.telecom.cts.apps.AttributesUtil.getRandomAttributesForApp;
import static android.telecom.cts.apps.AttributesUtil.getRandomAttributesForManaged;
import static android.telecom.cts.apps.ShellCommandExecutor.COMMAND_CLEANUP_STUCK_CALLS;
import static android.telecom.cts.apps.ShellCommandExecutor.COMMAND_RESET_CAR;
import static android.telecom.cts.apps.ShellCommandExecutor.dumpTelecom;
import static android.telecom.cts.apps.ShellCommandExecutor.executeShellCommand;
import static android.telecom.cts.apps.TelecomTestApp.ManagedConnectionServiceApp;
import static android.telecom.cts.apps.TelecomTestApp.ManagedConnectionServiceAppClone;
import static android.telecom.cts.apps.WaitForInCallService.verifyCallExtras;
import static android.telecom.cts.apps.WaitForInCallService.verifyCallState;
import static android.telecom.cts.apps.WaitForInCallService.waitForInCallServiceBinding;
import static android.telecom.cts.apps.WaitForInCallService.waitUntilExpectCallCount;
import static android.telecom.cts.apps.WaitForInCallService.waitUntilNewCallId;
import static android.telecom.cts.apps.WaitUntil.DEFAULT_TIMEOUT_MS;
import static android.telecom.cts.apps.WaitUntil.waitUntilConditionIsTrueOrTimeout;

import static junit.framework.Assert.assertEquals;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.AppOpsManager;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.RemoteException;
import android.telecom.Call;
import android.telecom.CallAttributes;
import android.telecom.CallEndpoint;
import android.telecom.CallException;
import android.telecom.Connection;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellIdentityUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * This class implements all the methods test classes call into to perform some action on an
 * application that is bound to in the cts/tests/tests/telecom-apps dir.
 */
public class BaseAppVerifierImpl {
    static final String TAG = BaseAppVerifierImpl.class.getSimpleName();
    static final boolean HAS_TELECOM = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    private static final String REGISTER_SIM_SUBSCRIPTION_PERMISSION =
            "android.permission.REGISTER_SIM_SUBSCRIPTION";
    private static final String MODIFY_PHONE_STATE_PERMISSION =
            "android.permission.MODIFY_PHONE_STATE";
    private static final int FOCUS_TIMEOUT_MILLIS = 6000;
    private static final int TIME_BETWEEN_FOCUS_ATTEMPTS_MILLIS = 500;
    private static final int MAX_FOCUS_ATTEMPTS = 10;

    /**
     * Audio attributes for a typical music app.
     * Adapted from the <a href="https://developer.android.com/media/optimize/audio-focus">Android
     * Developers site</a>.
     */
    private static final AudioAttributes MUSIC_AUDIO_ATTRIBUTES = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build();

    public Context mContext;
    public TelecomManager mTelecomManager;
    private final BindUtils mBindUtils = new BindUtils();
    private final List<PhoneAccount> mManagedAccounts;
    private final List<PhoneAccount> mManagedCloneAccounts;
    private final Instrumentation mInstrumentation;
    private final InCallServiceMethods mVerifierMethods;
    private final String mCallingPackageName;
    private final AudioManager mAudioManager;
    private boolean mIsEmergencyCallingSetup = false;

    public String mPreviousDefaultDialer = "";
    public PhoneAccountHandle mPreviousDefaultPhoneAccount = null;
    public boolean mEnableCallOperationCapture = false;

    // Stores the current audio focus
    private final LinkedBlockingQueue<Integer> mMusicAudioFocusQueue =
            new LinkedBlockingQueue<>();

    /**
     * Handle audio focus changes for simulated music playback; put these onto a focus queue so we
     * can wait for it later.
     */
    private final AudioManager.OnAudioFocusChangeListener mMusicAudioFocusChangeListener =
            focusChange -> {
                android.util.Log.i(TAG, "onAudioFocusChange: changed to " + focusChange);
                mMusicAudioFocusQueue.offer(focusChange);
            };

    /**
     * Setup an audio focus request for simulated pre-call music playback.  We want to get notified
     * of focus changes pertaining to the music playback.
     * Adapted from the <a href="https://developer.android.com/media/optimize/audio-focus">Android
     * Developers Site</a>.
     */
    public final AudioFocusRequest mMusicFocusRequest = new AudioFocusRequest
            .Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(MUSIC_AUDIO_ATTRIBUTES)
            .setAcceptsDelayedFocusGain(true)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener(mMusicAudioFocusChangeListener)
            .build();

    public BaseAppVerifierImpl(Instrumentation i, List<PhoneAccount> pAs,
            List<PhoneAccount> pAClones, InCallServiceMethods vm) {
        mInstrumentation = i;
        mContext = i.getContext();
        mTelecomManager = mContext.getSystemService(TelecomManager.class);
        mManagedAccounts = pAs;
        mManagedCloneAccounts = pAClones;
        mVerifierMethods = vm;
        mCallingPackageName = mContext.getPackageName();
        mAudioManager = mContext.getSystemService(AudioManager.class);
    }

    public void setUp() throws Exception {
        executeShellCommand(mInstrumentation, COMMAND_RESET_CAR);
        AppOpsManager aom = mContext.getSystemService(AppOpsManager.class);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(aom,
                (appOpsMan) -> appOpsMan.setUidMode(AppOpsManager.OPSTR_PROCESS_OUTGOING_CALLS,
                        Process.myUid(), AppOpsManager.MODE_ALLOWED));
        mPreviousDefaultDialer = ShellCommandExecutor.getDefaultDialer(mInstrumentation);
        ShellCommandExecutor.setDefaultDialer(mInstrumentation, mCallingPackageName);
        // In order to isolate cascading test failures, cleanup the telecom or cts test process
        maybeCleanupTelecom();
        HoldableTracker.clearTrackedConnections();
    }

    public void tearDown() throws Exception {
        executeShellCommand(mInstrumentation, COMMAND_CLEANUP_STUCK_CALLS);
        if (!mPreviousDefaultDialer.equals("")) {
            ShellCommandExecutor.setDefaultDialer(mInstrumentation, mPreviousDefaultDialer);
        }
        clearUserDefaultPhoneAccountOverride();
        ShellIdentityUtils.dropShellPermissionIdentity();
        HoldableTracker.clearTrackedConnections();
    }

    public void maybeCleanupTelecom() {
        if (!shouldTestTelecom(mContext)) {
            return;
        }
        try {
            if (mTelecomManager.isInCall()) {
                Log.w(TAG, "maybeCleanupTelecom: Telecom is in a call");
                dumpTelecom(mInstrumentation);
                executeShellCommand(mInstrumentation, COMMAND_CLEANUP_STUCK_CALLS);
            }
            if (BindUtils.hasBoundTestApp()) {
                Log.w(TAG, "maybeCleanupTelecom: A test app is bound when it should not be");
                BindUtils.printBoundTestApps();
            }
        } catch (Exception e) {
            // ignore exception
        }
    }

    public static boolean shouldTestTelecom(Context context) {
        if (!HAS_TELECOM) {
            return false;
        }
        final PackageManager pm = context.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_TELECOM);
    }

    public AppControlWrapper bindToApp(TelecomTestApp applicationName) throws Exception {
        AppControlWrapper control = mBindUtils.bindToApp(mContext, applicationName);
        Log.i("BaseAppVerifierImpl", "bindToApp: applicationName = " + applicationName);
        if (isManagedConnectionService(applicationName)) {
            for (PhoneAccount pA : mManagedAccounts) {
                registerManagedPhoneAccount(pA);
            }
        } else if (isManagedConnectionServiceClone(applicationName)) {
            Log.i(
                    "BaseAppVerifierImpl",
                    "bindToApp: clone app managed - phone account: "
                            + mManagedCloneAccounts.get(0));
            for (PhoneAccount pA : mManagedCloneAccounts) {
                registerManagedPhoneAccount(pA);
            }
        }
        return control;
    }

    private boolean isManagedConnectionService(TelecomTestApp applicationName) {
        return applicationName.equals(ManagedConnectionServiceApp);
    }

    private boolean isManagedConnectionServiceClone(TelecomTestApp applicationName) {
        return applicationName.equals(ManagedConnectionServiceAppClone);
    }

    public List<AppControlWrapper> bindToApps(List<TelecomTestApp> applicationNames)
            throws Exception {
        ArrayList<AppControlWrapper> controls = new ArrayList<>();
        for (TelecomTestApp name : applicationNames) {
            AppControlWrapper control = bindToApp(name);
            controls.add(control);
        }
        return controls;
    }

    public void tearDownApp(AppControlWrapper appControl) {
        if (appControl != null) {
            mBindUtils.unbindFromApp(mContext, appControl);
        }
    }

    public void tearDownApps(List<AppControlWrapper> appControls) {
        for (AppControlWrapper control : appControls) {
            tearDownApp(control);
        }
    }

    /***********************************************************
     /                 core methods
     /***********************************************************/

    public CallAttributes getDefaultAttributes(TelecomTestApp name,
            boolean isOutgoing)
            throws Exception {
        if (name.equals(ManagedConnectionServiceApp)) {
            // Treat the first element in mManagedAccounts as the "default"
            return getDefaultAttributesForManaged(
                    mManagedAccounts.get(0).getAccountHandle(),
                    isOutgoing,
                    false /* isEmergency */);
        } else if (name.equals(ManagedConnectionServiceAppClone)) {
            return getDefaultAttributesForManaged(
                    mManagedCloneAccounts.get(0).getAccountHandle(),
                    isOutgoing,
                    false /* isEmergency */);
        }
        return getDefaultAttributesForApp(name, isOutgoing);
    }

    public CallAttributes getDefaultAttributes(TelecomTestApp name, PhoneAccountHandle pAH,
            boolean isOutgoing)
            throws Exception {
        if (name.equals(ManagedConnectionServiceApp)
                || name.equals(ManagedConnectionServiceAppClone)) {
            return getDefaultAttributesForManaged(pAH, isOutgoing, false /* isEmergency */);
        }
        return getDefaultAttributesForApp(name, isOutgoing);
    }

    public CallAttributes getDefaultAttributesForEmergency(TelecomTestApp name) {
        if (name.equals(ManagedConnectionServiceApp)) {
            // Treat the first element in mManagedAccounts as the "default"
            return getDefaultAttributesForManaged(
                    mManagedAccounts.get(0).getAccountHandle(),
                    true /* isOutgoing */,
                    true /* isEmergency */);
        } else if (name.equals(ManagedConnectionServiceAppClone)) {
            return getDefaultAttributesForManaged(
                    mManagedCloneAccounts.get(0).getAccountHandle(),
                    true /* isOutgoing */,
                    true /* isEmergency */);
        }
        return null;
    }

    public CallAttributes getDefaultMmiAttributes(TelecomTestApp name, boolean inCallMmi)
            throws Exception {
        if (name.equals(ManagedConnectionServiceApp)) {
            // Treat the first element in mManagedAccounts as the "default"
            return getDefaultMmiAttributesForApp(
                    name, mManagedAccounts.get(0).getAccountHandle(), inCallMmi);
        } else if (name.equals(ManagedConnectionServiceAppClone)) {
            return getDefaultMmiAttributesForApp(
                    name, mManagedCloneAccounts.get(0).getAccountHandle(), inCallMmi);
        }
        return getDefaultMmiAttributesForApp(name, null /* handle */, inCallMmi);
    }

    public CallAttributes getRandomAttributes(TelecomTestApp name,
            boolean isOutgoing,
            boolean isHoldable)
            throws Exception {
        if (name.equals(ManagedConnectionServiceApp)) {
            // Treat the first element in mManagedAccounts as the "default"
            return getRandomAttributesForManaged(mManagedAccounts.get(0).getAccountHandle(),
                    isOutgoing, isHoldable);
        } else if (name.equals(ManagedConnectionServiceAppClone)) {
            return getRandomAttributesForManaged(mManagedCloneAccounts.get(0).getAccountHandle(),
                    isOutgoing, isHoldable);
        }
        return getRandomAttributesForApp(name, isOutgoing, isHoldable);
    }

    public int addCall(AppControlWrapper appControl, CallAttributes attributes)
            throws Exception {
        int currentCallCount = mVerifierMethods.getCurrentCallCount();
        appControl.addCall(attributes);
        waitForInCallServiceBinding(mVerifierMethods);
        return currentCallCount;
    }

    public int addCall(AppControlWrapper appControl, CallAttributes attributes,
            Consumer<CallStateTransitionOperation> consumer) throws Exception {
        int currentCallCount = mVerifierMethods.getCurrentCallCount();
        appControl.addCall(attributes, consumer);
        waitForInCallServiceBinding(mVerifierMethods);
        return currentCallCount;
    }

    public String addCallAndVerify(AppControlWrapper appControl, CallAttributes attributes,
            Consumer<CallStateTransitionOperation> consumer) throws Exception {
        int currentCallCount = addCall(appControl, attributes, consumer);
        waitUntilExpectedCallCount(currentCallCount + 1);
        return mVerifierMethods.getLastAddedCall().getDetails().getId();
    }

    public String addCallAndVerify(AppControlWrapper appControl, CallAttributes attributes)
            throws Exception {
        int currentCallCount = addCall(appControl, attributes);
        waitUntilExpectedCallCount(currentCallCount + 1);
        return mVerifierMethods.getLastAddedCall().getDetails().getId();
    }

    public String verifyAddEmergencyCall(
            AppControlWrapper appControl,
            CallAttributes attributes,
            Consumer<CallStateTransitionOperation> consumer,
            int numDisconnectDueToEcc)
            throws Exception {
        int currentCallCount = mVerifierMethods.getCurrentCallCount();
        appControl.verifyAddCall(attributes, consumer);
        waitForInCallServiceBinding(mVerifierMethods);
        waitUntilExpectedCallCount(currentCallCount + 1 - numDisconnectDueToEcc);
        return mVerifierMethods.getLastAddedCall().getDetails().getId();
    }

    public String addAndGetNewCall(AppControlWrapper appControl, CallAttributes attr,
            String idToExclude, Consumer<CallStateTransitionOperation> consumer
    ) throws Exception {
        addCall(appControl, attr, consumer);
        waitUntilNewCallId(mVerifierMethods, idToExclude);
        return mVerifierMethods.getLastAddedCall().getDetails().getId();
    }

    public void addCallAndVerifyFailure(AppControlWrapper appControl, CallAttributes attributes)
            throws Exception {
        appControl.addFailedCall(attributes);
    }

    public void addFailedCallWithCreateConnectionVerify(
            AppControlWrapper appControl, CallAttributes attributes) throws Exception {
        appControl.addFailedCallWithCreateConnectionVerify(attributes);
    }

    public void waitUntilExpectedCallCount(int expectedCallCount) {
        waitUntilExpectCallCount(mVerifierMethods, expectedCallCount);
    }

    // -- call state
    public void setCallState(AppControlWrapper appControl, String id, int callState)
            throws Exception {
        appControl.setCallState(id, callState, true, new Bundle());
    }

    public void setCallStateAndVerify(AppControlWrapper appControl, String id, int callState)
            throws Exception {
        appControl.setCallState(id, callState, true, new Bundle());
        verifyCallState(mVerifierMethods, id, callState);
    }

    public void setCallStateAndVerify(AppControlWrapper appControl, String id, int targetCallState,
            int arg) throws Exception {
        Bundle extras = new Bundle();
        if (targetCallState == STATE_ACTIVE) {
            verifyCallIsInState(id, STATE_RINGING);
            extras = CallControlExtras.addVideoStateExtra(extras, arg);
        } else if (targetCallState == STATE_DISCONNECTED) {
            extras = CallControlExtras.addDisconnectCauseExtra(extras, arg);
        }
        appControl.setCallState(id, targetCallState, true, extras);
        // verify the call was added in the ICS
        verifyCallState(mVerifierMethods, id, targetCallState);
    }

    public void verifyCallExtraPresent(String id, String extraToVerify, boolean expected)
            throws Exception {
        verifyCallExtras(mVerifierMethods, id, extraToVerify, expected);
    }

    public CallException setCallStateButExpectOnError(AppControlWrapper appControl,
            String id,
            int targetCallState)
            throws Exception {
        verifyAppIsTransactional(appControl);
        return appControl.setCallState(id, targetCallState, false, new Bundle());

    }

    private void verifyAppIsTransactional(AppControlWrapper appControlWrapper) throws Exception {
        if (!appControlWrapper.isTransactionalControl()) {
            throw new Exception("This method is only for Transactional Apps");
        }
    }

    public CallException setCallStateButExpectOnError(AppControlWrapper appControl,
            String id,
            int targetCallState,
            int arg) throws Exception {
        verifyAppIsTransactional(appControl);
        Bundle extras = new Bundle();
        if (targetCallState == STATE_ACTIVE) {
            verifyCallIsInState(id, STATE_RINGING);
            extras = CallControlExtras.addVideoStateExtra(extras, arg);
        } else if (targetCallState == STATE_DISCONNECTED) {
            extras = CallControlExtras.addDisconnectCauseExtra(extras, arg);
        }
        return appControl.setCallState(id, targetCallState, false, extras);
    }

    public void verifyCallIsInState(String id, int state) throws Exception {
        waitForInCallServiceBinding(mVerifierMethods);
        verifyCallState(mVerifierMethods, id, state);
    }

    public void answerViaInCallService(String id, int videoState) throws Exception {
        Call targetCall = findTargetCall(id);
        targetCall.answer(videoState);
    }

    public void answerViaInCallServiceAndVerify(String id, int videoState) throws Exception {
        answerViaInCallService(id, videoState);
        verifyCallIsInState(id, STATE_ACTIVE);
    }

    public void holdCallViaInCallService(String id) {
        Call call = findTargetCall(id);
        call.hold();
    }

    public void unholdCallViaInCallService(String id) {
        Call call = findTargetCall(id);
        call.unhold();
    }

    public void disconnectCallViaInCallService(String id) {
        Call call = findTargetCall(id);
        call.disconnect();
    }

    public boolean isCallHoldable(String id) {
        Call call = findTargetCall(id);
        return (call.getDetails().getCallCapabilities() & Connection.CAPABILITY_HOLD) != 0;
    }

    // -- audio state
    public CallEndpoint getAnotherCallEndpoint(AppControlWrapper appControl, String id)
            throws Exception {
        CallEndpoint currentCallEndpoint = getCurrentCallEndpoint(appControl, id);
        List<CallEndpoint> endpoints = getAvailableCallEndpoints(appControl, id);

        if (currentCallEndpoint == null) {
            fail("currentCallEndpoint is NULL");
        }
        if (endpoints == null) {
            fail("available endpoints list is NULL");
        }
        if (endpoints.size() == 1) {
            return null;
        }
        for (CallEndpoint endpoint : endpoints) {
            if (endpoint.getEndpointType() != currentCallEndpoint.getEndpointType()) {
                return endpoint;
            }
        }
        return null;
    }

    public CallEndpoint getCurrentCallEndpoint(AppControlWrapper appControl, String id)
            throws Exception {
        return appControl.getCurrentCallEndpoint(id);
    }

    public List<CallEndpoint> getAvailableCallEndpoints(AppControlWrapper appControl, String id)
            throws Exception {
        return appControl.getAvailableCallEndpoints(id);
    }


    public void setAudioRouteStateAndVerify(AppControlWrapper appControl, String id,
            CallEndpoint newCallEndpoint) throws Exception {
        appControl.setAudioRouteStateAndVerify(id, newCallEndpoint);
    }

    public boolean isMuted(AppControlWrapper appControl, String id) throws RemoteException {
        return appControl.isMuted(id);
    }

    public void setMuteState(AppControlWrapper appControl, String id, boolean isMuted)
            throws RemoteException {
        appControl.setMuteState(id, isMuted);
    }

    public void sendConnectionEvent(AppControlWrapper appControl, String id, String event)
            throws RemoteException {
        appControl.sendConnectionEvent(id, event);
    }

    // -- phone accounts
    public void registerDefaultPhoneAccount(AppControlWrapper appControl) throws RemoteException {
        appControl.registerDefaultPhoneAccount();
        if (appControl.isManagedAppControl()) {
            for (PhoneAccount pa : mManagedAccounts) {
                assertTrue("Managed PhoneAccount [ +" + pa.getAccountHandle() + " ] is not"
                        + "registered.", isPhoneAccountRegistered(pa.getAccountHandle()));
            }
        } if (appControl.isManagedAppControlClone()) {
            for (PhoneAccount pa : mManagedCloneAccounts) {
                assertTrue("Managed Clone PhoneAccount [ +" + pa.getAccountHandle() + " ] is not"
                        + "registered.", isPhoneAccountRegistered(pa.getAccountHandle()));
            }
        } else {
            PhoneAccount account = appControl.getDefaultPhoneAccount();
            assertTrue(isPhoneAccountRegistered(account.getAccountHandle()));
        }
    }

    public void registerCustomPhoneAccount(AppControlWrapper appControl, PhoneAccount account)
            throws RemoteException {
        appControl.registerCustomPhoneAccount(account);
        assertTrue(isPhoneAccountRegistered(account.getAccountHandle()));
    }

    public void registerManagedPhoneAccount(PhoneAccount pa) throws Exception {
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelecomManager,
                tm -> tm.registerPhoneAccount(pa),
                MODIFY_PHONE_STATE_PERMISSION,
                REGISTER_SIM_SUBSCRIPTION_PERMISSION);
        ShellCommandExecutor.enablePhoneAccount(mInstrumentation, pa.getAccountHandle());
    }

    public void setUserDefaultPhoneAccountOverride(PhoneAccountHandle handle) throws Exception {
        mPreviousDefaultPhoneAccount = mTelecomManager.getUserSelectedOutgoingPhoneAccount();
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelecomManager,
                (tm) -> tm.setUserSelectedOutgoingPhoneAccount(handle));
        assertEquals("Could not set " + handle + "as the user default" , handle,
                mTelecomManager.getUserSelectedOutgoingPhoneAccount());
    }

    private void clearUserDefaultPhoneAccountOverride() throws Exception {
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelecomManager,
                (tm) -> tm.setUserSelectedOutgoingPhoneAccount(mPreviousDefaultPhoneAccount));
    }

    public void unregisterPhoneAccountWithHandle(AppControlWrapper appControl,
            PhoneAccountHandle handle) throws RemoteException {
        appControl.unregisterPhoneAccountWithHandle(handle);
        assertFalse(isPhoneAccountRegistered(handle));
    }

    public List<PhoneAccountHandle> getAccountHandlesForApp(AppControlWrapper appControl)
            throws RemoteException {
        return appControl.getAccountHandlesForApp();
    }

    public void verifyCallPhoneAccount(String id, PhoneAccountHandle handle) {
        Call targetCall = findTargetCall(id);
        if (targetCall.getDetails() == null) {
            fail("verifyCallPhoneAccount: failed to find target call details, id=" + id);
        }
        assertEquals("Call PhoneAccount did not match expected", handle,
                targetCall.getDetails().getAccountHandle());
    }

    public boolean isPhoneAccountRegistered(PhoneAccountHandle handle) {
        return mTelecomManager.getPhoneAccount(handle) != null;
    }

    public void assertAudioMode(final int expectedMode) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return mAudioManager.getMode() == expectedMode;
                    }
                },
                DEFAULT_TIMEOUT_MS,
                "Audio mode was expected to be " + expectedMode
        );
    }
    public void verifyNotificationPostedForCall(AppControlWrapper appControlWrapper, String callId){
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        try {
                            return appControlWrapper.isNotificationPostedForCall(callId);
                        } catch (RemoteException e) {
                            throw new RuntimeException(e);
                        }
                    }
                },
                DEFAULT_TIMEOUT_MS,
               String.format("Expected to find notification for call with id=[%s], "
                               + "for application=[%s], but no notification was posted by the"
                               + " notification manager", callId,
                                appControlWrapper.getTelecomApps()));
    }

    /**
     * Acquire media focus for music playback; pretend we are listening to music so that we can
     * verify that focus is lost during a call and restored later.
     */
    public void acquireAudioFocusForMusic() {
        final int[] result = {AudioManager.AUDIOFOCUS_REQUEST_FAILED};
        ShellIdentityUtils.invokeWithShellPermissions(() -> {
            int attempts = 0;
            while (result[0] == AudioManager.AUDIOFOCUS_REQUEST_FAILED
                    && attempts <= MAX_FOCUS_ATTEMPTS) {
                attempts++;
                result[0] = mAudioManager.requestAudioFocus(mMusicFocusRequest);
                if (result[0] == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
                    sleep(TIME_BETWEEN_FOCUS_ATTEMPTS_MILLIS);
                }
            }
        });
        if (result[0] == AudioManager.AUDIOFOCUS_REQUEST_DELAYED) {
            waitForAndVerifyMusicFocus(true, new int[] {AudioManager.AUDIOFOCUS_REQUEST_GRANTED});
        } else {
            assertEquals("Failed to acquire focus for media playback in order to verify that "
                            + "media focus is lost in calls.",
                    AudioManager.AUDIOFOCUS_REQUEST_GRANTED,
                    result[0]);
        }
    }

    /** Waits to ensure that the music audio focus was one of the expected values. */
    public boolean waitForAndVerifyMusicFocus(boolean verifyPresence, int[] expectedValues) {
        Integer newFocus = null;
        try {
            newFocus = mMusicAudioFocusQueue.poll(FOCUS_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
            fail("Expected to get new music focus but timed out.");
        }
        if (verifyPresence) {
            assertNotNull(
                    "Expected focus to be reported but none was within the timeout.", newFocus);
        }
        boolean wasExpectedFocusValue = false;
        if (newFocus != null) {
            int newFocusValue = newFocus.intValue();

            // We expect to have lost focus; it will likely be reported as transient focus lost.
            // Both of these focus lost types indicate that something else has gained exclusive
            // access to the audio focus.
            wasExpectedFocusValue = Arrays.stream(expectedValues).anyMatch(v -> v == newFocusValue);
            assertTrue(
                    "Expected focus to be one of "
                            + Arrays.toString(expectedValues)
                            + " but was "
                            + newFocusValue,
                    wasExpectedFocusValue);
        }
        return wasExpectedFocusValue;
    }

    /**
     * Release media focus for media playback; pretend we are not listening to music any longer.
     */
    public void releaseAudioFocusForMusic() {
        int result = mAudioManager.abandonAudioFocusRequest(mMusicFocusRequest);
        assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, result);
    }

    public void setupForEmergencyCalling() throws Exception {
        mIsEmergencyCallingSetup = true;
        ShellCommandExecutor.setSystemDialerOverride(
                InstrumentationRegistry.getInstrumentation(),
                AttributesUtil.SYSTEM_DIALER_PKG_NAME);
        ShellCommandExecutor.addTestEmergencyNumber(
                InstrumentationRegistry.getInstrumentation(), AttributesUtil.TEST_EMERGENCY_NUMBER);
        ShellCommandExecutor.setTestEmergencyPhoneAccountPackageFilter(
                InstrumentationRegistry.getInstrumentation(),
                AttributesUtil.TEST_EMERGENCY_MANAGED_PHONE_ACCOUNT_PKG_NAME
                        + ","
                        + AttributesUtil.TEST_EMERGENCY_MANAGED_CLONE_PHONE_ACCOUNT_PKG_NAME);
    }

    public void tearDownEmergencyCalling() throws Exception {
        if (!mIsEmergencyCallingSetup) return;
        ShellCommandExecutor.clearSystemDialerOverride(
                InstrumentationRegistry.getInstrumentation());
        ShellCommandExecutor.clearTestEmergencyNumbers(
                InstrumentationRegistry.getInstrumentation());
        ShellCommandExecutor.clearTestEmergencyPhoneAccountPackageFilter(
                InstrumentationRegistry.getInstrumentation());
    }

    private Call findTargetCall(String id) {
        waitForInCallServiceBinding(mVerifierMethods);
        List<Call> calls = mVerifierMethods.getOngoingCalls();
        Call targetCall = null;
        for (Call call : calls) {
            if (call.getDetails().getId().equals(id)) {
                targetCall = call;
                break;
            }
        }
        if (targetCall == null) {
            fail("answerViaInCallServiceAndVerify: failed to find target call id=" + id);
        }
        return targetCall;
    }
}
