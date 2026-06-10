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

package android.telecom.cts.cuj;

import static android.telecom.cts.apps.AttributesUtil.getExtrasWithPhoneAccount;
import static android.telecom.cts.apps.AttributesUtil.isOutgoing;
import static android.telecom.cts.apps.TelecomTestApp.MANAGED_ADDRESS;
import static android.telecom.cts.apps.TelecomTestApp.MANAGED_APP_CLONE_LABEL;
import static android.telecom.cts.apps.TelecomTestApp.MANAGED_APP_CN;
import static android.telecom.cts.apps.TelecomTestApp.MANAGED_APP_ID;
import static android.telecom.cts.apps.TelecomTestApp.MANAGED_APP_LABEL;
import static android.telecom.cts.apps.TelecomTestApp.MANAGED_CLONE_ADDRESS;
import static android.telecom.cts.apps.TelecomTestApp.MANAGED_CLONE_APP_CN;
import static android.telecom.cts.apps.TelecomTestApp.MANAGED_CLONE_APP_ID;

import static junit.framework.Assert.assertNotNull;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.RemoteException;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.telecom.Call;
import android.telecom.CallAttributes;
import android.telecom.CallEndpoint;
import android.telecom.CallException;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.cts.apps.AppControlWrapper;
import android.telecom.cts.apps.BaseAppVerifierImpl;
import android.telecom.cts.apps.CallStateTransitionOperation;
import android.telecom.cts.apps.InCallServiceMethods;
import android.telecom.cts.apps.TelecomTestApp;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * BaseAppVerifier should be extended by any test class that wants to bind to the test apps in the
 * cts/tests/tests/telecomApps directory.
 */
public class BaseAppVerifier {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();
    public static final boolean S_IS_TEST_DISABLED = true;
    public boolean mShouldTestTelecom = true;
    public boolean mSupportsManagedCalls = false;
    private BaseAppVerifierImpl mBaseAppVerifierImpl;
    private TelecomManager mTelecomManager;
    protected Context mContext = null;
    /***********************************************************
     /  ManagedConnectionServiceApp/ ManagedConnectionServiceAppClone - The PhoneAccountHandle and
     /  PhoneAccount must reside in the CTS test process.
     /***********************************************************/
    public static final PhoneAccountHandle MANAGED_HANDLE_1 =
            new PhoneAccountHandle(MANAGED_APP_CN, MANAGED_APP_ID);

    private static final PhoneAccount MANAGED_DEFAULT_ACCOUNT_1 =
            PhoneAccount.builder(MANAGED_HANDLE_1, MANAGED_APP_LABEL)
                    .setAddress(Uri.parse(MANAGED_ADDRESS))
                    .setSubscriptionAddress(Uri.parse(MANAGED_ADDRESS))
                    .setCapabilities(
                            PhoneAccount.CAPABILITY_VIDEO_CALLING
                                    /* needed in order to be default sub */
                                    | PhoneAccount.CAPABILITY_CALL_PROVIDER
                                    /* needed to place ECC */
                                    | PhoneAccount.CAPABILITY_PLACE_EMERGENCY_CALLS)
                    .setHighlightColor(Color.RED)
                    .addSupportedUriScheme(PhoneAccount.SCHEME_SIP)
                    .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                    .addSupportedUriScheme(PhoneAccount.SCHEME_VOICEMAIL)
                    .build();

    public static final PhoneAccountHandle MANAGED_HANDLE_2 =
            new PhoneAccountHandle(MANAGED_APP_CN, MANAGED_APP_ID + "_2");
    private static final PhoneAccount MANAGED_DEFAULT_ACCOUNT_2 =
            PhoneAccount.builder(MANAGED_HANDLE_2, MANAGED_APP_LABEL)
                    .setAddress(Uri.parse(MANAGED_ADDRESS + "_2"))
                    .setSubscriptionAddress(Uri.parse(MANAGED_ADDRESS + "_2"))
                    .setCapabilities(
                            PhoneAccount.CAPABILITY_VIDEO_CALLING
                                    /* needed in order to be default sub */
                                    | PhoneAccount.CAPABILITY_CALL_PROVIDER
                                    /* needed to place ECC */
                                    | PhoneAccount.CAPABILITY_PLACE_EMERGENCY_CALLS)
                    .setHighlightColor(Color.RED)
                    .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                    .build();

