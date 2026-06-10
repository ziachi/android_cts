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

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Build;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.ContactsContract;
import android.provider.ContactsContract.RawContacts.DefaultAccount;
import android.provider.ContactsContract.RawContacts.DefaultAccount.DefaultAccountAndState;
import android.provider.ContactsContract.SimAccount;
import android.provider.ContactsContract.SimContacts;
import android.provider.cts.contacts.account.StaticAccountAuthenticator;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class ContactsContract_DefaultAccountTest {
    // Using unique account name and type because these tests may break or be broken by
    // other tests running. No other tests should use the following accounts.
    private static final Account ACCT_1 = new Account("DAT test for default account1",
            StaticAccountAuthenticator.TYPE);
    private static final Account ACCT_2 = new Account("DAT test for default account2",
            StaticAccountAuthenticator.TYPE);
    private static final Account ACCT_NOT_PRESENT =
            new Account("DAT test for account not signed in", StaticAccountAuthenticator.TYPE);
    private static final String SIM_ACCT_NAME = "sim account name for DAT test";
    private static final String SIM_ACCT_TYPE = "sim account type for DAT test";
    private static final Account SIM_ACCT = new Account(SIM_ACCT_NAME, SIM_ACCT_TYPE);
    private static final int SIM_SLOT_0 = 0;
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();
    private Context mContext;
    private ContentResolver mResolver;
    private AccountManager mAccountManager;

    @Before
    public void setUp() throws Exception {
        mContext = getInstrumentation().getContext();
        mResolver = getContext().getContentResolver();
        mAccountManager = AccountManager.get(getContext());

        mAccountManager.addAccountExplicitly(ACCT_1, null, null);
        mAccountManager.addAccountExplicitly(ACCT_2, null, null);

        SystemUtil.runWithShellPermissionIdentity(() -> {
            SimContacts.addSimAccount(mResolver, SIM_ACCT_NAME, SIM_ACCT_TYPE, SIM_SLOT_0,
                    SimAccount.ADN_EF_TYPE);
        });
    }

    @After
    public void tearDown() throws Exception {
        mAccountManager.removeAccount(ACCT_1, null, null);
        mAccountManager.removeAccount(ACCT_2, null, null);

        SystemUtil.runWithShellPermissionIdentity(() -> {
            SimContacts.removeSimAccounts(mResolver, SIM_SLOT_0);
        });

        SystemUtil.runWithShellPermissionIdentity(() -> {
            setDefaultAccountForNewContacts(DefaultAccountAndState.ofNotSet());
        });
    }

    @RequiresFlagsEnabled({FLAG_NEW_DEFAULT_ACCOUNT_API_ENABLED})
    @Test
    public void testInitialDefaultAccountState() {
        DefaultAccountAndState defaultAccountAndState = getDefaultAccountForNewContacts();
        assertEquals(DefaultAccountAndState.ofNotSet(), defaultAccountAndState);
    }

    @RequiresFlagsEnabled(FLAG_NEW_DEFAULT_ACCOUNT_API_ENABLED)
    @Test
    public void testDefaultAccountInLocalState() {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            setDefaultAccountForNewContacts(DefaultAccountAndState.ofLocal());
        });
        assertEquals(DefaultAccountAndState.ofLocal(), getDefaultAccountForNewContacts());
    }

    @RequiresFlagsEnabled(FLAG_NEW_DEFAULT_ACCOUNT_API_ENABLED)
    @Test
    public void testDefaultAccount_cloud_accountIsNotSignedIn() {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            assertThrows(IllegalArgumentException.class, () -> setDefaultAccountForNewContacts(
                    DefaultAccountAndState.ofCloud(ACCT_NOT_PRESENT)));
        });
        assertEquals(DefaultAccountAndState.ofNotSet(), getDefaultAccountForNewContacts());
    }

    @RequiresFlagsEnabled(FLAG_NEW_DEFAULT_ACCOUNT_API_ENABLED)
    @Test
    public void testDefaultAccount_cloud_accountIsSignedIn() {
        SystemUtil.runWithShellPermissionIdentity(
                () -> setDefaultAccountForNewContacts(DefaultAccountAndState.ofCloud(ACCT_1)));
        assertEquals(DefaultAccountAndState.ofCloud(ACCT_1), getDefaultAccountForNewContacts());
    }

    @RequiresFlagsEnabled(FLAG_NEW_DEFAULT_ACCOUNT_API_ENABLED)
    @Test
    public void testDefaultAccount_cloud_invalidCloudAccounts() {
        // SIM_ACCT is not a SIM account, which cannot be set as default account with the state SIM.
        SystemUtil.runWithShellPermissionIdentity(() -> {
            assertThrows(IllegalArgumentException.class, () -> setDefaultAccountForNewContacts(
                    DefaultAccountAndState.ofCloud(SIM_ACCT)));
        });
        assertEquals(DefaultAccountAndState.ofNotSet(), getDefaultAccountForNewContacts());
    }

    @RequiresFlagsEnabled(FLAG_NEW_DEFAULT_ACCOUNT_API_ENABLED)
    @Test
    public void testDefaultAccount_sim() {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            setDefaultAccountForNewContacts(DefaultAccountAndState.ofSim(SIM_ACCT));
        });
        assertEquals(DefaultAccountAndState.ofSim(SIM_ACCT), getDefaultAccountForNewContacts());
    }

    @RequiresFlagsEnabled(FLAG_NEW_DEFAULT_ACCOUNT_API_ENABLED)
    @Test
    public void testDefaultAccount_sim_invalidSimAccount() {
        // ACCT_1 is not a SIM account, which cannot be set as default account with the state SIM.
        SystemUtil.runWithShellPermissionIdentity(() -> {
            assertThrows(IllegalArgumentException.class,
                    () -> setDefaultAccountForNewContacts(DefaultAccountAndState.ofSim(ACCT_1)));
        });
        assertEquals(DefaultAccountAndState.ofNotSet(), getDefaultAccountForNewContacts());
    }

    @RequiresFlagsEnabled(FLAG_NEW_DEFAULT_ACCOUNT_API_ENABLED)
    @Test
    public void testDefaultAccount_getCloudEligibleAccounts() {
        HashSet<String> eligibleCloudAccountTypes = new HashSet<>(
                Arrays.asList(configuredEligibleCloudAccountTypes()));

        Set<Account> accountsOtherThanEligibleCloudAccounts =
                new HashSet<>(Arrays.asList(mAccountManager.getAccounts()))
                        .stream()
                                .filter(
                                        account -> {
                                            try {
                                                return ContentResolver.getIsSyncable(
                                                                account, ContactsContract.AUTHORITY)
                                                        > 0;
                                            } catch (Exception e) {
                                                return false;
                                            }
                                        })
                                .collect(Collectors.toSet());

        SystemUtil.runWithShellPermissionIdentity(() -> {
            List<Account> eligibleCloudAccounts = getEligibleCloudAccounts();

            // Check that very eligible cloud account must be with the eligible cloud account type.
            for (Account account : eligibleCloudAccounts) {
                assertTrue(eligibleCloudAccountTypes.contains(account.type));
                accountsOtherThanEligibleCloudAccounts.remove(account);
            }

            // Check that no account other than those returned by getEligibleCloudAccounts
            // come with an eligible cloud account type.
            for (Account account : accountsOtherThanEligibleCloudAccounts) {
                assertFalse(eligibleCloudAccountTypes.contains(account.type));
            }
        });
    }

    @Test
    @RequiresFlagsEnabled(FLAG_NEW_DEFAULT_ACCOUNT_API_ENABLED)
    public void testRawContactAndGroupInsert_whenDefaultAccountIsCloud() throws Exception {
        assumeTrue(
                "Skipped if the config_rawContactsAccountRestrictionEnabled is set to false",
                configuredAccountRestrictionEnabled());

        assumeTrue(
                "Skipped for target SDK version < Android B",
                mContext.getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.BAKLAVA
        );

        SystemUtil.runWithShellPermissionIdentity(
                () -> setDefaultAccountForNewContacts(DefaultAccountAndState.ofCloud(ACCT_1)));
        assertEquals(DefaultAccountAndState.ofCloud(ACCT_1), getDefaultAccountForNewContacts());

        // Insert without an account, should store contacts in default account ACCT_1.
        long rawContactId0 = RawContactUtil.insertRawContactIgnoringNullAccount(mResolver, null);
        assertRawContactAccount(rawContactId0, ACCT_1);

        // Insert with SIM or local account, should fail.
        assertThrows(IllegalArgumentException.class,
                () -> RawContactUtil.insertRawContactUsingNullAccount(mResolver, null));

        assertThrows(IllegalArgumentException.class,
                () -> RawContactUtil.insertRawContactUsingNullAccount(mResolver,
                        getLocalAccount()));

        assertThrows(IllegalArgumentException.class,
                () -> RawContactUtil.insertRawContactUsingNullAccount(mResolver, SIM_ACCT));

        long rawContactId1 = RawContactUtil.insertRawContactUsingNullAccount(mResolver, ACCT_1);
        assertRawContactAccount(rawContactId1, ACCT_1);

        long rawContactId2 = RawContactUtil.insertRawContactUsingNullAccount(mResolver, ACCT_2);
        assertRawContactAccount(rawContactId2, ACCT_2);

        // Insert without an account, should store contacts in default account ACCT_1.
        long groupId0 = GroupUtil.insertGroupWithoutAccount(mResolver);
        assertGroupAccount(groupId0, ACCT_1);

        // Insert with SIM or local account, should fail.
        assertThrows(IllegalArgumentException.class,
                () -> GroupUtil.insertGroupWithAccount(mResolver, null));

        assertThrows(IllegalArgumentException.class,
                () -> GroupUtil.insertGroupWithAccount(mResolver, getLocalAccount()));

        assertThrows(IllegalArgumentException.class,
                () -> GroupUtil.insertGroupWithAccount(mResolver, SIM_ACCT));

        long groupId1 = GroupUtil.insertGroupWithAccount(mResolver, ACCT_1);
        assertGroupAccount(groupId1, ACCT_1);

        long groupId2 = GroupUtil.insertGroupWithAccount(mResolver, ACCT_2);
        assertGroupAccount(groupId2, ACCT_2);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_NEW_DEFAULT_ACCOUNT_API_ENABLED)
    public void testRawContactAndGroupInsert_whenDefaultAccountIsLocal() throws Exception {
        SystemUtil.runWithShellPermissionIdentity(
                () -> setDefaultAccountForNewContacts(DefaultAccountAndState.ofLocal()));
        assertEquals(DefaultAccountAndState.ofLocal(), getDefaultAccountForNewContacts());

        // Insert without an account, should store contacts in default account getLocalAccount.
        long rawContactId0 = RawContactUtil.insertRawContactIgnoringNullAccount(mResolver, null);
        assertRawContactAccount(rawContactId0, getLocalAccount());

        // Insert with account should always succeed and to be placed in the correct account.
        long rawContactId1 = RawContactUtil.insertRawContactUsingNullAccount(mResolver, null);
        assertRawContactAccount(rawContactId1, getLocalAccount());

        long rawContactId2 = RawContactUtil.insertRawContactUsingNullAccount(mResolver,
                getLocalAccount());
        assertRawContactAccount(rawContactId2, getLocalAccount());

        long rawContactId3 = RawContactUtil.insertRawContactUsingNullAccount(mResolver, SIM_ACCT);
        assertRawContactAccount(rawContactId3, SIM_ACCT);

        long rawContactId4 = RawContactUtil.insertRawContactUsingNullAccount(mResolver, ACCT_1);
        assertRawContactAccount(rawContactId4, ACCT_1);

        // Insert without an account, should store contacts in default account getLocalAccount.
        long groupId0 = GroupUtil.insertGroupWithoutAccount(mResolver);
        assertGroupAccount(groupId0, getLocalAccount());

        // Insert with account should always succeed and to be placed in the correct account.
        long groupId1 = GroupUtil.insertGroupWithAccount(mResolver, null);
        assertGroupAccount(groupId1, getLocalAccount());

        long groupId2 = GroupUtil.insertGroupWithAccount(mResolver, getLocalAccount());
        assertGroupAccount(groupId2, getLocalAccount());

        long groupId3 = GroupUtil.insertGroupWithAccount(mResolver, SIM_ACCT);
        assertGroupAccount(groupId3, SIM_ACCT);

        long groupId4 = GroupUtil.insertGroupWithAccount(mResolver, ACCT_1);
        assertGroupAccount(groupId4, ACCT_1);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_NEW_DEFAULT_ACCOUNT_API_ENABLED)
    public void testRawContactAndGroupInsert_whenDefaultAccountIsSim() throws Exception {
        SystemUtil.runWithShellPermissionIdentity(
                () -> setDefaultAccountForNewContacts(DefaultAccountAndState.ofSim(SIM_ACCT)));
        assertEquals(DefaultAccountAndState.ofSim(SIM_ACCT), getDefaultAccountForNewContacts());

        // Insert without an account, should store contacts in default account SIM_ACCT.
        long rawContactId0 = RawContactUtil.insertRawContactIgnoringNullAccount(mResolver, null);
        assertRawContactAccount(rawContactId0, SIM_ACCT);

        // Insert with account should always succeed and to be placed in the correct account.
        long rawContactId1 = RawContactUtil.insertRawContactUsingNullAccount(mResolver, null);
        assertRawContactAccount(rawContactId1, getLocalAccount());

        long rawContactId2 = RawContactUtil.insertRawContactUsingNullAccount(mResolver,
                getLocalAccount());
        assertRawContactAccount(rawContactId2, getLocalAccount());

        long rawContactId3 = RawContactUtil.insertRawContactUsingNullAccount(mResolver, SIM_ACCT);
        assertRawContactAccount(rawContactId3, SIM_ACCT);

        long rawContactId4 = RawContactUtil.insertRawContactUsingNullAccount(mResolver, ACCT_1);
        assertRawContactAccount(rawContactId4, ACCT_1);

        // Insert without an account, should store contacts in default account SIM_ACCT.
        long groupId0 = GroupUtil.insertGroupWithoutAccount(mResolver);
        assertGroupAccount(groupId0, SIM_ACCT);

        // Insert with account should always succeed and to be placed in the correct account.
        long groupId1 = GroupUtil.insertGroupWithAccount(mResolver, null);
        assertGroupAccount(groupId1, getLocalAccount());

        long groupId2 = GroupUtil.insertGroupWithAccount(mResolver, getLocalAccount());
        assertGroupAccount(groupId2, getLocalAccount());

        long groupId3 = GroupUtil.insertGroupWithAccount(mResolver, SIM_ACCT);
        assertGroupAccount(groupId3, SIM_ACCT);

        long groupId4 = GroupUtil.insertGroupWithAccount(mResolver, ACCT_1);
        assertGroupAccount(groupId4, ACCT_1);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_NEW_DEFAULT_ACCOUNT_API_ENABLED)
    public void testRawContactAndGroupInsert_whenDefaultAccountIsNotSet() throws Exception {
        SystemUtil.runWithShellPermissionIdentity(
                () -> setDefaultAccountForNewContacts(DefaultAccountAndState.ofNotSet()));
        assertEquals(DefaultAccountAndState.ofNotSet(), getDefaultAccountForNewContacts());

        // Insert without an account, should store contacts to local account.
        long rawContactId0 = RawContactUtil.insertRawContactIgnoringNullAccount(mResolver, null);
        assertRawContactAccount(rawContactId0, getLocalAccount());

        // Insert with account should always succeed and to be placed in the correct account.
        long rawContactId1 = RawContactUtil.insertRawContactUsingNullAccount(mResolver, null);
        assertRawContactAccount(rawContactId1, getLocalAccount());

        long rawContactId2 = RawContactUtil.insertRawContactUsingNullAccount(mResolver,
                getLocalAccount());
        assertRawContactAccount(rawContactId2, getLocalAccount());

        long rawContactId3 = RawContactUtil.insertRawContactUsingNullAccount(mResolver, SIM_ACCT);
        assertRawContactAccount(rawContactId3, SIM_ACCT);

        long rawContactId4 = RawContactUtil.insertRawContactUsingNullAccount(mResolver, ACCT_1);
        assertRawContactAccount(rawContactId4, ACCT_1);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_NEW_DEFAULT_ACCOUNT_API_ENABLED)
    public void testRawContactAndGroupAccountUpdate_whenDefaultAccountIsCloud() throws Exception {
        assumeTrue(
                "Skipped if the config_rawContactsAccountRestrictionEnabled is set to false",
                configuredAccountRestrictionEnabled());

        assumeTrue(
                "Skipped for target SDK version < Android B",
                mContext.getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.BAKLAVA
        );

        SystemUtil.runWithShellPermissionIdentity(
                () -> setDefaultAccountForNewContacts(DefaultAccountAndState.ofCloud(ACCT_1)));
        assertEquals(DefaultAccountAndState.ofCloud(ACCT_1), getDefaultAccountForNewContacts());

        // Insert without an account, should store contacts in default account ACCT_1.
        long rawContactId0 = RawContactUtil.insertRawContactIgnoringNullAccount(mResolver, null);
        assertRawContactAccount(rawContactId0, ACCT_1);

        // Update the contact's account to local or SIM account should fail.
        assertThrows(IllegalArgumentException.class,
                () -> RawContactUtil.updateRawContactAccount(mResolver, rawContactId0, null));
        assertRawContactAccount(rawContactId0, ACCT_1);

        assertThrows(IllegalArgumentException.class,
                () -> RawContactUtil.updateRawContactAccount(mResolver, rawContactId0,
                        getLocalAccount()));
        assertRawContactAccount(rawContactId0, ACCT_1);

        assertThrows(IllegalArgumentException.class,
                () -> RawContactUtil.updateRawContactAccount(mResolver, rawContactId0, SIM_ACCT));
        assertRawContactAccount(rawContactId0, ACCT_1);

        // Update the contact's account to cloud account should succeed.
        assertEquals(1, RawContactUtil.updateRawContactAccount(mResolver, rawContactId0, ACCT_1));
        assertRawContactAccount(rawContactId0, ACCT_1);

        assertEquals(1, RawContactUtil.updateRawContactAccount(mResolver, rawContactId0, ACCT_2));
        assertRawContactAccount(rawContactId0, ACCT_2);


        // Insert without an account, should store group in default account ACCT_1.
        long groupId0 = GroupUtil.insertGroupWithoutAccount(mResolver);
        assertGroupAccount(groupId0, ACCT_1);

        // Update the contact's account to local or SIM account should fail.
        assertThrows(IllegalArgumentException.class,
                () -> GroupUtil.updateGroupAccount(mResolver, groupId0, null));
        assertGroupAccount(groupId0, ACCT_1);

        assertThrows(IllegalArgumentException.class,
                () -> GroupUtil.updateGroupAccount(mResolver, groupId0, getLocalAccount()));
        assertGroupAccount(groupId0, ACCT_1);

        assertThrows(IllegalArgumentException.class,
                () -> GroupUtil.updateGroupAccount(mResolver, groupId0, SIM_ACCT));
        assertGroupAccount(groupId0, ACCT_1);

        // Update the contact's account to cloud account should succeed.
        assertEquals(1, GroupUtil.updateGroupAccount(mResolver, groupId0, ACCT_1));
        assertGroupAccount(groupId0, ACCT_1);

        assertEquals(1, GroupUtil.updateGroupAccount(mResolver, groupId0, ACCT_2));
        assertGroupAccount(groupId0, ACCT_2);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_NEW_DEFAULT_ACCOUNT_API_ENABLED)
    public void testRawContactAndGroupAccountUpdate_whenDefaultAccountIsLocal() throws Exception {
        SystemUtil.runWithShellPermissionIdentity(
                () -> setDefaultAccountForNewContacts(DefaultAccountAndState.ofLocal()));
        assertEquals(DefaultAccountAndState.ofLocal(), getDefaultAccountForNewContacts());

        // Insert without an account, should store contacts in local account.
        long rawContactId0 = RawContactUtil.insertRawContactIgnoringNullAccount(mResolver, null);
        assertRawContactAccount(rawContactId0, getLocalAccount());

        // Update the raw contact account should always succeed and take effect.
        assertEquals(1, RawContactUtil.updateRawContactAccount(mResolver, rawContactId0, ACCT_1));
        assertRawContactAccount(rawContactId0, ACCT_1);

        assertEquals(1, RawContactUtil.updateRawContactAccount(mResolver, rawContactId0, null));
        assertRawContactAccount(rawContactId0, null);

        assertEquals(1, RawContactUtil.updateRawContactAccount(mResolver, rawContactId0,
                getLocalAccount()));
        assertRawContactAccount(rawContactId0, getLocalAccount());

        assertEquals(1, RawContactUtil.updateRawContactAccount(mResolver, rawContactId0, SIM_ACCT));
        assertRawContactAccount(rawContactId0, SIM_ACCT);

        // Insert without an account, should store group in the local account.
        long groupId0 = GroupUtil.insertGroupWithoutAccount(mResolver);
        assertGroupAccount(groupId0, getLocalAccount());

        // Update the group account should always succeed and take effect.
        assertEquals(1, GroupUtil.updateGroupAccount(mResolver, groupId0, null));
        assertGroupAccount(groupId0, null);

        assertEquals(1, GroupUtil.updateGroupAccount(mResolver, groupId0, getLocalAccount()));
        assertGroupAccount(groupId0, getLocalAccount());

        assertEquals(1, GroupUtil.updateGroupAccount(mResolver, groupId0, SIM_ACCT));
        assertGroupAccount(groupId0, SIM_ACCT);

        // Update the contact's account to cloud account should succeed.
        assertEquals(1, GroupUtil.updateGroupAccount(mResolver, groupId0, ACCT_1));
        assertGroupAccount(groupId0, ACCT_1);

        assertEquals(1, GroupUtil.updateGroupAccount(mResolver, groupId0, ACCT_2));
        assertGroupAccount(groupId0, ACCT_2);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_NEW_DEFAULT_ACCOUNT_API_ENABLED)
    public void testRawContactAndGroupAccountUpdate_whenDefaultAccountIsSim() throws Exception {
        SystemUtil.runWithShellPermissionIdentity(
                () -> setDefaultAccountForNewContacts(DefaultAccountAndState.ofSim(SIM_ACCT)));
        assertEquals(DefaultAccountAndState.ofSim(SIM_ACCT), getDefaultAccountForNewContacts());

        // Insert without an account, should store contacts in default account SIM_ACCT.
        long rawContactId0 = RawContactUtil.insertRawContactIgnoringNullAccount(mResolver, null);
        assertRawContactAccount(rawContactId0, SIM_ACCT);

        // Updating contact's account should always succeed and take effect.
        assertEquals(1, RawContactUtil.updateRawContactAccount(mResolver, rawContactId0, ACCT_1));
        assertRawContactAccount(rawContactId0, ACCT_1);

        assertEquals(1, RawContactUtil.updateRawContactAccount(mResolver, rawContactId0, null));
        assertRawContactAccount(rawContactId0, null);

        assertEquals(1, RawContactUtil.updateRawContactAccount(mResolver, rawContactId0,
                getLocalAccount()));
        assertRawContactAccount(rawContactId0, getLocalAccount());

        assertEquals(1, RawContactUtil.updateRawContactAccount(mResolver, rawContactId0, SIM_ACCT));
        assertRawContactAccount(rawContactId0, SIM_ACCT);

        // Insert without an account, should store group in the SIM default account.
        long groupId0 = GroupUtil.insertGroupWithoutAccount(mResolver);
        assertGroupAccount(groupId0, SIM_ACCT);

        // Update the group account should always succeed and take effect.
        assertEquals(1, GroupUtil.updateGroupAccount(mResolver, groupId0, null));
        assertGroupAccount(groupId0, null);

        assertEquals(1, GroupUtil.updateGroupAccount(mResolver, groupId0, getLocalAccount()));
        assertGroupAccount(groupId0, getLocalAccount());

        assertEquals(1, GroupUtil.updateGroupAccount(mResolver, groupId0, SIM_ACCT));
        assertGroupAccount(groupId0, SIM_ACCT);

        // Update the contact's account to cloud account should succeed.
        assertEquals(1, GroupUtil.updateGroupAccount(mResolver, groupId0, ACCT_1));
        assertGroupAccount(groupId0, ACCT_1);

        assertEquals(1, GroupUtil.updateGroupAccount(mResolver, groupId0, ACCT_2));
        assertGroupAccount(groupId0, ACCT_2);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_NEW_DEFAULT_ACCOUNT_API_ENABLED)
    public void testRawContactAndGroupAccountUpdate_whenDefaultAccountIsNotSet() throws Exception {
        SystemUtil.runWithShellPermissionIdentity(
                () -> setDefaultAccountForNewContacts(DefaultAccountAndState.ofNotSet()));
        assertEquals(DefaultAccountAndState.ofNotSet(), getDefaultAccountForNewContacts());

        // Insert without an account, should store contacts in local account.
        long rawContactId0 = RawContactUtil.insertRawContactIgnoringNullAccount(mResolver, null);
        assertRawContactAccount(rawContactId0, getLocalAccount());

        // Updating contact's account should always succeed and take effect.
        assertEquals(1, RawContactUtil.updateRawContactAccount(mResolver, rawContactId0, ACCT_1));
        assertRawContactAccount(rawContactId0, ACCT_1);

        assertEquals(1, RawContactUtil.updateRawContactAccount(mResolver, rawContactId0, null));
        assertRawContactAccount(rawContactId0, null);

        assertEquals(1, RawContactUtil.updateRawContactAccount(mResolver, rawContactId0,
                getLocalAccount()));
        assertRawContactAccount(rawContactId0, getLocalAccount());

        assertEquals(1, RawContactUtil.updateRawContactAccount(mResolver, rawContactId0, SIM_ACCT));
        assertRawContactAccount(rawContactId0, SIM_ACCT);

        // Insert without an account, should store group in the local account.
        long groupId0 = GroupUtil.insertGroupWithoutAccount(mResolver);
        assertGroupAccount(groupId0, getLocalAccount());

        // Update the group account should always succeed and take effect.
        assertEquals(1, GroupUtil.updateGroupAccount(mResolver, groupId0, null));
        assertGroupAccount(groupId0, null);

        assertEquals(1, GroupUtil.updateGroupAccount(mResolver, groupId0, getLocalAccount()));
        assertGroupAccount(groupId0, getLocalAccount());

        assertEquals(1, GroupUtil.updateGroupAccount(mResolver, groupId0, SIM_ACCT));
        assertGroupAccount(groupId0, SIM_ACCT);

        // Update the contact's account to cloud account should succeed.
        assertEquals(1, GroupUtil.updateGroupAccount(mResolver, groupId0, ACCT_1));
        assertGroupAccount(groupId0, ACCT_1);

        assertEquals(1, GroupUtil.updateGroupAccount(mResolver, groupId0, ACCT_2));
        assertGroupAccount(groupId0, ACCT_2);
    }

    private Account getLocalAccount() {
        String accountName = ContactsContract.RawContacts.getLocalAccountName(mContext);
        String accountType = ContactsContract.RawContacts.getLocalAccountType(mContext);

        assertFalse(accountName != null ^ accountType != null);
        if (accountName == null) {
            return null;
        } else {
            return new Account(accountName, accountType);
        }
    }

    private void assertRawContactAccount(long rawContactId, Account account) {
        assertTrue(RawContactUtil.rawContactExistsById(mResolver, rawContactId));
        String[] results = RawContactUtil.queryByRawContactId(mResolver, rawContactId,
                new String[]{
                        ContactsContract.RawContacts.ACCOUNT_NAME,
                        ContactsContract.RawContacts.ACCOUNT_TYPE,
                        ContactsContract.RawContacts.DELETED,
                });
        assertArrayEquals(new String[]{
                account != null ? account.name : null,
                account != null ? account.type : null,
                "0" // must not be deleted
        }, results);
    }

    private void assertGroupAccount(long groupId, Account account) {
        assertTrue(GroupUtil.groupExistsById(mResolver, groupId));
        String[] results = GroupUtil.queryByGroupId(mResolver, groupId,
                new String[]{
                        ContactsContract.Groups.ACCOUNT_NAME,
                        ContactsContract.Groups.ACCOUNT_TYPE,
                        ContactsContract.Groups.DELETED,
                });

        assertArrayEquals(new String[]{
                account != null ? account.name : null,
                account != null ? account.type : null,
                "0" // must not be deleted
        }, results);
    }

    private DefaultAccountAndState getDefaultAccountForNewContacts() {
        return DefaultAccount.getDefaultAccountForNewContacts(mResolver);
    }

    private void setDefaultAccountForNewContacts(DefaultAccountAndState defaultAccountAndState) {
        DefaultAccount.setDefaultAccountForNewContacts(mResolver, defaultAccountAndState);
    }

    private String[] configuredEligibleCloudAccountTypes() {
        // Get com.android.internal.R.array.config_rawContactsEligibleDefaultAccountTypes
        int resId = getContext().getResources().getIdentifier(
                "config_rawContactsEligibleDefaultAccountTypes", "array", "android");
        return getContext().getResources().getStringArray(resId);
    }

    private boolean configuredAccountRestrictionEnabled() {
        int resId =
                getContext()
                        .getResources()
                        .getIdentifier(
                                "config_rawContactsAccountRestrictionEnabled", "bool", "android");
        return getContext().getResources().getBoolean(resId);
    }

    private List<Account> getEligibleCloudAccounts() {
        return DefaultAccount.getEligibleCloudAccounts(mResolver);
    }

    private Context getContext() {
        return mContext;
    }
}
