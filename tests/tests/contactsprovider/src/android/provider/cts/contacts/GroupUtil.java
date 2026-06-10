/*
 * Copyright (C) 2013 The Android Open Source Project
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

import static org.junit.Assert.assertNotNull;

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Groups;

/**
 * Utility class for interacting with the Contacts Provider's Groups table.
 */
public class GroupUtil {
    private static final Uri URI = ContactsContract.Groups.CONTENT_URI;

    /**
     * Inserts a group without an associated account into the Contacts Provider.
     *
     * @param resolver The {@link ContentResolver} to use for the insertion.
     * @return The ID of the newly inserted group.
     */
    public static Long insertGroupWithoutAccount(ContentResolver resolver) {
        ContentValues values = new ContentValues();
        values.put(Groups.TITLE, "group a");
        Uri uri = resolver.insert(URI, values);
        assertNotNull(uri);

        return ContentUris.parseId(uri);
    }

    /**
     * Inserts a group with an associated account into the Contacts Provider.
     *
     * @param resolver The {@link ContentResolver} to use for the insertion.
     * @param account The {@link Account} to associate with the group.
     * @return The ID of the newly inserted group.
     */
    public static Long insertGroupWithAccount(ContentResolver resolver, Account account) {
        ContentValues values = new ContentValues();
        values.put(Groups.TITLE, "group a");
        if (account == null) {
            values.put(ContactsContract.Groups.ACCOUNT_NAME, (String) null);
            values.put(ContactsContract.Groups.ACCOUNT_TYPE, (String) null);
        } else {
            values.put(ContactsContract.Groups.ACCOUNT_NAME, account.name);
            values.put(ContactsContract.Groups.ACCOUNT_TYPE, account.type);
        }
        values.put(ContactsContract.Groups.DATA_SET, (String) null);
        Uri uri = resolver.insert(ContactsContract.Groups.CONTENT_URI, values);
        assertNotNull(uri);
        return ContentUris.parseId(uri);
    }

    /**
     * Queries the Contacts Provider for a group with the given ID.
     *
     * @param resolver The {@link ContentResolver} to use for the query.
     * @param groupId The ID of the group to query.
     * @param projection The columns to include in the query result.
     * @return A {@code String[]} containing the values of the requested columns for the group.
     */
    public static String[] queryByGroupId(ContentResolver resolver,
            long groupId, String[] projection) {
        Uri uri = ContentUris.withAppendedId(URI, groupId);
        Cursor cursor = resolver.query(uri, projection, null, null, null);
        return CommonDatabaseUtils.singleRecordToArray(cursor);
    }

    /**
     * Checks if a group with the given ID exists in the Contacts Provider.
     *
     * @param resolver The {@link ContentResolver} to use for the query.
     * @param groupId The ID of the group to check for.
     * @return {@code true} if a group with the given ID exists, {@code false} otherwise.
     */
    public static boolean groupExistsById(ContentResolver resolver, long groupId) {
        String[] projection = new String[]{Groups._ID}; // Only need to check for the ID
        Uri uri = ContentUris.withAppendedId(URI, groupId);
        Cursor cursor = resolver.query(uri, projection, null, null, null);
        if (cursor != null) {
            try {
                return cursor.moveToFirst(); // Returns true if a record exists
            } finally {
                cursor.close();
            }
        }
        return false;
    }

    /**
     * Updates the account associated with a group in the Contacts Provider.
     *
     * @param resolver The {@link ContentResolver} to use for the update.
     * @param groupId The ID of the group to update.
     * @param accountUpdateTo The new {@link Account} to associate with the group.
     * @return The number of rows updated.
     */
    public static long updateGroupAccount(ContentResolver resolver, long groupId,
            Account accountUpdateTo) {
        ContentValues values = new ContentValues();
        if (accountUpdateTo == null) {
            values.put(ContactsContract.RawContacts.ACCOUNT_NAME, (String) null);
            values.put(ContactsContract.RawContacts.ACCOUNT_TYPE, (String) null);
        } else {
            values.put(ContactsContract.RawContacts.ACCOUNT_NAME, accountUpdateTo.name);
            values.put(ContactsContract.RawContacts.ACCOUNT_TYPE, accountUpdateTo.type);
        }
        return update(resolver, groupId, values);
    }

    /**
     * Updates a group in the Contacts Provider with the given values.
     *
     * @param resolver The {@link ContentResolver} to use for the update.
     * @param groupId The ID of the group to update.
     * @param values The new values for the group.
     * @return The number of rows updated.
     */
    public static int update(ContentResolver resolver, long groupId,
            ContentValues values) {
        Uri uri = ContentUris.withAppendedId(URI, groupId);
        return resolver.update(uri, values, null, null);
    }
}
