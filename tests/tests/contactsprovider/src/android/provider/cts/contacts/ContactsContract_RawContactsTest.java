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


import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.RawContacts.DefaultAccount;
import android.provider.ContactsContract.RawContacts.DefaultAccount.DefaultAccountAndState;
import android.provider.cts.contacts.ContactsContract_TestDataBuilder.TestContact;
import android.provider.cts.contacts.ContactsContract_TestDataBuilder.TestRawContact;
import android.provider.cts.contacts.account.StaticAccountAuthenticator;
import android.test.AndroidTestCase;
import android.test.MoreAsserts;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.SystemUtil;

public class ContactsContract_RawContactsTest extends AndroidTestCase {
    private ContentResolver mResolver;
    private ContactsContract_TestDataBuilder mBuilder;

    private AccountManager mAccountManager;

    private static final String[] RAW_CONTACTS_PROJECTION = new String[]{
            RawContacts._ID,
            RawContacts.CONTACT_ID,
            RawContacts.DELETED,
            RawContacts.DISPLAY_NAME_PRIMARY,
            RawContacts.DISPLAY_NAME_ALTERNATIVE,
            RawContacts.DISPLAY_NAME_SOURCE,
            RawContacts.PHONETIC_NAME,
            RawContacts.PHONETIC_NAME_STYLE,
            RawContacts.SORT_KEY_PRIMARY,
            RawContacts.SORT_KEY_ALTERNATIVE,
            RawContacts.TIMES_CONTACTED,
            RawContacts.LAST_TIME_CONTACTED,
            RawContacts.CUSTOM_RINGTONE,
            RawContacts.SEND_TO_VOICEMAIL,
            RawContacts.STARRED,
            RawContacts.PINNED,
            RawContacts.AGGREGATION_MODE,
            RawContacts.RAW_CONTACT_IS_USER_PROFILE,
            RawContacts.ACCOUNT_NAME,
            RawContacts.ACCOUNT_TYPE,
            RawContacts.DATA_SET,
            RawContacts.ACCOUNT_TYPE_AND_DATA_SET,
            RawContacts.DIRTY,
            RawContacts.SOURCE_ID,
            RawContacts.BACKUP_ID,
            RawContacts.VERSION,
            RawContacts.SYNC1,
            RawContacts.SYNC2,
            RawContacts.SYNC3,
            RawContacts.SYNC4
    };

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mResolver = getContext().getContentResolver();
        ContentProviderClient provider =
                mResolver.acquireContentProviderClient(ContactsContract.AUTHORITY);
        mBuilder = new ContactsContract_TestDataBuilder(provider);

