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

import static android.provider.Flags.FLAG_NEW_DEFAULT_ACCOUNT_API_ENABLED;

import static com.android.providers.contacts.flags.Flags.FLAG_DISABLE_CP2_ACCOUNT_MOVE_FLAG;
import static com.android.providers.contacts.flags.Flags.FLAG_DISABLE_MOVE_TO_INELIGIBLE_DEFAULT_ACCOUNT_FLAG;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Instrumentation;
import android.content.ContentResolver;
import android.content.Context;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.ContactsContract;
import android.provider.ContactsContract.RawContacts.DefaultAccount;
import android.provider.ContactsContract.SimAccount;
import android.provider.ContactsContract.SimContacts;
import android.provider.cts.contacts.account.StaticAccountAuthenticator;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class ContactsContract_MoveToCloudDeviceContactsAccount {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private Context mContext;
    private ContentResolver mResolver;
    private AccountManager mAccountManager;
    private Set<Long> mCreatedContacts;
    private boolean mNeedDefaultAccountReset;

    private static final Account CLOUD_ACCOUNT = new Account("test for move account1",
            StaticAccountAuthenticator.TYPE);

    private static final Account OTHER_ACCOUNT = new Account("test for move account2",
            StaticAccountAuthenticator.TYPE);

    // Using unique account name and types because these tests may break or be broken by
    // other tests running.  No other tests should use the following accounts.
    private static final String SIM_ACCT_NAME_1 = "move test sim acct name 1";
    private static final String SIM_ACCT_TYPE_1 = "move test sim acct type 1";
    private static final String SIM_ACCT_NAME_2 = "move test sim acct name 2";
    private static final String SIM_ACCT_TYPE_2 = "move test sim acct type 2";

    private static final int SIM_SLOT_0 = 0;
    private static final int SIM_SLOT_1 = 1;

    @Before
    public void setUp() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = instrumentation.getContext();
        mResolver = getContext().getContentResolver();
        mAccountManager = AccountManager.get(getContext());

        mAccountManager.addAccountExplicitly(CLOUD_ACCOUNT, null, null);
        mAccountManager.addAccountExplicitly(OTHER_ACCOUNT, null, null);
        SystemUtil.runWithShellPermissionIdentity(() -> {
            SimContacts.addSimAccount(mResolver, SIM_ACCT_NAME_1, SIM_ACCT_TYPE_1, SIM_SLOT_0,
                    SimAccount.ADN_EF_TYPE);
            SimContacts.addSimAccount(mResolver, SIM_ACCT_NAME_2, SIM_ACCT_TYPE_2, SIM_SLOT_1,
                    SimAccount.ADN_EF_TYPE);
        });

        mCreatedContacts = new HashSet<>();
        mNeedDefaultAccountReset = false;

        // this test uses the null account which is hard to otherwise keep isolated
        deleteLocalRawContacts();
    }

    @After
    public void tearDown() throws Exception {
        for (Long contactId : mCreatedContacts) {
            RawContactUtil.delete(mResolver, contactId, /* isSyncAdapter= */ true);
        }

        SystemUtil.runWithShellPermissionIdentity(() -> {
            mAccountManager.removeAccount(CLOUD_ACCOUNT, null , null, null);
            mAccountManager.removeAccount(OTHER_ACCOUNT, null , null, null);
            SimContacts.removeSimAccounts(mResolver, SIM_SLOT_0);
            SimContacts.removeSimAccounts(mResolver, SIM_SLOT_1);
        });

        resetDefaultAccount();
    }


    private void deleteLocalRawContacts() {
        // definitely remove all contacts from the null account
        mResolver.delete(ContactsContract.RawContacts.CONTENT_URI,
                ContactsContract.RawContacts.ACCOUNT_NAME + " IS NULL"
                        + " AND " + ContactsContract.RawContacts.ACCOUNT_TYPE + " IS NULL",
                new String[] {});

        String localAccountName = ContactsContract.RawContacts.getLocalAccountName(getContext());
        String localAccountType = ContactsContract.RawContacts.getLocalAccountType(getContext());

        if (localAccountName == null && localAccountType == null) {
            // if the LOCAL account is null, we are done
            return;
        }

        // if both the name & type for the custom local account are non-null, just handle that
        if (localAccountName != null && localAccountType != null) {
            mResolver.delete(ContactsContract.RawContacts.CONTENT_URI,
                    ContactsContract.RawContacts.ACCOUNT_NAME + " = ?"
                            + " AND " + ContactsContract.RawContacts.ACCOUNT_TYPE + " = ?",
                    new String[] {
                            localAccountName,
                            localAccountType,
                    });
            return;
        }

        // if only one field is null, figure out  which and purge those
        if (localAccountName != null) {
            mResolver.delete(ContactsContract.RawContacts.CONTENT_URI,
                    ContactsContract.RawContacts.ACCOUNT_NAME + " = ?"
                            + " AND " + ContactsContract.RawContacts.ACCOUNT_TYPE + " IS NULL",
                    new String[] {
                            localAccountName,
                    });
        } else {
            mResolver.delete(ContactsContract.RawContacts.CONTENT_URI,
                    ContactsContract.RawContacts.ACCOUNT_NAME + " IS NULL"
                            + " AND " + ContactsContract.RawContacts.ACCOUNT_TYPE + " = ?",
                    new String[] {
                            localAccountType,
                    });
        }
    }

    private void resetDefaultAccount() {
        if (mNeedDefaultAccountReset) {
            SystemUtil.runWithShellPermissionIdentity(() -> {
                setDefaultAccountForNewContacts(DefaultAccount.DefaultAccountAndState.ofNotSet());
            });
        }
    }

    private long insertRawContact(Account account) throws Exception {
        long rawContactId = RawContactUtil.insertRawContactIgnoringNullAccount(mResolver, account);
        mCreatedContacts.add(rawContactId);
        return rawContactId;
    }

    private Context getContext() {
        return mContext;
    }

    private Account getLocalAccount() {
        String customAccountName =
                ContactsContract.RawContacts.getLocalAccountName(getContext());
        String customAccountType =
                ContactsContract.RawContacts.getLocalAccountType(getContext());
        if (customAccountName == null || customAccountType == null) {
            return null;
        }
        return new Account(customAccountName, customAccountType);
    }

    private void moveSimContactsToCloudDefaultAccount() {
        SystemUtil.runWithShellPermissionIdentity(() ->
                DefaultAccount.moveSimContactsToCloudDefaultAccount(mResolver));
    }

    private void moveLocalContactsToCloudDefaultAccount() {
        SystemUtil.runWithShellPermissionIdentity(() ->
                DefaultAccount.moveLocalContactsToCloudDefaultAccount(mResolver));
    }

    private int getNumberOfMovableSimContacts() {
        return SystemUtil.runWithShellPermissionIdentity(() ->
                DefaultAccount.getNumberOfMovableSimContacts(mResolver));
    }

    private int getNumberOfMovableLocalContacts() {
        return SystemUtil.runWithShellPermissionIdentity(() ->
                DefaultAccount.getNumberOfMovableLocalContacts(mResolver));
    }

    private void setDefaultAccountForNewContacts(
            DefaultAccount.DefaultAccountAndState defaultAccountAndState) {
        mNeedDefaultAccountReset = true;
        SystemUtil.runWithShellPermissionIdentity(() ->
                DefaultAccount.setDefaultAccountForNewContacts(mResolver, defaultAccountAndState));
    }

    private void assertRawContactAccount(long rawContactId, Account account) {
        assertTrue(RawContactUtil.rawContactExistsById(mResolver, rawContactId));
        String[] results = RawContactUtil.queryByRawContactId(mResolver, rawContactId,
                new String[] {
                    ContactsContract.RawContacts.ACCOUNT_NAME,
                    ContactsContract.RawContacts.ACCOUNT_TYPE,
                    ContactsContract.RawContacts.DELETED,
                });
        assertArrayEquals(new String[] {
                account != null ? account.name : null,
                account != null ? account.type : null,
                "0" // must not be deleted
        }, results);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_NEW_DEFAULT_ACCOUNT_API_ENABLED})
    @RequiresFlagsDisabled({FLAG_DISABLE_CP2_ACCOUNT_MOVE_FLAG})
    public void testGetNumberOfMovableLocalContactsWithNoLocalContacts() throws Exception {
        // create a contact with a non-local account
        insertRawContact(OTHER_ACCOUNT);
        setDefaultAccountForNewContacts(DefaultAccount.DefaultAccountAndState.ofLocal());
        int count = getNumberOfMovableLocalContacts();
        assertEquals(0, count);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_NEW_DEFAULT_ACCOUNT_API_ENABLED})
    @RequiresFlagsDisabled({FLAG_DISABLE_CP2_ACCOUNT_MOVE_FLAG,
            FLAG_DISABLE_MOVE_TO_INELIGIBLE_DEFAULT_ACCOUNT_FLAG})
    public void testGetNumberOfMovableLocalContactsWithLocalContacts() throws Exception {
        // create contact and explicitly set the account to null
        insertRawContact(null);
        // set a cloud default account
        setDefaultAccountForNewContacts(
                DefaultAccount.DefaultAccountAndState.ofCloud(CLOUD_ACCOUNT));
        int count = getNumberOfMovableLocalContacts();
        // the local null account is hard to isolate, so assume there may be extra contacts
        assertEquals(1, count);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_NEW_DEFAULT_ACCOUNT_API_ENABLED,
            FLAG_DISABLE_CP2_ACCOUNT_MOVE_FLAG})
    public void testGetNumberOfMovableLocalContactsWithLocalContacts_flagsOff() throws Exception {
        // create contact and explicitly set the account to null
        insertRawContact(null);

        int count = getNumberOfMovableLocalContacts();
        assertEquals(0, count);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_NEW_DEFAULT_ACCOUNT_API_ENABLED})
    @RequiresFlagsDisabled({FLAG_DISABLE_CP2_ACCOUNT_MOVE_FLAG,
            FLAG_DISABLE_MOVE_TO_INELIGIBLE_DEFAULT_ACCOUNT_FLAG})
    public void testMoveLocalContactsToCloudDefaultAccount() throws Exception {
        // create contact and explicitly set the account to null
        long rawContactId1 = insertRawContact(null);

        // create contact with OEM configurable local account
        long rawContactId2 = insertRawContact(getLocalAccount());

        // set a cloud default account
        setDefaultAccountForNewContacts(
                DefaultAccount.DefaultAccountAndState.ofCloud(CLOUD_ACCOUNT));
        int count = getNumberOfMovableLocalContacts();
        // the local null account is hard to isolate, so assume there maybe extra contacts
        assertEquals(2, count);

        // contacts are moved from both null and OEM configurable local accounts
        moveLocalContactsToCloudDefaultAccount();

        count = getNumberOfMovableLocalContacts();
        assertEquals(0, count);

        assertRawContactAccount(rawContactId1, CLOUD_ACCOUNT);
        assertRawContactAccount(rawContactId2, CLOUD_ACCOUNT);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_NEW_DEFAULT_ACCOUNT_API_ENABLED,
            FLAG_DISABLE_CP2_ACCOUNT_MOVE_FLAG})
    public void testMoveLocalContactsToCloudDefaultAccount_flagsOff() throws Exception {
        // create contact and explicitly set the account to null
        long rawContactId1 = insertRawContact(null);

        // create contact with OEM configurable local account
        long rawContactId2 = insertRawContact(getLocalAccount());

        int count = getNumberOfMovableLocalContacts();
        assertEquals(0, count);

        moveLocalContactsToCloudDefaultAccount();

        count = getNumberOfMovableLocalContacts();
        assertEquals(0, count);

        assertRawContactAccount(rawContactId1, null);
        assertRawContactAccount(rawContactId2, getLocalAccount());
    }

    @Test
    @RequiresFlagsEnabled({FLAG_NEW_DEFAULT_ACCOUNT_API_ENABLED})
    @RequiresFlagsDisabled({FLAG_DISABLE_CP2_ACCOUNT_MOVE_FLAG,
            FLAG_DISABLE_MOVE_TO_INELIGIBLE_DEFAULT_ACCOUNT_FLAG})
    public void testGetNumberOfMovableSimContactsWithSimContacts() throws Exception {
        insertRawContact(
                new Account(SIM_ACCT_NAME_1, SIM_ACCT_TYPE_1));
        insertRawContact(
                new Account(SIM_ACCT_NAME_2, SIM_ACCT_TYPE_2));

        setDefaultAccountForNewContacts(
                DefaultAccount.DefaultAccountAndState.ofCloud(CLOUD_ACCOUNT));

        int count = getNumberOfMovableSimContacts();
        assertEquals(2, count);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_NEW_DEFAULT_ACCOUNT_API_ENABLED,
            FLAG_DISABLE_CP2_ACCOUNT_MOVE_FLAG})
    public void testGetNumberOfMovableSimContactsWithSimContacts_flagsOff() throws Exception {
        insertRawContact(
                new Account(SIM_ACCT_NAME_1, SIM_ACCT_TYPE_1));
        insertRawContact(
                new Account(SIM_ACCT_NAME_2, SIM_ACCT_TYPE_2));

        int count = getNumberOfMovableSimContacts();
        assertEquals(0, count);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_NEW_DEFAULT_ACCOUNT_API_ENABLED})
    @RequiresFlagsDisabled({FLAG_DISABLE_CP2_ACCOUNT_MOVE_FLAG,
            FLAG_DISABLE_MOVE_TO_INELIGIBLE_DEFAULT_ACCOUNT_FLAG})
    public void testMoveSimContactsToCloudDefaultAccount() throws Exception {
        // create contact and explicitly set the account to null
        long rawContactId = insertRawContact(new Account(SIM_ACCT_NAME_1, SIM_ACCT_TYPE_1));

        // set a cloud default account
        setDefaultAccountForNewContacts(
                DefaultAccount.DefaultAccountAndState.ofCloud(CLOUD_ACCOUNT));
        int count = getNumberOfMovableSimContacts();
        assertEquals(1, count);

        moveSimContactsToCloudDefaultAccount();

        count = getNumberOfMovableSimContacts();
        assertEquals(0, count);

        assertRawContactAccount(rawContactId, CLOUD_ACCOUNT);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_NEW_DEFAULT_ACCOUNT_API_ENABLED,
            FLAG_DISABLE_CP2_ACCOUNT_MOVE_FLAG})
    public void testMoveSimContactsToCloudDefaultAccount_flagsOff() throws Exception {
        // create contact and explicitly set the account to null
        long rawContactId = insertRawContact(new Account(SIM_ACCT_NAME_1, SIM_ACCT_TYPE_1));

        int count = getNumberOfMovableSimContacts();
        assertEquals(0, count);

        moveSimContactsToCloudDefaultAccount();

        count = getNumberOfMovableSimContacts();
        assertEquals(0, count);

        assertRawContactAccount(rawContactId, new Account(SIM_ACCT_NAME_1, SIM_ACCT_TYPE_1));
    }

    public void testMoveLocalContactsToInvalidDefaultAccountInternal(
            DefaultAccount.DefaultAccountAndState defaultAccountAndState) throws Exception {
        // create contact and explicitly set the account to null
        long rawContactId = insertRawContact(null);

        // set a default account
        setDefaultAccountForNewContacts(defaultAccountAndState);

        // no contacts will be movable
        int count = getNumberOfMovableLocalContacts();
        assertEquals(0, count);

        moveLocalContactsToCloudDefaultAccount();

        // contact is not moved
        assertRawContactAccount(rawContactId, getLocalAccount());
    }

    @Test
    @RequiresFlagsEnabled({FLAG_NEW_DEFAULT_ACCOUNT_API_ENABLED})
    @RequiresFlagsDisabled({FLAG_DISABLE_CP2_ACCOUNT_MOVE_FLAG})
    public void testMoveLocalContactsToLocalDefaultAccount() throws Exception {
        testMoveLocalContactsToInvalidDefaultAccountInternal(
                DefaultAccount.DefaultAccountAndState.ofLocal());
    }

    @Test
    @RequiresFlagsEnabled({FLAG_NEW_DEFAULT_ACCOUNT_API_ENABLED})
    @RequiresFlagsDisabled({FLAG_DISABLE_CP2_ACCOUNT_MOVE_FLAG})
    public void testMoveLocalContactsToNotSetDefaultAccount() throws Exception {
        testMoveLocalContactsToInvalidDefaultAccountInternal(
                DefaultAccount.DefaultAccountAndState.ofNotSet());
    }

    @Test
    @RequiresFlagsEnabled({FLAG_NEW_DEFAULT_ACCOUNT_API_ENABLED})
    @RequiresFlagsDisabled({FLAG_DISABLE_CP2_ACCOUNT_MOVE_FLAG})
    public void testMoveLocalContactsToSimDefaultAccount() throws Exception {
        testMoveLocalContactsToInvalidDefaultAccountInternal(
                DefaultAccount.DefaultAccountAndState.ofSim(
                        new Account(SIM_ACCT_NAME_1, SIM_ACCT_TYPE_1)));
    }
}
