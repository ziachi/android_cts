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

import static android.telecom.cts.TestUtils.shouldTestTelecom;

import androidx.test.filters.FlakyTest;

/**
 * Tests for third-party {@link android.telecom.CallScreeningService} implementations, focusing on
 * scenarios where the application *does not* have the {@link
 * android.Manifest.permission#READ_CONTACTS} permission.
 *
 * <p>This class extends {@link BaseThirdPartyCallScreeningServiceTest} and inherits its common
 * setup and helper methods. It specifically revokes the {@code READ_CONTACTS} permission in its
 * {@link #setUp()} method before each test. The tests verify the behavior of the {@link
 * android.telecom.CallScreeningService} when it cannot access contact information, including:
 *
 * <ul>
 *   <li>Call blocking/allowing behavior when contacts permission is denied.
 *   <li>Whether the CallScreeningService is invoked for outgoing calls when contacts permission is
 *       missing.
 *   <li>Correct logging of calls when contacts cannot be accessed.
 * </ul>
 *
 * Tests that *do* have the contact permission are in {@link
 * ScreeningServiceWithContactsPermissionTest}.
 */
public class ScreeningServiceNoContactsPermissionTest
        extends BaseThirdPartyCallScreeningServiceTest {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (!mShouldTestTelecom) {
            return;
        }
        revokeReadContactPermission();
    }

    /**
     * Tests that an incoming call from a number *not* in contacts is blocked when READ_CONTACTS
     * permission is revoked and the CallScreeningService is set to reject.
     */
    @FlakyTest // b/400864548 , b/401247439 , b/401246633
    public void testNoPermissionAndNoContactIncoming() throws Exception {
        if (!shouldTestTelecom(mContext)) {
            return;
        }
        try {
            setupCallScreening();
            verifyPermission(false); // Verify permission is indeed revoked
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
     * Tests that an incoming call from a number *in* contacts is *allowed* when READ_CONTACTS
     * permission is revoked. The CallScreeningService should not be able to access the contact
     * information and therefore cannot block based on contact data.
     */
    @FlakyTest // b/400864548 , b/401247439 , b/401246633
    public void testNoPermissionAndHasContactIncoming() throws Exception {
        if (!shouldTestTelecom(mContext) || !TestUtils.hasTelephonyFeature(mContext)) {
            return;
        }
        try {
            setupCallScreening();
            verifyPermission(false); // Verify permission is indeed revoked
            mCallScreeningControl.setCallResponse(
                    true /* shouldDisallowCall */,
                    true /* shouldRejectCall */,
                    false /* shouldSilenceCall */,
                    false /* shouldSkipCallLog */,
                    true /* shouldSkipNotification */);
            addIncomingAndVerifyAllowed(true /* addContact */);
        } finally {
            resetAndRestoreCallScreening();
        }
    }

    /**
     * Tests that the {@link android.telecom.CallScreeningService} *is* still invoked for outgoing
     * calls when READ_CONTACTS permission is revoked, for a number that is *not* a contact.
     */
    @FlakyTest // b/400864548 , b/401247439 , b/401246633
    public void testNoPermissionAndNoContactOutgoing() throws Exception {
        if (!shouldTestTelecom(mContext)) {
            return;
        }
        try {
            setupCallScreening();
            verifyPermission(false); // Verify permission is indeed revoked
            placeOutgoingCall(false /* addContact */);
            assertTrue(mCallScreeningControl.waitForBind());
        } finally {
            resetAndRestoreCallScreening();
        }
    }

    /**
     * Tests that the {@link android.telecom.CallScreeningService} is *not* invoked for outgoing
     * calls to known contacts when READ_CONTACTS permission is revoked. Since Telecom can determine
     * that the number is a contact *without* needing to ask the CallScreeningService, it bypasses
     * the service.
     */
    @FlakyTest // b/400864548 , b/401247439 , b/401246633
    public void testNoPermissionAndHasContactOutgoing() throws Exception {
        if (!shouldTestTelecom(mContext) || !TestUtils.hasTelephonyFeature(mContext)) {
            return;
        }
        try {
            setupCallScreening();
            verifyPermission(false); // Verify permission is indeed revoked
            placeOutgoingCall(true /* addContact */);
            assertFalse(mCallScreeningControl.waitForBind());
        } finally {
            resetAndRestoreCallScreening();
        }
    }
}
