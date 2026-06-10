/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Directory;
import android.provider.ContactsContract.Groups;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.Settings;
import android.provider.cts.contacts.ContactsContract_TestDataBuilder.TestContact;
import android.provider.cts.contacts.ContactsContract_TestDataBuilder.TestGroup;
import android.provider.cts.contacts.ContactsContract_TestDataBuilder.TestRawContact;
import android.provider.cts.contacts.account.StaticAccountAuthenticator;
import android.test.AndroidTestCase;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ContactsContract_ContactsTest extends AndroidTestCase {

    private StaticAccountAuthenticator mAuthenticator;
    private ContentResolver mResolver;
    private ContactsContract_TestDataBuilder mBuilder;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mResolver = getContext().getContentResolver();
        ContentProviderClient provider =
                mResolver.acquireContentProviderClient(ContactsContract.AUTHORITY);
        mBuilder = new ContactsContract_TestDataBuilder(provider);

        mAuthenticator = new StaticAccountAuthenticator(getContext());
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        mBuilder.cleanup();
    }

    public void testMarkAsContacted() throws Exception {
        TestRawContact rawContact = mBuilder.newRawContact().insert().load();
        TestContact contact = rawContact.getContact().load();

        assertEquals(0, contact.getLong(Contacts.LAST_TIME_CONTACTED));
        assertEquals(0, contact.getLong(Contacts.TIMES_CONTACTED));

        assertEquals(0, rawContact.getLong(Contacts.LAST_TIME_CONTACTED));
        assertEquals(0, rawContact.getLong(Contacts.TIMES_CONTACTED));

        // Note we no longer support contact affinity as of Q, so times_contacted and
        // last_time_contacted are always 0.

        for (int i = 1; i < 10; i++) {
            Contacts.markAsContacted(mResolver, contact.getId());
            contact.load();
            rawContact.load();

            assertEquals(0, contact.getLong(Contacts.LAST_TIME_CONTACTED));
            assertEquals("#" + i, 0, contact.getLong(Contacts.TIMES_CONTACTED));

            assertEquals(0, rawContact.getLong(Contacts.LAST_TIME_CONTACTED));
            assertEquals("#" + i, 0, rawContact.getLong(Contacts.TIMES_CONTACTED));
        }
    }

    public void testContentUri() {
        Context context = getContext();
        PackageManager packageManager = context.getPackageManager();
        Intent intent = new Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI);
        List<ResolveInfo> resolveInfos = packageManager.queryIntentActivities(intent, 0);
        assertFalse("Device does not support the activity intent: " + intent,
                resolveInfos.isEmpty());
    }

    public void testLookupUri() throws Exception {
        TestRawContact rawContact = mBuilder.newRawContact().insert().load();
        TestContact contact = rawContact.getContact().load();

        Uri contactUri = contact.getUri();
        long contactId = contact.getId();
        String lookupKey = contact.getString(Contacts.LOOKUP_KEY);

        Uri lookupUri = Contacts.getLookupUri(contactId, lookupKey);
        assertEquals(ContentUris.withAppendedId(Uri.withAppendedPath(Contacts.CONTENT_LOOKUP_URI,
                lookupKey), contactId), lookupUri);

        Uri nullLookupUri = Contacts.getLookupUri(contactId, null);
        assertNull(nullLookupUri);

        Uri emptyLookupUri = Contacts.getLookupUri(contactId, "");
        assertNull(emptyLookupUri);

        Uri lookupUri2 = Contacts.getLookupUri(mResolver, contactUri);
        assertEquals(lookupUri, lookupUri2);

        Uri contactUri2 = Contacts.lookupContact(mResolver, lookupUri);
        assertEquals(contactUri, contactUri2);
    }

    public void testQueryByAccount() throws Exception {
        String accountName = "ContactsTest_testQueryByAccount_accountName";
        String accountType = "ContactsTest_testQueryByAccount_accountType";
        String accountDataset = "ContactsTest_testQueryByAccount_dataset";

        TestRawContact rawContact = mBuilder.newRawContact()
                .with(RawContacts.ACCOUNT_NAME, accountName)
                .with(RawContacts.ACCOUNT_TYPE, accountType)
                .with(RawContacts.DATA_SET, accountDataset)
                .insert().load();
        TestContact contact = rawContact.getContact().load();
        long contactId = contact.getId();

        // verify we can also get contact with no filters, or account filters for it's raw contact
        assertContactFound(Contacts.CONTENT_URI, contactId);
        assertContactFound(Contacts.CONTENT_URI.buildUpon()
                .appendQueryParameter(RawContacts.ACCOUNT_NAME, accountName)
                .appendQueryParameter(RawContacts.ACCOUNT_TYPE, accountType)
                .appendQueryParameter(RawContacts.DATA_SET, accountDataset)
                .build(), contactId);


        // verify we can also filter the contact id out by filtering out the raw contacts on it
        assertContactNotFound(Contacts.CONTENT_URI.buildUpon()
                .appendQueryParameter(RawContacts.ACCOUNT_NAME, "a")
                .appendQueryParameter(RawContacts.ACCOUNT_TYPE, accountType)
                .appendQueryParameter(RawContacts.DATA_SET, accountDataset)
                .build(), contactId);
        assertContactNotFound(Contacts.CONTENT_URI.buildUpon()
                .appendQueryParameter(RawContacts.ACCOUNT_NAME, accountName)
                .appendQueryParameter(RawContacts.ACCOUNT_TYPE, "b")
                .appendQueryParameter(RawContacts.DATA_SET, accountDataset)
                .build(), contactId);
        assertContactNotFound(Contacts.CONTENT_URI.buildUpon()
                .appendQueryParameter(RawContacts.ACCOUNT_NAME, accountName)
                .appendQueryParameter(RawContacts.ACCOUNT_TYPE, accountType)
                .appendQueryParameter(RawContacts.DATA_SET, "c")
                .build(), contactId);
        assertContactNotFound(Contacts.CONTENT_URI.buildUpon()
                .appendQueryParameter(RawContacts.ACCOUNT_NAME, accountName)
                .appendQueryParameter(RawContacts.ACCOUNT_TYPE, accountType)
                .build(), contactId);
    }

    public void testQueryByAccountWithNullDataSet() throws Exception {
        String accountName = "ContactsTest_testQueryByAccountWithNullDataSet_accountName";
        String accountType = "ContactsTest_testQueryByAccountWithNullDataSet_accountType";
        TestRawContact rawContact = mBuilder.newRawContact()
                .with(RawContacts.ACCOUNT_NAME, accountName)
                .with(RawContacts.ACCOUNT_TYPE, accountType)
                .insert().load();
        TestContact contact = rawContact.getContact().load();
        long contactId = contact.getId();

        // verify we can also get contact with no filters, or account filters for it's raw contact
        assertContactFound(Contacts.CONTENT_URI, contactId);
        assertContactFound(Contacts.CONTENT_URI.buildUpon()
                .appendQueryParameter(RawContacts.ACCOUNT_NAME, accountName)
                .appendQueryParameter(RawContacts.ACCOUNT_TYPE, accountType)
                .build(), contactId);


        // verify we can also filter the contact id out by filtering out the raw contacts on it
        assertContactNotFound(Contacts.CONTENT_URI.buildUpon()
                .appendQueryParameter(RawContacts.ACCOUNT_NAME, "a")
                .appendQueryParameter(RawContacts.ACCOUNT_TYPE, accountType)
                .build(), contactId);
        assertContactNotFound(Contacts.CONTENT_URI.buildUpon()
                .appendQueryParameter(RawContacts.ACCOUNT_NAME, accountName)
                .appendQueryParameter(RawContacts.ACCOUNT_TYPE, "b")
                .build(), contactId);
        assertContactNotFound(Contacts.CONTENT_URI.buildUpon()
                .appendQueryParameter(RawContacts.ACCOUNT_NAME, accountName)
                .appendQueryParameter(RawContacts.ACCOUNT_TYPE, accountType)
                .appendQueryParameter(RawContacts.DATA_SET, "c")
                .build(), contactId);
    }

    public void testQueryVisibleContacts() throws Exception {
        Set<String> visibleContactNames = new HashSet<>();
        Set<String> notVisibleContactNames = new HashSet<>();
        String account1Name = "ContactsTest_testQueryVisibleContacts_account1";
        String account2Name = "ContactsTest_testQueryVisibleContacts_account2";
        // Set Settings.UNGROUPED_VISIBLE to false for account1
        ContentValues values = new ContentValues();
        values.put(Settings.UNGROUPED_VISIBLE, 0);
        mResolver.insert(
                Settings.CONTENT_URI
                        .buildUpon()
                        .appendQueryParameter(Settings.ACCOUNT_NAME, account1Name)
                        .appendQueryParameter(
                                Settings.ACCOUNT_TYPE, StaticAccountAuthenticator.TYPE)
                        .build(),
                values);
        // Create an ungrouped raw contact in account1.
        TestRawContact rawContact1 = createRawContactWithName(account1Name, "Contact1 NotVisible");
        notVisibleContactNames.add(rawContact1.getString(RawContacts.DISPLAY_NAME_PRIMARY));
        // Set Settings.UNGROUPED_VISIBLE to true for the account2
        values.put(Settings.UNGROUPED_VISIBLE, 1);
        mResolver.insert(
                Settings.CONTENT_URI
                        .buildUpon()
                        .appendQueryParameter(Settings.ACCOUNT_NAME, account2Name)
                        .appendQueryParameter(
                                Settings.ACCOUNT_TYPE, StaticAccountAuthenticator.TYPE)
                        .build(),
                values);
        // Create an ungrouped raw contact in account2.
        TestRawContact rawContact2 = createRawContactWithName(account2Name, "Contact2 Visible");
        visibleContactNames.add(rawContact2.getString(RawContacts.DISPLAY_NAME_PRIMARY));
        // Create a visible group with one contact in account1
        TestGroup visibleGroup1 =
                mBuilder.newGroup()
                        .with(Groups.ACCOUNT_NAME, account1Name)
                        .with(Groups.ACCOUNT_TYPE, StaticAccountAuthenticator.TYPE)
                        .with(Groups.TITLE, "group1")
                        .with(Groups.GROUP_VISIBLE, 1)
                        .insert();
        TestRawContact rawContact3 = createRawContactWithName(account1Name, "Contact3 Visible");
        addToGroup(visibleGroup1, rawContact3);
        visibleContactNames.add(rawContact3.getString(RawContacts.DISPLAY_NAME_PRIMARY));
        // Create a visible group with one contact in account2
        TestGroup visibleGroup2 =
                mBuilder.newGroup()
                        .with(Groups.ACCOUNT_NAME, account2Name)
                        .with(Groups.ACCOUNT_TYPE, StaticAccountAuthenticator.TYPE)
                        .with(Groups.TITLE, "group2")
                        .with(Groups.GROUP_VISIBLE, 1)
                        .insert();
        TestRawContact rawContact4 = createRawContactWithName(account2Name, "Contact4 Visible");
        addToGroup(visibleGroup2, rawContact4);
        visibleContactNames.add(rawContact4.getString(RawContacts.DISPLAY_NAME_PRIMARY));
        // Create a non-visible group with one contact in account1
        TestGroup notVisibleGroup1 =
                mBuilder.newGroup()
                        .with(Groups.ACCOUNT_NAME, account1Name)
                        .with(Groups.ACCOUNT_TYPE, StaticAccountAuthenticator.TYPE)
                        .with(Groups.TITLE, "hiddenGroup1")
                        .with(Groups.GROUP_VISIBLE, 0)
                        .insert();
        TestRawContact rawContact5 = createRawContactWithName(account1Name, "Contact5 NotVisible");
        addToGroup(notVisibleGroup1, rawContact5);
        notVisibleContactNames.add(rawContact5.getString(RawContacts.DISPLAY_NAME_PRIMARY));
        // Create a non-visible group with one contact in account2
        TestGroup notVisibleGroup2 =
                mBuilder.newGroup()
                        .with(Groups.ACCOUNT_NAME, account2Name)
                        .with(Groups.ACCOUNT_TYPE, StaticAccountAuthenticator.TYPE)
                        .with(Groups.TITLE, "hiddenGroup2")
                        .with(Groups.GROUP_VISIBLE, 0)
                        .insert();
        TestRawContact rawContact6 = createRawContactWithName(account2Name, "Contact6 NotVisible");
        addToGroup(notVisibleGroup2, rawContact6);
        notVisibleContactNames.add(rawContact6.getString(RawContacts.DISPLAY_NAME_PRIMARY));

        Set<String> actualContactNames = new HashSet<>();
        try (Cursor cursor =
                mResolver.query(
                        Contacts.CONTENT_URI,
                        new String[] {Contacts.DISPLAY_NAME_PRIMARY},
                        Contacts.IN_VISIBLE_GROUP + "= ?",
                        new String[] {"1"},
                        null,
                        null)) {
            while (cursor.moveToNext()) {
                actualContactNames.add(cursor.getString(0));
            }
        }

        assertTrue(
                "Expected to contain all of: "
                        + visibleContactNames
                        + " Actual: "
                        + actualContactNames,
                actualContactNames.containsAll(visibleContactNames));
        assertTrue(
                "Expected to contain none of: "
                        + notVisibleContactNames
                        + " Actual: "
                        + actualContactNames,
                Collections.disjoint(actualContactNames, notVisibleContactNames));
    }

    public void testInsert_isUnsupported() {
        DatabaseAsserts.assertInsertIsUnsupported(mResolver, Contacts.CONTENT_URI);
    }

    public void testContactDelete_removesContactRecord() {
        assertContactCreateDelete();
    }

    public void testContactDelete_hasDeleteLog() {
        long start = System.currentTimeMillis();
        DatabaseAsserts.ContactIdPair ids = assertContactCreateDelete();
        DatabaseAsserts.assertHasDeleteLogGreaterThan(mResolver, ids.mContactId, start);

        // Clean up. Must also remove raw contact.
        RawContactUtil.delete(mResolver, ids.mRawContactId, true);
    }

    public void testContactDelete_marksRawContactsForDeletion() {
        DatabaseAsserts.ContactIdPair ids = assertContactCreateDelete();

        String[] projection = new String[] {
                RawContacts.DIRTY,
                RawContacts.DELETED
        };
        List<String[]> records = RawContactUtil.queryByContactId(mResolver, ids.mContactId,
                projection);
        for (String[] arr : records) {
            assertEquals("1", arr[0]);
            assertEquals("1", arr[1]);
        }

        // Clean up
        RawContactUtil.delete(mResolver, ids.mRawContactId, true);
    }

    public void testContactDelete_localContactDeletedImmediately() {
        // Create a raw contact in the local (null) account
        DatabaseAsserts.ContactIdPair ids = DatabaseAsserts.assertAndCreateContact(
                mResolver, null);

        ContactUtil.delete(mResolver, ids.mContactId);

        // Assert that the local raw contact is removed from the database and
        // not merely marked DELETED=1.
        assertNull(RawContactUtil.queryByRawContactId(mResolver, ids.mRawContactId, null));

        // Nothing to clean up
    }

    public void testContactDelete_allLocalContactsDeletedImmediately() {
        // Create two raw contacts in the local (null) account
        DatabaseAsserts.ContactIdPair ids1 = DatabaseAsserts.assertAndCreateContactWithName(
                mResolver, null, "John Smith");
        DatabaseAsserts.ContactIdPair ids2 = DatabaseAsserts.assertAndCreateContactWithName(
                mResolver, null, "John Smith");

        // Aggregate the two raw contacts together
        ContactUtil.setAggregationException(mResolver,
                ContactsContract.AggregationExceptions.TYPE_KEEP_TOGETHER, ids1.mRawContactId,
                ids2.mRawContactId);

        // Assert that the contacts were aggregated together
        long contactId1 = RawContactUtil.queryContactIdByRawContactId(mResolver,
                ids1.mRawContactId);
        long contactId2 = RawContactUtil.queryContactIdByRawContactId(mResolver,
                ids2.mRawContactId);
        assertEquals(contactId1, contactId2);

        // Delete the contact
        ContactUtil.delete(mResolver, contactId1);

        // Assert that both of the local raw contacts were removed from the database and
        // not merely marked DELETED=1.
        assertNull(RawContactUtil.queryByRawContactId(mResolver, ids1.mRawContactId, null));
        assertNull(RawContactUtil.queryByRawContactId(mResolver, ids2.mRawContactId, null));

        // Nothing to clean up
    }

    public void testContactDelete_localContactDeletedImmediatelyWhenAggregatedWithNonLocal() {
        // Create a raw contact in the local (null) account
        DatabaseAsserts.ContactIdPair ids1 = DatabaseAsserts.assertAndCreateContactWithName(
                mResolver, null, "John Smith");

        // Create a raw contact in a non-local account with the same name
        DatabaseAsserts.ContactIdPair ids2 = DatabaseAsserts.assertAndCreateContactWithName(
                mResolver, StaticAccountAuthenticator.ACCOUNT_1, "John Smith");

        // Aggregate the two raw contacts together
        ContactUtil.setAggregationException(mResolver,
                ContactsContract.AggregationExceptions.TYPE_KEEP_TOGETHER, ids1.mRawContactId,
                ids2.mRawContactId);

        // Assert that the contacts were aggregated together
        long contactId1 = RawContactUtil.queryContactIdByRawContactId(mResolver,
                ids1.mRawContactId);
        long contactId2 = RawContactUtil.queryContactIdByRawContactId(mResolver,
                ids2.mRawContactId);
        assertEquals(contactId1, contactId2);

        // Delete the contact
        ContactUtil.delete(mResolver, contactId1);

        // Assert that the local raw contact was removed from the database
        assertNull(RawContactUtil.queryByRawContactId(mResolver, ids1.mRawContactId, null));

        // Assert that the non-local raw contact was marked DELETED=1
        String[] projection = new String[]{
                RawContacts.DIRTY,
                RawContacts.DELETED
        };
        List<String[]> records = RawContactUtil.queryByContactId(mResolver, ids2.mContactId,
                projection);
        for (String[] arr : records) {
            assertEquals("1", arr[0]);
            assertEquals("1", arr[1]);
        }

        // Clean up
        RawContactUtil.delete(mResolver, ids2.mRawContactId, true);
    }

    public void testContactUpdate_updatesContactUpdatedTimestamp() {
        DatabaseAsserts.ContactIdPair ids = DatabaseAsserts.assertAndCreateContact(mResolver);

        long baseTime = ContactUtil.queryContactLastUpdatedTimestamp(mResolver, ids.mContactId);

        ContentValues values = new ContentValues();
        values.put(ContactsContract.Contacts.STARRED, 1);

        SystemClock.sleep(1);
        ContactUtil.update(mResolver, ids.mContactId, values);

        long newTime = ContactUtil.queryContactLastUpdatedTimestamp(mResolver, ids.mContactId);
        assertTrue(newTime > baseTime);

        // Clean up
        RawContactUtil.delete(mResolver, ids.mRawContactId, true);
    }

    /**
     * Note we no longer support contact affinity as of Q, so times_contacted and
     * last_time_contacted are always 0.
     */
    public void testContactUpdate_usageStats() throws Exception {
        final TestRawContact rawContact = mBuilder.newRawContact().insert().load();
        final TestContact contact = rawContact.getContact().load();

        contact.load();
        assertEquals(0L, contact.getLong(Contacts.TIMES_CONTACTED));
        assertEquals(0L, contact.getLong(Contacts.LAST_TIME_CONTACTED));

        final long now = System.currentTimeMillis();

        ContentValues values = new ContentValues();
        values.clear();
        values.put(Contacts.TIMES_CONTACTED, 3);
        values.put(Contacts.LAST_TIME_CONTACTED, now);
        ContactUtil.update(mResolver, contact.getId(), values);

        contact.load();
        assertEquals(0, contact.getLong(Contacts.TIMES_CONTACTED));
        assertEquals(0, contact.getLong(Contacts.LAST_TIME_CONTACTED));

        // This is also the same as markAsContacted().
        values.clear();
        values.put(Contacts.LAST_TIME_CONTACTED, now);
        ContactUtil.update(mResolver, contact.getId(), values);

        contact.load();
        assertEquals(0, contact.getLong(Contacts.TIMES_CONTACTED));
        assertEquals(0, contact.getLong(Contacts.LAST_TIME_CONTACTED));

        values.clear();
        values.put(Contacts.TIMES_CONTACTED, 10);

        ContactUtil.update(mResolver, contact.getId(), values);

        contact.load();
        assertEquals(0, contact.getLong(Contacts.TIMES_CONTACTED));
        assertEquals(0, contact.getLong(Contacts.LAST_TIME_CONTACTED));
    }

    /**
     * Make sure the rounded usage stats values are also what the callers would see in where
     * clauses.
     *
     * This tests both contacts and raw_contacts.
     */
    public void testContactUpdateDelete_usageStats_visibilityInWhere() throws Exception {
        final TestRawContact rawContact = mBuilder.newRawContact().insert().load();
        final TestContact contact = rawContact.getContact().load();

        // To make things more predictable, inline markAsContacted here with a known timestamp.
        final long now = (System.currentTimeMillis() / 86400 * 86400) + 86400 * 5 + 123;

        ContentValues values = new ContentValues();
        values.put(Contacts.LAST_TIME_CONTACTED, now);

        // This makes the internal TIMES_CONTACTED 35.  But the visible value is still 30.
        for (int i = 0; i < 35; i++) {
            ContactUtil.update(mResolver, contact.getId(), values);
        }

        contact.load();
        rawContact.load();

        assertEquals(0, contact.getLong(Contacts.LAST_TIME_CONTACTED));
        assertEquals(0, contact.getLong(Contacts.TIMES_CONTACTED));

        assertEquals(0, rawContact.getLong(Contacts.LAST_TIME_CONTACTED));
        assertEquals(0, rawContact.getLong(Contacts.TIMES_CONTACTED));
    }

    /** Make sure local contacts are visible by default. */
    public void testContactQuery_localContactVisibleByDefault() throws Exception {
        // Raw contacts without an account specified are created in the local account
        final TestRawContact localRawContact = mBuilder.newRawContact().insert().load();
        final TestContact contact = localRawContact.getContact().load();

        assertEquals(RawContacts.getLocalAccountName(mContext),
                localRawContact.getString(RawContacts.ACCOUNT_NAME));
        assertEquals(RawContacts.getLocalAccountType(mContext),
                localRawContact.getString(RawContacts.ACCOUNT_TYPE));
        assertNull(localRawContact.getString(RawContacts.DATA_SET));
        assertEquals(1, contact.getLong(Contacts.IN_VISIBLE_GROUP));
    }

    public void testProjection() throws Exception {
        final TestRawContact rawContact = mBuilder.newRawContact().insert().load();
        rawContact.newDataRow(StructuredName.CONTENT_ITEM_TYPE)
                .with(StructuredName.GIVEN_NAME, "xxx")
                .insert();

        final TestContact contact = rawContact.getContact().load();
        final long contactId = contact.getId();
        final String lookupKey = contact.getString(Contacts.LOOKUP_KEY);

        final String[] PROJECTION = new String[]{
                Contacts._ID,
                Contacts.DISPLAY_NAME,
                Contacts.DISPLAY_NAME_PRIMARY,
                Contacts.DISPLAY_NAME_ALTERNATIVE,
                Contacts.DISPLAY_NAME_SOURCE,
                Contacts.PHONETIC_NAME,
                Contacts.PHONETIC_NAME_STYLE,
                Contacts.SORT_KEY_PRIMARY,
                Contacts.SORT_KEY_ALTERNATIVE,
                Contacts.LAST_TIME_CONTACTED,
                Contacts.TIMES_CONTACTED,
                Contacts.STARRED,
                Contacts.PINNED,
                Contacts.IN_DEFAULT_DIRECTORY,
                Contacts.IN_VISIBLE_GROUP,
                Contacts.PHOTO_ID,
                Contacts.PHOTO_FILE_ID,
                Contacts.PHOTO_URI,
                Contacts.PHOTO_THUMBNAIL_URI,
                Contacts.CUSTOM_RINGTONE,
                Contacts.HAS_PHONE_NUMBER,
                Contacts.SEND_TO_VOICEMAIL,
                Contacts.IS_USER_PROFILE,
                Contacts.LOOKUP_KEY,
                Contacts.NAME_RAW_CONTACT_ID,
                Contacts.CONTACT_PRESENCE,
                Contacts.CONTACT_CHAT_CAPABILITY,
                Contacts.CONTACT_STATUS,
                Contacts.CONTACT_STATUS_TIMESTAMP,
                Contacts.CONTACT_STATUS_RES_PACKAGE,
                Contacts.CONTACT_STATUS_LABEL,
                Contacts.CONTACT_STATUS_ICON,
                Contacts.CONTACT_LAST_UPDATED_TIMESTAMP
        };

        // Contacts.CONTENT_URI
        DatabaseAsserts.checkProjection(mResolver,
                Contacts.CONTENT_URI,
                PROJECTION,
                new long[]{contact.getId()}
        );

        // Contacts.CONTENT_FILTER_URI
        DatabaseAsserts.checkProjection(mResolver,
                Contacts.CONTENT_FILTER_URI.buildUpon().appendEncodedPath("xxx").build(),
                PROJECTION,
                new long[]{contact.getId()}
        );

        // Contacts.CONTENT_FILTER_URI
        DatabaseAsserts.checkProjection(mResolver,
                Contacts.ENTERPRISE_CONTENT_FILTER_URI.buildUpon().appendEncodedPath("xxx")
                        .appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY,
                                String.valueOf(Directory.DEFAULT)).build(),
                PROJECTION,
                new long[]{contact.getId()}
        );

        // Contacts.CONTENT_LOOKUP_URI
        DatabaseAsserts.checkProjection(mResolver,
                Contacts.getLookupUri(contactId, lookupKey),
                PROJECTION,
                new long[]{contact.getId()}
        );
    }

    /**
     * Create a contact.  Delete it.  And assert that the contact record is no longer present.
     *
     * @return The contact id and raw contact id that was created.
     */
    private DatabaseAsserts.ContactIdPair assertContactCreateDelete() {
        DatabaseAsserts.ContactIdPair ids = DatabaseAsserts.assertAndCreateContact(mResolver);

        SystemClock.sleep(1);
        ContactUtil.delete(mResolver, ids.mContactId);

        assertFalse(ContactUtil.recordExistsForContactId(mResolver, ids.mContactId));

        return ids;
    }


    private void assertContactFound(Uri uri, long contactId) {
        try (Cursor c = mResolver.query(uri,
                new String[]{Contacts._ID},
                Contacts._ID + "=?",
                new String[]{String.valueOf(contactId)},
                null)) {
            assertEquals(1, c.getCount());
            c.moveToFirst();
            assertEquals(contactId, c.getLong(0));
        }
    }

    private void assertContactNotFound(Uri uri, long contactId) {
        try (Cursor c = mResolver.query(uri,
                new String[]{Contacts._ID},
                Contacts._ID + "=?",
                new String[]{String.valueOf(contactId)},
                null)) {
            assertEquals(0, c.getCount());
        }
    }

    private TestRawContact createRawContactWithName(String accountName, String contactName) throws Exception {
        TestRawContact rawContact =
                mBuilder.newRawContact()
                        .with(RawContacts.ACCOUNT_NAME, accountName)
                        .with(RawContacts.ACCOUNT_TYPE, StaticAccountAuthenticator.TYPE)
                        .insert();
      rawContact.newDataRow(StructuredName.CONTENT_ITEM_TYPE)
                .with(StructuredName.DISPLAY_NAME, contactName)
                .insert();
      return rawContact.load();
    }

    private void addToGroup(TestGroup group, TestRawContact rawContact) throws Exception {
      rawContact.newDataRow(GroupMembership.CONTENT_ITEM_TYPE)
          .with(GroupMembership.GROUP_ROW_ID, group.getId())
                .insert();
    }
}