    public static final PhoneAccountHandle MANAGED_CLONE_HANDLE_1 =
            new PhoneAccountHandle(MANAGED_CLONE_APP_CN, MANAGED_CLONE_APP_ID);
    private static final PhoneAccount MANAGED_CLONE_DEFAULT_ACCOUNT_1 =
            PhoneAccount.builder(MANAGED_CLONE_HANDLE_1, MANAGED_APP_CLONE_LABEL)
                    .setAddress(Uri.parse(MANAGED_CLONE_ADDRESS))
                    .setSubscriptionAddress(Uri.parse(MANAGED_CLONE_ADDRESS))
                    .setCapabilities(
                            PhoneAccount.CAPABILITY_VIDEO_CALLING
                                    /* needed in order to be default sub */
                                    | PhoneAccount.CAPABILITY_CALL_PROVIDER
                                    /* needed to place ECC */
                                    | PhoneAccount.CAPABILITY_PLACE_EMERGENCY_CALLS)
                    .setHighlightColor(Color.RED)
                    .addSupportedUriScheme(PhoneAccount.SCHEME_SIP)
                    .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                    .addSupportedUriScheme(PhoneAccount.SCHEME_VOICEMAIL)
                    .build();

    private static final Map<PhoneAccountHandle, PhoneAccount> MANAGED_PHONE_ACCOUNTS =
            new HashMap<>();
    static {
        MANAGED_PHONE_ACCOUNTS.put(MANAGED_HANDLE_1, MANAGED_DEFAULT_ACCOUNT_1);
        MANAGED_PHONE_ACCOUNTS.put(MANAGED_HANDLE_2, MANAGED_DEFAULT_ACCOUNT_2);
    }

