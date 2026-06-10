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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.accounts.Account;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.ContactsContract.RawContacts.DefaultAccount.DefaultAccountAndState;
import android.provider.cts.contacts.account.StaticAccountAuthenticator;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;


@RequiresFlagsEnabled(FLAG_NEW_DEFAULT_ACCOUNT_API_ENABLED)
@RunWith(AndroidJUnit4.class)
@MediumTest
public class ContactsContract_DefaultAccountAndStateTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final Account ACCT_1 = new Account("cp removal acct 1",
            StaticAccountAuthenticator.TYPE);

    @Test
    @RequiresFlagsEnabled(FLAG_NEW_DEFAULT_ACCOUNT_API_ENABLED)
    public void testDefaultContactsAccountClass_cloud() {
        DefaultAccountAndState defaultContactsAccount = DefaultAccountAndState.ofCloud(ACCT_1);
        assertEquals(DefaultAccountAndState.DEFAULT_ACCOUNT_STATE_CLOUD,
                defaultContactsAccount.getState());
        assertEquals(ACCT_1, defaultContactsAccount.getAccount());
    }


    @Test
    @RequiresFlagsEnabled(FLAG_NEW_DEFAULT_ACCOUNT_API_ENABLED)
    public void testDefaultContactsAccountClass_sim() {
        DefaultAccountAndState defaultContactsAccount = DefaultAccountAndState.ofSim(ACCT_1);
        assertEquals(DefaultAccountAndState.DEFAULT_ACCOUNT_STATE_SIM,
                defaultContactsAccount.getState());
        assertEquals(ACCT_1, defaultContactsAccount.getAccount());
    }

    @Test
    @RequiresFlagsEnabled(FLAG_NEW_DEFAULT_ACCOUNT_API_ENABLED)
    public void testDefaultContactsAccountClass_local() {
        DefaultAccountAndState defaultContactsAccount = DefaultAccountAndState.ofLocal();
        assertEquals(DefaultAccountAndState.DEFAULT_ACCOUNT_STATE_LOCAL,
                defaultContactsAccount.getState());
        assertNull(defaultContactsAccount.getAccount());
    }

    @Test
    @RequiresFlagsEnabled(FLAG_NEW_DEFAULT_ACCOUNT_API_ENABLED)
    public void testDefaultContactsAccountClass_notSet() {
        DefaultAccountAndState defaultContactsAccount = DefaultAccountAndState.ofNotSet();
        assertEquals(DefaultAccountAndState.DEFAULT_ACCOUNT_STATE_NOT_SET,
                defaultContactsAccount.getState());
        assertNull(defaultContactsAccount.getAccount());
    }
}
