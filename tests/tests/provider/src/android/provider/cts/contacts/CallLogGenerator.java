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

package android.provider.cts.contacts;

import static android.provider.CallLog.Calls.CONTENT_URI;
import static android.provider.CallLog.Calls.MISSED_REASON_NOT_MISSED;
import static android.provider.CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME;
import static android.provider.CallLog.Calls.PHONE_ACCOUNT_ID;
import static android.provider.CallLog.Calls.PRESENTATION_ALLOWED;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CallLog;
import android.telecom.CallerInfo;
import android.telecom.PhoneAccountHandle;

import java.util.Random;

/** A generator that generates random call log entries for testing purposes. */
public final class CallLogGenerator {
    private static final String NAME_CHARACTERS =
            "abcdefghijklmnopqrstuvwxyz-ABCDEFGHIJKLMNOPQRSTUVWXYZ_0123456789.";
    private static final String NUMBER_CHARACTERS = "0123456789";

    private static final int MAX_NAME_SIZE = 24;
    private static final int MAX_PHONE_NUMBER_SIZE = 12;
    private static final int MAX_DURATION_SECONDS = 300;
    private static final int MAX_PHONE_TYPE = 3;

    private final Random mRandom = new Random();
    private final Context mContext;

    /** A constructor with a provided context. */
    public CallLogGenerator(Context context) {
        mContext = context;
    }

    /**
     * Generates a specified number of call logs for a given phone account.
     *
     * @param phoneAccountHandle The handle to the phone account for which to generate call logs.
     * @param size The number of call logs to generate.
     */
    public void generateCallLogs(PhoneAccountHandle phoneAccountHandle, int size) {
        for (int i = 0; i < size; i++) {
            addCallLog(phoneAccountHandle);
        }
    }

    /**
     * Adds a random call log entry.
     * @param phoneAccountHandle The phone account associated with the call.
     * @return The URI of the newly created call log entry.
     */
    public Uri addCallLog(PhoneAccountHandle phoneAccountHandle) {
        final CallerInfo callerInfo = new CallerInfo();
        callerInfo.setName(getRandomName());
        return CallLog.Calls.addCall(
                callerInfo,
                mContext,
                getRandomPhoneNumber(),
                PRESENTATION_ALLOWED,
                getRandomPhoneType(),
                0 /* features */,
                phoneAccountHandle,
                System.currentTimeMillis(),
                getRandomDuration(),
                null /* dataUsage */,
                MISSED_REASON_NOT_MISSED,
                0 /* isPhoneAccountMigrationPending */);
    }

    /**
     * Deletes call logs associated with a specific phone account.
     *
     * @param phoneAccountHandle The phone account associated with the call.
     * @return The number of call log entries deleted.
     */
    public int deleteCallLogs(PhoneAccountHandle phoneAccountHandle) {
        final String accountComponentString =
                phoneAccountHandle.getComponentName().flattenToString();
        final String accountId = phoneAccountHandle.getId();
        return mContext.getContentResolver().delete(
                CONTENT_URI,
                PHONE_ACCOUNT_COMPONENT_NAME + " = ? AND " + PHONE_ACCOUNT_ID + " = ?",
                new String[] {accountComponentString, accountId});
    }

    /**
     * Get the number of call log entries with a specific phone account.
     *
     * @param phoneAccountHandle The phone account associated with the call.
     * @return The number of call log entries.
     */
    public int getCallLogSize(PhoneAccountHandle phoneAccountHandle) {
        final String accountComponentString =
                phoneAccountHandle.getComponentName().flattenToString();
        final String accountId = phoneAccountHandle.getId();
        try (Cursor cursor = mContext.getContentResolver().query(
                CONTENT_URI,
                new String[] {"_id"},
                PHONE_ACCOUNT_COMPONENT_NAME + " = ? AND " + PHONE_ACCOUNT_ID + " = ?",
                new String[] {accountComponentString, accountId},
                null /* sortOrder */)) {
            return cursor != null ? cursor.getCount() : 0;
        }
    }

    private String getRandomPhoneNumber() {
        final int length = mRandom.nextInt(MAX_PHONE_NUMBER_SIZE) + 1;
        return randomString(NUMBER_CHARACTERS, length);
    }

    private String getRandomName() {
        final int length = mRandom.nextInt(MAX_NAME_SIZE) + 1;
        return randomString(NAME_CHARACTERS, length);
    }

    private int getRandomPhoneType() {
        return mRandom.nextInt(MAX_PHONE_TYPE) + 1;
    }

    private int getRandomDuration() {
        return mRandom.nextInt(MAX_DURATION_SECONDS) + 1;
    }

    private String randomString(String characters, int length) {
        final char[] bytes = new char[length];
        for (int i = 0; i < length; ++i) {
            final int charIx = mRandom.nextInt(characters.length());
            bytes[i] = characters.charAt(charIx);
        }
        return new String(bytes);
    }
}