        mAccountManager = AccountManager.get(getContext());
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        mBuilder.cleanup();
    }

    public void testGetLookupUriBySourceId() throws Exception {
        TestRawContact rawContact = mBuilder.newRawContact()
                .with(RawContacts.ACCOUNT_TYPE, "test_type")
                .with(RawContacts.ACCOUNT_NAME, "test_name")
                .with(RawContacts.SOURCE_ID, "source_id")
                .insert();

        // TODO remove this. The method under test is currently broken: it will not
        // work without at least one data row in the raw contact.
        rawContact.newDataRow(StructuredName.CONTENT_ITEM_TYPE)
                .with(StructuredName.DISPLAY_NAME, "test name")
                .insert();

        Uri lookupUri = RawContacts.getContactLookupUri(mResolver, rawContact.getUri());
        assertNotNull("Could not produce a lookup URI", lookupUri);

        TestContact lookupContact = mBuilder.newContact().setUri(lookupUri).load();
        assertEquals("Lookup URI matched the wrong contact",
                lookupContact.getId(), rawContact.load().getContactId());
    }

    public void testGetLookupUriByDisplayName() throws Exception {
        TestRawContact rawContact = mBuilder.newRawContact()
                .with(RawContacts.ACCOUNT_TYPE, "test_type")
                .with(RawContacts.ACCOUNT_NAME, "test_name")
                .insert();
        rawContact.newDataRow(StructuredName.CONTENT_ITEM_TYPE)
                .with(StructuredName.DISPLAY_NAME, "test name")
                .insert();

        Uri lookupUri = RawContacts.getContactLookupUri(mResolver, rawContact.getUri());
        assertNotNull("Could not produce a lookup URI", lookupUri);

        TestContact lookupContact = mBuilder.newContact().setUri(lookupUri).load();
        assertEquals("Lookup URI matched the wrong contact",
                lookupContact.getId(), rawContact.load().getContactId());
    }

    public void testGetDeviceAccountNameAndType_haveSameNullness() {
        String name = RawContacts.getLocalAccountName(mContext);
        String type = RawContacts.getLocalAccountType(mContext);

        assertTrue("Device account name and type must both be null or both be non-null",
                (name == null && type == null) || (name != null && type != null));
    }

    public void testRawContactDelete_setsDeleteFlag() {
        long rawContactid = RawContactUtil.insertRawContactIgnoringNullAccount(mResolver,
                StaticAccountAuthenticator.ACCOUNT_1);

        assertTrue(RawContactUtil.rawContactExistsById(mResolver, rawContactid));

        RawContactUtil.delete(mResolver, rawContactid, false);

        String[] projection = new String[]{
                ContactsContract.RawContacts.CONTACT_ID,
                ContactsContract.RawContacts.DELETED
        };
        String[] result = RawContactUtil.queryByRawContactId(mResolver, rawContactid,
                projection);

        // Contact id should be null
        assertNull(result[0]);
        // Record should be marked deleted.
        assertEquals("1", result[1]);
    }

    @CddTest(requirement = "3.18/C-1-5")
    public void testRawContactDelete_localDeleteRemovesRecord() {
        String name = RawContacts.getLocalAccountName(mContext);
        String type = RawContacts.getLocalAccountType(mContext);
        Account account = name != null && type != null ? new Account(name, type) : null;

        long rawContactid = RawContactUtil.insertRawContactIgnoringNullAccount(mResolver, account);
        assertTrue(RawContactUtil.rawContactExistsById(mResolver, rawContactid));

        // Local raw contacts should be deleted immediately even if isSyncAdapter=false
        RawContactUtil.delete(mResolver, rawContactid, false);

        assertFalse(RawContactUtil.rawContactExistsById(mResolver, rawContactid));

        // Nothing to clean up
    }

    public void testRawContactDelete_removesRecord() {
        long rawContactid = RawContactUtil.insertRawContactIgnoringNullAccount(mResolver,
                StaticAccountAuthenticator.ACCOUNT_1);
        assertTrue(RawContactUtil.rawContactExistsById(mResolver, rawContactid));

        RawContactUtil.delete(mResolver, rawContactid, true);

        assertFalse(RawContactUtil.rawContactExistsById(mResolver, rawContactid));

        // already clean
    }

    // This implicitly tests the Contact create case.
    public void testRawContactCreate_updatesContactUpdatedTimestamp() {
        long startTime = System.currentTimeMillis();

        DatabaseAsserts.ContactIdPair ids = DatabaseAsserts.assertAndCreateContact(mResolver);
        long lastUpdated = getContactLastUpdatedTimestampByRawContactId(mResolver,
                ids.mRawContactId);

        assertTrue(lastUpdated > startTime);

        // Clean up
        RawContactUtil.delete(mResolver, ids.mRawContactId, true);
    }

    /**
     * The local account is the default if a raw contact insert does not specify a value for
     * {@link RawContacts#ACCOUNT_NAME} and {@link RawContacts#ACCOUNT_TYPE}.
     *
     * <p>The values returned by {@link RawContacts#getLocalAccountName()} and
     * {@link RawContacts#getLocalAccountType()} can be  customized by overriding the
     * config_rawContactsLocalAccountName and config_rawContactsLocalAccountType resource strings 
     * defined in platform/frameworks/base/core/res/res/values/config.xml.
     */
    @CddTest(requirement="3.18/C-1-1,C-1-2,C-1-3")
    public void testRawContactCreate_noAccountUsesLocalAccount() {
        // Save a raw contact without an account.
        long rawContactid = RawContactUtil.insertRawContactIgnoringNullAccount(mResolver, null);

        String[] row =  RawContactUtil.queryByRawContactId(mResolver, rawContactid,
                new String[] {
                        RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE
                });

        // When no account is specified the contact is created in the local account.
        assertEquals(RawContacts.getLocalAccountName(mContext), row[0]);
        assertEquals(RawContacts.getLocalAccountType(mContext), row[1]);

        // Clean up
        RawContactUtil.delete(mResolver, rawContactid, true);
    }

    /**
     * The local account is the default if a raw contact insert uses null for
     * the {@link RawContacts#ACCOUNT_NAME} and {@link RawContacts#ACCOUNT_TYPE}.
     *
     * <p>See {@link #testRawContactCreate_noAccountUsesLocalAccount()}
     */
    @CddTest(requirement = "3.18/C-1-1,C-1-2,C-1-3")
    public void testRawContactCreate_nullAccountUsesLocalAccount() throws Exception {
        // Save a raw contact using the default local account
        TestRawContact rawContact = mBuilder.newRawContact()
                .with(RawContacts.ACCOUNT_TYPE, (String) null)
                .with(RawContacts.ACCOUNT_NAME, (String) null)
                .insert();

        String[] row =  RawContactUtil.queryByRawContactId(mResolver, rawContact.getId(),
                new String[] {
                        RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE
                });

        // When the raw contact is inserted with null account specified, the contact is created
        // in the custom local account.
        assertEquals(RawContacts.getLocalAccountName(mContext), row[0]);
        assertEquals(RawContacts.getLocalAccountType(mContext), row[1]);
    }

    /**
     * The local account is set as the default account: if a raw contact insert does not specify a
     * value for {@link RawContacts#ACCOUNT_NAME} and {@link RawContacts#ACCOUNT_TYPE}, it will be
     * saved to the local account.
     *
     * <p>The values returned by {@link RawContacts#getLocalAccountName()} and {@link
     * RawContacts#getLocalAccountType()} can be customized by overriding the
     * config_rawContactsLocalAccountName and config_rawContactsLocalAccountType resource strings
     * defined in platform/frameworks/base/core/res/res/values/config.xml.
     */
    @CddTest(requirement = "3.18/C-1-1,C-1-2,C-1-3")
    public void testRawContactCreate_defaultAccountIsLocal_noAccountUsesLocalAccount() {
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    setDefaultAccountForNewContacts(DefaultAccountAndState.ofLocal());
                });

        // Save a raw contact without an account.
        long rawContactId = RawContactUtil.insertRawContactIgnoringNullAccount(mResolver, null);

        String[] row =
                RawContactUtil.queryByRawContactId(
                        mResolver,
                        rawContactId,
                        new String[] {RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE});

        // When no account is specified the contact is created in the custom local account.
        assertEquals(RawContacts.getLocalAccountName(mContext), row[0]);
        assertEquals(RawContacts.getLocalAccountType(mContext), row[1]);

        // Clean up
        RawContactUtil.delete(mResolver, rawContactId, true);
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    setDefaultAccountForNewContacts(DefaultAccountAndState.ofNotSet());
                });
    }

    /**
     * A cloud account is set as the default account: if a raw contact insert does not specify a
     * value for {@link RawContacts#ACCOUNT_NAME} and {@link RawContacts#ACCOUNT_TYPE}, it will be
     * saved to that cloud account.
     */
    @CddTest(requirement = "3.18/C-1-1,C-1-2,C-1-3")
    public void testRawContactCreate_defaultAccountIsCloud_noAccountUsesDefaultAccount() {
        Account cloudAccount =
                new Account(
                        "ContactsContract_DefaultAccountTest1", StaticAccountAuthenticator.TYPE);
        mAccountManager.addAccountExplicitly(cloudAccount, null, null);

        // Set CLOUD_ACCOUNT_1 as the default account.
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    setDefaultAccountForNewContacts(DefaultAccountAndState.ofCloud(cloudAccount));
                });

        // Save a raw contact without an account.
        long rawContactId = RawContactUtil.insertRawContactIgnoringNullAccount(mResolver, null);

        String[] row =
                RawContactUtil.queryByRawContactId(
                        mResolver,
                        rawContactId,
                        new String[] {RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE});

        // When no account is specified the contact is created in the default account.
        assertEquals(cloudAccount.name, row[0]);
        assertEquals(cloudAccount.type, row[1]);

        // Clean up
        RawContactUtil.delete(mResolver, rawContactId, true);
        mAccountManager.removeAccount(cloudAccount, null, null);
        SystemUtil.runWithShellPermissionIdentity(
                () -> {
                    setDefaultAccountForNewContacts(DefaultAccountAndState.ofNotSet());
                });
    }

    public void testRawContactUpdate_updatesContactUpdatedTimestamp() {
        DatabaseAsserts.ContactIdPair ids = DatabaseAsserts.assertAndCreateContact(mResolver);

        long baseTime = ContactUtil.queryContactLastUpdatedTimestamp(mResolver, ids.mContactId);

        ContentValues values = new ContentValues();
        values.put(ContactsContract.RawContacts.STARRED, 1);
        RawContactUtil.update(mResolver, ids.mRawContactId, values);

        long newTime = ContactUtil.queryContactLastUpdatedTimestamp(mResolver, ids.mContactId);
        assertTrue(newTime > baseTime);

        // Clean up
        RawContactUtil.delete(mResolver, ids.mRawContactId, true);
    }

    public void testRawContactPsuedoDelete_hasDeleteLogForContact() {
        DatabaseAsserts.ContactIdPair ids = DatabaseAsserts.assertAndCreateContact(mResolver);

        long baseTime = ContactUtil.queryContactLastUpdatedTimestamp(mResolver, ids.mContactId);

        RawContactUtil.delete(mResolver, ids.mRawContactId, false);

        DatabaseAsserts.assertHasDeleteLogGreaterThan(mResolver, ids.mContactId, baseTime);

        // clean up
        RawContactUtil.delete(mResolver, ids.mRawContactId, true);
    }

    public void testRawContactDelete_hasDeleteLogForContact() {
        DatabaseAsserts.ContactIdPair ids = DatabaseAsserts.assertAndCreateContact(mResolver);

        long baseTime = ContactUtil.queryContactLastUpdatedTimestamp(mResolver, ids.mContactId);

        RawContactUtil.delete(mResolver, ids.mRawContactId, true);

        DatabaseAsserts.assertHasDeleteLogGreaterThan(mResolver, ids.mContactId, baseTime);

        // already clean
    }

    private long getContactLastUpdatedTimestampByRawContactId(ContentResolver resolver,
            long rawContactId) {
        long contactId = RawContactUtil.queryContactIdByRawContactId(mResolver, rawContactId);
        MoreAsserts.assertNotEqual(CommonDatabaseUtils.NOT_FOUND, contactId);

        return ContactUtil.queryContactLastUpdatedTimestamp(mResolver, contactId);
    }

    public void testProjection() throws Exception {
        TestRawContact rawContact = mBuilder.newRawContact()
                .with(RawContacts.ACCOUNT_TYPE, "test_type")
                .with(RawContacts.ACCOUNT_NAME, "test_name")
                .insert();
        rawContact.newDataRow(StructuredName.CONTENT_ITEM_TYPE)
                .with(StructuredName.DISPLAY_NAME, "test name")
                .insert();

        DatabaseAsserts.checkProjection(mResolver, RawContacts.CONTENT_URI,
                RAW_CONTACTS_PROJECTION,
                new long[]{rawContact.getId()}
        );
    }

    public void testInsertUsageStat() throws Exception {
        // Note we no longer support contact affinity as of Q, so times_contacted and
        // last_time_contacted are always 0, and "frequent" is always empty.

        final long now = System.currentTimeMillis();
        {
            TestRawContact rawContact = mBuilder.newRawContact()
                    .with(RawContacts.ACCOUNT_TYPE, "test_type")
                    .with(RawContacts.ACCOUNT_NAME, "test_name")
                    .with(RawContacts.TIMES_CONTACTED, 12345)
                    .with(RawContacts.LAST_TIME_CONTACTED, now)
                    .insert();

            rawContact.load();
            assertEquals(0, rawContact.getLong(RawContacts.TIMES_CONTACTED));
            assertEquals(0, rawContact.getLong(RawContacts.LAST_TIME_CONTACTED));
        }

        {
            TestRawContact rawContact = mBuilder.newRawContact()
                    .with(RawContacts.ACCOUNT_TYPE, "test_type")
                    .with(RawContacts.ACCOUNT_NAME, "test_name")
                    .with(RawContacts.TIMES_CONTACTED, 5)
                    .insert();

            rawContact.load();
            assertEquals(0, rawContact.getLong(RawContacts.TIMES_CONTACTED));
            assertEquals(0, rawContact.getLong(RawContacts.LAST_TIME_CONTACTED));
        }
        {
            TestRawContact rawContact = mBuilder.newRawContact()
                    .with(RawContacts.ACCOUNT_TYPE, "test_type")
                    .with(RawContacts.ACCOUNT_NAME, "test_name")
                    .with(RawContacts.LAST_TIME_CONTACTED, now)
                    .insert();

            rawContact.load();
            assertEquals(0, rawContact.getLong(RawContacts.TIMES_CONTACTED));
            assertEquals(0, rawContact.getLong(RawContacts.LAST_TIME_CONTACTED));
        }
    }

    public void testUpdateUsageStat() throws Exception {
        ContentValues values = new ContentValues();

        final long now = System.currentTimeMillis();
        TestRawContact rawContact = mBuilder.newRawContact()
                .with(RawContacts.ACCOUNT_TYPE, "test_type")
                .with(RawContacts.ACCOUNT_NAME, "test_name")
                .with(RawContacts.TIMES_CONTACTED, 12345)
                .with(RawContacts.LAST_TIME_CONTACTED, now)
                .insert();

        rawContact.load();
        assertEquals(0, rawContact.getLong(RawContacts.TIMES_CONTACTED));
        assertEquals(0, rawContact.getLong(RawContacts.LAST_TIME_CONTACTED));

        values.clear();
        values.put(RawContacts.TIMES_CONTACTED, 99999);
        RawContactUtil.update(mResolver, rawContact.getId(), values);

        rawContact.load();
        assertEquals(0, rawContact.getLong(RawContacts.TIMES_CONTACTED));
        assertEquals(0, rawContact.getLong(RawContacts.LAST_TIME_CONTACTED));

        values.clear();
        values.put(RawContacts.LAST_TIME_CONTACTED, now + 86400);
        RawContactUtil.update(mResolver, rawContact.getId(), values);

        rawContact.load();
        assertEquals(0, rawContact.getLong(RawContacts.TIMES_CONTACTED));
        assertEquals(0, rawContact.getLong(RawContacts.LAST_TIME_CONTACTED));
    }


    public void testQueryByAccount() throws Exception {
        String accountName = "RawContactsTest_testQueryByAccount_accountName";
        String accountType = "RawContactsTest_testQueryByAccount_accountType";
        String accountDataSet = "RawContactsTest_testQueryByAccount_dataSet";
        TestRawContact rawContact = mBuilder.newRawContact()
                .with(RawContacts.ACCOUNT_NAME, accountName)
                .with(RawContacts.ACCOUNT_TYPE, accountType)
                .with(RawContacts.DATA_SET, accountDataSet)
                .insert().load();
        long rawContactId = rawContact.getId();

        // verify we can get raw contact with no filters, or with correct account filters
        assertRawContactFound(RawContacts.CONTENT_URI, rawContactId);

        assertRawContactFound(RawContacts.CONTENT_URI.buildUpon()
                .appendQueryParameter(RawContacts.ACCOUNT_NAME, accountName)
                .appendQueryParameter(RawContacts.ACCOUNT_TYPE, accountType)
                .appendQueryParameter(RawContacts.DATA_SET, accountDataSet)
                .build(), rawContactId);

        // verify we can filter the raw contact out
        assertRawContactNotFound(RawContacts.CONTENT_URI.buildUpon()
                .appendQueryParameter(RawContacts.ACCOUNT_NAME, "a")
                .appendQueryParameter(RawContacts.ACCOUNT_TYPE, accountType)
                .appendQueryParameter(RawContacts.DATA_SET, accountDataSet)
                .build(), rawContactId);
        assertRawContactNotFound(RawContacts.CONTENT_URI.buildUpon()
                .appendQueryParameter(RawContacts.ACCOUNT_NAME, accountName)
                .appendQueryParameter(RawContacts.ACCOUNT_TYPE, "b")
                .appendQueryParameter(RawContacts.DATA_SET, accountDataSet)
                .build(), rawContactId);
        assertRawContactNotFound(RawContacts.CONTENT_URI.buildUpon()
                .appendQueryParameter(RawContacts.ACCOUNT_NAME, accountName)
                .appendQueryParameter(RawContacts.ACCOUNT_TYPE, accountType)
                .appendQueryParameter(RawContacts.DATA_SET, "c")
                .build(), rawContactId);
        assertRawContactNotFound(RawContacts.CONTENT_URI.buildUpon()
                .appendQueryParameter(RawContacts.ACCOUNT_NAME, accountName)
                .appendQueryParameter(RawContacts.ACCOUNT_TYPE, accountType)
                .build(), rawContactId);
    }

    public void testQueryByAccountWithNullDataSet() throws Exception {
        String accountName = "RawContactsTest_testQueryByAccountWithNullDataSet_accountName";
        String accountType = "RawContactsTest_testQueryByAccountWithNullDataSet_accountType";
        TestRawContact rawContact = mBuilder.newRawContact()
                .with(RawContacts.ACCOUNT_NAME, accountName)
                .with(RawContacts.ACCOUNT_TYPE, accountType)
                .insert().load();
        long rawContactId = rawContact.getId();

        // verify we can get raw contact with no filters, or with correct account filters
        assertRawContactFound(RawContacts.CONTENT_URI, rawContactId);

        assertRawContactFound(RawContacts.CONTENT_URI.buildUpon()
                .appendQueryParameter(RawContacts.ACCOUNT_NAME, accountName)
                .appendQueryParameter(RawContacts.ACCOUNT_TYPE, accountType)
                .build(), rawContactId);

        // verify we can filter the raw contact out
        assertRawContactNotFound(RawContacts.CONTENT_URI.buildUpon()
                .appendQueryParameter(RawContacts.ACCOUNT_NAME, "a")
                .appendQueryParameter(RawContacts.ACCOUNT_TYPE, accountType)
                .build(), rawContactId);
        assertRawContactNotFound(RawContacts.CONTENT_URI.buildUpon()
                .appendQueryParameter(RawContacts.ACCOUNT_NAME, accountName)
                .appendQueryParameter(RawContacts.ACCOUNT_TYPE, "b")
                .build(), rawContactId);
        assertRawContactNotFound(RawContacts.CONTENT_URI.buildUpon()
                .appendQueryParameter(RawContacts.ACCOUNT_NAME, accountName)
                .appendQueryParameter(RawContacts.ACCOUNT_TYPE, accountType)
                .appendQueryParameter(RawContacts.DATA_SET, "c")
                .build(), rawContactId);
    }

    private void assertRawContactFound(Uri uri, long rawContactId) {
        try (Cursor c = mResolver.query(uri,
                new String[]{RawContacts._ID},
                RawContacts._ID + "=?",
                new String[]{String.valueOf(rawContactId)},
                null)) {
            assertEquals(1, c.getCount());
            c.moveToFirst();
            assertEquals(rawContactId, c.getLong(0));
        }
    }

    private void assertRawContactNotFound(Uri uri, long rawContactId) {
        try (Cursor c = mResolver.query(uri,
                new String[]{RawContacts._ID},
                RawContacts._ID + "=?",
                new String[]{String.valueOf(rawContactId)},
                null)) {
            assertEquals(0, c.getCount());
        }
    }

    private void setDefaultAccountForNewContacts(DefaultAccountAndState defaultAccountAndState) {
        DefaultAccount.setDefaultAccountForNewContacts(mResolver, defaultAccountAndState);
    }
}
