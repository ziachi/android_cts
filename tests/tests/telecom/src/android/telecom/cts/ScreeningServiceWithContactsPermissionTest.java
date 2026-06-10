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

package android.telecom.cts;

import static android.telecom.cts.TestUtils.hasTelephonyFeature;
import static android.telecom.cts.TestUtils.shouldTestTelecom;
import static android.telecom.cts.TestUtils.waitOnAllHandlers;

import android.net.Uri;
import android.os.Bundle;
import android.telecom.Call;
import android.telecom.TelecomManager;
import android.telecom.cts.screeningtestapp.CtsCallScreeningService;

import androidx.test.filters.FlakyTest;

/**
 * Tests for third-party {@link android.telecom.CallScreeningService} implementations, focusing on
 * scenarios where the application has the {@link android.Manifest.permission#READ_CONTACTS}
 * permission.
 *
 * <p>This class extends {@link BaseThirdPartyCallScreeningServiceTest} and inherits its common
 * setup and helper methods. It focuses on testing how the presence of the {@code READ_CONTACTS}
 * permission affects call screening behavior, including:
 *
 * <ul>
 *   <li>Accessing contact information during call screening.
 *   <li>Interactions between contact data and call blocking/allowing decisions.
 *   <li>Correct logging of calls from known contacts.
 * </ul>
 *
 * The tests in this class *do not* revoke the {@code READ_CONTACTS} permission. Tests that
 * specifically revoke this permission are located in {@link
 * ScreeningServiceNoContactsPermissionTest}.
 */