    /***********************************************************
     /                 setUp and tearDown methods
     /***********************************************************/
    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mShouldTestTelecom = BaseAppVerifierImpl.shouldTestTelecom(mContext);
        mSupportsManagedCalls = TestUtils.hasDialerRole(mContext)
                && TestUtils.hasTelephonyFeature(mContext);
        if (!mShouldTestTelecom) {
            return;
        }
        mBaseAppVerifierImpl = new BaseAppVerifierImpl(
                InstrumentationRegistry.getInstrumentation(),
                Arrays.asList(MANAGED_DEFAULT_ACCOUNT_1, MANAGED_DEFAULT_ACCOUNT_2),
                Arrays.asList(MANAGED_CLONE_DEFAULT_ACCOUNT_1),
                new InCallServiceMethods() {

                    @Override
                    public boolean isBound() {
                        return CujInCallService.isServiceBound();
                    }

                    @Override
                    public List<Call> getOngoingCalls() {
                        return CujInCallService.getOngoingCalls();
                    }

                    @Override
                    public Call getLastAddedCall() {
                        return CujInCallService.getLastAddedCall();
                    }

                    @Override
                    public int getCurrentCallCount() {
                        return CujInCallService.getCurrentCallCount();
                    }
                });
        mBaseAppVerifierImpl.setUp();
        mTelecomManager = mContext.getSystemService(TelecomManager.class);
    }

    @After
    public void tearDown() throws Exception {
        if (!mShouldTestTelecom || mBaseAppVerifierImpl == null) {
            return;
        }
        mBaseAppVerifierImpl.tearDown();
    }

    /***********************************************************
     /                 setUp and tearDown methods
     /***********************************************************/

    /**
     * This method will check TelecomManager#isInCall to determine if there is an ongoing call at
     * the test start. If there is a call, the Telecom command to disconnect all ongoing calls will
     * run and attempt to put the test class in a good state.  Also, the method will log any bound
     * test apps.
     */
    public void maybeCleanupTelecom() {
        mBaseAppVerifierImpl.maybeCleanupTelecom();
    }

    public AppControlWrapper bindToApp(TelecomTestApp applicationName) throws Exception {
        return mBaseAppVerifierImpl.bindToApp(applicationName);
    }

    public List<AppControlWrapper> bindToApps(List<TelecomTestApp> applicationNames)
            throws Exception {
        return mBaseAppVerifierImpl.bindToApps(applicationNames);
    }

    public void tearDownApp(AppControlWrapper appControl) {
        mBaseAppVerifierImpl.tearDownApp(appControl);
    }

    public void tearDownApps(List<AppControlWrapper> appControls) {
        mBaseAppVerifierImpl.tearDownApps(appControls);
    }

    public CallAttributes getDefaultAttributes(TelecomTestApp name, boolean isOutgoing)
            throws Exception {
        return mBaseAppVerifierImpl.getDefaultAttributes(name, isOutgoing);
    }

    public CallAttributes getDefaultAttributes(TelecomTestApp name, PhoneAccountHandle pAH,
            boolean isOutgoing)
            throws Exception {
        return mBaseAppVerifierImpl.getDefaultAttributes(name, pAH, isOutgoing);
    }

    public CallAttributes getDefaultMmiAttributes(TelecomTestApp name, boolean inCallMmi)
            throws Exception {
        return mBaseAppVerifierImpl.getDefaultMmiAttributes(name, inCallMmi);
    }

    public CallAttributes getRandomAttributes(
            TelecomTestApp name, boolean isOutgoing, boolean isHoldable) throws Exception {
        return mBaseAppVerifierImpl.getRandomAttributes(name, isOutgoing, isHoldable);
    }

    public String addOutgoingCallAndVerify(AppControlWrapper appControl)
            throws Exception {
        CallAttributes outgoingAttributes = mBaseAppVerifierImpl.getRandomAttributes(
                appControl.getTelecomApps(),
                true /*isOutgoing*/,
                true /* isHoldable */);
        return mBaseAppVerifierImpl.addCallAndVerify(appControl, outgoingAttributes);
    }

    public String addEmergencyCallAndVerify(
            AppControlWrapper appControl,
            Consumer<CallStateTransitionOperation> consumer,
            int numDisconnectDueToEcc)
            throws Exception {
        CallAttributes emergencyCallAttrs =
                mBaseAppVerifierImpl.getDefaultAttributesForEmergency(appControl.getTelecomApps());
        // ECC has to be placed in the same package as the system dialer (CujInCallService).
        if (isOutgoing(emergencyCallAttrs)) {
            mTelecomManager.placeCall(
                    emergencyCallAttrs.getAddress(), getExtrasWithPhoneAccount(emergencyCallAttrs));
        } else {
            mTelecomManager.addNewIncomingCall(
                    emergencyCallAttrs.getPhoneAccountHandle(),
                    getExtrasWithPhoneAccount(emergencyCallAttrs));
        }
        return mBaseAppVerifierImpl.verifyAddEmergencyCall(
                appControl, emergencyCallAttrs, consumer, numDisconnectDueToEcc);
    }

    public String addIncomingCallAndVerify(AppControlWrapper appControl)
            throws Exception {
        CallAttributes incomingAttributes = mBaseAppVerifierImpl.getRandomAttributes(
                appControl.getTelecomApps(),
                false /*isOutgoing*/,
                true /* isHoldable */);
        return mBaseAppVerifierImpl.addCallAndVerify(appControl, incomingAttributes);
    }

    public String addOutgoingCallAndVerify(AppControlWrapper appControl, boolean isHoldable)
            throws Exception {
        CallAttributes outgoingAttributes = mBaseAppVerifierImpl.getRandomAttributes(
                appControl.getTelecomApps(),
                true /*isOutgoing*/,
                isHoldable /* isHoldable */);
        return mBaseAppVerifierImpl.addCallAndVerify(appControl, outgoingAttributes);
    }

    public String addIncomingCallAndVerify(AppControlWrapper appControl, boolean isHoldable)
            throws Exception {
        CallAttributes incomingAttributes = mBaseAppVerifierImpl.getRandomAttributes(
                appControl.getTelecomApps(),
                false /*isOutgoing*/,
                isHoldable /* isHoldable */);
        return mBaseAppVerifierImpl.addCallAndVerify(appControl, incomingAttributes);
    }

    public String addCallAndVerify(AppControlWrapper appControl, CallAttributes attributes)
            throws Exception {
        return mBaseAppVerifierImpl.addCallAndVerify(appControl, attributes);
    }

    public String addCallAndVerify(AppControlWrapper appControl, CallAttributes attributes,
            Consumer<CallStateTransitionOperation> consumer)
            throws Exception {
        return mBaseAppVerifierImpl.addCallAndVerify(appControl, attributes, consumer);
    }

    public String addCallAndVerifyNewCall(AppControlWrapper appControl, CallAttributes attributes,
            String idToExclude, Consumer<CallStateTransitionOperation> consumer
    ) throws Exception {
        return mBaseAppVerifierImpl.addAndGetNewCall(appControl, attributes, idToExclude, consumer);
    }

    public void addOutgoingCallAndVerifyFailure(AppControlWrapper appControl) throws Exception {
        CallAttributes outgoingAttributes =
                mBaseAppVerifierImpl.getRandomAttributes(
                        appControl.getTelecomApps(), true /*isOutgoing*/, true /* isHoldable */);
        mBaseAppVerifierImpl.addCallAndVerifyFailure(appControl, outgoingAttributes);
    }

    public void addOutgoingCallAndVerifyFailure(
            AppControlWrapper appControl, CallAttributes attributes) throws Exception {
        mBaseAppVerifierImpl.addCallAndVerifyFailure(appControl, attributes);
    }

    public void addFailedCallWithCreateConnectionVerify(
            AppControlWrapper appControl, CallAttributes attributes) throws Exception {
        mBaseAppVerifierImpl.addFailedCallWithCreateConnectionVerify(appControl, attributes);
    }

    public void waitUntilExpectedCallCount(int expectedCallCount) {
        mBaseAppVerifierImpl.waitUntilExpectedCallCount(expectedCallCount);
    }

    public void setCallState(AppControlWrapper appControl, String id, int callState)
            throws Exception {
        mBaseAppVerifierImpl.setCallState(appControl, id, callState);
    }
    public void setCallStateAndVerify(AppControlWrapper appControl, String id, int callState)
            throws Exception {
        mBaseAppVerifierImpl.setCallStateAndVerify(appControl, id, callState);
    }

    public void setCallStateAndVerify(AppControlWrapper appControl, String id, int targetCallState,
                                      int arg) throws Exception {
        mBaseAppVerifierImpl.setCallStateAndVerify(appControl, id, targetCallState, arg);
    }

    public void answerViaInCallService(String id, int videoState) throws Exception {
        mBaseAppVerifierImpl.answerViaInCallService(id, videoState);
    }

    public void answerViaInCallServiceAndVerify(String id, int videoState) throws Exception {
        mBaseAppVerifierImpl.answerViaInCallServiceAndVerify(id, videoState);
    }

    public void holdCallViaInCallService(String id) {
        mBaseAppVerifierImpl.holdCallViaInCallService(id);
    }

    public void unholdCallViaInCallService(String id) {
        mBaseAppVerifierImpl.unholdCallViaInCallService(id);
    }

    public void disconnectCallViaInCallService(String id) {
        mBaseAppVerifierImpl.disconnectCallViaInCallService(id);
    }

    public boolean isCallHoldable(String id) {
        return mBaseAppVerifierImpl.isCallHoldable(id);
    }

    public CallException setCallStateButExpectOnError(AppControlWrapper appControl,
                                                      String id,
                                                      int targetCallState)
            throws Exception {
        return  mBaseAppVerifierImpl.setCallStateButExpectOnError(appControl, id, targetCallState);
    }


    public CallException setCallControlActionButExpectOnError(AppControlWrapper appControl,
                                                              String id,
                                                              int targetCallState,
                                                              int arg) throws Exception {
        return  mBaseAppVerifierImpl.setCallStateButExpectOnError(
                appControl, id, targetCallState, arg);
    }

    public void verifyCallIsInState(String id, int state) throws Exception {
        mBaseAppVerifierImpl.verifyCallIsInState(id, state);
    }

    public void verifyCallExtraPresent(String id, String extraToVerify, boolean expected)
            throws Exception {
        mBaseAppVerifierImpl.verifyCallExtraPresent(id, extraToVerify, expected);
    }

    public CallEndpoint getAnotherCallEndpoint(AppControlWrapper appControl, String id)
            throws Exception {
        return mBaseAppVerifierImpl.getAnotherCallEndpoint(appControl, id);
    }

    public void setAudioRouteStateAndVerify(AppControlWrapper appControl, String id,
                                            CallEndpoint newCallEndpoint) throws Exception {
        mBaseAppVerifierImpl.setAudioRouteStateAndVerify(appControl, id, newCallEndpoint);
    }

    public boolean isMuted(AppControlWrapper appControl, String id) throws RemoteException {
        return mBaseAppVerifierImpl.isMuted(appControl, id);
    }

    public void setMuteState(AppControlWrapper appControl, String id, boolean isMuted)
            throws RemoteException {
        mBaseAppVerifierImpl.setMuteState(appControl, id, isMuted);
    }

    public void sendConnectionEvent(AppControlWrapper appControl, String id, String event)
            throws RemoteException {
        mBaseAppVerifierImpl.sendConnectionEvent(appControl, id, event);
    }

    public CallEndpoint getCurrentCallEndpoint(AppControlWrapper appControl, String id)
            throws Exception {
        return mBaseAppVerifierImpl.getCurrentCallEndpoint(appControl, id);
    }

    public List<CallEndpoint> getAvailableCallEndpoints(AppControlWrapper appControl, String id)
            throws Exception {
        return mBaseAppVerifierImpl.getAvailableCallEndpoints(appControl, id);
    }

    public void registerDefaultPhoneAccount(AppControlWrapper appControl) throws RemoteException {
        mBaseAppVerifierImpl.registerDefaultPhoneAccount(appControl);
    }

    public void registerCustomPhoneAccount(AppControlWrapper appControl, PhoneAccount account)
            throws Exception {
        mBaseAppVerifierImpl.registerCustomPhoneAccount(appControl, account);
    }

    public void unregisterPhoneAccountWithHandle(AppControlWrapper appControl,
            PhoneAccountHandle handle) throws Exception {
        mBaseAppVerifierImpl.unregisterPhoneAccountWithHandle(appControl, handle);
    }

    public List<PhoneAccountHandle> getAccountHandlesForApp(AppControlWrapper appControl)
            throws Exception {
        return mBaseAppVerifierImpl.getAccountHandlesForApp(appControl);
    }

    public void verifyCallPhoneAccount(String id, PhoneAccountHandle handle) {
        mBaseAppVerifierImpl.verifyCallPhoneAccount(id, handle);
    }
    /**
     * Fetch the PhoneAccount associated with the given PhoneAccountHandle
     */
    public List<PhoneAccount> getRegisteredPhoneAccounts(AppControlWrapper appControl)
            throws Exception {
        return appControl.getRegisteredPhoneAccounts();
    }

    public void setUserDefaultPhoneAccountOverride(PhoneAccountHandle handle) throws Exception {
        mBaseAppVerifierImpl.setUserDefaultPhoneAccountOverride(handle);
    }

    public boolean isPhoneAccountRegistered(PhoneAccountHandle handle) {
        return mBaseAppVerifierImpl.isPhoneAccountRegistered(handle);
    }

    public void switchToAnotherCallEndpoint(AppControlWrapper appControl, String callId)
            throws Exception {
        CallEndpoint originalCallEndpoint = getCurrentCallEndpoint(appControl, callId);
        CallEndpoint anotherCallEndpoint = getAnotherCallEndpoint(appControl, callId);
        if (anotherCallEndpoint != null && !originalCallEndpoint.equals(anotherCallEndpoint)) {
            setAudioRouteStateAndVerify(appControl, callId, anotherCallEndpoint);
            // reset the DUT to the original endpoint for cleanup purposes
            setAudioRouteStateAndVerify(appControl, callId, originalCallEndpoint);
        }
    }

    public void assertAudioMode(final int expectedMode) {
        mBaseAppVerifierImpl.assertAudioMode(expectedMode);
    }

    /**
     * NOTIFICATION STUFF
     */

    public void verifyNotificationIsPostedForCall(AppControlWrapper appControl, String callId) {
       mBaseAppVerifierImpl.verifyNotificationPostedForCall(appControl, callId);
    }

    public void removeNotificationForCall(AppControlWrapper appControl, String callId)
            throws RemoteException {
        appControl.removeNotificationForCall(callId);
    }

    /**
     * Checks if foreground service delegation is active for a specific phone account a call was
     * created on.
     *
     * <p>This method determines whether the given phone account is currently delegating its
     * foreground service capabilities to another application. Foreground service delegation allows
     * an app to manage calls and perform call-related operations even when it's not in the
     * foreground.
     *
     * @param appControl The {@link AppControlWrapper} instance representing the application whose
     *     foreground service delegation status is being checked.
     * @param handle The {@link PhoneAccountHandle} identifying the phone account to check.
     * @return {@code true} if foreground service delegation is active for the specified phone
     *     account that is owned by the application, {@code false} otherwise.
     * @throws RemoteException if there is an error communicating with the remote application.
     */
    public boolean isForegroundServiceDelegationActive(
            AppControlWrapper appControl, PhoneAccountHandle handle) throws RemoteException {
        return appControl.isForegroundServiceDelegationActive(handle);
    }

    /**
     * Modifies the existing managed PhoneAccount to include a new PhoneAccount restriction. Must be
     * called after the PhoneAccount was registered as part of {@link #bindToApp(TelecomTestApp)}.
     */
    public void updateManagedPhoneAccountWithRestriction(
            PhoneAccountHandle handle, Set<PhoneAccountHandle> restrictions) throws Exception {
        PhoneAccount acctToUpdate = MANAGED_PHONE_ACCOUNTS.get(handle);
        assertNotNull("setManagedPhoneAccountRestriction: test error, couldn't find PA "
                + "from PAH: " + handle, acctToUpdate);
        PhoneAccount.Builder newAcct = new PhoneAccount.Builder(acctToUpdate);
        if (restrictions == null) {
            newAcct.clearSimultaneousCallingRestriction();
        } else {
            newAcct.setSimultaneousCallingRestriction(restrictions);
        }
        mBaseAppVerifierImpl.registerManagedPhoneAccount(newAcct.build());
    }

    /**
     * Audio focus stuff
     */

    /**
     * Acquire media focus for music playback; pretend we are listening to music so that we can
     * verify that focus is lost during a call and restored later.
     */
    public void acquireAudioFocusForMusic() {
        mBaseAppVerifierImpl.acquireAudioFocusForMusic();
    }

    /** Waits to ensure that the music audio focus was one of the expected values */
    public boolean waitForAndVerifyMusicFocus(boolean verifyPresence, int... expectedValues) {
        return mBaseAppVerifierImpl.waitForAndVerifyMusicFocus(verifyPresence, expectedValues);
    }

    /**
     * Release media focus for media playback; pretend we are not listening to music any longer.
     */
    public void releaseAudioFocusForMusic() {
        mBaseAppVerifierImpl.releaseAudioFocusForMusic();
    }

    /** Setup required to test emergency calling in CUJ. */
    public void setupForEmergencyCalling() throws Exception {
        if (mBaseAppVerifierImpl == null) {
            return;
        }
        mBaseAppVerifierImpl.setupForEmergencyCalling();
    }

    /** Cleanup setup procedure done to test emergency calling. d */
    public void tearDownEmergencyCalling() throws Exception {
        if (mBaseAppVerifierImpl == null) {
            return;
        }
        mBaseAppVerifierImpl.tearDownEmergencyCalling();
    }
}