public class ScreeningServiceWithContactsPermissionTest
        extends BaseThirdPartyCallScreeningServiceTest {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (!mShouldTestTelecom) {
            return;
        }
        grantReadContactPermission();
    }

    /**
     * Verifies that a {@link android.telecom.CallScreeningService} can reject an incoming call.
     * Ensures that the system logs the blocked call to the call log.
     */
    @FlakyTest // b/400864548 , b/401247439 , b/401246633
    public void testRejectCall() throws Exception {
        if (!shouldTestTelecom(mContext)) {
            return;
        }
        try {
            setupCallScreening();
            // Tell the test app to block the call.
            mCallScreeningControl.setCallResponse(
                    true /* shouldDisallowCall */,
                    true /* shouldRejectCall */,
                    false /* shouldSilenceCall */,
                    false /* shouldSkipCallLog */,
                    true /* shouldSkipNotification */);

            addIncomingAndVerifyBlocked(false /* addContact */);
        } finally {
            resetAndRestoreCallScreening();
        }
    }

    /**
     * Similar to {@link #testRejectCall()}, except the {@link android.telecom.CallScreeningService}
     * tries to skip logging the call to the call log. We verify that Telecom still logs the call to
     * the call log, retaining the API behavior documented in {@link
     * android.telecom.CallScreeningService#respondToCall(Call.Details,
     * CallScreeningService.CallResponse)}
     */
    @FlakyTest // b/400864548 , b/401247439 , b/401246633
    public void testRejectCallAndTryToSkipCallLog() throws Exception {
        if (!shouldTestTelecom(mContext)) {
            return;
        }
        try {
            setupCallScreening();
            // Tell the test app to block the call; also try to skip logging the call.
            mCallScreeningControl.setCallResponse(
                    true /* shouldDisallowCall */,
                    true /* shouldRejectCall */,
                    false /* shouldSilenceCall */,
                    true /* shouldSkipCallLog */,
                    true /* shouldSkipNotification */);

            addIncomingAndVerifyBlocked(false /* addContact */);
        } finally {
            resetAndRestoreCallScreening();
        }
    }

    /**
     * Verifies that a {@link android.telecom.CallScreeningService} set the extra to silence a call.
     */
    @FlakyTest // b/400864548 , b/401247439 , b/401246633
    public void testIncomingCallHasSilenceExtra() throws Exception {
        if (!shouldTestTelecom(mContext)) {
            return;
        }
        try {
            setupCallScreening();
            // Tell the test app to silence the call.
            mCallScreeningControl.setCallResponse(
                    false /* shouldDisallowCall */,
                    false /* shouldRejectCall */,
                    true /* shouldSilenceCall */,
                    false /* shouldSkipCallLog */,
                    false /* shouldSkipNotification */);

            addIncomingAndVerifyCallExtraForSilence(true);
        } finally {
            resetAndRestoreCallScreening();
        }
    }

    /**
     * Verifies that a {@link android.telecom.CallScreeningService} did not set the extra to silence
     * an incoming call.
     */
    @FlakyTest // b/400864548 , b/401247439 , b/401246633
    public void testIncomingCallDoesNotHaveHaveSilenceExtra() throws Exception {
        if (!shouldTestTelecom(mContext)) {
            return;
        }
        try {
            setupCallScreening();
            // Tell the test app to not silence the call.
            mCallScreeningControl.setCallResponse(
                    false /* shouldDisallowCall */,
                    false /* shouldRejectCall */,
                    false /* shouldSilenceCall */,
                    false /* shouldSkipCallLog */,
                    false /* shouldSkipNotification */);

            addIncomingAndVerifyCallExtraForSilence(false);
        } finally {
            resetAndRestoreCallScreening();
        }
    }

    @FlakyTest // b/400864548 , b/401247439 , b/401246633
    public void testHasPermissionAndNoContactIncoming() throws Exception {
        if (!shouldTestTelecom(mContext)) {
            return;
        }
        try {
            setupCallScreening();
            verifyPermission(true);
            // Tell the test app to block the call.
            mCallScreeningControl.setCallResponse(
                    true /* shouldDisallowCall */,
                    true /* shouldRejectCall */,
                    false /* shouldSilenceCall */,
                    false /* shouldSkipCallLog */,
                    true /* shouldSkipNotification */);
            addIncomingAndVerifyBlocked(false /* addContact */);
        } finally {
            resetAndRestoreCallScreening();
        }
    }

    @FlakyTest // b/400864548 , b/401247439 , b/401246633
    public void testHasPermissionAndHasContactIncoming() throws Exception {
        if (!shouldTestTelecom(mContext)) {
            return;
        }
        try {
            setupCallScreening();
            verifyPermission(true);
            mCallScreeningControl.setCallResponse(
                    true /* shouldDisallowCall */,
                    true /* shouldRejectCall */,
                    false /* shouldSilenceCall */,
                    false /* shouldSkipCallLog */,
                    true /* shouldSkipNotification */);
            addIncomingAndVerifyBlocked(true /* addContact */);
        } finally {
            resetAndRestoreCallScreening();
        }
    }

    @FlakyTest // b/400864548 , b/401247439 , b/401246633
    public void testHasPermissionAndNoContactOutgoing() throws Exception {
        if (!shouldTestTelecom(mContext)) {
            return;
        }
        try {
            setupCallScreening();
            verifyPermission(true);
            placeOutgoingCall(false /* addContact */);
            assertTrue(mCallScreeningControl.waitForBind());
        } finally {
            resetAndRestoreCallScreening();
        }
    }

    @FlakyTest // b/400864548 , b/401247439 , b/401246633
    public void testHasPermissionAndHasContactOutgoing() throws Exception {
        if (!shouldTestTelecom(mContext)) {
            return;
        }
        try {
            setupCallScreening();
            verifyPermission(true);
            placeOutgoingCall(true /* addContact */);
            assertTrue(mCallScreeningControl.waitForBind());
        } finally {
            resetAndRestoreCallScreening();
        }
    }

    @FlakyTest // b/400864548 , b/401247439 , b/401246633
    public void testNoPostCallActivityWithoutRole() throws Exception {
        if (!shouldTestTelecom(mContext)) {
            return;
        }
        try {
            setupCallScreening();
            removeRoleHolder(
                    ROLE_CALL_SCREENING, CtsCallScreeningService.class.getPackage().getName());
            addIncomingAndVerifyAllowed(false);
            assertFalse(mCallScreeningControl.waitForActivity());
        } finally {
            resetAndRestoreCallScreening();
        }
    }

    @FlakyTest // b/400864548 , b/401247439 , b/401246633
    public void testAllowCall() throws Exception {
        if (!mShouldTestTelecom || !hasTelephonyFeature(mContext)) {
            return;
        }
        try {
            setupCallScreening();
            mCallScreeningControl.setCallResponse(
                    false /* shouldDisallowCall */,
                    false /* shouldRejectCall */,
                    false /* shouldSilenceCall */,
                    false /* shouldSkipCallLog */,
                    false /* shouldSkipNotification */);
            addIncomingAndVerifyAllowed(false /* addContact */);
            assertTrue(mCallScreeningControl.waitForActivity());
        } finally {
            resetAndRestoreCallScreening();
        }
    }

    @FlakyTest // b/400864548 , b/401247439 , b/401246633
    public void testNoPostCallActivityWhenBlocked() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        try {
            setupCallScreening();
            mCallScreeningControl.setCallResponse(
                    true /* shouldDisallowCall */,
                    true /* shouldRejectCall */,
                    false /* shouldSilenceCall */,
                    false /* shouldSkipCallLog */,
                    true /* shouldSkipNotification */);
            addIncomingAndVerifyBlocked(false /* addContact */);
            assertFalse(mCallScreeningControl.waitForActivity());
        } finally {
            resetAndRestoreCallScreening();
        }
    }

    @FlakyTest // b/400864548 , b/401247439 , b/401246633
    public void testNoPostCallActivityWhenAudioProcessing() throws Exception {
        if (!shouldTestTelecom(mContext)) {
            return;
        }
        try {
            setupCallScreening();
            mCallScreeningControl.setCallResponse(
                    false /* shouldDisallowCall */,
                    false /* shouldRejectCall */,
                    false /* shouldSilenceCall */,
                    false /* shouldSkipCallLog */,
                    false /* shouldSkipNotification */);
            Uri testNumber = createRandomTestNumber();
            Bundle extras = new Bundle();
            extras.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, testNumber);
            mTelecomManager.addNewIncomingCall(TestUtils.TEST_PHONE_ACCOUNT_HANDLE, extras);

            // Wait until the new incoming call is processed.
            waitOnAllHandlers(getInstrumentation());

            assertEquals(1, mInCallCallbacks.getService().getCallCount());
            Call call = mInCallCallbacks.getService().getLastCall();
            call.enterBackgroundAudioProcessing();

            waitOnAllHandlers(getInstrumentation());
            mInCallCallbacks.getService().disconnectAllCalls();
            assertFalse(mCallScreeningControl.waitForActivity());
        } finally {
            resetAndRestoreCallScreening();
        }
    }

    @FlakyTest // b/400864548 , b/401247439 , b/401246633
    public void testNoPostCallActivityForOutgoingEmergencyCall() throws Exception {
        if (!shouldTestTelecom(mContext) || !hasTelephonyFeature(mContext)) {
            return;
        }
        try {
            setupCallScreening();
            setupForEmergencyCalling(TEST_EMERGENCY_NUMBER);
            Bundle extras = new Bundle();
            extras.putParcelable(TestUtils.EXTRA_PHONE_NUMBER, TEST_EMERGENCY_URI);
            placeAndVerifyCall(extras);

            // Wait until the new incoming call is processed.
            waitOnAllHandlers(getInstrumentation());
            mInCallCallbacks.getService().disconnectAllCalls();
            assertFalse(mCallScreeningControl.waitForActivity());
        } finally {
            resetAndRestoreCallScreening();
        }
    }

    @FlakyTest // b/400864548 , b/401247439 , b/401246633
    public void testNoPostCallActivityForIncomingEmergencyCall() throws Exception {
        if (!shouldTestTelecom(mContext) || !hasTelephonyFeature(mContext)) {
            return;
        }
        try {
            setupCallScreening();
            setupForEmergencyCalling(TEST_EMERGENCY_NUMBER);
            mCallScreeningControl.setCallResponse(
                    false /* shouldDisallowCall */,
                    false /* shouldRejectCall */,
                    false /* shouldSilenceCall */,
                    false /* shouldSkipCallLog */,
                    false /* shouldSkipNotification */);
            Bundle extras = new Bundle();
            extras.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, TEST_EMERGENCY_URI);
            extras.putBoolean(EXTRA_NETWORK_IDENTIFIED_EMERGENCY_CALL, true);
            mTelecomManager.addNewIncomingCall(TestUtils.TEST_PHONE_ACCOUNT_HANDLE, extras);

            // Wait until the new incoming call is processed.
            waitOnAllHandlers(getInstrumentation());
            mInCallCallbacks.getService().disconnectAllCalls();

            assertFalse(mCallScreeningControl.waitForActivity());
        } finally {
            resetAndRestoreCallScreening();
        }
    }
}
